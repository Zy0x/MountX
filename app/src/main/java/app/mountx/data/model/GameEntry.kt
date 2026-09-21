package app.mountx.data.model

/** Mount mode determines how app data is bind-mounted from MicroSD */
enum class MountMode {
    /** Mount the entire Android/data/PKG directory */
    PKG,
    /** Mount only Android/data/PKG/files (keeps databases on internal) */
    FILES
}

/** Current mount status of an app */
enum class MountStatus {
    MOUNTED,
    UNMOUNTED,
    ERROR,
    NEED_MIGRATION,
    UNKNOWN,
    DISK_DETACHED
}

/**
 * Metadata for installed application item shown in AddAppSheet.
 * Marked @Immutable for zero-recomposition 120 FPS LazyColumn scrolling.
 */
@androidx.compose.runtime.Immutable
data class InstalledAppInfo(
    val packageName: String,
    val displayName: String,
    val isGame: Boolean = false,
    val isSystemApp: Boolean = false,
    val hasPreset: Boolean = false
)
