package app.mountx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
                    // Storage disconnected: emergency lazy unmount to prevent kernel panic
                    scope.launch {
                        emergencyLazyUnmount()
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
     * Start the passive event-driven watchdog listener.
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
            context.registerReceiver(eventReceiver, screenFilter)
            context.registerReceiver(eventReceiver, mediaFilter)
            context.registerReceiver(eventReceiver, pkgFilter)
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
     */
    suspend fun checkAndRemountCanaries() {
        val games = gameDao.getAllGames().firstOrNull() ?: return
        val sdBase = appPreferences.sdBasePath.first()

        for (game in games) {
            if (game.isEnabled && game.mountStatus == MountStatus.MOUNTED) {
                val hasCanary = mountManager.verifyCanary(game.packageName, game)
                if (!hasCanary) {
                    val isAlreadyMounted = (RootShell.isMountpoint("/storage/emulated/0/Android/data/${game.packageName}") ||
                            RootShell.isMountpoint("/mnt/runtime/default/emulated/0/Android/data/${game.packageName}")) &&
                            (RootShell.isMountpoint("/storage/emulated/0/Android/obb/${game.packageName}") ||
                            RootShell.isMountpoint("/mnt/runtime/default/emulated/0/Android/obb/${game.packageName}"))
                    if (!isAlreadyMounted) {
                        AppLogger.warn("Watchdog", "Canary lost for ${game.displayName} (${game.packageName}), re-mounting...")
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
    }

    /**
     * Emergency unmount when MicroSD is ejected or unmounted by the system.
     */
    suspend fun emergencyLazyUnmount() {
        AppLogger.warn("Watchdog", "MicroSD ejection detected! Performing emergency lazy unmount...")
        val sdBase = appPreferences.sdBasePath.first()
        mountManager.unmountAll(sdBase)
        val games = gameDao.getAllGames().firstOrNull() ?: emptyList()
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
