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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mountify.R
import app.mountify.data.model.FilesystemType
import app.mountify.data.model.InternalStorageInfo
import app.mountify.data.model.MountStatus
import app.mountify.data.model.PartitionInfo
import app.mountify.data.model.PartitionSchemeConfig
import app.mountify.data.model.SdCardDiskInfo
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
    val diskInfo by viewModel.diskInfo.collectAsState()
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

    // Wizard states
    val isWizardOpen by viewModel.isWizardOpen.collectAsState()
    val wizardPartitions by viewModel.wizardPartitions.collectAsState()
    val isRepartitioning by viewModel.isRepartitioning.collectAsState()
    val repartitionError by viewModel.repartitionError.collectAsState()

    var showFormatDialog by remember { mutableStateOf(false) }
    var showRepartitionFinalConfirm by remember { mutableStateOf(false) }

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
        diskInfo = diskInfo,
        partitions = partitions,
        selectedPartition = selectedPartition,
        configuredSdBase = configuredSdBase,
        isScanning = isScanning,
        isCheckingFs = isCheckingFs,
        statusMessage = statusMessage,
        offloadedStats = offloadedStats,
        onSelectPartition = { viewModel.selectPartition(it) },
        onRefreshPartitions = { viewModel.detectPartitions(force = true) },
        onMountPartition = { dev, fs -> viewModel.mountPartition(dev, fs) },
        onUnmountPartition = { viewModel.unmountPartition() },
        onFormatClick = { showFormatDialog = true },
        onCheckFilesystem = { part -> viewModel.checkFilesystem(part) },
        onOpenWizard = { viewModel.openPartitionWizard() },
        onClearStatusMessage = { viewModel.clearStatusMessage() },
        onExportConfig = { exportLauncher.launch("mountify_config.json") },
        onImportConfig = { importLauncher.launch(arrayOf("application/json")) },
        onNavigateToBackup = onNavigateToBackup,
        onNavigateToGames = onNavigateToGames,
        modifier = modifier
    )

    // Single Partition Format Dialog
    if (showFormatDialog) {
        SingleFormatDialog(
            partition = selectedPartition,
            defaultLabel = partitionLabel,
            isFormatting = isFormatting,
            onDismiss = { showFormatDialog = false },
            onConfirmFormat = { fs, label ->
                showFormatDialog = false
                val targetPath = selectedPartition?.path ?: configuredSdBase
                viewModel.formatPartition(targetPath, fs, label)
            }
        )
    }

    // Partition Wizard Dialog
    if (isWizardOpen) {
        PartitionWizardDialog(
            diskInfo = diskInfo,
            partitions = wizardPartitions,
            isRepartitioning = isRepartitioning,
            repartitionError = repartitionError,
            onClose = { viewModel.closePartitionWizard() },
            onUpdateSizeKb = { idx, sizeKb -> viewModel.updatePartitionSizeKb(idx, sizeKb) },
            onUpdateFs = { idx, fs -> viewModel.updatePartitionFsType(idx, fs) },
            onUpdateLabel = { idx, label -> viewModel.updatePartitionLabel(idx, label) },
            onAddPartition = { viewModel.addPartition() },
            onRemovePartition = { idx -> viewModel.removePartition(idx) },
            onAutoBalance = { viewModel.autoBalanceWizardPartitions() },
            onTriggerApply = { showRepartitionFinalConfirm = true }
        )
    }

    // Multi-Step Repartition Final Confirmation Dialog
    if (showRepartitionFinalConfirm) {
        val targetDiskPath = diskInfo?.devicePath ?: "/dev/block/mmcblk0"
        val summaryLines = wizardPartitions.mapIndexed { idx, p ->
            "• ${stringResource(R.string.storage_wizard_partition_n, idx + 1)}: ${String.format(java.util.Locale.US, "%.1f GB", p.sizeGb)} (${p.fsType.label}, \"${p.label}\")"
        }.joinToString("\n")

        ConfirmDialog(
            title = stringResource(R.string.storage_wizard_confirm_title),
            message = "${stringResource(R.string.storage_wizard_confirm_warning)}\n\n" +
                    "Target: $targetDiskPath\n\n$summaryLines",
            confirmText = stringResource(R.string.storage_wizard_apply_btn),
            isDestructive = true,
            onConfirm = {
                showRepartitionFinalConfirm = false
                viewModel.executeRepartition()
            },
            onDismiss = { showRepartitionFinalConfirm = false }
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
    internalStorage: InternalStorageInfo? = null,
    diskInfo: SdCardDiskInfo? = null,
    partitions: List<PartitionInfo> = emptyList(),
    selectedPartition: PartitionInfo? = null,
    configuredSdBase: String = "/data/sdext2",
    isScanning: Boolean = false,
    isCheckingFs: Boolean = false,
    statusMessage: String? = null,
    offloadedStats: Pair<Int, Long> = Pair(0, 0L),
    onSelectPartition: (PartitionInfo) -> Unit = {},
    onRefreshPartitions: () -> Unit = {},
    onMountPartition: (String, FilesystemType) -> Unit = { _, _ -> },
    onUnmountPartition: () -> Unit = {},
    onFormatClick: () -> Unit = {},
    onCheckFilesystem: (PartitionInfo) -> Unit = {},
    onOpenWizard: () -> Unit = {},
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
                        .weight(1.1f)
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
                        SdCardVisualDiskMapCard(
                            diskInfo = diskInfo,
                            partitions = partitions,
                            selectedPartition = selectedPartition,
                            onSelectPartition = onSelectPartition,
                            onOpenWizard = onOpenWizard
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
                        .weight(0.9f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)
                ) {
                    item {
                        PartitionActionHubCard(
                            storage = storage,
                            selectedPartition = selectedPartition,
                            configuredSdBase = configuredSdBase,
                            isCheckingFs = isCheckingFs,
                            onMount = {
                                val dev = selectedPartition?.path ?: ""
                                val fs = when (selectedPartition?.fsType?.lowercase()) {
                                    "f2fs" -> FilesystemType.F2FS
                                    "ext4" -> FilesystemType.EXT4
                                    "vfat", "fat32" -> FilesystemType.FAT32
                                    "exfat" -> FilesystemType.EXFAT
                                    else -> FilesystemType.F2FS
                                }
                                if (dev.isNotBlank()) onMountPartition(dev, fs)
                            },
                            onUnmount = onUnmountPartition,
                            onFormat = onFormatClick,
                            onCheckFilesystem = {
                                selectedPartition?.let { onCheckFilesystem(it) }
                            }
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
                    SdCardVisualDiskMapCard(
                        diskInfo = diskInfo,
                        partitions = partitions,
                        selectedPartition = selectedPartition,
                        onSelectPartition = onSelectPartition,
                        onOpenWizard = onOpenWizard
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
                    PartitionActionHubCard(
                        storage = storage,
                        selectedPartition = selectedPartition,
                        configuredSdBase = configuredSdBase,
                        isCheckingFs = isCheckingFs,
                        onMount = {
                            val dev = selectedPartition?.path ?: ""
                            val fs = when (selectedPartition?.fsType?.lowercase()) {
                                "f2fs" -> FilesystemType.F2FS
                                "ext4" -> FilesystemType.EXT4
                                "vfat", "fat32" -> FilesystemType.FAT32
                                "exfat" -> FilesystemType.EXFAT
                                else -> FilesystemType.F2FS
                            }
                            if (dev.isNotBlank()) onMountPartition(dev, fs)
                        },
                        onUnmount = onUnmountPartition,
                        onFormat = onFormatClick,
                        onCheckFilesystem = {
                            selectedPartition?.let { onCheckFilesystem(it) }
                        }
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
                        "REPARTITION_OK" -> stringResource(R.string.storage_wizard_success)
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

            // 2. MicroSD Extended Storage Telemetry Row
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

// ── Visual Disk Map Card (AOMEI Partition Assistant Style) ──
@Composable
private fun SdCardVisualDiskMapCard(
    diskInfo: SdCardDiskInfo?,
    partitions: List<PartitionInfo>,
    selectedPartition: PartitionInfo?,
    onSelectPartition: (PartitionInfo) -> Unit,
    onOpenWizard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardTitle = diskInfo?.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.storage_disk_title)
    val devicePath = diskInfo?.devicePath ?: "/dev/block/mmcblk0"

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.storage_disk_map_title),
            action = {
                Button(
                    onClick = onOpenWizard,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.storage_action_repartition),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Disk Hardware Banner
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SdStorage,
                            contentDescription = null,
                            tint = CyberEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = cardTitle,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = devicePath,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Proportional Partition Map Bar
                if (partitions.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = stringResource(R.string.storage_partition_no_partitions),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    VisualDiskMapBar(
                        partitions = partitions,
                        selectedPartition = selectedPartition,
                        onSelectPartition = onSelectPartition
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Map Legend Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DiskMapLegendItem(
                            color = CyberEmerald,
                            label = stringResource(R.string.storage_partition_target_mount)
                        )
                        DiskMapLegendItem(
                            color = ElectricCyan,
                            label = stringResource(R.string.storage_badge_portable)
                        )
                        DiskMapLegendItem(
                            color = MaterialTheme.colorScheme.primary,
                            label = "Linux (Ext4)"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualDiskMapBar(
    partitions: List<PartitionInfo>,
    selectedPartition: PartitionInfo?,
    onSelectPartition: (PartitionInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalBytes = partitions.sumOf { it.sizeBytes }.coerceAtLeast(1L)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            partitions.forEach { part ->
                val rawFraction = (part.sizeBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 1f)
                val weight = rawFraction.coerceAtLeast(0.12f)
                val isSelected = selectedPartition?.path == part.path

                val partColor = when {
                    part.isTargetMount || part.fsType.equals("f2fs", ignoreCase = true) -> CyberEmerald
                    part.fsType.equals("ext4", ignoreCase = true) -> MaterialTheme.colorScheme.primary
                    part.fsType.equals("fat32", ignoreCase = true) || part.fsType.equals("exfat", ignoreCase = true) || part.fsType.equals("vfat", ignoreCase = true) -> ElectricCyan
                    else -> MaterialTheme.colorScheme.secondary
                }

                val shortName = if (weight < 0.25f && part.name.startsWith("mmcblk0p")) {
                    "p${part.name.removePrefix("mmcblk0p")}"
                } else if (weight < 0.20f && part.name.startsWith("mmcblk")) {
                    part.name.removePrefix("mmcblk")
                } else {
                    part.name
                }

                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(partColor.copy(alpha = if (isSelected) 0.32f else 0.15f))
                        .clickable { onSelectPartition(part) }
                        .then(
                            if (isSelected) {
                                Modifier.border(1.8.dp, partColor, RoundedCornerShape(8.dp))
                            } else {
                                Modifier.border(0.5.dp, partColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = shortName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (weight < 0.25f) 9.5.sp else 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                            ),
                            color = if (isSelected) partColor else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (part.sizeBytes > 0) FormatUtils.formatBytes(part.sizeBytes) else part.fsType.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiskMapLegendItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                                text = stringResource(R.string.storage_partition_target_mount),
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
                    } else if (partition.isMountTargetReady) {
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

// ── Contextual Partition Action Hub Card ────────────────────
@Composable
private fun PartitionActionHubCard(
    storage: StorageInfo?,
    selectedPartition: PartitionInfo?,
    configuredSdBase: String,
    isCheckingFs: Boolean,
    onMount: () -> Unit,
    onUnmount: () -> Unit,
    onFormat: () -> Unit,
    onCheckFilesystem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val devPath = selectedPartition?.path ?: ""
    val isLinuxFs = selectedPartition?.fsType?.equals("f2fs", ignoreCase = true) == true ||
            selectedPartition?.fsType?.equals("ext4", ignoreCase = true) == true
    val isMountedCurrently = storage?.isMounted == true &&
            (storage.blockDevice == devPath || selectedPartition?.isTargetMount == true)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.storage_quick_actions))

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
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (selectedPartition != null) "${selectedPartition.name} (${FormatUtils.formatBytes(selectedPartition.sizeBytes)})" else stringResource(R.string.storage_no_devices_detected),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = configuredSdBase,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Readiness Checklist
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReadinessCheckItem(
                            label = stringResource(R.string.storage_step_root),
                            isPassed = true,
                            sublabel = "Active"
                        )
                        ReadinessCheckItem(
                            label = stringResource(R.string.storage_step_partition),
                            isPassed = isLinuxFs,
                            sublabel = if (isLinuxFs) selectedPartition?.fsType?.uppercase() ?: "Linux" else "Requires F2FS or Ext4"
                        )
                        ReadinessCheckItem(
                            label = stringResource(R.string.storage_step_mounted),
                            isPassed = isMountedCurrently,
                            sublabel = if (isMountedCurrently) "Active at $configuredSdBase" else "Standby"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Mount & Unmount Action Bar (40dp)
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
                            stringResource(R.string.storage_action_mount),
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
                            stringResource(R.string.storage_action_unmount),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Actions: Format Partisi & Periksa Filesystem (36dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onFormat,
                        enabled = devPath.isNotBlank() && !isMountedCurrently,
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
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonCrimson)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.storage_action_format),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FilledTonalButton(
                        onClick = onCheckFilesystem,
                        enabled = devPath.isNotBlank() && !isMountedCurrently && !isCheckingFs,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        if (isCheckingFs) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.storage_fsck_checking),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.storage_action_fsck),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (isMountedCurrently) {
                    Spacer(modifier = Modifier.height(6.dp))
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
private fun ReadinessCheckItem(
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

// ── Partitioning Wizard Dialog (AOMEI Partition Assistant Style) ──
@Composable
private fun PartitionWizardDialog(
    diskInfo: SdCardDiskInfo?,
    partitions: List<PartitionSchemeConfig>,
    isRepartitioning: Boolean,
    repartitionError: String?,
    onClose: () -> Unit,
    onUpdateSizeKb: (Int, Long) -> Unit,
    onUpdateFs: (Int, FilesystemType) -> Unit,
    onUpdateLabel: (Int, String) -> Unit,
    onAddPartition: () -> Unit,
    onRemovePartition: (Int) -> Unit,
    onAutoBalance: () -> Unit,
    onTriggerApply: () -> Unit
) {
    val totalDiskBytes = diskInfo?.totalSizeBytes ?: 0L
    val totalDiskKb = totalDiskBytes / 1024L
    val allocatedKb = partitions.sumOf { it.sizeKb }
    val unallocatedKb = (totalDiskKb - allocatedKb).coerceAtLeast(0L)

    var isConfirmedCheckbox by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isRepartitioning) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 14.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar (44dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.storage_wizard_title),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = diskInfo?.displayName ?: stringResource(R.string.storage_disk_title),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = onClose,
                            enabled = !isRepartitioning,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    // Scrollable Wizard Content
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        // 1. Capacity & Visual Map Preview
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(R.string.storage_wizard_total_capacity, FormatUtils.formatBytes(totalDiskBytes)),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.storage_wizard_unallocated, FormatUtils.formatBytes(unallocatedKb * 1024L)),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (unallocatedKb > 1024L * 1024L) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Dynamic Live Preview Bar
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(34.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val validTotal = allocatedKb.coerceAtLeast(1L)
                                            partitions.forEachIndexed { idx, p ->
                                                val weight = (p.sizeKb.toFloat() / validTotal.toFloat()).coerceIn(0.08f, 1f)
                                                val color = when (p.fsType) {
                                                    FilesystemType.F2FS -> CyberEmerald
                                                    FilesystemType.EXT4 -> MaterialTheme.colorScheme.primary
                                                    FilesystemType.FAT32, FilesystemType.EXFAT -> ElectricCyan
                                                    else -> MaterialTheme.colorScheme.secondary
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(weight)
                                                        .fillMaxHeight()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(color.copy(alpha = 0.3f))
                                                        .border(1.dp, color, RoundedCornerShape(6.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "P${idx + 1} (${p.fsType.label})",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = color,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Individual Partition Scheme Cards
                        itemsIndexed(partitions) { index, config ->
                            WizardPartitionCard(
                                index = index,
                                totalCount = partitions.size,
                                config = config,
                                maxTotalKb = totalDiskKb,
                                onSizeKbChange = { onUpdateSizeKb(index, it) },
                                onFsChange = { onUpdateFs(index, it) },
                                onLabelChange = { onUpdateLabel(index, it) },
                                onRemove = { onRemovePartition(index) }
                            )
                        }

                        // 3. Action Buttons: Add Partition & Auto-balance
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onAddPartition,
                                    enabled = partitions.size < 4 && !isRepartitioning,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.storage_wizard_add_partition),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                FilledTonalButton(
                                    onClick = onAutoBalance,
                                    enabled = !isRepartitioning,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Bagi Rata",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 4. Critical Warning & Checkbox
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = NeonCrimson.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            Icons.Default.Security,
                                            contentDescription = null,
                                            tint = NeonCrimson,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.storage_wizard_confirm_warning),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isConfirmedCheckbox = !isConfirmedCheckbox }
                                    ) {
                                        Checkbox(
                                            checked = isConfirmedCheckbox,
                                            onCheckedChange = { isConfirmedCheckbox = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = NeonCrimson,
                                                checkmarkColor = Color.White
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.storage_wizard_confirm_checkbox),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        if (repartitionError != null) {
                            item {
                                Text(
                                    text = stringResource(R.string.storage_wizard_error, repartitionError),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                                    color = NeonCrimson,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    // Bottom Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClose,
                            enabled = !isRepartitioning,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Text(stringResource(R.string.common_close), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onTriggerApply,
                            enabled = isConfirmedCheckbox && !isRepartitioning && partitions.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCrimson,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(42.dp)
                        ) {
                            if (isRepartitioning) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.storage_wizard_progress), fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.storage_wizard_apply_btn),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardPartitionCard(
    index: Int,
    totalCount: Int,
    config: PartitionSchemeConfig,
    maxTotalKb: Long,
    onSizeKbChange: (Long) -> Unit,
    onFsChange: (FilesystemType) -> Unit,
    onLabelChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rawTextKb by remember(config.sizeKb) { mutableStateOf(config.sizeKb.toString()) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Partition Number & Role Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.storage_wizard_partition_n, index + 1),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (index == 0) ElectricCyan.copy(alpha = 0.15f) else CyberEmerald.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (index == 0) stringResource(R.string.storage_badge_portable) else stringResource(R.string.storage_partition_target_mount),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (index == 0) ElectricCyan else CyberEmerald,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (totalCount > 1) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.storage_wizard_remove_partition),
                            tint = NeonCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Size in KB Field (Manual precision input per user requirement)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.storage_wizard_size_kb),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
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
                            BasicTextField(
                                value = rawTextKb,
                                onValueChange = { input ->
                                    val digitsOnly = input.filter { it.isDigit() }
                                    rawTextKb = digitsOnly
                                    val parsedKb = digitsOnly.toLongOrNull() ?: 0L
                                    if (parsedKb > 0) {
                                        onSizeKbChange(parsedKb)
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Partition Label Input
                Column(modifier = Modifier.weight(0.7f)) {
                    Text(
                        text = stringResource(R.string.storage_label_field),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp)
                        ) {
                            BasicTextField(
                                value = config.label,
                                onValueChange = onLabelChange,
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
            }

            // Size Human Readable Conversion Label
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "≈ ${String.format(java.util.Locale.US, "%.1f GB", config.sizeGb)} (${String.format(java.util.Locale.US, "%,d MB", config.sizeMb.toLong())})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberEmerald
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filesystem Chips Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(FilesystemType.FAT32, FilesystemType.EXFAT, FilesystemType.F2FS, FilesystemType.EXT4).forEach { fs ->
                    val isSelected = config.fsType == fs
                    Surface(
                        onClick = { onFsChange(fs) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) {
                            if (fs == FilesystemType.F2FS) CyberEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) (if (fs == FilesystemType.F2FS) CyberEmerald else MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = fs.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) {
                                    if (fs == FilesystemType.F2FS) CyberEmerald else MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Single Partition Format Dialog ──────────────────────────
@Composable
private fun SingleFormatDialog(
    partition: PartitionInfo?,
    defaultLabel: String,
    isFormatting: Boolean,
    onDismiss: () -> Unit,
    onConfirmFormat: (FilesystemType, String) -> Unit
) {
    var chosenFs by remember { mutableStateOf(FilesystemType.F2FS) }
    var labelInput by remember { mutableStateOf(partition?.label?.takeIf { it.isNotBlank() } ?: defaultLabel) }

    AlertDialog(
        onDismissRequest = { if (!isFormatting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = NeonCrimson,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.format_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Warning text
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonCrimson.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.format_warning_desc),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.format_filesystem),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Filesystem Options
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormatFsRadioOption(
                        type = FilesystemType.F2FS,
                        isSelected = chosenFs == FilesystemType.F2FS,
                        isRecommended = true,
                        title = "F2FS",
                        subtitle = stringResource(R.string.format_f2fs_desc),
                        onSelect = { chosenFs = FilesystemType.F2FS }
                    )
                    FormatFsRadioOption(
                        type = FilesystemType.EXT4,
                        isSelected = chosenFs == FilesystemType.EXT4,
                        isRecommended = false,
                        title = "Ext4",
                        subtitle = stringResource(R.string.format_ext4_desc),
                        onSelect = { chosenFs = FilesystemType.EXT4 }
                    )
                    FormatFsRadioOption(
                        type = FilesystemType.FAT32,
                        isSelected = chosenFs == FilesystemType.FAT32,
                        isRecommended = false,
                        title = "FAT32",
                        subtitle = "Universal portable storage",
                        onSelect = { chosenFs = FilesystemType.FAT32 }
                    )
                    FormatFsRadioOption(
                        type = FilesystemType.EXFAT,
                        isSelected = chosenFs == FilesystemType.EXFAT,
                        isRecommended = false,
                        title = "exFAT",
                        subtitle = stringResource(R.string.format_exfat_desc),
                        onSelect = { chosenFs = FilesystemType.EXFAT }
                    )
                }

                // Partition Label input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.storage_label_field),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                            BasicTextField(
                                value = labelInput,
                                onValueChange = { labelInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmFormat(chosenFs, labelInput) },
                enabled = !isFormatting,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                if (isFormatting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.format_in_progress), fontSize = 11.5.sp)
                } else {
                    Text(stringResource(R.string.format_confirm_button), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isFormatting,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(stringResource(R.string.common_close), fontSize = 11.5.sp)
            }
        }
    )
}

@Composable
private fun FormatFsRadioOption(
    type: FilesystemType,
    isSelected: Boolean,
    isRecommended: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) {
        if (isRecommended) CyberEmerald else MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color.Transparent,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isRecommended) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CyberEmerald.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = stringResource(R.string.common_recommended),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberEmerald,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
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
            diskInfo = SdCardDiskInfo(
                devicePath = "/dev/block/mmcblk0",
                diskName = "mmcblk0",
                vendorName = "Samsung MicroSD",
                modelName = "YD4QD",
                totalSizeBytes = 128_100_000_000L
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
                    label = "STORAGE",
                    isMounted = true,
                    isTargetMount = false,
                    isMountTargetReady = false
                ),
                PartitionInfo(
                    path = "/dev/block/mmcblk0p2",
                    name = "mmcblk0p2",
                    diskName = "mmcblk0",
                    partitionNumber = 2,
                    sizeBytes = 96_100_000_000L,
                    fsType = "f2fs",
                    mountPoint = "/data/sdext2",
                    label = "sdext2",
                    isMounted = true,
                    isTargetMount = true,
                    isMountTargetReady = true
                )
            ),
            selectedPartition = PartitionInfo(
                path = "/dev/block/mmcblk0p2",
                name = "mmcblk0p2",
                diskName = "mmcblk0",
                partitionNumber = 2,
                sizeBytes = 96_100_000_000L,
                fsType = "f2fs",
                mountPoint = "/data/sdext2",
                label = "sdext2",
                isMounted = true,
                isTargetMount = true,
                isMountTargetReady = true
            ),
            configuredSdBase = "/data/sdext2",
            isScanning = false,
            isCheckingFs = false,
            statusMessage = null,
            offloadedStats = Pair(3, 42_500_000_000L)
        )
    }
}
