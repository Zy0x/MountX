package app.mountx.ui.storage

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountx.R
import app.mountx.data.model.BenchmarkResult
import app.mountx.data.model.DiskHardwareDetails
import app.mountx.data.model.DiskIoConfig
import app.mountx.data.model.IoPreset
import app.mountx.data.model.SdCardDiskInfo
import app.mountx.ui.theme.BadgeMountedBgDark
import app.mountx.ui.theme.BadgeMountedBgLight
import app.mountx.ui.theme.BadgeMountedTextDark
import app.mountx.ui.theme.BadgeMountedTextLight
import app.mountx.ui.theme.OutlinedNeutralBgDark
import app.mountx.ui.theme.OutlinedNeutralBgLight
import app.mountx.ui.theme.OutlinedNeutralBorderDark
import app.mountx.ui.theme.OutlinedNeutralBorderLight
import app.mountx.ui.theme.OutlinedNeutralTextDark
import app.mountx.ui.theme.OutlinedNeutralTextLight
import app.mountx.ui.theme.WarmStoneSlateDark
import app.mountx.ui.theme.WarmStoneSlateLight

/** In-sheet navigation pages for Disk Tools */
enum class DiskToolsPage {
    HUB,
    IO_BOOSTER,
    MAINTENANCE,
    BENCHMARK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiskToolsBottomSheet(
    disk: SdCardDiskInfo,
    ioConfig: DiskIoConfig?,
    isApplyingIo: Boolean,
    hardwareDetails: DiskHardwareDetails?,
    isBenchmarking: Boolean,
    benchmarkResult: BenchmarkResult?,
    isTrimming: Boolean,
    trimOutput: String?,
    isUrgentGcRunning: Boolean,
    onDismiss: () -> Unit,
    onApplyPreset: (IoPreset) -> Unit = {},
    onApplyCustomConfig: (DiskIoConfig) -> Unit,
    onRunBenchmark: () -> Unit,
    onRunGlobalTrim: () -> Unit,
    onRunUrgentGc: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentPage by remember { mutableStateOf(DiskToolsPage.HUB) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier
    ) {
        // Intercept back button to navigate to HUB before closing sheet
        BackHandler(enabled = currentPage != DiskToolsPage.HUB) {
            currentPage = DiskToolsPage.HUB
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Header Row: Dynamically switches between Root Hub and Sub-pages
            DiskToolsHeader(
                page = currentPage,
                disk = disk,
                onBack = { currentPage = DiskToolsPage.HUB },
                onDismiss = onDismiss
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Animated In-Sheet Screen Transitions
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState != DiskToolsPage.HUB) {
                        (slideInHorizontally { width -> width / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut())
                    }
                },
                label = "DiskToolsNavigation",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) { page ->
                when (page) {
                    DiskToolsPage.HUB -> {
                        DiskToolsHubView(
                            disk = disk,
                            ioConfig = ioConfig,
                            benchmarkResult = benchmarkResult,
                            hardwareDetails = hardwareDetails,
                            onNavigate = { target -> currentPage = target }
                        )
                    }
                    DiskToolsPage.IO_BOOSTER -> {
                        DiskIoBoosterSubPage(
                            ioConfig = ioConfig,
                            isApplyingIo = isApplyingIo,
                            onApplyCustomConfig = onApplyCustomConfig
                        )
                    }
                    DiskToolsPage.MAINTENANCE -> {
                        DiskMaintenanceSubPage(
                            isTrimming = isTrimming,
                            isUrgentGcRunning = isUrgentGcRunning,
                            onRunGlobalTrim = onRunGlobalTrim,
                            onRunUrgentGc = onRunUrgentGc
                        )
                    }
                    DiskToolsPage.BENCHMARK -> {
                        DiskBenchmarkSubPage(
                            isBenchmarking = isBenchmarking,
                            benchmarkResult = benchmarkResult,
                            onRunBenchmark = onRunBenchmark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Header Component ────────────────────────────────────────
@Composable
private fun DiskToolsHeader(
    page: DiskToolsPage,
    disk: SdCardDiskInfo,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val boltColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page == DiskToolsPage.HUB) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(boltColor.copy(alpha = if (isDark) 0.15f else 0.10f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = boltColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.storage_disk_tools_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = disk.hardwareTitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    val pageTitle = when (page) {
                        DiskToolsPage.IO_BOOSTER -> stringResource(R.string.storage_io_booster_title)
                        DiskToolsPage.MAINTENANCE -> stringResource(R.string.storage_flash_maint_title)
                        DiskToolsPage.BENCHMARK -> stringResource(R.string.storage_benchmark_title)
                        DiskToolsPage.HUB -> ""
                    }
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = disk.hardwareTitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Hub View (Clean 3-Action Menu Tiles + Hardware Telemetry) ──────
@Composable
private fun DiskToolsHubView(
    disk: SdCardDiskInfo,
    ioConfig: DiskIoConfig?,
    benchmarkResult: BenchmarkResult?,
    hardwareDetails: DiskHardwareDetails?,
    onNavigate: (DiskToolsPage) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val boosterColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
    val maintColor = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight
    val benchColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. I/O Speed Booster Tile
        val currentRa = ioConfig?.readAheadKb ?: 2048
        val currentSched = ioConfig?.scheduler ?: "none"
        CyberMenuTile(
            icon = Icons.Default.Speed,
            iconTint = boosterColor,
            iconBg = boosterColor.copy(alpha = if (isDark) 0.15f else 0.10f),
            title = stringResource(R.string.storage_io_booster_title),
            subtitle = "$currentRa KB • $currentSched",
            onClick = { onNavigate(DiskToolsPage.IO_BOOSTER) }
        )

        // 2. Flash Maintenance Tile
        CyberMenuTile(
            icon = Icons.Default.CleaningServices,
            iconTint = maintColor,
            iconBg = if (isDark) BadgeMountedBgDark else BadgeMountedBgLight,
            title = stringResource(R.string.storage_flash_maint_title),
            subtitle = stringResource(R.string.storage_flash_maintenance_desc),
            onClick = { onNavigate(DiskToolsPage.MAINTENANCE) }
        )

        // 3. Benchmark Tile
        val benchSubtitle = if (benchmarkResult != null) {
            "${benchmarkResult.sequentialReadMbPerSec} MB/s • ${benchmarkResult.accessLatencyMs} ms"
        } else {
            stringResource(R.string.storage_benchmark_desc)
        }
        CyberMenuTile(
            icon = Icons.Default.PlayArrow,
            iconTint = benchColor,
            iconBg = benchColor.copy(alpha = if (isDark) 0.15f else 0.10f),
            title = stringResource(R.string.storage_benchmark_title),
            subtitle = benchSubtitle,
            onClick = { onNavigate(DiskToolsPage.BENCHMARK) }
        )

        // 4. Hardware & Bus Telemetry Section (Embedded at bottom)
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.storage_hardware_info_title).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = if (isDark) WarmStoneSlateDark else WarmStoneSlateLight
        )

        if (hardwareDetails != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.storage_hw_vendor), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(hardwareDetails.vendor.ifBlank { disk.hardwareTitle }, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (hardwareDetails.productName.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Product Name", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(hardwareDetails.productName, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.storage_hw_serial), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(hardwareDetails.serialNumber.ifBlank { "N/A" }, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.storage_hw_bus_speed), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(hardwareDetails.busClockMhz.ifBlank { "Standard High-Speed" }, fontSize = 10.5.sp, color = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.storage_hw_speed_class), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(hardwareDetails.uhsSpeedClass.ifBlank { "Class 10 / UHS-I" }, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight)
                    }
                }
            }
        }
    }
}

// ── Cyber Menu Tile ─────────────────────────────────────────
@Composable
private fun CyberMenuTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

// ── Sub-Page 1: I/O Speed Booster ───────────────────────────
@Composable
private fun DiskIoBoosterSubPage(
    ioConfig: DiskIoConfig?,
    isApplyingIo: Boolean,
    onApplyCustomConfig: (DiskIoConfig) -> Unit
) {
    val currentConfig = ioConfig ?: DiskIoConfig()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activePresetColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
    val raColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
    val schedColor = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight
    var selectedReadAhead by remember(ioConfig) { mutableIntStateOf(currentConfig.readAheadKb) }
    var selectedScheduler by remember(ioConfig) { mutableStateOf(currentConfig.scheduler) }
    var isPersistent by remember(ioConfig) { mutableStateOf(currentConfig.isBootPersistent) }

    val readAheadOptions = listOf(128, 512, 1024, 2048, 4096)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.storage_io_booster_desc),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Preset Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IoPreset.entries.forEach { preset ->
                val isActive = selectedReadAhead == preset.readAheadKb
                OutlinedButton(
                    onClick = {
                        // Only populate state — user still needs to press Apply button
                        selectedReadAhead = preset.readAheadKb
                        // Pick best matching scheduler from available list or fall back to "none"
                        selectedScheduler = when (preset) {
                            IoPreset.GAMING_ULTRA -> currentConfig.availableSchedulers
                                .firstOrNull { it == "deadline" || it == "mq-deadline" }
                                ?: currentConfig.availableSchedulers.firstOrNull()
                                ?: selectedScheduler
                            IoPreset.BALANCED -> currentConfig.availableSchedulers
                                .firstOrNull { it == "cfq" || it == "bfq" }
                                ?: currentConfig.availableSchedulers.firstOrNull()
                                ?: selectedScheduler
                            IoPreset.DEFAULT_SYSTEM -> currentConfig.availableSchedulers
                                .firstOrNull { it == "none" || it == "noop" }
                                ?: currentConfig.availableSchedulers.firstOrNull()
                                ?: selectedScheduler
                        }
                        isPersistent = preset != IoPreset.DEFAULT_SYSTEM
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isActive) activePresetColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isActive) activePresetColor.copy(alpha = if (isDark) 0.2f else 0.12f) else Color.Transparent,
                        contentColor = if (isActive) activePresetColor else MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Text(
                        text = preset.label,
                        fontSize = 10.5.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Read-Ahead Buffer Chips
        Text(
            text = "${stringResource(R.string.storage_io_read_ahead)}: $selectedReadAhead KB",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            readAheadOptions.forEach { kb ->
                val isSelected = selectedReadAhead == kb
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedReadAhead = kb },
                    label = { Text("${kb}K", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = raColor.copy(alpha = if (isDark) 0.2f else 0.12f),
                        selectedLabelColor = raColor
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) raColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // I/O Scheduler
        Text(
            text = "${stringResource(R.string.storage_io_scheduler)}: $selectedScheduler",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        val schedulers = currentConfig.availableSchedulers.ifEmpty { listOf("noop", "deadline", "cfq") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            schedulers.forEach { sched ->
                val isSelected = selectedScheduler.equals(sched, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedScheduler = sched },
                    label = { Text(sched, fontSize = 10.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (isDark) BadgeMountedBgDark else BadgeMountedBgLight,
                        selectedLabelColor = schedColor
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) schedColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Boot Persistence Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.storage_io_boot_persist),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = isPersistent,
                onCheckedChange = { isPersistent = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5)
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Apply Button
        Button(
            onClick = {
                onApplyCustomConfig(
                    currentConfig.copy(
                        readAheadKb = selectedReadAhead,
                        scheduler = selectedScheduler,
                        isBootPersistent = isPersistent
                    )
                )
            },
            enabled = !isApplyingIo,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            if (isApplyingIo) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.storage_io_apply_btn),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Sub-Page 2: Flash Maintenance ───────────────────────────
@Composable
private fun DiskMaintenanceSubPage(
    isTrimming: Boolean,
    isUrgentGcRunning: Boolean,
    onRunGlobalTrim: () -> Unit,
    onRunUrgentGc: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.storage_flash_maintenance_desc),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Global FSTRIM Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.storage_global_trim_btn),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.storage_global_trim_desc),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRunGlobalTrim,
                    enabled = !isTrimming,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, (if (isDark) BadgeMountedTextDark else BadgeMountedTextLight).copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isDark) BadgeMountedBgDark else BadgeMountedBgLight,
                        contentColor = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                ) {
                    if (isTrimming) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.storage_global_trim_btn), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // F2FS Urgent GC Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.storage_f2fs_gc_btn),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.storage_f2fs_gc_desc),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRunUrgentGc,
                    enabled = !isUrgentGcRunning,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isDark) OutlinedNeutralBorderDark else OutlinedNeutralBorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isDark) OutlinedNeutralBgDark else OutlinedNeutralBgLight,
                        contentColor = if (isDark) OutlinedNeutralTextDark else OutlinedNeutralTextLight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                ) {
                    if (isUrgentGcRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = if (isDark) OutlinedNeutralTextDark else OutlinedNeutralTextLight, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.storage_f2fs_gc_btn), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Sub-Page 3: Benchmark ───────────────────────────────────
@Composable
private fun DiskBenchmarkSubPage(
    isBenchmarking: Boolean,
    benchmarkResult: BenchmarkResult?,
    onRunBenchmark: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val speedColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
    val latencyColor = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.storage_benchmark_desc),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onRunBenchmark,
            enabled = !isBenchmarking,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            if (isBenchmarking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.storage_benchmark_btn), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (benchmarkResult != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, speedColor.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.storage_benchmark_speed),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${benchmarkResult.sequentialReadMbPerSec} MB/s",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = speedColor
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, latencyColor.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.storage_benchmark_latency),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${benchmarkResult.accessLatencyMs} ms",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = latencyColor
                        )
                    }
                }
            }
        }
    }
}
