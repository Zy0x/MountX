package app.mountify.root

import app.mountify.data.model.FilesystemType
import app.mountify.data.model.InternalStorageInfo
import app.mountify.data.model.MigrationTarget
import app.mountify.data.model.MoveDirection
import app.mountify.data.model.PartitionInfo
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
     * Query internal storage (/data) statistics (total, used, free space).
     */
    suspend fun getInternalStorageInfo(): InternalStorageInfo? = withContext(Dispatchers.IO) {
        val dfRes = RootShell.exec("df -k /data 2>/dev/null | tail -n 1")
        val dfParts = dfRes.output.trim().split(Regex("\\s+"))
        if (dfParts.size >= 4) {
            val total1k = dfParts.getOrNull(1)?.toLongOrNull() ?: 0L
            val used1k = dfParts.getOrNull(2)?.toLongOrNull() ?: 0L
            val free1k = dfParts.getOrNull(3)?.toLongOrNull() ?: 0L
            InternalStorageInfo(
                totalBytes = total1k * 1024L,
                usedBytes = used1k * 1024L,
                freeBytes = free1k * 1024L
            )
        } else {
            null
        }
    }

    /**
     * Comprehensive scan of all MicroSD/USB block devices and partitions.
     * Parses /proc/partitions, /proc/mounts, and blkid to obtain full metadata
     * (disk name, partition number, size, filesystem, mount point, label, UUID).
     */
    suspend fun detectPartitions(targetMountPoint: String = "/data/sdext2"): List<PartitionInfo> = withContext(Dispatchers.IO) {
        val mountsRes = RootShell.exec("cat /proc/mounts 2>/dev/null")
        val mountedMap = mutableMapOf<String, Pair<String, String>>()
        mountsRes.stdout.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 3) {
                val dev = parts[0]
                val mnt = parts[1]
                val fs = parts[2]
                mountedMap[dev] = Pair(mnt, fs)
            }
        }

        val partitionsRes = RootShell.exec("cat /proc/partitions 2>/dev/null")
        val partitionItems = mutableListOf<PartitionInfo>()

        if (partitionsRes.isSuccess && partitionsRes.stdout.size > 2) {
            for (line in partitionsRes.stdout) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val blocks = parts[2].toLongOrNull() ?: continue
                    val name = parts[3]

                    val isMmcPartition = name.matches(Regex("mmcblk[0-9]+p[0-9]+"))
                    val isSdPartition = name.matches(Regex("sd[a-z][0-9]+"))
                    if (!isMmcPartition && !isSdPartition) continue

                    val path = "/dev/block/$name"
                    val diskName = if (isMmcPartition) {
                        name.substringBeforeLast("p")
                    } else {
                        name.filter { it.isLetter() }
                    }
                    val partNum = if (isMmcPartition) {
                        name.substringAfterLast("p").toIntOrNull() ?: 1
                    } else {
                        name.filter { it.isDigit() }.toIntOrNull() ?: 1
                    }
                    val sizeBytes = blocks * 1024L

                    val mountInfo = mountedMap[path]
                        ?: mountedMap.entries.firstOrNull { it.key.endsWith("/$name") }?.value
                    val isMounted = mountInfo != null
                    val mountPoint = mountInfo?.first
                    var fsType = mountInfo?.second ?: ""

                    var label: String? = null
                    var uuid: String? = null

                    val blkidRes = RootShell.exec("blkid \"$path\" 2>/dev/null || toybox blkid \"$path\" 2>/dev/null")
                    if (blkidRes.isSuccess && blkidRes.output.isNotBlank()) {
                        val out = blkidRes.output
                        val typeMatch = Regex("TYPE=\"([^\"]+)\"").find(out)
                        val labelMatch = Regex("LABEL=\"([^\"]+)\"").find(out)
                        val uuidMatch = Regex("UUID=\"([^\"]+)\"").find(out)

                        if (typeMatch != null && fsType.isBlank()) {
                            fsType = typeMatch.groupValues[1]
                        }
                        label = labelMatch?.groupValues?.get(1)
                        uuid = uuidMatch?.groupValues?.get(1)
                    }

                    val isTargetMount = mountPoint == targetMountPoint
                    val isLinuxFs = fsType.equals("f2fs", ignoreCase = true) || fsType.equals("ext4", ignoreCase = true)
                    val isSuitable = isTargetMount || isLinuxFs || (partNum >= 2 && !isMounted)

                    partitionItems.add(
                        PartitionInfo(
                            path = path,
                            name = name,
                            diskName = diskName,
                            partitionNumber = partNum,
                            sizeBytes = sizeBytes,
                            fsType = fsType,
                            mountPoint = mountPoint,
                            label = label,
                            uuid = uuid,
                            isMounted = isMounted,
                            isTargetMount = isTargetMount,
                            isSuitableForApp2sd = isSuitable
                        )
                    )
                }
            }
        }

        if (partitionItems.isEmpty()) {
            val fallbackDevices = detectBlockDevices()
            fallbackDevices.forEach { devPath ->
                val name = devPath.substringAfterLast("/")
                val mountInfo = mountedMap[devPath]
                val isMounted = mountInfo != null
                partitionItems.add(
                    PartitionInfo(
                        path = devPath,
                        name = name,
                        diskName = name.substringBeforeLast("p"),
                        partitionNumber = name.filter { it.isDigit() }.toIntOrNull() ?: 1,
                        sizeBytes = 0L,
                        fsType = mountInfo?.second ?: "",
                        mountPoint = mountInfo?.first,
                        isMounted = isMounted,
                        isTargetMount = mountInfo?.first == targetMountPoint,
                        isSuitableForApp2sd = true
                    )
                )
            }
        }

        partitionItems.sortedWith(
            compareByDescending<PartitionInfo> { it.isTargetMount }
                .thenByDescending { it.isSuitableForApp2sd }
                .thenBy { it.name }
        )
    }

    /**
     * Check filesystem integrity of an unmounted block partition using fsck.
     * CAUTION: Must only be run when the partition is unmounted.
     */
    suspend fun checkFilesystem(
        blockDevice: String,
        fsType: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val mountsRes = RootShell.exec("grep -F \"$blockDevice\" /proc/mounts 2>/dev/null")
            if (mountsRes.output.isNotBlank()) {
                error("Cannot check filesystem while partition is mounted. Please unmount first.")
            }

            val cmd = when {
                fsType.contains("f2fs", ignoreCase = true) -> "fsck.f2fs -a \"$blockDevice\" 2>&1 || fsck.f2fs \"$blockDevice\" 2>&1"
                fsType.contains("ext4", ignoreCase = true) -> "e2fsck -p \"$blockDevice\" 2>&1 || e2fsck -y \"$blockDevice\" 2>&1"
                else -> "fsck -y \"$blockDevice\" 2>&1 || e2fsck -p \"$blockDevice\" 2>&1"
            }

            val res = RootShell.exec(cmd)
            val output = res.output.ifBlank { res.stderr.joinToString("\n") }
            if (output.isBlank()) {
                "Filesystem check completed with status code ${res.code}."
            } else {
                output
            }
        }
    }

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
                            RootShell.exec("chown -R $uid:1023 \"$internalData\"")
                            RootShell.exec("chmod -R 775 \"$internalData\"")
                            RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$internalData\"")
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
                            RootShell.exec("chown -R $uid:1023 \"$internalObb\"")
                            RootShell.exec("chmod -R 775 \"$internalObb\"")
                            RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$internalObb\"")
                            RootShell.exec("rm -rf \"$sdObb\"")
                        } else if (target == MigrationTarget.OBB_ONLY) {
                            error("SD OBB path does not exist: $sdObb")
                        }
                    }

                    // Restore internal app sandbox directory permissions
                    RootShell.exec("chown -R $uid:$uid \"/data/user/0/$packageName\" 2>/dev/null")
                    RootShell.exec("chmod -R 775 \"/data/user/0/$packageName\" 2>/dev/null")
                }
            }
            Unit
        }
    }
}
