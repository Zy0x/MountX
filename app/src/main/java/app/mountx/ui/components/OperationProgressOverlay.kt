package app.mountx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mountx.R
import app.mountx.ui.theme.BadgeMountedBgDark
import app.mountx.ui.theme.BadgeMountedBgLight
import app.mountx.ui.theme.BadgeMountedTextDark
import app.mountx.ui.theme.BadgeMountedTextLight
import app.mountx.ui.theme.WarmCrimsonBgDark
import app.mountx.ui.theme.WarmCrimsonBgLight
import app.mountx.ui.theme.WarmCrimsonBorderDark
import app.mountx.ui.theme.WarmCrimsonBorderLight
import app.mountx.ui.theme.WarmCrimsonDark
import app.mountx.ui.theme.WarmCrimsonLight
import app.mountx.ui.theme.HyperCyan
import app.mountx.ui.theme.SlateCyanLight

/**
 * State representation for any asynchronous root operation.
 */
sealed class OperationState {
    data class InProgress(
        val title: String,
        val stepMessage: String,
        val progressPercent: Float? = null // 0f..1f, or null for indeterminate
    ) : OperationState()

    data class Success(
        val title: String,
        val message: String,
        val details: List<Pair<String, String>> = emptyList(),
        val rawLog: String? = null
    ) : OperationState()

    data class Error(
        val title: String,
        val errorMessage: String,
        val rawLog: String? = null
    ) : OperationState()
}

/**
 * High-priority interactive progress overlay with real-time feedback.
 * MT-Manager & Partition-Wizard style visual experience.
 */
@Composable
fun OperationProgressOverlay(
    state: OperationState?,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (state == null) return

    val isCancellable = state !is OperationState.InProgress
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Dialog(
        onDismissRequest = { if (isCancellable) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = isCancellable,
            dismissOnClickOutside = isCancellable,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = when (state) {
                    is OperationState.InProgress -> (if (isDark) HyperCyan else MaterialTheme.colorScheme.primary).copy(alpha = 0.5f)
                    is OperationState.Success -> (if (isDark) BadgeMountedTextDark else BadgeMountedTextLight).copy(alpha = 0.5f)
                    is OperationState.Error -> (if (isDark) WarmCrimsonDark else WarmCrimsonLight).copy(alpha = 0.5f)
                }
            ),
            shadowElevation = 8.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    is OperationState.InProgress -> {
                        InProgressContent(state)
                    }
                    is OperationState.Success -> {
                        SuccessContent(state, onDismiss)
                    }
                    is OperationState.Error -> {
                        ErrorContent(state, onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun InProgressContent(state: OperationState.InProgress) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_rot")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "rot"
    )

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accentColor = if (isDark) HyperCyan else MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .padding(4.dp)
    ) {
        CircularProgressIndicator(
            progress = { state.progressPercent ?: 0.5f },
            strokeWidth = 3.dp,
            color = accentColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(54.dp)
                .rotate(if (state.progressPercent == null) angle else 0f)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = state.title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = state.stepMessage,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (state.progressPercent != null) {
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { state.progressPercent },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = accentColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${(state.progressPercent * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = accentColor
        )
    }
}

@Composable
private fun SuccessContent(
    state: OperationState.Success,
    onDismiss: () -> Unit
) {
    var isLogExpanded by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successColor = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight
    val successBg = if (isDark) BadgeMountedBgDark else BadgeMountedBgLight

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .background(successBg, CircleShape)
            .border(1.dp, successColor.copy(alpha = 0.4f), CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = successColor,
            modifier = Modifier.size(26.dp)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = state.title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = state.message,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (state.details.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isDark) Color(0xFF1C1917) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF44403C) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.details.forEach { (k, v) ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = k,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (isDark) Color(0xFFE7E5E4) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = v,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = successColor
                        )
                    }
                }
            }
        }
    }

    if (!state.rawLog.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { isLogExpanded = !isLogExpanded }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (isLogExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isLogExpanded) "Hide Technical Log" else "Show Technical Log",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = isLogExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1C1917) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF44403C) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = state.rawLog,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = if (isDark) Color(0xFFE7E5E4) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = onDismiss,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        Text(
            text = stringResource(R.string.common_ok),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ErrorContent(
    state: OperationState.Error,
    onDismiss: () -> Unit
) {
    var isLogExpanded by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val errorColor = if (isDark) WarmCrimsonDark else WarmCrimsonLight
    val errorBg = if (isDark) WarmCrimsonBgDark else WarmCrimsonBgLight
    val errorBorder = if (isDark) WarmCrimsonBorderDark else WarmCrimsonBorderLight

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .background(errorBg, CircleShape)
            .border(1.dp, errorBorder, CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = errorColor,
            modifier = Modifier.size(26.dp)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = state.title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = state.errorMessage,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = errorColor
    )

    if (!state.rawLog.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { isLogExpanded = !isLogExpanded }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (isLogExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isLogExpanded) "Hide Technical Log" else "Show Technical Log",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = isLogExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isDark) Color.Black.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(0.5.dp, if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = state.rawLog,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = errorColor,
                    modifier = Modifier
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = onDismiss,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        Text(
            text = stringResource(R.string.common_close),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
