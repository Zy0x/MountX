package app.mountify.root

import app.mountify.data.model.GameEntry
import app.mountify.data.model.MountMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Root namespaces to bind-mount game data into */
private val MOUNT_NAMESPACES = listOf(
    "/mnt/runtime/default/emulated/0",
    "/mnt/runtime/read/emulated/0",
    "/mnt/runtime/write/emulated/0",
    "/mnt/runtime/full/emulated/0",
    "/mnt/user/0/primary",
    "/storage/emulated/0",
    "/data/media/0"
)

/**
 * Handles mounting and unmounting game data directories using bind mounts.
 */
class MountManager {

    /**
     * Mount a single game's data from MicroSD into all runtime namespaces.
     * @param game The game entry to mount
     * @param sdBase The MicroSD mount point (e.g. /data/sdext2)
     */
    suspend fun mountGame(game: GameEntry, sdBase: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
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

                // Get app UID
                val uid = getGameUid(game.packageName)

                // Set ownership on internal data dir
                RootShell.exec("chown -R $uid:$uid \"/data/user/0/${game.packageName}\" 2>/dev/null")
                RootShell.exec("chmod -R 775 \"/data/user/0/${game.packageName}\" 2>/dev/null")

                if (game.mode == MountMode.FILES) {
                    // Allow databases to stay on internal
                    RootShell.exec(
                        "chmod 771 \"/data/user/0/${game.packageName}/databases\" 2>/dev/null"
                    )
                }

                // Bind mount Android/data if available on SD
                if (hasData) {
                    RootShell.exec("chown -R $uid:1023 \"$sdBase/Android/data/${game.packageName}\" 2>/dev/null")
                    RootShell.exec("chmod -R 777 \"$sdBase/Android/data/${game.packageName}\" 2>/dev/null")
                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$sdBase/Android/data/${game.packageName}\" 2>/dev/null")

                    for (namespace in MOUNT_NAMESPACES) {
                        val targetPath = "$namespace/$dataRelPath"
                        RootShell.exec("[ -d \"$namespace/Android/data\" ] && mkdir -p \"$targetPath\" 2>/dev/null")
                        RootShell.exec("[ -d \"$namespace/Android/data\" ] && mount -o bind \"$dataSrcPath\" \"$targetPath\" 2>/dev/null")
                    }
                }

                // Bind mount Android/obb if available on SD
                if (hasObb) {
                    RootShell.exec("chown -R $uid:1023 \"$obbSrcPath\" 2>/dev/null")
                    RootShell.exec("chmod -R 777 \"$obbSrcPath\" 2>/dev/null")
                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$obbSrcPath\" 2>/dev/null")

                    for (namespace in MOUNT_NAMESPACES) {
                        val targetPath = "$namespace/$obbRelPath"
                        RootShell.exec("[ -d \"$namespace/Android\" ] && mkdir -p \"$namespace/Android/obb\" \"$targetPath\" 2>/dev/null")
                        RootShell.exec("[ -d \"$namespace/Android\" ] && mount -o bind \"$obbSrcPath\" \"$targetPath\" 2>/dev/null")
                    }
                }
            }
        }

    /**
     * Unmount a single game from all runtime namespaces (both data and obb).
     */
    suspend fun unmountGame(game: GameEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val relPaths = listOf(
                    "Android/data/${game.packageName}/files",
                    "Android/data/${game.packageName}",
                    "Android/obb/${game.packageName}"
                )
                for (namespace in MOUNT_NAMESPACES) {
                    for (rel in relPaths) {
                        RootShell.exec("umount -f -l \"$namespace/$rel\" 2>/dev/null")
                    }
                }
            }
        }

    /**
     * Unmount all bind-mounted paths from the given sdBase.
     */
    suspend fun unmountAll(sdBase: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
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
}
