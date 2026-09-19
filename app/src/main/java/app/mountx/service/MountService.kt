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
import app.mountx.data.repository.GameRepository
import app.mountx.data.repository.StorageRepository
import app.mountx.util.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MountService : Service() {

    @Inject
    lateinit var gameRepository: GameRepository

    @Inject
    lateinit var storageRepository: StorageRepository

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var watchdogDaemon: MountWatchdogDaemon

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "mountx_service"
        const val NOTIFICATION_ID = 1001

        const val ACTION_MOUNT_ALL = "app.mountx.ACTION_MOUNT_ALL"
        const val ACTION_UNMOUNT_ALL = "app.mountx.ACTION_UNMOUNT_ALL"
        const val ACTION_BOOST_GAME = "app.mountx.ACTION_BOOST_GAME"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

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
        when (intent?.action) {
            ACTION_MOUNT_ALL -> {
                serviceScope.launch {
                    val sdBase = appPreferences.sdBasePath.first()
                    val blockDevice = appPreferences.sdBlockDevice.first()

                    updateNotification(getString(R.string.notif_mounting_games))
                    storageRepository.mountSdPartition(blockDevice, sdBase)

                    val mountedCount = gameRepository.mountAll(sdBase)

                    updateNotification(
                        getString(R.string.notif_games_mounted_active, mountedCount),
                        isFinished = false
                    )
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
            ACTION_UNMOUNT_ALL -> {
                serviceScope.launch {
                    val sdBase = appPreferences.sdBasePath.first()
                    updateNotification(getString(R.string.notif_unmounting_games))
                    gameRepository.unmountAll(sdBase)
                    updateNotification(getString(R.string.notif_games_unmounted), isFinished = true)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
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
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
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
            .setSmallIcon(R.drawable.ic_mountx_emblem)
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
