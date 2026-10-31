package app.mountx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mountx.data.repository.GameRepository
import app.mountx.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver for handling notification actions and root script triggers.
 */
@AndroidEntryPoint
class MountActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var mountNotificationManager: MountNotificationManager

    @Inject
    lateinit var gameRepository: GameRepository

    @Inject
    lateinit var systemSyncMonitor: SystemSyncMonitor

    @Inject
    lateinit var storageManager: app.mountx.root.StorageManager

    companion object {
        const val ACTION_SYNC_NOTIFICATION = "app.mountx.ACTION_SYNC_NOTIFICATION"
        const val ACTION_UNMOUNT_ALL = "app.mountx.ACTION_UNMOUNT_ALL"
        const val ACTION_MOUNT_ALL = "app.mountx.ACTION_MOUNT_ALL"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        AppLogger.info("MountActionReceiver", "Received action: $action")
        when (action) {
            ACTION_MOUNT_ALL -> {
                MountService.startMountAll(context)
                return
            }
            ACTION_UNMOUNT_ALL -> {
                MountService.startUnmountAll(context)
                return
            }
        }
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    when (action) {
                        ACTION_SYNC_NOTIFICATION -> {
                            if (!storageManager.isTransferActive) {
                                gameRepository.refreshMountStatuses()
                                mountNotificationManager.syncActiveMountNotification()
                            } else {
                                AppLogger.info("MountActionReceiver", "Transfer is active; skipping refreshMountStatuses to avoid lock contention.")
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLogger.error("MountActionReceiver", "Failed to handle action $action: ${t.message}")
            } finally {
                systemSyncMonitor.notifyRefreshAll()
                try {
                    pendingResult.finish()
                } catch (_: Exception) {}
            }
        }
    }
}
