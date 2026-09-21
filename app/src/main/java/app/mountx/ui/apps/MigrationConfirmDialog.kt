package app.mountx.ui.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mountx.R
import app.mountx.data.model.ConflictStrategy
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.MoveDirection
import app.mountx.data.model.PartitionInfo
import app.mountx.data.model.SdCardDiskInfo
import app.mountx.util.FormatUtils

private val CyberEmerald = Color(0xFF00E676)
private val DarkCardBg = Color(0xFF141923)
private val LightBorder = Color(0xFF263238)

data class MigrationConfirmData(
    val direction: MoveDirection,
    val totalBytes: Long,
    val sourceName: String,
    val destName: String,
    val destFreeBytes: Long,
    val destExistingBytes: Long = 0L,
    val targetDisk: SdCardDiskInfo? = null,
    val targetPartition: PartitionInfo? = null,
    val pointsToMigrate: List<MountPointConfig> = emptyList()
)

@Composable
fun MigrationConfirmDialog(
    data: MigrationConfirmData,
    onDismiss: () -> Unit,
    onConfirm: (ConflictStrategy) -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(ConflictStrategy.OVERWRITE) }
    val isSpaceLow = data.destFreeBytes in 1L until data.totalBytes
    val hasConflict = data.destExistingBytes > 64 * 1024L
    val isToSd = data.direction == MoveDirection.TO_SD

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                (if (isToSd) CyberEmerald else Color(0xFF2979FF)).copy(alpha = 0.15f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isToSd) Icons.Default.SdCard else Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = if (isToSd) CyberEmerald else Color(0xFF2979FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (isToSd) R.string.migration_confirm_title_to_sd
                                else R.string.migration_confirm_title_to_internal
                            ),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.migration_confirm_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Source -> Destination Flow Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Source
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = stringResource(R.string.migration_confirm_source_label),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = data.sourceName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Arrow Icon
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(28.dp)
                                .background(CyberEmerald.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = CyberEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Destination
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = stringResource(R.string.migration_confirm_dest_label),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = data.destName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isToSd) CyberEmerald else Color(0xFF2979FF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats: Size & Free Space
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = stringResource(R.string.migration_confirm_size_label),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FormatUtils.formatBytes(data.totalBytes),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSpaceLow) Color(0xFFE53935).copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSpaceLow) Color(0xFFE53935).copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = stringResource(R.string.migration_confirm_free_space_label),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isSpaceLow) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (data.destFreeBytes > 0L) FormatUtils.formatBytes(data.destFreeBytes) else "-",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSpaceLow) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Storage Low Warning
                if (isSpaceLow) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE53935).copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.migration_confirm_storage_low,
                                    FormatUtils.formatBytes(data.destFreeBytes),
                                    FormatUtils.formatBytes(data.totalBytes)
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color(0xFFE53935)
                            )
                        }
                    }
                }

                // Conflict Resolution Section
                if (hasConflict) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.migration_confirm_conflict_title),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF9800)
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.migration_confirm_conflict_desc,
                                    FormatUtils.formatBytes(data.destExistingBytes)
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Strategy Radio Buttons
                            ConflictStrategyOption(
                                title = stringResource(R.string.migration_conflict_overwrite_title),
                                description = stringResource(R.string.migration_conflict_overwrite_desc),
                                isSelected = selectedStrategy == ConflictStrategy.OVERWRITE,
                                onClick = { selectedStrategy = ConflictStrategy.OVERWRITE }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            ConflictStrategyOption(
                                title = stringResource(R.string.migration_conflict_merge_title),
                                description = stringResource(R.string.migration_conflict_merge_desc),
                                isSelected = selectedStrategy == ConflictStrategy.MERGE,
                                onClick = { selectedStrategy = ConflictStrategy.MERGE }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            ConflictStrategyOption(
                                title = stringResource(R.string.migration_conflict_backup_title),
                                description = stringResource(R.string.migration_conflict_backup_desc),
                                isSelected = selectedStrategy == ConflictStrategy.BACKUP_FIRST,
                                onClick = { selectedStrategy = ConflictStrategy.BACKUP_FIRST }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    Button(
                        onClick = { onConfirm(selectedStrategy) },
                        enabled = !isSpaceLow && data.totalBytes > 0L,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isToSd) CyberEmerald else Color(0xFF2979FF)
                        )
                    ) {
                        Text(
                            text = stringResource(
                                if (isToSd) R.string.migration_confirm_btn_start_move
                                else R.string.migration_confirm_btn_start_restore
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictStrategyOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyberEmerald.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) CyberEmerald.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) CyberEmerald else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
