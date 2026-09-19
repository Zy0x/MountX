package app.mountx

import android.app.Application
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for MountX.
 * Initializes libsu Shell with root access configuration.
 */
@HiltAndroidApp
class MountXApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Configure libsu for root shell access
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(30)
        )
    }
}
