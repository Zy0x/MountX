package app.mountx.data.model

/**
 * High-level categories for Universal Smart Directory Classification
 */
enum class MountPointCategory {
    EXTERNAL_DATA,     // /Android/data/<pkg>/files
    OBB_STORAGE,       // /Android/obb/<pkg>
    GAME_ASSETS,       // Legacy compatibility category (files/obb)
    MEDIA_DOWNLOADS,   // /sdcard/<App>/ and /Android/media/<pkg>/ with auto .nomedia
    CACHE_SHADERS,     // /Android/data/<pkg>/cache and GPU shaders
    CUSTOM,            // User-defined manual path binding
    PRIVATE_INTERNAL,  // /data/data/<pkg> (>1GB) using Virtual Ext4 Loop Container
    APP_PACKAGE        // /data/app/<pkg> Advanced Experimental
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
) {
    /**
     * Resolves normalized category based on targetPath and ID to prevent misclassification
     * (e.g., obb paths labeled as GAME_ASSETS).
     */
    fun resolveCategory(): MountPointCategory {
        return when {
            targetPath.contains("/Android/obb", ignoreCase = true) ||
                    sourcePath.contains("/Android/obb", ignoreCase = true) ||
                    id.contains("obb", ignoreCase = true) -> MountPointCategory.OBB_STORAGE

            targetPath.contains("/Android/data", ignoreCase = true) ||
                    sourcePath.contains("/Android/data", ignoreCase = true) ||
                    id.contains("data", ignoreCase = true) ||
                    id.contains("files", ignoreCase = true) ||
                    id.contains("pkg", ignoreCase = true) -> MountPointCategory.EXTERNAL_DATA

            targetPath.contains("/Android/media", ignoreCase = true) ||
                    sourcePath.contains("/Android/media", ignoreCase = true) -> MountPointCategory.MEDIA_DOWNLOADS

            targetPath.contains("/data/data", ignoreCase = true) ||
                    targetPath.contains("/data/user/0", ignoreCase = true) -> MountPointCategory.PRIVATE_INTERNAL

            targetPath.contains("/data/app", ignoreCase = true) -> MountPointCategory.APP_PACKAGE

            category == MountPointCategory.GAME_ASSETS -> MountPointCategory.EXTERNAL_DATA
            else -> category
        }
    }

    /**
     * Returns clean relative target path suitable for UI display (e.g. Android/data/com.pkg).
     */
    fun getCleanRelativePath(): String {
        val idx = targetPath.indexOf("Android/")
        return if (idx >= 0) {
            targetPath.substring(idx)
        } else {
            val lastSlash = targetPath.lastIndexOf('/')
            if (lastSlash >= 0 && lastSlash < targetPath.length - 1) {
                targetPath.substring(lastSlash + 1)
            } else {
                targetPath
            }
        }
    }

    /**
     * Returns whether this mount point belongs to core game data (Data or OBB).
     */
    fun isCoreGameData(): Boolean {
        val cat = resolveCategory()
        return cat == MountPointCategory.EXTERNAL_DATA || cat == MountPointCategory.OBB_STORAGE
    }
}
