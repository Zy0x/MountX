package app.mihon.ui.storage

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
import app.mihon.R
import app.mihon.data.model.FilesystemType
import app.mihon.ui.components.ConfirmDialog
import app.mihon.ui.components.ErrorCard
import app.mihon.ui.components.SectionHeader
import app.mihon.ui.theme.SecondaryTeal
import app.mihon.ui.theme.StatusSuccess
import app.mihon.util.FormatUtils

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.detectDevices() }) {
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
                            onValueChange = { selectedDevice = it },
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
                                        onClick = { selectedDevice = dev },
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
                                onClick = { viewModel.mountPartition(selectedDevice, selectedFs) },
                                enabled = selectedDevice.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.storage_mount))
                            }

                            OutlinedButton(
                                onClick = { viewModel.unmountPartition() },
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
                                    onClick = { selectedFs = fs }
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
                            onClick = { showFormatDialog = true },
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
