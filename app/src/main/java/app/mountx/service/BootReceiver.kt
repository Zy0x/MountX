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

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val autoMount = appPreferences.autoMountOnBoot.first()
                if (autoMount) {
                    MountService.startMountAll(context)
                }
            }
        }
    }
}
