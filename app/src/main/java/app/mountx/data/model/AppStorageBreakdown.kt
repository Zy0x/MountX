package app.mountx.data.model

import kotlin.math.roundToInt

/**
 * Detailed storage breakdown representing the 7 storage categories of an Android application:
 * 1. Apk: Base APK and split APKs
 * 2. Dex: Dalvik/ART compiled code (oat/odex/art/vdex)
 * 3. Lib: Native shared libraries (.so)
 * 4. Data: Private internal storage (/data/data/<pkg>) excluding cache
 * 5. Cache: Application internal cache (/data/data/<pkg>/cache + code_cache)
 * 6. Ext 1: Shared external storage on internal media (/data/media/0/Android/data/<pkg>)
 * 7. Ext 2: Secondary MicroSD partition storage ($sdBase/Android/data/<pkg>)
 */
data class AppStorageBreakdown(
    val apkBytes: Long = 0L,
    val dexBytes: Long = 0L,
    val libBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val ext1Bytes: Long = 0L,
    val ext2Bytes: Long = 0L,
    val ext1DataBytes: Long = 0L,
    val ext1ObbBytes: Long = 0L,
    val ext1MediaBytes: Long = 0L,
    val ext2DataBytes: Long = 0L,
    val ext2ObbBytes: Long = 0L,
    val ext2MediaBytes: Long = 0L,
    val customBytes: Long = 0L,
    val isExt1Mounted: Boolean = false,
    val isDataMounted: Boolean = false,
    val isObbMounted: Boolean = false,
    val isMediaMounted: Boolean = false
) {
    /** System & private storage (/data/app + /data/data) */
    val internalBytes: Long
        get() = apkBytes + dexBytes + libBytes + dataBytes + cacheBytes

    /** Physical internal storage occupied by shared Android folders (excluding bind-mounts from SD) */
    val physicalExt1Bytes: Long
        get() {
            var sum = 0L
            if (!isDataMounted) sum += ext1DataBytes
            if (!isObbMounted) sum += ext1ObbBytes
            if (!isMediaMounted) sum += ext1MediaBytes
            return sum
        }

    /** Total data residing physically on phone internal memory (System + unmounted Shared /data/media/0) */
    val phoneInternalBytes: Long
        get() = internalBytes + physicalExt1Bytes

    /** Total data residing physically on secondary MicroSD partition */
    val microSdBytes: Long
        get() = ext2Bytes + customBytes

    /** Total external MicroSD storage (Ext 2 + Custom) */
    val externalBytes: Long
        get() = ext2Bytes + customBytes

    /** Grand total storage physically occupied across all media */
    val totalBytes: Long
        get() = phoneInternalBytes + microSdBytes

    /** Percentage of phone internal storage over total (0 - 100) */
    val internalPercent: Int
        get() = if (totalBytes > 0) ((phoneInternalBytes.toDouble() / totalBytes) * 100).roundToInt() else 0

    /** Percentage of MicroSD storage over total (0 - 100) */
    val externalPercent: Int
        get() = if (totalBytes > 0) 100 - internalPercent else 0

    /** Helper for computing percentage of an arbitrary byte size against totalBytes */
    fun percentOfTotal(bytes: Long): Int {
        return if (totalBytes > 0) ((bytes.toDouble() / totalBytes) * 100).roundToInt() else 0
    }

    /** Returns bytes to transfer based on target scope and direction */
    fun getSizeForScope(target: MigrationTarget, direction: MoveDirection): Long {
        return when (direction) {
            MoveDirection.TO_SD -> when (target) {
                MigrationTarget.ALL -> ext1Bytes
                MigrationTarget.DATA_ONLY -> if (ext1DataBytes > 0L) ext1DataBytes else ext1Bytes
                MigrationTarget.OBB_ONLY -> ext1ObbBytes
            }
            MoveDirection.TO_INTERNAL -> when (target) {
                MigrationTarget.ALL -> ext2Bytes
                MigrationTarget.DATA_ONLY -> if (ext2DataBytes > 0L) ext2DataBytes else ext2Bytes
                MigrationTarget.OBB_ONLY -> ext2ObbBytes
            }
        }
    }
}
