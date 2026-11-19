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
import app.mountx.data.model.MountPointCategory
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Executes emergency safeguards (process kill + lazy unmount + package suspend) upon sudden MicroSD ejection,
 * and Self-Healing Package Reconciler upon MicroSD reconnection.
 */
private var lastCanaryRecoveryTime = 0L

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

    @Volatile
    private var isRegistered = false
    private val receiverLock = Any()
    private val storageMountedMutex = Mutex()
    @Volatile
    private var lastMediaMountedMs = 0L
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
                    val now = System.currentTimeMillis()
                    if (now - lastMediaMountedMs < 4_000L) return
                    lastMediaMountedMs = now
                    scope.launch {
                        handleStorageMounted(ctx ?: context)
                    }
                }
                ACTION_DISK_ERROR -> {
                    val err = intent.getStringExtra("error") ?: context.getString(app.mountx.R.string.storage_err_hardware_io)
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
        synchronized(receiverLock) {
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
                    val mountedGames = games.filter { it.mountStatus == MountStatus.MOUNTED && it.isEnabled }
                    val hasMountedGames = mountedGames.isNotEmpty()
                    val hasDetachedGames = games.any { it.mountStatus == MountStatus.DISK_DETACHED }
                    val sdBase = runCatching { appPreferences.sdBasePath.first() }.getOrDefault("/data/sdext2")
                    val configuredBlock = runCatching { appPreferences.sdBlockDevice.first() }.getOrDefault("")

                    if (hasDetachedGames && !hasMountedGames) {
                        val detachedUuids = games.filter { it.mountStatus == MountStatus.DISK_DETACHED }
                            .flatMap { it.mountPoints }
                            .mapNotNull { it.diskUuid }
                        if (RootShell.isAnyExternalBackingDiskAlive(sdBase, configuredBlock, detachedUuids)) {
                            AppLogger.info("SystemSyncMonitor", "Health loop detected storage reconnection! Initiating recovery...")
                            handleStorageMounted(context)
                        }
                    } else if (hasMountedGames) {
                        val isSdBaseMounted = RootShell.isMountpoint(sdBase)
                        val mountedUuids = mountedGames.flatMap { it.mountPoints }.mapNotNull { it.diskUuid }

                        // Passive Subsystem / Zygote / Deep-Sleep Integrity Check:
                        // Verify EVERY mounted game across /proc/1/mountinfo, /mnt/user, POSIX dev:ino parity / canary, and Zygote namespace.
                        if (RootShell.exists("/dev/.mountx_unmounted") || RootShell.exists("/dev/.mountx_migrating")) {
                            // Intentional user unmount or active migration in progress: skip auto-recovery
                        } else if (!isSdBaseMounted) {
                            if (RootShell.isAnyExternalBackingDiskAlive(sdBase, configuredBlock, mountedUuids)) {
                                AppLogger.warn("SystemSyncMonitor", "SD_BASE unmounted unexpectedly while games are active. Initiating auto-recovery...")
                                MountService.startMountAll(context)
                            }
                        } else {
                            val verifiedMap = mountManager.verifyCanariesBatch(mountedGames)
                            val unverifiedGames = mountedGames.filter { verifiedMap[it.packageName] != true }
                            if (unverifiedGames.isNotEmpty()) {
                                // Wait for service.sh watchdog cycle if it is already recovering right now
                                RootShell.awaitServiceMountCycleIfActive(timeoutMillis = 15_000L, waitForWatchdogUnlockGrace = false)
                                val reVerifiedMap = mountManager.verifyCanariesBatch(unverifiedGames)
                                val stillMissing = unverifiedGames.filter { reVerifiedMap[it.packageName] != true }
                                if (stillMissing.isNotEmpty()) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastCanaryRecoveryTime > 20_000L) {
                                        lastCanaryRecoveryTime = now
                                        val missingNames = stillMissing.joinToString(", ") { it.displayName }
                                        AppLogger.warn("SystemSyncMonitor", "Live mount verification failed for [$missingNames] (deep-sleep/soft-restart/shadowing detected). Initiating auto-recovery...")
                                        MountService.startMountAll(context)
                                    }
                                }
                            }
                        }

                        val hasFlag = RootShell.exists("/dev/.mountx_disk_error")
                        val ioHealth = if (!hasFlag) {
                            RootShell.probeStorageIoHealth(sdBase, configuredBlock)
                        } else "IO_ERROR"

                        // Check physical device presence: verify backing block device actually exists
                        val isDiskPhysicallyMissing = if (!hasFlag) {
                            if (ioHealth == "MISSING") {
                                true
                            } else if (!isSdBaseMounted) {
                                !RootShell.isAnyExternalBackingDiskAlive(sdBase, configuredBlock, mountedUuids)
                            } else false
                        } else false

                        val isConfirmedDiskError = hasFlag || ioHealth == "IO_ERROR" || ioHealth == "RO"

                        if (isDiskPhysicallyMissing) {
                            if (!previousErrorState) {
                                previousErrorState = true
                                handleEmergencyMediaEject()
                            }
                        } else if (isConfirmedDiskError) {
                            if (!previousErrorState) {
                                previousErrorState = true
                                handleDiskError("MicroSD", "Hardware I/O error or read-only filesystem lock ($ioHealth)")
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
    }

    fun stopMonitoring() {
        synchronized(receiverLock) {
            if (!isRegistered) return
            try {
                healthMonitorJob?.cancel()
                healthMonitorJob = null
                context.unregisterReceiver(systemReceiver)
                isRegistered = false
            } catch (_: Exception) {}
        }
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
        val configuredBlock = runCatching { appPreferences.sdBlockDevice.first() }.getOrDefault("")
        val isSdBaseMounted = RootShell.isMountpoint(sdBase)
        val isPrimaryDiskAlive = RootShell.isAnyExternalBackingDiskAlive(sdBase, configuredBlock)

        // 1. If primary external storage is actually physically detached or unmounted, trigger emergency protocol
        if (!isSdBaseMounted && !isPrimaryDiskAlive) {
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
                        AppLogger.warn("SystemSyncMonitor", "Game ${game.displayName} depends on detached storage $ejectedPath. Safely unmounting & suspending...")
                        mountManager.unmountGame(game)
                        RootShell.exec("pm suspend \"${game.packageName}\" 2>/dev/null")
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
     * 1. Force-stop active game processes (am force-stop) and suspend package in Launcher (pm suspend).
     * 2. Execute instant lazy unmount (umount -f -l).
     * 3. Show Heads-Up Emergency Notification.
     * 4. Notify all ViewModels of StorageDisconnected.
     */
    private suspend fun handleEmergencyMediaEject() {
        AppLogger.warn("SystemSyncMonitor", "EMERGENCY: MicroSD Ejection detected! Executing protocol...")

        val sdBase = runCatching { appPreferences.sdBasePath.first() }.getOrDefault("/data/sdext2")

        // 1. Kill active game processes, suspend Launcher icon, and mark status as DISK_DETACHED
        try {
            val games = gameDao.getAllGamesSync()
            for (game in games) {
                val pgrep = RootShell.exec("pgrep -f \"${game.packageName}\" 2>/dev/null")
                if (pgrep.isSuccess && pgrep.output.isNotBlank()) {
                    RootShell.exec("am force-stop \"${game.packageName}\" 2>/dev/null")
                    AppLogger.warn("SystemSyncMonitor", "Force-stopped active process: ${game.packageName}")
                }
                if (game.mountStatus == MountStatus.MOUNTED || game.isEnabled) {
                    // Suspend package so icon stays visible in Android Launcher (greyed out / paused) instead of crashing
                    RootShell.exec("pm suspend \"${game.packageName}\" 2>/dev/null")
                    gameDao.updateMountStatus(game.packageName, MountStatus.DISK_DETACHED)
                }
            }
        } catch (e: Exception) {
            AppLogger.error("SystemSyncMonitor", "Failed to check/kill processes: ${e.message}")
        }

        // 2. Instant lazy unmount across all namespaces dynamically (no -f to protect surviving F2FS superblocks)
        try {
            mountManager.unmountAll(sdBase, isUserIntentional = false)
            RootShell.exec("rm -f /dev/.mountx_unmounted 2>/dev/null")
            RootShell.exec("umount \"$sdBase\" 2>/dev/null || umount -l \"$sdBase\" 2>/dev/null")
            if (sdBase != "/data/sdext2") {
                mountManager.unmountAll("/data/sdext2", isUserIntentional = false)
                RootShell.exec("umount /data/sdext2 2>/dev/null || umount -l /data/sdext2 2>/dev/null")
            }
            RootShell.execScript(
                """
                if [ -r /proc/1/mountinfo ]; then
                  awk '{p=${'$'}5; gsub(/\\040/," ",p); if (p ~ /\/Android\/data\/|\/Android\/obb\/|\/MountX\//) print length(p), p}' /proc/1/mountinfo 2>/dev/null
                else
                  awk '{p=${'$'}2; gsub(/\\040/," ",p); if (p ~ /\/Android\/data\/|\/Android\/obb\/|\/MountX\//) print length(p), p}' /proc/mounts 2>/dev/null
                fi | sort -rn -k1,1 | cut -d' ' -f2- | awk '!seen[${'$'}0]++' | while IFS= read -r tgt; do
                    [ -n "${'$'}tgt" ] || continue
                    umount "${'$'}tgt" 2>/dev/null || umount -l "${'$'}tgt" 2>/dev/null
                done
                """.trimIndent()
            )
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
     * 1. Verify live block/filesystem I/O actually failed (prevents false-positive dmesg/autok unmounts on deep-sleep wakeup).
     * 2. Force-stop active game processes to prevent crash loops / freezing on blank screens.
     * 3. Lazy unmount broken bind mounts WITHOUT setting /dev/.mountx_unmounted.
     * 4. Show Heads-Up Emergency Notification & update ongoing notification.
     * 5. Notify all ViewModels of DiskError event.
     */
    private suspend fun handleDiskError(diskName: String, details: String) {
        val sdBase = runCatching { appPreferences.sdBasePath.first() }.getOrDefault("/data/sdext2")
        val configuredBlock = runCatching { appPreferences.sdBlockDevice.first() }.getOrDefault("")
        val hasFlag = RootShell.exists("/dev/.mountx_disk_error")
        if (!hasFlag) {
            val liveProbe = RootShell.probeStorageIoHealth(sdBase, configuredBlock)
            if (liveProbe == "OK") {
                AppLogger.info("SystemSyncMonitor", "Ignored transient dmesg/autok warning on $diskName: live storage I/O probe is OK.")
                return
            }
        }

        AppLogger.error("SystemSyncMonitor", "CRITICAL DISK ERROR: $diskName - $details")

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
                    RootShell.exec("pm suspend \"${game.packageName}\" 2>/dev/null")
                    gameDao.updateMountStatus(game.packageName, MountStatus.DISK_DETACHED)
                }
            }
        } catch (e: Exception) {
            AppLogger.error("SystemSyncMonitor", "Failed to check/kill processes on disk error: ${e.message}")
        }

        // 2. Instant lazy unmount of bind points across namespaces to unblock Android system (no -f to prevent F2FS EIO shutdown)
        try {
            mountManager.unmountAll(sdBase, isUserIntentional = false)
            RootShell.exec("rm -f /dev/.mountx_unmounted 2>/dev/null")
            RootShell.execScript(
                """
                if [ -r /proc/1/mountinfo ]; then
                  awk '{p=${'$'}5; gsub(/\\040/," ",p); if (p ~ /\/Android\/data\/|\/Android\/obb\/|\/MountX\//) print length(p), p}' /proc/1/mountinfo 2>/dev/null
                else
                  awk '{p=${'$'}2; gsub(/\\040/," ",p); if (p ~ /\/Android\/data\/|\/Android\/obb\/|\/MountX\//) print length(p), p}' /proc/mounts 2>/dev/null
                fi | sort -rn -k1,1 | cut -d' ' -f2- | awk '!seen[${'$'}0]++' | while IFS= read -r tgt; do
                    [ -n "${'$'}tgt" ] || continue
                    umount "${'$'}tgt" 2>/dev/null || umount -l "${'$'}tgt" 2>/dev/null
                done
                """.trimIndent()
            )
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
     * 3. Run Self-Healing Package Reconciler for any full-app migrated package that was dropped during cold boot without MicroSD.
     * 4. Re-bind all games/apps that are in DISK_DETACHED state and unsuspend Launcher icons.
     * 5. Post a success notification in the status bar and notify ViewModels.
     */
    private suspend fun handleStorageMounted(ctx: Context) = storageMountedMutex.withLock {
        AppLogger.info("SystemSyncMonitor", "Storage connected. Waiting ${REMOUNT_SETTLE_MS}ms for volume to settle...")
        delay(REMOUNT_SETTLE_MS)

        val sdBase = runCatching { appPreferences.sdBasePath.first() }.getOrDefault("/data/sdext2")

        // Clear unmounted flag as card was just inserted/connected
        RootShell.exec("rm -f /dev/.mountx_unmounted /dev/.mountx_disk_error 2>/dev/null")

        // Re-mount SD partition if not already mounted
        val isSdMounted = RootShell.exec("mountpoint -q \"$sdBase\" 2>/dev/null && echo YES").output.contains("YES")
        if (!isSdMounted) {
            AppLogger.info("SystemSyncMonitor", "SD not mounted, triggering MountService...")
            MountService.startMountAll(ctx)
            delay(5_000L) // give MountService time to work
        }

        // Reconcile & Re-bind all DISK_DETACHED / UNMOUNTED games
        var remountedCount = 0
        try {
            val games = gameDao.getAllGamesSync()
            for (game in games) {
                if (!game.isEnabled) continue
                val pkg = game.packageName
                var activeGame = game

                // Self-Healing Package Reconciler:
                // If user rebooted the device while MicroSD was detached and 100% of the app (including base.apk) was on MicroSD,
                // PackageManagerService may have dropped the package entry. Automatically re-register from SD base.apk and sync UID!
                val pmCheck = RootShell.execForOutput("pm path \"$pkg\" 2>/dev/null | head -n 1").trim()
                if (pmCheck.isBlank()) {
                    val apkDirCandidates = listOfNotNull(
                        activeGame.mountPoints.firstOrNull {
                            it.resolveCategory() == MountPointCategory.APP_PACKAGE && !it.targetPath.contains("/lib")
                        }?.sourcePath,
                        "$sdBase/MountX/app/$pkg",
                        "$sdBase/Android/app/$pkg"
                    ).distinct()

                    val validSdApkDir = apkDirCandidates.firstOrNull { RootShell.exists("$it/base.apk") }
                    if (validSdApkDir != null) {
                        AppLogger.info("SystemSyncMonitor", "Self-Healing Reconciler: Re-registering detached package $pkg from $validSdApkDir...")
                        val installRes = RootShell.exec("pm install -r -d \"$validSdApkDir\"/*.apk 2>/dev/null")
                        if (installRes.isSuccess) {
                            val newApkPath = RootShell.execForOutput("pm path \"$pkg\" 2>/dev/null | head -n 1")
                                .trim().removePrefix("package:").trim()
                            val newApkParent = if (newApkPath.isNotBlank()) java.io.File(newApkPath).parent ?: "" else ""
                            val newUid = RootShell.execForOutput(
                                "pm list packages -U 2>/dev/null | grep -F \"package:$pkg\" | sed -n 's/.*uid:\\([0-9]*\\).*/\\1/p' | head -n 1"
                            ).trim().toIntOrNull()

                            if (newUid != null && newUid >= 10000) {
                                // Sync new UID ownership to Private Data & External Data on MicroSD so save data stays 100% intact
                                RootShell.exec("chown -R $newUid:$newUid \"$sdBase/MountX/data/$pkg\" \"/data/user/0/$pkg\" 2>/dev/null")
                                RootShell.exec("chown -R $newUid:1023 \"$sdBase/MountX/Android/data/$pkg\" \"$sdBase/MountX/Android/obb/$pkg\" 2>/dev/null")
                            }
                            if (newApkParent.isNotBlank()) {
                                // Clean newly created internal APK duplicate so bind-mount overlays cleanly without wasting internal storage
                                RootShell.exec("find \"$newApkParent\" -depth -mindepth 1 -delete 2>/dev/null")
                                val updatedPoints = activeGame.mountPoints.map { mp ->
                                    if (mp.resolveCategory() == MountPointCategory.APP_PACKAGE) {
                                        if (mp.targetPath.contains("/lib") || mp.id.contains("lib")) {
                                            mp.copy(targetPath = "$newApkParent/lib")
                                        } else {
                                            mp.copy(targetPath = newApkParent)
                                        }
                                    } else mp
                                }
                                activeGame = activeGame.copy(mountPoints = updatedPoints)
                                gameDao.updateGame(activeGame)
                            }
                            AppLogger.success("SystemSyncMonitor", "Self-Healing Reconciler succeeded for $pkg (newUid=$newUid, apkDir=$newApkParent)")
                        } else {
                            AppLogger.warn("SystemSyncMonitor", "Self-Healing Reconciler pm install failed for $pkg: ${installRes.output}")
                        }
                    }
                }

                if (activeGame.mountStatus == MountStatus.DISK_DETACHED || activeGame.mountStatus == MountStatus.MOUNTED || activeGame.mountStatus == MountStatus.UNMOUNTED) {
                    val result = mountManager.mountGame(activeGame, sdBase)
                    if (result.isSuccess) {
                        RootShell.exec("pm unsuspend \"$pkg\" 2>/dev/null")
                        gameDao.updateMountStatus(pkg, MountStatus.MOUNTED)
                        remountedCount++
                        AppLogger.success("SystemSyncMonitor", "Auto-remounted & unsuspended: ${activeGame.displayName}")
                    } else {
                        AppLogger.warn("SystemSyncMonitor", "Failed to remount: ${activeGame.displayName}")
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
