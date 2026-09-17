package app.mountify.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Mount mode determines how game data is bind-mounted from MicroSD */
enum class MountMode {
    /** Mount the entire Android/data/PKG directory */
    PKG,
    /** Mount only Android/data/PKG/files (keeps databases on internal) */
    FILES
}

/** Current mount status of a game */
enum class MountStatus {
    MOUNTED,
    UNMOUNTED,
    ERROR,
    UNKNOWN
}

/**
 * Represents a game entry managed by Mountify.
 * Stored in Room database.
 */
@Entity(tableName = "games")
data class GameEntry(
    @PrimaryKey val packageName: String,
    val displayName: String = "",
    val mode: MountMode = MountMode.PKG,
    val mountStatus: MountStatus = MountStatus.UNKNOWN,
    val dataSizeBytes: Long = 0L,
    val isEnabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Metadata for installed application item shown in AddGameSheet.
 */
data class InstalledAppInfo(
    val packageName: String,
    val displayName: String,
    val isGame: Boolean = false,
    val isSystemApp: Boolean = false
)
