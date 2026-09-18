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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "mountx_service"
        const val NOTIFICATION_ID = 1001

        const val ACTION_MOUNT_ALL = "app.mountx.ACTION_MOUNT_ALL"
        const val ACTION_UNMOUNT_ALL = "app.mountx.ACTION_UNMOUNT_ALL"

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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing Mountify service..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MOUNT_ALL -> {
                serviceScope.launch {
                    val sdBase = appPreferences.sdBasePath.first()
                    val blockDevice = appPreferences.sdBlockDevice.first()

                    updateNotification("Checking storage partition...")
                    storageRepository.mountSdPartition(blockDevice, sdBase)

                    updateNotification("Mounting games...")
                    val mountedCount = gameRepository.mountAll(sdBase)

                    updateNotification("Mountify: $mountedCount game(s) mounted.")
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
            ACTION_UNMOUNT_ALL -> {
                serviceScope.launch {
                    val sdBase = appPreferences.sdBasePath.first()
                    updateNotification("Unmounting games...")
                    gameRepository.unmountAll(sdBase)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
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
                "Mountify Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of Mountify game mounting operations"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Mountify")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
