package app.mountify.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mountify.R
import app.mountify.data.model.FilesystemType
import app.mountify.ui.components.ConfirmDialog
import app.mountify.ui.components.ErrorCard
import app.mountify.ui.components.SectionHeader
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
            TopAppBar(
                title = { Text(stringResource(R.string.storage_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onRefreshDevices) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.storage_detect_devices))
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage Statistics Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.storage_overview),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (storage != null && storage!!.isMounted) {
                            Text(
                                text = "Device: ${storage!!.blockDevice}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Mount Point: ${storage!!.mountPoint}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Filesystem: ${storage!!.filesystem.uppercase()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { storage!!.usedPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Used: ${FormatUtils.formatBytes(storage!!.usedBytes)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Free: ${FormatUtils.formatBytes(storage!!.freeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryTeal
                                )
                                Text(
                                    text = "Total: ${FormatUtils.formatBytes(storage!!.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.storage_not_mounted),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Block Device Selection & Mount/Unmount
            item {
                SectionHeader(title = stringResource(R.string.storage_block_device))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = selectedDevice,
                            onValueChange = onSelectedDeviceChange,
                            label = { Text(stringResource(R.string.storage_block_device)) },
                            placeholder = { Text(stringResource(R.string.storage_block_device_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (devices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Detected devices:",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                devices.forEach { dev ->
                                    FilterChip(
                                        selected = selectedDevice == dev,
                                        onClick = { onSelectedDeviceChange(dev) },
                                        label = { Text(dev.substringAfterLast("/")) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { onMountPartition(selectedDevice, selectedFs) },
                                enabled = selectedDevice.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.storage_mount))
                            }

                            OutlinedButton(
                                onClick = onUnmountPartition,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.storage_unmount))
                            }
                        }
                    }
                }
            }

            // Format Partition Section
            item {
                SectionHeader(title = stringResource(R.string.format_title))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.format_warning_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.format_filesystem),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        FilesystemType.values().forEach { fs ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedFs == fs,
                                    onClick = { onSelectedFsChange(fs) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = fs.label, fontWeight = FontWeight.Medium)
                                        if (fs.isRecommended) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Badge(containerColor = StatusSuccess) {
                                                Text(
                                                    text = stringResource(R.string.common_recommended),
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onFormatClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = selectedDevice.isNotBlank() && !isFormatting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isFormatting)
                                    stringResource(R.string.format_in_progress)
                                else
                                    stringResource(R.string.format_button)
                            )
                        }
                    }
                }
            }

            // Backup & Restore Shortcut Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    onClick = onNavigateToBackup
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.storage_backup),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.backup_config_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
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
