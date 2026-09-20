package app.mountx

import android.app.Application
import app.mountx.root.ModuleManager
import app.mountx.service.MountWatchdogDaemon
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class for MountX.
 * Initializes libsu Shell with root access configuration.
 */
@HiltAndroidApp
class MountXApp : Application() {

    @Inject
    lateinit var watchdogDaemon: MountWatchdogDaemon

    override fun onCreate() {
        super.onCreate()
        // Configure libsu for root shell access
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR or Shell.FLAG_MOUNT_MASTER)
                .setTimeout(30)
        )

        // Start passive event-driven watchdog daemon
        watchdogDaemon.start()

        // Asynchronously check and synchronize Magisk/KernelSU/APatch module silently
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ModuleManager.checkAndSyncModuleSilently(this@MountXApp)
            } catch (_: Exception) {}
        }
    }
}
