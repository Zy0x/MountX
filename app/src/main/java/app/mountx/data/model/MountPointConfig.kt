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
 * Migration storage method for internal/sandbox categories (Private Data, Cache, APK, Lib).
 */
enum class InternalMigrationMethod {
    AUTO,           // Adaptive: Method 1 (Native Folder) on POSIX (EXT4/F2FS), Method 2 (.img) on Non-POSIX (exFAT/FAT32/NTFS)
    NATIVE_FOLDER,  // Method 1: Direct Native Folder (Supported on EXT4 & F2FS only)
    CONTAINER_IMG   // Method 2: Virtual Sparse EXT4 Container .img (Supported on all filesystems)
}

/**
 * Represents a single bind-mount or virtual container target for an application.
 */
@androidx.compose.runtime.Immutable
data class MountPointConfig(
    val id: String,
    val category: MountPointCategory,
    val sourcePath: String,
    val targetPath: String,
    val enabled: Boolean = true,
    val isVirtualContainer: Boolean = false,
    val containerImgPath: String? = null,
    val sizeBytes: Long = 0L,
    val diskUuid: String? = null,
    val label: String? = null,
    val preserveMedia: Boolean = false,
    val migrationMethod: InternalMigrationMethod = InternalMigrationMethod.AUTO
) {
    /**
     * Determines whether this target requires a virtual Ext4 loopback container
     * (e.g. for /data/data or .img container files).
     */
    fun isEffectivelyVirtualContainer(): Boolean {
        return isVirtualContainer ||
                sourcePath.endsWith(".img", ignoreCase = true) ||
                (!containerImgPath.isNullOrBlank())
    }

    /**
     * Returns true if this mount point belongs to an internal/system category
     * (Private Data, Cache, APK, or Native Libraries) that supports the 3-method engine.
     */
    fun isInternalSandboxOrPackageCategory(): Boolean {
        val cat = resolveCategory()
        return cat == MountPointCategory.PRIVATE_INTERNAL ||
                cat == MountPointCategory.CACHE_SHADERS ||
                cat == MountPointCategory.APP_PACKAGE
    }

    /**
     * Returns true if this APP_PACKAGE mount point specifically targets the native libraries (/lib) folder.
     */
    fun isLibTarget(): Boolean {
        return id == "lib" ||
                id.startsWith("lib_") ||
                targetPath.contains("/lib") ||
                sourcePath.contains("/lib") ||
                sourcePath.endsWith("_lib.img", ignoreCase = true) ||
                (containerImgPath?.endsWith("_lib.img", ignoreCase = true) == true)
    }

    /**
     * Resolves the standardized Sparse EXT4 .img container path on the target MicroSD partition.
     */
    fun resolveContainerImgPath(sdBase: String, packageName: String): String {
        val cleanBase = sdBase.trimEnd('/')
        return when (resolveCategory()) {
            MountPointCategory.PRIVATE_INTERNAL -> "$cleanBase/.mountx/containers/${packageName}_data.img"
            MountPointCategory.CACHE_SHADERS -> "$cleanBase/.mountx/containers/${packageName}_cache.img"
            MountPointCategory.APP_PACKAGE -> {
                if (isLibTarget()) {
                    "$cleanBase/.mountx/containers/${packageName}_lib.img"
                } else {
                    "$cleanBase/.mountx/containers/${packageName}_apk.img"
                }
            }
            else -> "$cleanBase/.mountx/containers/${packageName}_${id}.img"
        }
    }

    /**
     * Resolves the standardized Direct Native Folder path on the target MicroSD partition.
     */
    fun resolveNativeFolderPath(sdBase: String, packageName: String): String {
        val cleanBase = sdBase.trimEnd('/')
        return when (resolveCategory()) {
            MountPointCategory.PRIVATE_INTERNAL -> "$cleanBase/MountX/data/$packageName"
            MountPointCategory.CACHE_SHADERS -> "$cleanBase/MountX/cache/$packageName"
            MountPointCategory.APP_PACKAGE -> {
                if (isLibTarget()) {
                    "$cleanBase/MountX/lib/$packageName"
                } else {
                    "$cleanBase/MountX/app/$packageName"
                }
            }
            MountPointCategory.EXTERNAL_DATA -> "$cleanBase/Android/data/$packageName"
            MountPointCategory.OBB_STORAGE -> "$cleanBase/Android/obb/$packageName"
            MountPointCategory.MEDIA_DOWNLOADS -> "$cleanBase/Android/media/$packageName"
            else -> sourcePath
        }
    }

    /**
     * Resolves normalized category based on targetPath and ID to prevent misclassification
     * (e.g., obb paths labeled as GAME_ASSETS).
     */
    fun resolveCategory(): MountPointCategory {
        return when {
            targetPath.contains("/Android/obb", ignoreCase = true) ||
                    sourcePath.contains("/Android/obb", ignoreCase = true) ||
                    id.contains("obb", ignoreCase = true) -> MountPointCategory.OBB_STORAGE

            category == MountPointCategory.CACHE_SHADERS ||
                    targetPath.contains("/cache", ignoreCase = true) ||
                    sourcePath.contains("/cache", ignoreCase = true) ||
                    id.contains("cache", ignoreCase = true) -> MountPointCategory.CACHE_SHADERS

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
     * Determines whether two MountPointConfigs represent the exact same functional storage target,
     * cleanly distinguishing between APK packages and native libraries (/lib).
     */
    fun isSameTargetAs(other: MountPointConfig): Boolean {
        if (this.id == other.id) return true
        if (this.targetPath.isNotBlank() && other.targetPath.isNotBlank() &&
            this.targetPath.trimEnd('/') == other.targetPath.trimEnd('/')) return true
        val catA = this.resolveCategory()
        val catB = other.resolveCategory()
        if (catA == MountPointCategory.APP_PACKAGE && catB == MountPointCategory.APP_PACKAGE) {
            return this.isLibTarget() == other.isLibTarget()
        }
        return catA == catB && catA != MountPointCategory.CUSTOM
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

    /**
     * Resolves the target disk base directory for this mount point given available disks.
     */
    fun resolveTargetDiskBase(availableDisks: List<SdCardDiskInfo>, defaultBase: String): String {
        if (diskUuid != null) {
            val matched = availableDisks.firstOrNull { it.uuid == diskUuid }
            if (matched != null) return matched.mountPath
        }
        return defaultBase
    }
}

/**
 * Target storage location for deleting category data.
 */
enum class CategoryDeleteLocation {
    INTERNAL_ONLY,
    SD_ONLY,
    BOTH
}
