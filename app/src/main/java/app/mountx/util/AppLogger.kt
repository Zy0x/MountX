package app.mountx.util

import app.mountx.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-time unified logging engine for MountX.
 * Appends formatted log entries to persistent logs:
 * - /storage/emulated/0/mountx.log
 * - /data/adb/modules/MountX/mountx.log
 * - /data/adb/modules/Mountify/mountify.log (backward compatibility)
 * - /storage/emulated/0/mountify.log (backward compatibility)
 */
object AppLogger {

    private val loggerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private const val USER_LOG_FILE = "/storage/emulated/0/mountx.log"
    private const val MOD_LOG_FILE = "/data/adb/modules/MountX/mountx.log"
    private const val LEGACY_USER_LOG = "/storage/emulated/0/mountify.log"
    private const val LEGACY_MOD_LOG = "/data/adb/modules/Mountify/mountify.log"

    fun info(tag: String, message: String) = log("INFO ", tag, message)
    fun success(tag: String, message: String) = log("SUCCESS", tag, message)
    fun warn(tag: String, message: String) = log("WARN ", tag, message)
    fun error(tag: String, message: String) = log("ERROR", tag, message)
    fun debug(tag: String, message: String) = log("DEBUG", tag, message)

    private fun log(level: String, tag: String, message: String) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val sanitizedMsg = message.replace("\"", "'").replace("\n", " ")
        val formattedLine = "[$timestamp] [$level] [$tag] $sanitizedMsg"

        loggerScope.launch {
            val cmd = buildString {
                append("echo \"").append(formattedLine).append("\" >> \"").append(USER_LOG_FILE).append("\" 2>/dev/null; ")
                append("echo \"").append(formattedLine).append("\" >> \"").append(MOD_LOG_FILE).append("\" 2>/dev/null; ")
                append("echo \"").append(formattedLine).append("\" >> \"").append(LEGACY_USER_LOG).append("\" 2>/dev/null; ")
                append("echo \"").append(formattedLine).append("\" >> \"").append(LEGACY_MOD_LOG).append("\" 2>/dev/null")
            }
            RootShell.exec(cmd)
        }
    }
}
