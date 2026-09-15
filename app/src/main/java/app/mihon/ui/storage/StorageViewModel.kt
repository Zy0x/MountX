package app.mihon.ui.storage

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mihon.data.model.FilesystemType
import app.mihon.data.model.StorageInfo
import app.mihon.data.repository.GameRepository
import app.mihon.data.repository.StorageRepository
import app.mihon.util.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val gameRepository: GameRepository,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val storageInfo = storageRepository.observeStorageInfo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _detectedDevices = MutableStateFlow<List<String>>(emptyList())
    val detectedDevices: StateFlow<List<String>> = _detectedDevices.asStateFlow()

    private val _isFormatting = MutableStateFlow(false)
    val isFormatting: StateFlow<Boolean> = _isFormatting.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        detectDevices()
    }

    fun detectDevices() {
        viewModelScope.launch {
            _detectedDevices.value = storageRepository.detectBlockDevices()
        }
    }

    fun mountPartition(blockDevice: String, fsType: FilesystemType) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            val result = storageRepository.mountSdPartition(blockDevice, sdBase, fsType)
            if (result.isSuccess) {
                appPreferences.setSdBlockDevice(blockDevice)
                _statusMessage.value = "MOUNT_OK"
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Mount failed"
            }
        }
    }

    fun unmountPartition() {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            val result = storageRepository.unmountSdPartition(sdBase)
            if (result.isSuccess) {
                _statusMessage.value = "UNMOUNT_OK"
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Unmount failed"
            }
        }
    }

    fun formatPartition(blockDevice: String, fsType: FilesystemType) {
        viewModelScope.launch {
            _isFormatting.value = true
            _statusMessage.value = null
            val result = storageRepository.formatPartition(blockDevice, fsType)
            _isFormatting.value = false
            if (result.isSuccess) {
                _statusMessage.value = "FORMAT_OK"
                // Auto mount after format
                val sdBase = appPreferences.sdBasePath.first()
                storageRepository.mountSdPartition(blockDevice, sdBase, fsType)
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Format failed"
            }
        }
    }

    fun exportConfig(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val games = gameRepository.observeGames().first()
                val jsonArr = JSONArray()
                games.forEach { g ->
                    val obj = JSONObject().apply {
                        put("packageName", g.packageName)
                        put("displayName", g.displayName)
                        put("mode", g.mode.name)
                    }
                    jsonArr.put(obj)
                }
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(jsonArr.toString(2).toByteArray())
                }
                _statusMessage.value = "EXPORT_OK"
            }.onFailure {
                _statusMessage.value = it.message
            }
        }
    }

    fun importConfig(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: error("Unable to open file")

                val jsonArr = JSONArray(content)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val pkg = obj.getString("packageName")
                    val name = obj.optString("displayName", pkg)
                    val modeStr = obj.optString("mode", "PKG")
                    val mode = try {
                        app.mihon.data.model.MountMode.valueOf(modeStr)
                    } catch (e: Exception) {
                        app.mihon.data.model.MountMode.PKG
                    }
                    gameRepository.addGame(pkg, name, mode)
                }
                _statusMessage.value = "IMPORT_OK"
            }.onFailure {
                _statusMessage.value = it.message
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
