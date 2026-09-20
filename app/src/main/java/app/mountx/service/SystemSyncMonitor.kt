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
    private val appPreferences: AppPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<SystemSyncEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SystemSyncEvent> = _events.asSharedFlow()

    private var isRegistered = false

    companion object {
        const val EMERGENCY_CHANNEL_ID = "mountx_emergency"
        const val EMERGENCY_NOTIF_ID = 9999
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
                        _events.emit(SystemSyncEvent.StorageMounted)
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

        createEmergencyNotificationChannel()

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

        // 3. Post Heads-Up notification
        postEmergencyNotification()

        // 4. Emit event to UI ViewModels
        _events.emit(SystemSyncEvent.StorageDisconnected)
    }

    private fun createEmergencyNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                "MountX Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts for storage ejection and safety"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun postEmergencyNotification() {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            99,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, EMERGENCY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mountx_emblem)
            .setColor(0xFFFF1744.toInt())
            .setContentTitle("Penyimpanan Eksternal Terputus")
            .setContentText("Aplikasi terkait telah dihentikan demi keamanan data.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(EMERGENCY_NOTIF_ID, notif)
    }
}
