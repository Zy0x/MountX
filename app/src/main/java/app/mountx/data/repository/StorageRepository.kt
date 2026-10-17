package app.mountx.data.repository

import app.mountx.data.model.BenchmarkResult
import app.mountx.data.model.ConflictStrategy
import app.mountx.data.model.DiskHardwareDetails
import app.mountx.data.model.DiskIoConfig
import app.mountx.data.model.FilesystemType
import app.mountx.data.model.InternalStorageInfo
import app.mountx.data.model.MigrationTarget
import app.mountx.data.model.MoveDirection
import app.mountx.data.model.FsckReport
import app.mountx.data.model.PartitionInfo
import app.mountx.data.model.PartitionSchemeConfig
import app.mountx.data.model.SdCardDiskInfo
import app.mountx.data.model.StorageInfo
import app.mountx.data.model.StorageHierarchyResult
import app.mountx.data.model.SupportedFilesystemInfo
import app.mountx.data.model.GlobalTrimReport
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.OperationProgress
import app.mountx.root.StorageManager
import app.mountx.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import app.mountx.service.SystemSyncMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(
    private val storageManager: StorageManager,
    private val systemSyncMonitor: SystemSyncMonitor
) {

    @Volatile
    private var cachedHierarchy: StorageHierarchyResult? = null

    @Volatile
    private var lastScanTimeMillis: Long = 0L

    private val scanMutex = Mutex()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val CACHE_TTL_MS = 30_000L // 30s cache TTL for instant tab switching
    }

    init {
        repositoryScope.launch {
            systemSyncMonitor.events.collect { event ->
                when (event) {
                    is app.mountx.service.SystemSyncEvent.StorageMounted,
                    is app.mountx.service.SystemSyncEvent.StorageDisconnected,
                    is app.mountx.service.SystemSyncEvent.RefreshAll -> {
                        invalidateCache()
                    }
                    else -> {}
                }
            }
        }
    }

    fun getCachedDisks(): List<SdCardDiskInfo> = cachedHierarchy?.disks ?: emptyList()
    fun getCachedPartitions(): List<PartitionInfo> = cachedHierarchy?.partitions ?: emptyList()

    fun invalidateCache() {
        cachedHierarchy = null
        lastScanTimeMillis = 0L
    }

    suspend fun getStorageHierarchy(
        targetMountPoint: String = "/data/sdext2",
        force: Boolean = false
    ): StorageHierarchyResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedHierarchy
        if (!force && cached != null && (now - lastScanTimeMillis) < CACHE_TTL_MS) {
            return@withContext cached
        }
        scanMutex.withLock {
            val secondCheck = cachedHierarchy
            val secondNow = System.currentTimeMillis()
            if (!force && secondCheck != null && (secondNow - lastScanTimeMillis) < CACHE_TTL_MS) {
                return@withLock secondCheck
            }
            val result = storageManager.detectStorageHierarchy(targetMountPoint)
            cachedHierarchy = result
            lastScanTimeMillis = System.currentTimeMillis()
            result
        }
    }

    private val _manualRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun refreshStorage() {
        invalidateCache()
        _manualRefresh.tryEmit(Unit)
    }

    /**
     * Event-driven storage info observation.
     * Fires immediately on manual refresh; throttled to >= 3000ms for hardware system events.
     * Stops automatically when UI unsubscribes (lifecycle aware), allowing deep sleep.
     */
    fun observeStorageInfo(mountPoint: String = "/data/sdext2"): Flow<StorageInfo?> = flow {
        emit(storageManager.getStorageInfo(mountPoint))
        merge(
            _manualRefresh.map { false },
            systemSyncMonitor.events.map { true }
        ).collect { isSystemEvent ->
            if (isSystemEvent) {
                delay(3000L) // 3000ms debouncing only for hardware events to keep CPU cool
            }
            emit(storageManager.getStorageInfo(mountPoint))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Event-driven internal device storage (/data) observation.
     */
    fun observeInternalStorage(): Flow<InternalStorageInfo?> = flow {
        emit(storageManager.getInternalStorageInfo())
        merge(
            _manualRefresh.map { false },
            systemSyncMonitor.events.map { true }
        ).collect { isSystemEvent ->
            if (isSystemEvent) {
                delay(3000L) // 3000ms debouncing only for hardware events to keep CPU cool
            }
            emit(storageManager.getInternalStorageInfo())
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getStorageInfo(mountPoint: String = "/data/sdext2"): StorageInfo? =
        withContext(Dispatchers.IO) {
            storageManager.getStorageInfo(mountPoint)
        }

    suspend fun getInternalStorageInfo(): InternalStorageInfo? =
        withContext(Dispatchers.IO) {
            storageManager.getInternalStorageInfo()
        }

    suspend fun detectBlockDevices(): List<String> = withContext(Dispatchers.IO) {
        storageManager.detectBlockDevices()
    }

    suspend fun detectPartitions(
        targetMountPoint: String = "/data/sdext2",
        force: Boolean = false
    ): List<PartitionInfo> = withContext(Dispatchers.IO) {
        getStorageHierarchy(targetMountPoint, force).partitions
    }

    suspend fun checkFilesystem(blockDevice: String, fsType: String = ""): Result<FsckReport> =
        withContext(Dispatchers.IO) {
            AppLogger.info("FSCK", "Executing filesystem check on $blockDevice ($fsType)")
            val res = storageManager.checkFilesystem(blockDevice, fsType)
            if (res.isSuccess) {
                AppLogger.success("FSCK", "fsck passed for $blockDevice: ${res.getOrNull()?.status?.name}")
            } else {
                AppLogger.error("FSCK", "fsck failed for $blockDevice: ${res.exceptionOrNull()?.message}")
            }
            res
        }

    suspend fun setPartitionLabel(blockDevice: String, fsType: String, newLabel: String): Result<String> =
        withContext(Dispatchers.IO) {
            AppLogger.info("Storage", "Writing label '$newLabel' on $blockDevice ($fsType)")
            val res = storageManager.setPartitionLabel(blockDevice, fsType, newLabel)
            if (res.isSuccess) {
                invalidateCache()
                AppLogger.success("Storage", "Label '$newLabel' written to $blockDevice successfully")
            } else {
                AppLogger.error("Storage", "Failed to set label on $blockDevice: ${res.exceptionOrNull()?.message}")
            }
            res
        }

    suspend fun detectSupportedFilesystems(): List<SupportedFilesystemInfo> =
        withContext(Dispatchers.IO) {
            storageManager.detectSupportedFilesystems()
        }

    suspend fun mountSdPartition(
        blockDevice: String,
        mountPoint: String = "/data/sdext2",
        fsType: FilesystemType = FilesystemType.F2FS
    ): Result<Unit> = withContext(Dispatchers.IO) {
        AppLogger.info("Storage", "Mounting $blockDevice to $mountPoint as ${fsType.label}")
        val res = storageManager.mountSdPartition(blockDevice, mountPoint, fsType)
        if (res.isSuccess) {
            invalidateCache()
            AppLogger.success("Storage", "Mounted $blockDevice -> $mountPoint successfully")
        } else {
            AppLogger.error("Storage", "Mount failed for $blockDevice: ${res.exceptionOrNull()?.message}")
        }
        res
    }

    suspend fun unmountSdPartition(mountPoint: String = "/data/sdext2"): Result<Unit> =
        withContext(Dispatchers.IO) {
            AppLogger.info("Storage", "Unmounting SD partition from $mountPoint")
            val res = storageManager.unmountSdPartition(mountPoint)
            if (res.isSuccess) {
                invalidateCache()
                AppLogger.success("Storage", "Unmounted $mountPoint successfully")
            } else {
                AppLogger.error("Storage", "Unmount failed for $mountPoint: ${res.exceptionOrNull()?.message}")
            }
            res
        }

    suspend fun unmountPartition(partition: PartitionInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            AppLogger.info("Storage", "Unmounting partition ${partition.path} (${partition.name})")
            val res = storageManager.unmountPartition(partition)
            if (res.isSuccess) {
                invalidateCache()
                AppLogger.success("Storage", "Unmounted ${partition.path} successfully")
            } else {
                AppLogger.error("Storage", "Failed to unmount ${partition.path}: ${res.exceptionOrNull()?.message}")
            }
            res
        }

    suspend fun mountPartition(
        partition: PartitionInfo,
        targetMountPoint: String = "/data/sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        AppLogger.info("Storage", "Mounting ${partition.path} -> $targetMountPoint")
        val res = storageManager.mountPartition(partition, targetMountPoint)
        if (res.isSuccess) {
            invalidateCache()
            AppLogger.success("Storage", "Mounted ${partition.path} -> $targetMountPoint successfully")
        } else {
            AppLogger.error("Storage", "Failed to mount ${partition.path}: ${res.exceptionOrNull()?.message}")
        }
        res
    }

    suspend fun formatPartition(
        blockDevice: String,
        fsType: FilesystemType,
        label: String = "sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        AppLogger.info("Format", "Formatting $blockDevice to ${fsType.label} (label=$label)")
        val res = storageManager.formatPartition(blockDevice, fsType, label)
        if (res.isSuccess) {
            invalidateCache()
            AppLogger.success("Format", "Formatted $blockDevice to ${fsType.label} successfully")
        } else {
            AppLogger.error("Format", "Format failed for $blockDevice: ${res.exceptionOrNull()?.message}")
        }
        res
    }

    suspend fun getSdCardDiskInfo(
        targetMountPoint: String = "/data/sdext2",
        force: Boolean = false
    ): SdCardDiskInfo? = withContext(Dispatchers.IO) {
        val disks = getAllDisks(targetMountPoint, force = force)
        disks.firstOrNull { it.diskType == app.mountx.data.model.DiskType.MICRO_SD } ?: disks.firstOrNull()
    }

    suspend fun getAllDisks(
        targetMountPoint: String = "/data/sdext2",
        preScannedPartitions: List<PartitionInfo>? = null,
        force: Boolean = false
    ): List<SdCardDiskInfo> = withContext(Dispatchers.IO) {
        getStorageHierarchy(targetMountPoint, force).disks
    }

    suspend fun repartitionDisk(
        diskPath: String,
        partitions: List<PartitionSchemeConfig>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        AppLogger.info("Repartition", "Starting repartition on $diskPath with ${partitions.size} partitions")
        val res = storageManager.repartitionDisk(diskPath, partitions)
        if (res.isSuccess) {
            invalidateCache()
            AppLogger.success("Repartition", "Disk $diskPath repartitioned and formatted successfully")
        } else {
            AppLogger.error("Repartition", "Repartition failed on $diskPath: ${res.exceptionOrNull()?.message}")
        }
        res
    }

    suspend fun moveGameMountPoints(
        packageName: String,
        mountPoints: List<MountPointConfig>,
        direction: MoveDirection,
        sdBase: String = "/data/sdext2",
        conflictStrategy: ConflictStrategy = ConflictStrategy.OVERWRITE,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        app.mountx.root.RootShell.rootExecutionMutex.withLock {
            storageManager.moveMountPoints(packageName, mountPoints, direction, sdBase, conflictStrategy, onProgress)
        }
    }

    suspend fun cancelCurrentOperation() {
        storageManager.cancelCurrentTransfer()
    }

    suspend fun moveGameData(
        packageName: String,
        direction: MoveDirection,
        target: MigrationTarget = MigrationTarget.ALL,
        sdBase: String = "/data/sdext2",
        conflictStrategy: ConflictStrategy = ConflictStrategy.OVERWRITE,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        app.mountx.root.RootShell.rootExecutionMutex.withLock {
            storageManager.moveGameData(packageName, direction, target, sdBase, conflictStrategy, onProgress)
        }
    }

    suspend fun getDiskIoConfig(diskName: String): Result<DiskIoConfig> = withContext(Dispatchers.IO) {
        storageManager.getDiskIoConfig(diskName)
    }

    suspend fun applyDiskIoConfig(diskName: String, config: DiskIoConfig): Result<Unit> = withContext(Dispatchers.IO) {
        storageManager.applyDiskIoConfig(diskName, config)
    }

    suspend fun executeGlobalTrim(disk: SdCardDiskInfo): Result<String> = withContext(Dispatchers.IO) {
        storageManager.executeGlobalTrim(disk)
    }

    suspend fun executeGlobalTrimStructured(disk: SdCardDiskInfo): Result<GlobalTrimReport> = withContext(Dispatchers.IO) {
        AppLogger.info("TRIM", "Executing global TRIM on ${disk.hardwareTitle}")
        val res = storageManager.executeGlobalTrimStructured(disk)
        if (res.isSuccess) {
            val rep = res.getOrThrow()
            AppLogger.success("TRIM", "Global TRIM completed: ${rep.summary}")
        } else {
            AppLogger.error("TRIM", "Global TRIM failed: ${res.exceptionOrNull()?.message}")
        }
        res
    }

    suspend fun safeUnmountCheckAndRemount(partition: PartitionInfo): Result<FsckReport> = withContext(Dispatchers.IO) {
        AppLogger.info("FSCK", "Guided fsck repair initiated on ${partition.cleanShortName}")
        val res = storageManager.safeUnmountCheckAndRemount(partition)
        if (res.isSuccess) {
            AppLogger.success("FSCK", "Guided fsck repair finished on ${partition.cleanShortName}")
        } else {
            AppLogger.error("FSCK", "Guided fsck repair failed on ${partition.cleanShortName}: ${res.exceptionOrNull()?.message}")
        }
        res
    }

    suspend fun executePartitionTrim(mountPoint: String): Result<String> = withContext(Dispatchers.IO) {
        AppLogger.info("TRIM", "Running fstrim on $mountPoint")
        val res = storageManager.executePartitionTrim(mountPoint)
        if (res.isSuccess) {
            AppLogger.success("TRIM", "TRIM finished for $mountPoint")
        } else {
            AppLogger.error("TRIM", "TRIM failed for $mountPoint: ${res.exceptionOrNull()?.message}")
        }
        res
    }

    suspend fun executeF2fsUrgentGc(diskName: String): Result<Unit> = withContext(Dispatchers.IO) {
        AppLogger.info("F2FS", "Running F2FS Urgent GC on $diskName")
        val res = storageManager.executeF2fsUrgentGc(diskName)
        if (res.isSuccess) {
            AppLogger.success("F2FS", "F2FS Urgent GC completed on $diskName")
        } else {
            AppLogger.error("F2FS", "F2FS Urgent GC failed on $diskName: ${res.exceptionOrNull()?.message}")
        }
        res
    }

    suspend fun runQuickDiskBenchmark(blockDevice: String): Result<BenchmarkResult> = withContext(Dispatchers.IO) {
        AppLogger.info("Benchmark", "Running quick I/O benchmark on $blockDevice")
        val res = storageManager.runQuickDiskBenchmark(blockDevice)
        if (res.isSuccess) {
            val b = res.getOrNull()
            AppLogger.success("Benchmark", "Benchmark finished: ${String.format(java.util.Locale.US, "%.1f", b?.sequentialReadMbPerSec ?: 0.0)} MB/s, ${String.format(java.util.Locale.US, "%.2f", b?.accessLatencyMs ?: 0.0)} ms latency")
        } else {
            AppLogger.error("Benchmark", "Benchmark failed on $blockDevice: ${res.exceptionOrNull()?.message}")
        }
        res
    }

    suspend fun getDiskHardwareDetails(diskName: String): Result<DiskHardwareDetails> = withContext(Dispatchers.IO) {
        storageManager.getDiskHardwareDetails(diskName)
    }

    suspend fun mountAllPartitions(disk: SdCardDiskInfo, sdBase: String = "/data/sdext2"): Result<Unit> = withContext(Dispatchers.IO) {
        AppLogger.info("Storage", "Mounting all partitions on ${disk.hardwareTitle}")
        val res = storageManager.mountAllPartitions(disk, sdBase)
        if (res.isSuccess) {
            AppLogger.success("Storage", "All partitions mounted on ${disk.hardwareTitle}")
        } else {
            AppLogger.error("Storage", "Mount all failed: ${res.exceptionOrNull()?.message}")
        }
        res
    }

    suspend fun unmountAllPartitions(disk: SdCardDiskInfo): Result<Unit> = withContext(Dispatchers.IO) {
        AppLogger.info("Storage", "Ejecting / unmounting all partitions on ${disk.hardwareTitle}")
        val res = storageManager.unmountAllPartitions(disk)
        if (res.isSuccess) {
            AppLogger.success("Storage", "All partitions ejected from ${disk.hardwareTitle}")
        } else {
            AppLogger.error("Storage", "Eject failed: ${res.exceptionOrNull()?.message}")
        }
        res
    }
}

