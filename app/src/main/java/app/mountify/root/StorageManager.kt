package app.mountify.root

import app.mountify.data.model.DiskType
import app.mountify.data.model.FilesystemType
import app.mountify.data.model.InternalStorageInfo
import app.mountify.data.model.MigrationTarget
import app.mountify.data.model.MoveDirection
import app.mountify.data.model.PartitionInfo
import app.mountify.data.model.PartitionSchemeConfig
import app.mountify.data.model.SdCardDiskInfo
import app.mountify.data.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        try {
            val stat = android.os.StatFs("/data")
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val freeBytes = stat.availableBlocksLong * blockSize
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
            InternalStorageInfo(
                totalBytes = totalBytes,
                usedBytes = usedBytes,
                freeBytes = freeBytes
            )
        } catch (_: Exception) {
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
    }

    /**
     * Comprehensive scan of all MicroSD/USB block devices and partitions.
     * Parses /proc/partitions, /proc/mounts, and blkid to obtain full metadata
     * (disk name, partition number, size, filesystem, mount point, label, UUID).
     */
    suspend fun detectPartitions(targetMountPoint: String = "/data/sdext2"): List<PartitionInfo> = withContext(Dispatchers.IO) {
        // Collect all mount entries from /proc/mounts
        data class RawMount(val spec: String, val file: String, val vfstype: String)
        val allMounts = mutableListOf<RawMount>()

        try {
            val procMounts = java.io.File("/proc/mounts")
            if (procMounts.exists() && procMounts.canRead()) {
                procMounts.forEachLine { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        allMounts.add(RawMount(parts[0], parts[1], parts[2]))
                    }
                }
            }
        } catch (_: Exception) {}

        if (allMounts.isEmpty()) {
            val mountsRes = RootShell.exec("cat /proc/mounts 2>/dev/null")
            mountsRes.stdout.forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 3) {
                    allMounts.add(RawMount(parts[0], parts[1], parts[2]))
                }
            }
        }

        // Map device major:minor from /sys/class/block/*/dev for Android vold correlation
        val devMajorMinorMap = mutableMapOf<String, String>() // e.g. "mmcblk0p1" -> "179:1"
        try {
            val blockDir = java.io.File("/sys/class/block")
            if (blockDir.exists() && blockDir.isDirectory) {
                blockDir.listFiles()?.forEach { f ->
                    val devFile = java.io.File(f, "dev")
                    if (devFile.exists()) {
                        val mm = devFile.readText().trim()
                        if (mm.isNotBlank()) {
                            devMajorMinorMap[f.name] = mm
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (devMajorMinorMap.isEmpty()) {
            val devRes = RootShell.exec("grep . /sys/class/block/*/dev 2>/dev/null")
            if (devRes.isSuccess) {
                devRes.stdout.forEach { line ->
                    // line format: /sys/class/block/mmcblk0p1/dev:179:1
                    val devName = line.substringBefore("/dev:").substringAfterLast("/")
                    val mm = line.substringAfter("/dev:").trim()
                    if (devName.isNotBlank() && mm.isNotBlank()) {
                        devMajorMinorMap[devName] = mm
                    }
                }
            }
        }

        // Batch scan all blkid entries in ONE single command
        val blkidMap = mutableMapOf<String, Triple<String?, String?, String?>>()
        val blkidRes = RootShell.exec("blkid 2>/dev/null || toybox blkid 2>/dev/null")
        if (blkidRes.isSuccess && blkidRes.output.isNotBlank()) {
            for (line in blkidRes.stdout) {
                val devPath = line.substringBefore(":").trim()
                if (devPath.isNotBlank()) {
                    val typeMatch = Regex("TYPE=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                    val labelMatch = Regex("LABEL=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                    val uuidMatch = Regex("UUID=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                    val info = Triple(typeMatch, labelMatch, uuidMatch)
                    blkidMap[devPath] = info
                    val nameOnly = devPath.substringAfterLast("/")
                    blkidMap[nameOnly] = info
                }
            }
        }

        // Query df -k for filesystem used and available space
        val dfMap = mutableMapOf<String, Pair<Long, Long>>() // devPath or mountPoint -> (usedBytes, freeBytes)
        val dfRes = RootShell.exec("df -k 2>/dev/null")
        if (dfRes.isSuccess && dfRes.output.isNotBlank()) {
            for (line in dfRes.stdout.drop(1)) {
                val tokens = line.trim().split(Regex("\\s+"))
                if (tokens.size >= 6) {
                    val dev = tokens[0]
                    val usedK = tokens[2].toLongOrNull() ?: 0L
                    val availK = tokens[3].toLongOrNull() ?: 0L
                    val mnt = tokens[5]
                    val pair = Pair(usedK * 1024L, availK * 1024L)
                    dfMap[dev] = pair
                    dfMap[mnt] = pair
                }
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

                    // Blkid metadata
                    var fsType = ""
                    var label: String? = null
                    var uuid: String? = null

                    val blkidEntry = blkidMap[path] ?: blkidMap[name]
                    if (blkidEntry != null) {
                        fsType = blkidEntry.first ?: ""
                        label = blkidEntry.second
                        uuid = blkidEntry.third
                    }

                    // Major:minor resolution for vold node detection
                    val majorMinor = devMajorMinorMap[name] ?: ""
                    val voldMajorComma = if (majorMinor.contains(":")) majorMinor.replace(':', ',') else ""
                    val voldMajorUnderscore = if (majorMinor.contains(":")) majorMinor.replace(':', '_') else ""

                    // Match all mounts for this physical partition across all Android subsystems
                    val matchingMounts = allMounts.filter { m ->
                        m.spec == path ||
                        m.spec.endsWith("/$name") ||
                        (majorMinor.isNotBlank() && (
                            (voldMajorComma.isNotBlank() && (m.spec.contains("public:$voldMajorComma") || m.spec.contains("disk:$voldMajorComma"))) ||
                            (voldMajorUnderscore.isNotBlank() && (m.spec.contains("public:$voldMajorUnderscore") || m.spec.contains("disk:$voldMajorUnderscore"))) ||
                            m.spec.endsWith("/$majorMinor") ||
                            m.spec.contains(majorMinor)
                        )) ||
                        (!uuid.isNullOrBlank() && (
                            m.file.contains(uuid) || m.spec.contains(uuid)
                        ))
                    }

                    // Canonical Mount Hierarchy (Anti-bind mount overwrite):
                    // 1. Configured target mount (/data/sdext2) has top priority
                    val targetMountEntry = matchingMounts.firstOrNull { it.file == targetMountPoint }
                    val isTargetMount = targetMountEntry != null

                    // 2. Android portable storage mount (/storage/<UUID> or /mnt/media_rw/<UUID>)
                    val portableMountEntry = matchingMounts.firstOrNull { m ->
                        (m.file.startsWith("/storage/") && !m.file.startsWith("/storage/emulated")) ||
                        m.file.startsWith("/mnt/media_rw/") ||
                        m.file.startsWith("/mnt/pass_through/")
                    }
                    val isPortableMount = !isTargetMount && portableMountEntry != null

                    // 3. Other non-bind root mount points
                    val rootMountEntry = matchingMounts.firstOrNull { m ->
                        !m.file.startsWith("/data/media/") &&
                        !m.file.startsWith("/mnt/runtime/") &&
                        !m.file.startsWith("/mnt/user/") &&
                        !m.file.startsWith("/storage/emulated/") &&
                        !m.file.startsWith("/apex/")
                    }

                    val isMounted = matchingMounts.isNotEmpty()
                    val canonicalMountPoint = when {
                        isTargetMount -> targetMountPoint
                        isPortableMount -> {
                            matchingMounts.firstOrNull { it.file.startsWith("/storage/") && !it.file.startsWith("/storage/emulated") }?.file
                                ?: portableMountEntry?.file
                        }
                        rootMountEntry != null -> rootMountEntry.file
                        isMounted -> matchingMounts.first().file
                        else -> null
                    }

                    if (fsType.isBlank()) {
                        fsType = targetMountEntry?.vfstype
                            ?: portableMountEntry?.vfstype
                            ?: rootMountEntry?.vfstype
                            ?: matchingMounts.firstOrNull()?.vfstype
                            ?: ""
                    }

                    // Disk removability check (sysfs /sys/block/<disk>/removable)
                    val isRemovable = try {
                        val remFile = java.io.File("/sys/block/$diskName/removable")
                        if (remFile.exists() && remFile.canRead()) {
                            remFile.readText().trim() == "1"
                        } else {
                            isMmcPartition
                        }
                    } catch (_: Exception) {
                        isMmcPartition
                    }

                    // Exclude internal fixed flash storage (such as UFS sda-sdf LUNs) unless already configured as target mount
                    if (isSdPartition && !isRemovable && !isTargetMount) {
                        continue
                    }

                    // Exclude tiny firmware partitions (< 250MB) unless already mounted as target
                    if (sizeBytes < 250 * 1024 * 1024L && !isTargetMount) {
                        continue
                    }

                    // Exclude partitions mounted to critical Android system hierarchy
                    val isSystemMount = canonicalMountPoint != null && (
                        canonicalMountPoint == "/" ||
                        canonicalMountPoint == "/system" ||
                        canonicalMountPoint == "/vendor" ||
                        canonicalMountPoint == "/product" ||
                        canonicalMountPoint == "/system_ext" ||
                        canonicalMountPoint == "/metadata" ||
                        canonicalMountPoint == "/data" ||
                        canonicalMountPoint == "/persist" ||
                        canonicalMountPoint.startsWith("/apex") ||
                        canonicalMountPoint.startsWith("/mnt/vendor")
                    )
                    if (isSystemMount && !isTargetMount) {
                        continue
                    }

                    val isLinuxFs = fsType.equals("f2fs", ignoreCase = true) || fsType.equals("ext4", ignoreCase = true)
                    val isSuitable = isTargetMount || (isRemovable && (isLinuxFs || (partNum >= 2 && !isMounted)))

                    // Accurately compute used & free bytes via canonical mount point and dfMap / StatFs
                    var usedBytes = 0L
                    var freeBytes = 0L
                    if (isMounted) {
                        val dfPair = if (canonicalMountPoint != null) {
                            dfMap[canonicalMountPoint]
                                ?: dfMap[path]
                                ?: dfMap[name]
                                ?: (if (!uuid.isNullOrBlank()) dfMap.entries.firstOrNull { e -> e.key.contains(uuid) }?.value else null)
                        } else {
                            dfMap[path] ?: dfMap[name]
                        }

                        if (dfPair != null && (dfPair.first > 0L || dfPair.second > 0L)) {
                            usedBytes = dfPair.first
                            freeBytes = dfPair.second
                        } else if (canonicalMountPoint != null) {
                            try {
                                val stat = android.os.StatFs(canonicalMountPoint)
                                val total = stat.blockCountLong * stat.blockSizeLong
                                val free = stat.availableBlocksLong * stat.blockSizeLong
                                val used = (total - free).coerceAtLeast(0L)
                                if (total > 0L) {
                                    usedBytes = used
                                    freeBytes = free
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    partitionItems.add(
                        PartitionInfo(
                            path = path,
                            name = name,
                            diskName = diskName,
                            partitionNumber = partNum,
                            sizeBytes = sizeBytes,
                            usedBytes = usedBytes,
                            freeBytes = freeBytes,
                            fsType = fsType,
                            mountPoint = canonicalMountPoint,
                            label = label,
                            uuid = uuid,
                            isMounted = isMounted,
                            isTargetMount = isTargetMount,
                            isPortableMount = isPortableMount,
                            isMountTargetReady = isSuitable
                        )
                    )
                }
            }
        }

        if (partitionItems.isEmpty()) {
            val fallbackDevices = detectBlockDevices()
            fallbackDevices.forEach { devPath ->
                val name = devPath.substringAfterLast("/")
                val mountInfo = allMounts.firstOrNull { it.spec == devPath || it.spec.endsWith("/$name") }
                val isMounted = mountInfo != null
                val dfPair = dfMap[devPath] ?: dfMap[name] ?: if (mountInfo?.file != null) dfMap[mountInfo.file] else null
                partitionItems.add(
                    PartitionInfo(
                        path = devPath,
                        name = name,
                        diskName = name.substringBeforeLast("p"),
                        partitionNumber = name.filter { it.isDigit() }.toIntOrNull() ?: 1,
                        sizeBytes = 0L,
                        usedBytes = dfPair?.first ?: 0L,
                        freeBytes = dfPair?.second ?: 0L,
                        fsType = mountInfo?.vfstype ?: "",
                        mountPoint = mountInfo?.file,
                        isMounted = isMounted,
                        isTargetMount = mountInfo?.file == targetMountPoint,
                        isMountTargetReady = true
                    )
                )
            }
        }

        partitionItems.sortedWith(
            compareByDescending<PartitionInfo> { it.isTargetMount }
                .thenByDescending { it.isMountTargetReady }
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
        val result = RootShell.exec("ls /dev/block/mmcblk*p* 2>/dev/null")
        if (!result.isSuccess) return@withContext emptyList()

        result.stdout
            .map { it.trim() }
            .filter { line ->
                // Keep partition blocks like mmcblk0p2, mmcblk1p1
                line.matches(Regex(".*/mmcblk[0-9]+p[0-9]+$"))
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
            var mountLine: String? = null
            try {
                val procMounts = java.io.File("/proc/mounts")
                if (procMounts.exists() && procMounts.canRead()) {
                    val target = " $mountPoint "
                    mountLine = procMounts.useLines { lines ->
                        lines.firstOrNull { it.contains(target) }
                    }
                }
            } catch (_: Exception) {}

            if (mountLine == null) {
                val mountsRes = RootShell.exec("grep \" $mountPoint \" /proc/mounts 2>/dev/null | head -n 1")
                if (mountsRes.isSuccess && mountsRes.output.isNotBlank()) {
                    mountLine = mountsRes.output.trim()
                }
            }

            if (mountLine.isNullOrBlank()) return@withContext null

            val mountParts = mountLine.trim().split(Regex("\\s+"))
            val blockDevice = mountParts.getOrNull(0) ?: ""
            val filesystem = mountParts.getOrNull(2) ?: ""

            var totalBytes = 0L
            var usedBytes = 0L
            var freeBytes = 0L

            try {
                val stat = android.os.StatFs(mountPoint)
                val bs = stat.blockSizeLong
                totalBytes = stat.blockCountLong * bs
                freeBytes = stat.availableBlocksLong * bs
                usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
            } catch (_: Exception) {
                val dfRes = RootShell.exec("df -k \"$mountPoint\" 2>/dev/null | tail -n 1")
                val dfParts = dfRes.output.trim().split(Regex("\\s+"))
                if (dfParts.size >= 4) {
                    val total1k = dfParts.getOrNull(1)?.toLongOrNull() ?: 0L
                    val used1k = dfParts.getOrNull(2)?.toLongOrNull() ?: 0L
                    val free1k = dfParts.getOrNull(3)?.toLongOrNull() ?: 0L
                    totalBytes = total1k * 1024L
                    usedBytes = used1k * 1024L
                    freeBytes = free1k * 1024L
                }
            }

            StorageInfo(
                blockDevice = blockDevice,
                mountPoint = mountPoint,
                filesystem = filesystem,
                totalBytes = totalBytes,
                usedBytes = usedBytes,
                freeBytes = freeBytes,
                isMounted = true
            )
        }

    /**
     * Detect all connected physical storage disks (MicroSD, USB OTG flashdrives, etc.)
     * and their partition hierarchies.
     */
    suspend fun detectAllDisks(targetMountPoint: String = "/data/sdext2"): List<SdCardDiskInfo> = withContext(Dispatchers.IO) {
        val partitions = detectPartitions(targetMountPoint)

        // 1. Discover all disk candidate names from partitions or /sys/block
        val candidateDiskNames = linkedSetOf<String>()
        partitions.forEach { candidateDiskNames.add(it.diskName) }

        // Also inspect /sys/block for mmcblk* and sd*
        try {
            val sysBlock = java.io.File("/sys/block")
            if (sysBlock.exists() && sysBlock.isDirectory) {
                sysBlock.listFiles()?.forEach { f ->
                    val name = f.name
                    if (name.startsWith("mmcblk") && !name.contains("boot") && !name.contains("rpmb")) {
                        candidateDiskNames.add(name)
                    } else if (name.matches(Regex("sd[a-z]"))) {
                        val rem = try {
                            java.io.File(f, "removable").readText().trim()
                        } catch (_: Exception) { "0" }
                        if (rem == "1") {
                            candidateDiskNames.add(name)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (candidateDiskNames.isEmpty()) {
            if (java.io.File("/sys/block/mmcblk0").exists()) candidateDiskNames.add("mmcblk0")
            else if (java.io.File("/sys/block/mmcblk1").exists()) candidateDiskNames.add("mmcblk1")
        }

        val disks = mutableListOf<SdCardDiskInfo>()

        for (candidateDisk in candidateDiskNames) {
            val diskPath = "/dev/block/$candidateDisk"
            val isMmc = candidateDisk.startsWith("mmcblk")
            val diskType = if (isMmc) DiskType.MICRO_SD else DiskType.USB_OTG

            // Read hardware manufacturer ID & model
            var manfid = try {
                val f = java.io.File("/sys/block/$candidateDisk/device/manfid")
                if (f.exists() && f.canRead()) f.readText().trim() else ""
            } catch (_: Exception) { "" }
            if (manfid.isBlank() && isMmc) {
                val res = RootShell.exec("cat /sys/block/$candidateDisk/device/manfid 2>/dev/null")
                if (res.isSuccess) manfid = res.output.trim()
            }

            var model = try {
                val f = java.io.File("/sys/block/$candidateDisk/device/name")
                if (f.exists() && f.canRead()) f.readText().trim() else ""
            } catch (_: Exception) { "" }
            if (model.isBlank()) {
                val res = RootShell.exec("cat /sys/block/$candidateDisk/device/name 2>/dev/null")
                if (res.isSuccess && res.output.isNotBlank()) {
                    model = res.output.trim()
                } else {
                    val usbModelRes = RootShell.exec("cat /sys/block/$candidateDisk/device/model 2>/dev/null")
                    if (usbModelRes.isSuccess) model = usbModelRes.output.trim()
                }
            }

            var vendor = try {
                val f = java.io.File("/sys/block/$candidateDisk/device/vendor")
                if (f.exists() && f.canRead()) f.readText().trim() else ""
            } catch (_: Exception) { "" }
            if (vendor.isBlank() && !isMmc) {
                val res = RootShell.exec("cat /sys/block/$candidateDisk/device/vendor 2>/dev/null")
                if (res.isSuccess) vendor = res.output.trim()
            }

            // Read disk sector count
            var sizeSectors = try {
                val f = java.io.File("/sys/block/$candidateDisk/size")
                if (f.exists() && f.canRead()) f.readText().trim().toLongOrNull() ?: 0L else 0L
            } catch (_: Exception) { 0L }
            if (sizeSectors == 0L) {
                val res = RootShell.exec("cat /sys/block/$candidateDisk/size 2>/dev/null")
                if (res.isSuccess) sizeSectors = res.output.trim().toLongOrNull() ?: 0L
            }

            val diskPartitions = partitions.filter { it.diskName == candidateDisk }
            val totalSizeBytes = if (sizeSectors > 0) sizeSectors * 512L else diskPartitions.sumOf { it.sizeBytes }

            if (totalSizeBytes <= 0L && diskPartitions.isEmpty()) {
                continue
            }

            val totalUsedBytes = diskPartitions.sumOf { it.usedBytes }
            val totalFreeBytes = if (totalUsedBytes > 0L) {
                (totalSizeBytes - totalUsedBytes).coerceAtLeast(0L)
            } else {
                diskPartitions.sumOf { it.freeBytes }
            }

            val vendorDisplayName = when {
                isMmc -> {
                    val mVendor = when (manfid.lowercase()) {
                        "0x00001b" -> "Samsung"
                        "0x000003" -> "SanDisk"
                        "0x000002" -> "Kingston"
                        "0x000074" -> "Transcend"
                        "0x000028" -> "Lexar"
                        "0x000013" -> "Micron"
                        "0x00009c" -> "Sony"
                        "0x000027", "0x000070" -> "Silicon Power"
                        "0x000041" -> "Kingston"
                        else -> "MicroSD"
                    }
                    if (mVendor == "MicroSD") "MicroSD Card" else "$mVendor MicroSD"
                }
                else -> {
                    if (vendor.isNotBlank()) "$vendor USB OTG" else "USB OTG Storage"
                }
            }

            disks.add(
                SdCardDiskInfo(
                    devicePath = diskPath,
                    diskName = candidateDisk,
                    vendorName = vendorDisplayName,
                    modelName = model,
                    totalSizeBytes = totalSizeBytes,
                    totalUsedBytes = totalUsedBytes,
                    totalFreeBytes = totalFreeBytes,
                    diskType = diskType,
                    partitions = diskPartitions
                )
            )
        }

        disks
    }

    /**
     * Detect primary MicroSD hardware disk information (manufacturer, model, capacity, partition list).
     */
    suspend fun detectSdCardDiskInfo(targetMountPoint: String = "/data/sdext2"): SdCardDiskInfo? = withContext(Dispatchers.IO) {
        detectAllDisks(targetMountPoint).firstOrNull { it.diskType == DiskType.MICRO_SD }
            ?: detectAllDisks(targetMountPoint).firstOrNull()
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
                FilesystemType.F2FS -> "mkfs.f2fs -l \"$label\" -f \"$blockDevice\" 2>&1"
                FilesystemType.EXT4 -> "mke2fs -t ext4 -b 4096 -L \"$label\" -F \"$blockDevice\" 2>&1 || mkfs.ext4 -L \"$label\" -F \"$blockDevice\" 2>&1"
                FilesystemType.FAT32 -> "newfs_msdos -F 32 -L \"$label\" \"$blockDevice\" 2>&1 || mkfs.vfat -F 32 -n \"$label\" \"$blockDevice\" 2>&1"
                FilesystemType.EXFAT -> "mkfs.exfat -n \"$label\" \"$blockDevice\" 2>&1 || newfs_msdos -F 32 -L \"$label\" \"$blockDevice\" 2>&1"
                FilesystemType.NTFS -> "mkfs.ntfs -f -L \"$label\" \"$blockDevice\" 2>&1"
            }

            val res = RootShell.exec(cmd)
            if (!res.isSuccess) {
                error("Formatting $blockDevice with ${fsType.label} failed: ${res.output}\n${res.stderr.joinToString("\n")}")
            }
        }
    }

    /**
     * Repartition the entire MicroSD card with a multi-partition scheme.
     * CAUTION: Destructive operation. Recreates partition table and formats each partition.
     */
    suspend fun repartitionDisk(
        diskPath: String,
        partitions: List<PartitionSchemeConfig>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (partitions.isEmpty()) error("No partitions specified in scheme.")

            // 1. Unmount all partitions currently mounted on this disk
            val mountsRes = RootShell.exec("grep -F \"$diskPath\" /proc/mounts 2>/dev/null")
            for (line in mountsRes.stdout) {
                val mntPoint = line.trim().split(Regex("\\s+")).getOrNull(1)
                if (!mntPoint.isNullOrBlank()) {
                    RootShell.exec("umount -f -l \"$mntPoint\" 2>/dev/null")
                }
            }

            // 2. Wipe existing partition table signatures
            RootShell.exec("sgdisk -Z \"$diskPath\" 2>/dev/null || wipefs -a \"$diskPath\" 2>/dev/null || dd if=/dev/zero of=\"$diskPath\" bs=1M count=8 conv=notrunc 2>/dev/null")

            // 3. Create fresh partition table using sgdisk (or fdisk fallback)
            val sgdiskCheck = RootShell.exec("which sgdisk 2>/dev/null").output.trim()
            if (sgdiskCheck.isNotBlank()) {
                RootShell.exec("sgdisk -o \"$diskPath\"")

                partitions.forEachIndexed { index, cfg ->
                    val partNum = index + 1
                    val isLast = index == partitions.size - 1

                    // Type code: 0700 for FAT32/exFAT (Basic Data), 8300 for Linux filesystem (F2FS/Ext4)
                    val typeCode = when (cfg.fsType) {
                        FilesystemType.FAT32, FilesystemType.EXFAT -> "0700"
                        else -> "8300"
                    }

                    val sizeParam = if (isLast) "0" else "+${cfg.sizeKb}K"
                    val createCmd = "sgdisk -n $partNum:0:$sizeParam -t $partNum:$typeCode -c $partNum:\"${cfg.label}\" \"$diskPath\""
                    val res = RootShell.exec(createCmd)
                    if (!res.isSuccess) {
                        error("Failed creating partition $partNum via sgdisk: ${res.output}")
                    }
                }
            } else {
                // Fallback via fdisk script
                val fdiskScript = StringBuilder("o\\n") // create new empty DOS partition table
                var currentPart = 1
                partitions.forEachIndexed { index, cfg ->
                    val isLast = index == partitions.size - 1
                    fdiskScript.append("n\\np\\n$currentPart\\n\\n")
                    if (isLast) {
                        fdiskScript.append("\\n")
                    } else {
                        val sizeM = (cfg.sizeKb / 1024L).coerceAtLeast(1L)
                        fdiskScript.append("+$sizeM" + "M\\n")
                    }
                    if (cfg.fsType == FilesystemType.FAT32) {
                        fdiskScript.append("t\\n")
                        if (partitions.size > 1) fdiskScript.append("$currentPart\\n")
                        fdiskScript.append("c\\n")
                    }
                    currentPart++
                }
                fdiskScript.append("w\\n")
                val res = RootShell.exec("printf '$fdiskScript' | fdisk \"$diskPath\" 2>/dev/null")
                if (!res.isSuccess) {
                    error("Failed to write partition table via fdisk: ${res.output}")
                }
            }

            // 4. Force kernel to re-read partition table
            RootShell.exec("blockdev --rereadpt \"$diskPath\" 2>/dev/null || partprobe \"$diskPath\" 2>/dev/null")
            delay(1500L)

            // 5. Format each newly created partition
            partitions.forEachIndexed { index, cfg ->
                val partNum = index + 1
                val partDev = "${diskPath}p$partNum"

                var attempts = 0
                while (attempts < 6 && !RootShell.exists(partDev)) {
                    delay(500L)
                    attempts++
                }

                val formatRes = formatPartition(
                    blockDevice = partDev,
                    fsType = cfg.fsType,
                    label = cfg.label
                )
                if (formatRes.isFailure) {
                    error("Partition $partNum format error: ${formatRes.exceptionOrNull()?.message}")
                }
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
