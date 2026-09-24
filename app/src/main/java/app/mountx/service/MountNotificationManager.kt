package app.mountx.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.mountx.MainActivity
import app.mountx.R
import app.mountx.data.db.GameDao
import app.mountx.data.model.MountStatus
import app.mountx.root.RootShell
import app.mountx.util.AppLogger
import app.mountx.util.FormatUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified notification manager that keeps Android notification shade
 * 100% in sync with real mount status across all lifecycle events.
 */
@Singleton
class MountNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    companion object {
        const val CHANNEL_ACTIVE_STATUS = "mountx_active_status"
        const val CHANNEL_EMERGENCY_ALERTS = "mountx_emergency_alerts"
        const val CHANNEL_OPERATIONS = "mountx_operations"

        const val ACTIVE_NOTIF_ID = 1001
        const val EMERGENCY_NOTIF_ID = 9999
        const val HOTPLUG_NOTIF_ID = 9998
    }

    /**
     * Initializes notification channels for MountX.
     */
    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                CHANNEL_ACTIVE_STATUS,
                context.getString(R.string.notif_channel_active_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_active_desc)
                setShowBadge(false)
                setSound(null, null)
            }

            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY_ALERTS,
                context.getString(R.string.notif_channel_emergency_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_emergency_desc)
                enableVibration(true)
            }

            val operationsChannel = NotificationChannel(
                CHANNEL_OPERATIONS,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(statusChannel)
            notificationManager.createNotificationChannel(emergencyChannel)
            notificationManager.createNotificationChannel(operationsChannel)
        }
    }

    /**
     * Synchronizes the persistent/ongoing active mount notification.
     * When apps are mounted, displays active status with unmount & open actions.
     * When 0 apps are mounted, automatically cancels the notification.
     */
    fun syncActiveMountNotification() {
        scope.launch {
            try {
                createNotificationChannels()
                val games = gameDao.getAllGamesSync()
                val mountedGames = games.filter { it.isEnabled && it.mountStatus == MountStatus.MOUNTED }
                if (mountedGames.isEmpty()) {
                    notificationManager.cancel(ACTIVE_NOTIF_ID)
                    return@launch
                }

                // Resolve primary disk label from /proc/mounts or first game mount
                var diskLabel = "MicroSD"
                val mountOut = RootShell.execForOutput("cat /proc/mounts 2>/dev/null | grep -E '(/data/sdext2|/mnt/media_rw)' | head -n 1")
                if (mountOut.isNotBlank()) {
                    val spec = mountOut.trim().split(Regex("\\s+")).getOrNull(0) ?: ""
                    if (spec.contains("mmcblk")) {
                        diskLabel = "MicroSD"
                    } else if (spec.contains("sd")) {
                        diskLabel = "External Storage"
                    }
                }

                val totalBytes = mountedGames.sumOf { it.dataSizeBytes }
                val formattedSize = if (totalBytes > 0) FormatUtils.formatBytes(totalBytes) else ""
                val count = mountedGames.size

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val unmountAllIntent = Intent(context, MountActionReceiver::class.java).apply {
                    action = MountActionReceiver.ACTION_UNMOUNT_ALL
                }
                val unmountAllPendingIntent = PendingIntent.getBroadcast(
                    context, 1, unmountAllIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val title = context.getString(R.string.notif_active_status_title, count)
                val body = if (formattedSize.isNotBlank()) {
                    context.getString(R.string.notif_active_status_body, diskLabel, formattedSize)
                } else {
                    diskLabel
                }

                val bigStyle = NotificationCompat.InboxStyle()
                    .setBigContentTitle(title)
                    .setSummaryText(body)

                for (g in mountedGames.take(6)) {
                    val gSize = if (g.dataSizeBytes > 0) " (${FormatUtils.formatBytes(g.dataSizeBytes)})" else ""
                    bigStyle.addLine("✓ ${g.displayName}$gSize")
                }
                if (mountedGames.size > 6) {
                    bigStyle.addLine("+${mountedGames.size - 6} more")
                }

                val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE_STATUS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(0xFF00E5FF.toInt())
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(bigStyle)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOngoing(true)
                    .setContentIntent(openPendingIntent)
                    .addAction(
                        0,
                        context.getString(R.string.notif_action_open),
                        openPendingIntent
                    )
                    .addAction(
                        0,
                        context.getString(R.string.notif_action_unmount_all),
                        unmountAllPendingIntent
                    )

                notificationManager.notify(ACTIVE_NOTIF_ID, builder.build())
                AppLogger.info("Notification", "Active mount notification synced: $count games active.")
            } catch (e: Exception) {
                AppLogger.error("Notification", "Failed to sync notification: ${e.message}")
            }
        }
    }

    /**
     * Posts high-importance heads-up alert when storage is abruptly ejected/disconnected.
     */
    fun postEmergencyDisconnectedNotification() {
        scope.launch {
            try {
                createNotificationChannels()
                notificationManager.cancel(ACTIVE_NOTIF_ID)

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 99, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_EMERGENCY_ALERTS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(0xFFFF1744.toInt())
                    .setContentTitle(context.getString(R.string.notif_emergency_eject_title))
                    .setContentText(context.getString(R.string.notif_emergency_eject_body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                notificationManager.notify(EMERGENCY_NOTIF_ID, builder.build())
            } catch (e: Exception) {
                AppLogger.error("Notification", "Failed to post emergency notification: ${e.message}")
            }
        }
    }

    /**
     * Posts notification when external storage is reconnected / hot-plugged.
     */
    fun postHotPlugConnectedNotification(count: Int, diskName: String = "MicroSD") {
        scope.launch {
            try {
                createNotificationChannels()
                notificationManager.cancel(EMERGENCY_NOTIF_ID)

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 98, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE_STATUS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(0xFF00E5FF.toInt())
                    .setContentTitle(context.getString(R.string.notif_hotplug_title))
                    .setContentText(context.getString(R.string.notif_hotplug_body, count))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                notificationManager.notify(HOTPLUG_NOTIF_ID, builder.build())
                syncActiveMountNotification()
            } catch (e: Exception) {
                AppLogger.error("Notification", "Failed to post hotplug notification: ${e.message}")
            }
        }
    }
}
