package app.mountx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mountx.util.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

import app.mountx.root.RootShell
import kotlinx.coroutines.delay

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var mountNotificationManager: MountNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    mountNotificationManager.createNotificationChannels()
                    val autoMount = appPreferences.autoMountOnBoot.first()
                    if (autoMount) {
                        // Cold-boot settling grace period: wait for storage daemon & vold to become ready
                        var attempts = 0
                        while (attempts < 10) {
                            if (RootShell.exists("/storage/emulated/0") ||
                                RootShell.isMountpoint("/data/sdext2")
                            ) {
                                break
                            }
                            delay(500)
                            attempts++
                        }
                        MountService.startMountAll(context)
                    }
                    mountNotificationManager.syncActiveMountNotification()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
