package app.mihon.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mihon.root.RootShell
import app.mihon.util.AppPreferences
import app.mihon.util.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
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
        viewModelScope.launch { appPreferences.setLanguage(lang) }
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
