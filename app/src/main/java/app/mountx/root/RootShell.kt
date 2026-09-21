package app.mountx.root

import app.mountx.data.model.ShellResult
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
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

    /** Returns true if root shell is available, initializing shell session if needed */
    val isAvailable: Boolean
        get() {
            val cached = Shell.isAppGrantedRoot()
            if (cached != null) return cached
            return try {
                Shell.getShell().isRoot
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Execute a shell command with root and return the result.
     * Enforces a hard timeout (default 15 seconds) to prevent kernel/daemon deadlocks.
     * @param cmd The command string to execute
     * @param timeoutMillis Hard timeout in milliseconds (default 15000 ms)
     */
    suspend fun exec(cmd: String, timeoutMillis: Long = 15000L): ShellResult = withContext(Dispatchers.IO) {
        val timedResult = withTimeoutOrNull(timeoutMillis) {
            val result = Shell.cmd(cmd).exec()
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
     */
    suspend fun execStreaming(
        cmd: String,
        timeoutMillis: Long = 600000L,
        onStdoutLine: (String) -> Unit
    ): ShellResult = withContext(Dispatchers.IO) {
        val timedResult = withTimeoutOrNull(timeoutMillis) {
            val outList = object : com.topjohnwu.superuser.CallbackList<String>() {
                override fun onAddElement(s: String?) {
                    if (s != null) {
                        onStdoutLine(s)
                    }
                }
            }
            val errList = ArrayList<String>()
            val result = Shell.cmd(cmd).to(outList, errList).exec()
            ShellResult(
                stdout = outList,
                stderr = errList,
                code = if (result.isSuccess) 0 else 1
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
            val lines = script.trim().lines().filter { it.isNotBlank() }
            val result = Shell.cmd(*lines.toTypedArray()).exec()
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
     * Check if a path is a mounted mountpoint.
     */
    suspend fun isMountpoint(path: String): Boolean {
        return exec("mountpoint -q \"$path\" && echo 1 || echo 0").output.trim() == "1"
    }
}
