package app.mountx.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import app.mountx.MainActivity
import app.mountx.R
import app.mountx.data.db.GameDao
import app.mountx.data.model.MountStatus
import app.mountx.root.MountManager
import app.mountx.root.RootShell
import app.mountx.util.AppLogger
import app.mountx.util.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed class SystemSyncEvent {
    object StorageMounted : SystemSyncEvent()
    object StorageDisconnected : SystemSyncEvent()
    data class PackageInstalled(val packageName: String) : SystemSyncEvent()
    data class PackageRemoved(val packageName: String) : SystemSyncEvent()
    object RefreshAll : SystemSyncEvent()
}

/**
 * Real-Time System Event Bus & Watchdog for MountX.
 * Dynamically monitors Storage connections/disconnections and Package installs/uninstalls.
 * Executes emergency safeguards (process kill + lazy unmount) upon sudden MicroSD ejection.
 */
@Singleton
class SystemSyncMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val mountManager: MountManager,
    private val appPreferences: AppPreferences,
    private val mountNotificationManager: MountNotificationManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<SystemSyncEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SystemSyncEvent> = _events.asSharedFlow()

    private var isRegistered = false

    companion object {
        const val EMERGENCY_CHANNEL_ID = "mountx_emergency"
        const val EMERGENCY_NOTIF_ID = 9999
        const val REMOUNT_NOTIF_ID = 9998
        private const val REMOUNT_SETTLE_MS = 3_000L
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            AppLogger.info("SystemSyncMonitor", "Received broadcast action: $action")
            when (action) {
                Intent.ACTION_MEDIA_EJECT,
                Intent.ACTION_MEDIA_UNMOUNTED,
                Intent.ACTION_MEDIA_REMOVED,
                Intent.ACTION_MEDIA_BAD_REMOVAL -> {
                    scope.launch {
                        handleEmergencyMediaEject()
                    }
                }
                Intent.ACTION_MEDIA_MOUNTED -> {
                    scope.launch {
                        handleStorageMounted(ctx ?: context)
                    }
                }
                Intent.ACTION_PACKAGE_ADDED -> {
                    val pkg = intent.data?.schemeSpecificPart
                    if (!pkg.isNullOrBlank()) {
                        scope.launch {
                            _events.emit(SystemSyncEvent.PackageInstalled(pkg))
                        }
                    }
                }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    val pkg = intent.data?.schemeSpecificPart
                    if (!pkg.isNullOrBlank()) {
                        scope.launch {
                            _events.emit(SystemSyncEvent.PackageRemoved(pkg))
                        }
                    }
                }
            }
        }
    }

    /**
     * Start the real-time event listener.
     */
    fun startMonitoring() {
        if (isRegistered) return

        mountNotificationManager.createNotificationChannels()

        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        try {
            context.registerReceiver(systemReceiver, mediaFilter)
            context.registerReceiver(systemReceiver, pkgFilter)
            isRegistered = true
            AppLogger.info("SystemSyncMonitor", "System broadcast listeners registered.")
        } catch (e: Exception) {
            AppLogger.error("SystemSyncMonitor", "Failed to register receivers: ${e.message}")
        }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(systemReceiver)
            isRegistered = false
        } catch (_: Exception) {}
    }

    /**
     * Trigger a manual global refresh event.
     */
    fun notifyRefresh() {
        scope.launch {
            _events.emit(SystemSyncEvent.RefreshAll)
        }
    }

    /**
     * Emergency Protocol when MicroSD is abruptly ejected/unmounted.
     * 1. Force-stop active game processes (am force-stop).
     * 2. Execute instant lazy unmount (umount -f -l).
     * 3. Show Heads-Up Emergency Notification.
     * 4. Notify all ViewModels of StorageDisconnected.
     */
    private suspend fun handleEmergencyMediaEject() {
        AppLogger.warn("SystemSyncMonitor", "EMERGENCY: MicroSD Ejection detected! Executing protocol...")

        val sdBase = runCatching { appPreferences.sdBasePath.first() }.getOrDefault("/data/sdext2")

        // 1. Kill active game processes and mark status as DISK_DETACHED
        try {
            val games = gameDao.getAllGamesSync()
            for (game in games) {
                val pgrep = RootShell.exec("pgrep -f \"${game.packageName}\" 2>/dev/null")
                if (pgrep.isSuccess && pgrep.output.isNotBlank()) {
                    RootShell.exec("am force-stop \"${game.packageName}\" 2>/dev/null")
                    AppLogger.warn("SystemSyncMonitor", "Force-stopped active process: ${game.packageName}")
                }
                if (game.mountStatus == MountStatus.MOUNTED) {
                    gameDao.updateMountStatus(game.packageName, MountStatus.DISK_DETACHED)
                }
            }
        } catch (e: Exception) {
            AppLogger.error("SystemSyncMonitor", "Failed to check/kill processes: ${e.message}")
        }

        // 2. Instant lazy unmount across all namespaces dynamically
        try {
            mountManager.unmountAll(sdBase)
            RootShell.exec("umount -f -l \"$sdBase\" 2>/dev/null")
            if (sdBase != "/data/sdext2") {
                mountManager.unmountAll("/data/sdext2")
                RootShell.exec("umount -f -l /data/sdext2 2>/dev/null")
            }
            // Dynamic cleanup for any remaining mountpoints matching game paths from /proc/mounts
            val mountsOutput = RootShell.execForOutput("cat /proc/mounts 2>/dev/null")
            mountsOutput.lines().forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val target = parts[1]
                    if (target.contains("/Android/data/") || target.contains("/Android/obb/") || target.contains("/MountX/")) {
                        RootShell.exec("umount -f -l \"$target\" 2>/dev/null")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.error("SystemSyncMonitor", "Failed lazy unmount: ${e.message}")
        }

        // 3. Post Heads-Up notification and sync status bar
        mountNotificationManager.postEmergencyDisconnectedNotification()

        // 4. Emit event to UI ViewModels
        _events.emit(SystemSyncEvent.StorageDisconnected)
    }

    /**
     * Hot-Plug Recovery Protocol: fires when external storage is mounted/re-connected.
     * 1. Wait 3s for vold/FUSE to fully settle the new volume.
     * 2. Re-mount SD partition if not already mounted.
     * 3. Re-bind all games/apps that are in DISK_DETACHED state.
     * 4. Post a success notification in the status bar.
     * 5. Notify ViewModels of StorageMounted event.
     */
    private suspend fun handleStorageMounted(ctx: Context) {
        AppLogger.info("SystemSyncMonitor", "Storage connected. Waiting ${REMOUNT_SETTLE_MS}ms for volume to settle...")
        delay(REMOUNT_SETTLE_MS)

        val sdBase = runCatching { appPreferences.sdBasePath.first() }.getOrDefault("/data/sdext2")
        val blockDevice = runCatching { appPreferences.sdBlockDevice.first() }.getOrDefault("")

        // Re-mount SD partition if not already mounted
        val isSdMounted = RootShell.exec("mountpoint -q \"$sdBase\" 2>/dev/null && echo YES").output.contains("YES")
        if (!isSdMounted && blockDevice.isNotBlank()) {
            AppLogger.info("SystemSyncMonitor", "SD not mounted, triggering MountService...")
            MountService.startMountAll(ctx)
            delay(5_000L) // give MountService time to work
        }

        // Re-bind all DISK_DETACHED games
        var remountedCount = 0
        try {
            val games = gameDao.getAllGamesSync()
            for (game in games) {
                if (!game.isEnabled) continue
                if (game.mountStatus == MountStatus.DISK_DETACHED || game.mountStatus == MountStatus.MOUNTED) {
                    val result = mountManager.mountGame(game, sdBase)
                    if (result.isSuccess) {
                        gameDao.updateMountStatus(game.packageName, MountStatus.MOUNTED)
                        remountedCount++
                        AppLogger.success("SystemSyncMonitor", "Auto-remounted: ${game.displayName}")
                    } else {
                        AppLogger.warn("SystemSyncMonitor", "Failed to remount: ${game.displayName}")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.error("SystemSyncMonitor", "Error during hot-plug remount: ${e.message}")
        }

        // Post status bar notification about hot-plug result & sync active status
        if (remountedCount > 0) {
            mountNotificationManager.postHotPlugConnectedNotification(remountedCount)
        } else {
            mountNotificationManager.syncActiveMountNotification()
        }

        // Emit event to refresh UI
        _events.emit(SystemSyncEvent.StorageMounted)
        AppLogger.success("SystemSyncMonitor", "Hot-plug recovery complete. $remountedCount app(s) remounted.")
    }
}
