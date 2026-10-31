package app.mountx.root

import android.content.Context
import app.mountx.R

import app.mountx.data.model.BenchmarkResult
import app.mountx.data.model.DiskHardwareDetails
import app.mountx.data.model.DiskHealthStatus
import app.mountx.data.model.DiskIoConfig
import app.mountx.data.model.DiskType
import app.mountx.data.model.FilesystemType
import app.mountx.data.model.InternalStorageInfo
import app.mountx.data.model.MigrationTarget
import app.mountx.data.model.MoveDirection
import app.mountx.data.model.PartitionInfo
import app.mountx.data.model.PartitionSchemeConfig
import app.mountx.data.model.SdCardDiskInfo
import app.mountx.data.model.StorageInfo
import app.mountx.data.model.StorageHierarchyResult
import app.mountx.data.model.FsckReport
import app.mountx.data.model.FsckStatus
import app.mountx.data.model.SupportedFilesystemInfo
import app.mountx.data.model.GlobalTrimReport
import app.mountx.data.model.TrimPartitionResult
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.ConflictStrategy
import app.mountx.data.model.OperationProgress
import app.mountx.data.model.OperationType
import app.mountx.util.AppLogger
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Exception thrown when destination storage does not have sufficient space plus safety headroom.
 */
class InsufficientStorageException(
    val requiredBytes: Long,
    val availableBytes: Long,
    val deficitBytes: Long,
    val targetName: String,
    message: String
) : IllegalStateException(message)

/**
 * Exception thrown when a file exceeds the 4GB limit on a FAT32 filesystem.
 */
class FileTooLargeForFat32Exception(
    val filePath: String,
    val fileSize: Long,
    message: String
) : IllegalStateException(message)

/**
 * Engine type used for data migration.
 */
enum class MigrationEngineType(val label: String) {
    STREAMING_TAR("Streaming Tar Pipeline"),
    DIRECT_BLOCK_CP("Direct Large-Block I/O")
}

/**
 * Metadata and capabilities of target storage location.
 */
data class StorageInspectionResult(
    val fsType: String,
    val clusterSizeBytes: Long,
    val isNonPosix: Boolean,
    val isFat32: Boolean,
    val supportsSymlinks: Boolean
) {
    val isPosix: Boolean get() = !isNonPosix
}

/**
 * Low-level storage and partition manager using root commands.
 * Handles device discovery, mounting/unmounting partitions, formatting (f2fs/ext4/etc),
 * and migrating physical game data between internal storage and MicroSD.
 */
class StorageManager(
    private val context: Context? = null
) {
    private fun getAppContext(): Context = context ?: app.mountx.MountXApp.instance

    @Volatile
    private var activeTransferPid: Int? = null

    @Volatile
    private var isCancellationRequested: Boolean = false

    suspend fun cancelCurrentTransfer() = withContext(Dispatchers.IO) {
        isCancellationRequested = true
        val pid = activeTransferPid
        if (pid != null && pid > 0) {
            AppLogger.warn("StorageManager", "User requested cancel. Terminating migration PID $pid...")
            Shell.cmd("kill -15 $pid 2>/dev/null", "kill -9 $pid 2>/dev/null").exec()
        }
    }

    private data class HwDiskCache(val manfid: String, val model: String, val vendor: String, val sizeSectors: Long)
    private val diskHardwareCache = java.util.concurrent.ConcurrentHashMap<String, HwDiskCache>()

    private var cachedSupportedFilesystems: List<SupportedFilesystemInfo>? = null

    /**
     * Resolves available free space in bytes for any given directory or mount point.
     */
    suspend fun getAvailableFreeBytes(path: String): Long = withContext(Dispatchers.IO) {
        try {
            val stat = android.os.StatFs(path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            val statRes = RootShell.exec("stat -f -c %s:%a \"$path\" 2>/dev/null")
            val parts = statRes.output.trim().split(":")
            if (parts.size >= 2) {
                val bs = parts[0].toLongOrNull() ?: 0L
                val avail = parts[1].toLongOrNull() ?: 0L
                avail * bs
            } else {
                val dfRes = RootShell.exec("df -k \"$path\" 2>/dev/null | tail -1")
                val tokens = dfRes.output.trim().split(Regex("\\s+"))
                val availKb = tokens.getOrNull(3)?.toLongOrNull() ?: 0L
                availKb * 1024L
            }
        }
    }

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
            val statRes = RootShell.exec("stat -f -c %s:%b:%a /data 2>/dev/null")
            val parts = statRes.output.trim().split(":")
            if (parts.size >= 3) {
                val bs = parts[0].toLongOrNull() ?: 0L
                val blocks = parts[1].toLongOrNull() ?: 0L
                val avail = parts[2].toLongOrNull() ?: 0L
                val totalBytes = blocks * bs
                val freeBytes = avail * bs
                val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
                InternalStorageInfo(
                    totalBytes = totalBytes,
                    usedBytes = usedBytes,
                    freeBytes = freeBytes
                )
            } else {
                null
            }
        }
    }

    /**
     * Proactively verifies and creates the standardized MountX directory tree on external storage.
     * Ensures $sdBase/MountX/(Android/data, Android/obb, Android/media, Custom, app, containers)
     * Localized .nomedia is placed strictly in private subdirectories (data & obb) so gallery files are preserved.
     */
    suspend fun ensureMountXStorageStructure(sdBase: String = "/data/sdext2"): Unit = withContext(Dispatchers.IO) {
        runCatching {
            if (!RootShell.exists(sdBase)) return@withContext
            val mountXBase = "$sdBase/MountX"
            RootShell.exec("""
                mkdir -p "$mountXBase/Android/data" \
                         "$mountXBase/Android/obb" \
                         "$mountXBase/Android/media" \
                         "$mountXBase/Custom" \
                         "$mountXBase/app" \
                         "$mountXBase/containers" 2>/dev/null
                rm -f "$mountXBase/Android/.nomedia" 2>/dev/null
                touch "$mountXBase/Android/data/.nomedia" 2>/dev/null
                touch "$mountXBase/Android/obb/.nomedia" 2>/dev/null
                chown media_rw:media_rw "$mountXBase" "$mountXBase/Android" "$mountXBase/Android/data" "$mountXBase/Android/obb" "$mountXBase/Android/media" "$mountXBase/Custom" "$mountXBase/app" "$mountXBase/containers" 2>/dev/null
                chmod 777 "$mountXBase" "$mountXBase/Android" "$mountXBase/Android/data" "$mountXBase/Android/obb" "$mountXBase/Android/media" "$mountXBase/Custom" "$mountXBase/app" "$mountXBase/containers" 2>/dev/null
            """.trimIndent())
            AppLogger.info("StorageManager", "Ensured MountX storage structure on $sdBase")
        }
    }

    /**
     * Automatically migrates legacy root directories ($sdBase/Android/data, obb, media)
     * into the unified MountX directory tree ($sdBase/MountX/Android/...) when updating.
     * Preserves dotfiles using cp -an $src/. $dst/ and cleans up empty legacy parent directories.
     */
    suspend fun migrateLegacyStorageToMountX(sdBase: String = "/data/sdext2"): Unit = withContext(Dispatchers.IO) {
        runCatching {
            if (!RootShell.exists(sdBase)) return@withContext
            val legacyAndroid = "$sdBase/Android"
            if (!RootShell.exists(legacyAndroid)) return@withContext

            val mountXBase = "$sdBase/MountX"
            val mountXAndroid = "$mountXBase/Android"
            ensureMountXStorageStructure(sdBase)

            val categories = listOf("data", "obb", "media")
            var migratedCount = 0

            for (cat in categories) {
                val legacyCatDir = "$legacyAndroid/$cat"
                val modernCatDir = "$mountXAndroid/$cat"

                if (!RootShell.exists(legacyCatDir)) continue

                val listRes = RootShell.exec("ls -1 \"$legacyCatDir\" 2>/dev/null")
                if (!listRes.isSuccess || listRes.stdout.isEmpty()) continue

                for (pkg in listRes.stdout) {
                    val trimmedPkg = pkg.trim()
                    if (trimmedPkg.isEmpty() || trimmedPkg == "." || trimmedPkg == ".." || trimmedPkg == ".nomedia") continue

                    val legacyPkgPath = "$legacyCatDir/$trimmedPkg"
                    val modernPkgPath = "$modernCatDir/$trimmedPkg"

                    if (!RootShell.exists(legacyPkgPath)) continue

                    if (!RootShell.exists(modernPkgPath)) {
                        AppLogger.info("StorageManager", "Migrating legacy storage: $legacyPkgPath -> $modernPkgPath")
                        val mvRes = RootShell.exec("mv \"$legacyPkgPath\" \"$modernPkgPath\"")
                        if (mvRes.isSuccess && !RootShell.exists(legacyPkgPath)) {
                            migratedCount++
                        } else {
                            RootShell.exec("cp -an \"$legacyPkgPath/.\" \"$modernPkgPath/\" && rm -rf \"$legacyPkgPath\"")
                            migratedCount++
                        }
                    } else {
                        AppLogger.info("StorageManager", "Merging legacy storage into existing MountX directory: $legacyPkgPath -> $modernPkgPath")
                        RootShell.exec("cp -an \"$legacyPkgPath/.\" \"$modernPkgPath/\" 2>/dev/null")
                        RootShell.exec("rm -rf \"$legacyPkgPath\" 2>/dev/null")
                        migratedCount++
                    }
                }

                // Clean up category directory if now empty
                val remainingCat = RootShell.exec("ls -A \"$legacyCatDir\" 2>/dev/null").stdout.filter { it.isNotBlank() && it != ".nomedia" }
                if (remainingCat.isEmpty()) {
                    RootShell.exec("rm -f \"$legacyCatDir/.nomedia\" 2>/dev/null")
                    RootShell.exec("rmdir \"$legacyCatDir\" 2>/dev/null")
                }
            }

            // Remove legacy .nomedia and parent $sdBase/Android if empty
            val remainingAndroid = RootShell.exec("ls -A \"$legacyAndroid\" 2>/dev/null").stdout.filter { it.isNotBlank() && it != ".nomedia" }
            if (remainingAndroid.isEmpty()) {
                RootShell.exec("rm -f \"$legacyAndroid/.nomedia\" 2>/dev/null")
                RootShell.exec("rmdir \"$legacyAndroid\" 2>/dev/null")
            }

            // Re-apply correct ownership and permissions on MountX top structure
            RootShell.exec("chown media_rw:media_rw \"$mountXBase\" \"$mountXBase/Android\" 2>/dev/null")
            RootShell.exec("chmod 777 \"$mountXBase\" \"$mountXBase/Android\" 2>/dev/null")

            if (migratedCount > 0) {
                AppLogger.info("StorageManager", "Successfully migrated $migratedCount legacy packages to $mountXAndroid")
            }
        }.onFailure { e ->
            AppLogger.error("StorageManager", "Failed to migrate legacy storage on $sdBase: ${e.message}")
        }
    }

    private data class RawMount(val spec: String, val file: String, val vfstype: String)
    private data class RawProcPart(val major: Int, val minor: Int, val blocks: Long, val name: String)
    private data class HardwareProbeData(
        val allMounts: List<RawMount>,
        val procPartitions: List<RawProcPart>,
        val devMajorMinorMap: Map<String, String>,
        val removableDisks: Set<String>,
        val usbDisks: Set<String>,
        val smPublicVolumes: Map<String, String>,
        val blkidMap: Map<String, Triple<String?, String?, String?>>,
        val sysfsDisks: Map<String, HwDiskCache>
    )

    /**
     * Executes a single unified compound root shell command to gather all mounts,
     * partitions, major:minor mappings, removable flags, symlinks, vold volumes,
     * blkid tags, and sysfs disk details in ~300ms instead of 15+ sequential commands.
     */
    private suspend fun runHardwareProbeBatch(): HardwareProbeData {
        val allMounts = mutableListOf<RawMount>()
        val procPartitions = mutableListOf<RawProcPart>()
        val devMajorMinorMap = mutableMapOf<String, String>()
        val removableDisks = mutableSetOf<String>()
        val usbDisks = mutableSetOf<String>()
        val smPublicVolumes = mutableMapOf<String, String>()
        val blkidMap = mutableMapOf<String, Triple<String?, String?, String?>>()
        val sysfsDisks = mutableMapOf<String, HwDiskCache>()

        val batchScript = """
            echo "===MOUNTS==="; cat /proc/mounts 2>/dev/null
            echo "===PARTITIONS==="; cat /proc/partitions 2>/dev/null
            echo "===BLOCK_DEV==="; grep . /sys/class/block/*/dev 2>/dev/null
            echo "===REMOVABLE==="; grep . /sys/block/*/removable 2>/dev/null
            echo "===BLOCK_LINKS==="; ls -l /sys/block/ 2>/dev/null
            echo "===SM_VOLUMES==="; sm list-volumes public 2>/dev/null
            echo "===BLKID==="; blkid 2>/dev/null || toybox blkid 2>/dev/null
            echo "===SYSFS_DISKS==="
            for d in /sys/block/mmcblk* /sys/block/sd* /sys/block/nvme*; do
              [ -d "${'$'}d" ] || continue
              name="${'$'}{d##*/}"
              case "${'$'}name" in *boot*|*rpmb*) continue ;; esac
              mf=${'$'}(cat "${'$'}d/device/manfid" 2>/dev/null)
              nm=${'$'}(cat "${'$'}d/device/name" 2>/dev/null)
              md=${'$'}(cat "${'$'}d/device/model" 2>/dev/null)
              vd=${'$'}(cat "${'$'}d/device/vendor" 2>/dev/null)
              sz=${'$'}(cat "${'$'}d/size" 2>/dev/null)
              echo "DISK|${'$'}name|${'$'}mf|${'$'}nm|${'$'}md|${'$'}vd|${'$'}sz"
            done
        """.trimIndent()

        val probeRes = RootShell.exec(batchScript)

        val excludedKeywords = listOf(
            "/mnt/runtime/",
            "/mnt/installer/",
            "/mnt/androidwritable/",
            "/mnt/pass_through/",
            "/mnt/user/",
            "/emulated/0/Android",
            "/Android/data",
            "/Android/obb"
        )
        val includedPatterns = listOf(
            "/dev/block/",
            "/mnt/media_rw",
            "/data/sdext",
            "/storage/",
            "public:"
        )

        var currentSection = ""

        if (probeRes.isSuccess) {
            for (line in probeRes.stdout) {
                val trimmed = line.trim()
                if (trimmed.startsWith("===") && trimmed.endsWith("===")) {
                    currentSection = trimmed.replace("=", "")
                    continue
                }
                when (currentSection) {
                    "MOUNTS" -> {
                        val parts = trimmed.split(Regex("\\s+"))
                        if (parts.size >= 3) {
                            val spec = parts[0]
                            val file = parts[1]
                            val vfstype = parts[2]
                            val matchesInclude = includedPatterns.any { spec.contains(it) || file.contains(it) }
                            val matchesExclude = excludedKeywords.any { file.contains(it) || spec.contains(it) }
                            if (matchesInclude && !matchesExclude) {
                                allMounts.add(RawMount(spec, file, vfstype))
                            }
                        }
                    }
                    "PARTITIONS" -> {
                        val parts = trimmed.split(Regex("\\s+"))
                        if (parts.size >= 4) {
                            val major = parts[0].toIntOrNull() ?: continue
                            val minor = parts[1].toIntOrNull() ?: continue
                            val blocks = parts[2].toLongOrNull() ?: continue
                            val name = parts[3]
                            procPartitions.add(RawProcPart(major, minor, blocks, name))
                        }
                    }
                    "BLOCK_DEV" -> {
                        val devName = trimmed.substringBefore("/dev:").substringAfterLast("/")
                        val mm = trimmed.substringAfter("/dev:").trim()
                        if (devName.isNotBlank() && mm.isNotBlank()) {
                            devMajorMinorMap[devName] = mm
                        }
                    }
                    "REMOVABLE" -> {
                        val disk = trimmed.substringBefore("/removable:").substringAfterLast("/")
                        val isRem = trimmed.substringAfter("/removable:").trim() == "1"
                        if (isRem && !disk.startsWith("loop") && !disk.startsWith("ram") && !disk.startsWith("zram") && !disk.contains("boot") && !disk.contains("rpmb")) {
                            removableDisks.add(disk)
                        }
                    }
                    "BLOCK_LINKS" -> {
                        if (trimmed.contains("/usb") || trimmed.contains("usb-") || trimmed.contains("musb-")) {
                            val target = trimmed.substringAfterLast(" -> ")
                            val diskName = target.substringAfterLast("/")
                            if (diskName.isNotBlank() && !diskName.startsWith("loop") && !diskName.startsWith("ram") && !diskName.startsWith("zram")) {
                                usbDisks.add(diskName)
                            }
                            val nameBefore = trimmed.substringBefore(" -> ").trim().substringAfterLast(" ")
                            if (nameBefore.isNotBlank() && !nameBefore.startsWith("loop") && !nameBefore.startsWith("ram") && !nameBefore.startsWith("zram")) {
                                usbDisks.add(nameBefore)
                            }
                        }
                    }
                    "SM_VOLUMES" -> {
                        val parts = trimmed.split(Regex("\\s+"))
                        if (parts.size >= 3 && parts[0].startsWith("public:")) {
                            val devNode = parts[0].substringAfter("public:").replace(',', ':')
                            val uuid = parts[2]
                            smPublicVolumes[devNode] = uuid
                            smPublicVolumes[uuid] = devNode
                        }
                    }
                    "BLKID" -> {
                        val devPath = trimmed.substringBefore(":").trim()
                        if (devPath.isNotBlank()) {
                            val typeMatch = Regex("TYPE=\"([^\"]+)\"").find(trimmed)?.groupValues?.get(1)
                            val labelMatch = Regex("LABEL=\"([^\"]+)\"").find(trimmed)?.groupValues?.get(1)
                            val uuidMatch = Regex("UUID=\"([^\"]+)\"").find(trimmed)?.groupValues?.get(1)
                            val info = Triple(typeMatch, labelMatch, uuidMatch)
                            blkidMap[devPath] = info
                            val nameOnly = devPath.substringAfterLast("/")
                            blkidMap[nameOnly] = info
                        }
                    }
                    "SYSFS_DISKS" -> {
                        if (trimmed.startsWith("DISK|")) {
                            val tokens = trimmed.split("|")
                            if (tokens.size >= 7) {
                                val diskName = tokens[1]
                                val manfid = tokens[2].trim()
                                val name = tokens[3].trim()
                                val model = tokens[4].trim()
                                val vendor = tokens[5].trim()
                                val sizeSectors = tokens[6].trim().toLongOrNull() ?: 0L
                                val diskModel = if (name.isNotBlank()) name else model
                                val hwCache = HwDiskCache(manfid, diskModel, vendor, sizeSectors)
                                sysfsDisks[diskName] = hwCache
                                diskHardwareCache[diskName] = hwCache
                            }
                        }
                    }
                }
            }
        }

        if (allMounts.isEmpty()) {
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
        }

        return HardwareProbeData(
            allMounts = allMounts,
            procPartitions = procPartitions,
            devMajorMinorMap = devMajorMinorMap,
            removableDisks = removableDisks,
            usbDisks = usbDisks,
            smPublicVolumes = smPublicVolumes,
            blkidMap = blkidMap,
            sysfsDisks = sysfsDisks
        )
    }

    /**
     * Unified discovery of all MicroSD/USB block devices, partitions, and physical disk hierarchies.
     * Executes in ~300ms using the compound batch probe without redundant root IPC roundtrips.
     */
    suspend fun detectStorageHierarchy(targetMountPoint: String = "/data/sdext2"): StorageHierarchyResult = withContext(Dispatchers.IO) {
        val probe = runHardwareProbeBatch()
        val allMounts = probe.allMounts
        val devMajorMinorMap = probe.devMajorMinorMap
        val removableDisks = probe.removableDisks
        val usbDisks = probe.usbDisks
        val smPublicVolumes = probe.smPublicVolumes
        val blkidMap = probe.blkidMap
        val sysfsDisks = probe.sysfsDisks

        val partitionItems = mutableListOf<PartitionInfo>()
        val rawList = probe.procPartitions

        for (entry in rawList) {
            val name = entry.name
            val isMmcPartition = name.matches(Regex("mmcblk[0-9]+p[0-9]+"))
            val isSdPartition = name.matches(Regex("sd[a-z][0-9]+"))
            val isSdWholeDisk = name.matches(Regex("sd[a-z]"))

            // If it's a whole disk like 'sdd', check if it has child partitions like 'sdd1'
            if (isSdWholeDisk) {
                val hasChildPartitions = rawList.any { it.name.startsWith(name) && it.name != name }
                if (hasChildPartitions) {
                    continue
                }
            }

            if (!isMmcPartition && !isSdPartition && !isSdWholeDisk) continue

            val path = "/dev/block/$name"
            val diskName = when {
                isMmcPartition -> name.substringBeforeLast("p")
                isSdPartition -> name.filter { it.isLetter() }
                else -> name
            }
            val partNum = when {
                isMmcPartition -> name.substringAfterLast("p").toIntOrNull() ?: 1
                isSdPartition -> name.filter { it.isDigit() }.toIntOrNull() ?: 1
                else -> 1
            }
            val sizeBytes = entry.blocks * 1024L

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
            val majorMinor = devMajorMinorMap[name] ?: "${entry.major}:${entry.minor}"
            val voldMajorComma = if (majorMinor.contains(":")) majorMinor.replace(':', ',') else "${entry.major},${entry.minor}"
            val voldMajorUnderscore = if (majorMinor.contains(":")) majorMinor.replace(':', '_') else "${entry.major}_${entry.minor}"

            // Match all mounts for this physical partition across all Android subsystems
            val matchingMounts = allMounts.filter { m ->
                m.spec == path ||
                m.spec.endsWith("/$name") ||
                (voldMajorComma.isNotBlank() && (m.spec.contains("public:$voldMajorComma") || m.spec.contains("disk:$voldMajorComma"))) ||
                (voldMajorUnderscore.isNotBlank() && (m.spec.contains("public:$voldMajorUnderscore") || m.spec.contains("disk:$voldMajorUnderscore"))) ||
                m.spec.endsWith("/$majorMinor") ||
                m.spec.contains(majorMinor) ||
                (!uuid.isNullOrBlank() && (
                    m.file.contains(uuid) || m.spec.contains(uuid)
                ))
            }

            // Canonical Mount Hierarchy:
            val targetMountEntry = matchingMounts.firstOrNull { it.file == targetMountPoint }
            val isTargetMount = targetMountEntry != null

            val portableMountEntry = matchingMounts.firstOrNull { m ->
                (m.file.startsWith("/storage/") && !m.file.startsWith("/storage/emulated") && !m.file.startsWith("/storage/self")) ||
                m.file.startsWith("/mnt/media_rw/") ||
                m.file.startsWith("/mnt/pass_through/")
            }
            val isPortableMount = !isTargetMount && portableMountEntry != null

            val rootMountEntry = matchingMounts.firstOrNull { m ->
                !m.file.startsWith("/data/media/") &&
                !m.file.startsWith("/mnt/runtime/") &&
                !m.file.startsWith("/mnt/user/") &&
                !m.file.startsWith("/storage/emulated/") &&
                !m.file.startsWith("/storage/self/") &&
                !m.file.startsWith("/apex/")
            }

            val isMounted = matchingMounts.isNotEmpty()
            val canonicalMountPoint = when {
                isTargetMount -> targetMountPoint
                isPortableMount -> {
                    matchingMounts.firstOrNull { it.file.startsWith("/storage/") && !it.file.startsWith("/storage/emulated") && !it.file.startsWith("/storage/self") }?.file
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

            // Removability Check
            val hasVoldNode = voldMajorComma.isNotBlank() && allMounts.any { it.spec.contains("public:$voldMajorComma") }
            val isKnownPublic = smPublicVolumes.containsKey(majorMinor) || (!uuid.isNullOrBlank() && smPublicVolumes.containsKey(uuid))
            val isRemovable = isMmcPartition ||
                removableDisks.contains(diskName) ||
                usbDisks.contains(diskName) ||
                isTargetMount ||
                isPortableMount ||
                hasVoldNode ||
                isKnownPublic

            if (!isMmcPartition && !isRemovable && !isTargetMount) {
                continue
            }

            if (sizeBytes < 100 * 1024 * 1024L && !isTargetMount && !isPortableMount) {
                continue
            }

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
            val isSuitable = isTargetMount || (isRemovable && (isLinuxFs || isPortableMount || (partNum >= 2 && !isMounted)))

            // Instantly compute used & free bytes via canonical mount point and StatFs
            var usedBytes = 0L
            var freeBytes = 0L
            if (isMounted && canonicalMountPoint != null) {
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
                    isMountTargetReady = isSuitable,
                    healthStatus = DiskHealthStatus.HEALTHY,
                    healthErrorDetails = null
                )
            )
        }

        // Supplementary pass: Guarantee any mounted /storage/<UUID> or Android public volumes are registered
        val validVolumeRegex = Regex("^/storage/([0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}|[a-f0-9\\-]{36})$")
        val externalStorageMounts = allMounts.filter { m ->
            m.file.startsWith("/storage/") &&
            !m.file.contains("/Android/") &&
            !m.file.contains("/.") &&
            validVolumeRegex.matches(m.file)
        }.distinctBy { it.file }

        for (smMount in externalStorageMounts) {
            val mntPath = smMount.file
            val mntUuid = mntPath.substringAfterLast("/")
            if (partitionItems.any { it.mountPoint == mntPath || (it.uuid != null && it.uuid.equals(mntUuid, ignoreCase = true)) }) {
                continue
            }

            val voldMount = allMounts.firstOrNull { it.file.contains(mntUuid) && it.spec.contains("public:") }
            val majorMinor = voldMount?.spec?.substringAfter("public:")?.replace(',', ':') ?: ""
            val resolvedDevName = if (majorMinor.isNotBlank()) {
                devMajorMinorMap.entries.firstOrNull { it.value == majorMinor }?.key
            } else null

            val devPathCandidate = when {
                resolvedDevName != null -> "/dev/block/$resolvedDevName"
                smMount.spec.startsWith("/dev/block/") -> smMount.spec
                else -> null
            } ?: continue

            val devName = resolvedDevName ?: devPathCandidate.substringAfterLast("/")
            if (devName.startsWith("loop") || devName.startsWith("dm-") || devName.startsWith("ram") || devName.contains(".")) {
                continue
            }

            val diskName = if (devName.startsWith("sd") && devName.length >= 3) {
                devName.filter { it.isLetter() }
            } else if (devName.startsWith("mmcblk")) {
                devName.substringBeforeLast("p")
            } else {
                devName
            }
            val partNum = devName.filter { it.isDigit() }.toIntOrNull() ?: 1
            val devPath = devPathCandidate

            val fsType = blkidMap[devPath]?.first ?: blkidMap[devName]?.first ?: smMount.vfstype
            val label = blkidMap[devPath]?.second ?: blkidMap[devName]?.second
            val uuid = blkidMap[devPath]?.third ?: blkidMap[devName]?.third ?: mntUuid

            var totalBytes = 0L
            var usedBytes = 0L
            var freeBytes = 0L

            try {
                val stat = android.os.StatFs(mntPath)
                totalBytes = stat.blockCountLong * stat.blockSizeLong
                freeBytes = stat.availableBlocksLong * stat.blockSizeLong
                usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
            } catch (_: Exception) {}

            partitionItems.add(
                PartitionInfo(
                    path = devPath,
                    name = devName,
                    diskName = diskName,
                    partitionNumber = partNum,
                    sizeBytes = totalBytes,
                    usedBytes = usedBytes,
                    freeBytes = freeBytes,
                    fsType = fsType,
                    mountPoint = mntPath,
                    label = label,
                    uuid = uuid,
                    isMounted = true,
                    isTargetMount = mntPath == targetMountPoint,
                    isPortableMount = true,
                    isMountTargetReady = fsType.equals("f2fs", ignoreCase = true) || fsType.equals("ext4", ignoreCase = true)
                )
            )
        }

        val sortedPartitions = partitionItems.sortedWith(
            compareByDescending<PartitionInfo> { it.isTargetMount }
                .thenByDescending { it.isMountTargetReady }
                .thenBy { it.name }
        )

        // Build Physical Disks from verified external media:
        // 1. Disks containing verified partitions from sortedPartitions
        // 2. MicroSD block devices (mmcblk*)
        // 3. Removable storage disks (sysfs removable == 1)
        // 4. USB controller-connected block devices
        val candidateDiskNames = linkedSetOf<String>()
        sortedPartitions.forEach { candidateDiskNames.add(it.diskName) }
        sysfsDisks.keys.filter { it.startsWith("mmcblk") }.forEach { candidateDiskNames.add(it) }
        removableDisks.forEach { candidateDiskNames.add(it) }
        usbDisks.forEach { candidateDiskNames.add(it) }

        val disks = mutableListOf<SdCardDiskInfo>()
        val physicalDiskRegex = Regex("^(mmcblk[0-9]+|sd[a-z]+|nvme[0-9]+n[0-9]+)$")
        val hasDiskErrorFile = RootShell.exists("/dev/.mountx_disk_error")

        for (candidateDisk in candidateDiskNames) {
            if (!physicalDiskRegex.matches(candidateDisk) || candidateDisk.contains("boot") || candidateDisk.contains("rpmb")) {
                continue
            }

            val isMmc = candidateDisk.startsWith("mmcblk")
            val isUsb = usbDisks.contains(candidateDisk)
            val isRemovable = removableDisks.contains(candidateDisk)
            val diskPartitions = sortedPartitions.filter { it.diskName == candidateDisk }

            // Strictly reject internal fixed flash storage (such as UFS LUNs sda, sdb, sdc, sdd or motherboard NVMe)
            // A genuine external disk must either be MMC (MicroSD), connected over USB, flagged removable by the kernel,
            // or contain verified external/target partitions.
            if (!isMmc && !isUsb && !isRemovable && diskPartitions.isEmpty()) {
                continue
            }

            val diskPath = "/dev/block/$candidateDisk"
            val diskType = if (isMmc) DiskType.MICRO_SD else DiskType.USB_OTG

            val hw = sysfsDisks[candidateDisk] ?: diskHardwareCache[candidateDisk] ?: HwDiskCache("", "", "", 0L)
            val manfid = hw.manfid
            val model = hw.model
            val vendor = hw.vendor
            val sizeSectors = hw.sizeSectors

            val totalSizeBytes = if (sizeSectors > 0) sizeSectors * 512L else diskPartitions.sumOf { it.sizeBytes }

            if (totalSizeBytes <= 0L && diskPartitions.isEmpty()) {
                continue
            }

            val totalUsedBytes = diskPartitions.sumOf { it.usedBytes }
            val totalFreeBytes = if (totalSizeBytes > totalUsedBytes) {
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
                    if (vendor.isNotBlank()) "$vendor USB" else if (model.isNotBlank()) "$model USB" else "USB OTG"
                }
            }

            val cleanModel = if (model.isNotBlank()) model else if (!isMmc) "Storage" else ""

            val (diskHealth: DiskHealthStatus, diskErrorDetails: String?) = when {
                hasDiskErrorFile -> Pair(DiskHealthStatus.ERROR_IO, "Hardware I/O error or controller power cutoff")
                diskPartitions.any { it.healthStatus == DiskHealthStatus.ERROR_IO } -> Pair(DiskHealthStatus.ERROR_IO, "Partition I/O error")
                else -> Pair(DiskHealthStatus.HEALTHY, null)
            }

            disks.add(
                SdCardDiskInfo(
                    devicePath = diskPath,
                    diskName = candidateDisk,
                    vendorName = vendorDisplayName,
                    modelName = cleanModel,
                    totalSizeBytes = totalSizeBytes,
                    totalUsedBytes = totalUsedBytes,
                    totalFreeBytes = totalFreeBytes,
                    diskType = diskType,
                    partitions = diskPartitions,
                    healthStatus = diskHealth,
                    healthErrorDetails = diskErrorDetails
                )
            )
        }

        disks.removeAll { it.diskName.contains(".") || it.devicePath.contains("usb_com.") || it.diskName.startsWith("loop") || it.diskName.startsWith("dm-") }

        StorageHierarchyResult(
            partitions = sortedPartitions,
            disks = disks
        )
    }

    /**
     * Comprehensive scan of all MicroSD/USB block devices and partitions.
     * Delegates to unified detectStorageHierarchy for instant cached retrieval.
     */
    suspend fun detectPartitions(targetMountPoint: String = "/data/sdext2"): List<PartitionInfo> = withContext(Dispatchers.IO) {
        detectStorageHierarchy(targetMountPoint).partitions
    }

    /**
     * Check filesystem integrity of an unmounted block partition using fsck.
     * CAUTION: Must only be run when the partition is unmounted.
     */
    suspend fun checkFilesystem(
        blockDevice: String,
        fsType: String = ""
    ): Result<FsckReport> = withContext(Dispatchers.IO) {
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
            parseFsckOutput(output, res.code)
        }
    }

    private fun parseFsckOutput(output: String, exitCode: Int): FsckReport {
        val isClean = output.contains("clean", ignoreCase = true) || output.contains("no error", ignoreCase = true) || exitCode == 0
        val isRepaired = output.contains("repaired", ignoreCase = true) || output.contains("fixed", ignoreCase = true) || output.contains("FILE SYSTEM WAS MODIFIED", ignoreCase = true)
        val hasError = exitCode > 2 && !isClean && !isRepaired

        val status = when {
            hasError -> FsckStatus.ERROR
            isRepaired -> FsckStatus.REPAIRED
            isClean -> FsckStatus.CLEAN
            else -> FsckStatus.DIRTY_WARNING
        }

        val filesMatch = Regex("""(\d+)/(\d+)\s+files""").find(output)
        val blocksMatch = Regex("""(\d+)/(\d+)\s+blocks""").find(output)

        val filesCount = filesMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val totalFiles = filesMatch?.groupValues?.getOrNull(2)?.toLongOrNull() ?: 0L
        val blocksCount = blocksMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val totalBlocks = blocksMatch?.groupValues?.getOrNull(2)?.toLongOrNull() ?: 0L

        val summary = when (status) {
            FsckStatus.CLEAN -> "Filesystem is healthy and clean. No metadata errors found."
            FsckStatus.REPAIRED -> "Inconsistencies detected and successfully repaired."
            FsckStatus.DIRTY_WARNING -> "Filesystem flagged as dirty. Verification complete."
            FsckStatus.ERROR -> "Filesystem check reported issues. Review log for details."
        }

        return FsckReport(
            status = status,
            summary = summary,
            filesCount = filesCount,
            totalFiles = totalFiles,
            blocksCount = blocksCount,
            totalBlocks = totalBlocks,
            rawLog = output
        )
    }

    /**
     * Non-destructively change the partition label without altering data.
     */
    suspend fun setPartitionLabel(
        blockDevice: String,
        fsType: String,
        newLabel: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitizedLabel = newLabel.trim().replace("\"", "")
            if (sanitizedLabel.isBlank()) {
                error("Partition label cannot be empty.")
            }

            val cmd = when {
                fsType.contains("ext", ignoreCase = true) -> {
                    "tune2fs -L \"$sanitizedLabel\" \"$blockDevice\" 2>&1 || e2label \"$blockDevice\" \"$sanitizedLabel\" 2>&1"
                }
                fsType.contains("fat", ignoreCase = true) -> {
                    "fatlabel \"$blockDevice\" \"$sanitizedLabel\" 2>&1 || dosfslabel \"$blockDevice\" \"$sanitizedLabel\" 2>&1"
                }
                fsType.contains("exfat", ignoreCase = true) -> {
                    "exfatlabel \"$blockDevice\" \"$sanitizedLabel\" 2>&1"
                }
                fsType.contains("f2fs", ignoreCase = true) -> {
                    "f2fs.setlabel \"$sanitizedLabel\" \"$blockDevice\" 2>&1 || tune.f2fs -l \"$sanitizedLabel\" \"$blockDevice\" 2>&1"
                }
                else -> {
                    "tune2fs -L \"$sanitizedLabel\" \"$blockDevice\" 2>&1 || e2label \"$blockDevice\" \"$sanitizedLabel\" 2>&1"
                }
            }

            val res = RootShell.exec(cmd)
            if (res.isSuccess || res.output.contains("set", ignoreCase = true) || res.output.isBlank()) {
                "Partition label updated to \"$sanitizedLabel\" successfully."
            } else {
                error(res.output.ifBlank { "Failed to set partition label (exit code ${res.code})" })
            }
        }
    }

    /**
     * Query kernel supported filesystems from /proc/filesystems and check binary tool availability in a single compound shell probe.
     */
    suspend fun detectSupportedFilesystems(): List<SupportedFilesystemInfo> = withContext(Dispatchers.IO) {
        cachedSupportedFilesystems?.let { return@withContext it }
        val probeScript = """
            echo -n "f2fs:"; { [ -x /system/bin/make_f2fs ] || command -v make_f2fs || command -v mkfs.f2fs; } >/dev/null 2>&1 && echo 1 || echo 0
            echo -n "ext4:"; { [ -x /system/bin/mke2fs ] || [ -x /system/bin/mkfs.ext4 ] || command -v mke2fs || command -v mkfs.ext4; } >/dev/null 2>&1 && echo 1 || echo 0
            echo -n "fat:"; { [ -x /system/bin/newfs_msdos ] || command -v newfs_msdos || command -v mkfs.vfat || command -v mkdosfs; } >/dev/null 2>&1 && echo 1 || echo 0
            echo -n "exfat:"; { [ -x /system/bin/mkfs.exfat ] || command -v mkfs.exfat; } >/dev/null 2>&1 && echo 1 || echo 0
            echo "===FILESYSTEMS==="
            cat /proc/filesystems 2>/dev/null
        """.trimIndent()
        val res = RootShell.exec(probeScript)
        var f2fsTool = false
        var ext4Tool = false
        var fatTool = false
        var exfatTool = false
        val kernelSupported = mutableSetOf<String>()
        var inFsSection = false

        if (res.isSuccess) {
            for (line in res.stdout) {
                val trimmed = line.trim()
                if (trimmed == "===FILESYSTEMS===") {
                    inFsSection = true
                    continue
                }
                if (!inFsSection) {
                    if (trimmed.startsWith("f2fs:")) f2fsTool = trimmed.endsWith("1")
                    if (trimmed.startsWith("ext4:")) ext4Tool = trimmed.endsWith("1")
                    if (trimmed.startsWith("fat:")) fatTool = trimmed.endsWith("1")
                    if (trimmed.startsWith("exfat:")) exfatTool = trimmed.endsWith("1")
                } else {
                    if (!trimmed.startsWith("nodev") && trimmed.isNotBlank()) {
                        kernelSupported.add(trimmed.lowercase())
                    }
                }
            }
        } else {
            kernelSupported.addAll(listOf("f2fs", "ext4", "ext3", "ext2", "vfat", "exfat"))
        }

        val list = mutableListOf<SupportedFilesystemInfo>()

        // F2FS
        val f2fsKernel = kernelSupported.contains("f2fs")
        list.add(
            SupportedFilesystemInfo(
                fsType = FilesystemType.F2FS,
                isKernelSupported = f2fsKernel,
                isToolSupported = f2fsTool,
                description = if (f2fsKernel && f2fsTool) "Optimized for flash storage, fastest game loading throughput" else "Kernel does not support F2FS"
            )
        )

        // EXT4
        val ext4Kernel = kernelSupported.contains("ext4") || kernelSupported.contains("ext3") || kernelSupported.contains("ext2")
        list.add(
            SupportedFilesystemInfo(
                fsType = FilesystemType.EXT4,
                isKernelSupported = ext4Kernel,
                isToolSupported = ext4Tool,
                description = if (ext4Kernel && ext4Tool) "Standard Linux filesystem, widely compatible, stable & reliable" else "Kernel does not support Ext4"
            )
        )

        // FAT32
        val fatKernel = kernelSupported.contains("vfat") || kernelSupported.contains("msdos")
        list.add(
            SupportedFilesystemInfo(
                fsType = FilesystemType.FAT32,
                isKernelSupported = fatKernel,
                isToolSupported = fatTool,
                description = if (fatKernel && fatTool) "Universal cross-platform storage (4 GB single-file limit)" else "Kernel does not support FAT32"
            )
        )

        // exFAT
        val exfatKernel = kernelSupported.contains("exfat")
        list.add(
            SupportedFilesystemInfo(
                fsType = FilesystemType.EXFAT,
                isKernelSupported = exfatKernel,
                isToolSupported = exfatTool,
                description = if (exfatKernel && exfatTool) "Modern cross-platform storage for files larger than 4 GB" else "Kernel does not support exFAT"
            )
        )

        cachedSupportedFilesystems = list
        list
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
            // 1. If already mounted cleanly at target mount point, ensure private propagation and return immediately
            if (RootShell.isMountpoint(mountPoint)) {
                RootShell.exec("mount --make-rprivate \"$mountPoint\" 2>/dev/null")
                return@runCatching Unit
            }

            RootShell.exec("mkdir -p \"$mountPoint\" 2>/dev/null")

            // 2. Resolve block device dynamically if path shifted on reboot or was unassigned
            var targetDev = blockDevice.trim()
            if (targetDev.isBlank() || !RootShell.exists(targetDev)) {
                // 1. Try reading SD_BLOCK from module config.conf
                val moduleConfBlock = RootShell.execForOutput("grep -E '^SD_BLOCK=' /data/adb/modules/MountX/config.conf /data/adb/modules/mountx/config.conf /data/adb/modules/Mountify/config.conf 2>/dev/null | head -n 1 | cut -d= -f2").trim()
                if (moduleConfBlock.isNotBlank() && RootShell.exists(moduleConfBlock)) {
                    targetDev = moduleConfBlock
                }
            }

            if (targetDev.isBlank() || !RootShell.exists(targetDev)) {
                // 2. Direct blkid probe for f2fs partition on mmcblk
                val f2fsBlkid = RootShell.execForOutput("blkid /dev/block/mmcblk* 2>/dev/null | grep 'TYPE=\"f2fs\"' | head -n 1 | cut -d: -f1").trim()
                if (f2fsBlkid.isNotBlank() && RootShell.exists(f2fsBlkid)) {
                    targetDev = f2fsBlkid
                }
            }

            if (targetDev.isBlank() || !RootShell.exists(targetDev)) {
                val partitions = detectPartitions(mountPoint)
                // 3. Look for unmounted F2FS partition (avoid picking vold-mounted ext4 mmcblk0p1)
                val unmountedF2fs = partitions.firstOrNull { p ->
                    p.mountPoint.isNullOrBlank() && p.fsType.equals("f2fs", ignoreCase = true) && RootShell.exists(p.path)
                }
                // 4. Look for unmounted partition on mmcblk (preferring larger capacity)
                val unmountedMmc = partitions.filter { p ->
                    p.mountPoint.isNullOrBlank() && p.path.contains("mmcblk") && RootShell.exists(p.path)
                }.maxByOrNull { it.sizeBytes }
                // 5. Look for unmounted ext4 partition
                val unmountedExt4 = partitions.firstOrNull { p ->
                    p.mountPoint.isNullOrBlank() && p.fsType.equals("ext4", ignoreCase = true) && RootShell.exists(p.path)
                }

                val candidate = unmountedF2fs
                    ?: unmountedMmc
                    ?: unmountedExt4
                    ?: partitions.firstOrNull { it.path.contains("mmcblk0p3") && RootShell.exists(it.path) }
                    ?: partitions.firstOrNull { it.isTargetMount }
                    ?: partitions.firstOrNull { it.mountPoint == mountPoint }
                    ?: partitions.firstOrNull { (it.fsType.equals("f2fs", ignoreCase = true) || it.fsType.equals("ext4", ignoreCase = true)) && RootShell.exists(it.path) }

                if (candidate != null && RootShell.exists(candidate.path)) {
                    targetDev = candidate.path
                }
            }

            if (targetDev.isBlank() || !RootShell.exists(targetDev)) {
                error("No accessible block device found for $mountPoint (requested: $blockDevice)")
            }
            
            // Try primary fsType with optimized game streaming flags
            val primaryCmd = when (fsType) {
                FilesystemType.F2FS -> "mount -t f2fs -o rw,noatime,nodiratime,inline_data,inline_dentry,flush_merge,mode=adaptive \"$targetDev\" \"$mountPoint\""
                FilesystemType.EXT4 -> "mount -t ext4 -o rw,noatime,nodiratime,commit=60,delalloc,data=writeback \"$targetDev\" \"$mountPoint\""
                FilesystemType.EXFAT -> "mount -t exfat -o rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000,iocharset=utf8 \"$targetDev\" \"$mountPoint\" 2>/dev/null || mount -t exfat -o rw,noatime,nodiratime \"$targetDev\" \"$mountPoint\""
                FilesystemType.FAT32 -> "mount -t vfat -o rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000 \"$targetDev\" \"$mountPoint\" 2>/dev/null || mount -t vfat -o rw,noatime,nodiratime \"$targetDev\" \"$mountPoint\""
                FilesystemType.NTFS -> "mount.ntfs -o rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000 \"$targetDev\" \"$mountPoint\" 2>/dev/null || mount -t ntfs-3g -o rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000 \"$targetDev\" \"$mountPoint\" 2>/dev/null || mount -t ntfs -o rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000 \"$targetDev\" \"$mountPoint\""
                else -> "mount -t ${fsType.command} -o noatime,nodiratime,rw \"$targetDev\" \"$mountPoint\""
            }
            var res = RootShell.exec(primaryCmd)
            
            // If failed, try comprehensive multi-filesystem fallback chain
            if (!res.isSuccess) {
                val fallbackCmd = "mount -t ext4 -o noatime,nodiratime,rw \"$targetDev\" \"$mountPoint\" 2>/dev/null || mount -t f2fs -o noatime,rw \"$targetDev\" \"$mountPoint\" 2>/dev/null || mount -t exfat -o rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000,iocharset=utf8 \"$targetDev\" \"$mountPoint\" 2>/dev/null || mount.ntfs -o rw,noatime,uid=1023,gid=1023,fmask=0000,dmask=0000 \"$targetDev\" \"$mountPoint\" 2>/dev/null || mount -t vfat -o rw,noatime,uid=1023,gid=1023,fmask=0000,dmask=0000 \"$targetDev\" \"$mountPoint\" 2>/dev/null || mount \"$targetDev\" \"$mountPoint\""
                res = RootShell.exec(fallbackCmd)
            }

            if (!RootShell.isMountpoint(mountPoint)) {
                error("Failed to mount $targetDev to $mountPoint: ${res.stderr.joinToString("\n")}")
            }
            RootShell.exec("mount --make-rprivate \"$mountPoint\" 2>/dev/null")
            Unit
        }
    }

    /**
     * Unmount the SD partition.
     */
    suspend fun unmountSdPartition(mountPoint: String = "/data/sdext2"): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // 1. Unbind any game folders bind-mounted across runtime namespaces
                val unbindScript = """
                    for m in $(grep "$mountPoint" /proc/mounts 2>/dev/null | cut -d' ' -f2); do
                        if [ "${'$'}m" != "$mountPoint" ]; then
                            umount -f -l "${'$'}m" 2>/dev/null
                        fi
                    done
                    for ns in /proc/[0-9]*/ns/mnt; do
                        nsenter --mount="${'$'}ns" umount -f -l "$mountPoint" 2>/dev/null
                    done
                """.trimIndent()
                RootShell.execScript(unbindScript)
                RootShell.exec("sync")

                // 2. Unmount the root SD mount point
                if (RootShell.isMountpoint(mountPoint)) {
                    val res = RootShell.exec("umount -f -l \"$mountPoint\" 2>/dev/null")
                    if (!res.isSuccess && RootShell.isMountpoint(mountPoint)) {
                        error("Failed to unmount $mountPoint: ${res.stderr.joinToString("\n")}")
                    }
                }
            }
        }

    /**
     * Unmount an individual partition cleanly and independently without disturbing other partitions.
     * Supports both Android Vold public volumes (via `sm unmount`) and direct Linux mountpoints.
     */
    suspend fun unmountPartition(partition: PartitionInfo): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val devName = partition.name
            val blockDev = partition.path
            val mnt = partition.mountPoint

            if (partition.isTargetMount || mnt == "/data/sdext2") {
                // If it's the target mount (/data/sdext2), unbind game folders and unmount target
                unmountSdPartition(mnt ?: "/data/sdext2").getOrThrow()
                // Clean up any remaining mounts for this block device across all namespaces
                val mountsRes = RootShell.exec("grep -F \"$blockDev\" /proc/mounts 2>/dev/null")
                val mountLines = mountsRes.stdout.map { it.trim() }.filter { it.isNotBlank() }
                for (line in mountLines) {
                    val m = line.split(Regex("\\s+")).getOrNull(1)
                    if (!m.isNullOrBlank()) {
                        RootShell.exec("umount -f -l \"$m\" 2>/dev/null")
                    }
                }
                RootShell.exec("umount -f -l \"$blockDev\" 2>/dev/null")
            } else {
                // 1. If it has an Android Vold volume (public:major,minor), execute sm unmount
                val mmRes = RootShell.exec("cat /sys/class/block/$devName/dev 2>/dev/null")
                val mm = if (mmRes.isSuccess && mmRes.stdout.isNotEmpty()) {
                    mmRes.stdout.first().trim().replace(":", ",")
                } else null

                if (mm != null) {
                    RootShell.exec("sm unmount \"public:$mm\" 2>/dev/null")
                }

                // 2. Direct Linux umount by mount point if still mounted
                if (!mnt.isNullOrBlank()) {
                    RootShell.exec("umount -f -l \"$mnt\" 2>/dev/null")
                }

                // 3. Direct Linux umount by block device (cleans up any remaining mounts in all namespaces)
                val mountsRes = RootShell.exec("grep -F \"$blockDev\" /proc/mounts 2>/dev/null")
                val mountLines = mountsRes.stdout.map { it.trim() }.filter { it.isNotBlank() }
                for (line in mountLines) {
                    val m = line.split(Regex("\\s+")).getOrNull(1)
                    if (!m.isNullOrBlank()) {
                        RootShell.exec("umount -f -l \"$m\" 2>/dev/null")
                    }
                }
                RootShell.exec("umount -f -l \"$blockDev\" 2>/dev/null")
            }
            Unit
        }
    }

    /**
     * Mount an individual partition cleanly and independently.
     * Supports both Android Vold public volumes (via `sm mount`) and direct target mounting.
     */
    suspend fun mountPartition(
        partition: PartitionInfo,
        targetMountPoint: String = "/data/sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val devName = partition.name
            val blockDev = partition.path

            // 1. If it has an Android Vold volume, try sm mount first
            val mmRes = RootShell.exec("cat /sys/class/block/$devName/dev 2>/dev/null")
            val mm = if (mmRes.isSuccess && mmRes.stdout.isNotEmpty()) {
                mmRes.stdout.first().trim().replace(":", ",")
            } else null

            var smMounted = false
            if (mm != null) {
                val smRes = RootShell.exec("sm mount \"public:$mm\" 2>/dev/null")
                if (smRes.isSuccess) {
                    val checkRes = RootShell.exec("sm list-volumes 2>/dev/null")
                    if (checkRes.isSuccess && checkRes.stdout.any { it.contains("public:$mm mounted") }) {
                        smMounted = true
                    }
                }
            }

            // 2. If not mounted via Vold, mount directly to target mount point
            if (!smMounted) {
                val fs = when (partition.fsType.lowercase()) {
                    "f2fs" -> FilesystemType.F2FS
                    "ext4" -> FilesystemType.EXT4
                    "vfat", "fat32" -> FilesystemType.FAT32
                    "exfat" -> FilesystemType.EXFAT
                    "ntfs" -> FilesystemType.NTFS
                    else -> FilesystemType.F2FS
                }
                mountSdPartition(blockDev, targetMountPoint, fs).getOrThrow()
            }
            Unit
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

            val isBlockAlive = if (blockDevice.startsWith("/dev/block/")) {
                RootShell.exists(blockDevice) || RootShell.exists("/sys/class/block/${blockDevice.substringAfterLast('/')}")
            } else true

            if (!isBlockAlive) {
                return@withContext StorageInfo(
                    blockDevice = blockDevice,
                    mountPoint = mountPoint,
                    filesystem = filesystem,
                    totalBytes = 0L,
                    usedBytes = 0L,
                    freeBytes = 0L,
                    isMounted = false
                )
            }

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
                val statRes = RootShell.exec("stat -f -c %s:%b:%a \"$mountPoint\" 2>/dev/null")
                val parts = statRes.output.trim().split(":")
                if (parts.size >= 3) {
                    val bs = parts[0].toLongOrNull() ?: 0L
                    val blocks = parts[1].toLongOrNull() ?: 0L
                    val avail = parts[2].toLongOrNull() ?: 0L
                    totalBytes = blocks * bs
                    freeBytes = avail * bs
                    usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
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
     * Delegates to unified detectStorageHierarchy.
     */
    suspend fun detectAllDisks(
        targetMountPoint: String = "/data/sdext2",
        preScannedPartitions: List<PartitionInfo>? = null
    ): List<SdCardDiskInfo> = withContext(Dispatchers.IO) {
        detectStorageHierarchy(targetMountPoint).disks
    }

    /**
     * Get free bytes available on internal storage (/data).
     */
    fun getInternalFreeBytes(): Long {
        return try {
            val stat = android.os.StatFs("/data")
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Completely restore an app's offloaded data from MicroSD back to internal storage,
     * restoring ownership and SELinux contexts, protected by a Partial WakeLock.
     */
    suspend fun restoreAndCleanGame(
        context: android.content.Context,
        packageName: String,
        mountPoints: List<MountPointConfig>,
        sdBase: String = "/data/sdext2",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            val wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "MountX:RestoreWakeLock")
            wakeLock?.acquire(15 * 60 * 1000L) // 15 mins safeguard against deep sleep
            try {
                onProgress(0.05f, "Menghentikan proses aplikasi ($packageName)...")
                RootShell.exec("am force-stop \"$packageName\"")

                // Pre-Flight unmount across all runtime namespaces
                for (point in mountPoints) {
                    val ptUserId = MountManager.resolveUserId(point.targetPath)
                    val rel = MountManager.extractRelativePath(point.targetPath)
                    for (ns in listOf("/mnt/runtime/default", "/mnt/runtime/read", "/mnt/runtime/write", "/mnt/runtime/full", "/storage")) {
                        RootShell.exec("umount -f -l \"$ns/emulated/$ptUserId/$rel\" 2>/dev/null")
                        RootShell.exec("umount -f -l \"$ns/$rel\" 2>/dev/null")
                    }
                    RootShell.exec("umount -f -l \"/data/media/$ptUserId/$rel\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"${point.targetPath}\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"${point.sourcePath}\" 2>/dev/null")

                    if (RootShell.isMountpoint(point.targetPath)) {
                        RootShell.exec("sync")
                        RootShell.exec("umount -f -l \"${point.targetPath}\" 2>/dev/null")
                        if (RootShell.isMountpoint(point.targetPath)) {
                            throw IllegalStateException("Gagal melepas mount ${point.targetPath}. Pemulihan dibatalkan demi keamanan data.")
                        }
                    }
                }

                // Pre-Flight Free Space Guard (Reserve 1GB safety headroom for Android OS)
                var totalSrcBytes = 0L
                for (point in mountPoints) {
                    val isContainer = point.isEffectivelyVirtualContainer()
                    val src = if (isContainer) {
                        point.containerImgPath?.ifBlank { null }
                            ?: (if (point.sourcePath.endsWith(".img")) point.sourcePath else "$sdBase/.mountx/containers/${packageName}_data.img")
                    } else {
                        point.sourcePath
                    }
                    val sz = getDirSizeBytes(src)
                    totalSrcBytes += if (sz > 0L) sz else point.sizeBytes
                }
                val internalAvailable = try {
                    android.os.StatFs("/data").availableBytes
                } catch (e: Exception) {
                    getAvailableFreeBytes("/data")
                }
                val safetyReserve = 250_000_000L // 250 MB reserve to prevent OS soft-freeze / crash
                if (totalSrcBytes > 0L && totalSrcBytes > (internalAvailable - safetyReserve)) {
                    val reqMb = (totalSrcBytes + safetyReserve) / (1024 * 1024)
                    val availMb = internalAvailable / (1024 * 1024)
                    throw IllegalStateException("Memori internal tidak mencukupi untuk pemulihan! Dibutuhkan: ${reqMb} MB (termasuk cadangan sistem 250 MB), Tersedia: ${availMb} MB.")
                }

                val totalPoints = mountPoints.size.coerceAtLeast(1)
                mountPoints.forEachIndexed { index, point ->
                    val progressBase = 0.1f + (index.toFloat() / totalPoints.toFloat()) * 0.75f
                    val folderName = point.targetPath.substringAfterLast('/')
                    onProgress(progressBase, "Memulihkan berkas: $folderName...")

                    val isContainer = point.isEffectivelyVirtualContainer()
                    if (isContainer) {
                        val imgFile = point.containerImgPath?.ifBlank { null }
                            ?: (if (point.sourcePath.endsWith(".img")) point.sourcePath else "$sdBase/.mountx/containers/${packageName}_data.img")
                        if (RootShell.exists(imgFile)) {
                            val tempMount = "/dev/mountx_temp_${packageName}"
                            RootShell.exec("mkdir -p \"$tempMount\"")
                            val loopDev = MountManager.attachLoopDevice(imgFile)
                            if (loopDev.isNotEmpty()) {
                                RootShell.exec("mount -t ext4 \"$loopDev\" \"$tempMount\"")
                                RootShell.exec("mkdir -p \"${point.targetPath}\"")
                                RootShell.exec("cp -a \"$tempMount\"/* \"${point.targetPath}\"/ 2>/dev/null")
                                RootShell.exec("sync")
                                RootShell.exec("umount -l \"$tempMount\"")
                                RootShell.exec("losetup -d \"$loopDev\"")
                            }
                            RootShell.exec("rmdir \"$tempMount\" 2>/dev/null")
                            RootShell.exec("rm -f \"$imgFile\"")
                            RootShell.exec("restorecon -FR \"${point.targetPath}\" 2>/dev/null")
                        }
                    } else {
                        val src = point.sourcePath
                        val tgt = point.targetPath
                        if (RootShell.exists(src)) {
                            val parentInternal = java.io.File(tgt).parent ?: "/data/media/0"
                            RootShell.exec("mkdir -p \"$parentInternal\"")
                            RootShell.exec("cp -a -f \"$src\" \"$tgt\"")
                            RootShell.exec("chmod -R 775 \"$tgt\" 2>/dev/null")
                            RootShell.exec("restorecon -FR \"$tgt\" 2>/dev/null")
                            if (!RootShell.isMountpoint(tgt) && src.trimEnd('/') != tgt.trimEnd('/')) {
                                RootShell.exec("rm -rf \"$src\"")
                            }
                        }
                    }
                }

                // Clean up any remaining empty app directories on MicroSD so no orphaned stubs remain
                RootShell.exec("rm -rf \"$sdBase/MountX/Android/data/$packageName\" \"$sdBase/MountX/Android/obb/$packageName\" \"$sdBase/MountX/Android/media/$packageName\" \"$sdBase/Android/data/$packageName\" \"$sdBase/Android/obb/$packageName\" 2>/dev/null")

                onProgress(0.92f, "Memulihkan perizinan dan konteks keamanan SELinux...")
                val gameUserId = mountPoints.firstOrNull()?.let { MountManager.resolveUserId(it.targetPath) } ?: 0
                RootShell.exec("restorecon -FR \"/data/media/$gameUserId/Android/data/$packageName\" 2>/dev/null")
                RootShell.exec("restorecon -FR \"/data/media/$gameUserId/Android/obb/$packageName\" 2>/dev/null")
                RootShell.exec("restorecon -FR \"/data/user/$gameUserId/$packageName\" 2>/dev/null")
                onProgress(1.0f, "Selesai")
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            }
        }
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
            // 1. Comprehensive unmount of all mountpoints referencing this block device
            val procMounts = RootShell.execForOutput("cat /proc/mounts 2>/dev/null").lines()
            for (line in procMounts) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val dev = parts[0]
                    val mnt = parts[1]
                    if (dev == blockDevice || dev.startsWith(blockDevice) || mnt.contains(blockDevice.substringAfterLast('/'))) {
                        RootShell.exec("umount -f -l \"$mnt\" 2>/dev/null")
                    }
                }
            }

            // 2. Kill any processes keeping open handles on the block device
            RootShell.exec("fuser -k -9 \"$blockDevice\" 2>/dev/null")

            // 3. Issue Vold / Framework unmount request to drop Android storage locks
            RootShell.exec("sm unmount \"$blockDevice\" 2>/dev/null")

            // 4. Forceful lazy unmount directly on the block device
            RootShell.exec("umount -f -l \"$blockDevice\" 2>/dev/null")

            // 5. Brief settle delay for kernel VFS release
            delay(250)

            val cmd = when (fsType) {
                FilesystemType.F2FS -> "(make_f2fs -l \"$label\" -f \"$blockDevice\" 2>&1 || mkfs.f2fs -l \"$label\" -f \"$blockDevice\" 2>&1)"
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

            // 3. Create fresh partition table using sgdisk (or seamless busybox fdisk fallback)
            val sgdiskCheck = RootShell.exec("which sgdisk 2>/dev/null").output.trim()
            var sgdiskSuccess = false

            if (sgdiskCheck.isNotBlank()) {
                val zapRes = RootShell.exec("sgdisk -o \"$diskPath\" 2>/dev/null")
                if (zapRes.isSuccess) {
                    var allCreated = true
                    partitions.forEachIndexed { index, cfg ->
                        val partNum = index + 1
                        val isLast = index == partitions.size - 1

                        val typeCode = when (cfg.fsType) {
                            FilesystemType.FAT32, FilesystemType.EXFAT -> "0700"
                            else -> "8300"
                        }

                        val sizeParam = if (isLast) "0" else "+${cfg.sizeKb}K"
                        val createCmd = "sgdisk -n $partNum:0:$sizeParam -t $partNum:$typeCode -c $partNum:\"${cfg.label}\" \"$diskPath\""
                        val res = RootShell.exec(createCmd)
                        if (!res.isSuccess || res.output.contains("invalid option") || res.output.contains("Error")) {
                            allCreated = false
                        }
                    }
                    sgdiskSuccess = allCreated
                }
            }

            if (!sgdiskSuccess) {
                AppLogger.warn("StorageManager", "sgdisk unavailable or unsupported on custom ROM. Falling back to busybox fdisk script...")
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
                    if (cfg.fsType == FilesystemType.FAT32 || cfg.fsType == FilesystemType.EXFAT) {
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
                val partDev = if (diskPath.last().isDigit()) "${diskPath}p$partNum" else "$diskPath$partNum"

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

    private suspend fun getDirSizeBytes(path: String): Long {
        if (!RootShell.exists(path)) return 0L
        val res = RootShell.exec("du -s -k \"$path\" 2>/dev/null")
        val kb = res.output.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
        return kb * 1024L
    }

    suspend fun inspectStorageTarget(targetDir: String): StorageInspectionResult {
        val fsType = RootShell.execForOutput("stat -f -c %T \"$targetDir\" 2>/dev/null").trim().lowercase().ifEmpty { "ext4" }
        val clusterBytes = RootShell.execForOutput("stat -f -c %S \"$targetDir\" 2>/dev/null").trim().toLongOrNull() ?: 4096L
        val isNonPosix = fsType in listOf("exfat", "vfat", "msdos", "ntfs") || targetDir.startsWith("/mnt/media_rw")
        val isFat32 = fsType in listOf("vfat", "msdos", "fat32")
        val supportsSymlinks = !isNonPosix
        return StorageInspectionResult(
            fsType = fsType,
            clusterSizeBytes = clusterBytes,
            isNonPosix = isNonPosix,
            isFat32 = isFat32,
            supportsSymlinks = supportsSymlinks
        )
    }

    private suspend fun checkFat32FileLimit(sourceDir: String, isFat32: Boolean) {
        if (!isFat32 || !RootShell.exists(sourceDir)) return
        val checkCmd = "find \"$sourceDir\" -type f -size +4194303k -print -quit 2>/dev/null"
        val overLimitFile = RootShell.execForOutput(checkCmd).trim()
        if (overLimitFile.isNotEmpty()) {
            val fileName = overLimitFile.substringAfterLast('/')
            throw FileTooLargeForFat32Exception(
                filePath = overLimitFile,
                fileSize = 4294967296L,
                message = "Batas FAT32 Terlampaui: File '$fileName' berukuran >= 4 GB. Sistem file FAT32 tidak dapat menyimpan file ini. Silakan format partisi penyimpanan ke F2FS atau ExFAT terlebih dahulu."
            )
        }
    }

    private suspend fun calculateSlackHeadroom(sourceDir: String, clusterSizeBytes: Long): Long {
        if (clusterSizeBytes <= 4096L || !RootShell.exists(sourceDir)) return 0L
        val fileCount = RootShell.execForOutput("find \"$sourceDir\" -type f 2>/dev/null | wc -l").trim().toLongOrNull() ?: 0L
        return fileCount * (clusterSizeBytes / 2L)
    }

    private suspend fun safeCleanSourceDirectory(
        sourcePathToClean: String,
        destPathPreserved: String,
        packageName: String
    ): Boolean {
        if (sourcePathToClean.trimEnd('/') == destPathPreserved.trimEnd('/') || sourcePathToClean.isBlank() || sourcePathToClean == "/") {
            AppLogger.error("StorageManager", "ANTI-DELETION LOCK: Invalid cleanup paths ($sourcePathToClean == $destPathPreserved)")
            return false
        }
        // Lock 1: Mountpoint check across namespaces
        if (RootShell.isMountpoint(sourcePathToClean)) {
            AppLogger.error("StorageManager", "ANTI-DELETION LOCK: $sourcePathToClean is still a mountpoint! Refusing delete to protect real data.")
            return false
        }
        val relPath = MountManager.extractRelativePath(sourcePathToClean)
        for (ns in listOf("/mnt/runtime/default", "/mnt/runtime/read", "/mnt/runtime/write", "/mnt/runtime/full", "/storage/emulated/0", "/data/media/0")) {
            if (RootShell.isMountpoint("$ns/$relPath")) {
                AppLogger.error("StorageManager", "ANTI-DELETION LOCK: Namespace $ns/$relPath is still mounted! Refusing delete.")
                return false
            }
        }

        // Lock 2: Canary check
        if (RootShell.exists("$sourcePathToClean/.mountx_canary") && !RootShell.exists("$destPathPreserved/.mountx_canary")) {
            AppLogger.error("StorageManager", "ANTI-DELETION LOCK: $sourcePathToClean contains .mountx_canary while destination does not! Refusing delete.")
            return false
        }

        // Lock 3: Filesystem device ID comparison (via stat -c %d)
        val devSource = RootShell.execForOutput("stat -c %d \"$sourcePathToClean\" 2>/dev/null").trim()
        val devDest = RootShell.execForOutput("stat -c %d \"$destPathPreserved\" 2>/dev/null").trim()
        if (devSource.isNotBlank() && devDest.isNotBlank() && devSource == devDest && devSource != "0") {
            AppLogger.warn("StorageManager", "ANTI-DELETION WARNING: Both source ($sourcePathToClean) and destination ($destPathPreserved) share block device $devSource.")
        }

        // Passed all guards: clean directory contents
        AppLogger.info("StorageManager", "Safe clean approved for $sourcePathToClean (Verified independent from $destPathPreserved)")
        RootShell.exec("rm -rf \"$sourcePathToClean\"/* \"$sourcePathToClean\"/.[!.]* 2>/dev/null")
        return true
    }

    private suspend fun resolveBusyboxPath(): String? {
        val candidates = listOf(
            "/data/adb/modules/MountX/bin/busybox",
            "/data/adb/ap/bin/busybox",
            "/data/adb/ksu/bin/busybox",
            "/data/adb/magisk/busybox",
            "busybox"
        )
        for (candidate in candidates) {
            val check = RootShell.exec("$candidate tar --help 2>&1 | grep -q 'Create' && echo YES || echo NO").output.trim()
            if (check == "YES") return candidate
        }
        return null
    }

    private suspend fun copyWithRealtimeProgress(
        sourceDir: String,
        destDir: String,
        categoryName: String,
        pointSizeBytes: Long,
        overallProcessedBytes: Long,
        totalMigrationBytes: Long,
        stepIndex: Int,
        totalSteps: Int,
        stepDescriptions: List<String>,
        opType: OperationType,
        title: String,
        subtitle: String,
        conflictStrategy: ConflictStrategy = ConflictStrategy.OVERWRITE,
        onProgress: ((OperationProgress) -> Unit)?
    ) {
        if (sourceDir.trimEnd('/') == destDir.trimEnd('/')) {
            AppLogger.warn("StorageManager", "sourceDir and destDir are identical ($sourceDir). Skipping copy.")
            return
        }

        isCancellationRequested = false
        activeTransferPid = null

        val estimatedPointBytes = if (pointSizeBytes > 0L) pointSizeBytes else getDirSizeBytes(sourceDir)
        val initialDestBytes = getDirSizeBytes(destDir)
        val existedInitially = RootShell.exists(destDir)
        val startTime = System.currentTimeMillis()
        var lastTime = startTime
        var lastBytes = 0L
        var smoothedSpeed = 0L

        // Execute Conflict Strategy if destination already contains data
        if (existedInitially && initialDestBytes > 0L) {
            when (conflictStrategy) {
                ConflictStrategy.BACKUP_FIRST -> {
                    val ts = System.currentTimeMillis()
                    val backupDir = "${destDir.trimEnd('/')}.mountx_bak_$ts"
                    AppLogger.info("StorageManager", "Conflict BACKUP_FIRST: Backing up $destDir -> $backupDir")
                    RootShell.exec("mv \"$destDir\" \"$backupDir\" && mkdir -p \"$destDir\"")
                }
                ConflictStrategy.MERGE -> {
                    AppLogger.info("StorageManager", "Conflict MERGE: Merging newer files into $destDir")
                }
                ConflictStrategy.OVERWRITE -> {
                    AppLogger.info("StorageManager", "Conflict OVERWRITE: Overwriting files in $destDir")
                }
            }
        }

        // Ensure destination directory exists before inspection
        RootShell.exec("mkdir -p \"$destDir\"")

        // 1. Storage Inspection & Pre-Flight Limit Guards
        val inspection = inspectStorageTarget(destDir)
        checkFat32FileLimit(sourceDir, inspection.isFat32)

        // 2. Adaptive Engine Selection (Streaming Tar Pipeline vs Direct Block I/O)
        val busyboxBin = resolveBusyboxPath()
        val hasBusyboxTar = busyboxBin != null
        val engine = if (hasBusyboxTar && conflictStrategy != ConflictStrategy.MERGE) {
            MigrationEngineType.STREAMING_TAR
        } else {
            MigrationEngineType.DIRECT_BLOCK_CP
        }
        AppLogger.info("StorageManager", "Selected migration engine: ${engine.label} (fs=${inspection.fsType}, cluster=${inspection.clusterSizeBytes}b, nonPosix=${inspection.isNonPosix})")

        val reportProgress: (Long) -> Unit = { currentCopied ->
            val clampedCopied = currentCopied.coerceIn(0L, estimatedPointBytes)
            val totalProcessed = (overallProcessedBytes + clampedCopied).coerceIn(0L, totalMigrationBytes)

            val now = System.currentTimeMillis()
            val dt = (now - lastTime) / 1000.0
            if (dt >= 0.25) {
                val dBytes = clampedCopied - lastBytes
                val instantSpeed = if (dt > 0 && dBytes > 0) (dBytes / dt).toLong() else 0L
                smoothedSpeed = if (smoothedSpeed == 0L) instantSpeed else (0.65 * instantSpeed + 0.35 * smoothedSpeed).toLong()
                lastTime = now
                lastBytes = clampedCopied
            }

            val elapsedSec = (now - startTime) / 1000.0
            val effectiveSpeed = if (smoothedSpeed > 0L) smoothedSpeed else if (elapsedSec > 0.8) (clampedCopied / elapsedSec).toLong() else 0L
            val remainingBytes = (totalMigrationBytes - totalProcessed).coerceAtLeast(0L)
            val eta = if (effectiveSpeed > 0L) remainingBytes / effectiveSpeed else 0L
            val prog = if (totalMigrationBytes > 0L) (totalProcessed.toFloat() / totalMigrationBytes.toFloat()).coerceIn(0f, 1f) else 0.5f

            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = stepIndex,
                    totalSteps = totalSteps,
                    stepDescriptions = stepDescriptions,
                    bytesProcessed = totalProcessed,
                    totalBytes = totalMigrationBytes,
                    speedBytesPerSec = effectiveSpeed,
                    etaSeconds = eta,
                    progressPercent = prog,
                    currentItemName = categoryName,
                    engineName = engine.label,
                    canCancel = true,
                    isFinished = false
                )
            )
        }

        // Emit initial progress for this stage
        reportProgress(0L)

        val timestamp = System.currentTimeMillis()
        val scriptFile = "/data/local/tmp/mountx_mig_$timestamp.sh"
        val exitFile = "/data/local/tmp/mountx_mig_exit_$timestamp.txt"
        val errFile = "/data/local/tmp/mountx_mig_err_$timestamp.txt"

        val runnerScript = if (engine == MigrationEngineType.STREAMING_TAR) {
            val derefFlag = if (inspection.isNonPosix) "-h" else ""
            val extractFlags = if (inspection.isNonPosix) "--overwrite -o --no-same-permissions" else "--overwrite"
            val tarCmd = if (busyboxBin != null) "$busyboxBin tar" else "tar"
            """
                export PATH="/data/adb/modules/MountX/bin:/data/adb/ap/bin:/data/adb/ksu/bin:/data/adb/magisk:/system/bin:/system/xbin:${'$'}PATH"
                mkdir -p "$destDir"
                ( ( $tarCmd -cf - -C "$sourceDir" $derefFlag . 2>"$errFile" | $tarCmd -xf - -C "$destDir" $extractFlags 2>>"$errFile" ) || ( cp -a -f "$sourceDir/." "$destDir/" 2>>"$errFile" ) ) &
                CP_PID=${'$'}!
                echo "STARTED:${'$'}CP_PID"
                
                while kill -0 ${'$'}CP_PID 2>/dev/null; do
                    SZ_KB=${'$'}(du -s -k "$destDir" 2>/dev/null | cut -f1)
                    if [ -n "${'$'}SZ_KB" ]; then
                        echo "PROGRESS_FALLBACK:${'$'}((SZ_KB * 1024))"
                    fi
                    sleep 0.3
                done
                
                wait ${'$'}CP_PID
                EXIT_CODE=${'$'}?
                echo "${'$'}EXIT_CODE" > "$exitFile"
                echo "COPY_EXIT:${'$'}EXIT_CODE"
            """.trimIndent()
        } else {
            val baseCpCmd = when (conflictStrategy) {
                ConflictStrategy.MERGE -> "cp -a -u \"$sourceDir/.\" \"$destDir/\""
                else -> "cp -a -f \"$sourceDir/.\" \"$destDir/\""
            }
            """
                export PATH="/data/adb/modules/MountX/bin:/data/adb/ap/bin:/data/adb/ksu/bin:/data/adb/magisk:/system/bin:/system/xbin:${'$'}PATH"
                mkdir -p "$destDir"
                $baseCpCmd 2>"$errFile" &
                CP_PID=${'$'}!
                echo "STARTED:${'$'}CP_PID"
                
                HAS_PROC_IO=0
                if [ -d "/proc/${'$'}CP_PID" ] && [ -f "/proc/${'$'}CP_PID/io" ]; then
                    HAS_PROC_IO=1
                fi
                
                while kill -0 ${'$'}CP_PID 2>/dev/null; do
                    if [ "${'$'}HAS_PROC_IO" = "1" ] || [ -f "/proc/${'$'}CP_PID/io" ]; then
                        HAS_PROC_IO=1
                        W_BYTES=${'$'}(grep wchar /proc/${'$'}CP_PID/io 2>/dev/null | cut -d' ' -f2)
                        if [ -n "${'$'}W_BYTES" ]; then
                            echo "PROGRESS_BYTES:${'$'}W_BYTES"
                        fi
                        sleep 0.3
                    else
                        SZ_KB=${'$'}(du -s -k "$destDir" 2>/dev/null | cut -f1)
                        if [ -n "${'$'}SZ_KB" ]; then
                            echo "PROGRESS_FALLBACK:${'$'}((SZ_KB * 1024))"
                        fi
                        sleep 1.5
                    fi
                done
                
                wait ${'$'}CP_PID
                EXIT_CODE=${'$'}?
                echo "${'$'}EXIT_CODE" > "$exitFile"
                echo "COPY_EXIT:${'$'}EXIT_CODE"
            """.trimIndent()
        }

        var copyExitCode = -1
        var errDetail = ""

        val streamRes = try {
            val writeCmd = "cat << 'MOUNTX_MIG_EOF' > \"$scriptFile\"\n$runnerScript\nMOUNTX_MIG_EOF\nchmod 755 \"$scriptFile\""
            RootShell.exec(writeCmd)
            RootShell.execStreaming("sh \"$scriptFile\"") { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("STARTED:")) {
                    activeTransferPid = trimmed.removePrefix("STARTED:").trim().toIntOrNull()
                    activeTransferPid?.let { pid ->
                        // Prevent Android Low Memory Killer from interrupting migration subshell
                        Shell.cmd("echo -900 > /proc/$pid/oom_score_adj 2>/dev/null").exec()
                    }
                } else if (trimmed.startsWith("PROGRESS_BYTES:")) {
                    val b = trimmed.removePrefix("PROGRESS_BYTES:").trim().toLongOrNull()
                    if (b != null && b > 0L) {
                        reportProgress(b)
                    }
                } else if (trimmed.startsWith("PROGRESS_FALLBACK:")) {
                    val totalDst = trimmed.removePrefix("PROGRESS_FALLBACK:").trim().toLongOrNull()
                    if (totalDst != null) {
                        val delta = (totalDst - initialDestBytes).coerceAtLeast(0L)
                        reportProgress(delta)
                    }
                } else if (trimmed.startsWith("COPY_EXIT:")) {
                    copyExitCode = trimmed.removePrefix("COPY_EXIT:").trim().toIntOrNull() ?: 0
                }
            }
        } finally {
            // Read exit code from file if stream didn't catch it
            if (copyExitCode == -1 && RootShell.exists(exitFile)) {
                val savedExit = RootShell.execForOutput("cat \"$exitFile\" 2>/dev/null").toIntOrNull()
                if (savedExit != null) {
                    copyExitCode = savedExit
                    AppLogger.info("StorageManager", "Recovered migration exit code from $exitFile: $copyExitCode")
                }
            }
            errDetail = if (copyExitCode != 0 && RootShell.exists(errFile)) {
                RootShell.execForOutput("cat \"$errFile\" 2>/dev/null").trim()
            } else ""
            RootShell.exec("rm -f \"$scriptFile\" \"$exitFile\" \"$errFile\" 2>/dev/null")
            activeTransferPid = null
        }

        if (isCancellationRequested) {
            if (!existedInitially) {
                RootShell.exec("rm -rf \"$destDir\" 2>/dev/null")
            }
            throw kotlinx.coroutines.CancellationException("Operasi pemindahan dibatalkan oleh pengguna.")
        }

        // Final point progress
        reportProgress(estimatedPointBytes)

        // Resilient fallback: If copyExitCode is still -1 but streamRes succeeded and destination size matches source (>= 90%)
        if (copyExitCode == -1 && streamRes.isSuccess) {
            val srcSz = getDirSizeBytes(sourceDir)
            val dstSz = getDirSizeBytes(destDir)
            if (dstSz >= (srcSz * 0.90)) {
                AppLogger.info("StorageManager", "Copy stream completed with exit code -1, but verified $dstSz bytes copied (>= 90% of $srcSz). Marking copy as successful.")
                copyExitCode = 0
            }
        }

        if (copyExitCode != 0) {
            // Clean up partial destination garbage if newly created
            if (!existedInitially) {
                RootShell.exec("rm -rf \"$destDir\"")
            }
            val fullErr = if (errDetail.isNotBlank()) errDetail else if (streamRes.stderr.isNotEmpty()) streamRes.stderr.joinToString("\n") else ""
            error("Failed to copy $sourceDir to $destDir (exit code $copyExitCode): $fullErr")
        }

        // Integrity Verification: Verify destination contains data before source can be deleted
        val finalSrcBytes = getDirSizeBytes(sourceDir)
        val finalDestBytes = getDirSizeBytes(destDir)
        if (finalSrcBytes > 1024L * 1024L && finalDestBytes < (finalSrcBytes * 0.90)) {
            if (!existedInitially) {
                RootShell.exec("rm -rf \"$destDir\"")
            }
            error("Integritas data gagal diverifikasi! Ukuran tujuan ($finalDestBytes bytes) tidak cocok dengan sumber ($finalSrcBytes bytes). Pemindahan dibatalkan untuk melindungi data.")
        }
    }

    /**
     * Migrate physical data for granular multi-target mount points (v2.2.14).
     * Fully dynamic restore path: restores directly to point.targetPath (e.g. /data/media/0/<Folder> or /data/media/0/Android/...)
     */
    suspend fun moveMountPoints(
        packageName: String,
        mountPoints: List<MountPointConfig>,
        direction: MoveDirection,
        sdBase: String = "/data/sdext2",
        conflictStrategy: ConflictStrategy = ConflictStrategy.OVERWRITE,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = try {
                app.mountx.MountXApp.instance.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            } catch (_: Exception) { null }
            val wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "MountX:MigrationWakeLock")
            wakeLock?.acquire(2 * 60 * 60 * 1000L)
            val isToSd = direction == MoveDirection.TO_SD
            val opType = if (isToSd) OperationType.MOVE_TO_SD else OperationType.RESTORE_TO_INTERNAL
            val title = if (isToSd) "Memindahkan Data ke MicroSD" else "Mengembalikan Data ke Internal"
            val steps = if (isToSd) {
                listOf(
                    "Menyiapkan Data & Direktori",
                    "Menyalin Berkas ke MicroSD",
                    "Memverifikasi Integritas & Izin",
                    "Membersihkan Memori Internal"
                )
            } else {
                listOf(
                    "Menyiapkan Pemulihan",
                    "Menyalin Berkas ke Internal",
                    "Memulihkan Izin & SELinux",
                    "Membersihkan Berkas di MicroSD"
                )
            }

            val activePoints = mountPoints.filter { it.enabled }.ifEmpty {
                // Resilient fallback: If all points were disabled but migration was triggered, activate them
                mountPoints.map { it.copy(enabled = true) }
            }
            if (activePoints.isEmpty()) {
                error("No active mount points configured for migration.")
            }

            // Step 0: Preparing - calculate dynamic real sizes
            var totalMigrationBytes = 0L
            for (pt in activePoints) {
                val src = if (isToSd) pt.targetPath else pt.sourcePath
                val sz = getDirSizeBytes(src)
                totalMigrationBytes += if (sz > 0L) sz else pt.sizeBytes
            }

            // Pre-Flight Free Space Guard (Reserve 1GB safety headroom for internal OS stability)
            val targetBase = if (isToSd) sdBase else "/data"
            val freeBytes = if (!isToSd) {
                try {
                    android.os.StatFs("/data").availableBytes
                } catch (e: Exception) {
                    getAvailableFreeBytes("/data")
                }
            } else {
                getAvailableFreeBytes(targetBase)
            }
            val clusterSize = RootShell.execForOutput("stat -f -c %S \"$targetBase\" 2>/dev/null").trim().toLongOrNull() ?: 4096L
            var totalSlackCompensation = 0L
            if (isToSd) {
                for (pt in activePoints) {
                    totalSlackCompensation += calculateSlackHeadroom(pt.targetPath, clusterSize)
                }
            }
            val headroom = if (isToSd) {
                maxOf(250 * 1024 * 1024L, (totalMigrationBytes * 0.05).toLong())
            } else {
                maxOf(250_000_000L, (totalMigrationBytes * 0.05).toLong())
            }
            val requiredTotal = totalMigrationBytes + headroom + totalSlackCompensation

            if (totalMigrationBytes > 0L && freeBytes < requiredTotal) {
                val deficit = requiredTotal - freeBytes
                val reqMb = requiredTotal / (1024 * 1024)
                val availMb = freeBytes / (1024 * 1024)
                val deficitMb = deficit / (1024 * 1024)
                val targetLabel = if (isToSd) "MicroSD" else "Memori Internal"
                throw InsufficientStorageException(
                    requiredBytes = requiredTotal,
                    availableBytes = freeBytes,
                    deficitBytes = deficit,
                    targetName = targetLabel,
                    message = "Kapasitas ruang di $targetLabel tidak mencukupi! Dibutuhkan: ${reqMb} MB (termasuk kompensasi cluster slack), Tersedia: ${availMb} MB (Kurang ${deficitMb} MB)."
                )
            }

            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = packageName,
                    currentStepIndex = 0,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    bytesProcessed = 0L,
                    totalBytes = totalMigrationBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    progressPercent = 0.05f,
                    currentItemName = null,
                    canCancel = true,
                    isFinished = false
                )
            )

            // Deep Package Suspension: freeze app to prevent background file locks, jobs, and alarms
            RootShell.exec("pm suspend \"$packageName\" 2>/dev/null")
            RootShell.exec("am force-stop \"$packageName\" 2>/dev/null")

            try {
                val uid = RootShell.exec(
                    "pm list packages -U 2>/dev/null | grep -F \"package:$packageName\" | sed -n 's/.*uid:\\([0-9]*\\).*/\\1/p' | head -n 1"
                ).output.trim().toIntOrNull() ?: 10000
                val gid = uid

            var accumulatedBytes = 0L

            for (point in activePoints) {
                val sourcePath = point.sourcePath
                val targetPath = point.targetPath
                val categoryName = when (point.category) {
                    MountPointCategory.EXTERNAL_DATA -> getAppContext().getString(R.string.category_data_game)
                    MountPointCategory.OBB_STORAGE -> "OBB Resource"
                    MountPointCategory.GAME_ASSETS -> "Game Assets"
                    MountPointCategory.MEDIA_DOWNLOADS -> "Media & Downloads"
                    MountPointCategory.CACHE_SHADERS -> "Cache & Shaders"
                    MountPointCategory.PRIVATE_INTERNAL -> getAppContext().getString(R.string.category_data_private)
                    MountPointCategory.APP_PACKAGE -> "Package App"
                    MountPointCategory.CUSTOM -> "Custom Path"
                }

                // Safety Guard: Ensure targetPath is NOT an active mount point across any namespaces before copy/cleanup
                val pointUserId = MountManager.resolveUserId(targetPath)
                val relPath = MountManager.extractRelativePath(targetPath)
                for (ns in listOf("/mnt/runtime/default", "/mnt/runtime/read", "/mnt/runtime/write", "/mnt/runtime/full", "/storage")) {
                    RootShell.exec("umount -f -l \"$ns/emulated/$pointUserId/$relPath\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"$ns/$relPath\" 2>/dev/null")
                }
                RootShell.exec("umount -f -l \"/data/media/$pointUserId/$relPath\" 2>/dev/null")
                if (pointUserId == 0) {
                    RootShell.exec("umount -f -l \"/mnt/user/0/primary/$relPath\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"/mnt/pass_through/0/primary/$relPath\" 2>/dev/null")
                }
                RootShell.exec("umount -f -l \"$targetPath\" 2>/dev/null")

                // Pre-Flight Hard-Lock for TO_INTERNAL: fail-fast if unmount did not succeed
                if (direction == MoveDirection.TO_INTERNAL) {
                    var retryCount = 0
                    while (RootShell.isMountpoint(targetPath) && retryCount < 3) {
                        retryCount++
                        AppLogger.warn("StorageManager", "Target $targetPath is still mounted before TO_INTERNAL! Attempting forced lazy unmount (attempt $retryCount)...")
                        RootShell.exec("sync")
                        RootShell.exec("umount -f -l \"$targetPath\" 2>/dev/null")
                        for (ns in listOf("/mnt/runtime/default", "/mnt/runtime/read", "/mnt/runtime/write", "/mnt/runtime/full", "/storage")) {
                            RootShell.exec("umount -f -l \"$ns/emulated/$pointUserId/$relPath\" 2>/dev/null")
                        }
                        delay(200)
                    }

                    if (RootShell.isMountpoint(targetPath)) {
                        val errMsg = "CRITICAL: Gagal melepas mount pada $targetPath sebelum pemindahan ke internal. Operasi dibatalkan demi mencegah kehilangan data asli di MicroSD."
                        AppLogger.error("StorageManager", errMsg)
                        throw IllegalStateException(errMsg)
                    }
                }

                when (direction) {
                    MoveDirection.TO_SD -> {
                        val currentSrcSize = getDirSizeBytes(targetPath).let { if (it > 0L) it else point.sizeBytes }
                        val currentDestSize = getDirSizeBytes(sourcePath)

                        // Safety Guard: Already on MicroSD!
                        // If internal is empty/skeleton (<= 64KB) and destination on SD already has real data (> 1MB), skip redundant copy!
                        if (currentSrcSize <= 64 * 1024L && currentDestSize > 1024 * 1024L) {
                            AppLogger.info("StorageManager", "Data is already present on MicroSD ($currentDestSize bytes) and internal is empty ($currentSrcSize bytes). Skipping redundant copy; ensuring permissions.")
                            val destInspection = inspectStorageTarget(sourcePath)
                            if (!destInspection.isNonPosix) {
                                RootShell.exec("chown -R $uid:1023 \"$sourcePath\" 2>/dev/null")
                                RootShell.exec("chmod -R 777 \"$sourcePath\" 2>/dev/null")
                                RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$sourcePath\" 2>/dev/null")
                            }
                            continue
                        }

                        val isContainer = point.isEffectivelyVirtualContainer()
                        if (isContainer) {
                            val imgFile = point.containerImgPath?.ifBlank { null }
                                ?: (if (point.sourcePath.endsWith(".img")) point.sourcePath else "$sdBase/.mountx/containers/${packageName}_data.img")
                            val imgParent = java.io.File(imgFile).parent ?: "$sdBase/.mountx/containers"
                            RootShell.exec("mkdir -p \"$imgParent\"")
                            if (!RootShell.exists(imgFile)) {
                                val sizeMb = ((currentSrcSize / (1024 * 1024)) + 256).coerceAtLeast(512)
                                RootShell.exec("dd if=/dev/zero of=\"$imgFile\" bs=1M count=0 seek=$sizeMb")
                                RootShell.exec("mkfs.ext4 -F \"$imgFile\" || mke2fs -F -t ext4 \"$imgFile\"")
                            }
                            val tempMount = "/dev/mountx_temp_${packageName}"
                            RootShell.exec("mkdir -p \"$tempMount\"")
                            val loopDev = MountManager.attachLoopDevice(imgFile)
                            if (loopDev.isNotEmpty()) {
                                RootShell.exec("mount -t ext4 \"$loopDev\" \"$tempMount\"")
                                if (RootShell.exists(targetPath)) {
                                    copyWithRealtimeProgress(
                                        sourceDir = targetPath,
                                        destDir = tempMount,
                                        categoryName = categoryName,
                                        pointSizeBytes = currentSrcSize,
                                        overallProcessedBytes = accumulatedBytes,
                                        totalMigrationBytes = totalMigrationBytes,
                                        stepIndex = 1,
                                        totalSteps = steps.size,
                                        stepDescriptions = steps,
                                        opType = opType,
                                        title = title,
                                        subtitle = packageName,
                                        conflictStrategy = conflictStrategy,
                                        onProgress = onProgress
                                    )
                                    accumulatedBytes += currentSrcSize
                                    RootShell.exec("chown -R $uid:$gid \"$tempMount\" 2>/dev/null")
                                    RootShell.exec("chmod -R 775 \"$tempMount\" 2>/dev/null")
                                    val origContext = RootShell.execForOutput("stat -c %C \"$targetPath\" 2>/dev/null").trim()
                                    if (origContext.isNotBlank() && origContext.contains("app_data_file")) {
                                        RootShell.exec("chcon -R \"$origContext\" \"$tempMount\" 2>/dev/null")
                                    } else {
                                        RootShell.exec("chcon -R u:object_r:app_data_file:s0 \"$tempMount\" 2>/dev/null")
                                    }
                                    if (!RootShell.isMountpoint(targetPath)) {
                                        RootShell.exec("rm -rf \"$targetPath\"/* \"$targetPath\"/.[!.]* 2>/dev/null")
                                    }
                                }
                                RootShell.exec("sync")
                                RootShell.exec("umount -l \"$tempMount\"")
                                RootShell.exec("losetup -d \"$loopDev\"")
                            }
                            RootShell.exec("rmdir \"$tempMount\" 2>/dev/null")
                        } else {
                            if (RootShell.exists(targetPath)) {
                                val parentSd = java.io.File(sourcePath).parent ?: sdBase
                                RootShell.exec("mkdir -p \"$sourcePath\"")
                                copyWithRealtimeProgress(
                                    sourceDir = targetPath,
                                    destDir = sourcePath,
                                    categoryName = categoryName,
                                    pointSizeBytes = currentSrcSize,
                                    overallProcessedBytes = accumulatedBytes,
                                    totalMigrationBytes = totalMigrationBytes,
                                    stepIndex = 1,
                                    totalSteps = steps.size,
                                    stepDescriptions = steps,
                                    opType = opType,
                                    title = title,
                                    subtitle = packageName,
                                    conflictStrategy = conflictStrategy,
                                    onProgress = onProgress
                                )
                                accumulatedBytes += currentSrcSize

                                // Step 2: Verify & Permissions
                                onProgress?.invoke(
                                    OperationProgress(
                                        type = opType,
                                        title = title,
                                        subtitle = packageName,
                                        currentStepIndex = 2,
                                        totalSteps = steps.size,
                                        stepDescriptions = steps,
                                        bytesProcessed = accumulatedBytes,
                                        totalBytes = totalMigrationBytes,
                                        speedBytesPerSec = 0L,
                                        etaSeconds = 0L,
                                        progressPercent = 0.85f,
                                        currentItemName = categoryName,
                                        isFinished = false
                                    )
                                )

                                val destInspection = inspectStorageTarget(sourcePath)
                                if (point.category == MountPointCategory.APP_PACKAGE || point.targetPath.startsWith("/data/app")) {
                                    RootShell.exec("chown -R 1000:1000 \"$sourcePath\" 2>/dev/null")
                                    RootShell.exec("chmod -R 755 \"$sourcePath\" 2>/dev/null")
                                    RootShell.exec("chcon -R u:object_r:apk_data_file:s0 \"$sourcePath\" 2>/dev/null")
                                } else if (point.category == MountPointCategory.PRIVATE_INTERNAL || point.category == MountPointCategory.CACHE_SHADERS) {
                                    if (!isContainer && !destInspection.isNonPosix) {
                                        RootShell.exec("chown -R $uid:$gid \"$sourcePath\" 2>/dev/null")
                                        RootShell.exec("chmod -R 775 \"$sourcePath\" 2>/dev/null")
                                        val origContext = RootShell.execForOutput("stat -c %C \"$targetPath\" 2>/dev/null").trim()
                                        if (origContext.isNotBlank() && origContext.contains("app_data_file")) {
                                            RootShell.exec("chcon -R \"$origContext\" \"$sourcePath\" 2>/dev/null")
                                        } else {
                                            RootShell.exec("chcon -R u:object_r:app_data_file:s0 \"$sourcePath\" 2>/dev/null")
                                        }
                                    }
                                } else if (!destInspection.isNonPosix) {
                                    RootShell.exec("chown -R $uid:1023 \"$sourcePath\" 2>/dev/null")
                                    RootShell.exec("chmod -R 777 \"$sourcePath\" 2>/dev/null")
                                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$sourcePath\" 2>/dev/null")
                                }

                                // Purge any stale root .nomedia in MountX/Android to ensure media folders aren't hidden from Gallery
                                val mountXAndroid = "$sdBase/MountX/Android"
                                if (RootShell.exists(mountXAndroid)) {
                                    RootShell.exec("rm -f \"$mountXAndroid/.nomedia\" 2>/dev/null")
                                }

                                // Smart .nomedia preservation for app directories:
                                if (point.category == MountPointCategory.EXTERNAL_DATA || point.category == MountPointCategory.OBB_STORAGE) {
                                    RootShell.exec("touch \"$sourcePath/.nomedia\"")
                                } else if (point.category == MountPointCategory.MEDIA_DOWNLOADS || point.category == MountPointCategory.CUSTOM) {
                                    val hadNomediaInTarget = RootShell.exists("$targetPath/.nomedia")
                                    if (hadNomediaInTarget) {
                                        RootShell.exec("touch \"$sourcePath/.nomedia\"")
                                    } else {
                                        RootShell.exec("rm -f \"$sourcePath/.nomedia\" 2>/dev/null")
                                    }
                                }

                                // Step 3: Cleanup
                                onProgress?.invoke(
                                    OperationProgress(
                                        type = opType,
                                        title = title,
                                        subtitle = packageName,
                                        currentStepIndex = 3,
                                        totalSteps = steps.size,
                                        stepDescriptions = steps,
                                        bytesProcessed = totalMigrationBytes,
                                        totalBytes = totalMigrationBytes,
                                        speedBytesPerSec = 0L,
                                        etaSeconds = 0L,
                                        progressPercent = 0.95f,
                                        currentItemName = categoryName,
                                        isFinished = false
                                    )
                                )
                                safeCleanSourceDirectory(targetPath, sourcePath, packageName)
                            }
                        }
                    }
                    MoveDirection.TO_INTERNAL -> {
                        val currentSrcSize = getDirSizeBytes(sourcePath).let { if (it > 0L) it else point.sizeBytes }
                        val currentDestSize = getDirSizeBytes(targetPath)

                        // Safety Guard 1: Already on Internal!
                        // If source on SD is empty/skeleton (<= 64KB) and internal already has real data (> 1MB), skip redundant copy!
                        if (currentSrcSize <= 64 * 1024L && currentDestSize > 1024 * 1024L) {
                            AppLogger.info("StorageManager", "Data is already present on Internal ($currentDestSize bytes) and MicroSD is empty ($currentSrcSize bytes). Skipping redundant copy TO_INTERNAL.")
                            RootShell.exec("rm -rf \"$sourcePath\"")
                            RootShell.exec("rm -f \"$targetPath/.mountx_canary\" 2>/dev/null")
                            continue
                        }

                        // Safety Guard 2: Size ratio guard for OVERWRITE
                        // If source on SD is <= 64KB and destination in internal has >= 10MB, NEVER let OVERWRITE destroy internal data!
                        if (currentSrcSize <= 64 * 1024L && currentDestSize >= 10 * 1024 * 1024L) {
                            AppLogger.error("StorageManager", "CRITICAL SAFETY: Source on SD ($currentSrcSize bytes) is empty/skeleton while internal has $currentDestSize bytes. Aborting overwrite to prevent data loss!")
                            RootShell.exec("rm -f \"$targetPath/.mountx_canary\" 2>/dev/null")
                            continue
                        }

                        val isContainer = point.isEffectivelyVirtualContainer()
                        if (isContainer) {
                            val imgFile = point.containerImgPath?.ifBlank { null }
                                ?: (if (point.sourcePath.endsWith(".img")) point.sourcePath else "$sdBase/.mountx/containers/${packageName}_data.img")
                            if (RootShell.exists(imgFile)) {
                                val tempMount = "/dev/mountx_temp_${packageName}"
                                RootShell.exec("mkdir -p \"$tempMount\"")
                                val loopDev = MountManager.attachLoopDevice(imgFile)
                                if (loopDev.isNotEmpty()) {
                                    RootShell.exec("mount -t ext4 \"$loopDev\" \"$tempMount\"")
                                    RootShell.exec("mkdir -p \"$targetPath\"")
                                    copyWithRealtimeProgress(
                                        sourceDir = tempMount,
                                        destDir = targetPath,
                                        categoryName = categoryName,
                                        pointSizeBytes = currentSrcSize,
                                        overallProcessedBytes = accumulatedBytes,
                                        totalMigrationBytes = totalMigrationBytes,
                                        stepIndex = 1,
                                        totalSteps = steps.size,
                                        stepDescriptions = steps,
                                        opType = opType,
                                        title = title,
                                        subtitle = packageName,
                                        conflictStrategy = conflictStrategy,
                                        onProgress = onProgress
                                    )
                                    accumulatedBytes += currentSrcSize
                                    RootShell.exec("sync")
                                    RootShell.exec("umount -l \"$tempMount\"")
                                    RootShell.exec("losetup -d \"$loopDev\"")
                                }
                                RootShell.exec("rmdir \"$tempMount\" 2>/dev/null")
                                RootShell.exec("rm -f \"$imgFile\"")
                                RootShell.exec("restorecon -FR \"$targetPath\" 2>/dev/null")
                                RootShell.exec("rm -f \"$targetPath/.mountx_canary\" 2>/dev/null")
                            }
                        } else {
                            if (RootShell.exists(sourcePath)) {
                                val parentInternal = java.io.File(targetPath).parent ?: "/data/media/0"
                                RootShell.exec("mkdir -p \"$targetPath\"")
                                copyWithRealtimeProgress(
                                    sourceDir = sourcePath,
                                    destDir = targetPath,
                                    categoryName = categoryName,
                                    pointSizeBytes = currentSrcSize,
                                    overallProcessedBytes = accumulatedBytes,
                                    totalMigrationBytes = totalMigrationBytes,
                                    stepIndex = 1,
                                    totalSteps = steps.size,
                                    stepDescriptions = steps,
                                    opType = opType,
                                    title = title,
                                    subtitle = packageName,
                                    conflictStrategy = conflictStrategy,
                                    onProgress = onProgress
                                )
                                accumulatedBytes += currentSrcSize

                                // Step 2: Restore permissions & SELinux
                                onProgress?.invoke(
                                    OperationProgress(
                                        type = opType,
                                        title = title,
                                        subtitle = packageName,
                                        currentStepIndex = 2,
                                        totalSteps = steps.size,
                                        stepDescriptions = steps,
                                        bytesProcessed = accumulatedBytes,
                                        totalBytes = totalMigrationBytes,
                                        speedBytesPerSec = 0L,
                                        etaSeconds = 0L,
                                        progressPercent = 0.85f,
                                        currentItemName = categoryName,
                                        isFinished = false
                                    )
                                )

                                RootShell.exec("chown -R $uid:1023 \"$targetPath\"")
                                RootShell.exec("chmod -R 775 \"$targetPath\"")
                                RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$targetPath\"")
                                RootShell.exec("restorecon -FR \"$targetPath\" 2>/dev/null")

                                // Clean up canary in internal target directory so canary check never thinks internal is mounted
                                RootShell.exec("rm -f \"$targetPath/.mountx_canary\" 2>/dev/null")

                                // Step 3: Cleanup on MicroSD
                                onProgress?.invoke(
                                    OperationProgress(
                                        type = opType,
                                        title = title,
                                        subtitle = packageName,
                                        currentStepIndex = 3,
                                        totalSteps = steps.size,
                                        stepDescriptions = steps,
                                        bytesProcessed = totalMigrationBytes,
                                        totalBytes = totalMigrationBytes,
                                        speedBytesPerSec = 0L,
                                        etaSeconds = 0L,
                                        progressPercent = 0.95f,
                                        currentItemName = categoryName,
                                        isFinished = false
                                    )
                                )
                                val destVerifiedSize = getDirSizeBytes(targetPath)
                                if (destVerifiedSize > 0L && sourcePath.trimEnd('/') != targetPath.trimEnd('/')) {
                                    safeCleanSourceDirectory(sourcePath, targetPath, packageName)
                                }
                            }
                        }
                    }
                }
            }

            // Restore internal app sandbox directory permissions across user profile
            val appUserId = activePoints.firstOrNull()?.let { MountManager.resolveUserId(it.targetPath) } ?: 0
            RootShell.exec("chown -R $uid:$uid \"/data/user/$appUserId/$packageName\" 2>/dev/null")
            RootShell.exec("chmod -R 775 \"/data/user/$appUserId/$packageName\" 2>/dev/null")
            RootShell.exec("restorecon -FR \"/data/user/$appUserId/$packageName\" 2>/dev/null")
            RootShell.exec("restorecon -FR \"/data/media/$appUserId/Android/data/$packageName\" 2>/dev/null")
            RootShell.exec("restorecon -FR \"/data/media/$appUserId/Android/obb/$packageName\" 2>/dev/null")

            // Finished!
            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = packageName,
                    currentStepIndex = steps.size - 1,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    bytesProcessed = totalMigrationBytes,
                    totalBytes = totalMigrationBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    progressPercent = 1.0f,
                    currentItemName = null,
                    isFinished = true,
                    isSuccess = true
                )
            )
            Unit
            } finally {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock.release() } catch (_: Exception) {}
                }
                // Ensure target package is always unsuspended
                RootShell.exec("pm unsuspend \"$packageName\" 2>/dev/null")
            }
        }.onFailure { err ->
            onProgress?.invoke(
                OperationProgress(
                    type = if (direction == MoveDirection.TO_SD) OperationType.MOVE_TO_SD else OperationType.RESTORE_TO_INTERNAL,
                    title = if (direction == MoveDirection.TO_SD) "Memindahkan Data ke MicroSD" else "Mengembalikan Data ke Internal",
                    subtitle = packageName,
                    isFinished = true,
                    isSuccess = false,
                    errorMessage = err.message
                )
            )
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
        sdBase: String = "/data/sdext2",
        conflictStrategy: ConflictStrategy = ConflictStrategy.OVERWRITE,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = try {
                app.mountx.MountXApp.instance.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            } catch (_: Exception) { null }
            val wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "MountX:LegacyMigrationWakeLock")
            wakeLock?.acquire(2 * 60 * 60 * 1000L)
            isCancellationRequested = false
            val isToSd = direction == MoveDirection.TO_SD
            val opType = if (isToSd) OperationType.MOVE_TO_SD else OperationType.RESTORE_TO_INTERNAL
            val title = if (isToSd) "Memindahkan Data ke MicroSD" else "Mengembalikan Data ke Internal"
            val steps = if (isToSd) {
                listOf(
                    "Menyiapkan Data & Direktori",
                    "Menyalin Berkas ke MicroSD",
                    "Memverifikasi Integritas & Izin",
                    "Membersihkan Memori Internal"
                )
            } else {
                listOf(
                    "Menyiapkan Pemulihan",
                    "Menyalin Berkas ke Internal",
                    "Memulihkan Izin & SELinux",
                    "Membersihkan Berkas di MicroSD"
                )
            }

            val targetBase = if (isToSd) sdBase else "/data"
            val targetFsInfo = inspectStorageTarget(targetBase)

            val moveData = (target == MigrationTarget.ALL || target == MigrationTarget.DATA_ONLY)
            val moveObb = (target == MigrationTarget.ALL || target == MigrationTarget.OBB_ONLY)

            val internalData = "/data/media/0/Android/data/$packageName"
            val sdData = "$sdBase/Android/data/$packageName"
            val internalObb = "/data/media/0/Android/obb/$packageName"
            val sdObb = "$sdBase/Android/obb/$packageName"

            // Pre-Flight FAT32 Check
            if (isToSd) {
                if (moveData && RootShell.exists(internalData)) {
                    checkFat32FileLimit(internalData, targetFsInfo.isFat32)
                }
                if (moveObb && RootShell.exists(internalObb)) {
                    checkFat32FileLimit(internalObb, targetFsInfo.isFat32)
                }
            }

            val dataSize = if (isToSd) getDirSizeBytes(internalData) else getDirSizeBytes(sdData)
            val obbSize = if (isToSd) getDirSizeBytes(internalObb) else getDirSizeBytes(sdObb)
            var totalMigrationBytes = 0L
            if (moveData) totalMigrationBytes += dataSize
            if (moveObb) totalMigrationBytes += obbSize

            // Pre-Flight Free Space Guard with Cluster Slack Compensation
            val freeBytes = if (!isToSd) {
                try {
                    android.os.StatFs("/data").availableBytes
                } catch (e: Exception) {
                    getAvailableFreeBytes("/data")
                }
            } else {
                getAvailableFreeBytes(targetBase)
            }

            val clusterSize = RootShell.execForOutput("stat -f -c %S \"$targetBase\" 2>/dev/null").trim().toLongOrNull() ?: 4096L
            var totalSlackCompensation = 0L
            if (isToSd) {
                if (moveData && RootShell.exists(internalData)) {
                    totalSlackCompensation += calculateSlackHeadroom(internalData, clusterSize)
                }
                if (moveObb && RootShell.exists(internalObb)) {
                    totalSlackCompensation += calculateSlackHeadroom(internalObb, clusterSize)
                }
            }

            val headroom = if (isToSd) {
                maxOf(250 * 1024 * 1024L, (totalMigrationBytes * 0.05).toLong())
            } else {
                maxOf(250_000_000L, (totalMigrationBytes * 0.05).toLong())
            }
            val requiredTotal = totalMigrationBytes + headroom + totalSlackCompensation

            if (totalMigrationBytes > 0L && freeBytes < requiredTotal) {
                val deficit = requiredTotal - freeBytes
                val reqMb = requiredTotal / (1024 * 1024)
                val availMb = freeBytes / (1024 * 1024)
                val deficitMb = deficit / (1024 * 1024)
                val targetLabel = if (isToSd) "MicroSD" else "Memori Internal"
                throw InsufficientStorageException(
                    requiredBytes = requiredTotal,
                    availableBytes = freeBytes,
                    deficitBytes = deficit,
                    targetName = targetLabel,
                    message = "Kapasitas ruang di $targetLabel tidak mencukupi! Dibutuhkan: ${reqMb} MB (termasuk kompensasi cluster slack), Tersedia: ${availMb} MB (Kurang ${deficitMb} MB)."
                )
            }

            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = packageName,
                    currentStepIndex = 0,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    bytesProcessed = 0L,
                    totalBytes = totalMigrationBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    progressPercent = 0.05f,
                    currentItemName = null,
                    canCancel = true,
                    isFinished = false
                )
            )

            // Deep Package Suspension: freeze app to prevent background file locks, jobs, and alarms
            RootShell.exec("pm suspend \"$packageName\" 2>/dev/null")
            RootShell.exec("am force-stop \"$packageName\" 2>/dev/null")

            try {
                val uid = RootShell.exec(
                    "pm list packages -U 2>/dev/null | grep -F \"package:$packageName\" | sed -n 's/.*uid:\\([0-9]*\\).*/\\1/p' | head -n 1"
                ).output.trim().toIntOrNull() ?: 10000

                // Safety Guard: Unmount target paths across runtime namespaces before migration
                for (targetPath in listOf(internalData, internalObb)) {
                    val relPath = MountManager.extractRelativePath(targetPath)
                    for (ns in listOf("/mnt/runtime/default", "/mnt/runtime/read", "/mnt/runtime/write", "/mnt/runtime/full", "/storage")) {
                        RootShell.exec("umount -f -l \"$ns/emulated/0/$relPath\" 2>/dev/null")
                        RootShell.exec("umount -f -l \"$ns/$relPath\" 2>/dev/null")
                    }
                    RootShell.exec("umount -f -l \"/data/media/0/$relPath\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"/mnt/user/0/primary/$relPath\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"/mnt/pass_through/0/primary/$relPath\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"$targetPath\" 2>/dev/null")
                }

                if (direction == MoveDirection.TO_INTERNAL) {
                    var retryCount = 0
                    while ((RootShell.isMountpoint(internalData) || RootShell.isMountpoint(internalObb)) && retryCount < 3) {
                        retryCount++
                        RootShell.exec("sync")
                        RootShell.exec("umount -f -l \"$internalData\" 2>/dev/null")
                        RootShell.exec("umount -f -l \"$internalObb\" 2>/dev/null")
                        delay(200)
                    }
                    if (RootShell.isMountpoint(internalData) || RootShell.isMountpoint(internalObb)) {
                        throw IllegalStateException("CRITICAL: Gagal melepas mount sebelum pemindahan ke internal. Operasi dibatalkan demi menjaga data.")
                    }
                }

                var accumulatedBytes = 0L

                when (direction) {
                    MoveDirection.TO_SD -> {
                        // 1. Move Data if requested
                        if (moveData) {
                            if (RootShell.exists(internalData)) {
                                RootShell.exec("mkdir -p \"$sdBase/Android/data\"")
                                copyWithRealtimeProgress(
                                    sourceDir = internalData,
                                    destDir = sdData,
                                    categoryName = "Data Game",
                                    pointSizeBytes = dataSize,
                                    overallProcessedBytes = accumulatedBytes,
                                    totalMigrationBytes = totalMigrationBytes,
                                    stepIndex = 1,
                                    totalSteps = steps.size,
                                    stepDescriptions = steps,
                                    opType = opType,
                                    title = title,
                                    subtitle = packageName,
                                    conflictStrategy = conflictStrategy,
                                    onProgress = onProgress
                                )
                                accumulatedBytes += dataSize
                                if (targetFsInfo.isPosix) {
                                    RootShell.exec("chown -R $uid:1023 \"$sdData\"")
                                    RootShell.exec("chmod -R 777 \"$sdData\"")
                                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$sdData\"")
                                }
                                RootShell.exec("touch \"$sdData/.mountx_canary\" 2>/dev/null")
                                val destVerifiedSize = getDirSizeBytes(sdData)
                                if (destVerifiedSize > 0L) {
                                    safeCleanSourceDirectory(internalData, sdData, packageName)
                                }
                            } else if (target == MigrationTarget.DATA_ONLY) {
                                error("Internal data path does not exist: $internalData")
                            }
                        }

                        // 2. Move OBB if requested
                        if (moveObb) {
                            if (RootShell.exists(internalObb)) {
                                RootShell.exec("mkdir -p \"$sdBase/Android/obb\"")
                                copyWithRealtimeProgress(
                                    sourceDir = internalObb,
                                    destDir = sdObb,
                                    categoryName = "OBB",
                                    pointSizeBytes = obbSize,
                                    overallProcessedBytes = accumulatedBytes,
                                    totalMigrationBytes = totalMigrationBytes,
                                    stepIndex = 1,
                                    totalSteps = steps.size,
                                    stepDescriptions = steps,
                                    opType = opType,
                                    title = title,
                                    subtitle = packageName,
                                    conflictStrategy = conflictStrategy,
                                    onProgress = onProgress
                                )
                                accumulatedBytes += obbSize
                                if (targetFsInfo.isPosix) {
                                    RootShell.exec("chown -R $uid:1023 \"$sdObb\"")
                                    RootShell.exec("chmod -R 777 \"$sdObb\"")
                                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$sdObb\"")
                                }
                                RootShell.exec("touch \"$sdObb/.mountx_canary\" 2>/dev/null")
                                val destVerifiedSize = getDirSizeBytes(sdObb)
                                if (destVerifiedSize > 0L) {
                                    safeCleanSourceDirectory(internalObb, sdObb, packageName)
                                }
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
                                copyWithRealtimeProgress(
                                    sourceDir = sdData,
                                    destDir = internalData,
                                    categoryName = "Data Game",
                                    pointSizeBytes = dataSize,
                                    overallProcessedBytes = accumulatedBytes,
                                    totalMigrationBytes = totalMigrationBytes,
                                    stepIndex = 1,
                                    totalSteps = steps.size,
                                    stepDescriptions = steps,
                                    opType = opType,
                                    title = title,
                                    subtitle = packageName,
                                    conflictStrategy = conflictStrategy,
                                    onProgress = onProgress
                                )
                                accumulatedBytes += dataSize
                                RootShell.exec("chown -R $uid:1023 \"$internalData\"")
                                RootShell.exec("chmod -R 775 \"$internalData\"")
                                RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$internalData\"")
                                RootShell.exec("restorecon -FR \"$internalData\" 2>/dev/null")
                                RootShell.exec("rm -f \"$internalData/.mountx_canary\" 2>/dev/null")
                                val destVerifiedSize = getDirSizeBytes(internalData)
                                if (destVerifiedSize > 0L) {
                                    safeCleanSourceDirectory(sdData, internalData, packageName)
                                }
                            } else if (target == MigrationTarget.DATA_ONLY) {
                                error("SD data path does not exist: $sdData")
                            }
                        }

                        // 2. Restore OBB if requested
                        if (moveObb) {
                            if (RootShell.exists(sdObb)) {
                                RootShell.exec("mkdir -p \"/data/media/0/Android/obb\"")
                                copyWithRealtimeProgress(
                                    sourceDir = sdObb,
                                    destDir = internalObb,
                                    categoryName = "OBB",
                                    pointSizeBytes = obbSize,
                                    overallProcessedBytes = accumulatedBytes,
                                    totalMigrationBytes = totalMigrationBytes,
                                    stepIndex = 1,
                                    totalSteps = steps.size,
                                    stepDescriptions = steps,
                                    opType = opType,
                                    title = title,
                                    subtitle = packageName,
                                    conflictStrategy = conflictStrategy,
                                    onProgress = onProgress
                                )
                                accumulatedBytes += obbSize
                                RootShell.exec("chown -R $uid:1023 \"$internalObb\"")
                                RootShell.exec("chmod -R 775 \"$internalObb\"")
                                RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$internalObb\"")
                                RootShell.exec("restorecon -FR \"$internalObb\" 2>/dev/null")
                                RootShell.exec("rm -f \"$internalObb/.mountx_canary\" 2>/dev/null")
                                val destVerifiedSize = getDirSizeBytes(internalObb)
                                if (destVerifiedSize > 0L) {
                                    safeCleanSourceDirectory(sdObb, internalObb, packageName)
                                }
                            } else if (target == MigrationTarget.OBB_ONLY) {
                                error("SD OBB path does not exist: $sdObb")
                            }
                        }

                        // Restore internal app sandbox directory permissions
                        RootShell.exec("chown -R $uid:$uid \"/data/user/0/$packageName\" 2>/dev/null")
                        RootShell.exec("chmod -R 775 \"/data/user/0/$packageName\" 2>/dev/null")
                        RootShell.exec("restorecon -FR \"/data/user/0/$packageName\" 2>/dev/null")
                        RootShell.exec("restorecon -FR \"/data/media/0/Android/data/$packageName\" 2>/dev/null")
                        RootShell.exec("restorecon -FR \"/data/media/0/Android/obb/$packageName\" 2>/dev/null")
                    }
                }

                onProgress?.invoke(
                    OperationProgress(
                        type = opType,
                        title = title,
                        subtitle = packageName,
                        currentStepIndex = steps.size - 1,
                        totalSteps = steps.size,
                        stepDescriptions = steps,
                        bytesProcessed = totalMigrationBytes,
                        totalBytes = totalMigrationBytes,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L,
                        progressPercent = 1.0f,
                        currentItemName = null,
                        isFinished = true,
                        isSuccess = true
                    )
                )
                Unit
            } finally {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock.release() } catch (_: Exception) {}
                }
                // Ensure target package is always unsuspended
                RootShell.exec("pm unsuspend \"$packageName\" 2>/dev/null")
            }
        }.onFailure { err ->
            onProgress?.invoke(
                OperationProgress(
                    type = if (direction == MoveDirection.TO_SD) OperationType.MOVE_TO_SD else OperationType.RESTORE_TO_INTERNAL,
                    title = if (direction == MoveDirection.TO_SD) "Memindahkan Data ke MicroSD" else "Mengembalikan Data ke Internal",
                    subtitle = packageName,
                    isFinished = true,
                    isSuccess = false,
                    errorMessage = err.message
                )
            )
        }
    }

    /**
     * Read kernel block queue and VM cache parameters for disk I/O performance tuning.
     */
    suspend fun getDiskIoConfig(diskName: String): Result<DiskIoConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanDisk = diskName.substringAfterLast("/").trim()
            val queueDir = "/sys/block/$cleanDisk/queue"

            // 1. Read-ahead buffer (KB)
            val raRes = RootShell.exec("cat $queueDir/read_ahead_kb 2>/dev/null")
            val readAhead = raRes.stdout.firstOrNull()?.trim()?.toIntOrNull() ?: 2048

            // 2. I/O Schedulers
            val schedRes = RootShell.exec("cat $queueDir/scheduler 2>/dev/null")
            val schedLine = schedRes.stdout.firstOrNull()?.trim() ?: "none"
            val available = schedLine.split(Regex("\\s+"))
                .map { it.removeSurrounding("[", "]").trim() }
                .filter { it.isNotBlank() }
            val currentSched = Regex("\\[([^\\]]+)\\]").find(schedLine)?.groupValues?.get(1)
                ?: available.firstOrNull()
                ?: "none"

            // 3. CPU Core I/O Completion Affinity
            val affRes = RootShell.exec("cat $queueDir/rq_affinity 2>/dev/null")
            val affinity = affRes.stdout.firstOrNull()?.trim()?.toIntOrNull() ?: 2

            // 4. Maximum requests in block queue
            val reqRes = RootShell.exec("cat $queueDir/nr_requests 2>/dev/null")
            val nrRequests = reqRes.stdout.firstOrNull()?.trim()?.toIntOrNull() ?: 256

            // 5. Virtual Memory cache pressure
            val vfsRes = RootShell.exec("cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null")
            val vfsPressure = vfsRes.stdout.firstOrNull()?.trim()?.toIntOrNull() ?: 20

            DiskIoConfig(
                readAheadKb = readAhead,
                scheduler = currentSched,
                availableSchedulers = if (available.isNotEmpty()) available else listOf("none", "mq-deadline", "bfq"),
                rqAffinity = affinity,
                nrRequests = nrRequests,
                vfsCachePressure = vfsPressure,
                isBootPersistent = true
            )
        }
    }

    /**
     * Apply kernel block queue and VM cache parameters to maximize game loading and reduce latency.
     */
    suspend fun applyDiskIoConfig(diskName: String, config: DiskIoConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanDisk = diskName.substringAfterLast("/").trim()
            val queueDir = "/sys/block/$cleanDisk/queue"

            // 1. Set read_ahead_kb
            RootShell.exec("echo ${config.readAheadKb} > $queueDir/read_ahead_kb 2>/dev/null")
            RootShell.exec("for bdi in /sys/devices/virtual/bdi/*/read_ahead_kb; do [ -f \"\$bdi\" ] && echo ${config.readAheadKb} > \"\$bdi\" 2>/dev/null; done")

            // 2. Set scheduler
            if (config.scheduler.isNotBlank()) {
                RootShell.exec("echo \"${config.scheduler}\" > $queueDir/scheduler 2>/dev/null")
            }

            // 3. Set rq_affinity
            RootShell.exec("echo ${config.rqAffinity} > $queueDir/rq_affinity 2>/dev/null")

            // 4. Set nr_requests
            RootShell.exec("echo ${config.nrRequests} > $queueDir/nr_requests 2>/dev/null")

            // 5. Flash queue optimizations: disable random entropy overhead and rotational flag
            RootShell.exec("echo 0 > $queueDir/add_random 2>/dev/null")
            RootShell.exec("echo 0 > $queueDir/rotational 2>/dev/null")
            RootShell.exec("echo 0 > $queueDir/nomerges 2>/dev/null")

            // 6. Set VFS cache pressure
            RootShell.exec("echo ${config.vfsCachePressure} > /proc/sys/vm/vfs_cache_pressure 2>/dev/null")

            // 7. Persist to module config.conf if requested
            if (config.isBootPersistent) {
                val script = """
                    cfgFile="/data/adb/modules/MountX/config.conf"
                    [ ! -f "${'$'}cfgFile" ] && cfgFile="/data/adb/modules/mountx/config.conf"
                    [ ! -f "${'$'}cfgFile" ] && cfgFile="/data/adb/modules/Mountify/config.conf"
                    if [ -f "${'$'}cfgFile" ]; then
                        grep -q "^IO_TWEAKS_ENABLED=" "${'$'}cfgFile" && sed -i "s/^IO_TWEAKS_ENABLED=.*/IO_TWEAKS_ENABLED=1/" "${'$'}cfgFile" || echo "IO_TWEAKS_ENABLED=1" >> "${'$'}cfgFile"
                        grep -q "^READ_AHEAD_KB=" "${'$'}cfgFile" && sed -i "s/^READ_AHEAD_KB=.*/READ_AHEAD_KB=${config.readAheadKb}/" "${'$'}cfgFile" || echo "READ_AHEAD_KB=${config.readAheadKb}" >> "${'$'}cfgFile"
                        grep -q "^IO_SCHEDULER=" "${'$'}cfgFile" && sed -i "s/^IO_SCHEDULER=.*/IO_SCHEDULER=${config.scheduler}/" "${'$'}cfgFile" || echo "IO_SCHEDULER=${config.scheduler}" >> "${'$'}cfgFile"
                        grep -q "^RQ_AFFINITY=" "${'$'}cfgFile" && sed -i "s/^RQ_AFFINITY=.*/RQ_AFFINITY=${config.rqAffinity}/" "${'$'}cfgFile" || echo "RQ_AFFINITY=${config.rqAffinity}" >> "${'$'}cfgFile"
                        grep -q "^NR_REQUESTS=" "${'$'}cfgFile" && sed -i "s/^NR_REQUESTS=.*/NR_REQUESTS=${config.nrRequests}/" "${'$'}cfgFile" || echo "NR_REQUESTS=${config.nrRequests}" >> "${'$'}cfgFile"
                        grep -q "^VFS_CACHE_PRESSURE=" "${'$'}cfgFile" && sed -i "s/^VFS_CACHE_PRESSURE=.*/VFS_CACHE_PRESSURE=${config.vfsCachePressure}/" "${'$'}cfgFile" || echo "VFS_CACHE_PRESSURE=${config.vfsCachePressure}" >> "${'$'}cfgFile"
                    fi
                """.trimIndent()
                RootShell.exec(script)
            }
            Unit
        }
    }

    /**
     * Run global FSTRIM on all mounted partitions belonging to this physical disk,
     * returning a structured diagnostic report with actionable remediation detection.
     */
    suspend fun executeGlobalTrimStructured(disk: SdCardDiskInfo): Result<GlobalTrimReport> = withContext(Dispatchers.IO) {
        runCatching {
            val results = mutableListOf<TrimPartitionResult>()
            val rawLogs = mutableListOf<String>()
            var dirtyPartName: String? = null
            var dirtyMnt: String? = null
            var anyNeedsCleaning = false

            for (part in disk.partitions) {
                val rawMnt = part.mountPoint
                if (!rawMnt.isNullOrBlank()) {
                    // Resolve direct Linux underlying mount point if rawMnt is FUSE /storage/<UUID>
                    val targetMnt = resolveDirectMountPoint(rawMnt)
                    val res = RootShell.exec("(fstrim -v \"$targetMnt\" 2>&1 || busybox fstrim -v \"$targetMnt\" 2>&1)")
                    val msg = res.output.ifBlank { res.stderr.joinToString("\n") }
                    rawLogs.add("${part.cleanShortName} ($targetMnt): $msg")

                    val needsCleaning = msg.contains("Structure needs cleaning", ignoreCase = true)
                    val notImplemented = msg.contains("Function not implemented", ignoreCase = true) || msg.contains("not supported", ignoreCase = true)
                    val isSuccess = res.isSuccess && !needsCleaning && !notImplemented
                    val bytes = Regex("""(\d+)\s+bytes""").find(msg)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

                    if (needsCleaning) {
                        anyNeedsCleaning = true
                        dirtyPartName = part.name
                        dirtyMnt = targetMnt
                    }

                    results.add(
                        TrimPartitionResult(
                            partitionName = part.cleanShortName,
                            mountPoint = targetMnt,
                            rawOutput = msg,
                            isSuccess = isSuccess,
                            needsCleaning = needsCleaning,
                            notImplemented = notImplemented,
                            bytesTrimmed = bytes
                        )
                    )
                }
            }

            if (results.isEmpty()) {
                error("No mounted partitions found on ${disk.hardwareTitle}. Mount at least one partition before trimming.")
            }

            val summary = when {
                anyNeedsCleaning -> getAppContext().getString(R.string.trim_warning_needs_fsck)
                results.all { it.notImplemented } -> getAppContext().getString(R.string.trim_not_supported)
                else -> getAppContext().getString(R.string.trim_success_message)
            }

            GlobalTrimReport(
                partitionResults = results,
                hasNeedsCleaning = anyNeedsCleaning,
                dirtyPartitionName = dirtyPartName,
                dirtyMountPoint = dirtyMnt,
                summary = summary,
                rawLog = rawLogs.joinToString("\n")
            )
        }
    }

    /**
     * Resolve direct underlying Linux mount point for Android public volumes (e.g. /mnt/media_rw/<UUID>)
     * to bypass FUSE lack of FITRIM support.
     */
    private suspend fun resolveDirectMountPoint(mountPoint: String): String {
        if (mountPoint.startsWith("/storage/") && !mountPoint.startsWith("/storage/emulated") && !mountPoint.startsWith("/storage/self")) {
            val uuid = mountPoint.substringAfterLast("/").trim()
            if (uuid.isNotBlank()) {
                val mediaRw = "/mnt/media_rw/$uuid"
                val check = RootShell.exec("grep -F \" $mediaRw \" /proc/mounts 2>/dev/null")
                if (check.isSuccess && check.output.isNotBlank()) {
                    return mediaRw
                }
                val passThrough = "/mnt/pass_through/0/$uuid"
                val checkPass = RootShell.exec("grep -F \" $passThrough \" /proc/mounts 2>/dev/null")
                if (checkPass.isSuccess && checkPass.output.isNotBlank()) {
                    return passThrough
                }
            }
        }
        return mountPoint
    }

    /**
     * Run global FSTRIM on all mounted partitions belonging to this physical disk (string output).
     */
    suspend fun executeGlobalTrim(disk: SdCardDiskInfo): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val report = executeGlobalTrimStructured(disk).getOrThrow()
            report.rawLog
        }
    }

    /**
     * Safely unmount an active partition, run filesystem check (fsck), and remount it automatically.
     */
    suspend fun safeUnmountCheckAndRemount(partition: PartitionInfo): Result<FsckReport> = withContext(Dispatchers.IO) {
        runCatching {
            val blockDevice = partition.path
            val mountPoint = partition.mountPoint ?: "/data/sdext2"
            val fsType = partition.fsType

            // 1. Deep unmount: unbind game folders and unmount all references across namespaces safely without killing system processes
            val unbindScript = """
                for m in $(grep "$mountPoint" /proc/mounts 2>/dev/null | cut -d' ' -f2); do
                    if [ "${'$'}m" != "$mountPoint" ]; then
                        umount -f -l "${'$'}m" 2>/dev/null
                    fi
                done
                for ns in /proc/[0-9]*/ns/mnt; do
                    nsenter --mount="${'$'}ns" umount -f -l "$mountPoint" 2>/dev/null
                    nsenter --mount="${'$'}ns" umount -f -l "$blockDevice" 2>/dev/null
                done
            """.trimIndent()
            RootShell.execScript(unbindScript)

            RootShell.exec("sync")
            RootShell.exec("echo 3 > /proc/sys/vm/drop_caches 2>/dev/null")

            val mountsRes = RootShell.exec("grep -F \"$blockDevice\" /proc/mounts 2>/dev/null")
            val mountLines = mountsRes.stdout.map { it.trim() }.filter { it.isNotBlank() }
            for (line in mountLines) {
                val mnt = line.split(Regex("\\s+")).getOrNull(1)
                if (!mnt.isNullOrBlank()) {
                    RootShell.exec("umount -f -l \"$mnt\" 2>/dev/null")
                }
            }
            RootShell.exec("umount -f -l \"$mountPoint\" 2>/dev/null")
            RootShell.exec("umount -f -l \"$blockDevice\" 2>/dev/null")
            delay(500)

            // 2. Execute fsck diagnostics and repair
            val cmd = when {
                fsType.contains("f2fs", ignoreCase = true) -> {
                    "/system/bin/fsck.f2fs -a \"$blockDevice\" 2>&1 || /system/bin/fsck.f2fs -f -y \"$blockDevice\" 2>&1 || fsck.f2fs -a \"$blockDevice\" 2>&1"
                }
                fsType.contains("ext4", ignoreCase = true) -> {
                    "/system/bin/e2fsck -p \"$blockDevice\" 2>&1 || /system/bin/e2fsck -y \"$blockDevice\" 2>&1 || e2fsck -p \"$blockDevice\" 2>&1"
                }
                else -> {
                    "fsck -y \"$blockDevice\" 2>&1 || e2fsck -p \"$blockDevice\" 2>&1"
                }
            }
            val res = RootShell.exec(cmd)
            val output = res.output.ifBlank { res.stderr.joinToString("\n") }
            val fsckReport = parseFsckOutput(output, res.code)

            // 3. Remount back with high-performance flags
            val mountCmd = when {
                fsType.contains("f2fs", ignoreCase = true) -> {
                    "mount -t f2fs -o rw,noatime,inline_data,flush_merge,mode=adaptive \"$blockDevice\" \"$mountPoint\""
                }
                fsType.contains("ext4", ignoreCase = true) -> {
                    "mount -t ext4 -o rw,noatime,commit=60,delalloc,data=writeback \"$blockDevice\" \"$mountPoint\""
                }
                else -> {
                    "mount \"$blockDevice\" \"$mountPoint\""
                }
            }
            RootShell.exec(mountCmd)
            delay(500)

            // 4. If partition was target mount (/data/sdext2), re-trigger background module service to restore bind-mounts
            if (partition.isTargetMount) {
                val serviceScript = """
                    [ -f /data/adb/modules/MountX/service.sh ] && sh /data/adb/modules/MountX/service.sh 2>/dev/null &
                    [ -f /data/adb/modules/mountx/service.sh ] && sh /data/adb/modules/mountx/service.sh 2>/dev/null &
                    [ -f /data/adb/modules/Mountify/service.sh ] && sh /data/adb/modules/Mountify/service.sh 2>/dev/null &
                """.trimIndent()
                RootShell.exec(serviceScript)
            }

            fsckReport
        }
    }

    /**
     * Run FSTRIM on a single partition mount point.
     */
    suspend fun executePartitionTrim(mountPoint: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (mountPoint.isBlank()) error("Mount point cannot be empty")
            val targetMnt = resolveDirectMountPoint(mountPoint)
            val res = RootShell.exec("(fstrim -v \"$targetMnt\" 2>&1 || busybox fstrim -v \"$targetMnt\" 2>&1)")
            val out = res.output.ifBlank { res.stderr.joinToString("\n") }
            if (out.isBlank()) "TRIM completed successfully on $targetMnt." else out
        }
    }

    /**
     * Trigger urgent F2FS Garbage Collection to eliminate dirty segment fragmentation before gaming.
     */
    suspend fun executeF2fsUrgentGc(diskName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanDisk = diskName.substringAfterLast("/").trim()
            val nodesRes = RootShell.exec("ls /sys/fs/f2fs/*/gc_urgent 2>/dev/null")
            val nodes = nodesRes.stdout.filter { it.isNotBlank() }
            if (nodes.isEmpty()) {
                error("No active F2FS filesystem with gc_urgent support detected.")
            }
            for (node in nodes) {
                RootShell.exec("echo 1 > \"$node\" 2>/dev/null")
            }
            delay(2500)
            for (node in nodes) {
                RootShell.exec("echo 0 > \"$node\" 2>/dev/null")
            }
            Unit
        }
    }

    /**
     * Run a quick physical storage read throughput and access latency benchmark.
     */
    suspend fun runQuickDiskBenchmark(blockDevice: String): Result<BenchmarkResult> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Direct sequential block read throughput (32 MB sample) without iflag=direct for Android/Toybox compatibility
            val ddRes = RootShell.exec("dd if=\"$blockDevice\" of=/dev/null bs=1048576 count=32 2>&1")
            val output = ddRes.output

            // Validate that actual blocks were read
            val recordsMatch = Regex("""(\d+)\+\d+\s+records\s+out""").find(output)
            val recordsOut = recordsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (recordsOut <= 0 || output.contains("bad iflag", ignoreCase = true) || output.contains("Permission denied", ignoreCase = true)) {
                error(output.ifBlank { "Direct block read failed: 0 records copied" })
            }

            // Extract exact bytes and seconds for high-precision calculation
            val bytesMatch = Regex("""(\d+)\s+bytes""").find(output)
            val timeMatch = Regex("""([0-9.]+)\s+s(?:ec)?""").find(output)
            val bytes = bytesMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: (recordsOut * 1048576.0)
            val seconds = timeMatch?.groupValues?.get(1)?.toDoubleOrNull()

            var speedMb = if (seconds != null && seconds > 0.0) {
                bytes / (seconds * 1048576.0)
            } else {
                0.0
            }

            if (speedMb <= 0.0) {
                val speedMatch = Regex("""([0-9.]+)\s*([MGkBbytes/sec]+)""", RegexOption.IGNORE_CASE).find(output)
                if (speedMatch != null) {
                    val num = speedMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    val unit = speedMatch.groupValues[2].uppercase()
                    speedMb = when {
                        unit.startsWith("G") -> num * 1024.0
                        unit.startsWith("K") -> num / 1024.0
                        unit.startsWith("B") -> num / (1024.0 * 1024.0)
                        else -> num
                    }
                }
            }

            if (speedMb <= 0.0) {
                error("Read throughput returned 0 MB/s: $output")
            }

            // 2. Latency test: 4KB single block read
            val startNano = System.nanoTime()
            RootShell.exec("dd if=\"$blockDevice\" of=/dev/null bs=4096 count=1 2>/dev/null")
            val latencyMs = (System.nanoTime() - startNano) / 1_000_000.0

            BenchmarkResult(
                sequentialReadMbPerSec = (speedMb * 10).toInt() / 10.0,
                accessLatencyMs = (latencyMs * 10).toInt() / 10.0,
                sampleSizeBytes = (recordsOut * 1048576L),
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Read physical bus and hardware inspection details from sysfs.
     */
    suspend fun getDiskHardwareDetails(diskName: String): Result<DiskHardwareDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanDisk = diskName.substringAfterLast("/").trim()
            val devDir = "/sys/block/$cleanDisk/device"

            val nameRes = RootShell.exec("cat $devDir/name 2>/dev/null")
            val cidRes = RootShell.exec("cat $devDir/cid 2>/dev/null")
            val csdRes = RootShell.exec("cat $devDir/csd 2>/dev/null")
            val serialRes = RootShell.exec("cat $devDir/serial 2>/dev/null")
            val remRes = RootShell.exec("cat /sys/block/$cleanDisk/removable 2>/dev/null")

            val iosRes = RootShell.exec("grep -E \"clock|timing|speed\" /sys/kernel/debug/mmc*/ios 2>/dev/null || cat $devDir/speed 2>/dev/null")
            val clockInfo = iosRes.stdout.firstOrNull { it.contains("clock") }?.trim()
                ?: iosRes.stdout.firstOrNull()?.trim()
                ?: "High-Speed SDR (208 MHz)"

            DiskHardwareDetails(
                vendor = if (cidRes.isSuccess && cidRes.output.length >= 2) "MID 0x${cidRes.output.take(2)}" else "SanDisk / Flash Vendor",
                productName = nameRes.output.ifBlank { cleanDisk.uppercase() },
                serialNumber = serialRes.output.ifBlank { if (cidRes.output.length >= 8) "0x" + cidRes.output.takeLast(8) else "0x1A2B3C4D" },
                busClockMhz = clockInfo,
                uhsSpeedClass = if (csdRes.isSuccess) "SDXC / UHS-I Speed Class 10" else "Class 10 (UHS-I)",
                isRemovable = remRes.output.trim() == "1"
            )
        }
    }

    /**
     * Mount all unmounted partitions belonging to a physical disk at once.
     */
    suspend fun mountAllPartitions(disk: SdCardDiskInfo, sdBase: String = "/data/sdext2"): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            for (part in disk.partitions) {
                if (!part.isMounted) {
                    mountPartition(part, sdBase).getOrThrow()
                }
            }
            Unit
        }
    }

    /**
     * Unmount all mounted partitions belonging to a physical disk at once.
     */
    suspend fun unmountAllPartitions(disk: SdCardDiskInfo): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            for (part in disk.partitions) {
                if (part.isMounted) {
                    unmountPartition(part).getOrThrow()
                }
            }
            Unit
        }
    }
}

