package app.mountx.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an application entry managed by MountX.
 * Stored in Room database table 'apps'.
 */
@Entity(tableName = "apps")
data class AppEntry(
    @PrimaryKey val packageName: String,
    val displayName: String = "",
    val mode: MountMode = MountMode.PKG,
    val mountStatus: MountStatus = MountStatus.UNKNOWN,
    val dataSizeBytes: Long = 0L,
    val isEnabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
    val mountPoints: List<MountPointConfig> = emptyList(),
    val preferredDiskUuid: String? = null
)

/** Backward-compatible typealias for legacy references */
typealias GameEntry = AppEntry
