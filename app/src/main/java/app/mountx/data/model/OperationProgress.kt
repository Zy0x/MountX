package app.mountx.data.model

/**
 * Type of storage or VFS operation currently being processed.
 */
enum class OperationType {
    MOVE_TO_SD,
    RESTORE_TO_INTERNAL,
    MOUNT,
    UNMOUNT,
    DELETE_CATEGORY
}

/**
 * Unified data model for real-time progress of storage operations.
 */
data class OperationProgress(
    val type: OperationType,
    val title: String,
    val subtitle: String,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 5,
    val stepDescriptions: List<String> = emptyList(),
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val progressPercent: Float = 0f, // 0.0f to 1.0f
    val currentItemName: String? = null,
    val isIndeterminate: Boolean = false,
    val isFinished: Boolean = false,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)
