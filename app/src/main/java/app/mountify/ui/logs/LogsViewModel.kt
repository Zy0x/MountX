package app.mountify.ui.logs

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountify.root.RootShell
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
        const val LOG_PATH = "/storage/emulated/0/mountify.log"
    }

    private val _logLines = MutableStateFlow<List<LogLine>>(emptyList())
    val logLines: StateFlow<List<LogLine>> = _logLines.asStateFlow()

    private val _isAutoRefresh = MutableStateFlow(true)
    val isAutoRefresh: StateFlow<Boolean> = _isAutoRefresh.asStateFlow()

    private val _selectedFilter = MutableStateFlow(LogLevel.INFO)
    val selectedFilter: StateFlow<LogLevel> = _selectedFilter.asStateFlow()

    init {
        startLogTail()
    }

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
                RootShell.exec("echo '' > \"$LOG_PATH\"")
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
        context.startActivity(Intent.createChooser(sendIntent, "Share Mountify Log").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun startLogTail() {
        viewModelScope.launch {
            while (isActive) {
                if (_isAutoRefresh.value) {
                    readLogFile()
                }
                delay(3000L)
            }
        }
    }

    private suspend fun readLogFile() = withContext(Dispatchers.IO) {
        val result = RootShell.exec("cat \"/data/adb/modules/Mountify/mountify.log\" \"$LOG_PATH\" 2>/dev/null | tail -n 250")
        if (result.isSuccess && result.stdout.isNotEmpty()) {
            val parsed = result.stdout.map { line ->
                val level = when {
                    line.contains("ERROR", ignoreCase = true) || line.contains("failed", ignoreCase = true) -> LogLevel.ERROR
                    line.contains("Completed", ignoreCase = true) || line.contains("-> drwx", ignoreCase = true) -> LogLevel.SUCCESS
                    line.contains("DEBUG", ignoreCase = true) -> LogLevel.DEBUG
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
