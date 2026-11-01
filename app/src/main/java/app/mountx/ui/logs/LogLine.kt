package app.mountx.ui.logs

enum class LogLevel {
    ALL,
    SUCCESS,
    INFO,
    WARN,
    ERROR,
    DEBUG
}

data class LogLine(
    val rawText: String,
    val level: LogLevel,
    val tag: String = ""
)
