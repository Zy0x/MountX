package app.mountx.ui.logs

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.mountx.R
import app.mountx.ui.components.CompactScreenHeader
import kotlinx.coroutines.launch
import app.mountx.ui.theme.adaptiveAmber
import app.mountx.ui.theme.adaptiveAmberContainer
import app.mountx.ui.theme.adaptiveCrimson
import app.mountx.ui.theme.adaptiveCrimsonContainer
import app.mountx.ui.theme.adaptiveCyan
import app.mountx.ui.theme.adaptiveCyanContainer
import app.mountx.ui.theme.adaptiveEmerald
import app.mountx.ui.theme.adaptiveEmeraldContainer

private val DeepStoneBg = Color(0xFF1C1917)
private val DeepStoneBorder = Color(0xFF44403C)
private val SandstoneLightText = Color(0xFFE7E5E4)
private val TimeStampColor = Color(0xFFA8A29E)
private val ModuleTagColor = Color(0xFFD8B4FE)
private val PathColor = Color(0xFFFDE68A)
private val SuccessColor = Color(0xFF86EFAC)
private val WarnColor = Color(0xFFFCD34D)
private val ErrorColor = Color(0xFFFCA5A5)

private val TimestampRegex = Regex("""^\[?(?:\d{4}-\d{2}-\d{2}\s+)?(\d{2}:\d{2}:\d{2})\]?\s*""")
private val BracketTagRegex = Regex("""^\[([^\]]+)\]""")
private val PathRegex = Regex("""^(?:\.{2,3})?/[a-zA-Z0-9_\-./+=~]+""")

private fun buildEarthyMineralLogLine(rawText: String): AnnotatedString {
    return buildAnnotatedString {
        var cursorText = rawText
        val tsMatch = TimestampRegex.find(cursorText)
        if (tsMatch != null) {
            val timePart = tsMatch.groupValues[1]
            withStyle(SpanStyle(color = TimeStampColor, fontWeight = FontWeight.Medium)) {
                append(timePart)
                append(" ")
            }
            cursorText = cursorText.substring(tsMatch.range.last + 1)
        }

        val isSuccessLine = cursorText.contains("[SUCCESS]", ignoreCase = true) ||
                cursorText.contains("activated via Root", ignoreCase = true) ||
                cursorText.contains("Heartbeat", ignoreCase = true)
        val isWarnLine = cursorText.contains("[WARN]", ignoreCase = true)
        val isErrorLine = cursorText.contains("[ERROR]", ignoreCase = true) ||
                cursorText.contains("FAILED", ignoreCase = true) ||
                cursorText.contains("CRITICAL", ignoreCase = true)

        val defaultTextColor = when {
            isSuccessLine -> SuccessColor
            isWarnLine -> WarnColor
            isErrorLine -> ErrorColor
            else -> SandstoneLightText
        }

        var index = 0
        while (index < cursorText.length) {
            val remaining = cursorText.substring(index)

            val bracketMatch = BracketTagRegex.find(remaining)
            if (bracketMatch != null) {
                val fullTag = bracketMatch.value
                val tagContent = bracketMatch.groupValues[1].trim()
                val tagColor = when {
                    tagContent.equals("SUCCESS", ignoreCase = true) || tagContent.contains("OK", ignoreCase = true) -> SuccessColor
                    tagContent.equals("WARN", ignoreCase = true) || tagContent.equals("WARNING", ignoreCase = true) -> WarnColor
                    tagContent.equals("ERROR", ignoreCase = true) || tagContent.equals("FAILED", ignoreCase = true) || tagContent.equals("CRITICAL", ignoreCase = true) -> ErrorColor
                    tagContent.equals("INFO", ignoreCase = true) || tagContent.equals("DEBUG", ignoreCase = true) -> TimeStampColor
                    else -> ModuleTagColor
                }
                withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold)) {
                    append(fullTag)
                }
                index += fullTag.length
                continue
            }

            val pathMatch = PathRegex.find(remaining)
            if (pathMatch != null) {
                val pathStr = pathMatch.value
                withStyle(SpanStyle(color = PathColor)) {
                    append(pathStr)
                }
                index += pathStr.length
                continue
            }

            val nextBracket = remaining.indexOf('[')
            val nextSlash = remaining.indexOf('/')
            val specials = listOf(nextBracket, nextSlash).filter { it > 0 }
            val step = if (specials.isNotEmpty()) specials.minOrNull()!! else remaining.length

            val plainChunk = remaining.substring(0, step)
            withStyle(SpanStyle(color = defaultTextColor)) {
                append(plainChunk)
            }
            index += step
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel,
    modifier: Modifier = Modifier
) {
    val filteredLines by viewModel.filteredLines.collectAsState()
    val allLines by viewModel.logLines.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val counts by viewModel.levelCounts.collectAsState()
    val isWatchdogHealthy by viewModel.isWatchdogHealthy.collectAsState()
    val isGeneratingReport by viewModel.isGeneratingReport.collectAsState()

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.saveLogToUri(
                uri = uri,
                onSuccess = { fileName ->
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.logs_saved_success, fileName),
                            actionLabel = context.getString(R.string.logs_action_open),
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            runCatching {
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "text/plain")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(openIntent)
                            }
                        }
                    }
                },
                onError = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.logs_save_failed),
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
        }
    }

    DisposableEffect(Unit) {
        viewModel.startTailing()
        onDispose {
            viewModel.stopTailing()
        }
    }

    LaunchedEffect(filteredLines.size) {
        if (filteredLines.isNotEmpty()) {
            listState.scrollToItem(filteredLines.size - 1)
        }
    }

    var refreshRotation by remember { mutableStateOf(0f) }
    val animatedRefreshRotation by animateFloatAsState(
        targetValue = refreshRotation,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "logs_refresh_spin"
    )

    // Pulse animation for daemon health dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Scaffold(
        topBar = {
            CompactScreenHeader(
                title = stringResource(R.string.logs_title),
                subtitle = stringResource(R.string.logs_subtitle_live_stream, allLines.size),
                actions = {
                    // Unified Modern Save Log Button (SAF Document Creation)
                    IconButton(
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            saveLauncher.launch("mountx_log_$timestamp.log")
                        },
                        enabled = !isGeneratingReport,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isGeneratingReport) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                                color = adaptiveCyan()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = stringResource(R.string.logs_save_log),
                                tint = adaptiveCyan(),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.clearLog() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ClearAll,
                            contentDescription = stringResource(R.string.logs_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            refreshRotation += 360f
                            viewModel.refreshLogs()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = animatedRefreshRotation }
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 8.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionColor = adaptiveCyan(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp)
        ) {
            // Live Status Header with Pulsing Dot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(if (isWatchdogHealthy) pulseAlpha else 1.0f)
                            .background(
                                color = if (isWatchdogHealthy) adaptiveEmerald() else adaptiveCrimson(),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isWatchdogHealthy) stringResource(R.string.logs_daemon_healthy) else stringResource(R.string.logs_daemon_error),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isWatchdogHealthy) adaptiveEmerald() else adaptiveCrimson()
                    )
                }

                Text(
                    text = stringResource(R.string.logs_lines_count_format, filteredLines.size, allLines.size),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Colored Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ALL
                LogFilterChip(
                    label = stringResource(R.string.logs_filter_all),
                    count = counts[LogLevel.ALL] ?: 0,
                    isSelected = selectedFilter == LogLevel.ALL,
                    activeColor = adaptiveCyan(),
                    activeContainer = adaptiveCyanContainer(),
                    onClick = { viewModel.setFilter(LogLevel.ALL) }
                )
                // ERROR (Red)
                LogFilterChip(
                    label = stringResource(R.string.logs_filter_error),
                    count = counts[LogLevel.ERROR] ?: 0,
                    isSelected = selectedFilter == LogLevel.ERROR,
                    activeColor = adaptiveCrimson(),
                    activeContainer = adaptiveCrimsonContainer(),
                    onClick = { viewModel.setFilter(LogLevel.ERROR) }
                )
                // WARN (Yellow)
                LogFilterChip(
                    label = stringResource(R.string.logs_filter_warn),
                    count = counts[LogLevel.WARN] ?: 0,
                    isSelected = selectedFilter == LogLevel.WARN,
                    activeColor = adaptiveAmber(),
                    activeContainer = adaptiveAmberContainer(),
                    onClick = { viewModel.setFilter(LogLevel.WARN) }
                )
                // SUCCESS (Green)
                LogFilterChip(
                    label = stringResource(R.string.logs_filter_success),
                    count = counts[LogLevel.SUCCESS] ?: 0,
                    isSelected = selectedFilter == LogLevel.SUCCESS,
                    activeColor = adaptiveEmerald(),
                    activeContainer = adaptiveEmeraldContainer(),
                    onClick = { viewModel.setFilter(LogLevel.SUCCESS) }
                )
                // INFO (Cyan)
                LogFilterChip(
                    label = stringResource(R.string.logs_filter_info),
                    count = counts[LogLevel.INFO] ?: 0,
                    isSelected = selectedFilter == LogLevel.INFO,
                    activeColor = adaptiveCyan(),
                    activeContainer = adaptiveCyanContainer(),
                    onClick = { viewModel.setFilter(LogLevel.INFO) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (filteredLines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.logs_empty_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.logs_empty_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepStoneBg),
                    border = BorderStroke(1.dp, DeepStoneBorder),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        // Dense Continuous Log Stream
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            items(filteredLines.size, key = { it }) { index ->
                                val line = filteredLines[index]
                                val isError = line.level == LogLevel.ERROR
                                val isSuccess = line.level == LogLevel.SUCCESS

                                Surface(
                                    color = when {
                                        isError -> Color(0x28EF4444)
                                        isSuccess -> Color(0x0C10B981)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        if (isError) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(13.dp)
                                                    .clip(RoundedCornerShape(1.5.dp))
                                                    .background(Color(0xFFEF4444))
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = buildEarthyMineralLogLine(line.rawText),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    activeColor: Color,
    activeContainer: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) activeContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) activeColor.copy(alpha = 0.5f) else Color.Transparent
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
