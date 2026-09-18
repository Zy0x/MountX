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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import app.mountify.data.model.DiskType
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
import app.mountify.ui.theme.AmberWarn
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
    val allDisks by viewModel.allDisks.collectAsState()
    val selectedDiskForDetail by viewModel.selectedDiskForDetail.collectAsState()
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

    if (selectedDiskForDetail != null) {
        DiskDetailView(
            disk = selectedDiskForDetail!!,
            storage = storage,
            configuredSdBase = configuredSdBase,
            isCheckingFs = isCheckingFs,
            isFormatting = isFormatting,
            onBack = { viewModel.closeDiskDetail() },
            onRefresh = { viewModel.detectPartitions(force = true) },
            onOpenWizard = { viewModel.openPartitionWizard(selectedDiskForDetail) },
            onMountPartition = { part -> viewModel.mountPartition(part) },
            onUnmountPartition = { part -> viewModel.unmountPartition(part) },
            onFormatPartition = { part ->
                viewModel.selectPartition(part)
                showFormatDialog = true
            },
            onCheckFilesystem = { part -> viewModel.checkFilesystem(part) },
            modifier = modifier
        )
    } else {
        StorageContent(
            storage = storage,
            internalStorage = internalStorage,
            diskInfo = diskInfo,
            allDisks = if (allDisks.isNotEmpty()) allDisks else (if (diskInfo != null) listOf(diskInfo!!) else emptyList()),
            statusMessage = statusMessage,
            offloadedStats = offloadedStats,
            isScanning = isScanning,
            onRefreshPartitions = { viewModel.detectPartitions(force = true) },
            onOpenDiskDetail = { disk -> viewModel.openDiskDetail(disk) },
            onClearStatusMessage = { viewModel.clearStatusMessage() },
            onExportConfig = { exportLauncher.launch("mountify_config.json") },
            onImportConfig = { importLauncher.launch(arrayOf("application/json")) },
            onNavigateToBackup = onNavigateToBackup,
            onNavigateToGames = onNavigateToGames,
            modifier = modifier
        )
    }

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
    val activeWizardDisk = selectedDiskForDetail ?: diskInfo
    if (isWizardOpen) {
        PartitionWizardDialog(
            diskInfo = activeWizardDisk,
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
        val targetDiskPath = activeWizardDisk?.devicePath ?: "/dev/block/mmcblk0"
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
    allDisks: List<SdCardDiskInfo> = emptyList(),
    statusMessage: String? = null,
    offloadedStats: Pair<Int, Long> = Pair(0, 0L),
    isScanning: Boolean = false,
    onRefreshPartitions: () -> Unit = {},
    onOpenDiskDetail: (SdCardDiskInfo) -> Unit = {},
    onClearStatusMessage: () -> Unit = {},
    onExportConfig: () -> Unit = {},
    onImportConfig: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToGames: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val effectiveDisks = if (allDisks.isNotEmpty()) allDisks else (if (diskInfo != null) listOf(diskInfo) else emptyList())

    Scaffold(
        topBar = {
            CompactScreenHeader(
                title = stringResource(R.string.storage_title)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isScanning,
            onRefresh = onRefreshPartitions,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLandscape) {
                // Dual-Column Responsive Layout for Landscape / Tablet
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)
                    ) {
                        item {
                            StatusFeedbackBanner(
                                statusMessage = statusMessage,
                                onDismiss = onClearStatusMessage
                            )
                        }
                        item {
                            MultiDiskVisualMapSection(
                                internalStorage = internalStorage,
                                disks = effectiveDisks,
                                onOpenDiskDetail = onOpenDiskDetail
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

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)
                    ) {
                        // Right column placeholder — will be used for future content
                    }
                }
            } else {
                // Single-Column Responsive Layout for Portrait
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        StatusFeedbackBanner(
                            statusMessage = statusMessage,
                            onDismiss = onClearStatusMessage
                        )
                    }

                    item {
                        MultiDiskVisualMapSection(
                            internalStorage = internalStorage,
                            disks = effectiveDisks,
                            onOpenDiskDetail = onOpenDiskDetail
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

// ── Multi-Disk Cumulative Telemetry Card ────────────────────
@Composable
private fun MultiDiskTelemetryCard(
    storage: StorageInfo?,
    internalStorage: InternalStorageInfo?,
    disks: List<SdCardDiskInfo>,
    offloadedStats: Pair<Int, Long>,
    onNavigateToGames: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title & Detected Disks count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Filled.SdStorage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.storage_specs),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val diskCountText = if (disks.isNotEmpty()) {
                            "${disks.size} External ${if (disks.size == 1) "Disk" else "Disks"}"
                        } else {
                            stringResource(R.string.storage_not_mounted)
                        }
                        Text(
                            text = diskCountText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Internal Storage Telemetry Row
            if (internalStorage != null && internalStorage.totalBytes > 0) {
                val internalUsedPct = (internalStorage.usedBytes.toFloat() / internalStorage.totalBytes).coerceIn(0f, 1f)
                val isLowSpace = internalUsedPct > 0.85f

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Internal Storage (/data)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "${FormatUtils.formatBytes(internalStorage.usedBytes)} / ${FormatUtils.formatBytes(internalStorage.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = FormatUtils.getHealthColor(internalUsedPct)
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // 4-Tier Continuous Health Gradient Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        if (internalUsedPct > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = internalUsedPct.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(FormatUtils.getHealthColor(internalUsedPct))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(internalUsedPct * 100).toInt()}% " + stringResource(R.string.storage_used),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = FormatUtils.getHealthColor(internalUsedPct)
                        )
                        Text(
                            text = "${FormatUtils.formatBytes(internalStorage.freeBytes)} " + stringResource(R.string.storage_free),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. External Disks Cumulative Telemetry
            disks.forEach { disk ->
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))

                val diskIcon = if (disk.diskType == DiskType.USB_OTG) Icons.Default.Usb else Icons.Default.SdCard
                val diskUsedPct = disk.usedPercent

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = diskIcon,
                                contentDescription = null,
                                tint = CyberEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = disk.hardwareTitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${FormatUtils.formatBytes(disk.totalUsedBytes)} / ${FormatUtils.formatBytes(disk.totalSizeBytes)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = FormatUtils.getHealthColor(diskUsedPct)
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // 4-Tier Continuous Health Gradient Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        if (diskUsedPct > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = diskUsedPct.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(FormatUtils.getHealthColor(diskUsedPct))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(diskUsedPct * 100).toInt()}% " + stringResource(R.string.storage_used),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = FormatUtils.getHealthColor(diskUsedPct)
                        )
                        Text(
                            text = "${FormatUtils.formatBytes(disk.totalFreeBytes)} " + stringResource(R.string.storage_free),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Offloaded Games Summary Footer
            if (offloadedStats.first > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    onClick = onNavigateToGames,
                    shape = RoundedCornerShape(10.dp),
                    color = CyberEmerald.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CyberEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.storage_offloaded_summary, offloadedStats.first, FormatUtils.formatBytes(offloadedStats.second)),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = CyberEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Multi-Disk Visual Partition Map Section ─────────────────
@Composable
private fun MultiDiskVisualMapSection(
    internalStorage: InternalStorageInfo? = null,
    disks: List<SdCardDiskInfo>,
    onOpenDiskDetail: (SdCardDiskInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = stringResource(R.string.storage_disk_map_title)
        )

        // Internal Storage — always first, read-only Protected card
        if (internalStorage != null) {
            InternalDiskVisualMapCard(internalStorage = internalStorage)
        }

        if (disks.isEmpty() && internalStorage == null) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.storage_no_devices_detected),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.storage_partition_no_partitions),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            disks.forEach { disk ->
                DiskVisualMapOverviewCard(
                    disk = disk,
                    onClick = { onOpenDiskDetail(disk) }
                )
            }
        }
    }
}

// ── Internal Storage Visual Map Card (Read-Only / Protected) ──
@Composable
private fun InternalDiskVisualMapCard(
    internalStorage: InternalStorageInfo,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        onClick = { showDialog = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ElectricCyan.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.storage_internal_partition_name),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "/data • F2FS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Protected badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AmberWarn.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AmberWarn.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AmberWarn,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = stringResource(R.string.storage_internal_protected_badge),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = AmberWarn
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val healthColor = FormatUtils.getHealthColor(internalStorage.usedPercent)

            // Capacity row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total: ${FormatUtils.formatBytes(internalStorage.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(text = "•", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.outline)
                Text(
                    text = "${stringResource(R.string.storage_used)}: ${FormatUtils.formatBytes(internalStorage.usedBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                    color = healthColor
                )
                Text(text = "•", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.outline)
                Text(
                    text = "${stringResource(R.string.storage_free)}: ${FormatUtils.formatBytes(internalStorage.freeBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                    color = healthColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Single-partition visual map bar
            val usedFrac = internalStorage.usedPercent.coerceIn(0f, 1f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF182030))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
                ) {
                    if (usedFrac > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = usedFrac)
                                .background(FormatUtils.getHealthColor(usedFrac))
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(1.5.dp)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.85f))
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "userdata",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = FormatUtils.formatBytes(internalStorage.totalBytes),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // Protected info dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = AmberWarn, modifier = Modifier.size(28.dp))
            },
            title = {
                Text(
                    text = stringResource(R.string.storage_internal_protected_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.storage_internal_protected_desc),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = { showDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "OK", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp
        )
    }
}

// ── Interactive Disk Visual Map Overview Card (AOMEI Partition Style) ──
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiskVisualMapOverviewCard(
    disk: SdCardDiskInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Disk Name & Type Icon (full width, no truncation)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val diskIcon = if (disk.diskType == DiskType.USB_OTG) Icons.Default.Usb else Icons.Default.SdCard
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CyberEmerald.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = diskIcon,
                        contentDescription = null,
                        tint = CyberEmerald,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = disk.hardwareTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${disk.devicePath} • ${disk.partitions.size} " + stringResource(R.string.storage_disk_partitions_list).lowercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val diskHealthColor = FormatUtils.getHealthColor(disk.usedPercent)

            // Capacity Breakdown Row: Total • Digunakan • Bebas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total: ${FormatUtils.formatBytes(disk.totalSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "${stringResource(R.string.storage_used)}: ${FormatUtils.formatBytes(disk.totalUsedBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = diskHealthColor
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "${stringResource(R.string.storage_free)}: ${FormatUtils.formatBytes(disk.totalFreeBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = diskHealthColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Proportional Two-Tier Visual Partition Map Bar (4-Tier Health Dynamics)
            if (disk.partitions.isNotEmpty()) {
                val totalDiskBytes = disk.totalSizeBytes.coerceAtLeast(1L)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    disk.partitions.forEachIndexed { idx, part ->
                        if (idx > 0) {
                            Spacer(modifier = Modifier.width(3.dp))
                        }

                        val weightFraction = (part.sizeBytes.toFloat() / totalDiskBytes.toFloat()).coerceAtLeast(0.06f)
                        val usedFraction = if (part.usedBytes > 0L) {
                            (part.usedBytes.toFloat() / part.sizeBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Box(
                            modifier = Modifier
                                .weight(weightFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF182030))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            // Inner solid used space bar with 4-tier health gradient and right edge highlight line
                            if (usedFraction > 0.001f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = usedFraction)
                                        .background(
                                            FormatUtils.getHealthColor(usedFraction)
                                        )
                                ) {
                                    // Sharp white highlight divider line at used space edge
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(1.5.dp)
                                            .fillMaxHeight()
                                            .background(Color.White.copy(alpha = 0.85f))
                                    )
                                }
                            }

                            // Centered Partition Label Overlay
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = part.cleanShortName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = FormatUtils.formatBytes(part.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Partition Legend Items with Used & Free breakdown
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    disk.partitions.forEach { part ->
                        val healthColor = FormatUtils.getHealthColor(part.usedPercent)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(healthColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val usageDetail = if (part.usedBytes > 0L) {
                                "${part.cleanShortName} (${stringResource(R.string.storage_used)}: ${FormatUtils.formatBytes(part.usedBytes)} • ${stringResource(R.string.storage_free)}: ${FormatUtils.formatBytes(part.freeBytes)})"
                            } else {
                                "${part.cleanShortName} (${stringResource(R.string.storage_total)}: ${FormatUtils.formatBytes(part.sizeBytes)})"
                            }
                            Text(
                                text = usageDetail,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Right Manage Action Pill Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.storage_disk_manage_badge),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
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
        val sampleDisk = SdCardDiskInfo(
            devicePath = "/dev/block/mmcblk0",
            diskName = "mmcblk0",
            vendorName = "Samsung MicroSD",
            modelName = "YD4QD",
            totalSizeBytes = 128_100_000_000L,
            totalUsedBytes = 28_000_000_000L,
            totalFreeBytes = 100_100_000_000L,
            partitions = listOf(
                PartitionInfo(
                    path = "/dev/block/mmcblk0p1",
                    name = "mmcblk0p1",
                    diskName = "mmcblk0",
                    partitionNumber = 1,
                    sizeBytes = 32_000_000_000L,
                    usedBytes = 10_000_000_000L,
                    freeBytes = 22_000_000_000L,
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
                    usedBytes = 18_000_000_000L,
                    freeBytes = 78_100_000_000L,
                    fsType = "f2fs",
                    mountPoint = "/data/sdext2",
                    label = "sdext2",
                    isMounted = true,
                    isTargetMount = true,
                    isMountTargetReady = true
                )
            )
        )

        StorageContent(
            storage = StorageInfo(
                blockDevice = "/dev/block/mmcblk0p2",
                mountPoint = "/data/sdext2",
                filesystem = "f2fs",
                totalBytes = 96_100_000_000L,
                usedBytes = 18_000_000_000L,
                freeBytes = 78_100_000_000L,
                isMounted = true
            ),
            internalStorage = InternalStorageInfo(
                totalBytes = 128_000_000_000L,
                usedBytes = 45_000_000_000L,
                freeBytes = 83_000_000_000L
            ),
            diskInfo = sampleDisk,
            allDisks = listOf(sampleDisk),
            isScanning = false,
            statusMessage = null,
            offloadedStats = Pair(3, 42_500_000_000L)
        )
    }
}
