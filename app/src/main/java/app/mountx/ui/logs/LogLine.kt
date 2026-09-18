package app.mountx.ui.logs

enum class LogLevel {
    INFO,
    SUCCESS,
    ERROR,
    DEBUG
}

data class LogLine(
    val rawText: String,
    val level: LogLevel,
    val tag: String = ""
)
