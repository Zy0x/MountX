package app.mountx.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountx.R
import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.SdCardDiskInfo
import app.mountx.util.FormatUtils

data class MigrationCategoryOption(
    val id: String,
    val category: MountPointCategory,
    val title: String,
    val pathHint: String,
    val icon: ImageVector,
    val originalConfig: MountPointConfig? = null
)

data class MigrationPartitionTarget(
    val label: String,
    val subLabel: String,
    val mountPoint: String,
    val freeBytes: Long
)

@Composable
fun NeedMigrationDialog(
    game: GameEntry,
    availableDisks: List<SdCardDiskInfo> = emptyList(),
    defaultSdBase: String = "/data/sdext2",
    onConfirmMigration: (selectedPoints: List<MountPointConfig>, targetBase: String) -> Unit,
    onOpenDetail: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val amberColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFF57C00)
    val pkg = game.packageName

    // 1. Build partition targets
    val partitionTargets = remember(availableDisks, defaultSdBase) {
        val list = mutableListOf<MigrationPartitionTarget>()
        availableDisks.forEach { disk ->
            val diskTitle = disk.displayName.ifBlank { disk.diskName }
            if (disk.partitions.isNotEmpty()) {
                disk.partitions.forEach { part ->
                    val mp = part.mountPoint ?: disk.mountPath
                    if (mp.isNotBlank()) {
                        val fs = part.fsType.ifBlank { "EXT4" }.uppercase()
                        val partName = part.shortName.ifBlank { part.name }
                        val freeFormatted = FormatUtils.formatBytes(part.freeBytes)
                        list.add(
                            MigrationPartitionTarget(
                                label = "$diskTitle • $partName ($fs)",
                                subLabel = "$freeFormatted • $mp",
                                mountPoint = mp,
                                freeBytes = part.freeBytes
                            )
                        )
                    }
                }
            } else if (disk.mountPath.isNotBlank()) {
                list.add(
                    MigrationPartitionTarget(
                        label = diskTitle,
                        subLabel = "${FormatUtils.formatBytes(disk.totalFreeBytes)} • ${disk.mountPath}",
                        mountPoint = disk.mountPath,
                        freeBytes = disk.totalFreeBytes
                    )
                )
            }
        }
        if (list.isEmpty()) {
            list.add(
                MigrationPartitionTarget(
                    label = "MicroSD Default",
                    subLabel = defaultSdBase,
                    mountPoint = defaultSdBase,
                    freeBytes = 0L
                )
            )
        }
        list
    }

    var selectedTarget by remember(partitionTargets) {
        mutableStateOf(partitionTargets.first())
    }
    var isPartitionDropdownExpanded by remember { mutableStateOf(false) }

    // 2. Build Category Options
    val categoryOptions = remember(game) {
        val options = mutableListOf<MigrationCategoryOption>()
        val existingData = game.mountPoints.firstOrNull { it.resolveCategory() == MountPointCategory.EXTERNAL_DATA }
        val existingObb = game.mountPoints.firstOrNull { it.resolveCategory() == MountPointCategory.OBB_STORAGE }
        val existingMedia = game.mountPoints.firstOrNull { it.resolveCategory() == MountPointCategory.MEDIA_DOWNLOADS }

        options.add(
            MigrationCategoryOption(
                id = "data",
                category = MountPointCategory.EXTERNAL_DATA,
                title = "Data Game",
                pathHint = "Android/data/$pkg",
                icon = Icons.Default.Storage,
                originalConfig = existingData
            )
        )
        options.add(
            MigrationCategoryOption(
                id = "obb",
                category = MountPointCategory.OBB_STORAGE,
                title = "Resource OBB",
                pathHint = "Android/obb/$pkg",
                icon = Icons.Default.FolderZip,
                originalConfig = existingObb
            )
        )
        if (existingMedia != null) {
            options.add(
                MigrationCategoryOption(
                    id = "media",
                    category = MountPointCategory.MEDIA_DOWNLOADS,
                    title = "Media & Unduhan",
                    pathHint = "Android/media/$pkg",
                    icon = Icons.Default.PermMedia,
                    originalConfig = existingMedia
                )
            )
        }
        options
    }

    var selectedCategoryIds by remember(categoryOptions) {
        mutableStateOf(setOf("data", "obb"))
    }

    val isSpaceLow = remember(selectedTarget, game.dataSizeBytes) {
        selectedTarget.freeBytes > 0L && game.dataSizeBytes > 0L && game.dataSizeBytes > selectedTarget.freeBytes
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = amberColor.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = amberColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(R.string.dialog_need_migration_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // App summary card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppIconImage(packageName = game.packageName, size = 36.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = game.displayName,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (game.dataSizeBytes > 0L) {
                                Text(
                                    text = FormatUtils.formatBytes(game.dataSizeBytes) + " di memori internal",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = amberColor
                                )
                            }
                        }
                    }
                }

                // Section 1: Choose categories
                Text(
                    text = stringResource(R.string.migration_select_categories_title),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categoryOptions.forEach { opt ->
                        val isChecked = selectedCategoryIds.contains(opt.id)
                        Surface(
                            onClick = {
                                selectedCategoryIds = if (isChecked) {
                                    selectedCategoryIds - opt.id
                                } else {
                                    selectedCategoryIds + opt.id
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedCategoryIds = if (checked) {
                                            selectedCategoryIds + opt.id
                                        } else {
                                            selectedCategoryIds - opt.id
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = opt.icon,
                                    contentDescription = null,
                                    tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = opt.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = opt.pathHint,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedCategoryIds.isEmpty()) {
                    Text(
                        text = stringResource(R.string.migration_no_category_selected),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Section 2: Destination Partition
                Text(
                    text = stringResource(R.string.migration_target_partition_title),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { isPartitionDropdownExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SdCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = selectedTarget.label,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = selectedTarget.subLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isPartitionDropdownExpanded,
                        onDismissRequest = { isPartitionDropdownExpanded = false }
                    ) {
                        partitionTargets.forEach { target ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = target.label,
                                            fontWeight = if (target.mountPoint == selectedTarget.mountPoint) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.5.sp
                                        )
                                        Text(
                                            text = target.subLabel,
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SdCard,
                                        contentDescription = null,
                                        tint = if (target.mountPoint == selectedTarget.mountPoint) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = if (target.mountPoint == selectedTarget.mountPoint) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    selectedTarget = target
                                    isPartitionDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (isSpaceLow) {
                    Text(
                        text = stringResource(R.string.migration_insufficient_space),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetBase = selectedTarget.mountPoint
                    val chosenPoints = categoryOptions.filter { selectedCategoryIds.contains(it.id) }.map { opt ->
                        val existing = opt.originalConfig
                        val relSource = when (opt.category) {
                            MountPointCategory.EXTERNAL_DATA -> "MountX/Android/data/$pkg"
                            MountPointCategory.OBB_STORAGE -> "MountX/Android/obb/$pkg"
                            MountPointCategory.MEDIA_DOWNLOADS -> "MountX/Android/media/$pkg"
                            else -> "MountX/data/$pkg"
                        }
                        val finalSource = "$targetBase/$relSource"
                        val defaultTarget = when (opt.category) {
                            MountPointCategory.EXTERNAL_DATA -> "/data/media/0/Android/data/$pkg"
                            MountPointCategory.OBB_STORAGE -> "/data/media/0/Android/obb/$pkg"
                            MountPointCategory.MEDIA_DOWNLOADS -> "/data/media/0/Android/media/$pkg"
                            else -> "/data/data/$pkg"
                        }
                        existing?.copy(
                            sourcePath = finalSource,
                            targetPath = if (existing.targetPath.isNotBlank() && !existing.targetPath.startsWith("/sdcard")) existing.targetPath else defaultTarget,
                            enabled = true
                        ) ?: MountPointConfig(
                            id = "${opt.id}_${pkg}_${System.currentTimeMillis()}",
                            category = opt.category,
                            sourcePath = finalSource,
                            targetPath = defaultTarget,
                            enabled = true
                        )
                    }
                    onConfirmMigration(chosenPoints, targetBase)
                    onDismiss()
                },
                enabled = selectedCategoryIds.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = amberColor),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = stringResource(R.string.migration_start_action),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onOpenDetail != null) {
                    OutlinedButton(
                        onClick = {
                            onOpenDetail()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.migration_open_detail_action),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun NeedMigrationDialog(
    game: GameEntry,
    onConfirmMigration: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeedMigrationDialog(
        game = game,
        availableDisks = emptyList(),
        defaultSdBase = "/data/sdext2",
        onConfirmMigration = { _, _ -> onConfirmMigration() },
        onOpenDetail = null,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
