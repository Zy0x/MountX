package app.mihon.data.model

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
