package app.mountx.ui.logs

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountx.root.RootShell
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        const val LOG_PATH = "/storage/emulated/0/mountx.log"
        const val MOD_LOG_PATH = "/data/adb/modules/MountX/mountx.log"
        const val LEGACY_LOG_PATH = "/storage/emulated/0/mountify.log"
        const val LEGACY_MOD_LOG_PATH = "/data/adb/modules/Mountify/mountify.log"
    }

    private val _logLines = MutableStateFlow<List<LogLine>>(emptyList())
    val logLines: StateFlow<List<LogLine>> = _logLines.asStateFlow()

    private val _isAutoRefresh = MutableStateFlow(true)
    val isAutoRefresh: StateFlow<Boolean> = _isAutoRefresh.asStateFlow()

    private val _selectedFilter = MutableStateFlow(LogLevel.INFO)
    val selectedFilter: StateFlow<LogLevel> = _selectedFilter.asStateFlow()

    private var tailJob: kotlinx.coroutines.Job? = null

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
                RootShell.exec("echo '' > \"$LOG_PATH\"; echo '' > \"$MOD_LOG_PATH\" 2>/dev/null; rm -f \"$LEGACY_LOG_PATH\" 2>/dev/null; echo '' > \"$LEGACY_MOD_LOG_PATH\" 2>/dev/null")
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
        context.startActivity(Intent.createChooser(sendIntent, "Share MountX Log").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
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
        val cmd = "cat \"$MOD_LOG_PATH\" \"$LOG_PATH\" \"$LEGACY_MOD_LOG_PATH\" \"$LEGACY_LOG_PATH\" 2>/dev/null | tail -n 350"
        val result = RootShell.exec(cmd)
        val rawLines = if (result.isSuccess && result.stdout.isNotEmpty()) {
            result.stdout.filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        if (rawLines.isNotEmpty()) {
            val parsed = rawLines.map { line ->
                val level = when {
                    line.contains("ERROR", ignoreCase = true) || line.contains("failed", ignoreCase = true) -> LogLevel.ERROR
                    line.contains("SUCCESS", ignoreCase = true) || line.contains("Completed", ignoreCase = true) || line.contains("-> drwx", ignoreCase = true) -> LogLevel.SUCCESS
                    line.contains("DEBUG", ignoreCase = true) -> LogLevel.DEBUG
                    line.contains("WARN", ignoreCase = true) -> LogLevel.WARN
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
