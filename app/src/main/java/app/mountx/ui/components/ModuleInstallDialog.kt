package app.mountx.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mountx.R
import app.mountx.data.model.RootSolution
import app.mountx.root.ModuleManager
import app.mountx.root.RootDetector
import app.mountx.ui.theme.CyberEmerald
import app.mountx.ui.theme.ForestGreenLight
import app.mountx.ui.theme.SunsetAmber
import app.mountx.ui.theme.WarmAmberLight
import app.mountx.ui.theme.adaptiveAmber
import app.mountx.ui.theme.adaptiveEmerald
import kotlinx.coroutines.launch

@Composable
fun ModuleInstallDialog(
    onDismiss: () -> Unit,
    onModuleInstalledOrEnabled: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isOperating by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var isDisabledState by remember { mutableStateOf(false) }
    var rootSolution by remember { mutableStateOf(RootSolution.NONE) }

    LaunchedEffect(Unit) {
        val info = RootDetector.getRootAndModuleInfo(forceRefresh = true)
        rootSolution = info.rootSolution
        isDisabledState = ModuleManager.isModuleDisabled()
    }

    Dialog(
        onDismissRequest = { if (!isOperating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = adaptiveAmber().copy(alpha = 0.15f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = adaptiveAmber(),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.module_dialog_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                if (isDisabledState) R.string.module_status_disabled_desc
                                else R.string.module_status_not_installed_desc
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Root Environment Info Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.module_root_solution_detected),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = rootSolution.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (rootSolution != RootSolution.NONE) adaptiveEmerald() else adaptiveAmber()
                        )
                    }
                }

                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

                // Action 1: Re-enable / Direct Install via Root
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, adaptiveEmerald().copy(alpha = 0.5f)),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = adaptiveEmerald().copy(alpha = 0.06f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isDisabledState) Icons.Default.CheckCircle else Icons.Default.Bolt,
                                contentDescription = null,
                                tint = adaptiveEmerald(),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (isDisabledState) R.string.module_action_enable_title
                                    else R.string.module_action_auto_install_title
                                ),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = adaptiveEmerald()
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                if (isDisabledState) R.string.module_action_enable_desc
                                else R.string.module_action_auto_install_desc
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isOperating = true
                                    operationMessage = "Memproses..."
                                    try {
                                        if (isDisabledState) {
                                            ModuleManager.enableModule().getOrThrow()
                                            Toast.makeText(context, "Modul berhasil diaktifkan kembali!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val msg = ModuleManager.installModuleDirectly(context).getOrThrow()
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                        onModuleInstalledOrEnabled()
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isOperating = false
                                    }
                                }
                            },
                            enabled = !isOperating,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = adaptiveEmerald(),
                                contentColor = if (isDark) Color.Black else Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            if (isOperating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = stringResource(
                                        if (isDisabledState) R.string.module_btn_enable_now
                                        else R.string.module_btn_install_now
                                    ),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Action 2: Export Flashable ZIP to Downloads
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.module_action_export_title),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.module_action_export_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isOperating = true
                                    try {
                                        val file = ModuleManager.exportModuleZip(context).getOrThrow()
                                        Toast.makeText(
                                            context,
                                            "ZIP tersimpan di Download: ${file.name}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal ekspor: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isOperating = false
                                    }
                                }
                            },
                            enabled = !isOperating,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderZip,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.module_btn_export_zip),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)
                            )
                        }
                    }
                }

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    enabled = !isOperating,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = stringResource(R.string.common_close),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
