package app.mountify.root

import app.mountify.data.model.ShellResult
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton wrapper around libsu Shell for executing root commands.
 */
object RootShell {

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
     * @param cmd The command string to execute
     */
    suspend fun exec(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        val result = Shell.cmd(cmd).exec()
        ShellResult(
            stdout = result.out,
            stderr = result.err,
            code = if (result.isSuccess) 0 else 1
        )
    }

    /**
     * Execute multiple commands as a script block.
     * @param script Multi-line shell script
     */
    suspend fun execScript(script: String): ShellResult = withContext(Dispatchers.IO) {
        val lines = script.trim().lines().filter { it.isNotBlank() }
        val result = Shell.cmd(*lines.toTypedArray()).exec()
        ShellResult(
            stdout = result.out,
            stderr = result.err,
            code = if (result.isSuccess) 0 else 1
        )
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
