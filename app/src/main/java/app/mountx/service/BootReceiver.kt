package app.mountx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mountx.data.db.GameDao
import app.mountx.root.RootShell
import app.mountx.util.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var mountNotificationManager: MountNotificationManager

    @Inject
    lateinit var gameDao: GameDao

    companion object {
        @Volatile
        private var hasHandledBoot = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // Deduplicate: prevent running mount cascade multiple times across LOCKED_BOOT, USER_UNLOCKED, and BOOT_COMPLETED
            if (action != Intent.ACTION_MY_PACKAGE_REPLACED && hasHandledBoot) {
                return
            }
            if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_USER_UNLOCKED) {
                hasHandledBoot = true
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    mountNotificationManager.createNotificationChannels()
                    val autoMount = appPreferences.autoMountOnBoot.first()
                    val rootAvailable = RootShell.isRootAvailable()

                    // Check if Magisk / KernelSU / APatch module already handled boot mounts to prevent race conditions
                    val isModuleHandled = RootShell.exists("/dev/.mountx_booted")

                    if (autoMount && rootAvailable && !isModuleHandled) {
                        // Cold-boot settling grace period: wait for storage daemon & vold to become ready
                        var attempts = 0
                        while (attempts < 10) {
                            if (RootShell.exists("/storage/emulated/0") ||
                                RootShell.isMountpoint("/data/sdext2") ||
                                RootShell.isMountpoint("/dev/mountx/sdext2")
                            ) {
                                break
                            }
                            delay(500)
                            attempts++
                        }
                        MountService.startMountAll(context)
                    }

                    // Post-reboot unmounted game launch protection:
                    // If root is not available (typical of TempRoot after reboot) or auto-mount is off,
                    // warn the user so they do not open games before executing TempRoot & Mount All.
                    val registeredGames = gameDao.getAllGamesSync().filter { it.isEnabled }
                    if (registeredGames.isNotEmpty() && (!rootAvailable || !autoMount)) {
                        mountNotificationManager.showUnmountedGameWarningNotification(registeredGames.size)
                    }

                    mountNotificationManager.syncActiveMountNotification()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
