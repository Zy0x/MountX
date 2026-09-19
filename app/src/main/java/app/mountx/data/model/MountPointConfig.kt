package app.mountx.data.model

/**
 * High-level categories for Universal Smart Directory Classification
 */
enum class MountPointCategory {
    GAME_ASSETS,       // /Android/data/<pkg>/files and /Android/obb/<pkg>
    MEDIA_DOWNLOADS,   // /sdcard/<App>/ and /Android/media/<pkg>/ with auto .nomedia
    CACHE_SHADERS,     // /Android/data/<pkg>/cache and GPU shaders
    CUSTOM,            // User-defined manual path binding
    PRIVATE_INTERNAL   // /data/data/<pkg> (>1GB) using Virtual Ext4 Loop Container
}

/**
 * Represents a single bind-mount or virtual container target for an application.
 */
data class MountPointConfig(
    val id: String,
    val category: MountPointCategory,
    val sourcePath: String,
    val targetPath: String,
    val enabled: Boolean = true,
    val isVirtualContainer: Boolean = false,
    val containerImgPath: String? = null,
    val sizeBytes: Long = 0L
)
