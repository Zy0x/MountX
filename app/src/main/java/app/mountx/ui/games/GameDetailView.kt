package app.mountx.ui.games

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import app.mountx.data.repository.CandidateDirectory
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import app.mountx.R
import app.mountx.data.model.AppStorageBreakdown
import app.mountx.data.model.GameEntry
import app.mountx.data.model.MigrationTarget
import app.mountx.data.model.DiskType
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.MountStatus
import app.mountx.data.model.MoveDirection
import app.mountx.data.model.PartitionInfo
import app.mountx.data.model.SdCardDiskInfo
import app.mountx.ui.components.AppIconImage
import app.mountx.ui.components.CompactScreenHeader
import app.mountx.ui.theme.CyberEmerald
import app.mountx.ui.theme.NeonCrimson
import app.mountx.util.FormatUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GameDetailView(
    game: GameEntry,
    breakdown: AppStorageBreakdown,
    isMoving: Boolean = false,
    moveMessage: String? = null,
    isDraftMode: Boolean = false,
    candidateDirectories: List<CandidateDirectory> = emptyList(),
    isLoadingCandidates: Boolean = false,
    availableDisks: List<SdCardDiskInfo> = emptyList(),
    internalFreeBytes: Long = 0L,
    isScanningDisks: Boolean = false,
    onQuickMountDisk: (SdCardDiskInfo) -> Unit = {},
    onQuickMountPartition: (PartitionInfo) -> Unit = {},
    onRefreshDisks: () -> Unit = {},
    sdBase: String = "/data/sdext2",
    onDismiss: () -> Unit,
    onMoveMountPoints: (MoveDirection, List<MountPointConfig>, SdCardDiskInfo?, PartitionInfo?) -> Unit = { _, _, _, _ -> },
    onSaveGame: ((GameEntry) -> Unit)? = null,
    onUpdateMountPoints: ((List<MountPointConfig>) -> Unit)? = null,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current

    var currentMountPoints by remember(game.packageName, game.mountPoints, candidateDirectories) {
        val initial = if (game.mountPoints.isNotEmpty()) {
            game.mountPoints
        } else if (candidateDirectories.isNotEmpty()) {
            candidateDirectories.map { cd ->
                MountPointConfig(
                    id = cd.id,
                    category = cd.category,
                    sourcePath = cd.sdPath,
                    targetPath = cd.internalPath,
                    enabled = cd.defaultEnabled,
                    isVirtualContainer = cd.isVirtualContainer,
                    containerImgPath = if (cd.isVirtualContainer) cd.sdPath else null,
                    sizeBytes = cd.sizeBytes
                )
            }
        } else {
            emptyList()
        }
        mutableStateOf(initial)
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    // Resolve app package metadata from PackageManager
    val packageInfo = remember(game.packageName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(game.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(game.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    val versionName = packageInfo?.versionName ?: "—"
    val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    val installTimeStr = remember(packageInfo?.firstInstallTime) {
        val time = packageInfo?.firstInstallTime ?: 0L
        if (time > 0L) {
            val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
            sdf.format(Date(time))
        } else {
            "—"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            CompactScreenHeader(
                title = stringResource(R.string.game_detail_app_title),
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", game.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.game_detail_system_info),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            )

            // ── PINNED APP HERO METADATA & CAPSULE TAB BAR ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // App Hero Metadata Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconImage(
                        packageName = game.packageName,
                        size = 48.dp
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = game.displayName.ifBlank { game.packageName },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = game.packageName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                lineHeight = 14.sp
                            ),
                            color = Color(0xFF00838F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = stringResource(R.string.game_detail_version, versionName, versionCode.toString()),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 13.sp
                            ),
                            color = Color(0xFF00838F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = stringResource(R.string.game_detail_install_time, installTimeStr),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 13.sp
                            ),
                            color = Color(0xFF00838F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(vertical = 1.dp)
                )

                // Capsule Tab Row (Storage vs Manage)
                DetailCapsuleTabRow(
                    selectedTabIndex = pagerState.targetPage,
                    onTabSelected = { index ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )
            }

            // ── SWIPEABLE HORIZONTAL PAGER CONTENT ──
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> StorageTabContent(breakdown = breakdown)
                    1 -> ManageTabContent(
                        game = game,
                        isDraftMode = isDraftMode,
                        mountPoints = currentMountPoints,
                        breakdown = breakdown,
                        availableDisks = availableDisks,
                        internalFreeBytes = internalFreeBytes,
                        isScanningDisks = isScanningDisks,
                        onQuickMountDisk = onQuickMountDisk,
                        onQuickMountPartition = onQuickMountPartition,
                        onRefreshDisks = onRefreshDisks,
                        onMountPointsChanged = { updated ->
                            currentMountPoints = updated
                            onUpdateMountPoints?.invoke(updated)
                        },
                        sdBase = sdBase,
                        isMoving = isMoving,
                        moveMessage = moveMessage,
                        onMove = { dir, disk, partition ->
                            val updatedPoints = currentMountPoints.map { pt ->
                                if (dir == MoveDirection.TO_SD) {
                                    val oldBase = sdBase
                                    val newBase = partition?.mountPoint ?: disk?.mountPath ?: sdBase
                                    val newSourcePath = if (pt.sourcePath.startsWith(oldBase)) {
                                        pt.sourcePath.replaceFirst(oldBase, newBase)
                                    } else if (!pt.sourcePath.startsWith(newBase)) {
                                        "$newBase/${pt.sourcePath.trimStart('/')}"
                                    } else {
                                        pt.sourcePath
                                    }
                                    pt.copy(diskUuid = partition?.uuid ?: disk?.uuid, sourcePath = newSourcePath)
                                } else {
                                    pt
                                }
                            }
                            currentMountPoints = updatedPoints
                            onUpdateMountPoints?.invoke(updatedPoints)
                            onMoveMountPoints(dir, updatedPoints, disk, partition)
                        },
                        onSaveDraft = {
                            val activeSize = currentMountPoints.filter { it.enabled }.sumOf { it.sizeBytes }
                            onSaveGame?.invoke(game.copy(mountPoints = currentMountPoints, dataSizeBytes = activeSize))
                            onDismiss()
                        },
                        onCancelDraft = onDismiss,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

/**
 * Modern Capsule / Pill Tab Bar for switching between Storage & Manage views.
 */
@Composable
private fun DetailCapsuleTabRow(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        stringResource(R.string.game_detail_tab_storage),
        stringResource(R.string.game_detail_tab_manage)
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTabIndex == index
                Surface(
                    onClick = { onTabSelected(index) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 0: Storage Breakdown & Visual Concentric Donut Chart
 */
@Composable
private fun StorageTabContent(
    breakdown: AppStorageBreakdown,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── CONCENTRIC PIE / DONUT CHART & 3-TIER LEGEND CARD ──
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ConcentricStorageChart(
                    breakdown = breakdown,
                    modifier = Modifier.size(152.dp)
                )

                // Storage Distribution Legend (Phone Memory, MicroSD, Total)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    // 1. Phone Memory (Internal)
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = Color(0xFFDF4006),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(R.string.game_detail_legend_phone),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = FormatUtils.formatLegendBytes(breakdown.phoneInternalBytes),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFDF4006),
                            modifier = Modifier.padding(start = 18.dp)
                        )
                    }

                    // 2. MicroSD Card
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SdCard,
                                contentDescription = null,
                                tint = Color(0xFF3BA71A),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(R.string.game_detail_legend_microsd),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = FormatUtils.formatLegendBytes(breakdown.microSdBytes),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF3BA71A),
                            modifier = Modifier.padding(start = 18.dp)
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(vertical = 1.dp)
                    )

                    // 3. Grand Total (Σ)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Σ",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = FormatUtils.formatLegendBytes(breakdown.totalBytes),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // ── DETAILED STORAGE BREAKDOWN CARD (WITH SMART DIMMING & GROUPING) ──
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Section 1 Header: System & Private Storage
                Text(
                    text = stringResource(R.string.game_detail_section_system).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                BreakdownRow(
                    label = stringResource(R.string.game_detail_cat_apk),
                    subLabel = stringResource(R.string.game_detail_cat_apk_sub),
                    labelColor = Color(0xFFE91E63),
                    sizeText = FormatUtils.formatExactBytes(breakdown.apkBytes),
                    bytes = breakdown.apkBytes,
                    isDisk = true,
                    customIconTint = Color(0xFFDF4006)
                )

                if (breakdown.libBytes > 0L) {
                    BreakdownRow(
                        label = stringResource(R.string.game_detail_cat_lib),
                        subLabel = stringResource(R.string.game_detail_cat_lib_sub),
                        labelColor = Color(0xFFFB8C00),
                        sizeText = FormatUtils.formatExactBytes(breakdown.libBytes),
                        bytes = breakdown.libBytes,
                        isDisk = true,
                        customIconTint = Color(0xFFDF4006)
                    )
                }

                BreakdownRow(
                    label = stringResource(R.string.game_detail_cat_data),
                    subLabel = stringResource(R.string.game_detail_cat_data_sub),
                    labelColor = Color(0xFF00897B),
                    sizeText = FormatUtils.formatExactBytes(breakdown.dataBytes),
                    bytes = breakdown.dataBytes,
                    isDisk = true,
                    customIconTint = Color(0xFFDF4006)
                )

                BreakdownRow(
                    label = stringResource(R.string.game_detail_cat_cache),
                    subLabel = stringResource(R.string.game_detail_cat_cache_sub),
                    labelColor = Color(0xFFE57373),
                    sizeText = FormatUtils.formatExactBytes(breakdown.cacheBytes),
                    bytes = breakdown.cacheBytes,
                    isDisk = true,
                    customIconTint = Color(0xFFDF4006)
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Section 2 Header: Shared Storage (MountX Target)
                Text(
                    text = stringResource(R.string.game_detail_section_shared).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                BreakdownRow(
                    label = stringResource(R.string.game_detail_cat_ext1),
                    subLabel = stringResource(R.string.game_detail_cat_ext1_sub),
                    labelColor = Color(0xFF5C6BC0),
                    sizeText = FormatUtils.formatExactBytes(breakdown.ext1Bytes),
                    bytes = breakdown.ext1Bytes,
                    vectorIcon = Icons.Default.Smartphone,
                    customIconTint = Color(0xFF3149FF),
                    statusBadge = if (breakdown.isExt1Mounted) stringResource(R.string.game_detail_badge_offloaded) else null
                )

                BreakdownRow(
                    label = stringResource(R.string.game_detail_cat_ext2),
                    subLabel = stringResource(R.string.game_detail_cat_ext2_sub),
                    labelColor = Color(0xFF43A047),
                    sizeText = FormatUtils.formatExactBytes(breakdown.ext2Bytes),
                    bytes = breakdown.ext2Bytes,
                    vectorIcon = Icons.Default.SdCard,
                    customIconTint = Color(0xFF3BA71A),
                    statusBadge = if (breakdown.ext2Bytes > 0L && breakdown.isExt1Mounted) stringResource(R.string.game_detail_badge_mounted) else null
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Tab 1: Universal Directory-Driven Mount & Migration Management Hub (v2.2.14)
 * Single Source of Truth: Active MountPointConfig entries.
 */
@Composable
private fun ManageTabContent(
    game: GameEntry,
    isDraftMode: Boolean,
    mountPoints: List<MountPointConfig>,
    breakdown: AppStorageBreakdown? = null,
    availableDisks: List<SdCardDiskInfo> = emptyList(),
    internalFreeBytes: Long = 0L,
    isScanningDisks: Boolean = false,
    onQuickMountDisk: (SdCardDiskInfo) -> Unit = {},
    onQuickMountPartition: (PartitionInfo) -> Unit = {},
    onRefreshDisks: () -> Unit = {},
    onMountPointsChanged: (List<MountPointConfig>) -> Unit,
    sdBase: String,
    isMoving: Boolean,
    moveMessage: String?,
    onMove: (MoveDirection, SdCardDiskInfo?, PartitionInfo?) -> Unit,
    onSaveDraft: () -> Unit,
    onCancelDraft: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomPathDialog by remember { mutableStateOf(false) }
    var selectedPointForDetail by remember { mutableStateOf<MountPointConfig?>(null) }

    val isMounted = game.mountStatus == MountStatus.MOUNTED
    val activeMountPoints = mountPoints.filter { it.enabled }
    val totalActiveSizeBytes = activeMountPoints.sumOf { getEffectiveMountPointSize(it, breakdown) }

    // Synthetic fallback/default MountX storage partition (/data/sdext2)
    val defaultMountXPartition = remember(sdBase, internalFreeBytes) {
        PartitionInfo(
            path = sdBase,
            name = "sdext2",
            diskName = "local",
            partitionNumber = 1,
            sizeBytes = internalFreeBytes,
            usedBytes = 0L,
            freeBytes = internalFreeBytes,
            fsType = "f2fs",
            mountPoint = sdBase,
            label = "STANDAR_MOUNTX",
            uuid = "local_mountx_default",
            isMounted = true,
            isTargetMount = true,
            isPortableMount = false,
            isMountTargetReady = true
        )
    }

    // Helper to extract partitions from a disk (or synthesize one if disk has no raw partition table)
    fun getDiskPartitions(disk: SdCardDiskInfo): List<PartitionInfo> {
        return if (disk.partitions.isNotEmpty()) {
            disk.partitions
        } else {
            listOf(
                PartitionInfo(
                    path = disk.devicePath,
                    name = disk.diskName,
                    diskName = disk.diskName,
                    partitionNumber = 1,
                    sizeBytes = disk.totalSizeBytes,
                    usedBytes = disk.totalUsedBytes,
                    freeBytes = disk.totalFreeBytes,
                    fsType = "ext4",
                    mountPoint = disk.mountPath,
                    isMounted = disk.isMounted,
                    isTargetMount = true,
                    isMountTargetReady = true
                )
            )
        }
    }

    // Auto-select single disk or previously matched disk
    var selectedDisk by remember(availableDisks) {
        val matchingDisk = if (mountPoints.isNotEmpty()) {
            val boundUuid = mountPoints.firstOrNull { it.diskUuid != null }?.diskUuid
            availableDisks.firstOrNull { it.uuid == boundUuid }
        } else null
        mutableStateOf(matchingDisk ?: availableDisks.firstOrNull())
    }

    // Auto-select partition from selectedDisk, or default to fallback partition
    var selectedPartition by remember(selectedDisk, availableDisks) {
        val currentDisk = selectedDisk
        val initialPartition: PartitionInfo? = if (currentDisk != null) {
            val candidatePartitions = getDiskPartitions(currentDisk)
            candidatePartitions.firstOrNull { it.isTargetMount }
                ?: candidatePartitions.firstOrNull { it.isMounted }
                ?: candidatePartitions.firstOrNull { it.fsType.lowercase() in listOf("ext4", "f2fs") }
                ?: candidatePartitions.firstOrNull()
        } else {
            defaultMountXPartition
        }
        mutableStateOf(initialPartition)
    }

    val targetFreeSpace = selectedPartition?.freeBytes
        ?: selectedDisk?.totalFreeBytes
        ?: internalFreeBytes
    val hasSufficientDiskSpace = targetFreeSpace >= totalActiveSizeBytes
    val hasSufficientInternalSpace = internalFreeBytes == 0L || internalFreeBytes >= totalActiveSizeBytes
    val remainingTargetSpace = (targetFreeSpace - totalActiveSizeBytes).coerceAtLeast(0L)
    val isTargetMounted = selectedPartition?.isMounted ?: selectedDisk?.isMounted ?: (selectedDisk == null)

    val cyberEmerald = CyberEmerald
    val electricAmber = Color(0xFFFFB300)
    val neonCrimson = NeonCrimson

    if (showCustomPathDialog) {
        CustomPathDialog(
            sdBase = selectedPartition?.mountPoint ?: selectedDisk?.mountPath ?: sdBase,
            onDismiss = { showCustomPathDialog = false },
            onAdd = { customPoint ->
                val boundPoint = if (selectedDisk != null) {
                    customPoint.copy(diskUuid = selectedDisk?.uuid)
                } else customPoint
                onMountPointsChanged(mountPoints + boundPoint)
                showCustomPathDialog = false
            }
        )
    }

    if (selectedPointForDetail != null) {
        val point = selectedPointForDetail!!
        val effSize = getEffectiveMountPointSize(point, breakdown)
        DirectoryDetailDialog(
            point = point,
            effectiveSize = effSize,
            isMounted = isMounted,
            onDismiss = { selectedPointForDetail = null }
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── NOVICE GUIDE BANNER (CARA KERJA PEMINDAHAN DATA) ──
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = electricAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = stringResource(R.string.mount_guide_banner_title),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.mount_guide_banner_desc),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 14.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── LANGKAH 1: PILIH DATA YANG INGIN DIALIHKAN ──
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "1",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.mount_step1_title),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.mount_step1_desc),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = "${activeMountPoints.size} / ${mountPoints.size} Aktif",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    if (mountPoints.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Belum ada direktori data terdeteksi.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        mountPoints.forEachIndexed { index, point ->
                            val effSize = getEffectiveMountPointSize(point, breakdown)
                            MountPointItemCard(
                                point = point,
                                isMounted = isMounted,
                                effectiveSize = effSize,
                                targetDiskName = selectedDisk?.hardwareTitle ?: "MicroSD",
                                onToggleEnabled = { checked ->
                                    val updatedList = mountPoints.toMutableList()
                                    updatedList[index] = point.copy(enabled = checked)
                                    onMountPointsChanged(updatedList)
                                },
                                onDeleteCustom = if (point.category == MountPointCategory.CUSTOM) {
                                    {
                                        val updatedList = mountPoints.toMutableList()
                                        updatedList.removeAt(index)
                                        onMountPointsChanged(updatedList)
                                    }
                                } else null,
                                onClick = { selectedPointForDetail = point }
                            )
                        }
                    }

                    // Button: Add Custom Path
                    OutlinedButton(
                        onClick = { showCustomPathDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.mount_add_custom_path),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── LANGKAH 2: PILIH DISK & PARTISI PENYIMPANAN TUJUAN ──
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF00ACC1),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "2",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.mount_step2_title),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.mount_step2_desc),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Rescan Storage Button
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clickable(enabled = !isScanningDisks) { onRefreshDisks() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isScanningDisks) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(11.dp),
                                        strokeWidth = 1.5.dp,
                                        color = Color(0xFF00ACC1)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFF00ACC1),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = if (isScanningDisks) stringResource(R.string.mount_disk_refreshing)
                                           else stringResource(R.string.mount_disk_refresh),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color(0xFF00ACC1)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // ── DAFTAR DISK FISIK TERDETEKSI (MICRSD / USB OTG / SSD TYPE-C) ──
                    if (availableDisks.isNotEmpty()) {
                        availableDisks.forEach { disk ->
                            val isDiskSelected = selectedDisk == disk
                            val isDiskMounted = disk.isMounted
                            val partitionsOfThisDisk = getDiskPartitions(disk)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDiskSelected) CyberEmerald.copy(alpha = 0.05f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    if (isDiskSelected) 1.5.dp else 1.dp,
                                    if (isDiskSelected) CyberEmerald
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDisk = disk }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Disk Header Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Custom Radio Indicator for Disk
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(
                                                    color = if (isDiskSelected) CyberEmerald else Color.Transparent,
                                                    shape = RoundedCornerShape(9.dp)
                                                )
                                                .border(
                                                    width = if (isDiskSelected) 0.dp else 1.5.dp,
                                                    color = if (isDiskSelected) CyberEmerald else MaterialTheme.colorScheme.outline,
                                                    shape = RoundedCornerShape(9.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isDiskSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(Color.Black, RoundedCornerShape(3.dp))
                                                )
                                            }
                                        }

                                        // Disk Icon
                                        Icon(
                                            imageVector = if (disk.diskType == DiskType.MICRO_SD) Icons.Default.SdCard
                                                          else Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = if (isDiskSelected) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )

                                        // Disk Name & Hardware Info
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = disk.displayName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (disk.diskType == DiskType.MICRO_SD) "Slot MicroSD Card"
                                                       else "Eksternal USB Type-C / OTG",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Status Badge / Quick Mount Disk
                                        if (isDiskMounted) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = CyberEmerald.copy(alpha = 0.12f),
                                                border = BorderStroke(0.5.dp, CyberEmerald.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.mount_disk_ready_badge),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = CyberEmerald,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { onQuickMountDisk(disk) },
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = electricAmber,
                                                    contentColor = Color.Black
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(26.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.mount_disk_quick_mount),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Visual Capacity Progress Bar for Disk
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Total Disk: ${FormatUtils.formatBytes(disk.totalSizeBytes)}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Bebas: ${FormatUtils.formatBytes(disk.totalFreeBytes)}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = CyberEmerald
                                            )
                                        }

                                        LinearProgressIndicator(
                                            progress = { disk.usedPercent },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp),
                                            color = if (disk.usedPercent > 0.9f) NeonCrimson else CyberEmerald,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }

                                    // ── DAFTAR PARTISI PADA DISK TERPILIH ──
                                    if (isDiskSelected) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )

                                        Text(
                                            text = stringResource(R.string.mount_disk_partitions_header),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            partitionsOfThisDisk.forEach { part ->
                                                val isPartSelected = selectedPartition?.path == part.path
                                                val isFsLinux = part.fsType.lowercase() in listOf("ext4", "f2fs")
                                                val isPartCapacityWarning = isPartSelected && totalActiveSizeBytes > part.freeBytes

                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isPartSelected) CyberEmerald.copy(alpha = 0.08f)
                                                            else MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(
                                                        if (isPartSelected) 1.2.dp else 0.8.dp,
                                                        if (isPartSelected) CyberEmerald else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { selectedPartition = part }
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            // Partition Radio Indicator
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(15.dp)
                                                                    .background(
                                                                        color = if (isPartSelected) CyberEmerald else Color.Transparent,
                                                                        shape = RoundedCornerShape(7.5.dp)
                                                                    )
                                                                    .border(
                                                                        width = if (isPartSelected) 0.dp else 1.2.dp,
                                                                        color = if (isPartSelected) CyberEmerald else MaterialTheme.colorScheme.outline,
                                                                        shape = RoundedCornerShape(7.5.dp)
                                                                    ),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                if (isPartSelected) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(5.dp)
                                                                            .background(Color.Black, RoundedCornerShape(2.5.dp))
                                                                    )
                                                                }
                                                            }

                                                            // Partition Icon
                                                            Icon(
                                                                imageVector = Icons.Default.PieChart,
                                                                contentDescription = null,
                                                                tint = if (isPartSelected) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(16.dp)
                                                            )

                                                            // Partition Name & Badges
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                ) {
                                                                    Text(
                                                                        text = part.cleanShortName,
                                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        ),
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                    )

                                                                    // Filesystem Badge
                                                                    if (part.fsType.isNotBlank()) {
                                                                        Surface(
                                                                            shape = RoundedCornerShape(4.dp),
                                                                            color = if (isFsLinux) CyberEmerald.copy(alpha = 0.15f)
                                                                                    else electricAmber.copy(alpha = 0.15f),
                                                                            border = BorderStroke(
                                                                                0.5.dp,
                                                                                if (isFsLinux) CyberEmerald.copy(alpha = 0.4f)
                                                                                else electricAmber.copy(alpha = 0.4f)
                                                                            )
                                                                        ) {
                                                                            Text(
                                                                                text = part.fsType.uppercase(),
                                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                                    fontSize = 8.5.sp,
                                                                                    fontWeight = FontWeight.Bold
                                                                                ),
                                                                                color = if (isFsLinux) CyberEmerald else electricAmber,
                                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                            )
                                                                        }
                                                                    }

                                                                    if (isFsLinux) {
                                                                        Surface(
                                                                            shape = RoundedCornerShape(4.dp),
                                                                            color = CyberEmerald.copy(alpha = 0.1f)
                                                                        ) {
                                                                            Text(
                                                                                text = stringResource(R.string.mount_partition_recommended_badge),
                                                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                                                color = CyberEmerald,
                                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }

                                                                Text(
                                                                    text = "Bebas: ${FormatUtils.formatBytes(part.freeBytes)} / Total: ${FormatUtils.formatBytes(part.sizeBytes)}",
                                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }

                                                            // Mount Status or Quick Mount Partition Button
                                                            if (part.isMounted) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = CyberEmerald.copy(alpha = 0.12f),
                                                                    border = BorderStroke(0.5.dp, CyberEmerald.copy(alpha = 0.35f))
                                                                ) {
                                                                    Text(
                                                                        text = stringResource(R.string.mount_disk_ready_badge),
                                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                                            fontSize = 8.5.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        ),
                                                                        color = CyberEmerald,
                                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            } else {
                                                                Button(
                                                                    onClick = { onQuickMountPartition(part) },
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    colors = ButtonDefaults.buttonColors(
                                                                        containerColor = electricAmber,
                                                                        contentColor = Color.Black
                                                                    ),
                                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                                    modifier = Modifier.height(24.dp)
                                                                ) {
                                                                    Text(
                                                                        text = stringResource(R.string.mount_partition_quick_mount),
                                                                        fontSize = 8.5.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // Mini Partition Progress Bar
                                                        LinearProgressIndicator(
                                                            progress = { part.usedPercent },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(3.dp),
                                                            color = if (part.usedPercent > 0.9f) NeonCrimson else CyberEmerald,
                                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                                        )

                                                        // Filesystem format warning (if FAT32/exFAT)
                                                        if (isPartSelected && !isFsLinux && part.fsType.isNotBlank()) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = electricAmber.copy(alpha = 0.08f),
                                                                border = BorderStroke(0.5.dp, electricAmber.copy(alpha = 0.35f)),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Info,
                                                                        contentDescription = null,
                                                                        tint = electricAmber,
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                    Text(
                                                                        text = stringResource(R.string.mount_partition_format_warning, part.fsType.uppercase()),
                                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp),
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // Capacity Guard Warning on Partition
                                                        if (isPartCapacityWarning) {
                                                            val deficitBytes = totalActiveSizeBytes - part.freeBytes
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = NeonCrimson.copy(alpha = 0.08f),
                                                                border = BorderStroke(0.5.dp, NeonCrimson.copy(alpha = 0.35f)),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Error,
                                                                        contentDescription = null,
                                                                        tint = NeonCrimson,
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                    Text(
                                                                        text = stringResource(
                                                                            R.string.mount_disk_capacity_warning,
                                                                            FormatUtils.formatBytes(deficitBytes)
                                                                        ),
                                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                                            fontSize = 8.5.sp,
                                                                            fontWeight = FontWeight.SemiBold
                                                                        ),
                                                                        color = NeonCrimson
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── OPSI TARGET CADANGAN / PENYIMPANAN STANDAR MOUNTX (/data/sdext2) ──
                    val isStandardSelected = selectedDisk == null
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isStandardSelected) CyberEmerald.copy(alpha = 0.06f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            if (isStandardSelected) 1.5.dp else 1.dp,
                            if (isStandardSelected) CyberEmerald
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedDisk = null
                                selectedPartition = defaultMountXPartition
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Radio indicator
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(
                                            color = if (isStandardSelected) CyberEmerald else Color.Transparent,
                                            shape = RoundedCornerShape(9.dp)
                                        )
                                        .border(
                                            width = if (isStandardSelected) 0.dp else 1.5.dp,
                                            color = if (isStandardSelected) CyberEmerald else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(9.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isStandardSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color.Black, RoundedCornerShape(3.dp))
                                        )
                                    }
                                }

                                // Icon
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (isStandardSelected) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )

                                // Name & Subtitle
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.mount_standard_storage_title),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.mount_standard_storage_subtitle, sdBase),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Badge
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CyberEmerald.copy(alpha = 0.12f),
                                    border = BorderStroke(0.5.dp, CyberEmerald.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = stringResource(R.string.mount_disk_ready_badge),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = CyberEmerald,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Capacity Info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.mount_standard_storage_badge),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Bebas: ${FormatUtils.formatBytes(internalFreeBytes)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = CyberEmerald
                                )
                            }
                        }
                    }

                    // ── KARTU PANDUAN MEDIA EKSTERNAL JIKA BELUM ADA DISK TERDETEKSI ──
                    if (availableDisks.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, electricAmber.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Usb,
                                        contentDescription = null,
                                        tint = electricAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = stringResource(R.string.mount_external_guidance_title),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = electricAmber
                                        )
                                        Text(
                                            text = stringResource(R.string.mount_external_guidance_desc),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── LANGKAH 3: TINJAU & EKSEKUSI (PINNED STICKY BOTTOM BAR) ──
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mini summary row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CyberEmerald,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "3",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.Black
                                )
                            }
                        }
                        Text(
                            text = if (selectedDisk != null) {
                                val diskName = selectedDisk!!.hardwareTitle
                                val partInfo = selectedPartition?.cleanShortName ?: "Part 1"
                                val fsInfo = selectedPartition?.fsType?.uppercase() ?: "EXT4"
                                stringResource(
                                    R.string.mount_step3_summary_partition,
                                    FormatUtils.formatBytes(totalActiveSizeBytes),
                                    "$diskName • $partInfo",
                                    fsInfo,
                                    FormatUtils.formatBytes(remainingTargetSpace)
                                )
                            } else {
                                stringResource(
                                    R.string.mount_step3_summary_partition,
                                    FormatUtils.formatBytes(totalActiveSizeBytes),
                                    stringResource(R.string.mount_standard_storage_title),
                                    "F2FS",
                                    FormatUtils.formatBytes(remainingTargetSpace)
                                )
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isDraftMode) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.mount_btn_remove_game),
                                tint = NeonCrimson.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Action Buttons
                if (isDraftMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancelDraft,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = "Batal",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onSaveDraft,
                            enabled = activeMountPoints.isNotEmpty(),
                            modifier = Modifier
                                .weight(2f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Terapkan & Tambahkan",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    if (isMoving) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.common_loading),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        if (moveMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (moveMessage == "SUCCESS")
                                    CyberEmerald.copy(alpha = 0.12f)
                                else
                                    NeonCrimson.copy(alpha = 0.12f),
                                border = BorderStroke(
                                    1.dp,
                                    if (moveMessage == "SUCCESS") CyberEmerald.copy(alpha = 0.4f) else NeonCrimson.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (moveMessage == "SUCCESS") Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (moveMessage == "SUCCESS") CyberEmerald else NeonCrimson,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (moveMessage == "SUCCESS")
                                            stringResource(R.string.move_data_success)
                                        else
                                            stringResource(R.string.move_data_error, moveMessage),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                        color = if (moveMessage == "SUCCESS") CyberEmerald else NeonCrimson,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val targetDiskTitle = selectedDisk?.hardwareTitle ?: stringResource(R.string.mount_standard_storage_title)
                            val targetPartTitle = selectedPartition?.cleanShortName ?: "Part 1"
                            val canMoveToTarget = activeMountPoints.isNotEmpty() && isTargetMounted && hasSufficientDiskSpace

                            // If selected target partition is not mounted yet, show Quick Mount button first
                            if (!isTargetMounted) {
                                Button(
                                    onClick = {
                                        selectedPartition?.let { onQuickMountPartition(it) }
                                            ?: selectedDisk?.let { onQuickMountDisk(it) }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = electricAmber,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.mount_partition_quick_mount),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                // Move to Target Partition Button
                                Button(
                                    onClick = { onMove(MoveDirection.TO_SD, selectedDisk, selectedPartition) },
                                    enabled = canMoveToTarget,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedDisk != null) {
                                            stringResource(
                                                R.string.mount_btn_move_to_partition,
                                                targetDiskTitle,
                                                targetPartTitle,
                                                FormatUtils.formatBytes(totalActiveSizeBytes)
                                            )
                                        } else {
                                            stringResource(
                                                R.string.mount_btn_move_to_disk,
                                                targetDiskTitle,
                                                FormatUtils.formatBytes(totalActiveSizeBytes)
                                            )
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Restore to Internal Button (if game has data mounted/on SD)
                            if (isMounted) {
                                OutlinedButton(
                                    onClick = { onMove(MoveDirection.TO_INTERNAL, selectedDisk, selectedPartition) },
                                    enabled = activeMountPoints.isNotEmpty() && hasSufficientInternalSpace,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.mount_btn_restore_internal,
                                            FormatUtils.formatBytes(totalActiveSizeBytes)
                                        ),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomPathDialog(
    sdBase: String,
    onDismiss: () -> Unit,
    onAdd: (MountPointConfig) -> Unit
) {
    var customLabel by remember { mutableStateOf("") }
    var internalPath by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val rawInternal = internalPath.trim()
    val isPathSafe = !rawInternal.startsWith("/data/app") && !rawInternal.startsWith("/system")

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF111625),
            border = BorderStroke(1.dp, Color(0xFF232B3E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.custom_path_dialog_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.custom_path_dialog_desc),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { customLabel = it },
                    label = { Text(stringResource(R.string.custom_path_label_hint), fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberEmerald,
                        unfocusedBorderColor = Color(0xFF232B3E),
                        focusedLabelColor = CyberEmerald,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(alpha = 0.85f),
                        cursorColor = CyberEmerald
                    )
                )

                OutlinedTextField(
                    value = internalPath,
                    onValueChange = {
                        internalPath = it
                        errorMsg = null
                    },
                    label = { Text(stringResource(R.string.custom_path_internal_hint), fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberEmerald,
                        unfocusedBorderColor = Color(0xFF232B3E),
                        focusedLabelColor = CyberEmerald,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(alpha = 0.85f),
                        cursorColor = CyberEmerald
                    )
                )

                if (!isPathSafe && rawInternal.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.custom_path_error_security),
                        color = NeonCrimson,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                errorMsg?.let {
                    Text(it, color = NeonCrimson, fontSize = 10.sp)
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.custom_path_cancel), color = Color.White.copy(alpha = 0.6f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (customLabel.isBlank() || internalPath.isBlank()) {
                                errorMsg = "Semua kolom wajib diisi"
                                return@Button
                            }
                            if (!isPathSafe) {
                                errorMsg = "Target path melanggar kebijakan keamanan"
                                return@Button
                            }
                            val cleanLabel = customLabel.trim().replace(" ", "_")
                            val cleanInternal = internalPath.trim().removeSuffix("/")
                            val customPoint = MountPointConfig(
                                id = cleanLabel,
                                category = MountPointCategory.CUSTOM,
                                sourcePath = "$sdBase/$cleanLabel",
                                targetPath = cleanInternal,
                                enabled = true,
                                sizeBytes = 0L
                            )
                            onAdd(customPoint)
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald)
                    ) {
                        Text(stringResource(R.string.custom_path_save), fontSize = 12.sp, color = Color.Black)
                    }
                }
            }
        }
    }
}

/**
 * Calculates effective storage size for a mount point taking telemetry breakdown into account
 * if persisted point.sizeBytes is unmeasured or 0.
 */
private fun getEffectiveMountPointSize(point: MountPointConfig, breakdown: AppStorageBreakdown?): Long {
    if (point.sizeBytes > 0L) return point.sizeBytes
    if (breakdown == null) return 0L
    val category = point.resolveCategory()
    return when (category) {
        MountPointCategory.OBB_STORAGE -> maxOf(breakdown.ext1ObbBytes, breakdown.ext2ObbBytes)
        MountPointCategory.EXTERNAL_DATA -> {
            val dataSize = maxOf(breakdown.ext1DataBytes, breakdown.ext2DataBytes)
            if (dataSize > 0L) dataSize else maxOf(breakdown.ext1Bytes, breakdown.ext2Bytes)
        }
        else -> point.sizeBytes
    }
}

/**
 * Item Card representing a directory mount point with clear title (Data vs OBB),
 * relative path, MicroSD -> Phone flow indicators, real size, and tap-for-details affordance.
 */
@Composable
private fun MountPointItemCard(
    point: MountPointConfig,
    isMounted: Boolean,
    effectiveSize: Long,
    targetDiskName: String = "MicroSD",
    onToggleEnabled: (Boolean) -> Unit,
    onDeleteCustom: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val category = point.resolveCategory()
    val categoryTitle = when (category) {
        MountPointCategory.EXTERNAL_DATA -> stringResource(R.string.gerbong_external_data_title)
        MountPointCategory.OBB_STORAGE -> stringResource(R.string.gerbong_obb_title)
        MountPointCategory.MEDIA_DOWNLOADS -> stringResource(R.string.gerbong_media_title)
        MountPointCategory.CACHE_SHADERS -> stringResource(R.string.gerbong_cache_title)
        MountPointCategory.PRIVATE_INTERNAL -> stringResource(R.string.gerbong_private_title)
        MountPointCategory.APP_PACKAGE -> stringResource(R.string.gerbong_app_package_title)
        MountPointCategory.CUSTOM -> point.id.replace('_', ' ')
        else -> point.id
    }

    val iconVector = when (category) {
        MountPointCategory.EXTERNAL_DATA -> Icons.Default.Folder
        MountPointCategory.OBB_STORAGE -> Icons.Default.Inventory2
        MountPointCategory.MEDIA_DOWNLOADS -> Icons.Default.PermMedia
        MountPointCategory.CACHE_SHADERS -> Icons.Default.Cached
        MountPointCategory.PRIVATE_INTERNAL -> Icons.Default.Lock
        MountPointCategory.APP_PACKAGE -> Icons.Default.Android
        else -> Icons.Default.Storage
    }

    val iconTint = when (category) {
        MountPointCategory.EXTERNAL_DATA -> MaterialTheme.colorScheme.primary
        MountPointCategory.OBB_STORAGE -> Color(0xFFFFB300)
        MountPointCategory.MEDIA_DOWNLOADS -> Color(0xFF00ACC1)
        MountPointCategory.CACHE_SHADERS -> Color(0xFFAB47BC)
        else -> CyberEmerald
    }

    val cleanRelative = point.getCleanRelativePath()
    val sizeText = if (effectiveSize > 0L) {
        FormatUtils.formatBytes(effectiveSize)
    } else {
        stringResource(R.string.mount_empty_folder)
    }

    var isAccordionOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (point.enabled) 0.5f else 0.22f),
        border = BorderStroke(
            1.dp,
            if (point.enabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Category Icon
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconTint.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = if (point.enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Middle Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = categoryTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (point.enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        // Pill Status Lokasi
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isMounted) CyberEmerald.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                0.5.dp,
                                if (isMounted) CyberEmerald.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Text(
                                text = if (isMounted) stringResource(R.string.mount_location_external, targetDiskName)
                                else stringResource(R.string.mount_location_internal),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isMounted) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }

                    // Clean Path
                    Text(
                        text = cleanRelative,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Size + Accordion Subfolder Trigger
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (effectiveSize > 0L) CyberEmerald.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = sizeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (effectiveSize > 0L) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        // Accordion Trigger
                        Row(
                            modifier = Modifier
                                .clickable { isAccordionOpen = !isAccordionOpen }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (category == MountPointCategory.OBB_STORAGE) "Berkas OBB" else "Struktur Folder",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (isAccordionOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Right Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (onDeleteCustom != null) {
                        IconButton(
                            onClick = onDeleteCustom,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus",
                                tint = NeonCrimson.copy(alpha = 0.8f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Switch(
                        checked = point.enabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberEmerald,
                            checkedTrackColor = CyberEmerald.copy(alpha = 0.35f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // Collapsible Accordion Details
            AnimatedVisibility(
                visible = isAccordionOpen,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Path Internal HP:",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = point.targetPath,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Path Media Eksternal ($targetDiskName):",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = point.sourcePath,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            color = CyberEmerald
                        )
                    }
                }
            }
        }
    }
}

/**
 * Novice-friendly Directory Detail Dialog displaying explanation,
 * absolute paths (MicroSD & Phone), live status, and one-tap copy buttons.
 */
@Composable
private fun DirectoryDetailDialog(
    point: MountPointConfig,
    effectiveSize: Long,
    isMounted: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val category = point.resolveCategory()
    val categoryTitle = when (category) {
        MountPointCategory.EXTERNAL_DATA -> stringResource(R.string.gerbong_external_data_title)
        MountPointCategory.OBB_STORAGE -> stringResource(R.string.gerbong_obb_title)
        MountPointCategory.MEDIA_DOWNLOADS -> stringResource(R.string.gerbong_media_title)
        MountPointCategory.CACHE_SHADERS -> stringResource(R.string.gerbong_cache_title)
        MountPointCategory.PRIVATE_INTERNAL -> stringResource(R.string.gerbong_private_title)
        MountPointCategory.APP_PACKAGE -> stringResource(R.string.gerbong_app_package_title)
        MountPointCategory.CUSTOM -> point.id.replace('_', ' ')
        else -> point.id
    }

    val noviceExplanation = when (category) {
        MountPointCategory.EXTERNAL_DATA -> stringResource(R.string.gerbong_external_data_desc)
        MountPointCategory.OBB_STORAGE -> stringResource(R.string.gerbong_obb_desc)
        MountPointCategory.MEDIA_DOWNLOADS -> stringResource(R.string.gerbong_media_desc)
        MountPointCategory.CACHE_SHADERS -> stringResource(R.string.gerbong_cache_desc)
        MountPointCategory.PRIVATE_INTERNAL -> stringResource(R.string.gerbong_private_desc)
        MountPointCategory.APP_PACKAGE -> stringResource(R.string.gerbong_app_package_desc)
        else -> "Direktori penyimpanan kustom yang dihubungkan secara langsung ke partisi MicroSD."
    }

    val copyToClipboard: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("MountX Path", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.mount_path_copied), Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${stringResource(R.string.mount_details_title)}: $categoryTitle",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = point.getCleanRelativePath(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Size Pill Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (effectiveSize > 0L) CyberEmerald.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (effectiveSize > 0L) CyberEmerald.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = if (effectiveSize > 0L) FormatUtils.formatBytes(effectiveSize)
                            else stringResource(R.string.mount_empty_folder),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (effectiveSize > 0L) CyberEmerald
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Novice Explanation Card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = noviceExplanation,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Path Detail 1: Source (MicroSD)
                PathDetailBlock(
                    label = stringResource(R.string.mount_details_source_label),
                    path = point.sourcePath,
                    icon = Icons.Default.SdCard,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onCopy = { copyToClipboard(point.sourcePath) }
                )

                // Path Detail 2: Target (Ponsel Internal)
                PathDetailBlock(
                    label = stringResource(R.string.mount_details_target_label),
                    path = point.targetPath,
                    icon = Icons.Default.Smartphone,
                    iconTint = CyberEmerald,
                    onCopy = { copyToClipboard(point.targetPath) }
                )

                // Mount Status Summary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.mount_details_status_label),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (!point.enabled) "Nonaktif"
                        else if (isMounted) stringResource(R.string.mount_status_active_sd)
                        else stringResource(R.string.mount_status_phone_only),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (!point.enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else if (isMounted) CyberEmerald
                        else MaterialTheme.colorScheme.primary
                    )
                }

                // Close Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.mount_close),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

/**
 * Compact Path Detail Block with 1-tap copy button and monospace path display.
 */
@Composable
private fun PathDetailBlock(
    label: String,
    path: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = iconTint
            )
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.mount_copy_path),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Custom 3.5" Floppy/Hard Disk Icon for internal disk graphic.
 */
@Composable
fun FloppyDiskIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Outer disk body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.08f),
            size = Size(w * 0.84f, h * 0.84f),
            cornerRadius = CornerRadius(w * 0.16f, h * 0.16f)
        )
        // Top slider / label slot (white cutout)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.24f, h * 0.16f),
            size = Size(w * 0.52f, h * 0.28f),
            cornerRadius = CornerRadius(w * 0.05f, h * 0.05f)
        )
        // Center hub (white cutout circle)
        drawCircle(
            color = Color.White,
            radius = w * 0.15f,
            center = Offset(w * 0.5f, h * 0.65f)
        )
        // Center dot (tint)
        drawCircle(
            color = tint,
            radius = w * 0.06f,
            center = Offset(w * 0.5f, h * 0.65f)
        )
    }
}

/**
 * Concentric Pie / Donut Storage Telemetry Chart:
 * - Outer Ring: 7 slices for components (Dex, Lib, Data, Cache, Ext 1, Ext 2, Apk) with slice percentage labels
 * - Inner Circle: Solid Internal vs External representation with dashed dividing line and percentage labels
 */
@Composable
fun ConcentricStorageChart(
    breakdown: AppStorageBreakdown,
    modifier: Modifier = Modifier
) {
    val totalBytes = breakdown.totalBytes

    val slices = remember(breakdown) {
        listOf(
            ChartSlice("Dex", breakdown.dexBytes, Color(0xFFAB47BC)),
            ChartSlice("Lib", breakdown.libBytes, Color(0xFFFB8C00)),
            ChartSlice("Data", breakdown.dataBytes, Color(0xFF00ACC1)),
            ChartSlice("Cache", breakdown.cacheBytes, Color(0xFFE57373)),
            ChartSlice("Ext1", breakdown.ext1Bytes, Color(0xFF3149FF)),
            ChartSlice("Ext2", breakdown.ext2Bytes, Color(0xFF43A047)),
            ChartSlice("Apk", breakdown.apkBytes, Color(0xFFE91E63))
        )
    }

    val internalColor = Color(0xFFDF4006)
    val extColor = Color(0xFF3BA71A)
    val neutralTrack = MaterialTheme.colorScheme.surfaceVariant

    val density = LocalDensity.current
    val textPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 9.5.dp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
    }

    val centerTextPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 9.5.dp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
    }

    val dashPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(180, 255, 255, 255)
            strokeWidth = with(density) { 1.4.dp.toPx() }
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 4f), 0f)
            style = android.graphics.Paint.Style.STROKE
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)

        if (totalBytes <= 0L) {
            drawCircle(
                color = neutralTrack,
                radius = diameter * 0.45f,
                center = center,
                style = Stroke(width = diameter * 0.16f)
            )
            drawCircle(
                color = neutralTrack.copy(alpha = 0.5f),
                radius = diameter * 0.28f,
                center = center
            )
            return@Canvas
        }

        // 1. Draw Outer Donut Ring
        val outerRadius = diameter * 0.48f
        val innerRadius = diameter * 0.33f
        val strokeWidth = outerRadius - innerRadius
        val ringCenterRadius = (outerRadius + innerRadius) / 2f

        var currentAngle = -90f

        slices.forEach { slice ->
            if (slice.bytes > 0) {
                val fraction = slice.bytes.toDouble() / totalBytes.toDouble()
                val sweep = (fraction * 360f).toFloat()
                val pct = (fraction * 100).roundToInt()

                drawArc(
                    color = slice.color,
                    startAngle = currentAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - ringCenterRadius, center.y - ringCenterRadius),
                    size = Size(ringCenterRadius * 2f, ringCenterRadius * 2f),
                    style = Stroke(width = strokeWidth)
                )

                if (pct >= 4) {
                    val midAngleRad = Math.toRadians((currentAngle + sweep / 2f).toDouble())
                    val textX = center.x + (ringCenterRadius * cos(midAngleRad)).toFloat()
                    val textY = center.y + (ringCenterRadius * sin(midAngleRad)).toFloat() + (textPaint.textSize / 3f)

                    drawContext.canvas.nativeCanvas.drawText(
                        "$pct %",
                        textX,
                        textY,
                        textPaint
                    )
                }

                currentAngle += sweep
            }
        }

        // 2. Draw Inner Circle (Phone Internal vs MicroSD)
        val centerCircleRadius = innerRadius - 3.dp.toPx()
        val internalPct = breakdown.internalPercent
        val extPct = breakdown.externalPercent

        if (extPct == 0) {
            // 100% on Phone Internal Memory
            drawCircle(
                color = internalColor,
                radius = centerCircleRadius,
                center = center
            )
            drawContext.canvas.nativeCanvas.drawText(
                "$internalPct%",
                center.x,
                center.y + (centerTextPaint.textSize / 3f),
                centerTextPaint
            )
        } else if (internalPct == 0) {
            // 100% on MicroSD Card
            drawCircle(
                color = extColor,
                radius = centerCircleRadius,
                center = center
            )
            drawContext.canvas.nativeCanvas.drawText(
                "$extPct%",
                center.x,
                center.y + (centerTextPaint.textSize / 3f),
                centerTextPaint
            )
        } else {
            // Split between Phone Internal and MicroSD
            val internalSweep = (internalPct / 100f) * 360f
            drawArc(
                color = internalColor,
                startAngle = -90f,
                sweepAngle = internalSweep,
                useCenter = true,
                topLeft = Offset(center.x - centerCircleRadius, center.y - centerCircleRadius),
                size = Size(centerCircleRadius * 2f, centerCircleRadius * 2f)
            )
            drawArc(
                color = extColor,
                startAngle = -90f + internalSweep,
                sweepAngle = 360f - internalSweep,
                useCenter = true,
                topLeft = Offset(center.x - centerCircleRadius, center.y - centerCircleRadius),
                size = Size(centerCircleRadius * 2f, centerCircleRadius * 2f)
            )

            // Dotted divider vertical line between sectors
            drawContext.canvas.nativeCanvas.drawLine(
                center.x,
                center.y - centerCircleRadius * 0.65f,
                center.x,
                center.y + centerCircleRadius * 0.65f,
                dashPaint
            )

            val textY = center.y + (centerTextPaint.textSize / 3f)
            val leftX = center.x - centerCircleRadius * 0.45f
            val rightX = center.x + centerCircleRadius * 0.45f

            drawContext.canvas.nativeCanvas.drawText("$internalPct%", leftX, textY, centerTextPaint)
            drawContext.canvas.nativeCanvas.drawText("$extPct%", rightX, textY, centerTextPaint)
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    labelColor: Color,
    subLabel: String? = null,
    sizeText: String,
    bytes: Long = 0L,
    isDisk: Boolean = false,
    vectorIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    customIconTint: Color = Color.Unspecified,
    statusBadge: String? = null
) {
    val hasData = bytes > 0L
    val contentAlpha = if (hasData) 1.0f else 0.42f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = if (hasData) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = labelColor.copy(alpha = contentAlpha)
                )
                if (statusBadge != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CyberEmerald.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, CyberEmerald.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = statusBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyberEmerald,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            if (!subLabel.isNullOrBlank()) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hasData) 0.65f else 0.35f)
                )
            }
        }
        Text(
            text = sizeText,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                fontWeight = if (hasData) FontWeight.Bold else FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            modifier = Modifier.padding(end = 10.dp)
        )
        if (isDisk) {
            FloppyDiskIcon(
                tint = customIconTint.copy(alpha = contentAlpha),
                modifier = Modifier.size(15.dp)
            )
        } else if (vectorIcon != null) {
            Icon(
                imageVector = vectorIcon,
                contentDescription = null,
                tint = customIconTint.copy(alpha = contentAlpha),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private data class ChartSlice(
    val name: String,
    val bytes: Long,
    val color: Color
)
