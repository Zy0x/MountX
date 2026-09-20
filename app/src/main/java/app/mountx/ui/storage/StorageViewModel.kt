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
import app.mountx.data.model.GlobalTrimReport
import app.mountx.data.model.TrimPartitionResult
import app.mountx.ui.components.OperationState
import app.mountx.data.repository.GameRepository
import app.mountx.data.repository.StorageRepository
import app.mountx.util.AppPreferences
import app.mountx.util.FormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
import app.mountx.R
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val gameRepository: GameRepository,
    private val appPreferences: AppPreferences,
    private val systemSyncMonitor: app.mountx.service.SystemSyncMonitor,
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

    fun loadSupportedFilesystems(force: Boolean = false) {
        viewModelScope.launch {
            if (!force && _supportedFilesystems.value.isNotEmpty()) return@launch
            _supportedFilesystems.value = storageRepository.detectSupportedFilesystems()
        }
    }

    // Unmount & Mount Progress States
    private val _unmountingPartitionPath = MutableStateFlow<String?>(null)
    val unmountingPartitionPath: StateFlow<String?> = _unmountingPartitionPath.asStateFlow()

    private val _mountingPartitionPath = MutableStateFlow<String?>(null)
    val mountingPartitionPath: StateFlow<String?> = _mountingPartitionPath.asStateFlow()

    private val _partitionToUnmount = MutableStateFlow<PartitionInfo?>(null)
    val partitionToUnmount: StateFlow<PartitionInfo?> = _partitionToUnmount.asStateFlow()

    fun promptUnmountPartition(partition: PartitionInfo) {
        _partitionToUnmount.value = partition
    }

    fun clearUnmountPartitionPrompt() {
        _partitionToUnmount.value = null
    }

    fun confirmUnmountPartition() {
        val part = _partitionToUnmount.value ?: return
        _partitionToUnmount.value = null
        unmountPartition(part)
    }

    private val _diskToEject = MutableStateFlow<SdCardDiskInfo?>(null)
    val diskToEject: StateFlow<SdCardDiskInfo?> = _diskToEject.asStateFlow()

    fun promptEjectDisk(disk: SdCardDiskInfo) {
        _diskToEject.value = disk
    }

    fun clearEjectDiskPrompt() {
        _diskToEject.value = null
    }

    fun confirmEjectDisk() {
        val disk = _diskToEject.value ?: return
        _diskToEject.value = null
        unmountAllPartitions(disk)
    }

    // Structured Global Trim Report State
    private val _globalTrimReport = MutableStateFlow<GlobalTrimReport?>(null)
    val globalTrimReport: StateFlow<GlobalTrimReport?> = _globalTrimReport.asStateFlow()

    fun clearGlobalTrimReport() {
        _globalTrimReport.value = null
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

    init {
        // Eagerly pre-warm partition and filesystem detection in background so tab navigation is instant
        viewModelScope.launch(Dispatchers.IO) {
            detectPartitionsInternal(force = false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            loadSupportedFilesystems(force = false)
        }
        viewModelScope.launch {
            systemSyncMonitor.events.collect { event ->
                when (event) {
                    is app.mountx.service.SystemSyncEvent.StorageMounted,
                    is app.mountx.service.SystemSyncEvent.RefreshAll -> {
                        detectPartitionsInternal(force = true)
                    }
                    is app.mountx.service.SystemSyncEvent.StorageDisconnected -> {
                        _diskInfo.value = null
                        _allDisks.value = emptyList()
                        _selectedDiskForDetail.value = null
                        _partitions.value = emptyList()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun updatePartitionMountedState(path: String, isMounted: Boolean) {
        _partitions.value = _partitions.value.map {
            if (it.path == path) it.copy(isMounted = isMounted) else it
        }
        _selectedDiskForDetail.value = _selectedDiskForDetail.value?.let { disk ->
            val updated = disk.partitions.map {
                if (it.path == path) it.copy(isMounted = isMounted) else it
            }
            disk.copy(partitions = updated)
        }
    }

    suspend fun detectPartitionsInternal(force: Boolean = false) {
        if (_isScanning.value) return
        if (!force && _partitions.value.isNotEmpty()) return

        _isScanning.value = true
        try {
            val sdBase = appPreferences.sdBasePath.first()
            val detected = storageRepository.detectPartitions(sdBase)
            _partitions.value = detected
            _detectedDevices.value = detected.map { it.path }

            // Fetch hardware disks info reusing pre-scanned partitions
            val disks = storageRepository.getAllDisks(sdBase, detected)
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

    fun detectPartitions(force: Boolean = false) {
        viewModelScope.launch {
            detectPartitionsInternal(force)
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
            val disk = _selectedDiskForDetail.value ?: _diskInfo.value
            val totalKb = if (disk != null && disk.totalSizeBytes > 0) {
                disk.totalSizeBytes / 1024L
            } else {
                _partitions.value.sumOf { it.sizeBytes }.takeIf { it > 0 }?.div(1024L) ?: (64L * 1024 * 1024)
            }
            val minKb = 512L * 1024L // 512MB minimum per partition
            val otherPartitionsKb = current.filterIndexed { i, _ -> i != index }.sumOf { it.sizeKb }
            val maxAllowedKb = (totalKb - otherPartitionsKb).coerceAtLeast(minKb)
            val clampedKb = sizeKb.coerceIn(minKb, maxAllowedKb)
            current[index] = current[index].copy(sizeKb = clampedKb)
            _wizardPartitions.value = current
        }
    }

    fun allocateUnallocatedToPartition(index: Int) {
        val current = _wizardPartitions.value.toMutableList()
        if (index in current.indices) {
            val disk = _selectedDiskForDetail.value ?: _diskInfo.value
            val totalKb = (disk?.totalSizeBytes ?: 0L) / 1024L
            val allocatedKb = current.sumOf { it.sizeKb }
            val unallocatedKb = (totalKb - allocatedKb).coerceAtLeast(0L)
            if (unallocatedKb > 0L) {
                current[index] = current[index].copy(sizeKb = current[index].sizeKb + unallocatedKb)
                _wizardPartitions.value = current
            }
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

    /**
     * Adjust space between two adjacent partitions by sliding their mutual boundary.
     * Enforces a minimum partition size of 512 MB.
     */
    fun adjustAdjacentWizardPartitions(leftIndex: Int, deltaKb: Long) {
        val current = _wizardPartitions.value.toMutableList()
        if (leftIndex !in 0 until current.lastIndex) return

        val minKb = 512L * 1024L // 512 MB minimum
        val left = current[leftIndex]
        val right = current[leftIndex + 1]
        val totalPairKb = left.sizeKb + right.sizeKb
        if (totalPairKb < minKb * 2) return

        val newLeftRaw = left.sizeKb + deltaKb
        val clampedLeft = newLeftRaw.coerceIn(minKb, totalPairKb - minKb)
        val alignedLeft = ((clampedLeft / 2048L) * 2048L).coerceIn(minKb, totalPairKb - minKb)
        val alignedRight = totalPairKb - alignedLeft

        current[leftIndex] = left.copy(sizeKb = alignedLeft)
        current[leftIndex + 1] = right.copy(sizeKb = alignedRight)
        _wizardPartitions.value = current
    }

    /**
     * Quick increment/decrement partition size by gigabytes (e.g. +1 GB / -1 GB).
     */
    fun adjustPartitionSizeByGb(index: Int, deltaGb: Long) {
        val current = _wizardPartitions.value
        if (current.size <= 1 || index !in current.indices) return

        val deltaKb = deltaGb * 1024L * 1024L
        if (index < current.lastIndex) {
            adjustAdjacentWizardPartitions(index, deltaKb)
        } else if (index > 0) {
            adjustAdjacentWizardPartitions(index - 1, -deltaKb)
        }
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
            _mountingPartitionPath.value = partition.path
            try {
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
                    updatePartitionMountedState(partition.path, isMounted = true)
                    detectPartitionsInternal(force = true)
                } else {
                    _statusMessage.value = result.exceptionOrNull()?.message ?: "Mount failed"
                }
            } finally {
                _mountingPartitionPath.value = null
            }
        }
    }

    fun unmountPartition() {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            _unmountingPartitionPath.value = sdBase
            try {
                val result = storageRepository.unmountSdPartition(sdBase)
                if (result.isSuccess) {
                    _statusMessage.value = "UNMOUNT_OK"
                    detectPartitionsInternal(force = true)
                } else {
                    _statusMessage.value = result.exceptionOrNull()?.message ?: "Unmount failed"
                }
            } finally {
                _unmountingPartitionPath.value = null
            }
        }
    }

    fun unmountPartition(partition: PartitionInfo) {
        viewModelScope.launch {
            _unmountingPartitionPath.value = partition.path
            try {
                val result = storageRepository.unmountPartition(partition)
                if (result.isSuccess) {
                    _statusMessage.value = "UNMOUNT_OK"
                    updatePartitionMountedState(partition.path, isMounted = false)
                    detectPartitionsInternal(force = true)
                } else {
                    _statusMessage.value = result.exceptionOrNull()?.message ?: "Unmount failed"
                }
            } finally {
                _unmountingPartitionPath.value = null
            }
        }
    }

    fun formatPartition(blockDevice: String, fsType: FilesystemType, label: String = _partitionLabel.value) {
        viewModelScope.launch {
            _isFormatting.value = true
            _statusMessage.value = null
            _operationProgress.value = OperationState.InProgress(
                title = context.getString(R.string.op_format_title),
                stepMessage = context.getString(R.string.op_format_step, blockDevice, fsType.label)
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
                    title = context.getString(R.string.op_format_success_title),
                    message = context.getString(R.string.op_format_success_msg, fsType.label),
                    details = listOf(
                        context.getString(R.string.op_detail_device) to blockDevice,
                        context.getString(R.string.op_detail_filesystem) to fsType.label,
                        context.getString(R.string.op_detail_label) to label.ifBlank { "sdext2" },
                        context.getString(R.string.op_detail_mount_point) to sdBase
                    )
                )
            } else {
                val err = result.exceptionOrNull()?.message ?: context.getString(R.string.op_format_error_title)
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = context.getString(R.string.op_format_error_title),
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
                title = context.getString(R.string.op_rename_label_title),
                stepMessage = context.getString(R.string.op_rename_label_step, cleanLabel, partition.name, partition.fsType.uppercase())
            )
            val result = storageRepository.setPartitionLabel(partition.path, partition.fsType, cleanLabel)
            if (result.isSuccess) {
                _operationProgress.value = OperationState.Success(
                    title = context.getString(R.string.op_rename_label_success_title),
                    message = context.getString(R.string.op_rename_label_success_msg),
                    details = listOf(
                        context.getString(R.string.op_detail_device) to partition.name,
                        context.getString(R.string.op_detail_filesystem) to partition.fsType.uppercase(),
                        context.getString(R.string.op_detail_label) to cleanLabel
                    )
                )
                detectPartitions(force = true)
            } else {
                val err = result.exceptionOrNull()?.message ?: context.getString(R.string.op_rename_label_error_title)
                _operationProgress.value = OperationState.Error(
                    title = context.getString(R.string.op_rename_label_error_title),
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
                title = context.getString(R.string.op_fs_check_title),
                stepMessage = context.getString(R.string.op_fs_check_step, partition.fsType.uppercase(), partition.name)
            )
            val result = storageRepository.checkFilesystem(partition.path, partition.fsType)
            _isCheckingFs.value = false
            if (result.isSuccess) {
                val report = result.getOrNull()
                _operationProgress.value = null
                _fsckReport.value = report
                _fsCheckOutput.value = report?.rawLog
            } else {
                val err = result.exceptionOrNull()?.message ?: context.getString(R.string.op_fs_check_error_title)
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = context.getString(R.string.op_fs_check_error_title),
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
                val currentSdBase = appPreferences.sdBasePath.first()
                val games = gameRepository.observeGames().first()
                val rootObj = JSONObject().apply {
                    put("schemaVersion", 2)
                    put("exportedAt", System.currentTimeMillis())
                    put("appVersion", app.mountx.BuildConfig.VERSION_NAME)
                    put("sdBase", currentSdBase)

                    val gamesArr = JSONArray()
                    games.forEach { g ->
                        val gameObj = JSONObject().apply {
                            put("packageName", g.packageName)
                            put("displayName", g.displayName)
                            put("mode", g.mode.name)
                            put("isEnabled", g.isEnabled)
                            put("dataSizeBytes", g.dataSizeBytes)

                            val mpsArr = JSONArray()
                            g.mountPoints.forEach { mp ->
                                val mpObj = JSONObject().apply {
                                    put("id", mp.id)
                                    put("category", mp.category.name)
                                    // Normalize sourcePath to relative path if within sdBase
                                    val relSource = if (mp.sourcePath.startsWith(currentSdBase)) {
                                        mp.sourcePath.removePrefix(currentSdBase).removePrefix("/")
                                    } else {
                                        mp.sourcePath
                                    }
                                    put("relativeSourcePath", relSource)
                                    put("targetPath", mp.targetPath)
                                    put("enabled", mp.enabled)
                                    put("isVirtualContainer", mp.isVirtualContainer)
                                    if (mp.containerImgPath != null) put("containerImgPath", mp.containerImgPath)
                                    if (mp.diskUuid != null) put("diskUuid", mp.diskUuid)
                                    if (mp.label != null) put("label", mp.label)
                                }
                                mpsArr.put(mpObj)
                            }
                            put("mountPoints", mpsArr)
                        }
                        gamesArr.put(gameObj)
                    }
                    put("games", gamesArr)
                }

                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(rootObj.toString(2).toByteArray())
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

                val currentSdBase = appPreferences.sdBasePath.first()
                val trimmed = content.trim()

                if (trimmed.startsWith("{")) {
                    // Schema v2 (JSONObject with metadata & granular mountPoints)
                    val rootObj = JSONObject(trimmed)
                    val gamesArr = rootObj.optJSONArray("games") ?: JSONArray()
                    for (i in 0 until gamesArr.length()) {
                        val obj = gamesArr.getJSONObject(i)
                        val pkg = obj.getString("packageName")
                        val name = obj.optString("displayName", pkg)
                        val modeStr = obj.optString("mode", "PKG")
                        val mode = runCatching { app.mountx.data.model.MountMode.valueOf(modeStr) }
                            .getOrDefault(app.mountx.data.model.MountMode.PKG)
                        val isEnabled = obj.optBoolean("isEnabled", true)
                        val sizeBytes = obj.optLong("dataSizeBytes", 0L)

                        val mpsList = mutableListOf<app.mountx.data.model.MountPointConfig>()
                        val mpsArr = obj.optJSONArray("mountPoints")
                        if (mpsArr != null) {
                            for (j in 0 until mpsArr.length()) {
                                val mpObj = mpsArr.getJSONObject(j)
                                val id = mpObj.optString("id", "mp_${pkg}_$j")
                                val catStr = mpObj.optString("category", "EXTERNAL_DATA")
                                val category = runCatching { app.mountx.data.model.MountPointCategory.valueOf(catStr) }
                                    .getOrDefault(app.mountx.data.model.MountPointCategory.EXTERNAL_DATA)
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

                                mpsList.add(
                                    app.mountx.data.model.MountPointConfig(
                                        id = id,
                                        category = category,
                                        sourcePath = absSource,
                                        targetPath = targetPath,
                                        enabled = enabled,
                                        isVirtualContainer = isVirtualContainer,
                                        containerImgPath = containerImgPath,
                                        diskUuid = diskUuid,
                                        label = label
                                    )
                                )
                            }
                        }

                        val existing = gameRepository.getGame(pkg)
                        if (existing != null) {
                            val mergedPoints = if (mpsList.isNotEmpty()) mpsList else existing.mountPoints
                            gameRepository.updateGame(existing.copy(
                                displayName = name,
                                mode = mode,
                                isEnabled = isEnabled,
                                mountPoints = mergedPoints
                            ))
                        } else {
                            gameRepository.addGame(
                                packageName = pkg,
                                displayName = name,
                                mode = mode,
                                mountPoints = mpsList,
                                initialSizeBytes = sizeBytes
                            )
                        }
                    }
                } else {
                    // Fallback to legacy Schema v1 (JSONArray)
                    val jsonArr = JSONArray(trimmed)
                    for (i in 0 until jsonArr.length()) {
                        val obj = jsonArr.getJSONObject(i)
                        val pkg = obj.getString("packageName")
                        val name = obj.optString("displayName", pkg)
                        val modeStr = obj.optString("mode", "PKG")
                        val mode = runCatching { app.mountx.data.model.MountMode.valueOf(modeStr) }
                            .getOrDefault(app.mountx.data.model.MountMode.PKG)
                        val existing = gameRepository.getGame(pkg)
                        if (existing == null) {
                            gameRepository.addGame(pkg, name, mode)
                        }
                    }
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
                title = context.getString(R.string.op_io_booster_title),
                stepMessage = context.getString(R.string.op_io_booster_step, config.readAheadKb, config.scheduler)
            )
            val res = storageRepository.applyDiskIoConfig(disk.diskName, config)
            _isApplyingIo.value = false
            if (res.isSuccess) {
                _diskIoConfig.value = config
                appPreferences.setIoReadAheadKb(config.readAheadKb)
                appPreferences.setIoScheduler(config.scheduler)
                _statusMessage.value = "IO_APPLY_OK"
                _operationProgress.value = OperationState.Success(
                    title = context.getString(R.string.op_io_booster_success_title),
                    message = "I/O kernel parameters synced to the block device subsystem.",
                    details = listOf(
                        context.getString(R.string.op_detail_read_ahead) to "${config.readAheadKb} KB",
                        context.getString(R.string.op_detail_scheduler) to config.scheduler,
                        context.getString(R.string.op_detail_request_affinity) to "Level ${config.rqAffinity}",
                        context.getString(R.string.op_detail_queue_depth) to "${config.nrRequests} reqs",
                        context.getString(R.string.op_detail_boot_persistence) to if (config.isBootPersistent)
                            context.getString(R.string.op_detail_boot_persist_active)
                        else
                            context.getString(R.string.op_detail_boot_persist_inactive)
                    )
                )
            } else {
                val err = res.exceptionOrNull()?.message ?: context.getString(R.string.op_io_booster_error_title)
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = context.getString(R.string.op_io_booster_error_title),
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
                title = context.getString(R.string.op_benchmark_title),
                stepMessage = context.getString(R.string.op_benchmark_step)
            )
            val res = storageRepository.runQuickDiskBenchmark(blockDevice)
            _isBenchmarking.value = false
            if (res.isSuccess) {
                val benchResult = res.getOrNull()
                _benchmarkResult.value = benchResult
                _operationProgress.value = OperationState.Success(
                    title = context.getString(R.string.op_benchmark_success_title),
                    message = "I/O test completed successfully.",
                    details = listOf(
                        context.getString(R.string.op_detail_seq_read) to "${String.format(java.util.Locale.US, "%.1f", benchResult?.sequentialReadMbPerSec ?: 0.0)} MB/s",
                        context.getString(R.string.op_detail_latency) to "${String.format(java.util.Locale.US, "%.2f", benchResult?.accessLatencyMs ?: 0.0)} ms",
                        context.getString(R.string.op_detail_sample_size) to "64 MB (Direct I/O)"
                    )
                )
            } else {
                val err = res.exceptionOrNull()?.message ?: context.getString(R.string.op_benchmark_error_title)
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = context.getString(R.string.op_benchmark_error_title),
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
            _globalTrimReport.value = null
            _operationProgress.value = OperationState.InProgress(
                title = context.getString(R.string.op_trim_title),
                stepMessage = context.getString(R.string.op_trim_step, disk.hardwareTitle)
            )
            val res = storageRepository.executeGlobalTrimStructured(disk)
            _isTrimming.value = false
            if (res.isSuccess) {
                val report = res.getOrThrow()
                _globalTrimReport.value = report
                _trimOutput.value = report.rawLog

                val details = report.partitionResults.map {
                    val statusText = when {
                        it.needsCleaning -> context.getString(R.string.op_detail_needs_cleaning)
                        it.notImplemented -> context.getString(R.string.op_detail_unsupported)
                        it.bytesTrimmed > 0 -> "${FormatUtils.formatBytes(it.bytesTrimmed)} ${context.getString(R.string.op_detail_freed)}"
                        else -> context.getString(R.string.op_detail_done)
                    }
                    "${it.partitionName} (${it.mountPoint})" to statusText
                }

                _operationProgress.value = OperationState.Success(
                    title = if (report.hasNeedsCleaning) context.getString(R.string.op_trim_warn_title)
                            else context.getString(R.string.op_trim_success_title),
                    message = report.summary,
                    details = details,
                    rawLog = report.rawLog
                )
            } else {
                val err = res.exceptionOrNull()?.message ?: context.getString(R.string.op_trim_error_title)
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = context.getString(R.string.op_trim_error_title),
                    errorMessage = err
                )
            }
        }
    }

    fun executeGuidedFsckRepair(partition: PartitionInfo) {
        viewModelScope.launch {
            _operationProgress.value = OperationState.InProgress(
                title = context.getString(R.string.op_fsck_guided_title),
                stepMessage = context.getString(R.string.op_fsck_guided_step, partition.cleanShortName)
            )
            val res = storageRepository.safeUnmountCheckAndRemount(partition)
            if (res.isSuccess) {
                val report = res.getOrThrow()
                _fsckReport.value = report
                _operationProgress.value = OperationState.Success(
                    title = context.getString(R.string.op_fsck_guided_success_title),
                    message = context.getString(R.string.op_fsck_guided_success_msg, partition.cleanShortName),
                    details = listOf(
                        context.getString(R.string.op_detail_integrity_status) to report.status.name,
                        context.getString(R.string.op_detail_result) to report.summary
                    ),
                    rawLog = report.rawLog
                )
                detectPartitions(force = true)
            } else {
                val err = res.exceptionOrNull()?.message ?: context.getString(R.string.op_fsck_guided_error_title)
                _operationProgress.value = OperationState.Error(
                    title = context.getString(R.string.op_fsck_guided_error_title),
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
                title = context.getString(R.string.op_trim_title),
                stepMessage = context.getString(R.string.op_trim_step, partition.cleanShortName)
            )
            val res = storageRepository.executePartitionTrim(mnt)
            _isTrimming.value = false
            _operationProgress.value = null
            if (res.isSuccess) {
                val out = res.getOrNull() ?: "TRIM complete."
                _trimOutput.value = out
            } else {
                val err = res.exceptionOrNull()?.message ?: context.getString(R.string.op_trim_error_title)
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = context.getString(R.string.op_trim_error_title),
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
                title = context.getString(R.string.op_f2fs_gc_title),
                stepMessage = context.getString(R.string.op_f2fs_gc_step, disk.hardwareTitle)
            )
            val res = storageRepository.executeF2fsUrgentGc(disk.diskName)
            _isUrgentGcRunning.value = false
            if (res.isSuccess) {
                _statusMessage.value = "F2FS_GC_OK"
                _operationProgress.value = OperationState.Success(
                    title = context.getString(R.string.op_f2fs_gc_success_title),
                    message = "F2FS Garbage Collection completed, flash blocks consolidated.",
                    details = listOf(
                        "Disk" to disk.hardwareTitle,
                        "Mode GC" to "Urgent (Level 1)"
                    )
                )
            } else {
                val err = res.exceptionOrNull()?.message ?: context.getString(R.string.op_f2fs_gc_error_title)
                _statusMessage.value = err
                _operationProgress.value = OperationState.Error(
                    title = context.getString(R.string.op_f2fs_gc_error_title),
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

