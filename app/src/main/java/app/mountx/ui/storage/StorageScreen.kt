package app.mountx.ui.storage

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eject
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.draw.alpha
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
import app.mountx.R
import androidx.compose.material.icons.automirrored.filled.Label
import app.mountx.data.model.DiskType
import app.mountx.data.model.FilesystemType
import app.mountx.data.model.FsckReport
import app.mountx.data.model.FsckStatus
import app.mountx.data.model.InternalStorageInfo
import app.mountx.data.model.MountStatus
import app.mountx.data.model.PartitionInfo
import app.mountx.data.model.PartitionSchemeConfig
import app.mountx.data.model.SdCardDiskInfo
import app.mountx.data.model.StorageInfo
import app.mountx.data.model.SupportedFilesystemInfo
import app.mountx.ui.components.CompactScreenHeader
import app.mountx.ui.components.ConfirmDialog
import app.mountx.ui.components.OperationProgressOverlay
import app.mountx.ui.components.SectionHeader
import app.mountx.ui.components.StatusChip
import app.mountx.ui.theme.AmberWarn
import app.mountx.ui.theme.CyberEmerald
import app.mountx.ui.theme.ElectricCyan
import app.mountx.ui.theme.MountXTheme
import app.mountx.ui.theme.NeonCrimson
import app.mountx.util.FormatUtils

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

    // I/O Tweaks & Storage Tools States
    val diskIoConfig by viewModel.diskIoConfig.collectAsState()
    val isApplyingIo by viewModel.isApplyingIo.collectAsState()
    val isBenchmarking by viewModel.isBenchmarking.collectAsState()
    val benchmarkResult by viewModel.benchmarkResult.collectAsState()
    val isTrimming by viewModel.isTrimming.collectAsState()
    val trimOutput by viewModel.trimOutput.collectAsState()
    val diskHardwareDetails by viewModel.diskHardwareDetails.collectAsState()
    val isMountingAll by viewModel.isMountingAll.collectAsState()
    val isUnmountingAll by viewModel.isUnmountingAll.collectAsState()
    val isUrgentGcRunning by viewModel.isUrgentGcRunning.collectAsState()
    val operationProgress by viewModel.operationProgress.collectAsState()
    val fsckReport by viewModel.fsckReport.collectAsState()
    val supportedFilesystems by viewModel.supportedFilesystems.collectAsState()
    val partitionToUnmount by viewModel.partitionToUnmount.collectAsState()
    val diskToEject by viewModel.diskToEject.collectAsState()
    val globalTrimReport by viewModel.globalTrimReport.collectAsState()
    val unmountingPartitionPath by viewModel.unmountingPartitionPath.collectAsState()
    val mountingPartitionPath by viewModel.mountingPartitionPath.collectAsState()

    var showDiskToolsSheet by remember { mutableStateOf(false) }
    var showPartitionToolsSheet by remember { mutableStateOf(false) }
    var partitionForTools by remember { mutableStateOf<PartitionInfo?>(null) }
    var showEditLabelDialog by remember { mutableStateOf(false) }
    var partitionForLabelEdit by remember { mutableStateOf<PartitionInfo?>(null) }

    // Wizard states
    val isWizardOpen by viewModel.isWizardOpen.collectAsState()
    val wizardPartitions by viewModel.wizardPartitions.collectAsState()
    val isRepartitioning by viewModel.isRepartitioning.collectAsState()
    val repartitionError by viewModel.repartitionError.collectAsState()

    var showFormatDialog by remember { mutableStateOf(false) }
    var showRepartitionFinalConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.detectPartitions(force = false)
        viewModel.loadSupportedFilesystems()
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
            isMountingAll = isMountingAll,
            isUnmountingAll = isUnmountingAll,
            unmountingPartitionPath = unmountingPartitionPath,
            mountingPartitionPath = mountingPartitionPath,
            onBack = { viewModel.closeDiskDetail() },
            onRefresh = { viewModel.detectPartitions(force = true) },
            onOpenWizard = { viewModel.openPartitionWizard(selectedDiskForDetail) },
            onMountPartition = { part -> viewModel.mountPartition(part) },
            onUnmountPartition = { part -> viewModel.promptUnmountPartition(part) },
            onMountAll = { selectedDiskForDetail?.let { viewModel.mountAllPartitions(it) } },
            onUnmountAll = { selectedDiskForDetail?.let { viewModel.promptEjectDisk(it) } },
            onOpenDiskTools = { showDiskToolsSheet = true },
            onOpenPartitionTools = { part ->
                partitionForTools = part
                showPartitionToolsSheet = true
            },
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
            supportedFilesystems = supportedFilesystems,
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
            supportedFilesystems = supportedFilesystems,
            onClose = { viewModel.closePartitionWizard() },
            onUpdateSizeKb = { idx, sizeKb -> viewModel.updatePartitionSizeKb(idx, sizeKb) },
            onUpdateFs = { idx, fs -> viewModel.updatePartitionFsType(idx, fs) },
            onUpdateLabel = { idx, label -> viewModel.updatePartitionLabel(idx, label) },
            onAddPartition = { viewModel.addPartition() },
            onRemovePartition = { idx -> viewModel.removePartition(idx) },
            onAutoBalance = { viewModel.autoBalanceWizardPartitions() },
            onAllocateUnallocated = { idx -> viewModel.allocateUnallocatedToPartition(idx) },
            onAdjustAdjacent = { idx, deltaKb -> viewModel.adjustAdjacentWizardPartitions(idx, deltaKb) },
            onAdjustByGb = { idx, deltaGb -> viewModel.adjustPartitionSizeByGb(idx, deltaGb) },
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

    // Universal Operation Progress Overlay Dialog
    OperationProgressOverlay(
        state = operationProgress,
        onDismiss = { viewModel.clearOperationProgress() }
    )

    // Filesystem Check (fsck) Structured Report Dialog
    if (fsckReport != null) {
        FsckReportDialog(
            report = fsckReport!!,
            onDismiss = { viewModel.clearFsckReport() }
        )
    } else if (fsCheckOutput != null) {
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

    // Disk Tools Bottom Sheet
    if (showDiskToolsSheet && selectedDiskForDetail != null) {
        DiskToolsBottomSheet(
            disk = selectedDiskForDetail!!,
            ioConfig = diskIoConfig,
            isApplyingIo = isApplyingIo,
            hardwareDetails = diskHardwareDetails,
            isBenchmarking = isBenchmarking,
            benchmarkResult = benchmarkResult,
            isTrimming = isTrimming,
            trimOutput = trimOutput,
            isUrgentGcRunning = isUrgentGcRunning,
            onDismiss = { showDiskToolsSheet = false },
            onApplyPreset = { preset -> viewModel.applyIoPreset(selectedDiskForDetail!!, preset) },
            onApplyCustomConfig = { cfg -> viewModel.applyDiskIoConfig(selectedDiskForDetail!!, cfg) },
            onRunBenchmark = { viewModel.runQuickDiskBenchmark(selectedDiskForDetail!!.devicePath) },
            onRunGlobalTrim = { viewModel.runGlobalTrim(selectedDiskForDetail!!) },
            onRunUrgentGc = { viewModel.runF2fsUrgentGc(selectedDiskForDetail!!) }
        )
    }

    // Partition Tools Bottom Sheet
    if (showPartitionToolsSheet && partitionForTools != null) {
        PartitionToolsBottomSheet(
            partition = partitionForTools!!,
            isCheckingFs = isCheckingFs,
            isTrimming = isTrimming,
            onDismiss = { showPartitionToolsSheet = false },
            onFormatClick = {
                showPartitionToolsSheet = false
                viewModel.selectPartition(partitionForTools!!)
                viewModel.loadSupportedFilesystems()
                showFormatDialog = true
            },
            onEditLabelClick = {
                showPartitionToolsSheet = false
                partitionForLabelEdit = partitionForTools
                showEditLabelDialog = true
            },
            onCheckFsClick = {
                showPartitionToolsSheet = false
                viewModel.checkFilesystem(partitionForTools!!)
            },
            onTrimClick = {
                viewModel.runPartitionTrim(partitionForTools!!)
            }
        )
    }

    // Edit Partition Label Dialog (Non-destructive)
    if (showEditLabelDialog && partitionForLabelEdit != null) {
        EditPartitionLabelDialog(
            partition = partitionForLabelEdit!!,
            onDismiss = { showEditLabelDialog = false },
            onConfirm = { newLabel ->
                showEditLabelDialog = false
                viewModel.setPartitionLabel(partitionForLabelEdit!!, newLabel)
            }
        )
    }

    // Unmount Partition Confirmation Dialog
    if (partitionToUnmount != null) {
        val parentDisk = allDisks.find { disk -> disk.partitions.any { it.path == partitionToUnmount?.path } }
            ?: selectedDiskForDetail
        val diskTitle = parentDisk?.hardwareTitle?.takeIf { it.isNotBlank() }
            ?: partitionToUnmount?.diskName?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.storage_disk_title)

        UnmountPartitionConfirmDialog(
            partition = partitionToUnmount!!,
            diskName = diskTitle,
            onDismiss = { viewModel.clearUnmountPartitionPrompt() },
            onConfirm = { viewModel.confirmUnmountPartition() }
        )
    }

    // Eject Disk Confirmation Dialog
    if (diskToEject != null) {
        EjectDiskConfirmDialog(
            disk = diskToEject!!,
            onDismiss = { viewModel.clearEjectDiskPrompt() },
            onConfirm = { viewModel.confirmEjectDisk() }
        )
    }

    // Storage TRIM Output Dialog with Actionable Recommendations
    if (trimOutput != null) {
        val hasNeedsCleaning = globalTrimReport?.hasNeedsCleaning ?: trimOutput?.contains("Structure needs cleaning", ignoreCase = true) ?: false
        val dirtyPart = selectedDiskForDetail?.partitions?.firstOrNull { 
            it.name == globalTrimReport?.dirtyPartitionName || it.mountPoint == globalTrimReport?.dirtyMountPoint || it.isTargetMount 
        } ?: partitionForTools

        AlertDialog(
            onDismissRequest = {
                viewModel.clearTrimOutput()
                viewModel.clearGlobalTrimReport()
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = if (hasNeedsCleaning) AmberWarn else CyberEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.storage_trim_result_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
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
                    // Target Volume Metadata Card
                    if (dirtyPart != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = dirtyPart.cleanShortName.ifBlank { dirtyPart.name },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    dirtyPart.mountPoint?.let { mp ->
                                        Text(
                                            text = mp,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = dirtyPart.fsType.uppercase(),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasNeedsCleaning) AmberWarn else CyberEmerald,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // If Structure needs cleaning is detected -> Actionable Alert Banner
                    if (hasNeedsCleaning && dirtyPart != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AmberWarn.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AmberWarn.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = AmberWarn,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.storage_trim_clean_needed_title),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = AmberWarn
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.storage_trim_clean_needed_desc, dirtyPart.cleanShortName),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        viewModel.clearTrimOutput()
                                        viewModel.clearGlobalTrimReport()
                                        viewModel.executeGuidedFsckRepair(dirtyPart)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AmberWarn,
                                        contentColor = Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Build,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.storage_trim_fsck_action_btn),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Raw Output Terminal Box with Horizontal + Vertical Scrolling to prevent awkward wrap
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF07090F),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                                .horizontalScroll(rememberScrollState())
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = trimOutput ?: "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = if (hasNeedsCleaning) AmberWarn else CyberEmerald
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearTrimOutput()
                        viewModel.clearGlobalTrimReport()
                    },
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

// ── Interactive Multi-Partition Drag Slider (AOMEI Partition Assistant Style) ──
@Composable
private fun InteractivePartitionSliderBar(
    partitions: List<PartitionSchemeConfig>,
    totalDiskKb: Long,
    unallocatedKb: Long = 0L,
    onAdjustAdjacent: (Int, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val validTotalKb = totalDiskKb.coerceAtLeast(partitions.sumOf { it.sizeKb }).coerceAtLeast(1L)
    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.storage_wizard_drag_hint),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Multi-segment partition bar with draggable dividers
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)), RoundedCornerShape(10.dp))
                .onGloballyPositioned { coordinates ->
                    barWidthPx = coordinates.size.width.toFloat()
                }
        ) {
            // 1. Partition colored blocks
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                partitions.forEachIndexed { idx, p ->
                    val weight = (p.sizeKb.toFloat() / validTotalKb.toFloat()).coerceIn(0.06f, 1f)
                    val baseColor = when (p.fsType) {
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
                            .background(baseColor.copy(alpha = 0.22f))
                            .border(1.dp, baseColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "P${idx + 1}: ${p.label.ifBlank { p.fsType.label }}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = baseColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f GB", p.sizeGb),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }

                // If there is unallocated space, render the unallocated block!
                if (unallocatedKb > 1024L) {
                    val unallocatedWeight = (unallocatedKb.toFloat() / validTotalKb.toFloat()).coerceIn(0.04f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(unallocatedWeight)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.storage_wizard_unallocated_short),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f GB", unallocatedKb / 1024.0 / 1024.0),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 2. Draggable Divider Handles (||) placed at partition boundaries
            if (partitions.size > 1 && barWidthPx > 10f) {
                var cumulativeFraction = 0f
                for (i in 0 until partitions.size - 1) {
                    val partFraction = partitions[i].sizeKb.toFloat() / validTotalKb.toFloat()
                    cumulativeFraction += partFraction
                    val dividerCenterX = cumulativeFraction * barWidthPx
                    val handleHalfWidthDp = 18.dp
                    val handleHalfWidthPx = with(density) { handleHalfWidthDp.toPx() }
                    val handleOffsetX = (dividerCenterX - handleHalfWidthPx).coerceIn(0f, barWidthPx - handleHalfWidthPx * 2)

                    PartitionDividerHandle(
                        dividerIndex = i,
                        offsetX = handleOffsetX,
                        barWidthPx = barWidthPx,
                        totalDiskKb = validTotalKb,
                        onAdjustAdjacent = onAdjustAdjacent
                    )
                }
            }
        }
    }
}

/**
 * Dedicated high-performance divider handle with touch accumulator and stable pointerInput gesture tracking.
 */
@Composable
private fun PartitionDividerHandle(
    dividerIndex: Int,
    offsetX: Float,
    barWidthPx: Float,
    totalDiskKb: Long,
    onAdjustAdjacent: (Int, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    val currentOnAdjust by rememberUpdatedState(onAdjustAdjacent)
    val currentTotalKb by rememberUpdatedState(totalDiskKb)
    val currentBarWidthPx by rememberUpdatedState(barWidthPx)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .pointerInput(dividerIndex) {
                var accumulatedDeltaKb = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        accumulatedDeltaKb = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        accumulatedDeltaKb = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        accumulatedDeltaKb = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val widthPx = currentBarWidthPx
                        val totalKb = currentTotalKb
                        if (widthPx > 0f) {
                            val deltaFraction = dragAmount / widthPx
                            val rawDeltaKb = deltaFraction * totalKb
                            accumulatedDeltaKb += rawDeltaKb
                            // Step by 4MB chunks (4096 KB) to prevent micro-jitter and maintain smooth 60/120 FPS
                            val stepChunks = (accumulatedDeltaKb / 4096f).toInt()
                            if (stepChunks != 0) {
                                val deltaToApply = stepChunks * 4096L
                                currentOnAdjust(dividerIndex, deltaToApply)
                                accumulatedDeltaKb -= deltaToApply.toFloat()
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // High-contrast AOMEI-style handle pill with active dragging visual feedback
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isDragging) ElectricCyan else Color(0xFF1E293B),
            border = BorderStroke(1.5.dp, if (isDragging) Color.White else ElectricCyan),
            shadowElevation = if (isDragging) 6.dp else 3.dp,
            modifier = Modifier
                .width(if (isDragging) 16.dp else 14.dp)
                .height(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                val lineColor = if (isDragging) Color(0xFF0F1117) else ElectricCyan
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .height(14.dp)
                            .background(lineColor)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .height(14.dp)
                            .background(lineColor)
                    )
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
    supportedFilesystems: List<SupportedFilesystemInfo> = emptyList(),
    onClose: () -> Unit,
    onUpdateSizeKb: (Int, Long) -> Unit,
    onUpdateFs: (Int, FilesystemType) -> Unit,
    onUpdateLabel: (Int, String) -> Unit,
    onAddPartition: () -> Unit,
    onRemovePartition: (Int) -> Unit,
    onAutoBalance: () -> Unit,
    onAllocateUnallocated: (Int) -> Unit = {},
    onAdjustAdjacent: (Int, Long) -> Unit,
    onAdjustByGb: (Int, Long) -> Unit,
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
                        // 1. Capacity & Interactive Slider Bar (AOMEI Style)
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

                                    // AOMEI Interactive Partition Slider Bar
                                    InteractivePartitionSliderBar(
                                        partitions = partitions,
                                        totalDiskKb = totalDiskKb,
                                        unallocatedKb = unallocatedKb,
                                        onAdjustAdjacent = onAdjustAdjacent
                                    )
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
                                unallocatedKb = unallocatedKb,
                                supportedFilesystems = supportedFilesystems,
                                onSizeKbChange = { onUpdateSizeKb(index, it) },
                                onAdjustByGb = { deltaGb -> onAdjustByGb(index, deltaGb) },
                                onAllocateUnallocated = { onAllocateUnallocated(index) },
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
    unallocatedKb: Long = 0L,
    supportedFilesystems: List<SupportedFilesystemInfo> = emptyList(),
    onSizeKbChange: (Long) -> Unit,
    onAdjustByGb: (Long) -> Unit,
    onAllocateUnallocated: () -> Unit = {},
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

            Spacer(modifier = Modifier.height(8.dp))

            // Human readable capacity + Quick Steppers (-1 GB, +1 GB, +5 GB, + Sisa)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "≈ ${String.format(java.util.Locale.US, "%.1f GB", config.sizeGb)} (${String.format(java.util.Locale.US, "%,d MB", config.sizeMb.toLong())})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 0) ElectricCyan else CyberEmerald
                    )
                )

                // Quick steppers
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        onClick = { onAdjustByGb(-1L) },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text("-1 GB", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Surface(
                        onClick = { onAdjustByGb(1L) },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text("+1 GB", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Surface(
                        onClick = { onAdjustByGb(5L) },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text("+5 GB", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    if (unallocatedKb > 1024L) {
                        Surface(
                            onClick = onAllocateUnallocated,
                            shape = RoundedCornerShape(6.dp),
                            color = CyberEmerald.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.5f)),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                                Text(
                                    stringResource(R.string.storage_wizard_add_remaining, String.format(java.util.Locale.US, "%.1fG", unallocatedKb / 1024.0 / 1024.0)),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberEmerald
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Proportional Slider
            val maxValidKb = maxTotalKb.coerceAtLeast(1L)
            val currentFraction = (config.sizeKb.toFloat() / maxValidKb.toFloat()).coerceIn(0.01f, 1f)
            Slider(
                value = currentFraction,
                onValueChange = { fraction ->
                    val targetKb = (fraction * maxValidKb).toLong().coerceAtLeast(512L * 1024L)
                    onSizeKbChange(targetKb)
                },
                colors = SliderDefaults.colors(
                    thumbColor = if (index == 0) ElectricCyan else CyberEmerald,
                    activeTrackColor = if (index == 0) ElectricCyan else CyberEmerald,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Size in KB Field (Manual precision input per user requirement) & Partition Label
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

            Spacer(modifier = Modifier.height(8.dp))

            // Filesystem Chips Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(FilesystemType.FAT32, FilesystemType.EXFAT, FilesystemType.F2FS, FilesystemType.EXT4).forEach { fs ->
                    val isSelected = config.fsType == fs
                    val fsInfo = supportedFilesystems.firstOrNull { it.fsType == fs }
                    val isSupported = fsInfo?.isFullySupported ?: true

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
                            if (isSelected) {
                                (if (fs == FilesystemType.F2FS) CyberEmerald else MaterialTheme.colorScheme.primary)
                            } else {
                                if (!isSupported) NeonCrimson.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = fs.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) {
                                        if (fs == FilesystemType.F2FS) CyberEmerald else MaterialTheme.colorScheme.primary
                                    } else if (!isSupported) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (!isSupported) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(NeonCrimson)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // If selected filesystem has a status note or is not fully supported, show warning note
            val selectedFsInfo = supportedFilesystems.firstOrNull { it.fsType == config.fsType }
            if (selectedFsInfo != null && !selectedFsInfo.isFullySupported) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠ ${selectedFsInfo.description}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = NeonCrimson
                )
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
    supportedFilesystems: List<SupportedFilesystemInfo> = emptyList(),
    onDismiss: () -> Unit,
    onConfirmFormat: (FilesystemType, String) -> Unit
) {
    val f2fsSupported = supportedFilesystems.firstOrNull { it.fsType == FilesystemType.F2FS }?.isFullySupported ?: true
    val ext4Supported = supportedFilesystems.firstOrNull { it.fsType == FilesystemType.EXT4 }?.isFullySupported ?: true
    val fat32Supported = supportedFilesystems.firstOrNull { it.fsType == FilesystemType.FAT32 }?.isFullySupported ?: true
    val exfatSupported = supportedFilesystems.firstOrNull { it.fsType == FilesystemType.EXFAT }?.isFullySupported ?: true

    var chosenFs by remember {
        mutableStateOf(if (f2fsSupported) FilesystemType.F2FS else (if (ext4Supported) FilesystemType.EXT4 else FilesystemType.FAT32))
    }
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

                // Filesystem Options with Kernel Detection Badges
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormatFsRadioOption(
                        type = FilesystemType.F2FS,
                        isSelected = chosenFs == FilesystemType.F2FS,
                        isRecommended = true,
                        isKernelSupported = f2fsSupported,
                        title = "F2FS",
                        subtitle = stringResource(R.string.format_f2fs_desc),
                        onSelect = { chosenFs = FilesystemType.F2FS }
                    )
                    FormatFsRadioOption(
                        type = FilesystemType.EXT4,
                        isSelected = chosenFs == FilesystemType.EXT4,
                        isRecommended = false,
                        isKernelSupported = ext4Supported,
                        title = "Ext4",
                        subtitle = stringResource(R.string.format_ext4_desc),
                        onSelect = { chosenFs = FilesystemType.EXT4 }
                    )
                    FormatFsRadioOption(
                        type = FilesystemType.FAT32,
                        isSelected = chosenFs == FilesystemType.FAT32,
                        isRecommended = false,
                        isKernelSupported = fat32Supported,
                        title = "FAT32",
                        subtitle = "Universal portable storage",
                        onSelect = { chosenFs = FilesystemType.FAT32 }
                    )
                    FormatFsRadioOption(
                        type = FilesystemType.EXFAT,
                        isSelected = chosenFs == FilesystemType.EXFAT,
                        isRecommended = false,
                        isKernelSupported = exfatSupported,
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
    isKernelSupported: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = isKernelSupported
    val borderColor = if (isSelected) {
        if (isRecommended) CyberEmerald else MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = if (isEnabled) 0.4f else 0.15f)
    }

    Surface(
        onClick = { if (isEnabled) onSelect() },
        enabled = isEnabled,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color.Transparent,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .then(if (!isEnabled) Modifier.alpha(0.5f) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = { if (isEnabled) onSelect() },
                enabled = isEnabled,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isKernelSupported) CyberEmerald.copy(alpha = 0.12f) else AmberWarn.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isKernelSupported) "Kernel Ready" else "Unsupported",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isKernelSupported) CyberEmerald else AmberWarn,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
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

// ── Filesystem Check (fsck) Structured Report Dialog ────────
@Composable
private fun FsckReportDialog(
    report: FsckReport,
    onDismiss: () -> Unit
) {
    var isExpandedLog by remember { mutableStateOf(false) }

    val statusColor = when (report.status) {
        FsckStatus.CLEAN -> CyberEmerald
        FsckStatus.REPAIRED -> ElectricCyan
        FsckStatus.DIRTY_WARNING -> AmberWarn
        FsckStatus.ERROR -> NeonCrimson
    }

    val statusBadgeText = when (report.status) {
        FsckStatus.CLEAN -> "Clean (Integritas Baik)"
        FsckStatus.REPAIRED -> "Diperbaiki Otomatis"
        FsckStatus.DIRTY_WARNING -> "Peringatan Direktori Kotor"
        FsckStatus.ERROR -> "Kesalahan Kritis"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Diagnostik Filesystem (fsck)",
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
                // Status Badge Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusBadgeText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = statusColor
                        )
                    }
                }

                // Summary Text
                Text(
                    text = report.summary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Metric Details Card
                if (report.filesCount > 0 || report.blocksCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (report.filesCount > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Files / Inodes", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val filesStr = if (report.totalFiles > 0) "${report.filesCount} / ${report.totalFiles}" else "${report.filesCount}"
                                    Text(filesStr, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            if (report.blocksCount > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Alokasi Blok", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val blocksStr = if (report.totalBlocks > 0) "${report.blocksCount} / ${report.totalBlocks}" else "${report.blocksCount}"
                                    Text(blocksStr, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                // Accordion CLI Raw Log
                if (report.rawLog.isNotBlank()) {
                    Surface(
                        onClick = { isExpandedLog = !isExpandedLog },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isExpandedLog) "Sembunyikan Log Shell" else "Lihat Output Shell (Raw Log)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (isExpandedLog) Icons.Default.Close else Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isExpandedLog) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                        ) {
                            Text(
                                text = report.rawLog,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                color = CyberEmerald,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(stringResource(R.string.common_close), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ── Unmount Partition Confirmation Dialog ─────────────────────
@Composable
private fun UnmountPartitionConfirmDialog(
    partition: PartitionInfo,
    diskName: String? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Eject,
                    contentDescription = null,
                    tint = NeonCrimson,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.storage_unmount_confirm_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!diskName.isNullOrBlank()) {
                        Text(
                            text = diskName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.storage_unmount_confirm_message, partition.cleanShortName.ifBlank { partition.name }, partition.fsType.uppercase()),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Compact Partition Metadata Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!diskName.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.storage_disk_title),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = diskName,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NeonCrimson.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = "P${partition.partitionNumber.takeIf { it > 0 } ?: 1}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCrimson
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = partition.path,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = partition.fsType.uppercase(),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberEmerald,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = partition.mountPoint ?: stringResource(R.string.storage_status_unmounted_badge),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (partition.isTargetMount) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (partition.sizeBytes > 0L) {
                                Text(
                                    text = FormatUtils.formatBytes(partition.sizeBytes),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Active Game Warning if Target Mount
                if (partition.isTargetMount) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NeonCrimson.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NeonCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.storage_unmount_confirm_warning_game),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                                color = NeonCrimson,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Eject,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.storage_unmount_confirm_btn), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(stringResource(R.string.common_cancel), fontSize = 11.5.sp)
            }
        }
    )
}

// ── Eject Disk Confirmation Dialog ────────────────────────────
@Composable
private fun EjectDiskConfirmDialog(
    disk: SdCardDiskInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val mountedPartitions = disk.partitions.filter { it.isMounted }
    val hasActiveGameMounts = mountedPartitions.any { it.isTargetMount }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Eject,
                    contentDescription = null,
                    tint = NeonCrimson,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.storage_eject_disk_confirm_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
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
                Text(
                    text = stringResource(R.string.storage_eject_disk_confirm_message, disk.displayName, mountedPartitions.size),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Compact Disk Info Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (disk.diskType == DiskType.MICRO_SD) Icons.Default.SdStorage else Icons.Default.Usb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = disk.diskName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = FormatUtils.formatBytes(disk.totalSizeBytes),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = disk.devicePath,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = ElectricCyan
                            )
                            Text(
                                text = "${mountedPartitions.size} partisi terpasang",
                                fontSize = 10.sp,
                                color = NeonCrimson,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Active Game Warning
                if (hasActiveGameMounts) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NeonCrimson.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NeonCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.storage_unmount_confirm_warning_game),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                                color = NeonCrimson,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Eject,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.storage_eject_disk_confirm_btn), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(stringResource(R.string.common_cancel), fontSize = 11.5.sp)
            }
        }
    )
}

// ── Edit Partition Label Dialog (Non-destructive) ───────────
@Composable
private fun EditPartitionLabelDialog(
    partition: PartitionInfo,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var labelInput by remember { mutableStateOf(partition.label?.ifBlank { "sdext2" } ?: "sdext2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.Label,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.storage_rename_label_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.storage_action_rename_label_desc),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Target: ${partition.path} (${partition.fsType.uppercase()})",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.storage_rename_label_field),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                            BasicTextField(
                                value = labelInput,
                                onValueChange = { labelInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
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
                onClick = { onConfirm(labelInput) },
                enabled = labelInput.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(stringResource(R.string.storage_rename_label_confirm), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(stringResource(R.string.common_cancel), fontSize = 11.5.sp)
            }
        }
    )
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
    MountXTheme(dynamicColor = false) {
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
