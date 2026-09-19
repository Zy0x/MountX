package app.mountx.root

import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles mounting and unmounting game & application data directories using bind mounts
 * and Virtual Ext4 Loop Containers for Universal Smart Directory Classification.
 */
class MountManager {

    /** Dynamically detects all target runtime namespaces including primary and dual apps */
    private suspend fun getTargetNamespaces(): List<String> = withContext(Dispatchers.IO) {
        val base = mutableListOf(
            "/mnt/runtime/default/emulated/0",
            "/mnt/runtime/read/emulated/0",
            "/mnt/runtime/write/emulated/0",
            "/mnt/runtime/full/emulated/0",
            "/mnt/user/0/primary",
            "/storage/emulated/0",
            "/data/media/0"
        )
        if (RootShell.exists("/data/media/999")) {
            base.add("/data/media/999")
            base.add("/storage/emulated/999")
        }
        base
    }

    /**
     * Mount an application entry into runtime namespaces.
     * Supports both Multi-Target Array (v2.2.13) and legacy mode fallback.
     */
    suspend fun mountGame(game: GameEntry, sdBase: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Dynamic UID/GID resolution directly from stat /data/data/$pkg
                val identity = resolveAppIdentity(game.packageName)
                val uid = identity.uid
                val gid = identity.gid

                // Set ownership on internal data directory
                RootShell.exec("chown -R $uid:$gid \"/data/user/0/${game.packageName}\" 2>/dev/null")
                RootShell.exec("chmod -R 775 \"/data/user/0/${game.packageName}\" 2>/dev/null")

                val namespaces = getTargetNamespaces()

                if (game.mountPoints.isNotEmpty()) {
                    // ── UNIVERSAL SMART MULTI-TARGET ARRAY ──
                    for (mp in game.mountPoints) {
                        if (!mp.enabled) continue

                        // Security Hard-Lock: reject /data/app unless explicitly marked as APP_PACKAGE
                        if ((mp.targetPath.startsWith("/data/app") || mp.sourcePath.startsWith("/data/app")) && mp.category != MountPointCategory.APP_PACKAGE) {
                            AppLogger.error("MountManager", "Security violation: blocked unverified mount target in /data/app (${mp.id})")
                            continue
                        }

                        when (mp.category) {
                            MountPointCategory.EXTERNAL_DATA, MountPointCategory.OBB_STORAGE, MountPointCategory.GAME_ASSETS -> {
                                mountDirectoryTarget(mp, uid, gid, namespaces, isMedia = false)
                            }
                            MountPointCategory.MEDIA_DOWNLOADS -> {
                                // Auto .nomedia placement in source directory on MicroSD to protect MediaStore
                                RootShell.exec("mkdir -p \"${mp.sourcePath}\" 2>/dev/null")
                                RootShell.exec("touch \"${mp.sourcePath}/.nomedia\" 2>/dev/null")
                                mountDirectoryTarget(mp, uid, gid, namespaces, isMedia = true)
                            }
                            MountPointCategory.CACHE_SHADERS -> {
                                mountDirectoryTarget(mp, uid, gid, namespaces, isMedia = false)
                            }
                            MountPointCategory.CUSTOM -> {
                                mountDirectoryTarget(mp, uid, gid, namespaces, isMedia = false)
                            }
                            MountPointCategory.PRIVATE_INTERNAL -> {
                                mountVirtualExt4Container(game.packageName, mp, sdBase, uid, gid)
                            }
                            MountPointCategory.APP_PACKAGE -> {
                                RootShell.exec("mkdir -p \"${mp.sourcePath}\" 2>/dev/null")
                                RootShell.exec("mount -o bind,exec \"${mp.sourcePath}\" \"${mp.targetPath}\"")
                                for (ns in namespaces) {
                                    RootShell.exec("nsenter --mount=\"$ns\" mount -o bind,exec \"${mp.sourcePath}\" \"${mp.targetPath}\" 2>/dev/null")
                                }
                                RootShell.exec("restorecon -FR \"${mp.targetPath}\" 2>/dev/null")
                            }
                        }
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

                        for (namespace in namespaces) {
                            val targetPath = "$namespace/$obbRelPath"
                            RootShell.exec("[ -d \"$namespace/Android\" ] && mkdir -p \"$namespace/Android/obb\" \"$targetPath\" 2>/dev/null")
                            RootShell.exec("[ -d \"$namespace/Android\" ] && mount -o bind \"$obbSrcPath\" \"$targetPath\" 2>/dev/null")
                        }
                    }
                }
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
    ) {
        if (!RootShell.exists(mp.sourcePath)) {
            RootShell.exec("mkdir -p \"${mp.sourcePath}\" 2>/dev/null")
        }

        RootShell.exec("chown -R $uid:$gid \"${mp.sourcePath}\" 2>/dev/null")
        RootShell.exec("chmod -R 775 \"${mp.sourcePath}\" 2>/dev/null")
        RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"${mp.sourcePath}\" 2>/dev/null")
        RootShell.exec("touch \"${mp.sourcePath}/.mountx_canary\" 2>/dev/null")

        // Extract relative path from target path (e.g. /data/media/0/Android/data/... -> Android/data/...)
        val relPath = mp.targetPath
            .removePrefix("/data/media/0/")
            .removePrefix("/storage/emulated/0/")
            .removePrefix("/mnt/user/0/primary/")
            .removePrefix("/")

        for (namespace in namespaces) {
            val nsTarget = "$namespace/$relPath"
            RootShell.exec("mkdir -p \"$nsTarget\" 2>/dev/null")
            RootShell.exec("mount -o bind \"${mp.sourcePath}\" \"$nsTarget\" 2>/dev/null")
        }
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
    suspend fun unmountGame(game: GameEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Pre-Unmount Process Check: force-stop active process if running
                val pgrepRes = RootShell.exec("pgrep -f \"${game.packageName}\" 2>/dev/null")
                if (pgrepRes.isSuccess && pgrepRes.output.isNotBlank()) {
                    RootShell.exec("am force-stop \"${game.packageName}\" 2>/dev/null")
                    AppLogger.info("MountManager", "Force-stopped process before unmount: ${game.packageName}")
                }

                val namespaces = getTargetNamespaces()

                if (game.mountPoints.isNotEmpty()) {
                    for (mp in game.mountPoints) {
                        if (mp.category == MountPointCategory.PRIVATE_INTERNAL || mp.isVirtualContainer) {
                            // Unmount virtual container with kernel sync and loop detach
                            RootShell.exec("sync")
                            RootShell.exec("umount -f -l \"/data/user/0/${game.packageName}\" 2>/dev/null")
                            RootShell.exec("umount -f -l \"/data/data/${game.packageName}\" 2>/dev/null")
                            val loopDev = RootShell.execForOutput(
                                "losetup -a 2>/dev/null | grep \"${game.packageName}_data.img\" | cut -d':' -f1 | tr -d '\\r\\n'"
                            )
                            if (loopDev.isNotBlank()) {
                                RootShell.exec("losetup -d \"$loopDev\" 2>/dev/null")
                                AppLogger.info("MountManager", "Detached loop device: $loopDev")
                            }
                        } else {
                            val relPath = mp.targetPath
                                .removePrefix("/data/media/0/")
                                .removePrefix("/storage/emulated/0/")
                                .removePrefix("/mnt/user/0/primary/")
                                .removePrefix("/")
                            for (ns in namespaces) {
                                RootShell.exec("umount -f -l \"$ns/$relPath\" 2>/dev/null")
                            }
                        }
                    }
                }

                // Fallback unmount standard default paths
                val legacyRelPaths = listOf(
                    "Android/data/${game.packageName}/files",
                    "Android/data/${game.packageName}",
                    "Android/obb/${game.packageName}"
                )
                for (namespace in namespaces) {
                    for (rel in legacyRelPaths) {
                        RootShell.exec("umount -f -l \"$namespace/$rel\" 2>/dev/null")
                    }
                }
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
                    for loop_dev in $(losetup -a 2>/dev/null | grep "\.mountx/containers" | cut -d':' -f1 | tr -d '\r'); do
                        if [ -n "${'$'}loop_dev" ]; then
                            sync
                            umount -f -l "${'$'}loop_dev" 2>/dev/null
                            losetup -d "${'$'}loop_dev" 2>/dev/null
                        fi
                    done
                """.trimIndent()
                RootShell.execScript(containersScript)

                // Unmount bind mounts
                val script = """
                    for m in $(grep "$sdBase" /proc/mounts 2>/dev/null | cut -d' ' -f2); do
                        if [ "${'$'}m" != "$sdBase" ]; then
                            umount -f -l "${'$'}m" 2>/dev/null
                        fi
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
        RootShell.exec("cat /proc/mounts 2>/dev/null")
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
     * Resolves dynamic UID and GID directly from stat /data/data/$packageName
     * to ensure full compatibility with Android 11-14+ app isolation.
     */
    suspend fun resolveAppIdentity(packageName: String): AppIdentity = withContext(Dispatchers.IO) {
        val statRes = RootShell.exec(
            "stat -c \"%u %g\" \"/data/data/$packageName\" 2>/dev/null || " +
            "stat -c \"%u %g\" \"/data/user/0/$packageName\" 2>/dev/null"
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
        AppIdentity(fallbackUid, fallbackUid)
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
     */
    suspend fun verifyCanary(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val primaryCanary = "/storage/emulated/0/Android/data/$packageName/.mountx_canary"
        val dataMediaCanary = "/data/media/0/Android/data/$packageName/.mountx_canary"
        RootShell.exists(primaryCanary) || RootShell.exists(dataMediaCanary)
    }
}

/** Dynamic identity representing UID and GID for modern Android sandboxes */
data class AppIdentity(val uid: Int, val gid: Int)
