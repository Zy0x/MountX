package app.mountify.data.repository

import app.mountify.data.model.FilesystemType
import app.mountify.data.model.MoveDirection
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
     * Poll storage info every 5 seconds.
     */
    fun observeStorageInfo(mountPoint: String = "/data/sdext2"): Flow<StorageInfo?> = flow {
        while (true) {
            emit(storageManager.getStorageInfo(mountPoint))
            delay(5000L)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getStorageInfo(mountPoint: String = "/data/sdext2"): StorageInfo? =
        withContext(Dispatchers.IO) {
            storageManager.getStorageInfo(mountPoint)
        }

    suspend fun detectBlockDevices(): List<String> = withContext(Dispatchers.IO) {
        storageManager.detectBlockDevices()
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

    suspend fun formatPartition(
        blockDevice: String,
        fsType: FilesystemType,
        label: String = "sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        storageManager.formatPartition(blockDevice, fsType, label)
    }

    suspend fun moveGameData(
        packageName: String,
        direction: MoveDirection,
        sdBase: String = "/data/sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        storageManager.moveGameData(packageName, direction, sdBase)
    }
}
