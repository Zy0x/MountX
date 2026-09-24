package app.mountx

import android.app.Application
import app.mountx.root.ModuleManager
import app.mountx.service.MountNotificationManager
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

    @Inject
    lateinit var mountNotificationManager: MountNotificationManager

    override fun onCreate() {
        super.onCreate()
        // Configure libsu for root shell access
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR or Shell.FLAG_MOUNT_MASTER)
                .setTimeout(30)
        )

        // Initialize notification channels & sync status bar notification
        mountNotificationManager.createNotificationChannels()

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
