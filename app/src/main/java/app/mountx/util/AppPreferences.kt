package app.mountx.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mountx_settings")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_SD_BASE_PATH = stringPreferencesKey("sd_base_path")
        val KEY_SD_BLOCK_DEVICE = stringPreferencesKey("sd_block_device")
        val KEY_AUTO_MOUNT = booleanPreferencesKey("auto_mount_on_boot")
        val KEY_IO_TWEAKS_ENABLED = booleanPreferencesKey("io_tweaks_enabled")
        val KEY_IO_PRESET = stringPreferencesKey("io_preset")
        val KEY_IO_READ_AHEAD_KB = androidx.datastore.preferences.core.intPreferencesKey("io_read_ahead_kb")
        val KEY_IO_SCHEDULER = stringPreferencesKey("io_scheduler")
        val KEY_BASELINE_READ_AHEAD_KB = androidx.datastore.preferences.core.intPreferencesKey("baseline_read_ahead_kb")
        val KEY_BASELINE_SCHEDULER = stringPreferencesKey("baseline_scheduler")
        val KEY_BASELINE_CAPTURED = booleanPreferencesKey("baseline_captured")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val language: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: run {
            val sysLang = java.util.Locale.getDefault().language
            if (sysLang.equals("in", ignoreCase = true) || sysLang.equals("id", ignoreCase = true)) "id" else "en"
        }
    }

    val sdBasePath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SD_BASE_PATH] ?: "/data/sdext2"
    }

    val sdBlockDevice: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SD_BLOCK_DEVICE] ?: "/dev/block/mmcblk0p3"
    }

    val autoMountOnBoot: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_MOUNT] ?: true
    }

    val ioTweaksEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IO_TWEAKS_ENABLED] ?: true
    }

    val ioPreset: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_IO_PRESET] ?: "GAMING_ULTRA"
    }

    val ioReadAheadKb: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_IO_READ_AHEAD_KB] ?: 2048
    }

    val ioScheduler: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_IO_SCHEDULER] ?: "none"
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun setSdBasePath(path: String) {
        context.dataStore.edit { it[KEY_SD_BASE_PATH] = path }
    }

    suspend fun setSdBlockDevice(device: String) {
        context.dataStore.edit { it[KEY_SD_BLOCK_DEVICE] = device }
    }

    suspend fun setAutoMountOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_MOUNT] = enabled }
    }

    suspend fun setIoTweaksEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IO_TWEAKS_ENABLED] = enabled }
    }

    suspend fun setIoPreset(preset: String) {
        context.dataStore.edit { it[KEY_IO_PRESET] = preset }
    }

    suspend fun setIoReadAheadKb(kb: Int) {
        context.dataStore.edit { it[KEY_IO_READ_AHEAD_KB] = kb }
    }

    suspend fun setIoScheduler(scheduler: String) {
        context.dataStore.edit { it[KEY_IO_SCHEDULER] = scheduler }
    }

    val baselineCaptured: Flow<Boolean> = context.dataStore.data.map { it[KEY_BASELINE_CAPTURED] ?: false }
    val baselineReadAheadKb: Flow<Int> = context.dataStore.data.map { it[KEY_BASELINE_READ_AHEAD_KB] ?: 128 }
    val baselineScheduler: Flow<String> = context.dataStore.data.map { it[KEY_BASELINE_SCHEDULER] ?: "" }

    suspend fun saveBaselineIfFirstTime(readAheadKb: Int, scheduler: String) {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_BASELINE_CAPTURED] != true) {
                prefs[KEY_BASELINE_CAPTURED] = true
                prefs[KEY_BASELINE_READ_AHEAD_KB] = readAheadKb
                prefs[KEY_BASELINE_SCHEDULER] = scheduler
            }
        }
    }

    suspend fun resetDefaults() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}

