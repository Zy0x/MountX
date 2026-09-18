package app.mountify.ui.storage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountify.R
import app.mountify.data.model.DiskType
import app.mountify.data.model.FilesystemType
import app.mountify.data.model.PartitionInfo
import app.mountify.data.model.SdCardDiskInfo
import app.mountify.data.model.StorageInfo
import app.mountify.ui.components.CompactScreenHeader
import app.mountify.ui.components.ConfirmDialog
import app.mountify.ui.components.SectionHeader
import app.mountify.ui.theme.CyberEmerald
import app.mountify.ui.theme.ElectricCyan
import app.mountify.ui.theme.NeonCrimson
import app.mountify.util.FormatUtils

/**
 * Dedicated sub-page for inspecting a physical storage disk (MicroSD or USB OTG)
 * and managing all of its individual partitions with direct actions.
 */
@Composable
fun DiskDetailView(
    disk: SdCardDiskInfo,
    storage: StorageInfo?,
    configuredSdBase: String,
    isCheckingFs: Boolean,
    isFormatting: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenWizard: () -> Unit,
    onMountPartition: (blockDevice: String, fsType: FilesystemType) -> Unit,
    onUnmountPartition: () -> Unit,
    onFormatPartition: (PartitionInfo) -> Unit,
    onCheckFilesystem: (PartitionInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            CompactScreenHeader(
                title = disk.displayName,
                subtitle = stringResource(R.string.storage_disk_detail_subtitle, disk.partitions.size),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.storage_detect_devices),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Disk Hardware Overview & Capacity Card
            item {
                DiskHardwareOverviewCard(
                    disk = disk,
                    onOpenWizard = onOpenWizard
                )
            }

            // 2. Visual Partition Map for this disk
            if (disk.partitions.isNotEmpty()) {
                item {
                    DiskMiniVisualMapCard(
                        partitions = disk.partitions
                    )
                }
            }

            // 3. Standalone Partition Cards Header
            item {
                SectionHeader(
                    title = stringResource(R.string.storage_disk_partitions_list)
                )
            }

            // 4. Standalone Partition Cards List
            if (disk.partitions.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.storage_partition_no_partitions),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(disk.partitions, key = { it.path }) { partition ->
                    DiskPartitionCard(
                        partition = partition,
                        isCheckingFs = isCheckingFs,
                        isFormatting = isFormatting,
                        onMount = {
                            val fs = when (partition.fsType.lowercase()) {
                                "f2fs" -> FilesystemType.F2FS
                                "ext4" -> FilesystemType.EXT4
                                "vfat", "fat32" -> FilesystemType.FAT32
                                "exfat" -> FilesystemType.EXFAT
                                else -> FilesystemType.F2FS
                            }
                            onMountPartition(partition.path, fs)
                        },
                        onUnmount = onUnmountPartition,
                        onFormat = { onFormatPartition(partition) },
                        onCheckFilesystem = { onCheckFilesystem(partition) }
                    )
                }
            }
        }
    }
}

// ── Disk Hardware Overview Card ─────────────────────────────
@Composable
private fun DiskHardwareOverviewCard(
    disk: SdCardDiskInfo,
    onOpenWizard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val diskTypeIcon = if (disk.diskType == DiskType.MICRO_SD) Icons.Default.SdStorage else Icons.Default.Storage

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = diskTypeIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = disk.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = disk.devicePath,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = ElectricCyan
                        )
                    }
                }

                // Partition Count Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${disk.partitions.size} Partisi",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Capacity Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.storage_specs).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(
                        R.string.storage_disk_total_capacity,
                        FormatUtils.formatBytes(disk.totalSizeBytes),
                        FormatUtils.formatBytes(disk.totalUsedBytes),
                        FormatUtils.formatBytes(disk.totalFreeBytes)
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = CyberEmerald
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cumulative Capacity Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (disk.totalSizeBytes > 0 && disk.totalUsedBytes > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = disk.usedPercent.coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(CyberEmerald, ElectricCyan)
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Partition Wizard Action Button
            Button(
                onClick = onOpenWizard,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.storage_action_repartition),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

// ── Mini Visual Partition Map Card ──────────────────────────
@Composable
private fun DiskMiniVisualMapCard(
    partitions: List<PartitionInfo>,
    modifier: Modifier = Modifier
) {
    val totalBytes = partitions.sumOf { it.sizeBytes }.coerceAtLeast(1L)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.storage_disk_map_title),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val partitionColors = listOf(
                        Color(0xFF0284C7), // Sky Blue for P1 (Portable)
                        Color(0xFF059669), // Cyber Emerald for P2/Target
                        Color(0xFFD97706), // Tangerine Amber for P3
                        Color(0xFF7C3AED), // Violet for P4
                        Color(0xFFEC4899)  // Pink for P5+
                    )

                    partitions.forEachIndexed { idx, part ->
                        val rawFraction = (part.sizeBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 1f)
                        val weight = rawFraction.coerceAtLeast(0.12f)
                        val color = partitionColors[idx % partitionColors.size]

                        val shortName = if (weight < 0.25f && part.name.startsWith("mmcblk0p")) {
                            "p${part.name.removePrefix("mmcblk0p")}"
                        } else if (weight < 0.20f && part.name.startsWith("mmcblk")) {
                            part.name.removePrefix("mmcblk")
                        } else {
                            part.shortName
                        }

                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF131722))
                                .border(BorderStroke(1.dp, color.copy(alpha = 0.65f)), RoundedCornerShape(8.dp))
                        ) {
                            // Two-tier fill level: Solid fill for Used space with white edge highlight line
                            if (part.usedPercent > 0.001f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = part.usedPercent)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(color.copy(alpha = 0.75f), color)
                                            )
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(1.5.dp)
                                            .fillMaxHeight()
                                            .background(Color.White.copy(alpha = 0.85f))
                                    )
                                }
                            }

                            // Centered label overlay
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = shortName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = if (weight < 0.25f) 9.5.sp else 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (part.sizeBytes > 0) FormatUtils.formatBytes(part.sizeBytes) else part.fsType.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Standalone Partition Card ───────────────────────────────
@Composable
private fun DiskPartitionCard(
    partition: PartitionInfo,
    isCheckingFs: Boolean,
    isFormatting: Boolean,
    onMount: () -> Unit,
    onUnmount: () -> Unit,
    onFormat: () -> Unit,
    onCheckFilesystem: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMountedFsckWarning by remember { mutableStateOf(false) }
    var showMountedFormatWarning by remember { mutableStateOf(false) }
    var showPortableInfoDialog by remember { mutableStateOf(false) }

    val partitionColors = listOf(
        Color(0xFF0284C7), // Sky Blue for P1
        Color(0xFF059669), // Cyber Emerald for Target
        Color(0xFFD97706), // Amber for P3
        Color(0xFF7C3AED)  // Violet for P4
    )
    val partColor = when {
        partition.isTargetMount -> CyberEmerald
        partition.isPortableMount -> Color(0xFF0284C7)
        partition.fsType.equals("f2fs", ignoreCase = true) -> CyberEmerald
        partition.fsType.equals("ext4", ignoreCase = true) -> Color(0xFF0284C7)
        else -> partitionColors[(partition.partitionNumber.coerceAtLeast(1) - 1) % partitionColors.size]
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (partition.isTargetMount) CyberEmerald.copy(alpha = 0.6f)
            else if (partition.isPortableMount) Color(0xFF0284C7).copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Partition Name & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(partColor.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = "P${partition.partitionNumber.takeIf { it > 0 } ?: 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = partColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = partition.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = partition.path,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                when {
                    partition.isTargetMount -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberEmerald.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = CyberEmerald,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.storage_badge_target_active),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberEmerald
                                )
                            }
                        }
                    }
                    partition.isPortableMount -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Default.SdStorage,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.storage_badge_system_portable),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7)
                                )
                            }
                        }
                    }
                    partition.isMounted -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElectricCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = stringResource(R.string.storage_badge_portable),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    partition.isMountTargetReady -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = stringResource(R.string.storage_badge_ready),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Text(
                                text = stringResource(R.string.storage_partition_unmounted),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Row: Filesystem, Size, Mount Point
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = partition.fsType.ifBlank { "RAW" }.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = partColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = FormatUtils.formatBytes(partition.sizeBytes),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (partition.mountPoint != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = partition.mountPoint,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            color = if (partition.isTargetMount) CyberEmerald else if (partition.isPortableMount) Color(0xFF0284C7) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Storage Fill Bar if mounted or size is known
            if (partition.isMounted && partition.usedBytes > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.storage_partition_free_used_fmt,
                            FormatUtils.formatBytes(partition.usedBytes),
                            FormatUtils.formatBytes(partition.freeBytes)
                        ),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(partition.usedPercent * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = partColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = partition.usedPercent)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(partColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row (AOMEI Partition Assistant style direct actions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Mount / Unmount / System Active Button
                if (partition.isTargetMount) {
                    OutlinedButton(
                        onClick = onUnmount,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCrimson),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.storage_action_unmount),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (partition.isPortableMount) {
                    OutlinedButton(
                        onClick = { showPortableInfoDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.storage_btn_system_mounted),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Button(
                        onClick = onMount,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberEmerald,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.storage_action_mount),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 2. Format Button with Safety Guard
                OutlinedButton(
                    onClick = {
                        if (partition.isMounted) {
                            showMountedFormatWarning = true
                        } else {
                            onFormat()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.storage_action_format),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 3. Filesystem Check (fsck) Button with Safety Guard
                OutlinedButton(
                    onClick = {
                        if (partition.isMounted) {
                            showMountedFsckWarning = true
                        } else {
                            onCheckFilesystem()
                        }
                    },
                    enabled = !isCheckingFs,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (partition.isMounted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    if (isCheckingFs) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.storage_action_check_fs),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Informational & Safety Dialogs for DiskPartitionCard
    if (showMountedFsckWarning) {
        ConfirmDialog(
            title = stringResource(R.string.storage_fsck_mounted_warning_title),
            message = stringResource(
                R.string.storage_fsck_mounted_warning_desc,
                partition.mountPoint ?: partition.path
            ),
            confirmText = stringResource(R.string.common_ok),
            onConfirm = { showMountedFsckWarning = false },
            onDismiss = { showMountedFsckWarning = false }
        )
    }

    if (showMountedFormatWarning) {
        ConfirmDialog(
            title = stringResource(R.string.format_warning),
            message = stringResource(
                R.string.storage_format_mounted_warning_desc,
                partition.mountPoint ?: partition.path
            ),
            confirmText = stringResource(R.string.common_ok),
            onConfirm = { showMountedFormatWarning = false },
            onDismiss = { showMountedFormatWarning = false }
        )
    }

    if (showPortableInfoDialog) {
        ConfirmDialog(
            title = stringResource(R.string.storage_system_mounted_info_title),
            message = stringResource(
                R.string.storage_system_mounted_info_desc,
                partition.mountPoint ?: partition.path
            ),
            confirmText = stringResource(R.string.common_ok),
            onConfirm = { showPortableInfoDialog = false },
            onDismiss = { showPortableInfoDialog = false }
        )
    }
}

