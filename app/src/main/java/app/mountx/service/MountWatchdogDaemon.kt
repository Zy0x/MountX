package app.mountx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.mountx.data.db.GameDao
import app.mountx.data.model.MountStatus
import androidx.core.content.ContextCompat
import app.mountx.root.MountManager
import app.mountx.root.RootShell
import app.mountx.util.AppLogger
import app.mountx.util.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passive, event-driven watchdog daemon.
 * Avoids battery drain by eliminating high-frequency polling.
 * Monitors SCREEN_ON, MEDIA_EJECT, and pre-launch hooks to ensure mount integrity and kernel safety.
 */
@Singleton
class MountWatchdogDaemon @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val mountManager: MountManager,
    private val appPreferences: AppPreferences
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRegistered = false
    private var lastScreenOnCheckMillis = 0L
    private val SCREEN_ON_THROTTLE_MS = 30_000L // 30 seconds debounce

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    // Screen woke up: passive integrity check throttled to max once per 30s
                    // Also catches post-zygote-restart scenarios where new processes
                    // may not have inherited the bind mount namespaces.
                    val now = System.currentTimeMillis()
                    if (now - lastScreenOnCheckMillis >= SCREEN_ON_THROTTLE_MS) {
                        lastScreenOnCheckMillis = now
                        scope.launch {
                            checkAndRemountCanaries()
                        }
                    }
                }
                Intent.ACTION_MEDIA_MOUNTED -> {
                    // External storage just connected: immediate canary check to catch
                    // hot-plug scenarios (SystemSyncMonitor handles the full remount;
                    // we do a quick canary pass here for fast recovery of namespace mounts)
                    scope.launch {
                        AppLogger.info("Watchdog", "MEDIA_MOUNTED received, running immediate canary check...")
                        checkAndRemountCanaries()
                    }
                }
                Intent.ACTION_MEDIA_EJECT,
                Intent.ACTION_MEDIA_UNMOUNTED -> {
                    scope.launch {
                        val sdBase = appPreferences.sdBasePath.first()
                        val isSdBaseMounted = RootShell.isMountpoint(sdBase)
                        val configuredDevice = appPreferences.sdBlockDevice.first().trim()
                        val parentDev = configuredDevice.removePrefix("/dev/block/").replace(Regex("p[0-9]+$"), "")
                        val isConfiguredAlive = configuredDevice.isNotBlank() && (
                            RootShell.exists(configuredDevice) ||
                            (parentDev.isNotBlank() && (RootShell.exists("/dev/block/$parentDev") || RootShell.exists("/sys/block/$parentDev")))
                        )
                        val isGenericDeviceAlive = RootShell.exists("/dev/block/mmcblk0") || RootShell.exists("/sys/block/mmcblk0") ||
                            RootShell.exists("/dev/block/mmcblk1") || RootShell.exists("/sys/block/mmcblk1") ||
                            RootShell.exists("/dev/block/sda") || RootShell.exists("/sys/block/sda") ||
                            RootShell.exists("/dev/block/sdb") || RootShell.exists("/sys/block/sdb")
                        val isDeviceStillAlive = isConfiguredAlive || isGenericDeviceAlive

                        if (!isSdBaseMounted && !isDeviceStillAlive) {
                            AppLogger.warn("Watchdog", "MicroSD physically detached. Executing emergency lazy unmount.")
                            emergencyLazyUnmount()
                        } else {
                            AppLogger.info("Watchdog", "Media unmount event received, but MicroSD ($sdBase) is still mounted & active. Preserving mounts.")
                        }
                    }
                }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    val pkg = intent.data?.schemeSpecificPart
                    if (!pkg.isNullOrBlank()) {
                        scope.launch {
                            handlePackageRemoved(pkg)
                        }
                    }
                }
            }
        }
    }

    /**
     * Start the passive event-driven watchdog listener with Android 14/15 export flags.
     */
    fun start() {
        if (isRegistered) return
        val screenFilter = IntentFilter(Intent.ACTION_SCREEN_ON)
        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addDataScheme("file")
        }
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        try {
            ContextCompat.registerReceiver(
                context,
                eventReceiver,
                screenFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            ContextCompat.registerReceiver(
                context,
                eventReceiver,
                mediaFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            ContextCompat.registerReceiver(
                context,
                eventReceiver,
                pkgFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isRegistered = true
            AppLogger.info("Watchdog", "Passive integrity watchdog registered (screen, media, pkg).")
        } catch (e: Exception) {
            AppLogger.error("Watchdog", "Failed to register watchdog receiver: ${e.message}")
        }
    }

    /**
     * Stop the watchdog receiver.
     */
    fun stop() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(eventReceiver)
            isRegistered = false
            AppLogger.info("Watchdog", "Passive integrity watchdog stopped.")
        } catch (e: Exception) {
            AppLogger.error("Watchdog", "Error stopping watchdog: ${e.message}")
        }
    }

    /**
     * Passive canary check: verifies whether mounted games still have valid canary visibility.
     * Re-mounts if Android system_server / vold dropped the mount during deep sleep.
     * Accurately supports both modular multi-mount points and legacy entries without OBB false-positives.
     */
    suspend fun checkAndRemountCanaries() {
        if (RootShell.exists("/dev/.mountx_unmounted")) {
            AppLogger.info("Watchdog", "User intentional unmount active (/dev/.mountx_unmounted exists), skipping auto-remount.")
            return
        }
        val games = gameDao.getAllGamesSync()
        val mountedGames = games.filter { it.isEnabled && it.mountStatus == MountStatus.MOUNTED }
        if (mountedGames.isEmpty()) return
        val sdBase = appPreferences.sdBasePath.first()
        val activeMounts = mountManager.getActiveMountpointsSet()
        val canaryResults = mountManager.verifyCanariesBatch(mountedGames)

        for (game in mountedGames) {
            val hasCanary = canaryResults[game.packageName] ?: false
            if (!hasCanary) {
                val isAlreadyMounted = if (game.mountPoints.isNotEmpty()) {
                    val activeConfigs = game.mountPoints.filter { it.enabled }
                    activeConfigs.isNotEmpty() && activeConfigs.all { mp ->
                        activeMounts.contains(mp.targetPath) ||
                        activeMounts.any { it.endsWith(MountManager.extractRelativePath(mp.targetPath)) } ||
                        RootShell.isMountpoint(mp.targetPath)
                    }
                } else {
                    val dataTarget = "/storage/emulated/0/Android/data/${game.packageName}"
                    val isDataMounted = activeMounts.contains(dataTarget) ||
                            RootShell.isMountpoint(dataTarget) ||
                            RootShell.isMountpoint("/mnt/runtime/default/emulated/0/Android/data/${game.packageName}")

                    val obbSrc = "$sdBase/Android/obb/${game.packageName}"
                    val hasObbOnSd = RootShell.exists(obbSrc)
                    if (hasObbOnSd) {
                        val obbTarget = "/storage/emulated/0/Android/obb/${game.packageName}"
                        val isObbMounted = activeMounts.contains(obbTarget) ||
                                RootShell.isMountpoint(obbTarget) ||
                                RootShell.isMountpoint("/mnt/runtime/default/emulated/0/Android/obb/${game.packageName}")
                        isDataMounted && isObbMounted
                    } else {
                        isDataMounted
                    }
                }

                if (!isAlreadyMounted) {
                    AppLogger.warn("Watchdog", "Canary lost and mount not active for ${game.displayName} (${game.packageName}), re-mounting...")
                    val result = mountManager.mountGame(game, sdBase)
                    if (result.isSuccess) {
                        AppLogger.success("Watchdog", "Auto-remounted ${game.displayName} successfully.")
                    } else {
                        AppLogger.error("Watchdog", "Failed auto-remount for ${game.displayName}")
                    }
                } else {
                    AppLogger.info("Watchdog", "Mountpoint still active for ${game.displayName}, skipping duplicate mount.")
                }
            }
        }
    }

    /**
     * Emergency unmount when MicroSD is ejected or unmounted by the system.
     */
    suspend fun emergencyLazyUnmount() {
        AppLogger.warn("Watchdog", "MicroSD ejection detected! Performing emergency lazy unmount...")
        val sdBase = appPreferences.sdBasePath.first()
        mountManager.unmountAll(sdBase)
        val games = gameDao.getAllGamesSync()
        for (g in games) {
            if (g.mountStatus == MountStatus.MOUNTED) {
                gameDao.updateMountStatus(g.packageName, MountStatus.DISK_DETACHED)
            }
        }
        AppLogger.success("Watchdog", "Emergency lazy unmount completed.")
    }

    /**
     * Handle uninstalled package by removing stale mounts.
     */
    private suspend fun handlePackageRemoved(packageName: String) {
        val game = gameDao.getGameByPackage(packageName) ?: return
        AppLogger.info("Watchdog", "Registered game $packageName was uninstalled from Android.")
        mountManager.unmountGame(game)
        gameDao.updateMountStatus(packageName, MountStatus.UNMOUNTED)
    }

    /**
     * Pre-launch hook: verifies game mount status and elevates I/O priority before game opens.
     */
    suspend fun prepareGameForLaunch(packageName: String): Boolean {
        val game = gameDao.getGameByPackage(packageName) ?: return true
        val sdBase = appPreferences.sdBasePath.first()

        if (game.isEnabled) {
            val isCanaryOk = mountManager.verifyCanary(game.packageName, game)
            if (!isCanaryOk || game.mountStatus != MountStatus.MOUNTED) {
                val mountRes = mountManager.mountGame(game, sdBase)
                if (mountRes.isSuccess) {
                    gameDao.updateMountStatus(packageName, MountStatus.MOUNTED)
                }
            }
            // Apply real-time / high priority I/O tuning
            mountManager.boostGameIo(packageName)
        }
        return true
    }
}
