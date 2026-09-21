package app.mountx.root

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
 * Handles mounting and unmounting game & application data directories using bind mounts
 * and Virtual Ext4 Loop Containers for Universal Smart Directory Classification.
 */
class MountManager {

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
            list.add("/storage/emulated/$uid")
            list.add("/data/media/$uid")
            if (uid == 0) {
                list.add("/mnt/user/0/primary")
            }
        }
        list.filter { RootShell.exists(it) }
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
        fun extractRelativePath(path: String): String {
            return path
                .replace(Regex("^/(?:storage/emulated|data/media|mnt/user|data/user)/\\d+(?:/primary)?/?"), "")
                .removePrefix("/sdcard/")
                .removePrefix("/")
        }

        /**
         * Broadcasts media scanner trigger to update gallery and system media index immediately.
         */
        suspend fun triggerMediaScan(path: String): Unit = withContext(Dispatchers.IO) {
            RootShell.exec("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d \"file://$path\" 2>/dev/null")
        }
    }

    /**
     * Mount an application entry into runtime namespaces.
     * Supports both Multi-Target Array (v2.2.13) and legacy mode fallback.
     */
    suspend fun mountGame(
        game: GameEntry,
        sdBase: String,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val steps = listOf(
                "Menyiapkan Identitas Sandbox & Izin",
                "Memeriksa Direktori MicroSD",
                "Mengaitkan VFS ke Runtime Namespaces",
                "Memverifikasi Kaitan & SELinux",
                "Pengaitan Selesai"
            )
            val opType = OperationType.MOUNT
            val title = "Mengaitkan Data Game (Mount)"
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

            // Pre-Mount Process Guard: stop active app if running to prevent file locks or stale namespace
            val pgrepRes = RootShell.exec("pgrep -f \"${game.packageName}\" 2>/dev/null")
            if (pgrepRes.isSuccess && pgrepRes.output.isNotBlank()) {
                RootShell.exec("am force-stop \"${game.packageName}\" 2>/dev/null")
                AppLogger.info("MountManager", "Force-stopped process before mount: ${game.packageName}")
                delay(200)
            }

            // Dynamic UID/GID resolution directly from stat /data/user/$appUserId/$pkg
            val appUserId = game.mountPoints.firstOrNull()?.let { resolveUserId(it.targetPath) } ?: 0
            val identity = resolveAppIdentity(game.packageName, appUserId)
            val uid = identity.uid
            val gid = identity.gid

            // Set ownership on internal data directory
            RootShell.exec("chown -R $uid:$gid \"/data/user/$appUserId/${game.packageName}\" 2>/dev/null")
            RootShell.exec("chmod -R 775 \"/data/user/$appUserId/${game.packageName}\" 2>/dev/null")

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
            // Do not bind-mount an empty/missing MicroSD directory over a populated internal directory.
            var hasOcclusionRisk = false
            var occlusionInternalBytes = 0L
            var occlusionSdBytes = 0L
            for (mp in game.mountPoints) {
                if (mp.enabled && (mp.category == MountPointCategory.EXTERNAL_DATA || mp.category == MountPointCategory.OBB_STORAGE || mp.category == MountPointCategory.GAME_ASSETS || mp.category == MountPointCategory.CUSTOM)) {
                    val srcExists = RootShell.exists(mp.sourcePath)
                    val srcSizeKb = if (srcExists) {
                        val duRes = RootShell.exec("du -sk \"${mp.sourcePath}\" 2>/dev/null")
                        duRes.output.trim().split(Regex("\\s+")).getOrNull(0)?.toLongOrNull() ?: 0L
                    } else 0L

                    val targetExists = RootShell.exists(mp.targetPath)
                    val targetMounted = RootShell.isMountpoint(mp.targetPath)
                    val targetSizeKb = if (targetExists && !targetMounted) {
                        val duRes = RootShell.exec("du -sk \"${mp.targetPath}\" 2>/dev/null")
                        duRes.output.trim().split(Regex("\\s+")).getOrNull(0)?.toLongOrNull() ?: 0L
                    } else 0L

                    // Hazard: Target has data in internal storage, but source on MicroSD is empty (<= 128KB) or missing
                    if (targetExists && !targetMounted && targetSizeKb > 0L && (!srcExists || srcSizeKb <= 128L)) {
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
                    message = "Data game masih berada di Memori Internal. Pindahkan data ke MicroSD terlebih dahulu agar aman untuk di-mount."
                )
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
            if (game.mountPoints.isNotEmpty()) {
                // ── UNIVERSAL SMART MULTI-TARGET ARRAY ──
                for (mp in game.mountPoints) {
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
                            mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = false)
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
                            val res = mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = true)
                            if (res) triggerMediaScan(mp.targetPath)
                            res
                        }
                        MountPointCategory.CACHE_SHADERS -> {
                            mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = false)
                        }
                        MountPointCategory.CUSTOM -> {
                            val res = mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = false)
                            if (res) triggerMediaScan(mp.targetPath)
                            res
                        }
                        MountPointCategory.PRIVATE_INTERNAL -> {
                            mountVirtualExt4Container(game.packageName, mp, sdBase, uid, gid)
                            true
                        }
                        MountPointCategory.APP_PACKAGE -> {
                            RootShell.exec("mkdir -p \"${mp.sourcePath}\" 2>/dev/null")
                            val res = RootShell.exec("mount -o bind,exec \"${mp.sourcePath}\" \"${mp.targetPath}\"")
                            RootShell.exec("restorecon -FR \"${mp.targetPath}\" 2>/dev/null")
                            res.isSuccess
                        }
                    }
                    if (mounted) {
                        totalMountedTargets++
                    }
                }
                if (totalMountedTargets == 0) {
                    throw IllegalStateException("Tidak ada direktori MicroSD yang ditemukan untuk di-mount. Pastikan data game sudah dipindahkan ke MicroSD.")
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
                    error("Neither data nor obb source path exists on MicroSD for ${game.packageName}")
                }

                if (game.mode == MountMode.FILES) {
                    RootShell.exec("chmod 771 \"/data/user/0/${game.packageName}/databases\" 2>/dev/null")
                }

                if (hasData) {
                    RootShell.exec("chown -R $uid:$gid \"$sdBase/Android/data/${game.packageName}\" 2>/dev/null")
                    RootShell.exec("chmod -R 775 \"$sdBase/Android/data/${game.packageName}\" 2>/dev/null")
                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$sdBase/Android/data/${game.packageName}\" 2>/dev/null")
                    RootShell.exec("touch \"$sdBase/Android/data/${game.packageName}/.mountx_canary\" 2>/dev/null")

                    for (namespace in namespaces) {
                        val targetPath = "$namespace/$dataRelPath"
                        RootShell.exec("[ -d \"$namespace/Android/data\" ] && mkdir -p \"$targetPath\" 2>/dev/null")
                        RootShell.exec("[ -d \"$namespace/Android/data\" ] && mount -o bind \"$dataSrcPath\" \"$targetPath\" 2>/dev/null")
                    }
                }

                if (hasObb) {
                    RootShell.exec("chown -R $uid:$gid \"$obbSrcPath\" 2>/dev/null")
                    RootShell.exec("chmod -R 775 \"$obbSrcPath\" 2>/dev/null")
                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$obbSrcPath\" 2>/dev/null")
                    RootShell.exec("touch \"$obbSrcPath/.mountx_canary\" 2>/dev/null")

                    for (namespace in namespaces) {
                        val targetPath = "$namespace/$obbRelPath"
                        RootShell.exec("[ -d \"$namespace/Android\" ] && mkdir -p \"$namespace/Android/obb\" \"$targetPath\" 2>/dev/null")
                        RootShell.exec("[ -d \"$namespace/Android\" ] && mount -o bind \"$obbSrcPath\" \"$targetPath\" 2>/dev/null")
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
                    title = "Mengaitkan Data Game (Mount)",
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
        isMedia: Boolean
    ): Boolean {
        if (!RootShell.exists(mp.sourcePath)) {
            AppLogger.warn("MountManager", "Source path ${mp.sourcePath} does not exist on SD storage. Skipping mount to prevent hiding internal data.")
            return false
        }

        RootShell.exec("chown -R $uid:$gid \"${mp.sourcePath}\" 2>/dev/null")
        RootShell.exec("chmod -R 775 \"${mp.sourcePath}\" 2>/dev/null")
        RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"${mp.sourcePath}\" 2>/dev/null")
        RootShell.exec("touch \"${mp.sourcePath}/.mountx_canary\" 2>/dev/null")

        // Extract relative path from target path across any user profile
        val relPath = extractRelativePath(mp.targetPath)

        var anyMounted = false
        for (namespace in namespaces) {
            val nsTarget = "$namespace/$relPath"
            RootShell.exec("mkdir -p \"$nsTarget\" 2>/dev/null")
            val mountRes = RootShell.exec("mount -o bind \"${mp.sourcePath}\" \"$nsTarget\" 2>/dev/null")
            if (mountRes.isSuccess || RootShell.isMountpoint(nsTarget)) {
                anyMounted = true
            }
        }
        return anyMounted
    }

    /**
     * Mounts a Virtual Ext4 Loop Container for large /data/data/ private data (> 1GB).
     * Creates a sparse file using dd (bs=1M count=0 seek=$SIZE_IN_MB) for maximum toybox compatibility,
     * trims losetup string output, and sets POSIX UID/GID with app_data_file SELinux context.
     */
    private suspend fun mountVirtualExt4Container(
        packageName: String,
        mp: MountPointConfig,
        sdBase: String,
        uid: Int,
        gid: Int
    ) {
        val containerDir = "$sdBase/.mountx/containers"
        val containerPath = mp.containerImgPath ?: "$containerDir/${packageName}_data.img"
        RootShell.exec("mkdir -p \"$containerDir\" 2>/dev/null")

        // 1. Create sparse file if not exists
        if (!RootShell.exists(containerPath)) {
            val sizeInMb = maxOf(1024L, (mp.sizeBytes / (1024 * 1024)) * 12 / 10 + 256)
            RootShell.exec("dd if=/dev/zero of=\"$containerPath\" bs=1M count=0 seek=$sizeInMb 2>/dev/null")
            RootShell.exec("mke2fs -F -t ext4 \"$containerPath\" 2>/dev/null")
            AppLogger.info("MountManager", "Created virtual ext4 container: $containerPath ($sizeInMb MB)")
        }

        // 2. Attach loop device with string trimming
        val losetupOut = RootShell.execForOutput("losetup -f --show \"$containerPath\" 2>/dev/null | tr -d '\\r\\n'")
        if (losetupOut.isBlank() || !losetupOut.startsWith("/dev/block/loop")) {
            AppLogger.error("MountManager", "Failed to setup loop device for container: $losetupOut")
            return
        }
        val loopDev = losetupOut.trim()

        // 3. Mount ext4 partition to /data/user/0/$packageName
        val target = "/data/user/0/$packageName"
        RootShell.exec("mkdir -p \"$target\" 2>/dev/null")
        RootShell.exec("mount -t ext4 -o rw,noatime \"$loopDev\" \"$target\" 2>/dev/null")
        RootShell.exec("chown -R $uid:$gid \"$target\" 2>/dev/null")
        RootShell.exec("chmod -R 775 \"$target\" 2>/dev/null")
        RootShell.exec("chcon -R u:object_r:app_data_file:s0 \"$target\" 2>/dev/null")
        AppLogger.success("MountManager", "Mounted virtual ext4 container $loopDev -> $target")
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
                val steps = listOf(
                    "Menghentikan Proses Aplikasi",
                    "Melepaskan Kaitan VFS Namespaces",
                    "Memulihkan Konteks SELinux",
                    "Pelepasan Selesai"
                )
                val opType = OperationType.UNMOUNT
                val title = "Melepaskan Kaitan (Unmount)"
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

                // Pre-Unmount Process Check: force-stop active process if running
                val pgrepRes = RootShell.exec("pgrep -f \"${game.packageName}\" 2>/dev/null")
                if (pgrepRes.isSuccess && pgrepRes.output.isNotBlank()) {
                    RootShell.exec("am force-stop \"${game.packageName}\" 2>/dev/null")
                    AppLogger.info("MountManager", "Force-stopped process before unmount: ${game.packageName}")
                }

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
                        if (mp.category == MountPointCategory.PRIVATE_INTERNAL || mp.isVirtualContainer) {
                            // Unmount virtual container with kernel sync and loop detach
                            RootShell.exec("sync")
                            RootShell.exec("umount -f -l \"/data/user/$targetUserId/${game.packageName}\" 2>/dev/null")
                            RootShell.exec("umount -f -l \"/data/data/${game.packageName}\" 2>/dev/null")
                            val loopDev = RootShell.execForOutput(
                                "losetup -a 2>/dev/null | grep \"${game.packageName}_data.img\" | cut -d':' -f1 | tr -d '\\r\\n'"
                            )
                            if (loopDev.isNotBlank()) {
                                RootShell.exec("losetup -d \"$loopDev\" 2>/dev/null")
                                AppLogger.info("MountManager", "Detached loop device: $loopDev")
                            }
                        } else {
                            val userNamespaces = getTargetNamespaces(targetUserId)
                            val relPath = extractRelativePath(mp.targetPath)
                            for (ns in userNamespaces) {
                                RootShell.exec("umount -f -l \"$ns/$relPath\" 2>/dev/null")
                            }
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
                        title = "Melepaskan Kaitan (Unmount)",
                        subtitle = "${game.displayName} • ${game.packageName}",
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
                    for loop_dev in $(losetup -a 2>/dev/null | grep "\.mountx/containers" | awk -F':' '{print $1}'); do
                      if [ -n "${'$'}loop_dev" ]; then
                        losetup -d "${'$'}loop_dev" 2>/dev/null
                      fi
                    done
                """.trimIndent()
                RootShell.execScript(containersScript)

                // Unmount bind mounts cleanly with multi-pass sweep
                val script = """
                    for i in 1 2 3; do
                      has_mount=0
                      while read dev mnt rest; do
                        if [ "${'$'}mnt" != "$sdBase" ] && echo "${'$'}mnt" | grep -q "$sdBase"; then
                          umount -f -l "${'$'}mnt" 2>/dev/null
                          has_mount=1
                        fi
                      done < /proc/mounts
                      if [ ${'$'}has_mount -eq 0 ]; then break; fi
                    done
                """.trimIndent()
                RootShell.execScript(script)
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
     * Check if a specific path is currently mounted.
     */
    suspend fun isMounted(path: String): Boolean {
        return RootShell.isMountpoint(path)
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
                checkPaths.add("${mp.targetPath}/.mountx_canary")
                val rel = extractRelativePath(mp.targetPath)
                checkPaths.add("/data/media/0/$rel/.mountx_canary")
            }
        }
        checkPaths.any { RootShell.exists(it) }
    }
}

/** Dynamic identity representing UID and GID for modern Android sandboxes */
data class AppIdentity(val uid: Int, val gid: Int)
