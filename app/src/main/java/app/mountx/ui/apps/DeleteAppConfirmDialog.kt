package app.mountx.ui.apps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mountx.R
import app.mountx.ui.theme.CyberEmerald
import app.mountx.ui.theme.NeonCrimson
import app.mountx.util.FormatUtils

@Composable
fun DeleteAppConfirmDialog(
    appName: String,
    packageName: String,
    requiredRestoreBytes: Long,
    internalFreeBytes: Long,
    isRestoring: Boolean,
    restoreProgress: Float,
    restoreMessage: String,
    onConfirmRestoreAndDelete: () -> Unit,
    onConfirmUnmountAndDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val isStorageSufficient = internalFreeBytes >= requiredRestoreBytes || requiredRestoreBytes == 0L

    Dialog(
        onDismissRequest = {
            if (!isRestoring) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isRestoring,
            dismissOnClickOutside = !isRestoring
        )
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF111625),
            border = BorderStroke(1.dp, Color(0xFF232B3E)),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isRestoring) {
                    // ── RESTORING PROGRESS STATE ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.dialog_delete_restoring_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }

                    Text(
                        text = stringResource(R.string.dialog_delete_restoring_msg),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = restoreMessage.ifBlank { "Menyalin berkas fisik..." },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = "${(restoreProgress * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        LinearProgressIndicator(
                            progress = { restoreProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                } else {
                    // ── NORMAL SELECTION STATE ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(NeonCrimson.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = NeonCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.dialog_delete_app_title),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.dialog_delete_app_subtitle, appName),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Warning if storage is insufficient for full restore
                    if (!isStorageSufficient) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NeonCrimson.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = NeonCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.dialog_delete_storage_insufficient,
                                        FormatUtils.formatBytes(internalFreeBytes),
                                        FormatUtils.formatBytes(requiredRestoreBytes)
                                    ),
                                    fontSize = 11.sp,
                                    color = NeonCrimson,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    // Option 1: Restore to Internal & Delete
                    Card(
                        onClick = { if (isStorageSufficient) onConfirmRestoreAndDelete() },
                        enabled = isStorageSufficient,
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isStorageSufficient) Color(0xFF161D2F) else Color(0xFF131722),
                            disabledContainerColor = Color(0xFF131722)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isStorageSufficient) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            else Color(0xFF1E2536)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderCopy,
                                contentDescription = null,
                                tint = if (isStorageSufficient) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(R.string.dialog_delete_restore_btn),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isStorageSufficient) Color.White else Color.Gray
                                    )
                                    if (requiredRestoreBytes > 0) {
                                        Text(
                                            text = FormatUtils.formatBytes(requiredRestoreBytes),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CyberEmerald
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = stringResource(R.string.dialog_delete_restore_desc),
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    // Option 2: Unmount Only & Delete
                    Card(
                        onClick = onConfirmUnmountAndDelete,
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161D2F)),
                        border = BorderStroke(1.dp, Color(0xFF232B3E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = null,
                                tint = NeonCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.dialog_delete_unmount_btn),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = stringResource(R.string.dialog_delete_unmount_desc),
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    // Cancel button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E384D)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.custom_path_cancel),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
