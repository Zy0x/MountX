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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountify.R
import app.mountify.data.model.FilesystemType
import app.mountify.data.model.InternalStorageInfo
import app.mountify.data.model.MountStatus
import app.mountify.data.model.PartitionInfo
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

@Composable
fun StorageScreen(
    viewModel: StorageViewModel,
    onNavigateToBackup: () -> Unit,
    onNavigateToGames: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val storage by viewModel.storageInfo.collectAsState()
    val internalStorage by viewModel.internalStorageInfo.collectAsState()
    val partitions by viewModel.partitions.collectAsState()
    val selectedPartition by viewModel.selectedPartition.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isFormatting by viewModel.isFormatting.collectAsState()
    val isCheckingFs by viewModel.isCheckingFs.collectAsState()
    val fsCheckOutput by viewModel.fsCheckOutput.collectAsState()
    val partitionLabel by viewModel.partitionLabel.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val offloadedStats by viewModel.offloadedStats.collectAsState()
    val configuredSdBase by viewModel.configuredSdBase.collectAsState()

    var selectedFs by remember { mutableStateOf(FilesystemType.F2FS) }
    var showFormatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.detectPartitions(force = false)
    }

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

    StorageContent(
        storage = storage,
        internalStorage = internalStorage,
        partitions = partitions,
        selectedPartition = selectedPartition,
        configuredSdBase = configuredSdBase,
        isScanning = isScanning,
        isFormatting = isFormatting,
        isCheckingFs = isCheckingFs,
        partitionLabel = partitionLabel,
        selectedFs = selectedFs,
        statusMessage = statusMessage,
        offloadedStats = offloadedStats,
        onSelectPartition = { viewModel.selectPartition(it) },
        onSelectedFsChange = { selectedFs = it },
        onPartitionLabelChange = { viewModel.setPartitionLabel(it) },
        onRefreshPartitions = { viewModel.detectPartitions() },
        onMountPartition = { dev, fs -> viewModel.mountPartition(dev, fs) },
        onUnmountPartition = { viewModel.unmountPartition() },
        onFormatClick = { showFormatDialog = true },
        onCheckFilesystem = { part -> viewModel.checkFilesystem(part) },
        onClearStatusMessage = { viewModel.clearStatusMessage() },
        onExportConfig = { exportLauncher.launch("mountify_config.json") },
        onImportConfig = { importLauncher.launch(arrayOf("application/json")) },
        onNavigateToBackup = onNavigateToBackup,
        onNavigateToGames = onNavigateToGames,
        modifier = modifier
    )

    // Two-step Destructive Format Confirmation Dialog
    if (showFormatDialog) {
        val targetDevicePath = selectedPartition?.path ?: configuredSdBase
        ConfirmDialog(
            title = stringResource(R.string.format_confirm_title),
            message = stringResource(R.string.format_confirm_desc, targetDevicePath),
            confirmText = stringResource(R.string.format_confirm_button),
            isDestructive = true,
            onConfirm = {
                showFormatDialog = false
                viewModel.formatPartition(targetDevicePath, selectedFs, partitionLabel)
            },
            onDismiss = { showFormatDialog = false }
        )
    }

    // Filesystem Check (fsck) Terminal Output Dialog
    if (fsCheckOutput != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearFsCheckOutput() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.storage_fsck_result_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    Text(
                        text = fsCheckOutput ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        color = CyberEmerald,
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearFsCheckOutput() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(stringResource(R.string.common_close), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun StorageContent(
    storage: StorageInfo?,
    internalStorage: InternalStorageInfo?,
    partitions: List<PartitionInfo>,
    selectedPartition: PartitionInfo?,
    configuredSdBase: String,
    isScanning: Boolean,
    isFormatting: Boolean,
    isCheckingFs: Boolean,
    partitionLabel: String,
    selectedFs: FilesystemType,
    statusMessage: String?,
    offloadedStats: Pair<Int, Long>,
    onSelectPartition: (PartitionInfo) -> Unit = {},
    onSelectedFsChange: (FilesystemType) -> Unit = {},
    onPartitionLabelChange: (String) -> Unit = {},
    onRefreshPartitions: () -> Unit = {},
    onMountPartition: (String, FilesystemType) -> Unit = { _, _ -> },
    onUnmountPartition: () -> Unit = {},
    onFormatClick: () -> Unit = {},
    onCheckFilesystem: (PartitionInfo) -> Unit = {},
    onClearStatusMessage: () -> Unit = {},
    onExportConfig: () -> Unit = {},
    onImportConfig: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToGames: () -> Unit = {},
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
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onRefreshPartitions,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.storage_detect_devices),
                            tint = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
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
                        DualStorageTelemetryCard(
                            storage = storage,
                            internalStorage = internalStorage,
                            offloadedStats = offloadedStats,
                            onNavigateToGames = onNavigateToGames
                        )
                    }
                    item {
                        PartitionScannerCard(
                            partitions = partitions,
                            selectedPartition = selectedPartition,
                            isScanning = isScanning,
                            onSelectPartition = onSelectPartition,
                            onRefresh = onRefreshPartitions
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
                        App2sdControlHubCard(
                            storage = storage,
                            selectedPartition = selectedPartition,
                            selectedFs = selectedFs,
                            configuredSdBase = configuredSdBase,
                            isCheckingFs = isCheckingFs,
                            onMount = {
                                val dev = selectedPartition?.path ?: ""
                                if (dev.isNotBlank()) onMountPartition(dev, selectedFs)
                            },
                            onUnmount = onUnmountPartition,
                            onCheckFilesystem = {
                                selectedPartition?.let { onCheckFilesystem(it) }
                            }
                        )
                    }
                    item {
                        FilesystemFormatterCard(
                            selectedPartition = selectedPartition,
                            selectedFs = selectedFs,
                            partitionLabel = partitionLabel,
                            isFormatting = isFormatting,
                            onSelectedFsChange = onSelectedFsChange,
                            onPartitionLabelChange = onPartitionLabelChange,
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
                    DualStorageTelemetryCard(
                        storage = storage,
                        internalStorage = internalStorage,
                        offloadedStats = offloadedStats,
                        onNavigateToGames = onNavigateToGames
                    )
                }

                item {
                    PartitionScannerCard(
                        partitions = partitions,
                        selectedPartition = selectedPartition,
                        isScanning = isScanning,
                        onSelectPartition = onSelectPartition,
                        onRefresh = onRefreshPartitions
                    )
                }

                item {
                    App2sdControlHubCard(
                        storage = storage,
                        selectedPartition = selectedPartition,
                        selectedFs = selectedFs,
                        configuredSdBase = configuredSdBase,
                        isCheckingFs = isCheckingFs,
                        onMount = {
                            val dev = selectedPartition?.path ?: ""
                            if (dev.isNotBlank()) onMountPartition(dev, selectedFs)
                        },
                        onUnmount = onUnmountPartition,
                        onCheckFilesystem = {
                            selectedPartition?.let { onCheckFilesystem(it) }
                        }
                    )
                }

                item {
                    FilesystemFormatterCard(
                        selectedPartition = selectedPartition,
                        selectedFs = selectedFs,
                        partitionLabel = partitionLabel,
                        isFormatting = isFormatting,
                        onSelectedFsChange = onSelectedFsChange,
                        onPartitionLabelChange = onPartitionLabelChange,
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

// ── Dual Storage Telemetry Hero Card ────────────────────────
@Composable
private fun DualStorageTelemetryCard(
    storage: StorageInfo?,
    internalStorage: InternalStorageInfo?,
    offloadedStats: Pair<Int, Long>,
    onNavigateToGames: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Mount Status
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
                                "${storage.blockDevice} • ${storage.mountPoint}"
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

            // 1. Internal Storage Telemetry Row
            if (internalStorage != null && internalStorage.totalBytes > 0) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Storage,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.storage_internal_title).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = stringResource(
                                R.string.storage_capacity_format,
                                FormatUtils.formatBytes(internalStorage.usedBytes),
                                FormatUtils.formatBytes(internalStorage.totalBytes),
                                (internalStorage.usedPercent * 100).toInt()
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Internal Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = internalStorage.usedPercent.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(ElectricCyan, MaterialTheme.colorScheme.primary)
                                    )
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // 2. MicroSD App2SD Storage Telemetry Row
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.SdStorage,
                            contentDescription = null,
                            tint = CyberEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.storage_microsd_title).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (storage != null && storage.isMounted) {
                        Text(
                            text = stringResource(
                                R.string.storage_capacity_format,
                                FormatUtils.formatBytes(storage.usedBytes),
                                FormatUtils.formatBytes(storage.totalBytes),
                                (storage.usedPercent * 100).toInt()
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = CyberEmerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (storage != null && storage.isMounted) {
                    // MicroSD Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3 Spec Chips
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
                            label = stringResource(R.string.storage_free),
                            value = FormatUtils.formatBytes(storage.freeBytes),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // Unmounted Callout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = NeonCrimson,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.storage_unmounted_hint),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Offloaded Games & Data Saved Callout
            if (offloadedStats.first > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = onNavigateToGames,
                    shape = RoundedCornerShape(10.dp),
                    color = CyberEmerald.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CyberEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.storage_offloaded_summary,
                                    offloadedStats.first,
                                    FormatUtils.formatBytes(offloadedStats.second)
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = CyberEmerald,
                            modifier = Modifier.size(16.dp)
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
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.5.sp,
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

// ── Disks & Partitions Inspector Card ───────────────────────
@Composable
private fun PartitionScannerCard(
    partitions: List<PartitionInfo>,
    selectedPartition: PartitionInfo?,
    isScanning: Boolean,
    onSelectPartition: (PartitionInfo) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val visiblePartitions = if (isExpanded || partitions.size <= 4) partitions else partitions.take(4)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            SectionHeader(title = stringResource(R.string.storage_partitions_title))

            if (isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.storage_scanning),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.storage_partitions_desc),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (partitions.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.storage_partition_no_partitions),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        visiblePartitions.forEach { part ->
                            key(part.path) {
                                val isSelected = selectedPartition?.path == part.path
                                PartitionListItem(
                                    partition = part,
                                    isSelected = isSelected,
                                    onClick = { onSelectPartition(part) }
                                )
                            }
                        }
                    }

                    if (partitions.size > 4) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (isExpanded) {
                                    stringResource(R.string.storage_show_less)
                                } else {
                                    "${stringResource(R.string.storage_show_more)} (${partitions.size})"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartitionListItem(
    partition: PartitionInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) {
        if (partition.isTargetMount) CyberEmerald else MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val containerColor = if (isSelected) {
        if (partition.isTargetMount) CyberEmerald.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SdStorage,
                contentDescription = null,
                tint = if (partition.isTargetMount) CyberEmerald else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = partition.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (partition.sizeBytes > 0) {
                        Text(
                            text = FormatUtils.formatBytes(partition.sizeBytes),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Badges Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Filesystem Badge
                    if (partition.fsType.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (partition.fsType.equals("f2fs", ignoreCase = true) || partition.fsType.equals("ext4", ignoreCase = true)) {
                                CyberEmerald.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = partition.fsType.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (partition.fsType.equals("f2fs", ignoreCase = true) || partition.fsType.equals("ext4", ignoreCase = true)) {
                                    CyberEmerald
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Mount Status Badge
                    if (partition.isTargetMount) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CyberEmerald.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = stringResource(R.string.storage_partition_app2sd_target),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberEmerald,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else if (partition.isMounted) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ElectricCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = partition.mountPoint ?: "Mounted",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                maxLines = 1
                            )
                        }
                    } else if (partition.isSuitableForApp2sd) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = stringResource(R.string.storage_partition_suitable),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Label badge
                    if (!partition.label.isNullOrBlank()) {
                        Text(
                            text = "\"${partition.label}\"",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (partition.isTargetMount) CyberEmerald else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ── App2SD & Mount Control Hub ──────────────────────────────
@Composable
private fun App2sdControlHubCard(
    storage: StorageInfo?,
    selectedPartition: PartitionInfo?,
    selectedFs: FilesystemType,
    configuredSdBase: String,
    isCheckingFs: Boolean,
    onMount: () -> Unit,
    onUnmount: () -> Unit,
    onCheckFilesystem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val devPath = selectedPartition?.path ?: ""
    val isLinuxFs = selectedPartition?.fsType?.equals("f2fs", ignoreCase = true) == true ||
            selectedPartition?.fsType?.equals("ext4", ignoreCase = true) == true
    val isMountedCurrently = storage?.isMounted == true &&
            (storage.blockDevice == devPath || selectedPartition?.isTargetMount == true)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.storage_app2sd_hub))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Target Partition Readout
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.storage_selected_partition).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (devPath.isNotBlank()) devPath else stringResource(R.string.storage_no_devices_detected),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = configuredSdBase,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // App2SD Readiness Checklist
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        App2sdCheckItem(
                            label = stringResource(R.string.storage_app2sd_step_root),
                            isPassed = true,
                            sublabel = "libsu sandbox active"
                        )
                        App2sdCheckItem(
                            label = stringResource(R.string.storage_app2sd_step_partition),
                            isPassed = isLinuxFs,
                            sublabel = if (isLinuxFs) selectedPartition?.fsType?.uppercase() ?: "Linux" else "Requires F2FS or Ext4"
                        )
                        App2sdCheckItem(
                            label = stringResource(R.string.storage_app2sd_step_mounted),
                            isPassed = isMountedCurrently,
                            sublabel = if (isMountedCurrently) "Active at $configuredSdBase" else "Standby (Not mounted)"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dual Mount / Unmount Action Bar (40-44dp height)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onMount,
                        enabled = devPath.isNotBlank() && !isMountedCurrently,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.storage_mount),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                            .height(40.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCrimson)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.storage_unmount),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filesystem Check Utility Button (34dp)
                FilledTonalButton(
                    onClick = onCheckFilesystem,
                    enabled = devPath.isNotBlank() && !isMountedCurrently && !isCheckingFs,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    if (isCheckingFs) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.storage_fsck_checking),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.storage_fsck_button),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (isMountedCurrently) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.storage_fsck_mounted_warning),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun App2sdCheckItem(
    label: String,
    isPassed: Boolean,
    sublabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isPassed) Icons.Default.Check else Icons.Default.Info,
                contentDescription = null,
                tint = if (isPassed) CyberEmerald else NeonCrimson,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = sublabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (isPassed) CyberEmerald else NeonCrimson
        )
    }
}

// ── Filesystem Formatter Hub (Danger Zone) ───────────────────
@Composable
private fun FilesystemFormatterCard(
    selectedPartition: PartitionInfo?,
    selectedFs: FilesystemType,
    partitionLabel: String,
    isFormatting: Boolean,
    onSelectedFsChange: (FilesystemType) -> Unit,
    onPartitionLabelChange: (String) -> Unit,
    onFormatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val devPath = selectedPartition?.path ?: ""

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

                Spacer(modifier = Modifier.height(12.dp))

                // Partition Label Input Field (38dp Compact Input per AGENTS.md rule 3.5)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.storage_label_field),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp)
                        ) {
                            if (partitionLabel.isBlank()) {
                                Text(
                                    text = stringResource(R.string.storage_label_hint),
                                    style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                )
                            }
                            BasicTextField(
                                value = partitionLabel,
                                onValueChange = onPartitionLabelChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Danger Format Button (40-44dp height)
                Button(
                    onClick = onFormatClick,
                    enabled = devPath.isNotBlank() && !isFormatting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCrimson,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
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
                            text = if (devPath.isNotBlank()) {
                                "${stringResource(R.string.format_button)} (${devPath.substringAfterLast("/")})"
                            } else {
                                stringResource(R.string.format_button)
                            },
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
            internalStorage = InternalStorageInfo(
                totalBytes = 128_000_000_000L,
                usedBytes = 45_000_000_000L,
                freeBytes = 83_000_000_000L
            ),
            partitions = listOf(
                PartitionInfo(
                    path = "/dev/block/mmcblk0p1",
                    name = "mmcblk0p1",
                    diskName = "mmcblk0",
                    partitionNumber = 1,
                    sizeBytes = 32_000_000_000L,
                    fsType = "exfat",
                    mountPoint = "/storage/1234-5678",
                    label = "SD_CARD",
                    isMounted = true,
                    isTargetMount = false,
                    isSuitableForApp2sd = false
                ),
                PartitionInfo(
                    path = "/dev/block/mmcblk0p2",
                    name = "mmcblk0p2",
                    diskName = "mmcblk0",
                    partitionNumber = 2,
                    sizeBytes = 32_000_000_000L,
                    fsType = "f2fs",
                    mountPoint = "/data/sdext2",
                    label = "sdext2",
                    isMounted = true,
                    isTargetMount = true,
                    isSuitableForApp2sd = true
                )
            ),
            selectedPartition = PartitionInfo(
                path = "/dev/block/mmcblk0p2",
                name = "mmcblk0p2",
                diskName = "mmcblk0",
                partitionNumber = 2,
                sizeBytes = 32_000_000_000L,
                fsType = "f2fs",
                mountPoint = "/data/sdext2",
                label = "sdext2",
                isMounted = true,
                isTargetMount = true,
                isSuitableForApp2sd = true
            ),
            configuredSdBase = "/data/sdext2",
            isScanning = false,
            isFormatting = false,
            isCheckingFs = false,
            partitionLabel = "sdext2",
            selectedFs = FilesystemType.F2FS,
            statusMessage = null,
            offloadedStats = Pair(3, 42_500_000_000L),
            onSelectPartition = {},
            onSelectedFsChange = {},
            onPartitionLabelChange = {},
            onRefreshPartitions = {},
            onMountPartition = { _, _ -> },
            onUnmountPartition = {},
            onFormatClick = {},
            onCheckFilesystem = {},
            onClearStatusMessage = {},
            onExportConfig = {},
            onImportConfig = {},
            onNavigateToBackup = {},
            onNavigateToGames = {}
        )
    }
}


