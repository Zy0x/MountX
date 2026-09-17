package app.mountify.ui.storage

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountify.R
import app.mountify.data.model.FilesystemType
import app.mountify.data.model.MountStatus
import app.mountify.data.model.StorageInfo
import app.mountify.ui.components.CompactScreenHeader
import app.mountify.ui.components.ConfirmDialog
import app.mountify.ui.components.SectionHeader
import app.mountify.ui.components.StatusChip
import app.mountify.ui.theme.CyberEmerald
import app.mountify.ui.theme.ElectricCyan
import app.mountify.ui.theme.MountifyTheme
import app.mountify.ui.theme.NeonCrimson
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

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportConfig(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importConfig(it) }
    }

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
        onClearStatusMessage = { viewModel.clearStatusMessage() },
        onExportConfig = { exportLauncher.launch("mountify_config.json") },
        onImportConfig = { importLauncher.launch(arrayOf("application/json")) },
        onNavigateToBackup = onNavigateToBackup,
        modifier = modifier
    )

    // Two-step Destructive Format Confirmation Dialog
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
    storage: StorageInfo?,
    devices: List<String>,
    isFormatting: Boolean,
    statusMessage: String?,
    selectedDevice: String,
    selectedFs: FilesystemType,
    onSelectedDeviceChange: (String) -> Unit = {},
    onSelectedFsChange: (FilesystemType) -> Unit = {},
    onRefreshDevices: () -> Unit = {},
    onMountPartition: (String, FilesystemType) -> Unit = { _, _ -> },
    onUnmountPartition: () -> Unit = {},
    onFormatClick: () -> Unit = {},
    onClearStatusMessage: () -> Unit = {},
    onExportConfig: () -> Unit = {},
    onImportConfig: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            CompactScreenHeader(
                title = stringResource(R.string.storage_title),
                subtitle = if (storage != null && storage.isMounted) {
                    "${storage.filesystem.uppercase()} • ${storage.mountPoint} • ${FormatUtils.formatBytes(storage.freeBytes)} free"
                } else {
                    stringResource(R.string.storage_not_mounted)
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToBackup,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = stringResource(R.string.backup_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onRefreshDevices,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.storage_detect_devices),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        if (isLandscape) {
            // Dual-Column Responsive Layout for Landscape / Tablet
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)
                ) {
                    item {
                        StatusFeedbackBanner(
                            statusMessage = statusMessage,
                            onDismiss = onClearStatusMessage
                        )
                    }
                    item {
                        StorageTelemetryCard(storage = storage)
                    }
                    item {
                        BlockDeviceControlCard(
                            storage = storage,
                            devices = devices,
                            selectedDevice = selectedDevice,
                            selectedFs = selectedFs,
                            onSelectedDeviceChange = onSelectedDeviceChange,
                            onMount = { onMountPartition(selectedDevice, selectedFs) },
                            onUnmount = onUnmountPartition
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)
                ) {
                    item {
                        FilesystemFormatterCard(
                            selectedDevice = selectedDevice,
                            selectedFs = selectedFs,
                            isFormatting = isFormatting,
                            onSelectedFsChange = onSelectedFsChange,
                            onFormatClick = onFormatClick
                        )
                    }
                    item {
                        QuickBackupCard(
                            onExport = onExportConfig,
                            onImport = onImportConfig,
                            onOpenFullBackup = onNavigateToBackup
                        )
                    }
                }
            }
        } else {
            // Single-Column Responsive Layout for Portrait
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    StatusFeedbackBanner(
                        statusMessage = statusMessage,
                        onDismiss = onClearStatusMessage
                    )
                }

                item {
                    StorageTelemetryCard(storage = storage)
                }

                item {
                    BlockDeviceControlCard(
                        storage = storage,
                        devices = devices,
                        selectedDevice = selectedDevice,
                        selectedFs = selectedFs,
                        onSelectedDeviceChange = onSelectedDeviceChange,
                        onMount = { onMountPartition(selectedDevice, selectedFs) },
                        onUnmount = onUnmountPartition
                    )
                }

                item {
                    FilesystemFormatterCard(
                        selectedDevice = selectedDevice,
                        selectedFs = selectedFs,
                        isFormatting = isFormatting,
                        onSelectedFsChange = onSelectedFsChange,
                        onFormatClick = onFormatClick
                    )
                }

                item {
                    QuickBackupCard(
                        onExport = onExportConfig,
                        onImport = onImportConfig,
                        onOpenFullBackup = onNavigateToBackup
                    )
                }
            }
        }
    }
}

// ── Contextual Feedback Banner ──────────────────────────────
@Composable
private fun StatusFeedbackBanner(
    statusMessage: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = statusMessage != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (statusMessage == null) return@AnimatedVisibility

        val isSuccess = statusMessage.contains("OK")
        val containerColor = if (isSuccess) CyberEmerald.copy(alpha = 0.12f) else NeonCrimson.copy(alpha = 0.12f)
        val borderColor = if (isSuccess) CyberEmerald.copy(alpha = 0.4f) else NeonCrimson.copy(alpha = 0.4f)
        val contentColor = if (isSuccess) CyberEmerald else NeonCrimson

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (statusMessage) {
                        "MOUNT_OK" -> stringResource(R.string.storage_mount_success)
                        "UNMOUNT_OK" -> stringResource(R.string.storage_unmount_success)
                        "FORMAT_OK" -> stringResource(R.string.format_success)
                        "EXPORT_OK" -> stringResource(R.string.backup_success, "JSON")
                        "IMPORT_OK" -> stringResource(R.string.restore_success)
                        else -> statusMessage
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ── Storage Telemetry Hero Card ─────────────────────────────
@Composable
private fun StorageTelemetryCard(
    storage: StorageInfo?,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Mount Status Chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Filled.SdStorage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.storage_specs),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (storage != null && storage.isMounted) {
                                storage.blockDevice
                            } else {
                                stringResource(R.string.storage_not_mounted)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusChip(
                    status = if (storage?.isMounted == true) MountStatus.MOUNTED else MountStatus.UNMOUNTED
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (storage != null && storage.isMounted) {
                // Large Readout: Free Space & Total Space
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.storage_free).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = FormatUtils.formatBytes(storage.freeBytes),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyberEmerald
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.storage_total).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                R.string.storage_capacity_format,
                                FormatUtils.formatBytes(storage.usedBytes),
                                FormatUtils.formatBytes(storage.totalBytes),
                                (storage.usedPercent * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Custom Gradient Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = storage.usedPercent.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ElectricCyan, CyberEmerald)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4-Item Telemetry Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetrySpecChip(
                        label = stringResource(R.string.storage_filesystem),
                        value = storage.filesystem.uppercase(),
                        modifier = Modifier.weight(1f)
                    )
                    TelemetrySpecChip(
                        label = stringResource(R.string.storage_mount_point),
                        value = storage.mountPoint,
                        modifier = Modifier.weight(1.3f)
                    )
                    TelemetrySpecChip(
                        label = stringResource(R.string.storage_used),
                        value = FormatUtils.formatBytes(storage.usedBytes),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Empty / Unmounted Guidance State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = NeonCrimson,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.storage_unmounted_hint),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetrySpecChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

// ── Partition & Block Device Control Hub ─────────────────────
@Composable
private fun BlockDeviceControlCard(
    storage: StorageInfo?,
    devices: List<String>,
    selectedDevice: String,
    selectedFs: FilesystemType,
    onSelectedDeviceChange: (String) -> Unit,
    onMount: () -> Unit,
    onUnmount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.storage_block_device))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Detected Device Chips
                if (devices.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.storage_detected_count, devices.size),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        devices.forEach { dev ->
                            val isSelected = selectedDevice == dev
                            val isActiveTarget = storage?.isMounted == true && storage.blockDevice == dev

                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectedDeviceChange(dev) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Storage,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(dev.substringAfterLast("/"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        if (isActiveTarget) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(CyberEmerald)
                                            )
                                        }
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.sizeIn(minHeight = 44.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Custom Device Path Text Field
                OutlinedTextField(
                    value = selectedDevice,
                    onValueChange = onSelectedDeviceChange,
                    label = { Text(stringResource(R.string.storage_device_label), fontSize = 11.sp) },
                    placeholder = { Text(stringResource(R.string.storage_block_device_hint), fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Dual Mount / Unmount Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isMountedCurrently = storage?.isMounted == true && storage.blockDevice == selectedDevice

                    Button(
                        onClick = onMount,
                        enabled = selectedDevice.isNotBlank() && !isMountedCurrently,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.storage_mount),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = onUnmount,
                        enabled = storage?.isMounted == true,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonCrimson
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCrimson)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.storage_unmount),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

// ── Filesystem Formatter Hub (Danger Zone) ───────────────────
@Composable
private fun FilesystemFormatterCard(
    selectedDevice: String,
    selectedFs: FilesystemType,
    isFormatting: Boolean,
    onSelectedFsChange: (FilesystemType) -> Unit,
    onFormatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            SectionHeader(title = stringResource(R.string.format_title))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = NeonCrimson.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.4f))
            ) {
                Text(
                    text = stringResource(R.string.storage_danger_zone).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    color = NeonCrimson,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Warning Banner
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonCrimson.copy(alpha = 0.08f))
                        .padding(10.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = NeonCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.format_warning_desc),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.format_filesystem),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Interactive Filesystem Choice Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilesystemChoiceCard(
                        type = FilesystemType.F2FS,
                        isSelected = selectedFs == FilesystemType.F2FS,
                        isRecommended = true,
                        title = "F2FS",
                        subtitle = stringResource(R.string.format_f2fs_desc),
                        onClick = { onSelectedFsChange(FilesystemType.F2FS) },
                        modifier = Modifier.weight(1f)
                    )

                    FilesystemChoiceCard(
                        type = FilesystemType.EXT4,
                        isSelected = selectedFs == FilesystemType.EXT4,
                        isRecommended = false,
                        title = "Ext4",
                        subtitle = stringResource(R.string.format_ext4_desc),
                        onClick = { onSelectedFsChange(FilesystemType.EXT4) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Danger Format Button
                Button(
                    onClick = onFormatClick,
                    enabled = selectedDevice.isNotBlank() && !isFormatting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCrimson,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    if (isFormatting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.format_in_progress),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    } else {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.format_button),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesystemChoiceCard(
    type: FilesystemType,
    isSelected: Boolean,
    isRecommended: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) {
        if (isRecommended) CyberEmerald else MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val containerColor = if (isSelected) {
        if (isRecommended) CyberEmerald.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier.sizeIn(minHeight = 72.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isRecommended) {
                    Badge(containerColor = CyberEmerald) {
                        Text(
                            text = stringResource(R.string.common_recommended),
                            modifier = Modifier.padding(horizontal = 4.dp),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

// ── Configuration Portability Hub (SAF) ──────────────────────
@Composable
private fun QuickBackupCard(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenFullBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.storage_backup_quick))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.backup_config_desc),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Dual Portability Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onExport,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.backup_config_export),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = onImport,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.backup_config_import),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Link to Full Backup & Restore Screen
                Surface(
                    onClick = onOpenFullBackup,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.backup_title),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.backup_data_desc),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Previews ────────────────────────────────────────────────
@Preview(name = "Storage Screen - Dark Theme", showBackground = true)
@Composable
private fun StorageScreenPreviewDark() {
    MountifyTheme(dynamicColor = false) {
        StorageContent(
            storage = StorageInfo(
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
            onClearStatusMessage = {},
            onExportConfig = {},
            onImportConfig = {},
            onNavigateToBackup = {}
        )
    }
}

