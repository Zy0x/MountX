package app.mountify.data.model

/** Root solutions supported by Mountify */
enum class RootSolution {
    MAGISK,
    KERNELSU,
    APATCH,
    NONE
}

/** Filesystem types for formatting MicroSD partitions */
enum class FilesystemType(
    val label: String,
    val command: String,
    val isRecommended: Boolean = false
) {
    F2FS("F2FS", "f2fs", isRecommended = true),
    EXT4("Ext4", "ext4"),
    EXFAT("exFAT", "exfat"),
    NTFS("NTFS", "ntfs")
}

/** Direction for moving game data */
enum class MoveDirection { TO_SD, TO_INTERNAL }

/** Target scope for migrating game storage */
enum class MigrationTarget {
    ALL,
    DATA_ONLY,
    OBB_ONLY
}

/** Information about the MicroSD storage partition */
data class StorageInfo(
    val blockDevice: String,
    val mountPoint: String,
    val filesystem: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val isMounted: Boolean
) {
    val usedPercent: Float get() =
        if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f
}

/** Internal device storage telemetry (/data) */
data class InternalStorageInfo(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L
) {
    val usedPercent: Float
        get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f
}

/** Detailed partition metadata from /proc/partitions, blkid, and /proc/mounts */
data class PartitionInfo(
    val path: String,
    val name: String,
    val diskName: String,
    val partitionNumber: Int = 0,
    val sizeBytes: Long = 0L,
    val fsType: String = "",
    val mountPoint: String? = null,
    val label: String? = null,
    val uuid: String? = null,
    val isMounted: Boolean = false,
    val isTargetMount: Boolean = false,
    val isSuitableForApp2sd: Boolean = false
)

/** Overall app status shown on Dashboard */
data class AppStatus(
    val rootSolution: RootSolution = RootSolution.NONE,
    val isModuleInstalled: Boolean = false,
    val moduleVersion: String = "",
    val storageInfo: StorageInfo? = null,
    val mountedGamesCount: Int = 0,
    val totalGamesCount: Int = 0
)

/** GitHub Release info for update checker */
data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val changelog: String,
    val isUpdateAvailable: Boolean
)

/** Result of a shell command execution */
data class ShellResult(
    val stdout: List<String>,
    val stderr: List<String>,
    val code: Int
) {
    val isSuccess: Boolean get() = code == 0
    val output: String get() = stdout.joinToString("\n")
}
