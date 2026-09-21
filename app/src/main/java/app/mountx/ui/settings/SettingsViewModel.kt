package app.mountx.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountx.data.model.AppEntry
import app.mountx.data.model.ConflictStrategy
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.MountStatus
import app.mountx.data.repository.AppRepository
import app.mountx.data.rescue.EmergencyRescueManager
import app.mountx.root.RootShell
import app.mountx.util.AppLogger
import app.mountx.util.AppPreferences
import app.mountx.util.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

enum class ImportMode {
    MERGE,
    REPLACE_ALL
}

data class SnapshotValidationSummary(
    val schemaVersion: Int,
    val appCount: Int,
    val deviceModel: String,
    val exportedAt: Long,
    val appNames: List<String>
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val appRepository: AppRepository,
    private val emergencyRescueManager: EmergencyRescueManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isExecutingRescue = MutableStateFlow(false)
    val isExecutingRescue: StateFlow<Boolean> = _isExecutingRescue.asStateFlow()

    private val _rescueMessage = MutableStateFlow<String?>(null)
    val rescueMessage: StateFlow<String?> = _rescueMessage.asStateFlow()

    // ── Configuration Portability State ──
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _portabilityMessage = MutableStateFlow<String?>(null)
    val portabilityMessage: StateFlow<String?> = _portabilityMessage.asStateFlow()

    private val _pendingImportSummary = MutableStateFlow<SnapshotValidationSummary?>(null)
    val pendingImportSummary: StateFlow<SnapshotValidationSummary?> = _pendingImportSummary.asStateFlow()

    private val _pendingImportUri = MutableStateFlow<Uri?>(null)
    val pendingImportUri: StateFlow<Uri?> = _pendingImportUri.asStateFlow()

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

    val conflictStrategy: StateFlow<ConflictStrategy> = appPreferences.conflictStrategy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConflictStrategy.OVERWRITE)

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

    fun setConflictStrategy(strategy: ConflictStrategy) {
        viewModelScope.launch { appPreferences.setConflictStrategy(strategy) }
    }

    fun resetSettings() {
        viewModelScope.launch { appPreferences.resetDefaults() }
    }

    fun executeEmergencyReset() {
        viewModelScope.launch {
            _isExecutingRescue.value = true
            val result = emergencyRescueManager.executeEmergencyReset()
            _isExecutingRescue.value = false
            if (result.isSuccess) {
                _rescueMessage.value = "Emergency reset completed. All mounts detached and permissions restored."
            } else {
                _rescueMessage.value = "Emergency reset failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun generateRescueScript() {
        viewModelScope.launch {
            val result = emergencyRescueManager.generateRescueScript()
            if (result.isSuccess) {
                _rescueMessage.value = "Rescue script created at ${result.getOrNull()}"
            } else {
                _rescueMessage.value = "Failed to create rescue script: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearRescueMessage() {
        _rescueMessage.value = null
    }

    fun clearPortabilityMessage() {
        _portabilityMessage.value = null
    }

    fun clearPendingImport() {
        _pendingImportSummary.value = null
        _pendingImportUri.value = null
    }

    // ── Configuration Portability (Full Snapshot JSON v2) ──

    fun exportFullSnapshot(uri: Uri) {
        viewModelScope.launch {
            _isExporting.value = true
            runCatching {
                val currentSdBase = appPreferences.sdBasePath.first()
                val currentSdBlock = appPreferences.sdBlockDevice.first()
                val autoBoot = appPreferences.autoMountOnBoot.first()
                val conflict = appPreferences.conflictStrategy.first()
                val lang = appPreferences.language.first()
                val theme = appPreferences.themeMode.first()
                val apps = appRepository.observeApps().first()

                val rootObj = JSONObject().apply {
                    put("schemaVersion", 2)
                    put("exportTimestamp", System.currentTimeMillis())
                    put("appVersion", app.mountx.BuildConfig.VERSION_NAME)
                    put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")

                    val prefsObj = JSONObject().apply {
                        put("sdBasePath", currentSdBase)
                        put("sdBlockDevice", currentSdBlock)
                        put("autoMountOnBoot", autoBoot)
                        put("conflictStrategy", conflict.name)
                        put("language", lang)
                        put("themeMode", theme.name)
                    }
                    put("preferences", prefsObj)

                    val appsArr = JSONArray()
                    apps.forEach { a ->
                        val appObj = JSONObject().apply {
                            put("packageName", a.packageName)
                            put("displayName", a.displayName)
                            put("mode", a.mode.name)
                            put("isEnabled", a.isEnabled)
                            put("dataSizeBytes", a.dataSizeBytes)
                            if (a.preferredDiskUuid != null) put("preferredDiskUuid", a.preferredDiskUuid)

                            val mpsArr = JSONArray()
                            a.mountPoints.forEach { mp ->
                                val mpObj = JSONObject().apply {
                                    put("id", mp.id)
                                    put("category", mp.category.name)
                                    val relSource = if (mp.sourcePath.startsWith(currentSdBase)) {
                                        mp.sourcePath.removePrefix(currentSdBase).removePrefix("/")
                                    } else {
                                        mp.sourcePath
                                    }
                                    put("relativeSourcePath", relSource)
                                    put("sourcePath", mp.sourcePath)
                                    put("targetPath", mp.targetPath)
                                    put("enabled", mp.enabled)
                                    put("isVirtualContainer", mp.isVirtualContainer)
                                    if (mp.containerImgPath != null) put("containerImgPath", mp.containerImgPath)
                                    if (mp.diskUuid != null) put("diskUuid", mp.diskUuid)
                                    if (mp.label != null) put("label", mp.label)
                                    put("preserveMedia", mp.preserveMedia)
                                }
                                mpsArr.put(mpObj)
                            }
                            put("mountPoints", mpsArr)
                        }
                        appsArr.put(appObj)
                    }
                    put("apps", appsArr)
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(rootObj.toString(2).toByteArray())
                    }
                }
                _portabilityMessage.value = "SNAPSHOT_EXPORT_OK"
            }.onFailure { ex ->
                AppLogger.error("Portability", "Snapshot export failed: ${ex.message}")
                _portabilityMessage.value = ex.message ?: "Export failed"
            }
            _isExporting.value = false
        }
    }

    fun validateAndPrepareImport(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    }
                } ?: error("Unable to open file")

                val trimmed = content.trim()
                if (!trimmed.startsWith("{")) {
                    error("Invalid JSON format")
                }

                val rootObj = JSONObject(trimmed)
                val schemaVersion = rootObj.optInt("schemaVersion", 1)
                val exportedAt = rootObj.optLong("exportTimestamp", 0L)
                val deviceModel = rootObj.optString("deviceModel", "Unknown Device")
                val appsArr = rootObj.optJSONArray("apps") ?: rootObj.optJSONArray("games") ?: JSONArray()
                val appNames = mutableListOf<String>()

                for (i in 0 until appsArr.length()) {
                    val obj = appsArr.getJSONObject(i)
                    val name = obj.optString("displayName", obj.optString("packageName", "App"))
                    appNames.add(name)
                }

                _pendingImportSummary.value = SnapshotValidationSummary(
                    schemaVersion = schemaVersion,
                    appCount = appsArr.length(),
                    deviceModel = deviceModel,
                    exportedAt = exportedAt,
                    appNames = appNames
                )
                _pendingImportUri.value = uri
            }.onFailure { ex ->
                AppLogger.error("Portability", "Import validation failed: ${ex.message}")
                _portabilityMessage.value = "Invalid backup file: ${ex.message}"
            }
        }
    }

    fun executeImport(mode: ImportMode) {
        val uri = _pendingImportUri.value ?: return
        clearPendingImport()
        viewModelScope.launch {
            _isImporting.value = true
            runCatching {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    }
                } ?: error("Unable to open file")

                val currentSdBase = appPreferences.sdBasePath.first()
                val rootObj = JSONObject(content.trim())

                // 1. If REPLACE_ALL, restore preferences
                if (mode == ImportMode.REPLACE_ALL) {
                    val prefsObj = rootObj.optJSONObject("preferences")
                    if (prefsObj != null) {
                        prefsObj.optString("sdBasePath").ifBlank { null }?.let { appPreferences.setSdBasePath(it) }
                        prefsObj.optString("sdBlockDevice").ifBlank { null }?.let { appPreferences.setSdBlockDevice(it) }
                        if (prefsObj.has("autoMountOnBoot")) {
                            appPreferences.setAutoMountOnBoot(prefsObj.getBoolean("autoMountOnBoot"))
                        }
                        prefsObj.optString("conflictStrategy").ifBlank { null }?.let {
                            runCatching { ConflictStrategy.valueOf(it) }.getOrNull()?.let { cs ->
                                appPreferences.setConflictStrategy(cs)
                            }
                        }
                        prefsObj.optString("language").ifBlank { null }?.let { setLanguage(it) }
                        prefsObj.optString("themeMode").ifBlank { null }?.let {
                            runCatching { ThemeMode.valueOf(it) }.getOrNull()?.let { tm ->
                                appPreferences.setThemeMode(tm)
                            }
                        }
                    }

                    // Unmount and remove old apps
                    val existingApps = appRepository.observeApps().first()
                    existingApps.forEach { a ->
                        appRepository.removeApp(a.packageName)
                    }
                }

                // 2. Parse and import apps
                val appsArr = rootObj.optJSONArray("apps") ?: rootObj.optJSONArray("games") ?: JSONArray()
                var importedCount = 0

                for (i in 0 until appsArr.length()) {
                    val obj = appsArr.getJSONObject(i)
                    val pkg = obj.getString("packageName")
                    val name = obj.optString("displayName", pkg)
                    val modeStr = obj.optString("mode", "PKG")
                    val appMode = runCatching { MountMode.valueOf(modeStr) }.getOrDefault(MountMode.PKG)
                    val isEnabled = obj.optBoolean("isEnabled", true)
                    val sizeBytes = obj.optLong("dataSizeBytes", 0L)
                    val preferredDiskUuid = obj.optString("preferredDiskUuid").ifBlank { null }

                    val mpsList = mutableListOf<MountPointConfig>()
                    val mpsArr = obj.optJSONArray("mountPoints")
                    if (mpsArr != null) {
                        for (j in 0 until mpsArr.length()) {
                            val mpObj = mpsArr.getJSONObject(j)
                            val id = mpObj.optString("id", "mp_${pkg}_$j")
                            val catStr = mpObj.optString("category", "EXTERNAL_DATA")
                            val category = runCatching { MountPointCategory.valueOf(catStr) }
                                .getOrDefault(MountPointCategory.EXTERNAL_DATA)
                            val relSource = mpObj.optString("relativeSourcePath", "")
                            val absSource = if (relSource.startsWith("/")) {
                                relSource
                            } else if (relSource.isNotBlank()) {
                                "$currentSdBase/$relSource"
                            } else {
                                "$currentSdBase/MountX/Android/data/$pkg"
                            }
                            val targetPath = mpObj.optString("targetPath", "/data/media/0/Android/data/$pkg")
                            val enabled = mpObj.optBoolean("enabled", true)
                            val isVirtualContainer = mpObj.optBoolean("isVirtualContainer", false)
                            val containerImgPath = mpObj.optString("containerImgPath").ifBlank { null }
                            val diskUuid = mpObj.optString("diskUuid").ifBlank { null }
                            val label = mpObj.optString("label").ifBlank { null }
                            val preserveMedia = mpObj.optBoolean("preserveMedia", false)

                            mpsList.add(
                                MountPointConfig(
                                    id = id,
                                    category = category,
                                    sourcePath = absSource,
                                    targetPath = targetPath,
                                    enabled = enabled,
                                    isVirtualContainer = isVirtualContainer,
                                    containerImgPath = containerImgPath,
                                    diskUuid = diskUuid,
                                    label = label,
                                    preserveMedia = preserveMedia
                                )
                            )
                        }
                    }

                    val existing = appRepository.getApp(pkg)
                    if (existing != null) {
                        if (mode == ImportMode.REPLACE_ALL) {
                            appRepository.updateApp(
                                existing.copy(
                                    displayName = name,
                                    mode = appMode,
                                    isEnabled = isEnabled,
                                    dataSizeBytes = sizeBytes,
                                    mountPoints = mpsList,
                                    preferredDiskUuid = preferredDiskUuid
                                )
                            )
                            importedCount++
                        }
                    } else {
                        appRepository.addApp(
                            packageName = pkg,
                            displayName = name,
                            mode = appMode,
                            mountPoints = mpsList,
                            initialSizeBytes = sizeBytes
                        )
                        importedCount++
                    }
                }

                appRepository.syncModuleConfig()
                _portabilityMessage.value = "SNAPSHOT_IMPORT_OK:$importedCount"
            }.onFailure { ex ->
                AppLogger.error("Portability", "Snapshot import failed: ${ex.message}")
                _portabilityMessage.value = ex.message ?: "Import failed"
            }
            _isImporting.value = false
        }
    }
}
