package app.mountx

import android.app.Application
import app.mountx.root.ModuleManager
import app.mountx.root.RootDetector
import app.mountx.service.MountNotificationManager
import app.mountx.service.MountWatchdogDaemon
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first

/**
 * Application class for MountX.
 * Initializes libsu Shell with root access configuration.
 */
@HiltAndroidApp
class MountXApp : Application() {

    @Inject
    lateinit var watchdogDaemon: MountWatchdogDaemon

    @Inject
    lateinit var mountNotificationManager: MountNotificationManager

    @Inject
    lateinit var appDatabase: app.mountx.data.db.AppDatabase

    @Inject
    lateinit var appPreferences: app.mountx.util.AppPreferences

    class ShellInitializer : Shell.Initializer() {
        override fun onInit(context: android.content.Context, shell: Shell): Boolean {
            shell.newJob().add("case \":\$PATH:\" in *\":/data/adb/modules/MountX/bin:\"*) ;; *) export PATH=\"/data/adb/modules/MountX/bin:/data/adb/modules/mountx/bin:/data/adb/ap/modules/MountX/bin:/data/adb/ksu/modules/MountX/bin:/data/adb/ap/bin:/data/adb/magisk:\$PATH\" ;; esac").exec()
            return true
        }
    }

    companion object {
        lateinit var instance: MountXApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Configure libsu for root shell access
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR or Shell.FLAG_MOUNT_MASTER)
                .setTimeout(30)
                .setInitializers(ShellInitializer::class.java)
        )

        // Initialize notification channels & sync status bar notification
        mountNotificationManager.createNotificationChannels()

        // [COLD-START] Pre-warm critical subsystems in parallel BEFORE watchdog
        // so the first UI interaction is already fast:
        // 1. libsu root shell spawn (most expensive: 200-800ms)
        // 2. Room DB connection warmup (SQLite WAL init)
        // 3. DataStore first-read warm (disk I/O, Protobuf decode)
        // 4. RootDetector cache population
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fire all warmups concurrently
                val shellWarm = async {
                    runCatching { Shell.getShell() }
                }
                val dbWarm = async {
                    runCatching { appDatabase.openHelper.readableDatabase }
                }
                val prefsWarm = async {
                    runCatching { appPreferences.sdBasePath.first() }
                }
                // Wait for shell to be ready, then populate RootDetector cache
                shellWarm.await()
                dbWarm.await()
                prefsWarm.await()
                // Pre-cache root solution so dashboard doesn't wait
                runCatching { RootDetector.getRootAndModuleInfo(forceRefresh = false) }
            } catch (_: Exception) {}
        }

        // Start passive event-driven watchdog daemon
        watchdogDaemon.start()

        // Asynchronously check and synchronize Magisk/KernelSU/APatch module silently
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ModuleManager.checkAndSyncModuleSilently(this@MountXApp)
                mountNotificationManager.syncActiveMountNotification()
            } catch (_: Exception) {}
        }
    }
}
