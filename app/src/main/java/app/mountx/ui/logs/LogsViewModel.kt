package app.mountx.ui.logs


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountx.R
import app.mountx.data.diagnostics.DiagnosticManager
import app.mountx.root.RootShell
import app.mountx.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticManager: DiagnosticManager
) : ViewModel() {

    companion object {
        const val LOG_PATH = "/data/user/0/app.mountx/files/mountx.log"
        const val MOD_LOG_PATH = "/data/adb/modules/MountX/mountx.log"
        const val ALT_MOD_LOG_PATH = "/data/adb/modules/mountx/mountx.log"
        const val LEGACY_LOG_PATH = "/storage/emulated/0/mountx.log"
        const val LEGACY_MOUNTIFY_LOG_PATH = "/storage/emulated/0/mountify.log"
        const val LEGACY_MOD_LOG_PATH = "/data/adb/modules/Mountify/mountify.log"
    }

    private val _logLines = MutableStateFlow<List<LogLine>>(emptyList())
    val logLines: StateFlow<List<LogLine>> = _logLines.asStateFlow()

    private val _isAutoRefresh = MutableStateFlow(true)
    val isAutoRefresh: StateFlow<Boolean> = _isAutoRefresh.asStateFlow()

    private val _selectedFilter = MutableStateFlow(LogLevel.ALL)
    val selectedFilter: StateFlow<LogLevel> = _selectedFilter.asStateFlow()

    private val _isGeneratingReport = MutableStateFlow(false)
    val isGeneratingReport: StateFlow<Boolean> = _isGeneratingReport.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var tailJob: kotlinx.coroutines.Job? = null

    val filteredLines: StateFlow<List<LogLine>> = combine(_logLines, _selectedFilter) { lines, filter ->
        if (filter == LogLevel.ALL) {
            lines
        } else {
            lines.filter { it.level == filter }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val levelCounts: StateFlow<Map<LogLevel, Int>> = _logLines.combine(_selectedFilter) { lines, _ ->
        val counts = mutableMapOf<LogLevel, Int>()
        counts[LogLevel.ALL] = lines.size
        counts[LogLevel.SUCCESS] = lines.count { it.level == LogLevel.SUCCESS }
        counts[LogLevel.INFO] = lines.count { it.level == LogLevel.INFO }
        counts[LogLevel.WARN] = lines.count { it.level == LogLevel.WARN }
        counts[LogLevel.ERROR] = lines.count { it.level == LogLevel.ERROR }
        counts[LogLevel.DEBUG] = lines.count { it.level == LogLevel.DEBUG }
        counts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val isWatchdogHealthy: StateFlow<Boolean> = _logLines.combine(_selectedFilter) { lines, _ ->
        val lastLines = lines.takeLast(10)
        !lastLines.any { it.level == LogLevel.ERROR && it.rawText.contains("Watchdog", ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun toggleAutoRefresh() {
        _isAutoRefresh.value = !_isAutoRefresh.value
    }

    fun setFilter(level: LogLevel) {
        _selectedFilter.value = level
    }

    fun refreshLogs() {
        viewModelScope.launch {
            readLogFile()
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                RootShell.exec("rm -f \"$LEGACY_LOG_PATH\" \"$LEGACY_MOUNTIFY_LOG_PATH\" 2>/dev/null; echo '' > \"$LOG_PATH\" 2>/dev/null; echo '' > \"$MOD_LOG_PATH\" 2>/dev/null; echo '' > \"$ALT_MOD_LOG_PATH\" 2>/dev/null; echo '' > \"$LEGACY_MOD_LOG_PATH\" 2>/dev/null")
            }
            readLogFile()
        }
    }

    fun shareLog() {
        val fullText = _logLines.value.joinToString("\n") { it.rawText }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, fullText)
            type = "text/plain"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.logs_share_title)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    fun copyDiagnosticReport() {
        viewModelScope.launch {
            _isGeneratingReport.value = true
            try {
                val report = diagnosticManager.generateSystemDiagnosticReport()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("MountX Diagnostic Report", report)
                clipboard?.setPrimaryClip(clip)

                val msg = context.getString(R.string.logs_diagnostic_report_copied)
                _toastEvent.emit(msg)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val errMsg = context.getString(R.string.logs_err_generate_report, e.message ?: "")
                _toastEvent.emit(errMsg)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isGeneratingReport.value = false
            }
        }
    }

    fun saveLogToUri(uri: Uri, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isGeneratingReport.value = true
            try {
                val content = withContext(Dispatchers.IO) {
                    val diagnosticHeader = diagnosticManager.generateSystemDiagnosticReport()
                    val rawLogs = _logLines.value.joinToString("\n") { it.rawText }
                    buildString {
                        append(diagnosticHeader)
                        append("\n\n")
                        append("═".repeat(64))
                        append("\nMOUNTX SYSTEM LOG STREAM\n")
                        append("═".repeat(64))
                        append("\n\n")
                        append(rawLogs)
                    }
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                        os.flush()
                    } ?: throw IllegalStateException(context.getString(R.string.logs_err_open_stream, uri.toString()))
                }
                val fileName = resolveFileNameFromUri(uri)
                withContext(Dispatchers.Main) {
                    onSuccess(fileName)
                }
            } catch (e: Exception) {
                AppLogger.error("LogsVM", "Failed to save log to URI: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError(e.message ?: context.getString(R.string.logs_save_failed))
                }
            } finally {
                _isGeneratingReport.value = false
            }
        }
    }

    private fun resolveFileNameFromUri(uri: Uri): String {
        return runCatching {
            var name: String? = null
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
            name ?: uri.lastPathSegment ?: "mountx_log.log"
        }.getOrDefault("mountx_log.log")
    }

    fun startTailing() {
        if (tailJob?.isActive == true) return
        tailJob = viewModelScope.launch {
            readLogFile()
            while (isActive) {
                delay(6000L)
                if (_isAutoRefresh.value) {
                    readLogFile()
                }
            }
        }
    }

    fun stopTailing() {
        tailJob?.cancel()
        tailJob = null
    }

    private suspend fun readLogFile() = withContext(Dispatchers.IO) {
        val cmd = "cat \"$MOD_LOG_PATH\" \"$ALT_MOD_LOG_PATH\" \"$LOG_PATH\" \"$LEGACY_MOD_LOG_PATH\" \"$LEGACY_LOG_PATH\" \"$LEGACY_MOUNTIFY_LOG_PATH\" 2>/dev/null | tail -n 350"
        val result = RootShell.exec(cmd)
        val rawLines = if (result.isSuccess && result.stdout.isNotEmpty()) {
            result.stdout.filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        if (rawLines.isNotEmpty()) {
            val parsed = rawLines.map { line ->
                val isExplicitInfo = line.contains("[INFO", ignoreCase = true)
                val isExplicitDebug = line.contains("[DEBUG", ignoreCase = true)
                val level = when {
                    line.contains("[ERROR", ignoreCase = true) || line.contains("CRITICAL:", ignoreCase = true) || line.contains("FATAL:", ignoreCase = true) -> LogLevel.ERROR
                    line.contains("[WARN", ignoreCase = true) -> LogLevel.WARN
                    line.contains("[SUCCESS", ignoreCase = true) || line.contains("-> drwx", ignoreCase = true) || line.contains("verified: UID", ignoreCase = true) || line.contains("Heartbeat", ignoreCase = true) -> LogLevel.SUCCESS
                    isExplicitDebug -> LogLevel.DEBUG
                    isExplicitInfo -> LogLevel.INFO
                    line.contains("error", ignoreCase = true) && !line.contains("error=false", ignoreCase = true) -> LogLevel.ERROR
                    line.contains("failed", ignoreCase = true) -> LogLevel.ERROR
                    line.contains("warn", ignoreCase = true) -> LogLevel.WARN
                    line.contains("success", ignoreCase = true) || line.contains("Completed", ignoreCase = true) -> LogLevel.SUCCESS
                    else -> LogLevel.INFO
                }
                LogLine(rawText = line, level = level)
            }
            _logLines.value = parsed
        } else {
            _logLines.value = emptyList()
        }
    }
}
