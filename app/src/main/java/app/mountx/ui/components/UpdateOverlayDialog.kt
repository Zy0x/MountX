package app.mountx.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mountx.R
import app.mountx.data.model.UpdateInfo
import app.mountx.ui.theme.ElectricIndigo
import app.mountx.ui.theme.ElectricIndigoLight
import app.mountx.ui.theme.adaptiveAmber
import app.mountx.ui.theme.adaptiveCrimson
import app.mountx.ui.theme.adaptiveCyan
import app.mountx.ui.theme.adaptiveEmerald

/**
 * Lightweight hardware-accelerated scrollbar indicator for scrollable dialog content.
 */
fun Modifier.dialogVerticalScrollbar(
    scrollState: ScrollState,
    thumbColor: Color,
    trackColor: Color = Color.Transparent,
    width: Dp = 4.dp,
    paddingEnd: Dp = 2.dp
): Modifier = this.drawWithContent {
    drawContent()
    if (scrollState.maxValue > 0) {
        val viewPortLength = scrollState.viewportSize.toFloat()
        val totalLength = viewPortLength + scrollState.maxValue.toFloat()
        val thumbLength = (viewPortLength / totalLength * size.height).coerceIn(24.dp.toPx(), size.height * 0.8f)
        val scrollFraction = (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
        val thumbOffset = scrollFraction * (size.height - thumbLength)
        val x = size.width - width.toPx() - paddingEnd.toPx()

        if (trackColor != Color.Transparent) {
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x, 0f),
                size = Size(width.toPx(), size.height),
                cornerRadius = CornerRadius(width.toPx() / 2)
            )
        }

        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(x, thumbOffset),
            size = Size(width.toPx(), thumbLength),
            cornerRadius = CornerRadius(width.toPx() / 2)
        )
    }
}

/**
 * Semantic representation of a parsed changelog element.
 */
sealed class ChangelogItem {
    data class Header(val text: String, val level: Int) : ChangelogItem()
    data class CategoryHeader(val category: String) : ChangelogItem()
    data class Callout(val text: String) : ChangelogItem()
    data class ListItem(val indentLevel: Int, val rawText: String) : ChangelogItem()
    data class Paragraph(val text: String) : ChangelogItem()
}

/**
 * Parses raw markdown or bullet-list changelog into structured semantic items.
 */
fun parseChangelogToItems(rawText: String, currentVersion: String): List<ChangelogItem> {
    val items = mutableListOf<ChangelogItem>()
    val lines = rawText.split("\r?\n".toRegex())
    val cleanVersion = currentVersion.removePrefix("v").trim()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) continue

        // Suppress redundant main title: "## Release Notes — MountX ..."
        if (trimmed.startsWith("## Release Notes", ignoreCase = true) ||
            trimmed.startsWith("# Release Notes", ignoreCase = true)) {
            continue
        }

        // Check version section header: "## [2.2.48] - 2026-11-04" or "## 2.2.48"
        val versionHeaderMatch = Regex("""^##\s*\[v?([^\]]+)\](?:\s*-\s*(.+))?""").find(trimmed)
        if (versionHeaderMatch != null) {
            val ver = versionHeaderMatch.groupValues[1].trim()
            val date = versionHeaderMatch.groupValues.getOrNull(2)?.trim() ?: ""
            if (ver.equals(cleanVersion, ignoreCase = true)) {
                // If it's the current version, the dialog header already highlights it,
                // but we can present a subtle date callout if available
                if (date.isNotBlank()) {
                    items.add(ChangelogItem.Paragraph(date))
                }
                continue
            } else {
                items.add(ChangelogItem.Header(if (date.isNotBlank()) "v$ver ($date)" else "v$ver", 2))
                continue
            }
        }

        // Category Header: "### Added", "### Improved", "### Fixed", "### UI/UX", etc.
        if (trimmed.startsWith("### ")) {
            val catName = trimmed.removePrefix("###").trim()
            items.add(ChangelogItem.CategoryHeader(catName))
            continue
        }

        // Category Header with emojis or plain colon: "✨ Added:", "🚀 Improved:", "Fixed:"
        val emojiCatMatch = Regex("""^(?:✨|🚀|🐛|🎨|⚙️|🔒)?\s*(Added|Improved|Fixed|Changed|Security|UI/UX|UI\s*/\s*UX|Removed):?$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (emojiCatMatch != null) {
            val catName = emojiCatMatch.groupValues[1].trim()
            items.add(ChangelogItem.CategoryHeader(catName))
            continue
        }

        // Blockquote / Callout: "> This release includes..."
        if (trimmed.startsWith("> ")) {
            items.add(ChangelogItem.Callout(trimmed.removePrefix("> ").trim()))
            continue
        }

        // List item with indentation check:
        // Counts leading whitespace to determine nesting level
        val listMatch = Regex("""^(\s*)(?:[-*•]|\d+[.)])\s+(.*)$""").find(line)
        if (listMatch != null) {
            val leadingWs = listMatch.groupValues[1].replace("	", "    ")
            val indentLevel = if (leadingWs.length < 2) 0 else 1
            val content = listMatch.groupValues[2].trim()
            items.add(ChangelogItem.ListItem(indentLevel, content))
            continue
        }

        // General other headers: "## Title"
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            items.add(ChangelogItem.Header(trimmed.removePrefix("#".repeat(level)).trim(), level))
            continue
        }

        // Regular paragraph
        items.add(ChangelogItem.Paragraph(trimmed))
    }

    return items
}

/**
 * Parses inline markdown: **bold**, `code`, and *italic* into an AnnotatedString.
 */
@Composable
fun parseInlineMarkdown(
    text: String,
    isDark: Boolean,
    fontSize: TextUnit = 11.5.sp,
    baseColor: Color = if (isDark) Color(0xFF94A3B8) else Color(0xFF57534E),
    boldColor: Color = MaterialTheme.colorScheme.onSurface,
    codeColor: Color = if (isDark) Color(0xFF67E8F9) else Color(0xFF0369A1),
    codeBg: Color = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
): AnnotatedString {
    return remember(text, isDark) {
        buildAnnotatedString {
            val pattern = Regex("""(\*\*.*?\*\*|`.*?`|\*.*?\*)""")
            var lastIndex = 0

            for (match in pattern.findAll(text)) {
                val start = match.range.first
                val end = match.range.last + 1

                if (start > lastIndex) {
                    withStyle(SpanStyle(color = baseColor, fontSize = fontSize)) {
                        append(text.substring(lastIndex, start))
                    }
                }

                val matchedValue = match.value
                when {
                    matchedValue.startsWith("**") && matchedValue.endsWith("**") && matchedValue.length >= 4 -> {
                        val inner = matchedValue.substring(2, matchedValue.length - 2)
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = boldColor,
                                fontSize = fontSize
                            )
                        ) {
                            append(inner)
                        }
                    }
                    matchedValue.startsWith("`") && matchedValue.endsWith("`") && matchedValue.length >= 2 -> {
                        val inner = matchedValue.substring(1, matchedValue.length - 1)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = codeColor,
                                background = codeBg,
                                fontSize = (fontSize.value - 0.5f).sp
                            )
                        ) {
                            append(" $inner ")
                        }
                    }
                    matchedValue.startsWith("*") && matchedValue.endsWith("*") && matchedValue.length >= 2 -> {
                        val inner = matchedValue.substring(1, matchedValue.length - 1)
                        withStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = baseColor,
                                fontSize = fontSize
                            )
                        ) {
                            append(inner)
                        }
                    }
                    else -> {
                        withStyle(SpanStyle(color = baseColor, fontSize = fontSize)) {
                            append(matchedValue)
                        }
                    }
                }
                lastIndex = end
            }

            if (lastIndex < text.length) {
                withStyle(SpanStyle(color = baseColor, fontSize = fontSize)) {
                    append(text.substring(lastIndex))
                }
            }
        }
    }
}

/**
 * Category Header Chip (e.g., Added, Improved, Fixed).
 */
@Composable
fun ChangelogCategoryView(
    category: String,
    isDark: Boolean
) {
    val clean = category.trim().removePrefix("###").removeSuffix(":").trim()
    val lower = clean.lowercase()

    val (icon, accentColor, title) = when {
        "add" in lower -> Triple("✨", adaptiveCyan(), clean.ifBlank { "Added" })
        "improv" in lower || "perf" in lower -> Triple("🚀", adaptiveEmerald(), clean.ifBlank { "Improved" })
        "fix" in lower || "bug" in lower -> Triple("🐛", adaptiveCrimson(), clean.ifBlank { "Fixed" })
        "ui" in lower || "ux" in lower || "change" in lower || "style" in lower -> Triple("🎨", if (isDark) ElectricIndigo else ElectricIndigoLight, clean.ifBlank { "UI / UX" })
        "sec" in lower -> Triple("🔒", adaptiveCrimson(), clean.ifBlank { "Security" })
        "remov" in lower || "deprecat" in lower -> Triple("🗑️", adaptiveAmber(), clean.ifBlank { "Removed" })
        else -> Triple("🏷️", if (isDark) ElectricIndigo else ElectricIndigoLight, clean)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = accentColor.copy(alpha = if (isDark) 0.15f else 0.10f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = if (isDark) 0.35f else 0.25f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = icon,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * List Item View with indentation and bullet styling.
 */
@Composable
fun ChangelogListItemView(
    item: ChangelogItem.ListItem,
    isDark: Boolean
) {
    val indent = if (item.indentLevel == 0) 0.dp else 16.dp
    val indigoAccent = if (isDark) ElectricIndigo else ElectricIndigoLight
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF57534E)

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = if (item.indentLevel == 0) 3.dp else 1.5.dp, bottom = 1.5.dp)
    ) {
        if (item.indentLevel == 0) {
            // Root bullet: crisp indigo dot
            Box(
                modifier = Modifier
                    .padding(top = 5.5.dp, end = 7.dp)
                    .size(5.dp)
                    .background(indigoAccent, CircleShape)
            )
        } else {
            // Nested bullet: subtle dash
            Text(
                text = "– ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = secondaryTextColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        Text(
            text = parseInlineMarkdown(
                text = item.rawText,
                isDark = isDark,
                fontSize = if (item.indentLevel == 0) 12.sp else 11.5.sp
            ),
            lineHeight = if (item.indentLevel == 0) 17.sp else 16.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Blockquote / Callout View.
 */
@Composable
fun ChangelogCalloutView(
    text: String,
    isDark: Boolean
) {
    val indigoAccent = if (isDark) ElectricIndigo else ElectricIndigoLight
    val calloutBg = if (isDark) Color(0xFF1E2235) else Color(0xFFF3F0E6)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = calloutBg,
        border = BorderStroke(1.dp, indigoAccent.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(indigoAccent, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = parseInlineMarkdown(text, isDark, fontSize = 11.sp),
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Compact, clean modal dialog notifying user of an available app update.
 * Styled symmetrically with the in-app Changelog dialog for cohesive visual identity.
 */
@Composable
fun UpdateOverlayDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val emeraldColor = adaptiveEmerald()
    val cardBg = if (isDark) Color(0xFF131620) else Color(0xFFFAF8F5)
    val cardBorder = if (isDark) Color(0xFF232838) else Color(0xFFE5E2DC)
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF57534E)
    val indigoAccent = if (isDark) ElectricIndigo else ElectricIndigoLight

    val rawNotes = updateInfo.changelog.ifBlank {
        stringResource(R.string.update_default_notes)
    }
    val parsedItems = remember(rawNotes, updateInfo.latestVersion) {
        parseChangelogToItems(rawNotes, updateInfo.latestVersion)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = 680.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp)
            ) {
                // Header: # Software Update + Close (X)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "#",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = indigoAccent
                        )
                        Text(
                            text = stringResource(R.string.update_dialog_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFE7E5E4),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Inner Release Card (Mirrors CompactChangelogReleaseCard)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // Version Pill & "New" Badge row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF4F46E5)
                                ) {
                                    Text(
                                        text = "v${updateInfo.latestVersion}",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = emeraldColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(emeraldColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.update_badge_available),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = emeraldColor
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.update_whats_new),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val scrollState = rememberScrollState()
                        val scrollbarThumbColor = indigoAccent.copy(alpha = 0.65f)
                        val scrollbarTrackColor = if (isDark) Color(0xFF232838).copy(alpha = 0.5f) else Color(0xFFE5E2DC).copy(alpha = 0.5f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 340.dp)
                                .dialogVerticalScrollbar(
                                    scrollState = scrollState,
                                    thumbColor = scrollbarThumbColor,
                                    trackColor = scrollbarTrackColor,
                                    width = 4.dp,
                                    paddingEnd = 2.dp
                                )
                                .verticalScroll(scrollState)
                                .padding(end = if (scrollState.maxValue > 0) 10.dp else 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                parsedItems.forEach { item ->
                                    when (item) {
                                        is ChangelogItem.CategoryHeader -> ChangelogCategoryView(item.category, isDark)
                                        is ChangelogItem.ListItem -> ChangelogListItemView(item, isDark)
                                        is ChangelogItem.Callout -> ChangelogCalloutView(item.text, isDark)
                                        is ChangelogItem.Header -> {
                                            Text(
                                                text = item.text,
                                                fontSize = if (item.level <= 2) 13.sp else 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                            )
                                        }
                                        is ChangelogItem.Paragraph -> {
                                            Text(
                                                text = parseInlineMarkdown(item.text, isDark, fontSize = 11.5.sp),
                                                lineHeight = 16.sp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Subtle scroll hint when there is more content below
                        if (scrollState.canScrollForward) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.update_scroll_hint),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = indigoAccent
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = indigoAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions: Later vs Download
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.update_btn_later),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.update_btn_download),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
