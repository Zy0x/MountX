package app.mountify.data.repository

import app.mountify.data.model.FilesystemType
import app.mountify.data.model.InternalStorageInfo
import app.mountify.data.model.MigrationTarget
import app.mountify.data.model.MoveDirection
import app.mountify.data.model.PartitionInfo
import app.mountify.data.model.PartitionSchemeConfig
import app.mountify.data.model.SdCardDiskInfo
import app.mountify.data.model.StorageInfo
import app.mountify.root.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(
    private val storageManager: StorageManager
) {

    /**
     * Poll storage info every 10 seconds.
     */
    fun observeStorageInfo(mountPoint: String = "/data/sdext2"): Flow<StorageInfo?> = flow {
        while (true) {
            emit(storageManager.getStorageInfo(mountPoint))
            delay(10000L)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Poll internal device storage (/data) every 10 seconds.
     */
    fun observeInternalStorage(): Flow<InternalStorageInfo?> = flow {
        while (true) {
            emit(storageManager.getInternalStorageInfo())
            delay(10000L)
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

    suspend fun detectPartitions(targetMountPoint: String = "/data/sdext2"): List<PartitionInfo> =
        withContext(Dispatchers.IO) {
            storageManager.detectPartitions(targetMountPoint)
        }

    suspend fun checkFilesystem(blockDevice: String, fsType: String = ""): Result<String> =
        withContext(Dispatchers.IO) {
            storageManager.checkFilesystem(blockDevice, fsType)
        }

    suspend fun mountSdPartition(
        blockDevice: String,
        mountPoint: String = "/data/sdext2",
        fsType: FilesystemType = FilesystemType.F2FS
    ): Result<Unit> = withContext(Dispatchers.IO) {
        storageManager.mountSdPartition(blockDevice, mountPoint, fsType)
    }

    suspend fun unmountSdPartition(mountPoint: String = "/data/sdext2"): Result<Unit> =
        withContext(Dispatchers.IO) {
            storageManager.unmountSdPartition(mountPoint)
        }

    suspend fun unmountPartition(partition: PartitionInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            storageManager.unmountPartition(partition)
        }

    suspend fun mountPartition(
        partition: PartitionInfo,
        targetMountPoint: String = "/data/sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        storageManager.mountPartition(partition, targetMountPoint)
    }

    suspend fun formatPartition(
        blockDevice: String,
        fsType: FilesystemType,
        label: String = "sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        storageManager.formatPartition(blockDevice, fsType, label)
    }

    suspend fun getSdCardDiskInfo(targetMountPoint: String = "/data/sdext2"): SdCardDiskInfo? =
        withContext(Dispatchers.IO) {
            storageManager.detectSdCardDiskInfo(targetMountPoint)
        }

    suspend fun getAllDisks(targetMountPoint: String = "/data/sdext2"): List<SdCardDiskInfo> =
        withContext(Dispatchers.IO) {
            storageManager.detectAllDisks(targetMountPoint)
        }

    suspend fun repartitionDisk(
        diskPath: String,
        partitions: List<PartitionSchemeConfig>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        storageManager.repartitionDisk(diskPath, partitions)
    }

    suspend fun moveGameData(
        packageName: String,
        direction: MoveDirection,
        target: MigrationTarget = MigrationTarget.ALL,
        sdBase: String = "/data/sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        storageManager.moveGameData(packageName, direction, target, sdBase)
    }
}
