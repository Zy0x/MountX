package app.mountx.util

import android.util.Log
import app.mountx.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-time unified logging engine for MountX.
 * - Immediately writes to android.util.Log (logcat) for real-time debugging.
 * - Asynchronously appends to persistent log files via a Channel queue so file
 *   writes never block the caller coroutine (prevents storage breakdown delay).
 * Persistent logs:
 * - /storage/emulated/0/mountx.log
 * - /data/adb/modules/MountX/mountx.log (or /data/adb/modules/Mountify/mountx.log fallback)
 */
object AppLogger {

    private val loggerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    // Channel buffers log lines so file-writes are fully decoupled from callers
    private val logChannel = Channel<String>(capacity = Channel.UNLIMITED)

    private const val USER_LOG_FILE = "/storage/emulated/0/mountx.log"
    private const val MOD_LOG_FILE = "/data/adb/modules/MountX/mountx.log"
    private const val FALLBACK_MOD_LOG = "/data/adb/modules/Mountify/mountx.log"

    init {
        // Background consumer: writes directly to file without root when possible,
        // and batches queued lines into a single root script execution to prevent shell locking.
        loggerScope.launch {
            for (firstLine in logChannel) {
                try {
                    val batch = mutableListOf(firstLine)
                    while (true) {
                        val next = logChannel.tryReceive().getOrNull() ?: break
                        batch.add(next)
                    }

                    val userFile = java.io.File(USER_LOG_FILE)
                    var directWritten = 0
                    try {
                        userFile.appendText(batch.joinToString("\n", postfix = "\n"))
                        directWritten = batch.size
                    } catch (_: Exception) {}

                    // Also mirror to Magisk module log if directory exists
                    val modDirMountX = java.io.File("/data/adb/modules/MountX")
                    val modDirMountify = java.io.File("/data/adb/modules/Mountify")
                    if (modDirMountX.exists() || modDirMountify.exists() || directWritten < batch.size) {
                        val script = batch.joinToString("\n") { line ->
                            val safe = line.replace("\"", "'")
                            val userAppend = if (directWritten < batch.size) "echo \"$safe\" >> \"$USER_LOG_FILE\" 2>/dev/null; " else ""
                            "$userAppend" +
                            "if [ -d /data/adb/modules/MountX ]; then echo \"$safe\" >> \"$MOD_LOG_FILE\" 2>/dev/null; " +
                            "elif [ -d /data/adb/modules/Mountify ]; then echo \"$safe\" >> \"$FALLBACK_MOD_LOG\" 2>/dev/null; fi"
                        }
                        RootShell.execScript(script)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun info(tag: String, message: String) = log("INFO ", tag, message)
    fun success(tag: String, message: String) = log("SUCCESS", tag, message)
    fun warn(tag: String, message: String) = log("WARN ", tag, message)
    fun error(tag: String, message: String) = log("ERROR", tag, message)
    fun debug(tag: String, message: String) = log("DEBUG", tag, message)

    private fun log(level: String, tag: String, message: String) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val sanitizedMsg = message.replace("\"", "'").replace("\n", " ")
        val formattedLine = "[$timestamp] [$level] [$tag] $sanitizedMsg"

        // 1. Immediate: write to Android logcat (does NOT need root, never blocks)
        when (level.trim()) {
            "ERROR" -> Log.e("MountX/$tag", message)
            "WARN"  -> Log.w("MountX/$tag", message)
            "DEBUG" -> Log.d("MountX/$tag", message)
            else    -> Log.i("MountX/$tag", message)
        }

        // 2. Async: enqueue for root file-write (never blocks caller)
        logChannel.trySend(formattedLine)
    }
}
