package app.mountx.root

import app.mountx.data.model.ShellResult
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import app.mountx.util.AppLogger

/**
 * Singleton wrapper around libsu Shell for executing root commands.
 */
object RootShell {

    /**
     * Centralized execution mutex to synchronize high-level shell operations
     * (mount, unmount, rsync/cp, migration, registry sync) and prevent concurrent execution races.
     */
    val rootExecutionMutex = Mutex()

    /**
     * Common su binary locations across all Android root solutions, including
     * TempRoot exploits (mtk-su, CVEs) located at /dev/su or /data/local/tmp/su.
     */
    val SU_CANDIDATES = listOf(
        "su",
        "/dev/su",
        "/data/local/tmp/su",
        "/sbin/su",
        "/system/xbin/su",
        "/system/bin/su"
    )

    @Volatile
    private var resolvedSuPath: String? = null

    /**
     * Resolves the active 'su' binary candidate on the device.
     * Essential for TempRoot where 'su' is placed in non-standard paths like /dev/su or /data/local/tmp/su.
     */
    fun resolveSuBinary(forceRefresh: Boolean = false): String {
        if (!forceRefresh) {
            resolvedSuPath?.let { return it }
        }
        for (candidate in SU_CANDIDATES) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf(candidate, "-c", "id"))
                val out = p.inputStream.bufferedReader().use { it.readText() }
                if (p.waitFor() == 0 && out.contains("uid=0")) {
                    resolvedSuPath = candidate
                    return candidate
                }
            } catch (_: Exception) {}
        }
        return "su"
    }

    /**
     * Directly tests if 'su' binary responds and grants root to this app UID.
     * Bypasses any libsu caching bugs or stale non-root state.
     */
    fun checkSuDirect(): Boolean {
        return try {
            val suBin = resolveSuBinary(forceRefresh = true)
            val process = Runtime.getRuntime().exec(arrayOf(suBin, "-c", "id"))
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            exitCode == 0 && stdout.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resets libsu's internal cached main shell and static confirmation state.
     * When libsu boots before root permission is granted, it permanently caches
     * a non-root 'sh' instance and locks Utils.currentRootState = 0 (false).
     * This method resets those internals via reflection so libsu is forced to spawn a true root shell.
     */
    fun resetLibsuShell(): Boolean {
        return try {
            try {
                Shell.getCachedShell()?.close()
            } catch (_: Exception) {}

            // 1. Clear MainShell.mainShell[0]
            try {
                val mainShellClass = Class.forName("com.topjohnwu.superuser.internal.MainShell")
                val mainShellField = mainShellClass.getDeclaredField("mainShell")
                mainShellField.isAccessible = true
                val array = mainShellField.get(null) as? Array<*>
                if (array != null && array.isNotEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    (array as Array<Any?>)[0] = null
                }
            } catch (e: Exception) {
                AppLogger.warn("RootShell", "Reflection clear mainShell: ${e.message}")
            }

            // 2. Reset Utils.currentRootState to -1 (UNKNOWN) so libsu re-queries su
            try {
                val utilsClass = Class.forName("com.topjohnwu.superuser.internal.Utils")
                val rootStateField = utilsClass.getDeclaredField("currentRootState")
                rootStateField.isAccessible = true
                rootStateField.setInt(null, -1)
            } catch (e: Exception) {
                AppLogger.warn("RootShell", "Reflection reset currentRootState: ${e.message}")
            }

            // 3. Rebuild main shell
            val shell = Shell.getShell()
            AppLogger.info("RootShell", "Libsu shell rebuilt: isRoot=${shell.isRoot}, status=${shell.status}")
            shell.isRoot
        } catch (e: Exception) {
            AppLogger.error("RootShell", "resetLibsuShell failed: ${e.message}")
            false
        }
    }

    /**
     * Checks if root shell is available.
     * When forceRefresh is true or when root was not previously granted,
     * directly verifies 'su' at OS level and re-elevates libsu's main shell.
     */
    fun isRootAvailable(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh) {
            val cached = Shell.getCachedShell()
            if (cached != null && cached.isRoot && Shell.isAppGrantedRoot() == true) {
                return true
            }
        }

        // Direct OS-level su verification
        val hasDirectSu = checkSuDirect()
        if (!hasDirectSu) {
            return false
        }

        // su is granted! Ensure libsu's main shell is elevated
        val currentCached = Shell.getCachedShell()
        if (currentCached == null || !currentCached.isRoot) {
            resetLibsuShell()
        }

        val activeShell = Shell.getCachedShell()
        return activeShell != null && activeShell.isRoot
    }

    /** Returns true if root shell is available, initializing shell session if needed */
    val isAvailable: Boolean
        get() = isRootAvailable(forceRefresh = false)

    private const val ENV_SETUP = "case \":\$PATH:\" in *\":/dev/mountx_bin:\"*) ;; *) export PATH=\"/dev/mountx_bin:/data/adb/modules/MountX/bin:/data/adb/modules/mountx/bin:/data/adb/ap/modules/MountX/bin:/data/adb/ksu/modules/MountX/bin:/data/adb/ap/bin:/data/adb/magisk:\$PATH\" ;; esac; "

    /**
     * Execute a shell command with root and return the result.
     * Enforces a hard timeout (default 15 seconds) to prevent kernel/daemon deadlocks.
     * @param cmd The command string to execute
     * @param timeoutMillis Hard timeout in milliseconds (default 15000 ms)
     */
    suspend fun exec(cmd: String, timeoutMillis: Long = 15000L): ShellResult = withContext(Dispatchers.IO) {
        val cached = Shell.getCachedShell()
        if (cached == null || !cached.isRoot) {
            if (checkSuDirect()) {
                resetLibsuShell()
            }
        }

        val timedResult = withTimeoutOrNull(timeoutMillis) {
            val result = Shell.cmd("$ENV_SETUP$cmd").exec()
            ShellResult(
                stdout = result.out,
                stderr = result.err,
                code = if (result.isSuccess) 0 else 1
            )
        }

        if (timedResult == null) {
            AppLogger.error("RootShell", "Command timed out after ${timeoutMillis}ms: $cmd")
            ShellResult(
                stdout = emptyList(),
                stderr = listOf("MountX: Command timed out after ${timeoutMillis}ms: $cmd"),
                code = 124
            )
        } else {
            timedResult
        }
    }

    /**
     * Execute a shell command and stream stdout lines in real-time.
     * Uses an isolated, dedicated OS root process to prevent locking the global libsu Shell
     * and guarantees all stdout reading happens strictly on Dispatchers.IO (zero UI thread ANR).
     */
    suspend fun execStreaming(
        cmd: String,
        timeoutMillis: Long = 600000L,
        onStdoutLine: (String) -> Unit
    ): ShellResult = withContext(Dispatchers.IO) {
        val timedResult = withTimeoutOrNull(timeoutMillis) {
            val stdoutList = mutableListOf<String>()
            val stderrList = mutableListOf<String>()
            var exitCode = 1
            var process: Process? = null

            try {
                val suBin = resolveSuBinary()
                val fullCmd = "$ENV_SETUP$cmd"
                val suCmds = listOf(
                    arrayOf(suBin, "-M", "-c", fullCmd),
                    arrayOf(suBin, "-mm", "-c", fullCmd),
                    arrayOf(suBin, "-c", fullCmd)
                )

                for (suCmd in suCmds) {
                    try {
                        process = Runtime.getRuntime().exec(suCmd)
                        break
                    } catch (_: Exception) {}
                }

                if (process == null) {
                    AppLogger.error("RootShell", "Failed to spawn standalone su process for execStreaming")
                    return@withTimeoutOrNull ShellResult(emptyList(), listOf("Failed to spawn su process"), 1)
                }

                val p = process
                val errReaderJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    try {
                        p.errorStream.bufferedReader().useLines { lines ->
                            lines.forEach { stderrList.add(it) }
                        }
                    } catch (_: Exception) {}
                }

                p.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        stdoutList.add(line)
                        onStdoutLine(line)
                    }
                }

                errReaderJob.join()
                exitCode = p.waitFor()
            } catch (e: Exception) {
                AppLogger.error("RootShell", "execStreaming error: ${e.message}")
                stderrList.add(e.message ?: "Unknown error")
                try { process?.destroyForcibly() } catch (_: Exception) {}
            } finally {
                try { process?.destroy() } catch (_: Exception) {}
            }

            ShellResult(
                stdout = stdoutList,
                stderr = stderrList,
                code = exitCode
            )
        }

        if (timedResult == null) {
            AppLogger.error("RootShell", "Streaming command timed out after ${timeoutMillis}ms: $cmd")
            ShellResult(
                stdout = emptyList(),
                stderr = listOf("MountX: Streaming command timed out after ${timeoutMillis}ms"),
                code = 124
            )
        } else {
            timedResult
        }
    }

    /**
     * Execute multiple commands as a script block.
     * @param script Multi-line shell script
     * @param timeoutMillis Hard timeout in milliseconds (default 30000 ms)
     */
    suspend fun execScript(script: String, timeoutMillis: Long = 30000L): ShellResult = withContext(Dispatchers.IO) {
        val timedResult = withTimeoutOrNull(timeoutMillis) {
            val scriptCmd = "$ENV_SETUP sh << 'MOUNTX_SCRIPT_EOF'\n${script.trim()}\nMOUNTX_SCRIPT_EOF"
            val result = Shell.cmd(scriptCmd).exec()
            ShellResult(
                stdout = result.out,
                stderr = result.err,
                code = if (result.isSuccess) 0 else 1
            )
        }

        if (timedResult == null) {
            AppLogger.error("RootShell", "Script execution timed out after ${timeoutMillis}ms")
            ShellResult(
                stdout = emptyList(),
                stderr = listOf("MountX: Script execution timed out after ${timeoutMillis}ms"),
                code = 124
            )
        } else {
            timedResult
        }
    }

    /**
     * Execute a command and return stdout as a single string.
     */
    suspend fun execForOutput(cmd: String): String {
        return exec(cmd).output.trim()
    }

    /**
     * Check if a file or directory exists via root.
     */
    suspend fun exists(path: String): Boolean {
        return exec("[ -e \"$path\" ] && echo 1 || echo 0").output.trim() == "1"
    }

    /**
     * Batch check existence of multiple paths in a single root shell command.
     * Returns the Set of paths that actually exist on disk.
     */
    suspend fun pathsExistBatch(paths: Collection<String>): Set<String> = withContext(Dispatchers.IO) {
        val validPaths = paths.map { it.trim().trim('\"') }.filter { it.isNotBlank() }.distinct()
        if (validPaths.isEmpty()) return@withContext emptySet()
        val existing = mutableSetOf<String>()
        validPaths.chunked(50).forEach { chunk ->
            val targets = chunk.joinToString(" ") { "\"$it\"" }
            val res = exec("ls -d $targets 2>/dev/null")
            for (line in res.stdout) {
                val p = line.trim()
                if (p.isNotBlank()) {
                    existing.add(p)
                }
            }
        }
        existing
    }

    /**
     * Builds a portable shell snippet that converts [path] (and subtree) to rprivate propagation.
     * Android's toybox `mount` lacks `--make-rprivate` (prints "bad /etc/fstab"), so prefer bundled BusyBox.
     */
    fun makeRprivateCmd(path: String): String {
        val escaped = path.replace("\"", "\\\"")
        return "(busybox mount --make-rprivate \"$escaped\" 2>/dev/null || mount --make-rprivate \"$escaped\" 2>/dev/null)"
    }

    /**
     * Builds a portable POSIX shell expression that evaluates true iff [doubleQuotedTarget] is
     * currently an active mountpoint in [mountInfoPath] (falling back to /proc/self/mountinfo and
     * `mountpoint -q`). Immune to same-backing-device `st_dev` collisions on nested bind mounts.
     */
    fun shellIsMountedExpr(doubleQuotedTarget: String, mountInfoPath: String = "/proc/1/mountinfo"): String {
        return "(T=\"$doubleQuotedTarget\"; _mi=\"$mountInfoPath\"; [ -r \"\$_mi\" ] || _mi=/proc/self/mountinfo; if [ -r \"\$_mi\" ]; then T=\"\$T\" awk '{m=\$5;gsub(/\\\\040/,\" \",m);if(m==ENVIRON[\"T\"]){f=1;exit}} END{exit !f}' \"\$_mi\" 2>/dev/null; else mountpoint -q \"\$T\" 2>/dev/null; fi)"
    }

    /**
     * Check if a path is an active mountpoint by inspecting /proc/1/mountinfo (falling back to
     * /proc/self/mountinfo and `mountpoint -q`). Parsing mountinfo avoids Toybox/BusyBox `mountpoint`
     * false negatives when a bind submount resides on the same backing block device (identical st_dev)
     * as its parent mount (e.g. PRIVATE_INTERNAL + CACHE_SHADERS or APP_PACKAGE + LIB on MicroSD).
     */
    suspend fun isMountpoint(path: String): Boolean {
        val clean = path.trim().trimEnd('/').ifEmpty { "/" }
        val escaped = clean.replace("'", "'\\''")
        val cmd = """
            _p='$escaped'
            _rp=$(readlink -f "${'$'}_p" 2>/dev/null)
            _rp=${'$'}{_rp%/}
            [ -z "${'$'}_rp" ] && _rp="${'$'}_p"
            _mi=/proc/1/mountinfo
            [ -r "${'$'}_mi" ] || _mi=/proc/self/mountinfo
            if [ -r "${'$'}_mi" ]; then
                T1="${'$'}_p" T2="${'$'}_rp" awk '{ m=$5; gsub(/\\040/, " ", m); if (m == ENVIRON["T1"] || m == ENVIRON["T2"]) { f=1; exit } } END { exit !f }' "${'$'}_mi" 2>/dev/null && echo 1 || echo 0
            else
                mountpoint -q "${'$'}_p" && echo 1 || echo 0
            fi
        """.trimIndent()
        return exec(cmd).output.trim() == "1"
    }

    /**
     * Atomically writes string content to a target file via root using Base64 decoding.
     * Prevents shell escaping glitches, argument buffer limits, and newline/quote corruption.
     */
    suspend fun writeFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val b64 = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val res = exec("echo '$b64' | base64 -d > \"$path\" && sync")
        res.isSuccess
    }

    /**
     * Waits until any active service.sh boot or unlock mount cycle releases its lock files
     * (/dev/.mountx_boot_active or /dev/.mountx_apply.lock) with a live PID check.
     */
    suspend fun awaitServiceMountCycleIfActive(
        waitForWatchdogUnlockGrace: Boolean = false,
        timeoutMillis: Long = 45_000L
    ): Unit = withContext(Dispatchers.IO) {
        val probeCmd = """
            _active=0
            for _lf in /dev/.mountx_boot_active /dev/.mountx_apply.lock; do
                if [ -f "${'$'}_lf" ]; then
                    _lpid=$(cat "${'$'}_lf" 2>/dev/null | tr -d ' \r\t\n')
                    if [ -n "${'$'}_lpid" ] && [ -d "/proc/${'$'}_lpid" ]; then
                        _active=1
                    else
                        rm -f "${'$'}_lf" 2>/dev/null || true
                    fi
                fi
            done
            _init=0
            [ -f /dev/.mountx_initial_mounted ] && _init=1
            _wd=0
            if pgrep -f 'service.sh' >/dev/null 2>&1; then _wd=1; fi
            echo "${'$'}_active|${'$'}_init|${'$'}_wd"
        """.trimIndent()

        val start = System.currentTimeMillis()
        val graceEnd = if (waitForWatchdogUnlockGrace) start + 8_000L else start
        while (System.currentTimeMillis() - start < timeoutMillis) {
            val parts = execForOutput(probeCmd).trim().split('|')
            val isActive = parts.getOrNull(0) == "1"
            val isInitialMounted = parts.getOrNull(1) == "1"
            val isServiceAlive = parts.getOrNull(2) == "1"

            if (isActive) {
                delay(500L)
                continue
            }
            if (waitForWatchdogUnlockGrace && !isInitialMounted && isServiceAlive && System.currentTimeMillis() < graceEnd) {
                delay(500L)
                continue
            }
            break
        }
    }
}
