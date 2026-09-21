package app.mountx.ui.logs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountx.R
import app.mountx.ui.components.CompactScreenHeader

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
                cursorText.contains("activated via Root", ignoreCase = true)
        val isWarnLine = cursorText.contains("[WARN]", ignoreCase = true)
        val isErrorLine = cursorText.contains("[ERROR]", ignoreCase = true) ||
                cursorText.contains("FAILED", ignoreCase = true)

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
                    tagContent.equals("ERROR", ignoreCase = true) || tagContent.equals("FAILED", ignoreCase = true) -> ErrorColor
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
    val logLines by viewModel.logLines.collectAsState()
    val isAutoRefresh by viewModel.isAutoRefresh.collectAsState()
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        viewModel.startTailing()
        onDispose {
            viewModel.stopTailing()
        }
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.scrollToItem(logLines.size - 1)
        }
    }

    var refreshRotation by remember { mutableStateOf(0f) }
    val animatedRefreshRotation by animateFloatAsState(
        targetValue = refreshRotation,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "logs_refresh_spin"
    )

    Scaffold(
        topBar = {
            CompactScreenHeader(
                title = stringResource(R.string.logs_title),
                subtitle = "${logLines.size} entries • Live Stream",
                actions = {
                    IconButton(
                        onClick = { viewModel.shareLog() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.logs_share),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
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
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp)
        ) {
            // Auto Refresh Toggle Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.logs_auto_refresh),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                    )
                    Switch(
                        checked = isAutoRefresh,
                        onCheckedChange = { viewModel.toggleAutoRefresh() },
                        modifier = Modifier.scale(0.85f)
                    )
                }
            }

            if (logLines.isEmpty()) {
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
                            .padding(12.dp)
                    ) {
                        // Terminal Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.logs_title),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFFAF8F5)
                            )
                            Text(
                                text = "${logLines.size} entries • Live Stream",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TimeStampColor
                            )
                        }

                        HorizontalDivider(
                            thickness = 0.8.dp,
                            color = DeepStoneBorder,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Dense Continuous Log Stream
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            items(logLines.size, key = { it }) { index ->
                                val line = logLines[index]
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
