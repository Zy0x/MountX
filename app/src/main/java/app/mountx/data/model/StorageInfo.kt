package app.mountx.data.model

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
    FAT32("FAT32", "vfat"),
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

enum class DiskType {
    MICRO_SD,
    USB_OTG
}

/** Detailed partition metadata from /proc/partitions, blkid, and /proc/mounts */
data class PartitionInfo(
    val path: String,
    val name: String,
    val diskName: String,
    val partitionNumber: Int = 0,
    val sizeBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val fsType: String = "",
    val mountPoint: String? = null,
    val label: String? = null,
    val uuid: String? = null,
    val isMounted: Boolean = false,
    val isTargetMount: Boolean = false,
    val isPortableMount: Boolean = false,
    val isMountTargetReady: Boolean = false
) {
    val usedPercent: Float
        get() = if (sizeBytes > 0L && usedBytes > 0L) (usedBytes.toFloat() / sizeBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val cleanShortName: String
        get() = if (partitionNumber > 0) "Part $partitionNumber" else name.takeLast(6)

    val shortName: String
        get() = when {
            isTargetMount -> "Part $partitionNumber (Target)"
            isPortableMount -> "Part $partitionNumber (Portable)"
            partitionNumber > 0 && fsType.isNotBlank() -> "Part $partitionNumber (${fsType.uppercase()})"
            partitionNumber > 0 -> "Part $partitionNumber"
            else -> name.takeLast(6)
        }
}

/** Physical MicroSD / USB OTG disk hardware metadata */
data class SdCardDiskInfo(
    val devicePath: String = "/dev/block/mmcblk0",
    val diskName: String = "mmcblk0",
    val vendorName: String = "MicroSD Card",
    val modelName: String = "",
    val totalSizeBytes: Long = 0L,
    val totalUsedBytes: Long = 0L,
    val totalFreeBytes: Long = 0L,
    val diskType: DiskType = DiskType.MICRO_SD,
    val partitions: List<PartitionInfo> = emptyList()
) {
    val hardwareTitle: String get() {
        val modelPart = if (modelName.isNotBlank()) " $modelName" else ""
        return "$vendorName$modelPart".trim()
    }

    val displayName: String get() {
        val modelPart = if (modelName.isNotBlank()) " $modelName" else ""
        return if (totalSizeBytes > 0L) {
            val sizeGb = String.format(java.util.Locale.US, "%.1f GB", totalSizeBytes / (1024.0 * 1024.0 * 1024.0))
            "$vendorName$modelPart ($sizeGb)"
        } else {
            "$vendorName$modelPart".trim()
        }
    }

    val usedPercent: Float
        get() = if (totalSizeBytes > 0L && totalUsedBytes > 0L) (totalUsedBytes.toFloat() / totalSizeBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

/** Specification for creating a partition in the Partition Wizard */
data class PartitionSchemeConfig(
    val partitionIndex: Int = 1,
    val sizeKb: Long = 0L,
    val fsType: FilesystemType = FilesystemType.FAT32,
    val label: String = "STORAGE",
    val isPrimary: Boolean = true
) {
    val sizeBytes: Long get() = sizeKb * 1024L
    val sizeMb: Double get() = sizeKb / 1024.0
    val sizeGb: Double get() = sizeKb / (1024.0 * 1024.0)
}

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

/** Preset tuning profiles for block device I/O */
enum class IoPreset(
    val label: String,
    val readAheadKb: Int,
    val rqAffinity: Int,
    val nrRequests: Int,
    val vfsCachePressure: Int
) {
    GAMING_ULTRA("Gaming Ultra", 2048, 2, 256, 20),
    BALANCED("Balanced", 1024, 1, 128, 50),
    DEFAULT_SYSTEM("Default", 128, 1, 128, 100)
}

/** Disk-level I/O performance tuning configuration */
data class DiskIoConfig(
    val readAheadKb: Int = 2048,
    val scheduler: String = "none",
    val availableSchedulers: List<String> = emptyList(),
    val rqAffinity: Int = 2,
    val nrRequests: Int = 256,
    val vfsCachePressure: Int = 20,
    val isBootPersistent: Boolean = true
)

/** Storage read/write benchmark result */
data class BenchmarkResult(
    val sequentialReadMbPerSec: Double = 0.0,
    val accessLatencyMs: Double = 0.0,
    val sampleSizeBytes: Long = 64L * 1024 * 1024,
    val timestamp: Long = System.currentTimeMillis()
)

/** Hardware inspection metadata from /sys/block/<disk>/device */
data class DiskHardwareDetails(
    val vendor: String = "",
    val productName: String = "",
    val serialNumber: String = "",
    val busClockMhz: String = "",
    val uhsSpeedClass: String = "",
    val isRemovable: Boolean = true
)

enum class FsckStatus {
    CLEAN,
    REPAIRED,
    DIRTY_WARNING,
    ERROR
}

data class FsckReport(
    val status: FsckStatus,
    val summary: String,
    val filesCount: Long = 0,
    val totalFiles: Long = 0,
    val blocksCount: Long = 0,
    val totalBlocks: Long = 0,
    val rawLog: String = ""
)

data class SupportedFilesystemInfo(
    val fsType: FilesystemType,
    val isKernelSupported: Boolean,
    val isToolSupported: Boolean,
    val description: String
) {
    val isFullySupported: Boolean get() = isKernelSupported && isToolSupported
}

data class TrimPartitionResult(
    val partitionName: String,
    val mountPoint: String,
    val rawOutput: String,
    val isSuccess: Boolean,
    val needsCleaning: Boolean = false,
    val notImplemented: Boolean = false,
    val bytesTrimmed: Long = 0L
)

data class GlobalTrimReport(
    val partitionResults: List<TrimPartitionResult>,
    val hasNeedsCleaning: Boolean,
    val dirtyPartitionName: String? = null,
    val dirtyMountPoint: String? = null,
    val summary: String,
    val rawLog: String
)

