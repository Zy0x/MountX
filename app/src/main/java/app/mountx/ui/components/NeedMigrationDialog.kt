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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountx.R
import app.mountx.data.model.GameEntry
import app.mountx.util.FormatUtils

@Composable
fun NeedMigrationDialog(
    game: GameEntry,
    onConfirmMigration: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amberColor = Color(0xFFFF9800)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111726),
        shape = RoundedCornerShape(24.dp),
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
                color = Color(0xFFF1F5F9)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF162035),
                    border = BorderStroke(1.dp, Color(0xFF2E3D5C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppIconImage(packageName = game.packageName, size = 36.dp)
                        Column {
                            Text(
                                text = game.displayName,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF1F5F9)
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

                val sizeText = if (game.dataSizeBytes > 0L) FormatUtils.formatBytes(game.dataSizeBytes) else "utama"
                Text(
                    text = stringResource(R.string.dialog_need_migration_desc, sizeText),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                    color = Color(0xFF94A3B8)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmMigration()
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = amberColor),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_need_migration_action),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF94A3B8)
                )
            }
        },
        modifier = modifier
    )
}
