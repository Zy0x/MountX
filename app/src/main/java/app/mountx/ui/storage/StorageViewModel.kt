package app.mountx.ui.storage

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountx.data.model.BenchmarkResult
import app.mountx.data.model.DiskHardwareDetails
import app.mountx.data.model.DiskIoConfig
import app.mountx.data.model.FilesystemType
import app.mountx.data.model.InternalStorageInfo
import app.mountx.data.model.IoPreset
import app.mountx.data.model.MountStatus
import app.mountx.data.model.PartitionInfo
import app.mountx.data.model.PartitionSchemeConfig
import app.mountx.data.model.SdCardDiskInfo
import app.mountx.data.model.StorageInfo
import app.mountx.data.model.FsckReport
import app.mountx.data.model.FsckStatus
import app.mountx.data.model.SupportedFilesystemInfo
import app.mountx.ui.components.OperationState
import app.mountx.data.repository.GameRepository
import app.mountx.data.repository.StorageRepository
import app.mountx.util.AppPreferences
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

    private val _diskInfo = MutableStateFlow<SdCardDiskInfo?>(null)
    val diskInfo: StateFlow<SdCardDiskInfo?> = _diskInfo.asStateFlow()

    private val _allDisks = MutableStateFlow<List<SdCardDiskInfo>>(emptyList())
    val allDisks: StateFlow<List<SdCardDiskInfo>> = _allDisks.asStateFlow()

    private val _selectedDiskForDetail = MutableStateFlow<SdCardDiskInfo?>(null)
    val selectedDiskForDetail: StateFlow<SdCardDiskInfo?> = _selectedDiskForDetail.asStateFlow()

    fun openDiskDetail(disk: SdCardDiskInfo) {
        _selectedDiskForDetail.value = disk
        disk.partitions.firstOrNull()?.let { selectPartition(it) }
        loadDiskIoConfig(disk)
    }

    fun closeDiskDetail() {
        _selectedDiskForDetail.value = null
        _diskIoConfig.value = null
        _benchmarkResult.value = null
        _trimOutput.value = null
    }

    private val _diskIoConfig = MutableStateFlow<DiskIoConfig?>(null)
    val diskIoConfig: StateFlow<DiskIoConfig?> = _diskIoConfig.asStateFlow()

    private val _isApplyingIo = MutableStateFlow(false)
    val isApplyingIo: StateFlow<Boolean> = _isApplyingIo.asStateFlow()

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val _benchmarkResult = MutableStateFlow<BenchmarkResult?>(null)
    val benchmarkResult: StateFlow<BenchmarkResult?> = _benchmarkResult.asStateFlow()

    private val _isTrimming = MutableStateFlow(false)
    val isTrimming: StateFlow<Boolean> = _isTrimming.asStateFlow()

    private val _trimOutput = MutableStateFlow<String?>(null)
    val trimOutput: StateFlow<String?> = _trimOutput.asStateFlow()

    private val _diskHardwareDetails = MutableStateFlow<DiskHardwareDetails?>(null)
    val diskHardwareDetails: StateFlow<DiskHardwareDetails?> = _diskHardwareDetails.asStateFlow()

    private val _isMountingAll = MutableStateFlow(false)
    val isMountingAll: StateFlow<Boolean> = _isMountingAll.asStateFlow()

    private val _isUnmountingAll = MutableStateFlow(false)
    val isUnmountingAll: StateFlow<Boolean> = _isUnmountingAll.asStateFlow()

    private val _isUrgentGcRunning = MutableStateFlow(false)
    val isUrgentGcRunning: StateFlow<Boolean> = _isUrgentGcRunning.asStateFlow()

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

    private val _operationProgress = MutableStateFlow<OperationState?>(null)
    val operationProgress: StateFlow<OperationState?> = _operationProgress.asStateFlow()

    fun clearOperationProgress() {
        _operationProgress.value = null
    }

    private val _fsckReport = MutableStateFlow<FsckReport?>(null)
    val fsckReport: StateFlow<FsckReport?> = _fsckReport.asStateFlow()

    fun clearFsckReport() {
        _fsckReport.value = null
    }

    private val _supportedFilesystems = MutableStateFlow<List<SupportedFilesystemInfo>>(emptyList())
    val supportedFilesystems: StateFlow<List<SupportedFilesystemInfo>> = _supportedFilesystems.asStateFlow()

    fun loadSupportedFilesystems() {
        viewModelScope.launch {
            _supportedFilesystems.value = storageRepository.detectSupportedFilesystems()
        }
    }


    // Wizard States
    private val _isWizardOpen = MutableStateFlow(false)
    val isWizardOpen: StateFlow<Boolean> = _isWizardOpen.asStateFlow()

    private val _wizardPartitions = MutableStateFlow<List<PartitionSchemeConfig>>(emptyList())
    val wizardPartitions: StateFlow<List<PartitionSchemeConfig>> = _wizardPartitions.asStateFlow()

    private val _isRepartitioning = MutableStateFlow(false)
    val isRepartitioning: StateFlow<Boolean> = _isRepartitioning.asStateFlow()

    private val _repartitionError = MutableStateFlow<String?>(null)
    val repartitionError: StateFlow<String?> = _repartitionError.asStateFlow()

    val configuredSdBase = appPreferences.sdBasePath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "/data/sdext2")

    // Offloaded stats: Pair(mountedGamesCount, totalDataSizeBytes)
    val offloadedStats: StateFlow<Pair<Int, Long>> = gameRepository.observeGames().map { games ->
        val mountedCount = games.count { it.mountStatus == MountStatus.MOUNTED }
        val totalBytes = games.filter { it.mountStatus == MountStatus.MOUNTED }.sumOf { it.dataSizeBytes }
        Pair(mountedCount, totalBytes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0L))

    fun detectPartitions(force: Boolean = false) {
        if (_isScanning.value) return
        if (!force && _partitions.value.isNotEmpty()) return

        viewModelScope.launch {
            _isScanning.value = true
            try {
                val sdBase = appPreferences.sdBasePath.first()
                val detected = storageRepository.detectPartitions(sdBase)
                _partitions.value = detected
                _detectedDevices.value = detected.map { it.path }

                // Fetch hardware disks info
                val disks = storageRepository.getAllDisks(sdBase)
                _allDisks.value = disks
                val primaryDisk = disks.firstOrNull { it.diskType == app.mountx.data.model.DiskType.MICRO_SD } ?: disks.firstOrNull()
                _diskInfo.value = primaryDisk

                // Update selected disk if active
                _selectedDiskForDetail.value?.let { currentSelDisk ->
                    _selectedDiskForDetail.value = disks.firstOrNull { it.devicePath == currentSelDisk.devicePath }
                }

                // Auto-select active target mount or first suitable partition
                val currentSel = _selectedPartition.value
                val matched = detected.firstOrNull { it.path == currentSel?.path }
                    ?: detected.firstOrNull { it.isTargetMount }
                    ?: detected.firstOrNull { it.isMountTargetReady }
                    ?: detected.firstOrNull()

                _selectedPartition.value = matched
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun openPartitionWizard(targetDisk: SdCardDiskInfo? = null) {
        val disk = targetDisk ?: _selectedDiskForDetail.value ?: _diskInfo.value
        val totalBytes = if (disk != null && disk.totalSizeBytes > 0) {
            disk.totalSizeBytes
        } else {
            _partitions.value.sumOf { it.sizeBytes }.takeIf { it > 0 } ?: (64L * 1024 * 1024 * 1024)
        }
        val totalKb = totalBytes / 1024L

        // Default 2-partition scheme:
        // Part 1: Portable storage (60%), FAT32 (or exFAT)
        // Part 2: Target Mount for games (40%), F2FS
        val part1Kb = ((totalKb * 0.60) / 2048).toLong() * 2048L
        val part2Kb = (totalKb - part1Kb).coerceAtLeast(1024L * 1024L)

        val part1Fs = if (part1Kb > 32L * 1024 * 1024) FilesystemType.FAT32 else FilesystemType.FAT32
        val p1 = PartitionSchemeConfig(
            partitionIndex = 1,
            sizeKb = part1Kb,
            fsType = part1Fs,
            label = "STORAGE",
            isPrimary = true
        )
        val p2 = PartitionSchemeConfig(
            partitionIndex = 2,
            sizeKb = part2Kb,
            fsType = FilesystemType.F2FS,
            label = "sdext2",
            isPrimary = true
        )

        _wizardPartitions.value = listOf(p1, p2)
        _repartitionError.value = null
        _isWizardOpen.value = true
    }

    fun closePartitionWizard() {
        _isWizardOpen.value = false
        _repartitionError.value = null
    }

    fun updatePartitionSizeKb(index: Int, sizeKb: Long) {
        val current = _wizardPartitions.value.toMutableList()
        if (index in current.indices) {
            val clampedKb = sizeKb.coerceAtLeast(1024L) // Min 1MB
            current[index] = current[index].copy(sizeKb = clampedKb)
            _wizardPartitions.value = current
        }
    }

    fun updatePartitionFsType(index: Int, fsType: FilesystemType) {
        val current = _wizardPartitions.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(fsType = fsType)
            _wizardPartitions.value = current
        }
    }

    fun updatePartitionLabel(index: Int, label: String) {
        val current = _wizardPartitions.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(label = label)
            _wizardPartitions.value = current
        }
    }

    fun addPartition() {
        val current = _wizardPartitions.value.toMutableList()
        if (current.size >= 4) return

        val disk = _selectedDiskForDetail.value ?: _diskInfo.value
        val totalKb = (disk?.totalSizeBytes ?: 0L) / 1024L
        val allocatedKb = current.sumOf { it.sizeKb }
        val unallocatedKb = (totalKb - allocatedKb).coerceAtLeast(0L)

        val newIndex = current.size + 1
        if (unallocatedKb >= 1024L * 1024L) {
            current.add(
                PartitionSchemeConfig(
                    partitionIndex = newIndex,
                    sizeKb = (unallocatedKb / 2048) * 2048L,
                    fsType = FilesystemType.EXT4,
                    label = "PART$newIndex"
                )
            )
        } else {
            val lastIdx = current.lastIndex
            val lastSize = current[lastIdx].sizeKb
            val halfSize = ((lastSize / 2) / 2048) * 2048L
            if (halfSize >= 1024L * 1024L) {
                current[lastIdx] = current[lastIdx].copy(sizeKb = lastSize - halfSize)
                current.add(
                    PartitionSchemeConfig(
                        partitionIndex = newIndex,
                        sizeKb = halfSize,
                        fsType = FilesystemType.EXT4,
                        label = "PART$newIndex"
                    )
                )
            }
        }
        _wizardPartitions.value = current
    }

    fun removePartition(index: Int) {
        val current = _wizardPartitions.value.toMutableList()
        if (current.size <= 1) return
        if (index in current.indices) {
            val removed = current.removeAt(index)
            val targetIdx = (index - 1).coerceAtLeast(0)
            current[targetIdx] = current[targetIdx].copy(sizeKb = current[targetIdx].sizeKb + removed.sizeKb)
            val reindexed = current.mapIndexed { idx, p -> p.copy(partitionIndex = idx + 1) }
            _wizardPartitions.value = reindexed
        }
    }

    fun autoBalanceWizardPartitions() {
        val current = _wizardPartitions.value
        if (current.isEmpty()) return
        val disk = _selectedDiskForDetail.value ?: _diskInfo.value
        val totalKb = (disk?.totalSizeBytes ?: 0L) / 1024L
        if (totalKb <= 0) return
        val perPartKb = ((totalKb / current.size) / 2048) * 2048L
        val remainder = totalKb - (perPartKb * current.size)
        val balanced = current.mapIndexed { idx, p ->
            val extra = if (idx == current.lastIndex) remainder else 0L
            p.copy(sizeKb = perPartKb + extra)
        }
        _wizardPartitions.value = balanced
    }

    fun executeRepartition() {
        viewModelScope.launch {
            _isRepartitioning.value = true
            _repartitionError.value = null
            val targetDisk = _selectedDiskForDetail.value ?: _diskInfo.value
            val diskPath = targetDisk?.devicePath ?: "/dev/block/mmcblk0"
            val result = storageRepository.repartitionDisk(diskPath, _wizardPartitions.value)
            _isRepartitioning.value = false
            if (result.isSuccess) {
                _statusMessage.value = "REPARTITION_OK"
                _isWizardOpen.value = false
                detectPartitions(force = true)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Repartition failed"
                _repartitionError.value = err
                _statusMessage.value = err
            }
        }
    }

    fun detectDevices() {
        detectPartitions(force = true)
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
                detectPartitions(force = true)
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Mount failed"
            }
        }
    }

    fun mountPartition(partition: PartitionInfo) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            val result = storageRepository.mountPartition(partition, sdBase)
            if (result.isSuccess) {
                if (partition.fsType.equals("f2fs", ignoreCase = true) ||
                    partition.fsType.equals("ext4", ignoreCase = true) ||
                    partition.isTargetMount
                ) {
                    appPreferences.setSdBlockDevice(partition.path)
                }
                _statusMessage.value = "MOUNT_OK"
                detectPartitions(force = true)
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
                detectPartitions(force = true)
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Unmount failed"
            }
        }
    }

    fun unmountPartition(partition: PartitionInfo) {
        viewModelScope.launch {
            val result = storageRepository.unmountPartition(partition)
            if (result.isSuccess) {
                _statusMessage.value = "UNMOUNT_OK"
                detectPartitions(force = true)
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Unmount failed"
            }
        }
    }

    fun formatPartition(blockDevice: String, fsType: FilesystemType, label: String = _partitionLabel.value) {
        viewModelScope.launch {
            _isFormatting.value = true
            _statusMessage.value = null
            _operationProgress.value = OperationState.InProgress(
                title = "Format Partisi",
                stepMessage = "Menyiapkan dan memformat $blockDevice ke ${fsType.label}..."
            )
            val result = storageRepository.formatPartition(blockDevice, fsType, label.ifBlank { "sdext2" })
            _isFormatting.value = false
            if (result.isSuccess) {
                _statusMessage.value = "FORMAT_OK"
                // Auto mount after format to sdBase
                val sdBase = appPreferences.sdBasePath.first()
                storageRepository.mountSdPartition(blockDevice, sdBase, fsType)
                detectPartitions(force = true)
                _operationProgress.value = OperationState.Success(
                    title = "Format Partisi Berhasil",
                    message = "Partisi berhasil diformat ke ${fsType.label} dan di-mount kembali.",
                    details = listOf(
                        "Perangkat" to blockDevice,
                        "Filesystem" to fsType.label,
                        "Label" to label.ifBlank { "sdext2" },
                        "Mount Point" to sdBase
                    )
                )
            } else {
                val err = result.exceptionOrNull()?.message ?: "Format failed"
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = "Format Gagal",
                    errorMessage = err
                )
            }
        }
    }

    fun setPartitionLabel(partition: PartitionInfo, newLabel: String) {
        viewModelScope.launch {
            val cleanLabel = newLabel.trim()
            if (cleanLabel.isBlank()) return@launch
            _operationProgress.value = OperationState.InProgress(
                title = "Ganti Label Partisi",
                stepMessage = "Menulis label \"$cleanLabel\" ke ${partition.name} (${partition.fsType.uppercase()})..."
            )
            val result = storageRepository.setPartitionLabel(partition.path, partition.fsType, cleanLabel)
            if (result.isSuccess) {
                _operationProgress.value = OperationState.Success(
                    title = "Label Partisi Diperbarui",
                    message = "Label volume berhasil disimpan ke superblok filesystem tanpa memformat data.",
                    details = listOf(
                        "Perangkat" to partition.name,
                        "Filesystem" to partition.fsType.uppercase(),
                        "Label Baru" to cleanLabel
                    )
                )
                detectPartitions(force = true)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Perintah gagal dieksekusi"
                _operationProgress.value = OperationState.Error(
                    title = "Gagal Mengubah Label",
                    errorMessage = err
                )
            }
        }
    }

    fun checkFilesystem(partition: PartitionInfo) {
        viewModelScope.launch {
            _isCheckingFs.value = true
            _fsCheckOutput.value = null
            _operationProgress.value = OperationState.InProgress(
                title = "Pemeriksaan Filesystem",
                stepMessage = "Menjalankan diagnostik integritas ${partition.fsType.uppercase()} pada ${partition.name}..."
            )
            val result = storageRepository.checkFilesystem(partition.path, partition.fsType)
            _isCheckingFs.value = false
            if (result.isSuccess) {
                val report = result.getOrNull()
                _operationProgress.value = null
                _fsckReport.value = report
                _fsCheckOutput.value = report?.rawLog
            } else {
                val err = result.exceptionOrNull()?.message ?: "Check failed"
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = "Pemeriksaan Gagal",
                    errorMessage = err
                )
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
                        app.mountx.data.model.MountMode.valueOf(modeStr)
                    } catch (e: Exception) {
                        app.mountx.data.model.MountMode.PKG
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

    fun loadDiskIoConfig(disk: SdCardDiskInfo) {
        viewModelScope.launch {
            val diskName = disk.diskName
            val ioRes = storageRepository.getDiskIoConfig(diskName)
            if (ioRes.isSuccess) {
                val cfg = ioRes.getOrNull()
                _diskIoConfig.value = cfg
                if (cfg != null) {
                    appPreferences.saveBaselineIfFirstTime(cfg.readAheadKb, cfg.scheduler)
                }
            }
            val hwRes = storageRepository.getDiskHardwareDetails(diskName)
            if (hwRes.isSuccess) {
                _diskHardwareDetails.value = hwRes.getOrNull()
            }
        }
    }

    fun applyDiskIoConfig(disk: SdCardDiskInfo, config: DiskIoConfig) {
        viewModelScope.launch {
            _isApplyingIo.value = true
            _operationProgress.value = OperationState.InProgress(
                title = "Menerapkan I/O Optimization",
                stepMessage = "Mengonfigurasi buffer ${config.readAheadKb} KB dan scheduler [${config.scheduler}]..."
            )
            val res = storageRepository.applyDiskIoConfig(disk.diskName, config)
            _isApplyingIo.value = false
            if (res.isSuccess) {
                _diskIoConfig.value = config
                appPreferences.setIoReadAheadKb(config.readAheadKb)
                appPreferences.setIoScheduler(config.scheduler)
                _statusMessage.value = "IO_APPLY_OK"
                _operationProgress.value = OperationState.Success(
                    title = "I/O Booster Aktif",
                    message = "Parameter I/O kernel berhasil disinkronkan ke subsistem blok disk.",
                    details = listOf(
                        "Read-Ahead Buffer" to "${config.readAheadKb} KB",
                        "I/O Scheduler" to config.scheduler,
                        "Request Affinity" to "Level ${config.rqAffinity}",
                        "Queue Depth" to "${config.nrRequests} reqs",
                        "Boot Persistence" to if (config.isBootPersistent) "Aktif (MountX Module)" else "Non-aktif"
                    )
                )
            } else {
                val err = res.exceptionOrNull()?.message ?: "Failed to apply I/O config"
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = "Gagal Menerapkan I/O",
                    errorMessage = err
                )
            }
        }
    }

    fun applyIoPreset(disk: SdCardDiskInfo, preset: IoPreset) {
        viewModelScope.launch {
            val current = _diskIoConfig.value ?: DiskIoConfig()
            val available = current.availableSchedulers

            // Intelligent scheduler selection based on device kernel capability
            val targetScheduler = when (preset) {
                IoPreset.GAMING_ULTRA -> {
                    when {
                        available.any { it.equals("none", ignoreCase = true) } -> "none"
                        available.any { it.equals("noop", ignoreCase = true) } -> "noop"
                        available.any { it.equals("mq-deadline", ignoreCase = true) } -> "mq-deadline"
                        available.any { it.equals("deadline", ignoreCase = true) } -> "deadline"
                        else -> available.firstOrNull() ?: current.scheduler
                    }
                }
                IoPreset.BALANCED -> {
                    when {
                        available.any { it.equals("mq-deadline", ignoreCase = true) } -> "mq-deadline"
                        available.any { it.equals("deadline", ignoreCase = true) } -> "deadline"
                        available.any { it.equals("cfq", ignoreCase = true) } -> "cfq"
                        available.any { it.equals("kyber", ignoreCase = true) } -> "kyber"
                        else -> available.firstOrNull() ?: current.scheduler
                    }
                }
                IoPreset.DEFAULT_SYSTEM -> {
                    val baselineSched = appPreferences.baselineScheduler.first()
                    if (baselineSched.isNotBlank() && available.any { it.equals(baselineSched, ignoreCase = true) }) {
                        baselineSched
                    } else {
                        available.firstOrNull() ?: current.scheduler
                    }
                }
            }

            val targetReadAhead = if (preset == IoPreset.DEFAULT_SYSTEM) {
                appPreferences.baselineReadAheadKb.first()
            } else {
                preset.readAheadKb
            }

            val updated = current.copy(
                readAheadKb = targetReadAhead,
                scheduler = targetScheduler,
                rqAffinity = preset.rqAffinity,
                nrRequests = preset.nrRequests,
                vfsCachePressure = preset.vfsCachePressure
            )
            appPreferences.setIoPreset(preset.name)
            applyDiskIoConfig(disk, updated)
        }
    }

    fun runQuickDiskBenchmark(blockDevice: String) {
        viewModelScope.launch {
            _isBenchmarking.value = true
            _benchmarkResult.value = null
            _operationProgress.value = OperationState.InProgress(
                title = "Uji Kecepatan Disk",
                stepMessage = "Menguji sequential read & latency pada sampel 64 MB..."
            )
            val res = storageRepository.runQuickDiskBenchmark(blockDevice)
            _isBenchmarking.value = false
            if (res.isSuccess) {
                val benchResult = res.getOrNull()
                _benchmarkResult.value = benchResult
                _operationProgress.value = OperationState.Success(
                    title = "Uji Kecepatan Selesai",
                    message = "Pengujian I/O berhasil dilaksanakan.",
                    details = listOf(
                        "Sequential Read" to "${String.format(java.util.Locale.US, "%.1f", benchResult?.sequentialReadMbPerSec ?: 0.0)} MB/s",
                        "Akses Latency" to "${String.format(java.util.Locale.US, "%.2f", benchResult?.accessLatencyMs ?: 0.0)} ms",
                        "Ukuran Sampel" to "64 MB (Direct I/O)"
                    )
                )
            } else {
                val err = res.exceptionOrNull()?.message ?: "Benchmark failed"
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = "Uji Kecepatan Gagal",
                    errorMessage = err
                )
            }
        }
    }

    fun clearBenchmarkResult() {
        _benchmarkResult.value = null
    }

    fun runGlobalTrim(disk: SdCardDiskInfo) {
        viewModelScope.launch {
            _isTrimming.value = true
            _trimOutput.value = null
            _operationProgress.value = OperationState.InProgress(
                title = "Flash Storage TRIM",
                stepMessage = "Mengirim sinyal fstrim ke seluruh blok memori ${disk.hardwareTitle}..."
            )
            val res = storageRepository.executeGlobalTrim(disk)
            _isTrimming.value = false
            if (res.isSuccess) {
                val out = res.getOrNull() ?: "Global TRIM complete."
                _trimOutput.value = out
                _operationProgress.value = OperationState.Success(
                    title = "Global TRIM Selesai",
                    message = "Seluruh blok flash yang tidak terpakai berhasil dibebaskan.",
                    details = listOf(
                        "Perangkat" to disk.hardwareTitle,
                        "Status" to "Trimmed successfully"
                    ),
                    rawLog = out
                )
            } else {
                val err = res.exceptionOrNull()?.message ?: "TRIM failed"
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = "TRIM Gagal",
                    errorMessage = err
                )
            }
        }
    }

    fun runPartitionTrim(partition: PartitionInfo) {
        val mnt = partition.mountPoint
        if (mnt.isNullOrBlank()) {
            _statusMessage.value = "Partition must be mounted to run TRIM"
            return
        }
        viewModelScope.launch {
            _isTrimming.value = true
            _trimOutput.value = null
            _operationProgress.value = OperationState.InProgress(
                title = "TRIM Partisi",
                stepMessage = "Menjalankan fstrim pada titik mount $mnt..."
            )
            val res = storageRepository.executePartitionTrim(mnt)
            _isTrimming.value = false
            if (res.isSuccess) {
                val out = res.getOrNull() ?: "TRIM complete."
                _trimOutput.value = out
                _operationProgress.value = OperationState.Success(
                    title = "TRIM Partisi Selesai",
                    message = "Blok tidak terpakai pada $mnt berhasil dibersihkan.",
                    details = listOf(
                        "Partisi" to partition.name,
                        "Mount Point" to mnt
                    ),
                    rawLog = out
                )
            } else {
                val err = res.exceptionOrNull()?.message ?: "TRIM failed"
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = "TRIM Partisi Gagal",
                    errorMessage = err
                )
            }
        }
    }

    fun clearTrimOutput() {
        _trimOutput.value = null
    }

    fun runF2fsUrgentGc(disk: SdCardDiskInfo) {
        viewModelScope.launch {
            _isUrgentGcRunning.value = true
            _operationProgress.value = OperationState.InProgress(
                title = "F2FS Flash Defragmentasi",
                stepMessage = "Menjalankan F2FS Urgent Garbage Collection pada sektor flash..."
            )
            val res = storageRepository.executeF2fsUrgentGc(disk.diskName)
            _isUrgentGcRunning.value = false
            if (res.isSuccess) {
                _statusMessage.value = "F2FS_GC_OK"
                _operationProgress.value = OperationState.Success(
                    title = "Defragmentasi F2FS Selesai",
                    message = "F2FS Garbage Collection berhasil dijalankan, ruang blok flash telah dipadatkan.",
                    details = listOf(
                        "Disk" to disk.hardwareTitle,
                        "Mode GC" to "Urgent (Level 1)"
                    )
                )
            } else {
                val err = res.exceptionOrNull()?.message ?: "F2FS Urgent GC failed"
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = "Defragmentasi Gagal",
                    errorMessage = err
                )
            }
        }
    }

    fun mountAllPartitions(disk: SdCardDiskInfo) {
        viewModelScope.launch {
            _isMountingAll.value = true
            val sdBase = appPreferences.sdBasePath.first()
            val res = storageRepository.mountAllPartitions(disk, sdBase)
            _isMountingAll.value = false
            if (res.isSuccess) {
                _statusMessage.value = "MOUNT_ALL_OK"
                detectPartitions(force = true)
            } else {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Failed to mount all partitions"
            }
        }
    }

    fun unmountAllPartitions(disk: SdCardDiskInfo) {
        viewModelScope.launch {
            _isUnmountingAll.value = true
            val res = storageRepository.unmountAllPartitions(disk)
            _isUnmountingAll.value = false
            if (res.isSuccess) {
                _statusMessage.value = "UNMOUNT_ALL_OK"
                detectPartitions(force = true)
            } else {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Failed to unmount all partitions"
            }
        }
    }
}

