package app.mihon.root

import app.mihon.data.model.FilesystemType
import app.mihon.data.model.MoveDirection
import app.mihon.data.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Low-level storage and partition manager using root commands.
 * Handles device discovery, mounting/unmounting partitions, formatting (f2fs/ext4/etc),
 * and migrating physical game data between internal storage and MicroSD.
 */
class StorageManager {

    /**
     * Detect potential MicroSD block devices and partitions.
     * Scans /dev/block for mmcblk[0-9]p* and sd[a-z][0-9] devices.
     */
    suspend fun detectBlockDevices(): List<String> = withContext(Dispatchers.IO) {
        val result = RootShell.exec("ls /dev/block/mmcblk* /dev/block/sd* 2>/dev/null")
        if (!result.isSuccess) return@withContext emptyList()

        result.stdout
            .map { it.trim() }
            .filter { line ->
                // Keep partition blocks like mmcblk0p2, mmcblk1p1, sda1, sdb2
                line.matches(Regex(".*/(mmcblk[0-9]+p[0-9]+|sd[a-z][0-9]+)$"))
            }
            .distinct()
            .sorted()
    }

    /**
     * Mount an external MicroSD partition to the specified mount point.
     */
    suspend fun mountSdPartition(
        blockDevice: String,
        mountPoint: String = "/data/sdext2",
        fsType: FilesystemType = FilesystemType.F2FS
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            RootShell.exec("mkdir -p \"$mountPoint\" 2>/dev/null")
            
            // Try primary fsType first
            val primaryCmd = "mount -t ${fsType.command} -o noatime,rw \"$blockDevice\" \"$mountPoint\""
            var res = RootShell.exec(primaryCmd)
            
            // If failed and not ext4, try fallback to ext4 or auto
            if (!res.isSuccess) {
                val fallbackCmd = "mount -t ext4 -o noatime,rw \"$blockDevice\" \"$mountPoint\" 2>/dev/null || mount \"$blockDevice\" \"$mountPoint\""
                res = RootShell.exec(fallbackCmd)
            }

            if (!RootShell.isMountpoint(mountPoint)) {
                error("Failed to mount $blockDevice to $mountPoint: ${res.stderr.joinToString("\n")}")
            }
        }
    }

    /**
     * Unmount the SD partition.
     */
    suspend fun unmountSdPartition(mountPoint: String = "/data/sdext2"): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (RootShell.isMountpoint(mountPoint)) {
                    val res = RootShell.exec("umount -f -l \"$mountPoint\" 2>/dev/null")
                    if (!res.isSuccess && RootShell.isMountpoint(mountPoint)) {
                        error("Failed to unmount $mountPoint: ${res.stderr.joinToString("\n")}")
                    }
                }
            }
        }

    /**
     * Query storage statistics for the given mount point (total, used, free space).
     */
    suspend fun getStorageInfo(mountPoint: String = "/data/sdext2"): StorageInfo? =
        withContext(Dispatchers.IO) {
            val isMounted = RootShell.isMountpoint(mountPoint)
            if (!isMounted) return@withContext null

            // Find block device & filesystem from /proc/mounts
            val mountsRes = RootShell.exec("grep \" $mountPoint \" /proc/mounts | head -n 1")
            val mountParts = mountsRes.output.trim().split(Regex("\\s+"))
            val blockDevice = mountParts.getOrNull(0) ?: ""
            val filesystem = mountParts.getOrNull(2) ?: ""

            // Use df to get sizes in 1K blocks: df -k /data/sdext2
            val dfRes = RootShell.exec("df -k \"$mountPoint\" | tail -n 1")
            val dfParts = dfRes.output.trim().split(Regex("\\s+"))

            if (dfParts.size >= 4) {
                val total1k = dfParts.getOrNull(1)?.toLongOrNull() ?: 0L
                val used1k = dfParts.getOrNull(2)?.toLongOrNull() ?: 0L
                val free1k = dfParts.getOrNull(3)?.toLongOrNull() ?: 0L

                StorageInfo(
                    blockDevice = blockDevice,
                    mountPoint = mountPoint,
                    filesystem = filesystem,
                    totalBytes = total1k * 1024L,
                    usedBytes = used1k * 1024L,
                    freeBytes = free1k * 1024L,
                    isMounted = true
                )
            } else {
                StorageInfo(
                    blockDevice = blockDevice,
                    mountPoint = mountPoint,
                    filesystem = filesystem,
                    totalBytes = 0L,
                    usedBytes = 0L,
                    freeBytes = 0L,
                    isMounted = true
                )
            }
        }

    /**
     * Format a block partition with a chosen filesystem.
     * CAUTION: Destructive operation.
     */
    suspend fun formatPartition(
        blockDevice: String,
        fsType: FilesystemType,
        label: String = "sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Unmount first if currently mounted
            RootShell.exec("umount -f \"$blockDevice\" 2>/dev/null")

            val cmd = when (fsType) {
                FilesystemType.F2FS -> "mkfs.f2fs -l \"$label\" -f \"$blockDevice\""
                FilesystemType.EXT4 -> "mkfs.ext4 -L \"$label\" -F \"$blockDevice\""
                FilesystemType.EXFAT -> "mkfs.exfat -n \"$label\" \"$blockDevice\""
                FilesystemType.NTFS -> "mkfs.ntfs -f -L \"$label\" \"$blockDevice\""
            }

            val res = RootShell.exec(cmd)
            if (!res.isSuccess) {
                error("Formatting $blockDevice with ${fsType.label} failed: ${res.output}\n${res.stderr.joinToString("\n")}")
            }
        }
    }

    /**
     * Migrate physical game data between Internal Storage and MicroSD.
     * Handles directory creation, cp/mv, permission restoration, and cleanup.
     */
    suspend fun moveGameData(
        packageName: String,
        direction: MoveDirection,
        sdBase: String = "/data/sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val internalPath = "/data/media/0/Android/data/$packageName"
            val sdPath = "$sdBase/Android/data/$packageName"

            when (direction) {
                MoveDirection.TO_SD -> {
                    if (!RootShell.exists(internalPath)) {
                        error("Internal data path does not exist: $internalPath")
                    }
                    // Ensure destination directory on MicroSD
                    RootShell.exec("mkdir -p \"$sdBase/Android/data\"")

                    // Copy data from internal to SD
                    val copyCmd = "cp -a \"$internalPath\" \"$sdBase/Android/data/\""
                    val copyRes = RootShell.exec(copyCmd)
                    if (!copyRes.isSuccess) {
                        error("Failed to copy game data to SD: ${copyRes.stderr.joinToString("\n")}")
                    }

                    // Verify copy
                    if (!RootShell.exists(sdPath)) {
                        error("Verification failed: SD data directory not found after copy.")
                    }

                    // Set permissions on SD
                    RootShell.exec("chmod -R 777 \"$sdPath\"")
                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$sdPath\"")

                    // Remove original internal data folder content to free space
                    // Keep the folder itself or let bind mount take over
                    RootShell.exec("rm -rf \"$internalPath\"/*")
                }
                MoveDirection.TO_INTERNAL -> {
                    if (!RootShell.exists(sdPath)) {
                        error("SD data path does not exist: $sdPath")
                    }
                    RootShell.exec("mkdir -p \"/data/media/0/Android/data\"")

                    // Copy back to internal
                    val copyCmd = "cp -a \"$sdPath\" \"/data/media/0/Android/data/\""
                    val copyRes = RootShell.exec(copyCmd)
                    if (!copyRes.isSuccess) {
                        error("Failed to copy game data back to internal: ${copyRes.stderr.joinToString("\n")}")
                    }

                    // Remove SD copy
                    RootShell.exec("rm -rf \"$sdPath\"")
                }
            }
        }
    }
}
