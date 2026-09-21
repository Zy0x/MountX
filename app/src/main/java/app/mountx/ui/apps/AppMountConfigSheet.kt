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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountx.R
import app.mountx.data.model.InstalledAppInfo
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.data.repository.CandidateDirectory
import app.mountx.ui.components.AppIconImage
import app.mountx.ui.theme.AuroraGradientBrush
import app.mountx.util.FormatUtils

/**
 * Universal Smart Directory Classification configuration sheet.
 * Displays 4 human-readable preset categories with pre-flight validation
 * and real-time total selected size estimation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMountConfigSheet(
    appInfo: InstalledAppInfo,
    candidateDirectories: List<CandidateDirectory>,
    isLoadingCandidates: Boolean,
    isFat32: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (List<MountPointConfig>, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val enabledMap = remember { mutableStateMapOf<String, Boolean>() }
    var customPaths by remember { mutableStateOf(listOf<MountPointConfig>()) }
    var showCustomPathDialog by remember { mutableStateOf(false) }

    LaunchedEffect(candidateDirectories) {
        for (c in candidateDirectories) {
            if (!enabledMap.containsKey(c.id)) {
                enabledMap[c.id] = c.defaultEnabled && !c.isLocked
            }
        }
    }

    // Calculate total selected size dynamically
    val totalSelectedBytes = remember(enabledMap.toMap(), candidateDirectories, customPaths) {
        val candidateTotal = candidateDirectories
            .filter { enabledMap[it.id] == true && !it.isLocked }
            .sumOf { it.sizeBytes }
        val customTotal = customPaths.filter { it.enabled }.sumOf { it.sizeBytes }
        candidateTotal + customTotal
    }

    val cyberEmerald = Color(0xFF00E676)
    val electricAmber = Color(0xFFFFB300)
    val neonCrimson = Color(0xFFFF1744)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Drag Handle Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            // Header: App Information
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(
                    packageName = appInfo.packageName,
                    size = 48.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = appInfo.packageName,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pre-flight validation warnings
            if (isFat32) {
                Surface(
                    color = electricAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, electricAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = electricAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Partisi MicroSD adalah FAT32: Batas ukuran file 4 GB berlaku pada Virtual Container dan tidak mendukung hak akses POSIX bawaan.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Scrollable Category Cards
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoadingCandidates) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Memindai direktori aplikasi...",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    candidateDirectories.forEach { candidate ->
                        val isChecked = enabledMap[candidate.id] ?: false

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (candidate.isLocked) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isChecked && !candidate.isLocked) cyberEmerald.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = candidate.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            ),
                                            color = if (candidate.isLocked) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )

                                        if (candidate.isLocked) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = "Locked",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    if (candidate.sizeBytes > 0L) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Text(
                                                text = FormatUtils.formatBytes(candidate.sizeBytes),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                ),
                                                color = cyberEmerald,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Switch(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (!candidate.isLocked) {
                                                enabledMap[candidate.id] = checked
                                            }
                                        },
                                        enabled = !candidate.isLocked,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = cyberEmerald,
                                            checkedTrackColor = cyberEmerald.copy(alpha = 0.35f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = candidate.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (candidate.category == MountPointCategory.CACHE_SHADERS) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Catatan: Disarankan tetap di internal flash agar kompilasi shader grafis tidak stutter.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                        color = electricAmber
                                    )
                                }

                                if (candidate.isLocked && candidate.lockReason != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Alasan: ${candidate.lockReason}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = neonCrimson
                                    )
                                }
                            }
                        }
                    }

                    // Custom Paths Added
                    customPaths.forEachIndexed { index, cp ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, cyberEmerald.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Kustom: ${cp.id}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = cp.targetPath,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = cp.enabled,
                                    onCheckedChange = { checked ->
                                        customPaths = customPaths.toMutableList().also { list ->
                                            list[index] = cp.copy(enabled = checked)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = cyberEmerald,
                                        checkedTrackColor = cyberEmerald.copy(alpha = 0.35f)
                                    )
                                )
                            }
                        }
                    }

                    // Button: Add Custom Path
                    OutlinedButton(
                        onClick = { showCustomPathDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.mount_add_custom_path),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Actions: Total Size & Apply Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Batal", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        val configuredPoints = mutableListOf<MountPointConfig>()
                        for (c in candidateDirectories) {
                            if (enabledMap[c.id] == true && !c.isLocked) {
                                configuredPoints.add(
                                    MountPointConfig(
                                        id = c.id,
                                        category = c.category,
                                        sourcePath = c.sdPath,
                                        targetPath = c.internalPath,
                                        enabled = true,
                                        isVirtualContainer = c.isVirtualContainer,
                                        containerImgPath = if (c.isVirtualContainer) c.sdPath else null,
                                        sizeBytes = c.sizeBytes
                                    )
                                )
                            }
                        }
                        configuredPoints.addAll(customPaths.filter { it.enabled })
                        onConfirm(configuredPoints, totalSelectedBytes)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    modifier = Modifier.weight(2f).height(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(AuroraGradientBrush, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Terapkan & Tautkan (${FormatUtils.formatBytes(totalSelectedBytes)})",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Custom Path Input Dialog
    if (showCustomPathDialog) {
        var customPathInput by remember { mutableStateOf("") }
        var showRootPicker by remember { mutableStateOf(false) }
        val dialogFieldColors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        AlertDialog(
            onDismissRequest = { showCustomPathDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    stringResource(R.string.dialog_add_custom_directory_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.dialog_add_custom_directory_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customPathInput,
                        onValueChange = { customPathInput = it },
                        placeholder = { Text("Contoh: Android/data/${appInfo.packageName}/files/assets", fontSize = 12.sp) },
                        trailingIcon = {
                            IconButton(onClick = { showRootPicker = true }) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Pilih Folder",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = dialogFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = customPathInput.trim().removePrefix("/").trimEnd('/')
                        if (path.isNotBlank() && !path.startsWith("data/app")) {
                            val cleanName = path.substringAfterLast('/')
                            val newMp = MountPointConfig(
                                id = "custom_${System.currentTimeMillis() % 10000}",
                                category = MountPointCategory.CUSTOM,
                                sourcePath = "/data/sdext2/MountX/Custom/$cleanName",
                                targetPath = "/data/media/0/$path",
                                enabled = true,
                                sizeBytes = 0L
                            )
                            customPaths = customPaths + newMp
                            showCustomPathDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.dialog_add_custom_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCustomPathDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )

        if (showRootPicker) {
            app.mountx.ui.components.RootDirectoryPickerSheet(
                initialPath = if (customPathInput.isNotBlank()) {
                    if (customPathInput.startsWith("/")) customPathInput else "/data/media/0/$customPathInput"
                } else "/data/media/0",
                sdBasePath = "/data/sdext2",
                onDismiss = { showRootPicker = false },
                onPathSelected = { pickedPath ->
                    val relPath = if (pickedPath.startsWith("/data/media/0/")) {
                        pickedPath.removePrefix("/data/media/0/")
                    } else if (pickedPath.startsWith("/sdcard/")) {
                        pickedPath.removePrefix("/sdcard/")
                    } else {
                        pickedPath.removePrefix("/")
                    }
                    customPathInput = relPath
                }
            )
        }
    }
}
