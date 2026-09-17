package app.mountify.root

import app.mountify.data.model.FilesystemType
import app.mountify.data.model.MigrationTarget
import app.mountify.data.model.MoveDirection
import app.mountify.data.model.StorageInfo
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
     * Supports granular migration target: ALL, DATA_ONLY, OBB_ONLY.
     */
    suspend fun moveGameData(
        packageName: String,
        direction: MoveDirection,
        target: MigrationTarget = MigrationTarget.ALL,
        sdBase: String = "/data/sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = RootShell.exec(
                "pm list packages -U 2>/dev/null | grep -F \"package:$packageName\" | sed -n 's/.*uid:\\([0-9]*\\).*/\\1/p' | head -n 1"
            ).output.trim().toIntOrNull() ?: 10000

            val moveData = (target == MigrationTarget.ALL || target == MigrationTarget.DATA_ONLY)
            val moveObb = (target == MigrationTarget.ALL || target == MigrationTarget.OBB_ONLY)

            val internalData = "/data/media/0/Android/data/$packageName"
            val sdData = "$sdBase/Android/data/$packageName"
            val internalObb = "/data/media/0/Android/obb/$packageName"
            val sdObb = "$sdBase/Android/obb/$packageName"

            when (direction) {
                MoveDirection.TO_SD -> {
                    // 1. Move Data if requested
                    if (moveData) {
                        if (RootShell.exists(internalData)) {
                            RootShell.exec("mkdir -p \"$sdBase/Android/data\"")
                            val copyRes = RootShell.exec("cp -a \"$internalData\" \"$sdBase/Android/data/\"")
                            if (!copyRes.isSuccess) {
                                error("Failed to copy game data to SD: ${copyRes.stderr.joinToString("\n")}")
                            }
                            if (!RootShell.exists(sdData)) {
                                error("Verification failed: SD data directory not found after copy.")
                            }
                            RootShell.exec("chown -R $uid:1023 \"$sdData\"")
                            RootShell.exec("chmod -R 777 \"$sdData\"")
                            RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$sdData\"")
                            // Clean original internal data content to free space
                            RootShell.exec("rm -rf \"$internalData\"/*")
                        } else if (target == MigrationTarget.DATA_ONLY) {
                            error("Internal data path does not exist: $internalData")
                        }
                    }

                    // 2. Move OBB if requested
                    if (moveObb) {
                        if (RootShell.exists(internalObb)) {
                            RootShell.exec("mkdir -p \"$sdBase/Android/obb\"")
                            val copyRes = RootShell.exec("cp -a \"$internalObb\" \"$sdBase/Android/obb/\"")
                            if (!copyRes.isSuccess) {
                                error("Failed to copy game OBB to SD: ${copyRes.stderr.joinToString("\n")}")
                            }
                            if (!RootShell.exists(sdObb)) {
                                error("Verification failed: SD OBB directory not found after copy.")
                            }
                            RootShell.exec("chown -R $uid:1023 \"$sdObb\"")
                            RootShell.exec("chmod -R 777 \"$sdObb\"")
                            RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$sdObb\"")
                            // Clean original internal OBB content to free space
                            RootShell.exec("rm -rf \"$internalObb\"/*")
                        } else if (target == MigrationTarget.OBB_ONLY) {
                            error("Internal OBB path does not exist: $internalObb")
                        }
                    }
                }
                MoveDirection.TO_INTERNAL -> {
                    // 1. Restore Data if requested
                    if (moveData) {
                        if (RootShell.exists(sdData)) {
                            RootShell.exec("mkdir -p \"/data/media/0/Android/data\"")
                            val copyRes = RootShell.exec("cp -a \"$sdData\" \"/data/media/0/Android/data/\"")
                            if (!copyRes.isSuccess) {
                                error("Failed to copy game data back to internal: ${copyRes.stderr.joinToString("\n")}")
                            }
                            if (!RootShell.exists(internalData)) {
                                error("Verification failed: Internal data directory not found after restore.")
                            }
                            RootShell.exec("rm -rf \"$sdData\"")
                        } else if (target == MigrationTarget.DATA_ONLY) {
                            error("SD data path does not exist: $sdData")
                        }
                    }

                    // 2. Restore OBB if requested
                    if (moveObb) {
                        if (RootShell.exists(sdObb)) {
                            RootShell.exec("mkdir -p \"/data/media/0/Android/obb\"")
                            val copyRes = RootShell.exec("cp -a \"$sdObb\" \"/data/media/0/Android/obb/\"")
                            if (!copyRes.isSuccess) {
                                error("Failed to copy game OBB back to internal: ${copyRes.stderr.joinToString("\n")}")
                            }
                            if (!RootShell.exists(internalObb)) {
                                error("Verification failed: Internal OBB directory not found after restore.")
                            }
                            RootShell.exec("rm -rf \"$sdObb\"")
                        } else if (target == MigrationTarget.OBB_ONLY) {
                            error("SD OBB path does not exist: $sdObb")
                        }
                    }
                }
            }
            Unit
        }
    }
}
