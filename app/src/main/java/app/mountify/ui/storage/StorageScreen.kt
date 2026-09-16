package app.mountify.ui.storage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountify.R
import app.mountify.data.model.FilesystemType
import app.mountify.ui.components.CompactScreenHeader
import app.mountify.ui.components.ConfirmDialog
import app.mountify.ui.components.ErrorCard
import app.mountify.ui.components.SectionHeader
import app.mountify.ui.theme.CoralError
import app.mountify.ui.theme.ElectricCyan
import app.mountify.ui.theme.EmeraldActive
import app.mountify.ui.theme.ObsidianBg
import app.mountify.ui.theme.ObsidianBorder
import app.mountify.ui.theme.ObsidianCard
import app.mountify.ui.theme.SecondaryTeal
import app.mountify.ui.theme.StatusSuccess
import app.mountify.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    viewModel: StorageViewModel,
    onNavigateToBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storage by viewModel.storageInfo.collectAsState()
    val devices by viewModel.detectedDevices.collectAsState()
    val isFormatting by viewModel.isFormatting.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var selectedDevice by remember { mutableStateOf("") }
    var selectedFs by remember { mutableStateOf(FilesystemType.F2FS) }
    var showFormatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(devices) {
        if (selectedDevice.isBlank() && devices.isNotEmpty()) {
            selectedDevice = devices.first()
        }
    }

    StorageContent(
        storage = storage,
        devices = devices,
        isFormatting = isFormatting,
        statusMessage = statusMessage,
        selectedDevice = selectedDevice,
        selectedFs = selectedFs,
        onSelectedDeviceChange = { selectedDevice = it },
        onSelectedFsChange = { selectedFs = it },
        onRefreshDevices = { viewModel.detectDevices() },
        onMountPartition = { dev, fs -> viewModel.mountPartition(dev, fs) },
        onUnmountPartition = { viewModel.unmountPartition() },
        onFormatClick = { showFormatDialog = true },
        onNavigateToBackup = onNavigateToBackup,
        modifier = modifier
    )

    // Format Confirmation Dialog
    if (showFormatDialog) {
        ConfirmDialog(
            title = stringResource(R.string.format_confirm_title),
            message = stringResource(R.string.format_confirm_desc, selectedDevice),
            confirmText = stringResource(R.string.format_confirm_button),
            isDestructive = true,
            onConfirm = {
                showFormatDialog = false
                viewModel.formatPartition(selectedDevice, selectedFs)
            },
            onDismiss = { showFormatDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageContent(
    storage: app.mountify.data.model.StorageInfo?,
    devices: List<String>,
    isFormatting: Boolean,
    statusMessage: String?,
    selectedDevice: String,
    selectedFs: FilesystemType,
    onSelectedDeviceChange: (String) -> Unit,
    onSelectedFsChange: (FilesystemType) -> Unit,
    onRefreshDevices: () -> Unit,
    onMountPartition: (String, FilesystemType) -> Unit,
    onUnmountPartition: () -> Unit,
    onFormatClick: () -> Unit,
    onNavigateToBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            CompactScreenHeader(
                title = stringResource(R.string.storage_title),
                subtitle = if (storage != null && storage!!.isMounted) {
                    "${storage!!.filesystem.uppercase()} • ${storage!!.mountPoint}"
                } else {
                    stringResource(R.string.storage_not_mounted)
                },
                actions = {
                    IconButton(
                        onClick = onRefreshDevices,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.storage_detect_devices),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        },
        containerColor = ObsidianBg,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Storage Statistics Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.storage_overview),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (storage != null && storage!!.isMounted) {
                            Text(
                                text = "Device: ${storage!!.blockDevice}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                            )
                            Text(
                                text = "Mount Point: ${storage!!.mountPoint}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                            )
                            Text(
                                text = "Filesystem: ${storage!!.filesystem.uppercase()}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = ElectricCyan
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { storage!!.usedPercent },
                                color = ElectricCyan,
                                trackColor = Color(0xFF1C2230),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Used: ${FormatUtils.formatBytes(storage!!.usedBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                                Text(
                                    text = "Free: ${FormatUtils.formatBytes(storage!!.freeBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = EmeraldActive,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Total: ${FormatUtils.formatBytes(storage!!.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.storage_not_mounted),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = CoralError
                            )
                        }
                    }
                }
            }

            // Block Device Selection & Mount/Unmount
            item {
                SectionHeader(title = stringResource(R.string.storage_block_device))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        OutlinedTextField(
                            value = selectedDevice,
                            onValueChange = onSelectedDeviceChange,
                            label = { Text(stringResource(R.string.storage_block_device), fontSize = 12.sp) },
                            placeholder = { Text(stringResource(R.string.storage_block_device_hint), fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ObsidianCard,
                                unfocusedContainerColor = ObsidianCard,
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = ObsidianBorder
                            )
                        )

                        if (devices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Detected devices:",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                devices.forEach { dev ->
                                    FilterChip(
                                        selected = selectedDevice == dev,
                                        onClick = { onSelectedDeviceChange(dev) },
                                        label = { Text(dev.substringAfterLast("/"), fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onMountPartition(selectedDevice, selectedFs) },
                                enabled = selectedDevice.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp, max = 46.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.storage_mount),
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                )
                            }

                            OutlinedButton(
                                onClick = onUnmountPartition,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp, max = 46.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = CoralError)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.storage_unmount),
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // Format Partition Section
            item {
                SectionHeader(title = stringResource(R.string.format_title))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.format_warning_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = CoralError
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.format_filesystem),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        FilesystemType.values().forEach { fs ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedFs == fs,
                                    onClick = { onSelectedFsChange(fs) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = fs.label, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                        if (fs.isRecommended) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Badge(containerColor = EmeraldActive) {
                                                Text(
                                                    text = stringResource(R.string.common_recommended),
                                                    modifier = Modifier.padding(horizontal = 4.dp),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onFormatClick,
                            colors = ButtonDefaults.buttonColors(containerColor = CoralError),
                            enabled = selectedDevice.isNotBlank() && !isFormatting,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp, max = 46.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isFormatting)
                                    stringResource(R.string.format_in_progress)
                                else
                                    stringResource(R.string.format_button),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Backup & Restore Shortcut Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131926)),
                    border = BorderStroke(1.dp, Color(0xFF222E46)),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToBackup
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.storage_backup),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.backup_config_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Storage Screen - Dark Theme", showBackground = true)
@Composable
private fun StorageScreenPreviewDark() {
    app.mountify.ui.theme.MountifyTheme(dynamicColor = false) {
        StorageContent(
            storage = app.mountify.data.model.StorageInfo(
                blockDevice = "/dev/block/mmcblk0p2",
                mountPoint = "/data/sdext2",
                filesystem = "f2fs",
                totalBytes = 64_000_000_000L,
                usedBytes = 28_000_000_000L,
                freeBytes = 36_000_000_000L,
                isMounted = true
            ),
            devices = listOf("/dev/block/mmcblk0p1", "/dev/block/mmcblk0p2"),
            isFormatting = false,
            statusMessage = null,
            selectedDevice = "/dev/block/mmcblk0p2",
            selectedFs = FilesystemType.F2FS,
            onSelectedDeviceChange = {},
            onSelectedFsChange = {},
            onRefreshDevices = {},
            onMountPartition = { _, _ -> },
            onUnmountPartition = {},
            onFormatClick = {},
            onNavigateToBackup = {}
        )
    }
}
