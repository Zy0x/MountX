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
import androidx.core.content.ContextCompat
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
    data class DiskError(val diskName: String, val details: String) : SystemSyncEvent()
    object DiskHealthRestored : SystemSyncEvent()
    data class PackageInstalled(val packageName: String) : SystemSyncEvent()
    data class PackageRemoved(val packageName: String) : SystemSyncEvent()
    object RefreshAll : SystemSyncEvent()
}

/**
 * Real-Time System Event Bus & Watchdog for MountX.
 * Dynamically monitors Storage connections/disconnections and Package installs/uninstalls.
 * Executes emergency safeguards (process kill + lazy unmount) upon sudden MicroSD ejection or hardware I/O error.
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

    /**
     * Broadcasts a global refresh event across all active view models (Dashboard, Apps, Storage).
     */
    fun notifyRefreshAll() {
        scope.launch {
            _events.emit(SystemSyncEvent.RefreshAll)
        }
    }

    private var isRegistered = false
    private var healthMonitorJob: kotlinx.coroutines.Job? = null

    companion object {
        const val EMERGENCY_CHANNEL_ID = "mountx_emergency"
        const val EMERGENCY_NOTIF_ID = 9999
        const val REMOUNT_NOTIF_ID = 9998
        const val DISK_ERROR_NOTIF_ID = 9997
        const val ACTION_DISK_ERROR = "app.mountx.ACTION_DISK_ERROR"
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
                        handleMediaUnmountOrEject(intent)
                    }
                }
                Intent.ACTION_MEDIA_MOUNTED -> {
                    scope.launch {
                        handleStorageMounted(ctx ?: context)
                    }
                }
                ACTION_DISK_ERROR -> {
                    val err = intent.getStringExtra("error") ?: "Hardware I/O error"
                    scope.launch {
                        handleDiskError("MicroSD", err)
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
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val pkg = intent.data?.schemeSpecificPart
                    if (!pkg.isNullOrBlank()) {
                        scope.launch {
                            AppLogger.info("SystemSyncMonitor", "Package updated/replaced: $pkg")
                            _events.emit(SystemSyncEvent.PackageInstalled(pkg))
                            _events.emit(SystemSyncEvent.RefreshAll)
                        }
                    }
                }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    val pkg = intent.data?.schemeSpecificPart
                    if (!pkg.isNullOrBlank() && !isReplacing) {
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
        val mediaFilterNoScheme = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
        }
        val diskErrorFilter = IntentFilter(ACTION_DISK_ERROR)
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        try {
            ContextCompat.registerReceiver(
                context,
                systemReceiver,
                mediaFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            ContextCompat.registerReceiver(
                context,
                systemReceiver,
                mediaFilterNoScheme,
                ContextCompat.RECEIVER_EXPORTED
            )
            ContextCompat.registerReceiver(
                context,
                systemReceiver,
                diskErrorFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            ContextCompat.registerReceiver(
                context,
                systemReceiver,
                pkgFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isRegistered = true
            AppLogger.info("SystemSyncMonitor", "System broadcast listeners registered.")
        } catch (e: Exception) {
            AppLogger.error("SystemSyncMonitor", "Failed to register receivers: ${e.message}")
        }

        // Active background disk health sentinel (probes every 30s when apps are mounted)
        healthMonitorJob?.cancel()
        healthMonitorJob = scope.launch {
            var previousErrorState = false
            while (true) {
                delay(30_000L)
                try {
                    val games = gameDao.getAllGamesSync()
                    val hasMountedGames = games.any { it.mountStatus == MountStatus.MOUNTED }
                    val hasDetachedGames = games.any { it.mountStatus == MountStatus.DISK_DETACHED }

                    if (hasDetachedGames && !hasMountedGames) {
                        // Check if physical MicroSD has been re-inserted
                        val sdBase = runCatching { appPreferences.sdBasePath.first() }.getOrDefault("/data/sdext2")
                        val hasMmc = RootShell.exists("/dev/block/mmcblk0") || RootShell.exists("/sys/block/mmcblk0")
                        val hasCustomDiskAlive = games.filter { it.mountStatus == MountStatus.DISK_DETACHED }
                            .flatMap { it.mountPoints }
                            .any { mp -> mp.diskUuid != null && RootShell.execForOutput("blkid | grep -i \"${mp.diskUuid}\"").isNotBlank() }
                        if (hasMmc || hasCustomDiskAlive) {
                            AppLogger.info("SystemSyncMonitor", "Health loop detected storage reconnection! Initiating recovery...")
                            handleStorageMounted(context)
                        }
                    } else if (hasMountedGames) {
                        val sdBase = runCatching { appPreferences.sdBasePath.first() }.getOrDefault("/data/sdext2")
                        val isSdBaseMounted = RootShell.isMountpoint(sdBase)

                        // Passive Subsystem / Zygote Soft-Restart Integrity Check:
                        // If DB thinks games are MOUNTED, but either SD base or sample app namespace was dropped,
                        // trigger silent background remount!
                        if (!isSdBaseMounted) {
                            val hasMmc = RootShell.exists("/dev/block/mmcblk0") || RootShell.exists("/sys/block/mmcblk0")
                            if (hasMmc) {
                                AppLogger.warn("SystemSyncMonitor", "SD_BASE unmounted unexpectedly while games are active. Initiating auto-recovery...")
                                MountService.startMountAll(context)
                            }
                        } else {
                            val sampleGame = games.firstOrNull { it.mountStatus == MountStatus.MOUNTED && it.isEnabled }
                            if (sampleGame != null) {
                                val sampleTarget = when (sampleGame.mode) {
                                    app.mountx.data.model.MountMode.FILES -> "/storage/emulated/0/Android/data/${sampleGame.packageName}/files"
                                    app.mountx.data.model.MountMode.PKG -> "/storage/emulated/0/Android/data/${sampleGame.packageName}"
                                }
                                val isSampleMounted = RootShell.isMountpoint(sampleTarget) ||
                                        RootShell.isMountpoint("/mnt/runtime/default/emulated/0/Android/data/${sampleGame.packageName}") ||
                                        RootShell.isMountpoint("/data/media/0/Android/data/${sampleGame.packageName}")
                                if (!isSampleMounted) {
                                    AppLogger.warn("SystemSyncMonitor", "Canary mount missing for ${sampleGame.displayName} (soft restart detected). Initiating auto-recovery...")
                                    MountService.startMountAll(context)
                                }
                            }
                        }

                        val hasFlag = RootShell.exists("/dev/.mountx_disk_error")
                        val isDmesgError = if (!hasFlag) {
                            val dmesgRes = RootShell.exec("dmesg 2>/dev/null | tail -n 25 | grep -iE 'Buffer I/O error|DATCRCERR|CMDCRCERR|autok fail|autok error'")
                            dmesgRes.isSuccess && dmesgRes.output.isNotBlank()
                        } else false

                        // Check physical device presence: verify backing block device actually exists
                        val isDiskPhysicallyMissing = if (!hasFlag && !isDmesgError) {
                            val sdBase = runCatching { appPreferences.sdBasePath.first() }.getOrDefault("/data/sdext2")
                            val isSdBaseMounted = RootShell.isMountpoint(sdBase)
                            if (isSdBaseMounted) {
                                val backingBlock = RootShell.execForOutput("cat /proc/mounts 2>/dev/null").lines()
                                    .firstOrNull { it.contains(" $sdBase ") }
                                    ?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""
                                if (backingBlock.startsWith("/dev/block/")) {
                                    !RootShell.exists(backingBlock) && !RootShell.exists("/sys/class/block/${backingBlock.substringAfterLast('/')}")
                                } else false
                            } else {
                                val hasAnyMmc = RootShell.exists("/dev/block/mmcblk0") || RootShell.exists("/sys/block/mmcblk0")
                                val hasCustomDiskAlive = games.filter { it.mountStatus == MountStatus.MOUNTED }
                                    .flatMap { it.mountPoints }
                                    .any { mp -> mp.diskUuid != null && RootShell.execForOutput("blkid | grep -i \"${mp.diskUuid}\"").isNotBlank() }
                                !hasAnyMmc && !hasCustomDiskAlive
                            }
                        } else false

                        if (isDiskPhysicallyMissing) {
                            if (!previousErrorState) {
                                previousErrorState = true
                                handleEmergencyMediaEject()
                            }
                        } else if (hasFlag || isDmesgError) {
                            if (!previousErrorState) {
                                previousErrorState = true
                                handleDiskError("MicroSD", "Hardware I/O error or controller power cutoff")
                            }
                        } else {
                            if (previousErrorState) {
                                previousErrorState = false
                                _events.emit(SystemSyncEvent.DiskHealthRestored)
                                mountNotificationManager.syncActiveMountNotification()
                            }
                        }
                    } else {
                        previousErrorState = false
                    }
                } catch (e: Exception) {
                    AppLogger.error("SystemSyncMonitor", "Health loop error: ${e.message}")
                }
            }
        }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            healthMonitorJob?.cancel()
            healthMonitorJob = null
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
     * Differentiates between true MicroSD physical removal and transient USB OTG media unmounts.
     * Prevents false ejections of MicroSD mounts when USB OTG is plugged in or unplugged.
     */
    private suspend fun handleMediaUnmountOrEject(intent: Intent?) {
        val ejectedPath = intent?.data?.path?.trimEnd('/') ?: ""
        AppLogger.info("SystemSyncMonitor", "Storage unmount/eject broadcast received for path: '$ejectedPath'")

        val sdBase = runCatching { appPreferences.sdBasePath.first() }.getOrDefault("/data/sdext2")
        val isSdBaseMounted = RootShell.isMountpoint(sdBase)
        val isMmcStillAlive = RootShell.exists("/dev/block/mmcblk0") || RootShell.exists("/sys/block/mmcblk0")

        // 1. If primary MicroSD is actually physically detached or unmounted, trigger emergency protocol
        if (!isSdBaseMounted && !isMmcStillAlive) {
            AppLogger.warn("SystemSyncMonitor", "Primary MicroSD ($sdBase) is missing. Executing emergency eject.")
            handleEmergencyMediaEject()
            return
        }

        // 2. Primary MicroSD is still alive and mounted! Do NOT unmount /data/sdext2 or panic.
        if (ejectedPath.isNotBlank() && ejectedPath != sdBase) {
            try {
                val games = gameDao.getAllGamesSync()
                var anyDetached = false
                for (game in games) {
                    if (game.mountStatus != MountStatus.MOUNTED) continue
                    val hasMountOnEjected = game.mountPoints.any { mp ->
                        mp.enabled && (
                            mp.sourcePath.startsWith(ejectedPath) ||
                            (mp.diskUuid != null && ejectedPath.contains(mp.diskUuid, ignoreCase = true)) ||
                            (!RootShell.exists(mp.sourcePath) && mp.sourcePath.startsWith("/mnt/media_rw/"))
                        )
                    }
                    if (hasMountOnEjected) {
                        AppLogger.warn("SystemSyncMonitor", "Game ${game.displayName} depends on detached storage $ejectedPath. Safely unmounting...")
                        mountManager.unmountGame(game)
                        gameDao.updateMountStatus(game.packageName, MountStatus.DISK_DETACHED)
                        anyDetached = true
                    }
                }
                if (anyDetached) {
                    mountNotificationManager.syncActiveMountNotification()
                }
            } catch (e: Exception) {
                AppLogger.error("SystemSyncMonitor", "Error inspecting games on media eject: ${e.message}")
            }
        }

        // Refresh UI state so disk/game lists update cleanly without emergency disconnect banner
        _events.emit(SystemSyncEvent.RefreshAll)
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
            // Emergency eject is NOT a user-intentional unmount!
            // unmountAll() touches /dev/.mountx_unmounted to prevent auto-remount during explicit user unmount.
            // On physical ejection, we MUST remove this flag so that when the card is re-inserted,
            // auto-remount can proceed!
            RootShell.exec("rm -f /dev/.mountx_unmounted 2>/dev/null")
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
     * Emergency Protocol when MicroSD suffers hardware I/O errors or kernel power cutoff.
     * 1. Force-stop active game processes to prevent crash loops / freezing on blank screens.
     * 2. Lazy unmount broken bind mounts.
     * 3. Show Heads-Up Emergency Notification & update ongoing notification.
     * 4. Notify all ViewModels of DiskError event.
     */
    private suspend fun handleDiskError(diskName: String, details: String) {
        AppLogger.error("SystemSyncMonitor", "CRITICAL DISK ERROR: $diskName - $details")

        val sdBase = runCatching { appPreferences.sdBasePath.first() }.getOrDefault("/data/sdext2")

        // 1. Force-stop active game processes to prevent crash loops / freezing
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
            AppLogger.error("SystemSyncMonitor", "Failed to check/kill processes on disk error: ${e.message}")
        }

        // 2. Instant lazy unmount of bind points across namespaces to unblock Android system
        try {
            mountManager.unmountAll(sdBase)
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
            AppLogger.error("SystemSyncMonitor", "Failed lazy unmount on disk error: ${e.message}")
        }

        // 3. Post Heads-Up notification and sync status bar
        mountNotificationManager.postDiskErrorNotification(diskName, details)

        // 4. Emit event to UI ViewModels
        _events.emit(SystemSyncEvent.DiskError(diskName, details))
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

        // Clear unmounted flag as card was just inserted/connected
        RootShell.exec("rm -f /dev/.mountx_unmounted /dev/.mountx_disk_error 2>/dev/null")

        // Re-mount SD partition if not already mounted
        val isSdMounted = RootShell.exec("mountpoint -q \"$sdBase\" 2>/dev/null && echo YES").output.contains("YES")
        if (!isSdMounted) {
            AppLogger.info("SystemSyncMonitor", "SD not mounted, triggering MountService...")
            MountService.startMountAll(ctx)
            delay(5_000L) // give MountService time to work
        }

        // Re-bind all DISK_DETACHED / UNMOUNTED games
        var remountedCount = 0
        try {
            val games = gameDao.getAllGamesSync()
            for (game in games) {
                if (!game.isEnabled) continue
                if (game.mountStatus == MountStatus.DISK_DETACHED || game.mountStatus == MountStatus.MOUNTED || game.mountStatus == MountStatus.UNMOUNTED) {
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
