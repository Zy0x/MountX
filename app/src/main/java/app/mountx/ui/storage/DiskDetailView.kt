package app.mountx.ui.storage

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
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
import app.mountx.R
import app.mountx.data.model.DiskType
import app.mountx.data.model.FilesystemType
import app.mountx.data.model.PartitionInfo
import app.mountx.data.model.SdCardDiskInfo
import app.mountx.data.model.StorageInfo
import app.mountx.ui.components.CompactScreenHeader
import app.mountx.ui.components.ConfirmDialog
import app.mountx.ui.components.SectionHeader
import app.mountx.ui.theme.CyberEmerald
import app.mountx.ui.theme.ElectricCyan
import app.mountx.ui.theme.NeonCrimson
import app.mountx.util.FormatUtils

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
    isMountingAll: Boolean = false,
    isUnmountingAll: Boolean = false,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenWizard: () -> Unit,
    onMountPartition: (PartitionInfo) -> Unit,
    onUnmountPartition: (PartitionInfo) -> Unit,
    onMountAll: () -> Unit,
    onUnmountAll: () -> Unit,
    onOpenDiskTools: () -> Unit,
    onOpenPartitionTools: (PartitionInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            CompactScreenHeader(
                title = disk.hardwareTitle,
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
                    onOpenWizard = onOpenWizard,
                    onOpenDiskTools = onOpenDiskTools,
                    onMountAll = onMountAll,
                    onUnmountAll = onUnmountAll,
                    isMountingAll = isMountingAll,
                    isUnmountingAll = isUnmountingAll
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
                        onMount = { onMountPartition(partition) },
                        onUnmount = { onUnmountPartition(partition) },
                        onOpenTools = { onOpenPartitionTools(partition) }
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
    onOpenDiskTools: () -> Unit,
    onMountAll: () -> Unit,
    onUnmountAll: () -> Unit,
    isMountingAll: Boolean,
    isUnmountingAll: Boolean,
    modifier: Modifier = Modifier
) {
    val diskTypeIcon = if (disk.diskType == DiskType.MICRO_SD) Icons.Default.SdStorage else Icons.Default.Usb
    val hasUnmounted = disk.partitions.any { !it.isMounted }

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
                            text = disk.hardwareTitle,
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
                        text = stringResource(R.string.storage_disk_partitions_badge, disk.partitions.size),
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
                    text = stringResource(R.string.storage_section_disk_label).uppercase(),
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
                    color = FormatUtils.getHealthColor(disk.usedPercent)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cumulative Capacity Progress Bar (4-Tier Health Gradient)
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
                                FormatUtils.getHealthColor(disk.usedPercent)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Baris 1: Partition Wizard Action Button (Harmonious Cyber Outline)
            Surface(
                onClick = onOpenWizard,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.storage_action_repartition),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Baris 2: Batch Mount/Eject + Disk Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Batch Mount / Eject button
                OutlinedButton(
                    onClick = {
                        if (hasUnmounted) onMountAll() else onUnmountAll()
                    },
                    enabled = disk.partitions.isNotEmpty() && !isMountingAll && !isUnmountingAll,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (hasUnmounted) CyberEmerald.copy(alpha = 0.6f) else NeonCrimson.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (hasUnmounted) CyberEmerald else NeonCrimson
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    if (isMountingAll || isUnmountingAll) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = if (hasUnmounted) CyberEmerald else NeonCrimson,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (hasUnmounted) Icons.Default.PlayArrow else Icons.Default.Eject,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasUnmounted) stringResource(R.string.storage_action_mount_all) else stringResource(R.string.storage_action_unmount_all),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Disk Tools Button
                OutlinedButton(
                    onClick = onOpenDiskTools,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ElectricCyan.copy(alpha = 0.08f),
                        contentColor = ElectricCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.storage_action_disk_tools),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
                    partitions.forEach { part ->
                        val rawFraction = (part.sizeBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 1f)
                        val weight = rawFraction.coerceAtLeast(0.12f)
                        val shortName = if (weight < 0.25f && part.partitionNumber > 0) {
                            "P${part.partitionNumber}"
                        } else {
                            part.cleanShortName
                        }

                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF182030))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            // Two-tier fill level: Solid fill for Used space with 4-tier health gradient and white edge highlight line
                            if (part.usedPercent > 0.001f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = part.usedPercent)
                                        .background(
                                            FormatUtils.getHealthColor(part.usedPercent)
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
    onMount: () -> Unit,
    onUnmount: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val partBadgeColor = if (partition.isMounted) CyberEmerald else MaterialTheme.colorScheme.primary


    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (partition.isMounted) CyberEmerald.copy(alpha = 0.5f)
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
                            .background(partBadgeColor.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = "P${partition.partitionNumber.takeIf { it > 0 } ?: 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = partBadgeColor
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

                // Standard Technical Status Badge (Mounted / Unmounted)
                if (partition.isMounted) {
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
                                text = stringResource(R.string.storage_status_mounted_badge),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberEmerald
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = stringResource(R.string.storage_status_unmounted_badge),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
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
                        color = if (partition.isMounted) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
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

            // Storage Fill Bar if mounted or size is known (4-Tier Health Gradient)
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
                        color = FormatUtils.getHealthColor(partition.usedPercent)
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
                            .background(FormatUtils.getHealthColor(partition.usedPercent))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row (65% Primary Action + 35% Partition Tools Menu)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Mount / Unmount Button (65%)
                if (partition.isMounted) {
                    OutlinedButton(
                        onClick = onUnmount,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCrimson),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(0.65f)
                            .height(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Eject,
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
                            .weight(0.65f)
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

                // 2. Partition Tools Button (35%)
                OutlinedButton(
                    onClick = onOpenTools,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(0.35f)
                        .height(34.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.storage_action_partition_tools),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

