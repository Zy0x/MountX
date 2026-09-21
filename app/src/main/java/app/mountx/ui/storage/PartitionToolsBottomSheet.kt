package app.mountx.ui.storage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountx.R
import app.mountx.data.model.PartitionInfo
import app.mountx.ui.theme.BadgeMountedTextDark
import app.mountx.ui.theme.BadgeMountedTextLight
import app.mountx.ui.theme.WarmCrimsonDark
import app.mountx.ui.theme.WarmCrimsonLight
import app.mountx.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartitionToolsBottomSheet(
    partition: PartitionInfo,
    isCheckingFs: Boolean,
    isTrimming: Boolean,
    onDismiss: () -> Unit,
    onFormatClick: () -> Unit,
    onEditLabelClick: () -> Unit,
    onCheckFsClick: () -> Unit,
    onTrimClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accentColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
    val crimsonColor = if (isDark) WarmCrimsonDark else WarmCrimsonLight
    val emeraldColor = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight
    val skyColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = if (isDark) 0.15f else 0.10f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.storage_partition_tools_title, partition.cleanShortName),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${partition.path} • ${FormatUtils.formatBytes(partition.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(10.dp))

            // Action 1: Format & Ganti Filesystem
            PartitionActionItem(
                icon = Icons.Default.Build,
                iconTint = crimsonColor,
                title = stringResource(R.string.storage_action_format_change_fs),
                subtitle = stringResource(R.string.storage_action_format_change_fs_desc),
                onClick = onFormatClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action 2: Ganti Label Partisi (Non-destructive)
            PartitionActionItem(
                icon = Icons.AutoMirrored.Filled.Label,
                iconTint = skyColor,
                title = stringResource(R.string.storage_action_rename_label),
                subtitle = stringResource(R.string.storage_action_rename_label_desc),
                onClick = onEditLabelClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action 3: Check Filesystem (fsck)
            PartitionActionItem(
                icon = Icons.Default.Security,
                iconTint = if (partition.isMounted) MaterialTheme.colorScheme.onSurfaceVariant else accentColor,
                title = stringResource(R.string.storage_fsck_button),
                subtitle = if (partition.isMounted) {
                    stringResource(R.string.storage_fsck_mounted_warning)
                } else {
                    stringResource(R.string.storage_action_fsck_desc)
                },
                isLoading = isCheckingFs,
                onClick = onCheckFsClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action 3: TRIM Partition (fstrim)
            PartitionActionItem(
                icon = Icons.Default.CleaningServices,
                iconTint = if (partition.isMounted) emeraldColor else MaterialTheme.colorScheme.onSurfaceVariant,
                title = stringResource(R.string.storage_action_trim_partition),
                subtitle = if (partition.isMounted) {
                    stringResource(R.string.storage_action_trim_partition_desc)
                } else {
                    stringResource(R.string.storage_action_trim_unmounted_warning)
                },
                isLoading = isTrimming,
                onClick = onTrimClick
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(10.dp))

            // Technical Metadata Section
            Text(
                text = stringResource(R.string.storage_action_details).uppercase(),
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
                        Text(stringResource(R.string.storage_filesystem), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = partition.fsType.ifBlank { "RAW" }.uppercase(),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (partition.isMounted) emeraldColor else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.storage_mount_point), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = partition.mountPoint ?: stringResource(R.string.storage_status_unmounted_badge),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (partition.isMounted) emeraldColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!partition.uuid.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.storage_uuid_label), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = partition.uuid,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (!partition.label.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.storage_label_field), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = partition.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = accentColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PartitionActionItem(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.12f))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = iconTint, modifier = Modifier.size(16.dp))
                } else {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
