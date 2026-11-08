package app.mountx.root

import android.content.Context
import app.mountx.R

import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.OperationProgress
import app.mountx.data.model.OperationType
import app.mountx.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Exception thrown when internal storage has substantive data but MicroSD only has an empty skeleton.
 */
class OcclusionHazardException(
    val packageName: String,
    val internalBytes: Long,
    val sdBytes: Long,
    message: String
) : IllegalStateException(message)

/**
 * Exception thrown when attempting to mount a game that is already active from a different storage disk.
 */
class MultiDiskCollisionException(
    val packageName: String,
    val activeDevice: String,
    val requestedDevice: String,
    message: String
) : IllegalStateException(message)

/**
 * Handles mounting and unmounting game & application data directories using bind mounts
 * and Virtual Ext4 Loop Containers for Universal Smart Directory Classification.
 */
class MountManager(
    private val context: Context? = null
) {

    private fun getAppContext(): Context = context ?: app.mountx.MountXApp.instance

    /** Resolves all active Android user IDs (User 0, Dual Apps 999, Work Profile 10+, etc.) */
    suspend fun getActiveUserIds(): List<Int> = withContext(Dispatchers.IO) {
        val userIds = mutableListOf(0)
        val pmUsers = RootShell.exec("pm list users 2>/dev/null").stdout
        for (line in pmUsers) {
            val match = Regex("UserInfo\\{(\\d+):").find(line)
            val id = match?.groupValues?.get(1)?.toIntOrNull()
            if (id != null && !userIds.contains(id)) {
                userIds.add(id)
            }
        }
        val mediaDirs = RootShell.exec("ls -1d /data/media/* 2>/dev/null").stdout
        for (dir in mediaDirs) {
            val name = dir.trim().substringAfterLast('/')
            val id = name.toIntOrNull()
            if (id != null && !userIds.contains(id)) {
                userIds.add(id)
            }
        }
        userIds.distinct()
    }

    /** Dynamically detects all target runtime namespaces across active users/profiles or an isolated user */
    suspend fun getTargetNamespaces(specificUserId: Int? = null): List<String> = withContext(Dispatchers.IO) {
        val list = mutableListOf<String>()
        val userIds = if (specificUserId != null) listOf(specificUserId) else getActiveUserIds()
        for (uid in userIds) {
            list.add("/mnt/runtime/default/emulated/$uid")
            list.add("/mnt/runtime/read/emulated/$uid")
            list.add("/mnt/runtime/write/emulated/$uid")
            list.add("/mnt/runtime/full/emulated/$uid")
            list.add("/mnt/user/$uid/primary")
            list.add("/mnt/user/$uid/emulated/$uid")
            // Android 11+ storage runtime views
            list.add("/mnt/installer/$uid/emulated/$uid")
            list.add("/mnt/androidwritable/$uid/emulated/$uid")
            list.add("/mnt/pass_through/$uid/emulated/$uid")
            // Only target /storage/emulated/$uid if /storage/emulated is confirmed a real mounted filesystem to prevent tmpfs collisions
            if (RootShell.isMountpoint("/storage/emulated")) {
                list.add("/storage/emulated/$uid")
            }
            // CRITICAL: /data/media/$uid MUST be mounted LAST after user-facing views to prevent sdcardfs EXDEV dead-lock
            list.add("/data/media/$uid")
        }
        list.filter { RootShell.exists(it) }.distinct()
    }

    companion object {
        /**
         * Resolves the Android user ID from an absolute storage path.
         * Defaults to 0 (primary user) if not identifiable.
         */
        fun resolveUserId(path: String): Int {
            val match = Regex("^/(?:storage/emulated|data/media|mnt/user|data/user)/(\\d+)").find(path)
            return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }

        /**
         * Extracts clean relative directory path from an absolute Android path across any user profile.
         * e.g. /data/media/0/Android/data/com.foo -> Android/data/com.foo
         *      /storage/emulated/999/Android/obb/com.bar -> Android/obb/com.bar
         *      /sdcard/Download/1DM -> Download/1DM
         */
        /**
         * Attaches a container image file to an available loop device.
         * Compatible with both BusyBox (where --show is unsupported) and Toybox.
         * Returns the attached loop device path (e.g. /dev/block/loop0) or empty string on failure.
         */
        suspend fun resolveBusybox(): String? {
            val candidates = listOf(
                "/data/adb/modules/MountX/bin/busybox",
                "/data/adb/ap/bin/busybox",
                "/data/adb/ksu/bin/busybox",
                "/data/adb/magisk/busybox"
            )
            for (p in candidates) {
                if (RootShell.exec("[ -x \"$p\" ] && echo YES || echo NO").output.trim() == "YES") {
                    return p
                }
            }
            val whichRes = RootShell.execForOutput("which busybox 2>/dev/null").trim()
            if (whichRes.isNotBlank() && whichRes.contains("busybox")) {
                return whichRes
            }
            return null
        }

        /**
         * Ensures a dynamic sparse EXT4 .img container has sufficient free virtual capacity.
         * If the container has less than 2 GB virtual free space and the underlying storage has >4 GB free physical space,
         * automatically expands the sparse image (+4 GB) and triggers resize2fs.
         */
        suspend fun ensureSparseContainerCapacity(containerPath: String, sdBase: String, isFat32: Boolean = false) {
            if (!RootShell.exists(containerPath)) return
            if (isFat32) return // FAT32 has a hard 4GB file size limit

            val freeSdOut = RootShell.execForOutput("df -k \"$sdBase\" 2>/dev/null | tail -1").trim()
            val freeSdParts = freeSdOut.split(Regex("\\s+"))
            val freeSdKb = if (freeSdParts.size >= 4) freeSdParts[3].toLongOrNull() ?: 0L else 0L
            if (freeSdKb < 4L * 1024L * 1024L) return // Less than 4 GB free physical disk space on MicroSD

            val tuneOut = RootShell.execForOutput("tune2fs -l \"$containerPath\" 2>/dev/null")
            if (tuneOut.isBlank()) return

            var blockSize = 4096L
            var freeBlocks = 0L
            for (line in tuneOut.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Block size:")) {
                    blockSize = trimmed.substringAfter("Block size:").trim().toLongOrNull() ?: 4096L
                } else if (trimmed.startsWith("Free blocks:")) {
                    freeBlocks = trimmed.substringAfter("Free blocks:").trim().toLongOrNull() ?: 0L
                }
            }

            val freeVirtualMb = (freeBlocks * blockSize) / (1024L * 1024L)
            if (freeVirtualMb < 2048L) {
                AppLogger.info("MountManager", "Auto-expanding sparse container: $containerPath (virtual free: ${freeVirtualMb}MB < 2048MB)")
                val expandMb = 4096L
                RootShell.exec("truncate -s +${expandMb}M \"$containerPath\" 2>/dev/null")
                RootShell.exec("e2fsck -fy \"$containerPath\" >/dev/null 2>&1")
                RootShell.exec("resize2fs \"$containerPath\" >/dev/null 2>&1")
                AppLogger.success("MountManager", "Successfully expanded sparse container: $containerPath (+${expandMb}MB)")
            }
        }

        /**
         * Creates a loop device for a container image.
         * Resolves through sysfs backing_file first, then checks losetup -j/-a,
         * and prefers busybox losetup to bypass Toybox's 64-char path limitation.
         */
        suspend fun attachLoopDevice(containerPath: String): String {
            RootShell.exec("chmod 777 \"$containerPath\" 2>/dev/null")
            // 1. Check sysfs backing_file first (Android kernel truncates lo_file_name in losetup -a to 64 chars)
            val sysfsCheck = RootShell.execForOutput(
                "grep -s -l \"$containerPath\" /sys/block/loop*/loop/backing_file 2>/dev/null | head -n 1"
            ).trim()
            if (sysfsCheck.isNotBlank()) {
                val loopName = sysfsCheck.substringBefore("/loop/").substringAfterLast("/")
                if (loopName.startsWith("loop")) {
                    return "/dev/block/$loopName"
                }
            }
            val bb = resolveBusybox()
            val losetupBin = if (bb != null) "$bb losetup" else "losetup"
            // 2. Fallback check losetup -a with container filename prefix (max 28 chars to bypass 64-char limit)
            val imgName = java.io.File(containerPath).name
            val shortPattern = if (imgName.length > 28) imgName.take(28) else imgName
            val existing = RootShell.execForOutput("$losetupBin -a 2>/dev/null | grep \"$shortPattern\" | head -n 1 | cut -d':' -f1").trim()
            if (existing.isNotBlank() && (existing.startsWith("/dev/block/loop") || existing.startsWith("/dev/loop"))) {
                return existing
            }
            // 3. Find free loop or dynamically allocate one against APEX exhaustion
            val freeLoop = RootShell.execForOutput("$losetupBin -f 2>/dev/null || losetup -f 2>/dev/null").lines().firstOrNull()?.trim().orEmpty()
            var targetLoop = freeLoop
            if (targetLoop.isBlank() || (!targetLoop.startsWith("/dev/block/loop") && !targetLoop.startsWith("/dev/loop"))) {
                // Dynamic Loop Allocator: Query loop-control or create new node if APEX exhausted loop0-loop45
                val allocScript = """
                    dev=""
                    if [ -c /dev/loop-control ]; then
                        num=$(busybox losetup -f 2>/dev/null)
                        [ -n "${'$'}num" ] && dev="${'$'}num"
                    fi
                    if [ -z "${'$'}dev" ]; then
                        for i in $(busybox seq 60 120 2>/dev/null || seq 60 120); do
                            node="/dev/block/loop${'$'}i"
                            if [ ! -b "${'$'}node" ]; then
                                mknod -m 660 "${'$'}node" b 7 "${'$'}i" 2>/dev/null
                                chcon u:object_r:loop_device:s0 "${'$'}node" 2>/dev/null
                                dev="${'$'}node"
                                break
                            fi
                        done
                    fi
                    echo "${'$'}dev"
                """.trimIndent()
                targetLoop = RootShell.execForOutput(allocScript).trim()
            }

            if (targetLoop.isNotBlank() && (targetLoop.startsWith("/dev/block/loop") || targetLoop.startsWith("/dev/loop"))) {
                RootShell.exec("$losetupBin --direct-io=on \"$targetLoop\" \"$containerPath\" 2>/dev/null || $losetupBin \"$targetLoop\" \"$containerPath\" 2>/dev/null || losetup \"$targetLoop\" \"$containerPath\" 2>/dev/null")
                val verifySysfs = RootShell.execForOutput(
                    "grep -s -l \"$containerPath\" /sys/block/loop*/loop/backing_file 2>/dev/null | head -n 1"
                ).trim()
                if (verifySysfs.isNotBlank()) {
                    val loopName = verifySysfs.substringBefore("/loop/").substringAfterLast("/")
                    if (loopName.startsWith("loop")) return "/dev/block/$loopName"
                }
                return targetLoop
            }
            return ""
        }

        /**
         * Detaches all loop devices attached to a container image path.
         */
        suspend fun detachLoopDevice(containerPath: String) {
            val bb = resolveBusybox()
            val losetupBin = if (bb != null) "$bb losetup" else "losetup"
            val imgName = java.io.File(containerPath).name
            val sysfsMatches = RootShell.execForOutput(
                "grep -s -l \"$containerPath\" /sys/block/loop*/loop/backing_file 2>/dev/null"
            ).lines().map { it.trim() }.filter { it.isNotBlank() }
            for (match in sysfsMatches) {
                val loopName = match.substringBefore("/loop/").substringAfterLast("/")
                if (loopName.startsWith("loop")) {
                    RootShell.exec("$losetupBin -d \"/dev/block/$loopName\" 2>/dev/null || losetup -d \"/dev/block/$loopName\" 2>/dev/null")
                    AppLogger.info("MountManager", "Detached loop device via sysfs: /dev/block/$loopName")
                }
            }
            val loopDevs = RootShell.execForOutput(
                "$losetupBin -a 2>/dev/null | grep \"$imgName\" | cut -d':' -f1"
            ).lines().map { it.trim() }.filter { it.isNotBlank() }
            for (ld in loopDevs) {
                RootShell.exec("$losetupBin -d \"$ld\" 2>/dev/null || losetup -d \"$ld\" 2>/dev/null")
                AppLogger.info("MountManager", "Detached loop device: $ld")
            }
        }

        fun extractRelativePath(path: String): String {
            return path
                .replace(Regex("^/(?:storage/emulated|data/media|mnt/user)(?:/\\d+)?(?:/primary)?/?"), "")
                .removePrefix("/sdcard/")
                .removePrefix("/")
        }

        /**
         * Broadcasts media scanner trigger to update gallery and system media index immediately.
         */
        suspend fun triggerMediaScan(path: String): Unit = withContext(Dispatchers.IO) {
            RootShell.exec("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d \"file://$path\" 2>/dev/null")
        }

        /**
         * Resolves the current physical block device mounted at a specific path or namespace target.
         * e.g. /data/media/0/Android/data/com.foo -> /dev/block/mmcblk0p3
         */
        suspend fun getActiveMountSourceDevice(targetPath: String): String? = withContext(Dispatchers.IO) {
            val relPath = extractRelativePath(targetPath)
            val targets = listOf(
                targetPath,
                "/storage/emulated/0/$relPath",
                "/data/media/0/$relPath",
                "/mnt/runtime/default/emulated/0/$relPath"
            )
            val grepPattern = targets.joinToString("|") { " $it " }
            val mountRes = RootShell.exec("cat /proc/mounts 2>/dev/null | grep -E '$grepPattern' | head -n 1")
            if (mountRes.isSuccess && mountRes.output.isNotBlank()) {
                val parts = mountRes.output.trim().split(Regex("\\s+"))
                return@withContext parts.getOrNull(0)
            }
            null
        }

        /**
         * Resolves the partition UUID for a given physical block device via blkid.
         */
        suspend fun getBlockDeviceUuid(blockDevice: String): String? = withContext(Dispatchers.IO) {
            val blkidRes = RootShell.exec("blkid \"$blockDevice\" 2>/dev/null")
            if (blkidRes.isSuccess && blkidRes.output.isNotBlank()) {
                val match = Regex("UUID=\"([^\"]+)\"").find(blkidRes.output)
                return@withContext match?.groupValues?.get(1)
            }
            null
        }
    }

    /**
     * Mount an application entry into runtime namespaces.
     * Supports both Multi-Target Array (v2.2.13) and legacy mode fallback.
     */
    suspend fun mountGame(
        game: GameEntry,
        sdBase: String,
        onProgress: ((OperationProgress) -> Unit)? = null,
        autoCreateSource: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = getAppContext()
            val steps = listOf(
                ctx.getString(R.string.mount_step_sandbox_perms),
                ctx.getString(R.string.mount_step_check_microsd),
                ctx.getString(R.string.mount_step_bind_vfs),
                ctx.getString(R.string.mount_step_verify_selinux),
                ctx.getString(R.string.mount_step_mount_complete)
            )
            val opType = OperationType.MOUNT
            val title = ctx.getString(R.string.op_mount_game_title)
            val subtitle = "${game.displayName} • ${game.packageName}"

            // Step 0: Sandbox & Izin
            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 0,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.2f
                )
            )

            // Clear user-unmounted flag since an intentional mount is taking place
            RootShell.exec("rm -f /dev/.mountx_unmounted 2>/dev/null")

            // Pre-Mount Process Guard: unconditionally force-stop app to ensure no file locks or stale namespaces exist
            forceStopApp(game.packageName)

            // Dynamic UID/GID resolution directly from stat /data/user/$appUserId/$pkg
            val appUserId = game.mountPoints.firstOrNull()?.let { resolveUserId(it.targetPath) } ?: 0
            val identity = resolveAppIdentity(game.packageName, appUserId)
            val uid = identity.uid
            val gid = identity.gid

            // Fast non-recursive ownership on internal sandbox directory & databases (Strict 0700 sandbox isolation)
            val sandboxDir = "/data/user/$appUserId/${game.packageName}"
            RootShell.exec("chown $uid:$gid \"$sandboxDir\" 2>/dev/null")
            RootShell.exec("chmod 700 \"$sandboxDir\" 2>/dev/null")
            RootShell.exec("chmod 700 \"$sandboxDir/databases\" 2>/dev/null")

            // Whitelist MountX in system Doze/battery optimization so background transfers & watchdog never get killed
            RootShell.exec("dumpsys deviceidle whitelist +app.mountx 2>/dev/null")

            // Auto-heal storage permissions on Android 11+ so games never crash with EACCES or OBB download error
            RootShell.exec("appops set \"${game.packageName}\" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null")
            RootShell.exec("appops set \"${game.packageName}\" LEGACY_STORAGE allow 2>/dev/null")
            RootShell.exec("pm grant \"${game.packageName}\" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null")
            RootShell.exec("pm grant \"${game.packageName}\" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null")

            // APatch stealth isolation: automatically add app to APatch exclusion list so RASP/anti-tamper engines (Promon, etc.) never detect root hooks
            RootShell.exec("""
                if [ -f /data/adb/ap/package_config ]; then
                    if ! grep -q "^${game.packageName}," /data/adb/ap/package_config 2>/dev/null; then
                        echo "${game.packageName},1,0,$uid,0," >> /data/adb/ap/package_config
                    fi
                fi
            """.trimIndent())

            // Step 1: Memeriksa Direktori MicroSD
            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 1,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.4f
                )
            )

            // Guard against accidental data occlusion:
            // ── MULTI-DISK COLLISION & ANTI-STACKING GUARD ──
            // Sort by targetPath length ascending so parent mounts (/data/app/pkg, /data/user/0/pkg)
            // are ALWAYS established before child mounts (/data/app/pkg/lib/arm64, /data/user/0/pkg/cache).
            val activeMountPoints = game.mountPoints
                .filter { it.enabled }
                .sortedWith(
                    compareBy<MountPointConfig> { it.targetPath.trimEnd('/').length }
                        .thenByDescending { !it.id.startsWith("legacy_") || it.diskUuid != null }
                        .thenByDescending { it.category == MountPointCategory.CUSTOM }
                        .thenByDescending { it.sizeBytes }
                )
                .distinctBy { it.targetPath.trimEnd('/') }

            // Do not bind-mount an empty/missing MicroSD directory over a populated internal directory.
            var hasOcclusionRisk = false
            var occlusionInternalBytes = 0L
            var occlusionSdBytes = 0L
            for (mp in activeMountPoints) {
                if (mp.category == MountPointCategory.EXTERNAL_DATA || mp.category == MountPointCategory.OBB_STORAGE || mp.category == MountPointCategory.GAME_ASSETS || mp.category == MountPointCategory.CUSTOM) {
                    val srcExists = RootShell.exists(mp.sourcePath)
                    val targetExists = RootShell.exists(mp.targetPath)
                    val targetMounted = RootShell.isMountpoint(mp.targetPath)

                    // Fast Path: Check unmigrated internal target size first.
                    // If target doesn't exist, is already mounted, or is merely a skeleton (<= 512KB),
                    // occlusion hazard is impossible, avoiding heavy du scans across tens of thousands of files on MicroSD.
                    val targetSizeKb = if (targetExists && !targetMounted) {
                        val duRes = RootShell.exec("du -sk \"${mp.targetPath}\" 2>/dev/null")
                        duRes.output.trim().split(Regex("\\s+")).getOrNull(0)?.toLongOrNull() ?: 0L
                    } else 0L

                    if (targetSizeKb <= 512L) {
                        if (!srcExists && !autoCreateSource) {
                            hasOcclusionRisk = true
                            break
                        }
                        continue
                    }

                    // Only when internal target contains substantive unmigrated data (> 512KB), inspect source on MicroSD:
                    val srcSizeKb = if (srcExists) {
                        val duRes = RootShell.exec("du -sk \"${mp.sourcePath}\" 2>/dev/null")
                        duRes.output.trim().split(Regex("\\s+")).getOrNull(0)?.toLongOrNull() ?: 0L
                    } else 0L

                    val isSourceMissingOrEmpty = !srcExists || srcSizeKb <= 128L
                    val isTargetSubstantiallyLarger = targetSizeKb > 512L && (targetSizeKb > srcSizeKb * 2L)
                    if (isSourceMissingOrEmpty || isTargetSubstantiallyLarger) {
                        hasOcclusionRisk = true
                        occlusionInternalBytes = targetSizeKb * 1024L
                        occlusionSdBytes = srcSizeKb * 1024L
                        AppLogger.warn("MountManager", "Occlusion hazard for ${mp.id}: source has only ${srcSizeKb}KB (exists=$srcExists) while internal target has ${targetSizeKb}KB")
                        break
                    }
                }
            }

            if (hasOcclusionRisk) {
                throw OcclusionHazardException(
                    packageName = game.packageName,
                    internalBytes = occlusionInternalBytes,
                    sdBytes = occlusionSdBytes,
                    message = getAppContext().getString(R.string.mount_occlusion_hazard_msg)
                )
            }

            for (mp in activeMountPoints) {
                val activeDev = getActiveMountSourceDevice(mp.targetPath)
                if (activeDev != null) {
                    val dfOut = RootShell.execForOutput("df \"${mp.sourcePath}\" 2>/dev/null").trim()
                    val requestedDev = dfOut.lines().getOrNull(1)?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""
                    val activeUuid = getBlockDeviceUuid(activeDev)
                    val requestedUuid = if (requestedDev.isNotBlank()) getBlockDeviceUuid(requestedDev) else null

                    if (requestedDev.isNotBlank() && activeDev != requestedDev && activeUuid != null && requestedUuid != null && activeUuid != requestedUuid) {
                        AppLogger.warn("MountManager", "SWITCHING DISK MOUNT: ${game.packageName} target ${mp.targetPath} is mounted from $activeDev ($activeUuid), switching to $requestedDev ($requestedUuid). Unmounting previous device mount cleanly...")
                        if (mp.isExternalStorageCategory()) {
                            val relPath = extractRelativePath(mp.targetPath)
                            if (relPath.isNotBlank()) {
                                val targetUserId = resolveUserId(mp.targetPath)
                                val userNamespaces = getTargetNamespaces(targetUserId)
                                for (ns in userNamespaces) {
                                    RootShell.exec("umount -f -l \"$ns/$relPath\" 2>/dev/null")
                                }
                            }
                        }
                        RootShell.exec("umount -f -l \"${mp.targetPath}\" 2>/dev/null")
                    }
                }
            }

            val namespaces = getTargetNamespaces(0)

            // Step 2: Mengaitkan VFS ke Runtime Namespaces
            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 2,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.65f
                )
            )

            var totalMountedTargets = 0
            if (activeMountPoints.isNotEmpty()) {
                // ── UNIVERSAL SMART MULTI-TARGET ARRAY ──
                for (mp in activeMountPoints) {
                    if (!mp.enabled) continue

                    // Security Hard-Lock: reject /data/app unless explicitly marked as APP_PACKAGE
                    if ((mp.targetPath.startsWith("/data/app") || mp.sourcePath.startsWith("/data/app")) && mp.category != MountPointCategory.APP_PACKAGE) {
                        AppLogger.error("MountManager", "Security violation: blocked unverified mount target in /data/app (${mp.id})")
                        continue
                    }

                    val targetUserId = resolveUserId(mp.targetPath)
                    val mpNamespaces = getTargetNamespaces(targetUserId)

                    val mounted = when (mp.category) {
                        MountPointCategory.EXTERNAL_DATA, MountPointCategory.OBB_STORAGE, MountPointCategory.GAME_ASSETS -> {
                            mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = false, autoCreateSource = autoCreateSource)
                        }
                        MountPointCategory.MEDIA_DOWNLOADS -> {
                            RootShell.exec("mkdir -p \"${mp.sourcePath}\" 2>/dev/null")
                            // Localized .nomedia: only place .nomedia in media folder if original target already had .nomedia
                            val hadNomedia = RootShell.exists("${mp.targetPath}/.nomedia")
                            if (hadNomedia) {
                                RootShell.exec("touch \"${mp.sourcePath}/.nomedia\" 2>/dev/null")
                            } else {
                                RootShell.exec("rm -f \"${mp.sourcePath}/.nomedia\" 2>/dev/null")
                            }
                            val res = mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = true, autoCreateSource = autoCreateSource)
                            if (res) triggerMediaScan(mp.targetPath)
                            res
                        }
                        MountPointCategory.CACHE_SHADERS -> {
                            if (mp.targetPath.startsWith("/data/user/") || mp.targetPath.startsWith("/data/data/")) {
                                val parentContext = RootShell.execForOutput("stat -c %C \"/data/user/0/${game.packageName}\" 2>/dev/null").trim()
                                val cacheContext = if (parentContext.isNotBlank() && parentContext.contains("app_data_file")) {
                                    parentContext
                                } else {
                                    "u:object_r:app_data_file:s0"
                                }

                                val candImg = mp.containerImgPath?.ifBlank { null }
                                    ?: mp.resolveContainerImgPath(sdBase, game.packageName)
                                val candNative = mp.resolveNativeFolderPath(sdBase, game.packageName)
                                val isContainer = if (RootShell.exists(candImg) && !RootShell.exists(candNative)) {
                                    true
                                } else if (RootShell.exists(candNative)) {
                                    false
                                } else {
                                    mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)
                                }

                                if (isContainer) {
                                    ensureSparseContainerCapacity(candImg, sdBase, isFat32 = false)
                                    val loopDev = attachLoopDevice(candImg)
                                    if (loopDev.isNotEmpty()) {
                                        RootShell.exec("mkdir -p \"${mp.targetPath}\" 2>/dev/null")
                                        val res = RootShell.exec("mount -t ext4 -o rw,nosuid,nodev,noatime,discard \"$loopDev\" \"${mp.targetPath}\" 2>/dev/null || mount -t ext4 \"$loopDev\" \"${mp.targetPath}\" 2>/dev/null")
                                        if (res.isSuccess || RootShell.isMountpoint(mp.targetPath)) {
                                            RootShell.exec("chown -R $uid:$gid \"${mp.targetPath}\" 2>/dev/null")
                                            RootShell.exec("chmod 700 \"${mp.targetPath}\" 2>/dev/null && chmod -R u+rwX,go-rwx \"${mp.targetPath}\" 2>/dev/null")
                                            RootShell.exec("chcon -R \"$cacheContext\" \"${mp.targetPath}\" 2>/dev/null")
                                            val altTarget = if (mp.targetPath.startsWith("/data/user/0/")) {
                                                mp.targetPath.replace("/data/user/0/", "/data/data/")
                                            } else if (mp.targetPath.startsWith("/data/data/")) {
                                                mp.targetPath.replace("/data/data/", "/data/user/0/")
                                            } else null
                                            if (altTarget != null && RootShell.exists(altTarget) && !RootShell.isMountpoint(altTarget)) {
                                                RootShell.exec("mount -o bind \"${mp.targetPath}\" \"$altTarget\" 2>/dev/null")
                                                RootShell.exec("chcon -R \"$cacheContext\" \"$altTarget\" 2>/dev/null")
                                            }
                                            val ceCacheTarget = "/data_mirror/data_ce/null/0/${game.packageName}/cache"
                                            if (RootShell.exists(ceCacheTarget) && !RootShell.isMountpoint(ceCacheTarget)) {
                                                RootShell.exec("mount -o bind \"${mp.targetPath}\" \"$ceCacheTarget\" 2>/dev/null")
                                                RootShell.exec("chcon -R \"$cacheContext\" \"$ceCacheTarget\" 2>/dev/null")
                                            }
                                            true
                                        } else {
                                            detachLoopDevice(candImg)
                                            false
                                        }
                                    } else false
                                } else {
                                    val finalSrc = if (RootShell.exists(candNative)) candNative else mp.sourcePath
                                    RootShell.exec("mkdir -p \"$finalSrc\" 2>/dev/null")
                                    RootShell.exec("mkdir -p \"${mp.targetPath}\" 2>/dev/null")
                                    val fsType = RootShell.execForOutput("stat -f -c %T \"$finalSrc\" 2>/dev/null").trim().lowercase()
                                    val isNonPosix = fsType in listOf("exfat", "vfat", "msdos", "ntfs") || finalSrc.startsWith("/mnt/media_rw")
                                    if (!isNonPosix) {
                                        RootShell.exec("chown -R $uid:$gid \"$finalSrc\" 2>/dev/null")
                                        RootShell.exec("chmod 700 \"$finalSrc\" 2>/dev/null && chmod -R u+rwX,go-rwx \"$finalSrc\" 2>/dev/null")
                                        RootShell.exec("chcon -R \"$cacheContext\" \"$finalSrc\" 2>/dev/null")
                                    }
                                    val res = RootShell.exec("mount -o bind \"$finalSrc\" \"${mp.targetPath}\"")
                                    if (res.isSuccess || RootShell.isMountpoint(mp.targetPath)) {
                                        RootShell.exec("chown -R $uid:$gid \"${mp.targetPath}\" 2>/dev/null")
                                        RootShell.exec("chmod 700 \"${mp.targetPath}\" 2>/dev/null && chmod -R u+rwX,go-rwx \"${mp.targetPath}\" 2>/dev/null")
                                        RootShell.exec("chcon -R \"$cacheContext\" \"${mp.targetPath}\" 2>/dev/null")
                                        val altTarget = if (mp.targetPath.startsWith("/data/user/0/")) {
                                            mp.targetPath.replace("/data/user/0/", "/data/data/")
                                        } else if (mp.targetPath.startsWith("/data/data/")) {
                                            mp.targetPath.replace("/data/data/", "/data/user/0/")
                                        } else null
                                        if (altTarget != null && RootShell.exists(altTarget) && !RootShell.isMountpoint(altTarget)) {
                                            RootShell.exec("mount -o bind \"$finalSrc\" \"$altTarget\" 2>/dev/null")
                                            RootShell.exec("chcon -R \"$cacheContext\" \"$altTarget\" 2>/dev/null")
                                        }
                                        val ceCacheTarget = "/data_mirror/data_ce/null/0/${game.packageName}/cache"
                                        if (RootShell.exists(ceCacheTarget) && !RootShell.isMountpoint(ceCacheTarget)) {
                                            RootShell.exec("mount -o bind \"$finalSrc\" \"$ceCacheTarget\" 2>/dev/null")
                                            RootShell.exec("chcon -R \"$cacheContext\" \"$ceCacheTarget\" 2>/dev/null")
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                            } else {
                                mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = false, autoCreateSource = autoCreateSource)
                            }
                        }
                        MountPointCategory.CUSTOM -> {
                            val res = mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = false, autoCreateSource = autoCreateSource)
                            if (res) triggerMediaScan(mp.targetPath)
                            res
                        }
                        MountPointCategory.PRIVATE_INTERNAL -> {
                            val hasSeparateCache = activeMountPoints.any {
                                it.category == MountPointCategory.CACHE_SHADERS || it.id.startsWith("cache_")
                            }
                            mountPrivateInternalData(game.packageName, mp, sdBase, uid, gid, hasSeparateCache)
                        }
                        MountPointCategory.APP_PACKAGE -> {
                            // Dynamic verification: resolve active APK directory via pm path in case update relocated the hash directory
                            val currentApkPathRes = RootShell.exec("pm path \"${game.packageName}\" 2>/dev/null | head -n 1")
                            val currentApkDir = if (currentApkPathRes.isSuccess && currentApkPathRes.output.contains("package:")) {
                                val fullPath = currentApkPathRes.output.substringAfter("package:").trim()
                                java.io.File(fullPath).parent ?: ""
                            } else ""
                            val isLib = mp.isLibTarget()

                            val archSubdir = when {
                                mp.targetPath.endsWith("/lib/arm64") || mp.targetPath.endsWith("/arm64") -> "lib/arm64"
                                mp.targetPath.endsWith("/lib/arm") || mp.targetPath.endsWith("/arm") -> "lib/arm"
                                mp.targetPath.endsWith("/lib/armeabi-v7a") -> "lib/armeabi-v7a"
                                mp.targetPath.endsWith("/lib/x86_64") -> "lib/x86_64"
                                else -> "lib/arm64"
                            }

                            val candImg = mp.containerImgPath?.ifBlank { null }
                                ?: mp.resolveContainerImgPath(sdBase, game.packageName)
                            val candNative = mp.resolveNativeFolderPath(sdBase, game.packageName)
                            val isContainer = if (RootShell.exists(candImg) && !RootShell.exists(candNative)) {
                                true
                            } else if (RootShell.exists(candNative)) {
                                false
                            } else {
                                mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)
                            }

                            val (finalSource, finalTarget) = if (isLib) {
                                val hasArm64Source = RootShell.exists("${candNative}/arm64") || RootShell.exists("${mp.sourcePath}/arm64")
                                val baseSrc = if (RootShell.exists(candNative)) candNative else mp.sourcePath
                                val src = if (hasArm64Source) "$baseSrc/arm64" else baseSrc
                                val tgt = if (currentApkDir.isNotBlank()) "$currentApkDir/$archSubdir" else mp.targetPath
                                src to tgt
                            } else {
                                val baseSrc = if (RootShell.exists(candNative)) candNative else mp.sourcePath
                                val tgt = if (currentApkDir.isNotBlank()) currentApkDir else mp.targetPath
                                baseSrc to tgt
                            }

                            val hasSeparateLibMount = activeMountPoints.any {
                                it.isLibTarget()
                            }
                            val libHoldDir = "/dev/mountx_lib_hold_${game.packageName}"
                            var heldInternalLib = false
                            if (!isLib && !hasSeparateLibMount && !RootShell.isMountpoint(finalTarget)) {
                                val internalLibHasFiles = RootShell.execForOutput("find \"$finalTarget/lib\" -type f 2>/dev/null | head -n 1").trim().isNotBlank()
                                val sdLibHasFiles = if (isContainer) false else RootShell.execForOutput("find \"$finalSource/lib\" -type f 2>/dev/null | head -n 1").trim().isNotBlank()
                                if (internalLibHasFiles && !sdLibHasFiles) {
                                    RootShell.exec("mkdir -p \"$libHoldDir\" 2>/dev/null && mount -o bind \"$finalTarget/lib\" \"$libHoldDir\" 2>/dev/null")
                                    heldInternalLib = RootShell.isMountpoint(libHoldDir)
                                }
                            }

                            val res = if (isContainer) {
                                ensureSparseContainerCapacity(candImg, sdBase, isFat32 = false)
                                val loopDev = attachLoopDevice(candImg)
                                if (loopDev.isNotEmpty()) {
                                    RootShell.exec("mkdir -p \"$finalTarget\" 2>/dev/null")
                                    val mRes = RootShell.exec("mount -t ext4 -o rw,noatime,discard \"$loopDev\" \"$finalTarget\" 2>/dev/null || mount -t ext4 \"$loopDev\" \"$finalTarget\" 2>/dev/null")
                                    if (mRes.isSuccess || RootShell.isMountpoint(finalTarget)) {
                                        RootShell.exec("mount -o remount,bind,exec \"$finalTarget\" 2>/dev/null || mount -o remount,exec \"$finalTarget\" 2>/dev/null")
                                        true
                                    } else {
                                        detachLoopDevice(candImg)
                                        false
                                    }
                                } else false
                            } else {
                                RootShell.exec("mkdir -p \"$finalSource\" 2>/dev/null")
                                RootShell.exec("mkdir -p \"$finalTarget\" 2>/dev/null")
                                val mRes = RootShell.exec("mount -o bind \"$finalSource\" \"$finalTarget\"")
                                mRes.isSuccess || RootShell.isMountpoint(finalTarget)
                            }

                            if (res || RootShell.isMountpoint(finalTarget)) {
                                RootShell.exec("mount -o remount,bind,exec \"$finalTarget\" 2>/dev/null || mount -o remount,exec \"$finalTarget\" 2>/dev/null")
                                RootShell.exec("mount --make-rprivate \"$finalTarget\" 2>/dev/null")
                                RootShell.exec("chown -R 1000:1000 \"$finalTarget\" 2>/dev/null")
                                RootShell.exec("chmod -R 755 \"$finalTarget\" 2>/dev/null")
                                RootShell.exec("chcon -R u:object_r:apk_data_file:s0 \"$finalTarget\" 2>/dev/null")
                                if (heldInternalLib) {
                                    RootShell.exec("mkdir -p \"$finalTarget/lib\" 2>/dev/null && mount -o bind \"$libHoldDir\" \"$finalTarget/lib\" 2>/dev/null")
                                    RootShell.exec("umount -l \"$libHoldDir\" 2>/dev/null && rmdir \"$libHoldDir\" 2>/dev/null")
                                }
                                true
                            } else {
                                if (heldInternalLib) {
                                    RootShell.exec("umount -l \"$libHoldDir\" 2>/dev/null && rmdir \"$libHoldDir\" 2>/dev/null")
                                }
                                false
                            }
                        }
                    }
                    if (mounted) {
                        totalMountedTargets++
                    }
                }
                if (totalMountedTargets == 0) {
                    throw IllegalStateException(getAppContext().getString(R.string.error_no_sd_directory_found))
                }
            } else {
                // ── LEGACY FALLBACK (Mode PKG or FILES) ──
                val dataSrcPath = when (game.mode) {
                    MountMode.FILES -> "$sdBase/Android/data/${game.packageName}/files"
                    MountMode.PKG -> "$sdBase/Android/data/${game.packageName}"
                }
                val dataRelPath = when (game.mode) {
                    MountMode.FILES -> "Android/data/${game.packageName}/files"
                    MountMode.PKG -> "Android/data/${game.packageName}"
                }
                val obbSrcPath = "$sdBase/Android/obb/${game.packageName}"
                val obbRelPath = "Android/obb/${game.packageName}"

                val hasData = RootShell.exists(dataSrcPath)
                val hasObb = RootShell.exists(obbSrcPath)

                if (!hasData && !hasObb) {
                    error(getAppContext().getString(R.string.error_game_data_not_found, game.packageName))
                }

                if (game.mode == MountMode.FILES) {
                    RootShell.exec("chmod 771 \"/data/user/0/${game.packageName}/databases\" 2>/dev/null")
                }

                if (hasData) {
                    val fsType = RootShell.execForOutput("stat -f -c %T \"$dataSrcPath\" 2>/dev/null").trim().lowercase()
                    val isNonPosix = fsType in listOf("exfat", "vfat", "msdos", "ntfs") || dataSrcPath.startsWith("/mnt/media_rw")
                    if (!isNonPosix) {
                        RootShell.exec("chown $uid:1023 \"$dataSrcPath\" 2>/dev/null")
                        RootShell.exec("chmod 777 \"$dataSrcPath\" 2>/dev/null")
                        RootShell.exec("chcon u:object_r:media_rw_data_file:s0 \"$dataSrcPath\" 2>/dev/null")
                    }
                    RootShell.exec("touch \"$dataSrcPath/.mountx_canary\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"/data/media/$appUserId/$dataRelPath\" 2>/dev/null")

                    for (namespace in namespaces) {
                        val targetPath = "$namespace/$dataRelPath"
                        if (RootShell.exists("$namespace/Android/data")) {
                            if (!RootShell.isMountpoint(targetPath)) {
                                RootShell.exec("mkdir -p \"$targetPath\" 2>/dev/null")
                                val mountRes = RootShell.exec("mount -o bind \"$dataSrcPath\" \"$targetPath\" 2>/dev/null")
                                if (!mountRes.isSuccess && !RootShell.isMountpoint(targetPath)) {
                                    RootShell.exec("restorecon -R \"$dataSrcPath\" 2>/dev/null")
                                    RootShell.exec("restorecon -R \"$targetPath\" 2>/dev/null")
                                    RootShell.exec("chmod 775 \"$targetPath\" 2>/dev/null")
                                    RootShell.exec("chown $uid:$gid \"$targetPath\" 2>/dev/null")
                                    if (!isNonPosix) {
                                        RootShell.exec("chcon u:object_r:media_rw_data_file:s0 \"$dataSrcPath\" 2>/dev/null")
                                    }
                                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$targetPath\" 2>/dev/null")
                                    RootShell.exec("mount -o bind \"$dataSrcPath\" \"$targetPath\" 2>/dev/null")
                                }
                                RootShell.exec("mount --make-rprivate \"$targetPath\" 2>/dev/null")
                            }
                        }
                    }
                }

                if (hasObb) {
                    val fsType = RootShell.execForOutput("stat -f -c %T \"$obbSrcPath\" 2>/dev/null").trim().lowercase()
                    val isNonPosix = fsType in listOf("exfat", "vfat", "msdos", "ntfs") || obbSrcPath.startsWith("/mnt/media_rw")
                    if (!isNonPosix) {
                        RootShell.exec("chown $uid:1023 \"$obbSrcPath\" 2>/dev/null")
                        RootShell.exec("chmod 777 \"$obbSrcPath\" 2>/dev/null")
                        RootShell.exec("chcon u:object_r:media_rw_data_file:s0 \"$obbSrcPath\" 2>/dev/null")
                    }
                    RootShell.exec("touch \"$obbSrcPath/.mountx_canary\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"/data/media/$appUserId/$obbRelPath\" 2>/dev/null")

                    for (namespace in namespaces) {
                        val targetPath = "$namespace/$obbRelPath"
                        if (RootShell.exists("$namespace/Android")) {
                            if (!RootShell.isMountpoint(targetPath)) {
                                RootShell.exec("mkdir -p \"$namespace/Android/obb\" \"$targetPath\" 2>/dev/null")
                                val mountRes = RootShell.exec("mount -o bind \"$obbSrcPath\" \"$targetPath\" 2>/dev/null")
                                if (!mountRes.isSuccess && !RootShell.isMountpoint(targetPath)) {
                                    RootShell.exec("restorecon -R \"$obbSrcPath\" 2>/dev/null")
                                    RootShell.exec("restorecon -R \"$targetPath\" 2>/dev/null")
                                    RootShell.exec("chmod 775 \"$targetPath\" 2>/dev/null")
                                    RootShell.exec("chown $uid:$gid \"$targetPath\" 2>/dev/null")
                                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$obbSrcPath\" 2>/dev/null")
                                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$targetPath\" 2>/dev/null")
                                    RootShell.exec("mount -o bind \"$obbSrcPath\" \"$targetPath\" 2>/dev/null")
                                }
                                RootShell.exec("mount --make-rprivate \"$targetPath\" 2>/dev/null")
                            }
                        }
                    }
                }
            }

            // Step 3: Memverifikasi Kaitan & SELinux
            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 3,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.9f
                )
            )
            // Ensure package is unsuspended if it was previously suspended during migration or MicroSD ejection
            RootShell.exec("pm unsuspend \"${game.packageName}\" 2>/dev/null")
            // Post-Mount Process Guard: force-stop app so Android ActivityManager purges cached process records.
            // When the user opens the app, Zygote spawns a completely fresh process that cleanly inherits the new mounts.
            forceStopApp(game.packageName)
            delay(150)

            // Step 4: Selesai
            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 4,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 1.0f,
                    isFinished = true,
                    isSuccess = true
                )
            )
            Unit
        }.onFailure { err ->
            onProgress?.invoke(
                OperationProgress(
                    type = OperationType.MOUNT,
                    title = getAppContext().getString(R.string.op_title_mount),
                    subtitle = "${game.displayName} • ${game.packageName}",
                    isFinished = true,
                    isSuccess = false,
                    errorMessage = err.message
                )
            )
        }
    }

    /**
     * Bind mount a single directory target into all runtime namespaces.
     */
    private suspend fun mountDirectoryTarget(
        mp: MountPointConfig,
        uid: Int,
        gid: Int,
        namespaces: List<String>,
        isMedia: Boolean,
        autoCreateSource: Boolean = true
    ): Boolean {
        val fsType = RootShell.execForOutput("stat -f -c %T \"${mp.sourcePath}\" 2>/dev/null").trim().lowercase()
        val isNonPosix = fsType in listOf("exfat", "vfat", "msdos", "ntfs") || mp.sourcePath.startsWith("/mnt/media_rw")

        if (!RootShell.exists(mp.sourcePath)) {
            if (autoCreateSource) {
                AppLogger.info("MountManager", "Source path ${mp.sourcePath} does not exist on target storage. Creating directory for direct mount.")
                RootShell.exec("mkdir -p \"${mp.sourcePath}\" 2>/dev/null")
                if (!isNonPosix) {
                    RootShell.exec("chown $uid:1023 \"${mp.sourcePath}\" 2>/dev/null")
                    RootShell.exec("chmod 777 \"${mp.sourcePath}\" 2>/dev/null")
                    RootShell.exec("chcon u:object_r:media_rw_data_file:s0 \"${mp.sourcePath}\" 2>/dev/null")
                }
                RootShell.exec("touch \"${mp.sourcePath}/.mountx_canary\" 2>/dev/null")
            } else {
                AppLogger.warn("MountManager", "Source path ${mp.sourcePath} does not exist on SD storage. Skipping mount to prevent hiding internal data.")
                return false
            }
        } else {
            if (!isNonPosix) {
                RootShell.exec("chown $uid:1023 \"${mp.sourcePath}\" 2>/dev/null")
                RootShell.exec("chmod 777 \"${mp.sourcePath}\" 2>/dev/null")
                RootShell.exec("chcon u:object_r:media_rw_data_file:s0 \"${mp.sourcePath}\" 2>/dev/null")
            }
            RootShell.exec("touch \"${mp.sourcePath}/.mountx_canary\" 2>/dev/null")
        }

        // Extract relative path from target path across any user profile
        val relPath = extractRelativePath(mp.targetPath)
        val targetUserId = resolveUserId(mp.targetPath)
        if (relPath.isBlank()) {
            AppLogger.error("MountManager", "Cannot mount to empty relative path for target: ${mp.targetPath}")
            return false
        }

        // Clear any preexisting /data/media mount point to prevent sdcardfs EXDEV (Cross-device link) error
        // When /data/media has an active mount on another filesystem, sdcardfs on /storage/emulated
        // throws EXDEV on any pathname lookup for that child directory.
        RootShell.exec("umount -f -l \"/data/media/$targetUserId/$relPath\" 2>/dev/null")

        // Pre-resolve source block device and active mounts snapshot once to eliminate 20+ redundant shell calls across 11 namespaces
        val reqDev = RootShell.execForOutput("df \"${mp.sourcePath}\" 2>/dev/null").lines()
            .getOrNull(1)?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""
        val procMountsLines = RootShell.execForOutput("cat /proc/mounts 2>/dev/null").lines()

        var anyMounted = false
        for (namespace in namespaces) {
            val nsTarget = "$namespace/$relPath"
            if (RootShell.exists(namespace)) {
                if (RootShell.isMountpoint(nsTarget)) {
                    val curDev = procMountsLines
                        .firstOrNull { it.contains(" $nsTarget ") }
                        ?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""
                    val isCurrentMountSame = curDev == mp.sourcePath || (curDev.isNotBlank() && reqDev.isNotBlank() && curDev == reqDev)
                    if (curDev.isNotBlank() && reqDev.isNotBlank() && !isCurrentMountSame) {
                        AppLogger.info("MountManager", "Unmounting stale mount on $nsTarget ($curDev -> $reqDev)")
                        RootShell.exec("umount -f -l \"$nsTarget\" 2>/dev/null")
                    } else if (isCurrentMountSame) {
                        anyMounted = true
                        continue
                    }
                }
                RootShell.exec("mkdir -p \"$nsTarget\" 2>/dev/null")
                var mountRes = if (isNonPosix) {
                    val sdRes = RootShell.exec("toybox mount -t sdcardfs -o fsuid=1023,fsgid=1023,gid=$uid,mask=0 \"${mp.sourcePath}\" \"$nsTarget\" 2>/dev/null")
                    if (sdRes.isSuccess || RootShell.isMountpoint(nsTarget)) {
                        sdRes
                    } else {
                        RootShell.exec("mount -o bind \"${mp.sourcePath}\" \"$nsTarget\" 2>/dev/null")
                    }
                } else {
                    RootShell.exec("mount -o bind \"${mp.sourcePath}\" \"$nsTarget\" 2>/dev/null")
                }

                // SELinux and Permission Self-Healing Fallback
                if (!mountRes.isSuccess && !RootShell.isMountpoint(nsTarget)) {
                    AppLogger.warn("MountManager", "Bind mount initially failed on $nsTarget. Executing SELinux and permission self-healing...")
                    RootShell.exec("restorecon -R \"${mp.sourcePath}\" 2>/dev/null")
                    RootShell.exec("restorecon -R \"$nsTarget\" 2>/dev/null")
                    RootShell.exec("chmod 775 \"$nsTarget\" 2>/dev/null")
                    RootShell.exec("chown $uid:$gid \"$nsTarget\" 2>/dev/null")
                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"${mp.sourcePath}\" 2>/dev/null")
                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$nsTarget\" 2>/dev/null")

                    mountRes = RootShell.exec("mount -o bind \"${mp.sourcePath}\" \"$nsTarget\" 2>/dev/null")
                    if (mountRes.isSuccess || RootShell.isMountpoint(nsTarget)) {
                        AppLogger.info("MountManager", "Self-healing succeeded: bind mount established on $nsTarget")
                    } else {
                        AppLogger.error("MountManager", "Self-healing bind mount retry failed on $nsTarget: ${mountRes.output}")
                    }
                }

                if (mountRes.isSuccess || RootShell.isMountpoint(nsTarget)) {
                    RootShell.exec("mount --make-rprivate \"$nsTarget\" 2>/dev/null")
                    anyMounted = true
                }
            }
        }
        return anyMounted
    }

    /**
     * Mounts PRIVATE_INTERNAL (/data/user/0/<pkg>) using the Adaptive Dual-Engine architecture:
     * - Engine A (Direct Native Folder): Used on POSIX filesystems (F2FS, EXT4) when $sdBase/MountX/data/<pkg> exists
     *   or no legacy .img exists. Zero overhead, 100% native speed, unlimited dynamic capacity.
     * - Engine B (Sparse Dynamic ext4 Loop Container): Used on Non-POSIX filesystems (exFAT, FAT32/vFAT, NTFS)
     *   or when an existing .img container is present. Uses sparse allocation + --direct-io=on.
     * Compatible across Android 10 through Android 17 (including /data_mirror/data_ce/null/0 and 4KB/16KB pages).
     */
    private suspend fun mountPrivateInternalData(
        packageName: String,
        mp: MountPointConfig,
        sdBase: String,
        uid: Int,
        gid: Int,
        hasSeparateCache: Boolean = false
    ): Boolean {
        val containerDir = "$sdBase/.mountx/containers"
        val containerPath = mp.containerImgPath?.ifBlank { null }
            ?: (if (mp.sourcePath.endsWith(".img", ignoreCase = true)) mp.sourcePath else "$containerDir/${packageName}_data.img")
        val nativeFolderPath = if (mp.sourcePath.isNotBlank() && !mp.sourcePath.endsWith(".img", ignoreCase = true)) {
            mp.sourcePath
        } else {
            "$sdBase/MountX/data/$packageName"
        }

        val fsType = RootShell.execForOutput("stat -f -c %T \"$sdBase\" 2>/dev/null").trim().lowercase()
        val isNonPosix = fsType in listOf("exfat", "vfat", "msdos", "ntfs") || sdBase.startsWith("/mnt/media_rw")
        val isFat32 = fsType in listOf("vfat", "msdos", "fat32")

        val hasNativeFolder = RootShell.exists(nativeFolderPath)
        val hasContainerImg = RootShell.exists(containerPath)

        // Choose Engine A (Direct Native Folder) if POSIX and either native folder exists or no legacy container exists
        val useDirectFolderEngine = !isNonPosix && (hasNativeFolder || !hasContainerImg)

        val target = "/data/user/0/$packageName"
        val dataDataTarget = "/data/data/$packageName"
        val dataCeTarget = "/data_mirror/data_ce/null/0/$packageName"
        RootShell.exec("mkdir -p \"$target\" 2>/dev/null")

        val cacheHoldDir = "/dev/mountx_cache_hold_$packageName"
        var heldInternalCache = false
        if (useDirectFolderEngine && !hasSeparateCache && !RootShell.isMountpoint(target)) {
            val internalCacheHasFiles = RootShell.execForOutput("find \"$target/cache\" -type f 2>/dev/null | head -n 1").trim().isNotBlank()
            val sdCacheHasFiles = RootShell.execForOutput("find \"$nativeFolderPath/cache\" -type f 2>/dev/null | head -n 1").trim().isNotBlank()
            if (internalCacheHasFiles && !sdCacheHasFiles) {
                RootShell.exec("mkdir -p \"$cacheHoldDir\" 2>/dev/null && mount -o bind \"$target/cache\" \"$cacheHoldDir\" 2>/dev/null")
                heldInternalCache = RootShell.isMountpoint(cacheHoldDir)
            }
        }

        val origMcsContext = RootShell.execForOutput("stat -c %C \"$target\" 2>/dev/null").trim()
        val effectiveContext = if (origMcsContext.isNotBlank() && origMcsContext.contains("app_data_file")) {
            origMcsContext
        } else {
            "u:object_r:app_data_file:s0"
        }

        val mountSucceeded = if (useDirectFolderEngine) {
            RootShell.exec("mkdir -p \"$nativeFolderPath\" 2>/dev/null")
            RootShell.exec("chown -R $uid:$gid \"$nativeFolderPath\" 2>/dev/null")
            RootShell.exec("chmod 700 \"$nativeFolderPath\" 2>/dev/null && chmod -R u+rwX,go-rwx \"$nativeFolderPath\" 2>/dev/null")
            RootShell.exec("find \"$nativeFolderPath\" -type d -exec chmod 700 {} + 2>/dev/null")
            RootShell.exec("find \"$nativeFolderPath\" -type f -exec chmod 600 {} + 2>/dev/null")
            RootShell.exec("chcon -R \"$effectiveContext\" \"$nativeFolderPath\" 2>/dev/null")

            val mountRes = RootShell.exec("mount -o bind \"$nativeFolderPath\" \"$target\" 2>/dev/null")
            val ok = mountRes.isSuccess || RootShell.isMountpoint(target)
            if (ok) {
                AppLogger.success("MountManager", "Mounted Engine A (Direct Native Folder) $nativeFolderPath -> $target")
            } else {
                AppLogger.error("MountManager", "Failed to mount Engine A folder $nativeFolderPath -> $target: ${mountRes.output}")
            }
            ok
        } else {
            RootShell.exec("mkdir -p \"$containerDir\" 2>/dev/null")
            if (!hasContainerImg) {
                val freeSdOut = RootShell.execForOutput("df -k \"$sdBase\" 2>/dev/null | tail -1").trim()
                val freeSdParts = freeSdOut.split(Regex("\\s+"))
                val freeSdMb = if (freeSdParts.size >= 4) (freeSdParts[3].toLongOrNull() ?: 0L) / 1024L else 4096L
                val minRequiredMb = ((mp.sizeBytes / (1024 * 1024)) + 512L).coerceAtLeast(1024L)
                val virtualSizeMb = if (isFat32) {
                    maxOf(minRequiredMb, minOf(freeSdMb - 128L, 3800L)).coerceAtMost(3800L)
                } else {
                    val maxDynamicMb = ((freeSdMb * 95L) / 100L).coerceAtLeast(minRequiredMb)
                    maxOf(maxOf(minRequiredMb, 8192L), maxDynamicMb)
                }
                RootShell.exec("truncate -s ${virtualSizeMb}M \"$containerPath\" 2>/dev/null || dd if=/dev/zero of=\"$containerPath\" bs=1M count=0 seek=$virtualSizeMb 2>/dev/null")
                RootShell.exec("mke2fs -F -t ext4 -b 4096 -m 0 -O ^has_journal -E lazy_itable_init=1 \"$containerPath\" 2>/dev/null || mkfs.ext4 -F -b 4096 -m 0 \"$containerPath\" 2>/dev/null")
                AppLogger.info("MountManager", "Created Sparse Dynamic ext4 container: $containerPath ($virtualSizeMb MB virtual ceiling)")
            } else {
                ensureSparseContainerCapacity(containerPath, sdBase, isFat32)
            }

            val loopDev = attachLoopDevice(containerPath)
            if (loopDev.isBlank()) {
                AppLogger.error("MountManager", "Failed to setup loop device for container: $containerPath")
                return false
            }

            val mountRes = RootShell.exec("mount -t ext4 -o rw,nosuid,nodev,noatime,discard \"$loopDev\" \"$target\" 2>/dev/null || mount -t ext4 -o rw,nosuid,nodev,noatime \"$loopDev\" \"$target\" 2>/dev/null")
            val ok = mountRes.isSuccess || RootShell.isMountpoint(target)
            if (ok) {
                AppLogger.success("MountManager", "Mounted Engine B (Sparse ext4 Container) $loopDev -> $target")
            } else {
                AppLogger.error("MountManager", "Failed to mount Engine B loop container $loopDev -> $target: ${mountRes.output}")
            }
            ok
        }

        if (mountSucceeded) {
            if (RootShell.exists(dataDataTarget) && !RootShell.isMountpoint(dataDataTarget)) {
                RootShell.exec("mount -o bind \"$target\" \"$dataDataTarget\" 2>/dev/null")
            }
            if (RootShell.exists(dataCeTarget) && !RootShell.isMountpoint(dataCeTarget)) {
                RootShell.exec("mount -o bind \"$target\" \"$dataCeTarget\" 2>/dev/null")
            }
            RootShell.exec("chown -R $uid:$gid \"$target\" 2>/dev/null")
            RootShell.exec("chmod 700 \"$target\" 2>/dev/null && chmod -R u+rwX,go-rwx \"$target\" 2>/dev/null")
            RootShell.exec("find \"$target\" -type d -exec chmod 700 {} + 2>/dev/null")
            RootShell.exec("find \"$target\" -type f -exec chmod 600 {} + 2>/dev/null")
            RootShell.exec("chcon -R \"$effectiveContext\" \"$target\" 2>/dev/null")
            if (RootShell.exists(dataDataTarget)) {
                RootShell.exec("chcon -R \"$effectiveContext\" \"$dataDataTarget\" 2>/dev/null")
            }
            if (RootShell.exists(dataCeTarget)) {
                RootShell.exec("chcon -R \"$effectiveContext\" \"$dataCeTarget\" 2>/dev/null")
            }
            if (heldInternalCache) {
                RootShell.exec("mkdir -p \"$target/cache\" 2>/dev/null && mount -o bind \"$cacheHoldDir\" \"$target/cache\" 2>/dev/null")
                if (RootShell.exists(dataDataTarget)) {
                    RootShell.exec("mkdir -p \"$dataDataTarget/cache\" 2>/dev/null && mount -o bind \"$cacheHoldDir\" \"$dataDataTarget/cache\" 2>/dev/null")
                }
                if (RootShell.exists(dataCeTarget)) {
                    RootShell.exec("mkdir -p \"$dataCeTarget/cache\" 2>/dev/null && mount -o bind \"$cacheHoldDir\" \"$dataCeTarget/cache\" 2>/dev/null")
                }
                RootShell.exec("umount -l \"$cacheHoldDir\" 2>/dev/null && rmdir \"$cacheHoldDir\" 2>/dev/null")
            }
            // Self-healing: ensure direct boot storage and ART profile directories exist to prevent Zygote Signal 11 crash
            RootShell.exec("""
                if [ ! -d "/data/user_de/0/$packageName" ]; then
                    mkdir -p "/data/user_de/0/$packageName/cache" "/data/user_de/0/$packageName/code_cache" 2>/dev/null
                    chown -R $uid:$gid "/data/user_de/0/$packageName" 2>/dev/null
                    chmod 700 "/data/user_de/0/$packageName" 2>/dev/null
                    restorecon -FR "/data/user_de/0/$packageName" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "/data/user_de/0/$packageName" 2>/dev/null
                fi
                if [ ! -d "/data/misc/profiles/cur/0/$packageName" ]; then
                    mkdir -p "/data/misc/profiles/cur/0/$packageName" 2>/dev/null
                    chown $uid:$gid "/data/misc/profiles/cur/0/$packageName" 2>/dev/null
                    chmod 700 "/data/misc/profiles/cur/0/$packageName" 2>/dev/null
                    chcon u:object_r:user_profile_data_file:s0 "/data/misc/profiles/cur/0/$packageName" 2>/dev/null
                fi
            """.trimIndent())
        } else if (heldInternalCache) {
            RootShell.exec("umount -l \"$cacheHoldDir\" 2>/dev/null && rmdir \"$cacheHoldDir\" 2>/dev/null")
        }
        return mountSucceeded
    }

    /**
     * Unmount a single game from all runtime namespaces and detach any loop containers.
     * Includes Pre-Unmount Process Check: stops active game process before unmounting to prevent corruption.
     */
    suspend fun unmountGame(
        game: GameEntry,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val ctx = getAppContext()
                val steps = listOf(
                    ctx.getString(R.string.op_unmount_step_stopping),
                    ctx.getString(R.string.op_unmount_step_unmounting),
                    ctx.getString(R.string.op_unmount_step_restoring_context),
                    ctx.getString(R.string.op_unmount_step_finished)
                )
                val opType = OperationType.UNMOUNT
                val title = ctx.getString(R.string.op_title_unmount)
                val subtitle = "${game.displayName} • ${game.packageName}"

                // Step 0: Stop app
                onProgress?.invoke(
                    OperationProgress(
                        type = opType,
                        title = title,
                        subtitle = subtitle,
                        currentStepIndex = 0,
                        totalSteps = steps.size,
                        stepDescriptions = steps,
                        progressPercent = 0.25f
                    )
                )

                // Pre-Unmount Process Check: unconditionally force-stop app to prevent I/O corruption
                forceStopApp(game.packageName)

                // Step 1: Unmount VFS
                onProgress?.invoke(
                    OperationProgress(
                        type = opType,
                        title = title,
                        subtitle = subtitle,
                        currentStepIndex = 1,
                        totalSteps = steps.size,
                        stepDescriptions = steps,
                        progressPercent = 0.6f
                    )
                )

                val allUserIds = getActiveUserIds()

                if (game.mountPoints.isNotEmpty()) {
                    for (mp in game.mountPoints) {
                        val targetUserId = resolveUserId(mp.targetPath)
                        if (mp.category == MountPointCategory.PRIVATE_INTERNAL || mp.isEffectivelyVirtualContainer()) {
                            // Unmount private internal data (Direct Folder or Virtual Loop Container)
                            RootShell.exec("sync")
                            RootShell.exec("umount -f -l \"/data_mirror/data_ce/null/$targetUserId/${game.packageName}\" 2>/dev/null")
                            RootShell.exec("umount -f -l \"/data_mirror/data_ce/null/0/${game.packageName}\" 2>/dev/null")
                            RootShell.exec("umount -f -l \"/data/data/${game.packageName}\" 2>/dev/null")
                            RootShell.exec("umount -f -l \"/data/user/$targetUserId/${game.packageName}\" 2>/dev/null")
                            val imgPath = mp.containerImgPath?.ifBlank { null }
                                ?: (if (mp.sourcePath.endsWith(".img", ignoreCase = true)) mp.sourcePath else "${game.packageName}_data.img")
                            detachLoopDevice(imgPath)
                        } else if (mp.category == MountPointCategory.APP_PACKAGE || mp.targetPath.startsWith("/data/app")) {
                            val currentApkPathRes = RootShell.exec("pm path \"${game.packageName}\" 2>/dev/null | head -n 1")
                            val currentApkDir = if (currentApkPathRes.isSuccess && currentApkPathRes.output.contains("package:")) {
                                val fullPath = currentApkPathRes.output.substringAfter("package:").trim()
                                java.io.File(fullPath).parent ?: ""
                            } else ""
                            val effectiveTarget = if (mp.targetPath.contains("/lib")) {
                                if (currentApkDir.isNotBlank()) "$currentApkDir/lib" else mp.targetPath
                            } else {
                                if (currentApkDir.isNotBlank()) currentApkDir else mp.targetPath
                            }
                            RootShell.exec("umount -f -l \"$effectiveTarget\" 2>/dev/null")
                            RootShell.exec("umount -f -l \"${mp.targetPath}\" 2>/dev/null")
                            if (mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)) {
                                val imgPath = mp.containerImgPath?.ifBlank { null } ?: mp.sourcePath
                                detachLoopDevice(imgPath)
                            }
                            RootShell.exec("restorecon -FR \"$effectiveTarget\" 2>/dev/null")
                        } else if (mp.category == MountPointCategory.CACHE_SHADERS && (mp.targetPath.startsWith("/data/user/") || mp.targetPath.startsWith("/data/data/"))) {
                            RootShell.exec("umount -f -l \"${mp.targetPath}\" 2>/dev/null")
                            val altTarget = if (mp.targetPath.startsWith("/data/user/0/")) {
                                mp.targetPath.replace("/data/user/0/", "/data/data/")
                            } else if (mp.targetPath.startsWith("/data/data/")) {
                                mp.targetPath.replace("/data/data/", "/data/user/0/")
                            } else null
                            if (altTarget != null) {
                                RootShell.exec("umount -f -l \"$altTarget\" 2>/dev/null")
                            }
                            if (mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)) {
                                val imgPath = mp.containerImgPath?.ifBlank { null } ?: mp.sourcePath
                                detachLoopDevice(imgPath)
                            }
                            RootShell.exec("restorecon -FR \"${mp.targetPath}\" 2>/dev/null")
                        } else {
                            if (mp.isExternalStorageCategory()) {
                                val userNamespaces = getTargetNamespaces(targetUserId)
                                val relPath = extractRelativePath(mp.targetPath)
                                if (relPath.isNotBlank()) {
                                    for (ns in userNamespaces) {
                                        RootShell.exec("umount -f -l \"$ns/$relPath\" 2>/dev/null")
                                    }
                                }
                            }
                            RootShell.exec("umount -f -l \"${mp.targetPath}\" 2>/dev/null")
                        }
                    }
                }

                // Fallback unmount standard default paths across all user profiles
                val legacyRelPaths = listOf(
                    "Android/data/${game.packageName}/files",
                    "Android/data/${game.packageName}",
                    "Android/obb/${game.packageName}"
                )
                for (uid in allUserIds) {
                    val userNamespaces = getTargetNamespaces(uid)
                    for (namespace in userNamespaces) {
                        for (rel in legacyRelPaths) {
                            RootShell.exec("umount -f -l \"$namespace/$rel\" 2>/dev/null")
                        }
                    }
                }

                // Universal sweeping unmount: purge any remaining or stacked mounts across all namespaces
                val sweepScript = """
                    for i in 1 2 3; do
                      has_mount=0
                      while read dev mnt rest; do
                        case "${'$'}mnt" in
                          *${game.packageName}*)
                            umount -f -l "${'$'}mnt" 2>/dev/null
                            has_mount=1
                            ;;
                        esac
                      done < /proc/mounts
                      if [ ${'$'}has_mount -eq 0 ]; then break; fi
                    done
                """.trimIndent()
                RootShell.execScript(sweepScript)

                // Step 2: Restore SELinux context across active users
                onProgress?.invoke(
                    OperationProgress(
                        type = opType,
                        title = title,
                        subtitle = subtitle,
                        currentStepIndex = 2,
                        totalSteps = steps.size,
                        stepDescriptions = steps,
                        progressPercent = 0.85f
                    )
                )
                for (uid in allUserIds) {
                    RootShell.exec("restorecon -FR \"/data/media/$uid/Android/data/${game.packageName}\" 2>/dev/null")
                    RootShell.exec("restorecon -FR \"/data/media/$uid/Android/obb/${game.packageName}\" 2>/dev/null")
                }
                // Post-Unmount Process Guard: ensure app processes are completely terminated
                forceStopApp(game.packageName)
                delay(150)

                // Step 3: Finished
                onProgress?.invoke(
                    OperationProgress(
                        type = opType,
                        title = title,
                        subtitle = subtitle,
                        currentStepIndex = 3,
                        totalSteps = steps.size,
                        stepDescriptions = steps,
                        progressPercent = 1.0f,
                        isFinished = true,
                        isSuccess = true
                    )
                )
                Unit
            }.onFailure { err ->
                onProgress?.invoke(
                    OperationProgress(
                        type = OperationType.UNMOUNT,
                        title = getAppContext().getString(R.string.op_title_unmount),
                        subtitle = "${game.displayName} • ${game.packageName}",
                        isFinished = true,
                        isSuccess = false,
                        errorMessage = err.message
                    )
                )
            }
        }

    /**
     * Unmount specific mount points without unmounting other active points of the game.
     */
    suspend fun unmountMountPoints(
        game: GameEntry,
        points: List<MountPointConfig>,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = getAppContext()
            val steps = listOf(
                ctx.getString(R.string.mount_step_stop_app),
                ctx.getString(R.string.mount_step_unbind_vfs),
                ctx.getString(R.string.mount_step_restore_selinux),
                ctx.getString(R.string.mount_step_unmount_complete)
            )
            val opType = OperationType.UNMOUNT
            val title = ctx.getString(R.string.op_unmount_categories_title)
            val subtitle = ctx.getString(R.string.op_game_categories_subtitle, game.displayName, points.size)

            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 0,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.25f
                )
            )

            // Force-stop app process unconditionally before unmounting
            forceStopApp(game.packageName)

            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 1,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.6f
                )
            )

            for (mp in points) {
                val targetUserId = resolveUserId(mp.targetPath)
                if (mp.category == MountPointCategory.PRIVATE_INTERNAL || mp.isEffectivelyVirtualContainer()) {
                    RootShell.exec("sync")
                    RootShell.exec("umount -f -l \"/data_mirror/data_ce/null/$targetUserId/${game.packageName}\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"/data_mirror/data_ce/null/0/${game.packageName}\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"/data/data/${game.packageName}\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"/data/user/$targetUserId/${game.packageName}\" 2>/dev/null")
                    val imgPath = mp.containerImgPath?.ifBlank { null }
                        ?: (if (mp.sourcePath.endsWith(".img", ignoreCase = true)) mp.sourcePath else "${game.packageName}_data.img")
                    detachLoopDevice(imgPath)
                } else if (mp.category == MountPointCategory.APP_PACKAGE || mp.targetPath.startsWith("/data/app")) {
                    val currentApkPathRes = RootShell.exec("pm path \"${game.packageName}\" 2>/dev/null | head -n 1")
                    val currentApkDir = if (currentApkPathRes.isSuccess && currentApkPathRes.output.contains("package:")) {
                        val fullPath = currentApkPathRes.output.substringAfter("package:").trim()
                        java.io.File(fullPath).parent ?: ""
                    } else ""
                    val effectiveTarget = if (mp.targetPath.contains("/lib")) {
                        if (currentApkDir.isNotBlank()) "$currentApkDir/lib" else mp.targetPath
                    } else {
                        if (currentApkDir.isNotBlank()) currentApkDir else mp.targetPath
                    }
                    RootShell.exec("umount -f -l \"$effectiveTarget\" 2>/dev/null")
                    RootShell.exec("umount -f -l \"${mp.targetPath}\" 2>/dev/null")
                    if (mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)) {
                        val imgPath = mp.containerImgPath?.ifBlank { null } ?: mp.sourcePath
                        detachLoopDevice(imgPath)
                    }
                    RootShell.exec("restorecon -FR \"$effectiveTarget\" 2>/dev/null")
                } else if (mp.category == MountPointCategory.CACHE_SHADERS && (mp.targetPath.startsWith("/data/user/") || mp.targetPath.startsWith("/data/data/"))) {
                    RootShell.exec("umount -f -l \"${mp.targetPath}\" 2>/dev/null")
                    val altTarget = if (mp.targetPath.startsWith("/data/user/0/")) {
                        mp.targetPath.replace("/data/user/0/", "/data/data/")
                    } else if (mp.targetPath.startsWith("/data/data/")) {
                        mp.targetPath.replace("/data/data/", "/data/user/0/")
                    } else null
                    if (altTarget != null) {
                        RootShell.exec("umount -f -l \"$altTarget\" 2>/dev/null")
                    }
                    if (mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)) {
                        val imgPath = mp.containerImgPath?.ifBlank { null } ?: mp.sourcePath
                        detachLoopDevice(imgPath)
                    }
                    RootShell.exec("restorecon -FR \"${mp.targetPath}\" 2>/dev/null")
                } else {
                    if (mp.isExternalStorageCategory()) {
                        val userNamespaces = getTargetNamespaces(targetUserId)
                        val relPath = extractRelativePath(mp.targetPath)
                        if (relPath.isNotBlank()) {
                            for (ns in userNamespaces) {
                                RootShell.exec("umount -f -l \"$ns/$relPath\" 2>/dev/null")
                            }
                            RootShell.exec("umount -f -l \"/data/media/$targetUserId/$relPath\" 2>/dev/null")
                        }
                    }
                    RootShell.exec("umount -f -l \"${mp.targetPath}\" 2>/dev/null")
                    RootShell.exec("restorecon -FR \"${mp.targetPath}\" 2>/dev/null")
                    if (mp.category == MountPointCategory.MEDIA_DOWNLOADS || mp.category == MountPointCategory.CUSTOM) {
                        triggerMediaScan(mp.targetPath)
                    }
                }
            }

            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 3,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 1.0f,
                    isFinished = true,
                    isSuccess = true
                )
            )
            forceStopApp(game.packageName)
            Unit
        }.onFailure { err ->
            onProgress?.invoke(
                OperationProgress(
                    type = OperationType.UNMOUNT,
                    title = getAppContext().getString(R.string.op_title_unmount),
                    subtitle = getAppContext().getString(R.string.op_game_categories_subtitle, game.displayName, points.size),
                    isFinished = true,
                    isSuccess = false,
                    errorMessage = err.message
                )
            )
        }
    }

    /**
     * Unmount all bind-mounted paths and loop devices from the given sdBase.
     */
    suspend fun unmountAll(sdBase: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Detach all virtual containers first
                val containersScript = """
                    while read dev mnt rest; do
                      case "${'$'}mnt" in
                        *.mountx/containers*|*/data/user/0/*|*/data/data/*)
                          sync
                          umount -f -l "${'$'}mnt" 2>/dev/null
                          ;;
                      esac
                    done < /proc/mounts
                    for loop_dev in $(losetup -a 2>/dev/null | grep "\.mountx/containers" | cut -d':' -f1); do
                      if [ -n "${'$'}loop_dev" ]; then
                        losetup -d "${'$'}loop_dev" 2>/dev/null
                      fi
                    done
                """.trimIndent()
                RootShell.execScript(containersScript)

                // Unmount bind mounts cleanly with multi-pass sweep across all external partitions
                val script = """
                    SD_BLOCK=${'$'}(mount | grep " $sdBase " | cut -d' ' -f1 | head -n 1)
                    for i in 1 2 3; do
                      has_mount=0
                      while read -r dev mnt rest; do
                        case "${'$'}mnt" in
                          *Android/data/*|*Android/obb/*|*Android/media/*)
                            if [ "${'$'}mnt" != "$sdBase" ]; then
                              if [ -n "${'$'}SD_BLOCK" ] && [ "${'$'}dev" = "${'$'}SD_BLOCK" ]; then
                                umount -f -l "${'$'}mnt" 2>/dev/null
                                has_mount=1
                              else
                                case "${'$'}dev" in
                                  /dev/block/mmcblk*|/dev/block/sd*|/dev/block/nvme*)
                                    if [ "${'$'}mnt" != "/data" ] && [ "${'$'}mnt" != "$sdBase" ]; then
                                      umount -f -l "${'$'}mnt" 2>/dev/null
                                      has_mount=1
                                    fi
                                    ;;
                                esac
                              fi
                            fi
                            ;;
                          *.mountx*|*MountX*)
                            if [ "${'$'}mnt" != "$sdBase" ]; then
                              umount -f -l "${'$'}mnt" 2>/dev/null
                              has_mount=1
                            fi
                            ;;
                        esac
                      done < /proc/mounts
                      if [ ${'$'}has_mount -eq 0 ]; then break; fi
                    done
                """.trimIndent()
                RootShell.execScript(script)
                RootShell.exec("touch /dev/.mountx_unmounted")
                Unit
            }
        }

    /**
     * Get all currently mounted paths from /proc/mounts.
     */
    suspend fun getMountedPaths(): List<String> = withContext(Dispatchers.IO) {
        try {
            val procMounts = java.io.File("/proc/mounts")
            if (procMounts.exists() && procMounts.canRead()) {
                return@withContext procMounts.readLines().mapNotNull { line -> line.split(" ").getOrNull(1) }
            }
        } catch (_: Exception) {}
        RootShell.exec("grep -E '(/data/media/0/Android|/data/sdext|/mnt/media_rw)' /proc/mounts 2>/dev/null")
            .stdout
            .mapNotNull { line -> line.split(" ").getOrNull(1) }
    }

    /**
     * Get all currently mounted target paths parsed into an in-memory HashSet.
     * Uses RootShell to ensure full root namespace visibility across Android namespaces.
     */
    suspend fun getActiveMountpointsSet(): Set<String> = withContext(Dispatchers.IO) {
        val mounts = HashSet<String>()
        val res = RootShell.exec("cat /proc/mounts 2>/dev/null")
        if (res.isSuccess) {
            for (line in res.stdout) {
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val target = parts[1].trimEnd('/')
                    if (target.isNotBlank()) {
                        mounts.add(target)
                    }
                }
            }
        }
        mounts
    }

    /**
     * Check if a specific path is currently mounted.
     */
    suspend fun isMounted(path: String): Boolean {
        val clean = path.trimEnd('/')
        return getActiveMountpointsSet().contains(clean)
    }

    /**
     * Thoroughly terminates all active, cached, and background processes, tasks, and services
     * for the specified package to prevent stale mount namespaces, open file descriptor leaks,
     * and force-close crashes upon launching after mount or migration.
     */
    suspend fun forceStopApp(packageName: String) = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext
        AppLogger.info("MountManager", "Force-stopping all processes for package: $packageName")
        RootShell.exec("am force-stop \"$packageName\" 2>/dev/null")
        RootShell.exec("pkill -9 -f \"$packageName\" 2>/dev/null || true")
        RootShell.exec("killall -9 \"$packageName\" 2>/dev/null || true")
        delay(150)
    }

    /**
     * Checks whether an application process is actively running in the background or foreground.
     */
    suspend fun isGameRunning(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext false
        val pgrepRes = RootShell.exec("pgrep -f \"$packageName\" 2>/dev/null || pidof \"$packageName\" 2>/dev/null")
        pgrepRes.isSuccess && pgrepRes.output.isNotBlank()
    }

    /**
     * Get the Linux UID of an installed app via PackageManager.
     * Falls back to 10000 if not found.
     */
    suspend fun getGameUid(packageName: String): Int = withContext(Dispatchers.IO) {
        val result = RootShell.exec(
            "pm list packages -U 2>/dev/null | grep -F \"package:$packageName\" | sed -n 's/.*uid:\\([0-9]*\\).*/\\1/p' | head -n 1"
        )
        result.output.trim().toIntOrNull() ?: 10000
    }

    /**
     * Resolves dynamic UID and GID directly from stat /data/user/$userId/$packageName
     * to ensure full compatibility with Android 11-14+ app and multi-user isolation.
     */
    suspend fun resolveAppIdentity(packageName: String, userId: Int = 0): AppIdentity = withContext(Dispatchers.IO) {
        val statRes = RootShell.exec(
            "stat -c \"%u %g\" \"/data/user/$userId/$packageName\" 2>/dev/null || " +
            "stat -c \"%u %g\" \"/data/data/$packageName\" 2>/dev/null"
        )
        if (statRes.isSuccess && statRes.output.isNotBlank()) {
            val tokens = statRes.output.trim().split("\\s+".toRegex())
            val uid = tokens.getOrNull(0)?.toIntOrNull()
            val gid = tokens.getOrNull(1)?.toIntOrNull()
            if (uid != null && gid != null && uid > 0) {
                return@withContext AppIdentity(uid, gid)
            }
        }
        val fallbackUid = getGameUid(packageName)
        val calculatedUid = if (userId > 0 && fallbackUid < 100000) (userId * 100000) + fallbackUid else fallbackUid
        AppIdentity(calculatedUid, calculatedUid)
    }

    /**
     * Dynamic I/O priority boost for game execution.
     * Sets Real-Time/Best-Effort ionice and elevates scheduling niceness.
     */
    suspend fun boostGameIo(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pgrep = RootShell.exec("pgrep -f \"$packageName\" 2>/dev/null")
            if (pgrep.isSuccess && pgrep.output.isNotBlank()) {
                val pids = pgrep.stdout.map { it.trim() }.filter { it.isNotBlank() }
                for (pid in pids) {
                    RootShell.exec("ionice -c 1 -n 0 -p $pid 2>/dev/null || ionice -c 2 -n 0 -p $pid 2>/dev/null")
                    RootShell.exec("renice -n -10 -p $pid 2>/dev/null")
                }
            }
        }
    }

    /**
     * High-performance batch canary verification check.
     * Probes all canary files across all games in a single shell command instead of N*M individual calls.
     */
    suspend fun verifyCanariesBatch(games: List<GameEntry>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (games.isEmpty()) return@withContext emptyMap()
        val pathsByGame = mutableMapOf<String, List<String>>()
        val allPaths = mutableSetOf<String>()

        for (game in games) {
            val pkg = game.packageName
            val checkPaths = mutableListOf(
                "/storage/emulated/0/Android/data/$pkg/.mountx_canary",
                "/data/media/0/Android/data/$pkg/.mountx_canary",
                "/storage/emulated/0/Android/obb/$pkg/.mountx_canary",
                "/data/media/0/Android/obb/$pkg/.mountx_canary"
            )
            for (mp in game.mountPoints) {
                if (mp.targetPath.isNotBlank()) {
                    checkPaths.add("${mp.targetPath}/.mountx_canary")
                    if (mp.isExternalStorageCategory()) {
                        val rel = extractRelativePath(mp.targetPath)
                        if (rel.isNotBlank()) {
                            checkPaths.add("/data/media/0/$rel/.mountx_canary")
                        }
                    }
                }
            }
            pathsByGame[pkg] = checkPaths
            allPaths.addAll(checkPaths)
        }

        val existing = RootShell.pathsExistBatch(allPaths)
        pathsByGame.mapValues { (_, paths) -> paths.any { existing.contains(it) } }
    }

    /**
     * Canary verification check: verifies if the canary file is visible in target namespace.
     * Checks data, obb, and any configured custom mount points.
     */
    suspend fun verifyCanary(packageName: String, appEntry: GameEntry? = null): Boolean = withContext(Dispatchers.IO) {
        val checkPaths = mutableListOf(
            "/storage/emulated/0/Android/data/$packageName/.mountx_canary",
            "/data/media/0/Android/data/$packageName/.mountx_canary",
            "/storage/emulated/0/Android/obb/$packageName/.mountx_canary",
            "/data/media/0/Android/obb/$packageName/.mountx_canary"
        )
        if (appEntry != null && appEntry.mountPoints.isNotEmpty()) {
            for (mp in appEntry.mountPoints) {
                if (mp.targetPath.isNotBlank()) {
                    checkPaths.add("${mp.targetPath}/.mountx_canary")
                    if (mp.isExternalStorageCategory()) {
                        val rel = extractRelativePath(mp.targetPath)
                        if (rel.isNotBlank()) {
                            checkPaths.add("/data/media/0/$rel/.mountx_canary")
                        }
                    }
                }
            }
        }
        val existing = RootShell.pathsExistBatch(checkPaths)
        existing.isNotEmpty()
    }
}

/** Dynamic identity representing UID and GID for modern Android sandboxes */
data class AppIdentity(val uid: Int, val gid: Int)
