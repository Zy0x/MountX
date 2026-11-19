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

            // Never hold BroadcastReceiver.goAsync() open across long module boot waits (avoids BroadcastQueue ANR)
            try {
                MountService.startPostBootSync(context, isUnlock)
                return
            } catch (_: Exception) {
                // Fallback if OEM background FGS launch is restricted (e.g. USER_UNLOCKED before allowlist applies)
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    mountNotificationManager.createNotificationChannels()
                    val autoMount = appPreferences.autoMountOnBoot.first()
                    val registeredGames = gameDao.getAllGamesSync().filter { it.isEnabled }
                    if (registeredGames.isNotEmpty() && !autoMount) {
                        mountNotificationManager.showUnmountedGameWarningNotification(registeredGames.size)
                    } else {
                        mountNotificationManager.syncActiveMountNotification()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
