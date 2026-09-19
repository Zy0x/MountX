package app.mountx.ui.settings
 
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountx.root.RootShell
import app.mountx.util.AppPreferences
import app.mountx.util.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = appPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val language: StateFlow<String> = appPreferences.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val sdBasePath: StateFlow<String> = appPreferences.sdBasePath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "/data/sdext2")

    val sdBlockDevice: StateFlow<String> = appPreferences.sdBlockDevice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "/dev/block/mmcblk0p3")

    val autoMountOnBoot: StateFlow<Boolean> = appPreferences.autoMountOnBoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appPreferences.setThemeMode(mode) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            appPreferences.setLanguage(lang)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
                    localeManager?.applicationLocales = android.os.LocaleList.forLanguageTags(lang)
                } catch (_: Exception) {}
            }
        }
    }

    fun setSdBasePath(path: String) {
        viewModelScope.launch { appPreferences.setSdBasePath(path) }
    }

    fun setSdBlockDevice(device: String) {
        viewModelScope.launch { appPreferences.setSdBlockDevice(device) }
    }

    fun setAutoMountOnBoot(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setAutoMountOnBoot(enabled) }
    }

    fun resetSettings() {
        viewModelScope.launch { appPreferences.resetDefaults() }
    }
}
