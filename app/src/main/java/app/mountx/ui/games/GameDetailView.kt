package app.mountx.ui.games

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextAlign
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

sealed interface ManageSubScreen {
    object Overview : ManageSubScreen
    data class CategoryDetail(val point: MountPointConfig, val effectiveSize: Long) : ManageSubScreen
    object SelectData : ManageSubScreen  
    data class SelectStorage(val selectedPoints: List<MountPointConfig>) : ManageSubScreen
    data class MovingProgress(val selectedPoints: List<MountPointConfig>, val targetDisk: SdCardDiskInfo?, val targetPartition: PartitionInfo?) : ManageSubScreen
    data class MoveSuccess(val movedPoints: List<MountPointConfig>, val totalBytes: Long) : ManageSubScreen
}

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
    var currentSubScreen by remember { mutableStateOf<ManageSubScreen>(ManageSubScreen.Overview) }
    var showFolderOptionsSheet by remember { mutableStateOf<MountPointConfig?>(null) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    val activeMountPoints = mountPoints.filter { it.enabled }
    val isMounted = game.mountStatus == MountStatus.MOUNTED

    BackHandler(enabled = currentSubScreen !is ManageSubScreen.Overview) {
        currentSubScreen = when (currentSubScreen) {
            is ManageSubScreen.CategoryDetail -> ManageSubScreen.Overview
            is ManageSubScreen.SelectStorage -> ManageSubScreen.SelectData
            is ManageSubScreen.MovingProgress -> currentSubScreen // tidak bisa back saat moving
            is ManageSubScreen.MoveSuccess -> ManageSubScreen.Overview
            else -> ManageSubScreen.Overview
        }
    }

    LaunchedEffect(isMoving, moveMessage) {
        if (!isMoving && moveMessage == "SUCCESS" && currentSubScreen is ManageSubScreen.MovingProgress) {
            val movingScreen = currentSubScreen as ManageSubScreen.MovingProgress
            currentSubScreen = ManageSubScreen.MoveSuccess(
                movedPoints = movingScreen.selectedPoints.filter { it.enabled },
                totalBytes = movingScreen.selectedPoints.filter { it.enabled }.sumOf { 
                    getEffectiveMountPointSize(it, breakdown)
                }
            )
        }
    }

    when (val screen = currentSubScreen) {
        is ManageSubScreen.Overview -> {
            ManageOverviewContent(
                game = game,
                breakdown = breakdown,
                mountPoints = activeMountPoints,
                isMounted = isMounted,
                onManageStorage = { currentSubScreen = ManageSubScreen.SelectData },
                onRestoreToInternal = { onMove(MoveDirection.TO_INTERNAL, null, null) },
                onCategoryClick = { point, size ->
                    currentSubScreen = ManageSubScreen.CategoryDetail(point, size)
                }
            )
        }
        is ManageSubScreen.CategoryDetail -> {
            CategoryDetailContent(
                point = screen.point,
                effectiveSize = screen.effectiveSize,
                isMounted = isMounted,
                diskName = "MicroSD",
                onBack = { currentSubScreen = ManageSubScreen.Overview },
                onMoveToSD = { currentSubScreen = ManageSubScreen.SelectData },
                onRestoreToInternal = { onMove(MoveDirection.TO_INTERNAL, null, null) },
                onUnmount = { /* trigger unmount */ },
                onChangeLocation = { showFolderOptionsSheet = screen.point }
            )
        }
        is ManageSubScreen.SelectData -> {
            SelectDataStepContent(
                mountPoints = activeMountPoints,
                breakdown = breakdown,
                onBack = { currentSubScreen = ManageSubScreen.Overview },
                onContinue = { selected ->
                    currentSubScreen = ManageSubScreen.SelectStorage(selected)
                }
            )
        }
        is ManageSubScreen.SelectStorage -> {
            SelectStorageStepContent(
                availableDisks = availableDisks,
                internalFreeBytes = internalFreeBytes,
                selectedPoints = screen.selectedPoints,
                breakdown = breakdown,
                onBack = { currentSubScreen = ManageSubScreen.SelectData },
                onStartMove = { disk, partition ->
                    onMove(MoveDirection.TO_SD, disk, partition)
                    currentSubScreen = ManageSubScreen.MovingProgress(screen.selectedPoints, disk, partition)
                }
            )
        }
        is ManageSubScreen.MovingProgress -> {
            MovingProgressStepContent(
                isMoving = isMoving,
                moveMessage = moveMessage,
                totalBytes = screen.selectedPoints.sumOf { getEffectiveMountPointSize(it, breakdown) },
                onCancel = { showCancelConfirm = true }
            )
        }
        is ManageSubScreen.MoveSuccess -> {
            MoveSuccessStepContent(
                movedPoints = screen.movedPoints,
                totalBytes = screen.totalBytes,
                onDone = { currentSubScreen = ManageSubScreen.Overview },
                onViewDetail = { currentSubScreen = ManageSubScreen.Overview }
            )
        }
    }

    showFolderOptionsSheet?.let { point ->
        FolderOptionsBottomSheet(
            point = point,
            effectiveSize = getEffectiveMountPointSize(point, breakdown),
            isMounted = isMounted,
            diskName = "MicroSD",
            onDismiss = { showFolderOptionsSheet = null },
            onMoveToSD = {
                showFolderOptionsSheet = null
                currentSubScreen = ManageSubScreen.SelectStorage(listOf(point))
            },
            onRestoreToInternal = {
                showFolderOptionsSheet = null
                onMove(MoveDirection.TO_INTERNAL, null, null)
            },
            onViewInFileManager = {
                showFolderOptionsSheet = null
            },
            onDelete = {
                showFolderOptionsSheet = null
                onDelete()
            }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(stringResource(R.string.manage_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.manage_cancel_confirm_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    currentSubScreen = ManageSubScreen.Overview
                }) {
                    Text(stringResource(R.string.manage_cancel_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text(stringResource(R.string.common_back))
                }
            }
        )
    }
}

@Composable
private fun StorageDonutChart(
    sdBytes: Long,
    internalBytes: Long,
    modifier: Modifier = Modifier
) {
    val total = (sdBytes + internalBytes).coerceAtLeast(1L)
    val sdSweep = ((sdBytes.toFloat() / total.toFloat()) * 360f).coerceIn(0f, 360f)
    val internalSweep = (360f - sdSweep).coerceIn(0f, 360f)
    val sdPercent = ((sdBytes.toDouble() / total.toDouble()) * 100).toInt()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val strokeWidth = 12.dp.toPx()
            val canvasSize = size.minDimension
            val radius = (canvasSize - strokeWidth) / 2
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - canvasSize + strokeWidth) / 2, (size.height - canvasSize + strokeWidth) / 2)
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)

            // Internal background arc
            drawArc(
                color = Color(0xFF232D42),
                startAngle = -90f + sdSweep,
                sweepAngle = internalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )

            // MicroSD arc (green CyberEmerald)
            if (sdBytes > 0L) {
                drawArc(
                    color = CyberEmerald,
                    startAngle = -90f,
                    sweepAngle = sdSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (sdBytes > 0L) "$sdPercent%" else "0%",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (sdBytes > 0L) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "SD",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManageOverviewContent(
    game: GameEntry,
    breakdown: AppStorageBreakdown?,
    mountPoints: List<MountPointConfig>,
    isMounted: Boolean,
    onManageStorage: () -> Unit,
    onRestoreToInternal: () -> Unit,
    onCategoryClick: (MountPointConfig, Long) -> Unit
) {
    // Compute SD-saved bytes accurately (from breakdown.microSdBytes or active mounted points)
    val mountedSizeFromPoints = mountPoints.filter { isMounted && it.enabled }.sumOf { getEffectiveMountPointSize(it, breakdown) }
    val sdBytes = if (breakdown?.microSdBytes != null && breakdown.microSdBytes > 0L) breakdown.microSdBytes else mountedSizeFromPoints
    val internalBytes = (breakdown?.phoneInternalBytes ?: 0L).let { if (it > 0L) it else breakdown?.internalBytes ?: 0L }
    val totalBytes = (breakdown?.totalBytes ?: 0L).let { if (it > 0L) it else (sdBytes + internalBytes) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // ── Section A: Status Banner ─────────────────────────────────────────
            if (isMounted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberEmerald.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberEmerald, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.manage_overview_status_hybrid_title), fontWeight = FontWeight.Bold, color = CyberEmerald, fontSize = 15.sp)
                            val sizeStr = FormatUtils.formatBytes(sdBytes)
                            Text(stringResource(R.string.manage_overview_status_hybrid_desc, sizeStr), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.manage_overview_status_internal_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(stringResource(R.string.manage_overview_status_internal_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section B: Donut Chart + Legenda ─────────────────────────────────
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StorageDonutChart(
                        sdBytes = sdBytes,
                        internalBytes = internalBytes,
                        modifier = Modifier.size(105.dp)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(FormatUtils.formatBytes(totalBytes), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Total Data App", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(CyberEmerald, shape = RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(stringResource(R.string.common_external), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text(FormatUtils.formatBytes(sdBytes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF232D42), shape = RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(stringResource(R.string.common_internal), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text(FormatUtils.formatBytes(internalBytes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (isMounted && sdBytes > 0L) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.manage_overview_saving, FormatUtils.formatBytes(sdBytes)),
                                fontSize = 11.sp, color = CyberEmerald, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section C: Daftar Kategori Data Game ─────────────────────────────
            Text(stringResource(R.string.manage_overview_categories_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // All categories (from mount points + static non-movable)
            data class CategoryRow(val point: MountPointConfig?, val name: String, val icon: @Composable () -> Unit, val size: Long, val isSd: Boolean, val isClickable: Boolean)

            val sdColor = CyberEmerald
            val internalColor = MaterialTheme.colorScheme.onSurfaceVariant

            val categoryRows: List<CategoryRow> = buildList {
                // From mount points
                mountPoints.forEach { point ->
                    val cat = point.resolveCategory()
                    val size = getEffectiveMountPointSize(point, breakdown)
                    val displayName = when (cat) {
                        MountPointCategory.EXTERNAL_DATA -> stringResource(R.string.manage_cat_data_game)
                        MountPointCategory.OBB_STORAGE -> stringResource(R.string.manage_cat_obb)
                        MountPointCategory.MEDIA_DOWNLOADS -> stringResource(R.string.manage_cat_other_files)
                        MountPointCategory.APP_PACKAGE -> stringResource(R.string.manage_cat_package_app)
                        MountPointCategory.PRIVATE_INTERNAL -> stringResource(R.string.manage_cat_native_lib)
                        else -> point.id
                    }
                    val icon: @Composable () -> Unit = when (cat) {
                        MountPointCategory.EXTERNAL_DATA -> { { Icon(Icons.Default.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) } }
                        MountPointCategory.OBB_STORAGE -> { { Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(24.dp)) } }
                        MountPointCategory.MEDIA_DOWNLOADS -> { { Icon(Icons.Default.PermMedia, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(24.dp)) } }
                        MountPointCategory.APP_PACKAGE -> { { Icon(Icons.Default.Android, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(24.dp)) } }
                        MountPointCategory.PRIVATE_INTERNAL -> { { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) } }
                        else -> { { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) } }
                    }
                    add(CategoryRow(point, displayName, icon, size, isMounted && point.enabled, true))
                }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                categoryRows.forEachIndexed { idx, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (row.isClickable && row.point != null) Modifier.clickable { onCategoryClick(row.point, row.size) } else Modifier)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.icon()
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(FormatUtils.formatBytes(row.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Location badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (row.isSd) CyberEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = if (row.isSd) stringResource(R.string.manage_location_badge_microsd) else stringResource(R.string.manage_location_badge_internal),
                                color = if (row.isSd) sdColor else internalColor,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (row.isClickable) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (idx < categoryRows.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
        }

        // ── Bottom Action Bar ─────────────────────────────────────────────────
        Surface(shadowElevation = 8.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isMounted) {
                    OutlinedButton(
                        onClick = onRestoreToInternal,
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.manage_btn_restore_internal), fontSize = 11.sp, textAlign = TextAlign.Center, color = NeonCrimson)
                    }
                } else {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.manage_btn_restore_internal), fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
                Button(
                    onClick = onManageStorage,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.SdCard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.manage_btn_manage_storage), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun CategoryDetailContent(
    point: MountPointConfig,
    effectiveSize: Long,
    isMounted: Boolean,
    diskName: String,
    onBack: () -> Unit,
    onMoveToSD: () -> Unit,
    onRestoreToInternal: () -> Unit,
    onUnmount: () -> Unit,
    onChangeLocation: () -> Unit = {}
) {
    val cat = point.resolveCategory()

    // Category display name
    val catName = when (cat) {
        MountPointCategory.EXTERNAL_DATA -> stringResource(R.string.manage_cat_data_game)
        MountPointCategory.OBB_STORAGE -> stringResource(R.string.manage_cat_obb)
        MountPointCategory.MEDIA_DOWNLOADS -> stringResource(R.string.manage_cat_other_files)
        MountPointCategory.APP_PACKAGE -> stringResource(R.string.manage_cat_package_app)
        MountPointCategory.PRIVATE_INTERNAL -> stringResource(R.string.manage_cat_native_lib)
        else -> point.id
    }

    // Recommendation badge
    val (recText, recColor) = when (cat) {
        MountPointCategory.EXTERNAL_DATA, MountPointCategory.OBB_STORAGE ->
            stringResource(R.string.manage_badge_recommended) to CyberEmerald
        MountPointCategory.APP_PACKAGE ->
            stringResource(R.string.manage_badge_not_recommended) to NeonCrimson
        MountPointCategory.PRIVATE_INTERNAL ->
            stringResource(R.string.manage_badge_optional_not_recommended) to Color(0xFFFFB300)
        else ->
            stringResource(R.string.manage_badge_optional) to Color(0xFFFFB300)
    }

    // Category description
    val catDesc = when (cat) {
        MountPointCategory.EXTERNAL_DATA -> stringResource(R.string.manage_cat_data_game_desc)
        MountPointCategory.OBB_STORAGE -> stringResource(R.string.manage_cat_obb_desc)
        MountPointCategory.MEDIA_DOWNLOADS -> stringResource(R.string.manage_cat_other_files_desc)
        MountPointCategory.APP_PACKAGE -> stringResource(R.string.manage_cat_package_app_desc)
        MountPointCategory.PRIVATE_INTERNAL -> stringResource(R.string.manage_cat_native_lib_desc)
        else -> ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(R.string.manage_category_detail_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            // Size + Category + Badge
            Text(FormatUtils.formatBytes(effectiveSize), fontWeight = FontWeight.Bold, fontSize = 36.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(catName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))

            // Recommendation badge
            Surface(shape = RoundedCornerShape(6.dp), color = recColor.copy(alpha = 0.15f)) {
                Text(recText, color = recColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
            if (catDesc.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(catDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info card with rows
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Row: Lokasi saat ini
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.manage_current_location), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (isMounted) "${stringResource(R.string.common_external)} ($diskName)" else stringResource(R.string.common_internal),
                                fontSize = 14.sp, fontWeight = FontWeight.Medium
                            )
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = if (isMounted) CyberEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)) {
                            Text(
                                if (isMounted) stringResource(R.string.manage_location_badge_microsd) else stringResource(R.string.manage_location_badge_internal),
                                color = if (isMounted) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onChangeLocation, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(stringResource(R.string.manage_detail_btn_change), fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Row: Direktori
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.manage_source_directory), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(point.getCleanRelativePath(), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Row: Status
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isMounted) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isMounted) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (isMounted) stringResource(R.string.manage_status_mounted_ok) else stringResource(R.string.manage_status_on_internal),
                            fontSize = 14.sp,
                            color = if (isMounted) CyberEmerald else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Safe-to-move info banner (only for safe categories)
            if (cat != MountPointCategory.APP_PACKAGE && cat != MountPointCategory.PRIVATE_INTERNAL) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.manage_status_safe_to_move), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Surface(shadowElevation = 8.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isMounted) {
                    OutlinedButton(
                        onClick = onRestoreToInternal,
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.6f))
                    ) {
                        Text(stringResource(R.string.manage_btn_restore_internal), fontSize = 11.sp, color = NeonCrimson, textAlign = TextAlign.Center)
                    }
                    OutlinedButton(
                        onClick = onUnmount,
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.6f))
                    ) {
                        Text(stringResource(R.string.manage_btn_unmount), fontSize = 11.sp, color = Color(0xFFFFB300), textAlign = TextAlign.Center)
                    }
                } else {
                    Button(onClick = onMoveToSD, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.manage_btn_move_to_sd))
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectDataStepContent(mountPoints: List<MountPointConfig>, breakdown: AppStorageBreakdown?, onBack: () -> Unit, onContinue: (List<MountPointConfig>) -> Unit) {
    var selected by remember {
        mutableStateOf(
            mountPoints.filter {
                val cat = it.resolveCategory()
                cat == MountPointCategory.EXTERNAL_DATA || cat == MountPointCategory.OBB_STORAGE
            }.toSet()
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Column {
                Text(stringResource(R.string.manage_select_data_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.manage_select_data_subtitle), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Category list in a card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                mountPoints.forEachIndexed { idx, point ->
                    val cat = point.resolveCategory()
                    val size = getEffectiveMountPointSize(point, breakdown)
                    val isChecked = selected.contains(point)

                    val displayName = when (cat) {
                        MountPointCategory.EXTERNAL_DATA -> stringResource(R.string.manage_cat_data_game)
                        MountPointCategory.OBB_STORAGE -> stringResource(R.string.manage_cat_obb)
                        MountPointCategory.MEDIA_DOWNLOADS -> stringResource(R.string.manage_cat_other_files)
                        MountPointCategory.APP_PACKAGE -> stringResource(R.string.manage_cat_package_app)
                        MountPointCategory.PRIVATE_INTERNAL -> stringResource(R.string.manage_cat_native_lib)
                        else -> point.id
                    }

                    val (recText, recColor) = when (cat) {
                        MountPointCategory.EXTERNAL_DATA, MountPointCategory.OBB_STORAGE ->
                            stringResource(R.string.manage_badge_recommended) to CyberEmerald
                        MountPointCategory.APP_PACKAGE ->
                            stringResource(R.string.manage_badge_not_recommended) to NeonCrimson
                        MountPointCategory.PRIVATE_INTERNAL ->
                            stringResource(R.string.manage_badge_optional_not_recommended) to Color(0xFFFFB300)
                        else ->
                            stringResource(R.string.manage_badge_optional) to Color(0xFFFFB300)
                    }

                    val iconComposable: @Composable () -> Unit = when (cat) {
                        MountPointCategory.EXTERNAL_DATA -> { { Icon(Icons.Default.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) } }
                        MountPointCategory.OBB_STORAGE -> { { Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(24.dp)) } }
                        MountPointCategory.MEDIA_DOWNLOADS -> { { Icon(Icons.Default.PermMedia, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(24.dp)) } }
                        MountPointCategory.APP_PACKAGE -> { { Icon(Icons.Default.Android, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(24.dp)) } }
                        MountPointCategory.PRIVATE_INTERNAL -> { { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) } }
                        else -> { { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) } }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (isChecked) selected - point else selected + point
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = { selected = if (isChecked) selected - point else selected + point })
                        Spacer(modifier = Modifier.width(12.dp))
                        iconComposable()
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = recColor.copy(alpha = 0.12f)) {
                                Text(recText, fontSize = 9.sp, color = recColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(FormatUtils.formatBytes(size), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    }

                    if (idx < mountPoints.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
        }

        Surface(shadowElevation = 8.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.manage_select_total_label), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(FormatUtils.formatBytes(selected.sumOf { getEffectiveMountPointSize(it, breakdown) }), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                        Text(stringResource(R.string.manage_select_categories_selected, selected.size), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onContinue(selected.toList()) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = selected.isNotEmpty()
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.manage_btn_continue))
                }
            }
        }
    }
}

@Composable
private fun MovingProgressStepContent(isMoving: Boolean, moveMessage: String?, totalBytes: Long, onCancel: () -> Unit) {
    // Simulated stage progression based on isMoving state
    var currentStep by remember { mutableStateOf(0) }
    val progress by animateFloatAsState(
        targetValue = when {
            !isMoving && moveMessage == "SUCCESS" -> 1.0f
            isMoving -> 0.68f
            else -> 0.0f
        },
        label = "moveProgress",
        animationSpec = androidx.compose.animation.core.tween(800)
    )

    LaunchedEffect(isMoving) {
        if (isMoving) {
            currentStep = 0
            kotlinx.coroutines.delay(300)
            currentStep = 1
        } else if (moveMessage == "SUCCESS") {
            currentStep = 4
        }
    }

    val steps = listOf(
        stringResource(R.string.manage_step_preparing),
        stringResource(R.string.manage_step_copying),
        stringResource(R.string.manage_step_verifying),
        stringResource(R.string.manage_step_mounting),
        stringResource(R.string.manage_step_finishing)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.manage_moving_title), fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(stringResource(R.string.manage_moving_subtitle), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(32.dp))

            // Circular progress donut
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 14.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    if (isMoving) {
                        Text(stringResource(R.string.manage_moving_copying), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 5-stage vertical stepper
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                steps.forEachIndexed { idx, stepName ->
                    val isDone = idx < currentStep || (!isMoving && moveMessage == "SUCCESS")
                    val isActive = idx == currentStep && isMoving
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Step indicator column
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
                            // Circle indicator
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        when {
                                            isDone -> CyberEmerald
                                            isActive -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .border(
                                        1.5.dp,
                                        when {
                                            isDone -> CyberEmerald
                                            isActive -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDone) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                } else if (isActive) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                }
                            }
                            // Connector line (except last)
                            if (idx < steps.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(20.dp)
                                        .background(
                                            if (isDone) CyberEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            stepName,
                            fontSize = 14.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isDone -> CyberEmerald
                                isActive -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(bottom = if (idx < steps.lastIndex) 20.dp else 0.dp)
                        )
                    }
                }
            }
        }

        // Cancel button at bottom
        Surface(shadowElevation = 8.dp) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
                enabled = isMoving
            ) {
                Text(stringResource(R.string.manage_btn_cancel_move))
            }
        }
    }
}

@Composable
private fun SelectStorageStepContent(availableDisks: List<SdCardDiskInfo>, internalFreeBytes: Long, selectedPoints: List<MountPointConfig>, breakdown: AppStorageBreakdown?, onBack: () -> Unit, onStartMove: (SdCardDiskInfo?, PartitionInfo?) -> Unit) {
    var selectedDisk by remember { mutableStateOf(availableDisks.firstOrNull()) }
    var selectedPartition by remember { mutableStateOf(selectedDisk?.partitions?.firstOrNull()) }
    var advancedExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Column {
                Text(stringResource(R.string.manage_select_storage_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.manage_select_storage_subtitle), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            availableDisks.forEach { disk ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedDisk = disk
                        selectedPartition = disk.partitions.firstOrNull()
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedDisk == disk, onClick = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.manage_microsd_recommended_label), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("${disk.vendorName} • ${FormatUtils.formatBytes(disk.totalSizeBytes)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    selectedDisk = null
                    selectedPartition = null
                }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selectedDisk == null, onClick = null)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.manage_internal_memory_label), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("${FormatUtils.formatBytes(internalFreeBytes)} tersedia", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.manage_storage_warning), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        Surface(shadowElevation = 8.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(onClick = { onStartMove(selectedDisk, selectedPartition) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(R.string.manage_btn_start_move))
                }
            }
        }
    }
}



@Composable
private fun MoveSuccessStepContent(movedPoints: List<MountPointConfig>, totalBytes: Long, onDone: () -> Unit, onViewDetail: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(80.dp).background(CyberEmerald, shape = RoundedCornerShape(40.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.manage_success_title), fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(stringResource(R.string.manage_success_subtitle, movedPoints.size), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        movedPoints.forEach { point ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text(point.id, modifier = Modifier.weight(1f))
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberEmerald, modifier = Modifier.size(16.dp))
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.manage_btn_done))
        }
        TextButton(onClick = onViewDetail) {
            Text(stringResource(R.string.manage_btn_view_detail))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderOptionsBottomSheet(
    point: MountPointConfig,
    effectiveSize: Long,
    isMounted: Boolean,
    diskName: String,
    onDismiss: () -> Unit,
    onMoveToSD: () -> Unit,
    onRestoreToInternal: () -> Unit,
    onViewInFileManager: () -> Unit,
    onDelete: () -> Unit
) {
    val cat = point.resolveCategory()
    val catName = when (cat) {
        MountPointCategory.EXTERNAL_DATA -> stringResource(R.string.manage_cat_data_game)
        MountPointCategory.OBB_STORAGE -> stringResource(R.string.manage_cat_obb)
        MountPointCategory.MEDIA_DOWNLOADS -> stringResource(R.string.manage_cat_other_files)
        MountPointCategory.APP_PACKAGE -> stringResource(R.string.manage_cat_package_app)
        MountPointCategory.PRIVATE_INTERNAL -> stringResource(R.string.manage_cat_native_lib)
        else -> point.id
    }
    val catDesc = when (cat) {
        MountPointCategory.EXTERNAL_DATA -> stringResource(R.string.manage_cat_data_game_desc)
        MountPointCategory.OBB_STORAGE -> stringResource(R.string.manage_cat_obb_desc)
        MountPointCategory.MEDIA_DOWNLOADS -> stringResource(R.string.manage_cat_other_files_desc)
        MountPointCategory.APP_PACKAGE -> stringResource(R.string.manage_cat_package_app_desc)
        MountPointCategory.PRIVATE_INTERNAL -> stringResource(R.string.manage_cat_native_lib_desc)
        else -> ""
    }
    val (recText, recColor) = when (cat) {
        MountPointCategory.EXTERNAL_DATA, MountPointCategory.OBB_STORAGE ->
            stringResource(R.string.manage_badge_recommended) to CyberEmerald
        MountPointCategory.APP_PACKAGE ->
            stringResource(R.string.manage_badge_not_recommended) to NeonCrimson
        MountPointCategory.PRIVATE_INTERNAL ->
            stringResource(R.string.manage_badge_optional_not_recommended) to Color(0xFFFFB300)
        else ->
            stringResource(R.string.manage_badge_optional) to Color(0xFFFFB300)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.manage_folder_detail_title),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$catName • ${FormatUtils.formatBytes(effectiveSize)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (catDesc.isNotBlank()) {
                        Row {
                            Text("Deskripsi: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(catDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row {
                        Text("${stringResource(R.string.manage_current_location)}: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (isMounted) "${stringResource(R.string.common_external)} ($diskName)" else stringResource(R.string.common_internal),
                            fontSize = 12.sp,
                            color = if (isMounted) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        Text("${stringResource(R.string.manage_source_directory)}: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(point.getCleanRelativePath(), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Row {
                        Text("${stringResource(R.string.manage_folder_recommendation)}: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(recText, fontSize = 12.sp, color = recColor, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            if (!isMounted) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.manage_folder_move_to_sd), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onMoveToSD()
                    }
                )
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.manage_folder_restore_internal), color = NeonCrimson, fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = NeonCrimson) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onRestoreToInternal()
                    }
                )
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.manage_folder_view_file_manager)) },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onViewInFileManager()
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.manage_folder_delete_warning), color = NeonCrimson.copy(alpha = 0.8f), fontSize = 13.sp) },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = NeonCrimson.copy(alpha = 0.8f)) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onDelete()
                }
            )
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
