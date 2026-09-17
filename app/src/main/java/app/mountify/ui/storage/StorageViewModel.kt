package app.mountify.ui.storage

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountify.data.model.FilesystemType
import app.mountify.data.model.InternalStorageInfo
import app.mountify.data.model.MountStatus
import app.mountify.data.model.PartitionInfo
import app.mountify.data.model.StorageInfo
import app.mountify.data.repository.GameRepository
import app.mountify.data.repository.StorageRepository
import app.mountify.util.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    val storageInfo: StateFlow<StorageInfo?> = storageRepository.observeStorageInfo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val internalStorageInfo: StateFlow<InternalStorageInfo?> = storageRepository.observeInternalStorage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _partitions = MutableStateFlow<List<PartitionInfo>>(emptyList())
    val partitions: StateFlow<List<PartitionInfo>> = _partitions.asStateFlow()

    private val _selectedPartition = MutableStateFlow<PartitionInfo?>(null)
    val selectedPartition: StateFlow<PartitionInfo?> = _selectedPartition.asStateFlow()

    private val _detectedDevices = MutableStateFlow<List<String>>(emptyList())
    val detectedDevices: StateFlow<List<String>> = _detectedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isFormatting = MutableStateFlow(false)
    val isFormatting: StateFlow<Boolean> = _isFormatting.asStateFlow()

    private val _isCheckingFs = MutableStateFlow(false)
    val isCheckingFs: StateFlow<Boolean> = _isCheckingFs.asStateFlow()

    private val _fsCheckOutput = MutableStateFlow<String?>(null)
    val fsCheckOutput: StateFlow<String?> = _fsCheckOutput.asStateFlow()

    private val _partitionLabel = MutableStateFlow("sdext2")
    val partitionLabel: StateFlow<String> = _partitionLabel.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val configuredSdBase = appPreferences.sdBasePath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "/data/sdext2")

    // Offloaded stats: Pair(mountedGamesCount, totalDataSizeBytes)
    val offloadedStats: StateFlow<Pair<Int, Long>> = gameRepository.observeGames().map { games ->
        val mountedCount = games.count { it.mountStatus == MountStatus.MOUNTED }
        val totalBytes = games.filter { it.mountStatus == MountStatus.MOUNTED }.sumOf { it.dataSizeBytes }
        Pair(mountedCount, totalBytes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0L))

    init {
        detectPartitions()
    }

    fun detectPartitions() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val sdBase = appPreferences.sdBasePath.first()
                val detected = storageRepository.detectPartitions(sdBase)
                _partitions.value = detected
                _detectedDevices.value = detected.map { it.path }

                // Auto-select active target mount or first suitable partition
                val currentSel = _selectedPartition.value
                val matched = detected.firstOrNull { it.path == currentSel?.path }
                    ?: detected.firstOrNull { it.isTargetMount }
                    ?: detected.firstOrNull { it.isSuitableForApp2sd }
                    ?: detected.firstOrNull()

                _selectedPartition.value = matched
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun detectDevices() {
        detectPartitions()
    }

    fun selectPartition(partition: PartitionInfo) {
        _selectedPartition.value = partition
    }

    fun setPartitionLabel(label: String) {
        _partitionLabel.value = label
    }

    fun mountPartition(blockDevice: String, fsType: FilesystemType) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            val result = storageRepository.mountSdPartition(blockDevice, sdBase, fsType)
            if (result.isSuccess) {
                appPreferences.setSdBlockDevice(blockDevice)
                _statusMessage.value = "MOUNT_OK"
                detectPartitions()
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
                detectPartitions()
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Unmount failed"
            }
        }
    }

    fun formatPartition(blockDevice: String, fsType: FilesystemType, label: String = _partitionLabel.value) {
        viewModelScope.launch {
            _isFormatting.value = true
            _statusMessage.value = null
            val result = storageRepository.formatPartition(blockDevice, fsType, label.ifBlank { "sdext2" })
            _isFormatting.value = false
            if (result.isSuccess) {
                _statusMessage.value = "FORMAT_OK"
                // Auto mount after format to sdBase
                val sdBase = appPreferences.sdBasePath.first()
                storageRepository.mountSdPartition(blockDevice, sdBase, fsType)
                detectPartitions()
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Format failed"
            }
        }
    }

    fun checkFilesystem(partition: PartitionInfo) {
        viewModelScope.launch {
            _isCheckingFs.value = true
            _fsCheckOutput.value = null
            val result = storageRepository.checkFilesystem(partition.path, partition.fsType)
            _isCheckingFs.value = false
            if (result.isSuccess) {
                _fsCheckOutput.value = result.getOrNull() ?: "Filesystem check clean."
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Check failed"
            }
        }
    }

    fun clearFsCheckOutput() {
        _fsCheckOutput.value = null
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
                        app.mountify.data.model.MountMode.valueOf(modeStr)
                    } catch (e: Exception) {
                        app.mountify.data.model.MountMode.PKG
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
