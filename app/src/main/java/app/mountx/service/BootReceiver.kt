package app.mountx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mountx.data.db.GameDao
import app.mountx.data.model.MountStatus
import app.mountx.data.repository.AppRepository
import app.mountx.root.MountManager
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

    @Inject
    lateinit var mountManager: MountManager

    @Inject
    lateinit var appRepository: AppRepository

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
            val isUnlock = action == Intent.ACTION_USER_UNLOCKED
            // Allow ACTION_USER_UNLOCKED to execute even if boot completed was already recorded
            if (action != Intent.ACTION_MY_PACKAGE_REPLACED && !isUnlock && hasHandledBoot) {
                return
            }
            if (action == Intent.ACTION_BOOT_COMPLETED || isUnlock) {
                hasHandledBoot = true
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    mountNotificationManager.createNotificationChannels()
                    val autoMount = appPreferences.autoMountOnBoot.first()
                    val rootAvailable = RootShell.isRootAvailable()

                    // Coordinate cleanly with service.sh so boot/unlock mounts never race or double-bind
                    if (autoMount && rootAvailable) {
                        RootShell.awaitServiceMountCycleIfActive(waitForWatchdogUnlockGrace = isUnlock)
                        appRepository.refreshMountStatuses()

                        val isModuleHandled = RootShell.exists("/dev/.mountx_booted")
                        val isInitialMounted = RootShell.exists("/dev/.mountx_initial_mounted")
                        val enabledGames = gameDao.getAllGamesSync().filter { it.isEnabled }
                        val verifiedMap = if (enabledGames.isNotEmpty()) {
                            mountManager.verifyCanariesBatch(enabledGames)
                        } else emptyMap()
                        val anyEligibleUnmounted = enabledGames.any { g ->
                            g.mountStatus != MountStatus.DISK_DETACHED && verifiedMap[g.packageName] != true
                        }

                        if (!isModuleHandled || (isUnlock && !isInitialMounted) || anyEligibleUnmounted) {
                            // Settling grace period: wait for storage daemon & vold when app is handling mounts
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
