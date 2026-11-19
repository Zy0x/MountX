package app.mountx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.mountx.R
import app.mountx.data.db.GameDao
import app.mountx.data.model.MountStatus
import app.mountx.data.repository.AppRepository
import app.mountx.data.repository.GameRepository
import app.mountx.data.repository.StorageRepository
import app.mountx.root.MountManager
import app.mountx.root.RootShell
import app.mountx.util.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MountService : Service() {

    @Inject
    lateinit var gameRepository: GameRepository

    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var storageRepository: StorageRepository

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var gameDao: GameDao

    @Inject
    lateinit var mountManager: MountManager

    @Inject
    lateinit var watchdogDaemon: MountWatchdogDaemon

    @Inject
    lateinit var mountNotificationManager: MountNotificationManager

    @Inject
    lateinit var systemSyncMonitor: SystemSyncMonitor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "mountx_service"
        const val NOTIFICATION_ID = 1000

        const val ACTION_MOUNT_ALL = "app.mountx.ACTION_MOUNT_ALL"
        const val ACTION_UNMOUNT_ALL = "app.mountx.ACTION_UNMOUNT_ALL"
        const val ACTION_BOOST_GAME = "app.mountx.ACTION_BOOST_GAME"
        const val ACTION_POST_BOOT_SYNC = "app.mountx.ACTION_POST_BOOT_SYNC"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_IS_UNLOCK = "extra_is_unlock"

        fun startMountAll(context: Context) {
            val intent = Intent(context, MountService::class.java).apply {
                action = ACTION_MOUNT_ALL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startPostBootSync(context: Context, isUnlock: Boolean) {
            val intent = Intent(context, MountService::class.java).apply {
                action = ACTION_POST_BOOT_SYNC
                putExtra(EXTRA_IS_UNLOCK, isUnlock)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startUnmountAll(context: Context) {
            val intent = Intent(context, MountService::class.java).apply {
                action = ACTION_UNMOUNT_ALL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notif_mounting_games)))
        watchdogDaemon.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notif_mounting_games)))
        } catch (_: Exception) {}
        when (intent?.action) {
            ACTION_MOUNT_ALL -> {
                serviceScope.launch {
                    try {
                        val sdBase = appPreferences.sdBasePath.first()
                        val blockDevice = appPreferences.sdBlockDevice.first()

                        updateNotification(getString(R.string.notif_mounting_games))
                        // Clear intentional unmount flag when mounting all games
                        RootShell.exec("rm -f /dev/.mountx_unmounted /dev/.mountx_disk_error 2>/dev/null")

                        // Wait if kernel module service.sh is actively running a mount cycle right now
                        RootShell.awaitServiceMountCycleIfActive(timeoutMillis = 30_000L, waitForWatchdogUnlockGrace = false)

                        if (RootShell.probeStorageIoHealth(sdBase, blockDevice).let { it == "IO_ERROR" || it == "RO" }) {
                            storageRepository.unmountSdPartition(sdBase)
                        }

                        val mountRes = storageRepository.mountSdPartition(blockDevice, sdBase)
                        if (mountRes.isSuccess && blockDevice.isBlank()) {
                            // Automatically persist the active block device if it was previously unset
                            val activeDev = RootShell.execForOutput("cat /proc/mounts 2>/dev/null").lines()
                                .firstOrNull { it.contains(" $sdBase ") }
                                ?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""
                            if (activeDev.startsWith("/dev/block/")) {
                                appPreferences.setSdBlockDevice(activeDev)
                            }
                        }

                        // Module-first parity: Refresh mount statuses first so any games already mounted
                        // by root module service.sh are recognized and not stacked
                        gameRepository.refreshMountStatuses()
                        val mountedCount = gameRepository.mountAll(sdBase)

                        val activeCount = maxOf(mountedCount, gameRepository.getMountedGamesCount())
                        updateNotification(
                            getString(R.string.notif_games_mounted_active, activeCount),
                            isFinished = false
                        )
                    } catch (e: Exception) {
                        try {
                            gameRepository.refreshMountStatuses()
                            val count = gameRepository.getMountedGamesCount()
                            updateNotification(
                                getString(R.string.notif_games_mounted_active, count),
                                isFinished = count == 0
                            )
                        } catch (ignored: Exception) {
                            updateNotification(getString(R.string.notif_games_mounted_active, 0), isFinished = true)
                        }
                    } finally {
                        mountNotificationManager.syncActiveMountNotification()
                        systemSyncMonitor.notifyRefreshAll()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
            }
            ACTION_UNMOUNT_ALL -> {
                serviceScope.launch {
                    try {
                        val sdBase = appPreferences.sdBasePath.first()
                        updateNotification(getString(R.string.notif_unmounting_games))
                        gameRepository.unmountAll(sdBase)
                        updateNotification(getString(R.string.notif_games_unmounted), isFinished = true)
                    } catch (e: Exception) {
                        // ignore and ensure notification dismissed
                    } finally {
                        mountNotificationManager.syncActiveMountNotification()
                        systemSyncMonitor.notifyRefreshAll()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
            }
            ACTION_BOOST_GAME -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (!pkg.isNullOrBlank()) {
                    serviceScope.launch {
                        watchdogDaemon.prepareGameForLaunch(pkg)
                    }
                }
            }
            ACTION_POST_BOOT_SYNC -> {
                val isUnlock = intent.getBooleanExtra(EXTRA_IS_UNLOCK, false)
                serviceScope.launch {
                    try {
                        mountNotificationManager.createNotificationChannels()
                        val autoMount = appPreferences.autoMountOnBoot.first()
                        val rootAvailable = RootShell.isRootAvailable()
                        val sdBase = appPreferences.sdBasePath.first()
                        val blockDevice = appPreferences.sdBlockDevice.first()

                        if (autoMount && rootAvailable) {
                            RootShell.awaitServiceMountCycleIfActive(timeoutMillis = 60_000L, waitForWatchdogUnlockGrace = isUnlock)
                            val ioHealth = RootShell.probeStorageIoHealth(sdBase, blockDevice)
                            appRepository.refreshMountStatuses()

                            val isModuleHandled = RootShell.exists("/dev/.mountx_booted")
                            val isInitialMounted = RootShell.exists("/dev/.mountx_initial_mounted")
                            val enabledGames = gameDao.getAllGamesSync().filter { it.isEnabled }
                            val verifiedMap = if (enabledGames.isNotEmpty()) {
                                mountManager.verifyCanariesBatch(enabledGames)
                            } else emptyMap()
                            val anyEligibleUnmounted = enabledGames.any { g ->
                                g.mountStatus != MountStatus.DISK_DETACHED && verifiedMap[g.packageName] != true
                            }

                            if ((!isModuleHandled || (isUnlock && !isInitialMounted) || anyEligibleUnmounted || ioHealth == "IO_ERROR" || ioHealth == "RO") &&
                                !RootShell.exists("/dev/.mountx_unmounted") &&
                                !RootShell.exists("/dev/.mountx_migrating")
                            ) {
                                var attempts = 0
                                while (attempts < 10) {
                                    if (RootShell.exists("/storage/emulated/0") ||
                                        RootShell.isMountpoint("/data/sdext2") ||
                                        RootShell.isMountpoint("/dev/mountx/sdext2")
                                    ) {
                                        break
                                    }
                                    delay(500)
                                    attempts++
                                }
                                if (ioHealth == "IO_ERROR" || ioHealth == "RO") {
                                    storageRepository.unmountSdPartition(sdBase)
                                }
                                val mountRes = storageRepository.mountSdPartition(blockDevice, sdBase)
                                if (mountRes.isSuccess && blockDevice.isBlank()) {
                                    val activeDev = RootShell.execForOutput("cat /proc/mounts 2>/dev/null").lines()
                                        .firstOrNull { it.contains(" $sdBase ") }
                                        ?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""
                                    if (activeDev.startsWith("/dev/block/")) {
                                        appPreferences.setSdBlockDevice(activeDev)
                                    }
                                }
                                gameRepository.refreshMountStatuses()
                                gameRepository.mountAll(sdBase)
                            }
                        }

                        val registeredGames = gameDao.getAllGamesSync().filter { it.isEnabled }
                        if (registeredGames.isNotEmpty() && (!rootAvailable || !autoMount)) {
                            mountNotificationManager.showUnmountedGameWarningNotification(registeredGames.size)
                        }
                    } catch (_: Exception) {
                        // Safe best-effort boot sync
                    } finally {
                        mountNotificationManager.syncActiveMountNotification()
                        systemSyncMonitor.notifyRefreshAll()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, isFinished: Boolean = false): Notification {
        val openAppIntent = Intent(this, app.mountx.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val unmountIntent = Intent(this, MountService::class.java).apply {
            action = ACTION_UNMOUNT_ALL
        }
        val unmountPendingIntent = android.app.PendingIntent.getService(
            this,
            1,
            unmountIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF00E5FF.toInt())
            .setContentTitle("MountX")
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setOngoing(!isFinished)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                getString(R.string.notif_action_open),
                openPendingIntent
            )

        if (!isFinished) {
            builder.addAction(
                0,
                getString(R.string.notif_action_unmount_all),
                unmountPendingIntent
            )
        }

        return builder.build()
    }

    private fun updateNotification(text: String, isFinished: Boolean = false) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text, isFinished))
    }
}
