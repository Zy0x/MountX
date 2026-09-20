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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.LinkOff
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
    onClearMoveMessage: () -> Unit = {},
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
    onToggleMount: (() -> Unit)? = null,
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
                    0 -> StorageTabContent(
                        game = game,
                        mountPoints = currentMountPoints,
                        breakdown = breakdown,
                        availableDisks = availableDisks,
                        internalFreeBytes = internalFreeBytes,
                        isMoving = isMoving,
                        moveMessage = moveMessage,
                        sdBase = sdBase,
                        onMove = { dir, pts, disk, partition ->
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
                            onMoveMountPoints(dir, pts, disk, partition)
                        },
                        onRestoreToInternal = {
                            onMoveMountPoints(MoveDirection.TO_INTERNAL, currentMountPoints, null, null)
                        },
                        onUnmount = {
                            onToggleMount?.invoke()
                        },
                        onMountPointsChanged = { updated ->
                            currentMountPoints = updated
                            onUpdateMountPoints?.invoke(updated)
                        }
                    )
                    1 -> ManageTabContent()
                }
            }
        }

        // Overlay dialogs for moving progress & results
        if (isMoving) {
            Dialog(
                onDismissRequest = {},
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    MovingProgressStepContent(
                        isMoving = true,
                        moveMessage = moveMessage,
                        totalBytes = breakdown.totalBytes,
                        onCancel = {}
                    )
                }
            }
        } else if (moveMessage == "SUCCESS") {
            Dialog(
                onDismissRequest = onClearMoveMessage,
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    MoveSuccessStepContent(
                        movedPoints = currentMountPoints,
                        totalBytes = breakdown.totalBytes,
                        onDone = onClearMoveMessage,
                        onViewDetail = onClearMoveMessage
                    )
                }
            }
        } else if (!moveMessage.isNullOrBlank()) {
            AlertDialog(
                onDismissRequest = onClearMoveMessage,
                title = { Text(stringResource(R.string.common_error)) },
                text = { Text(moveMessage) },
                confirmButton = {
                    TextButton(onClick = onClearMoveMessage) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
            )
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
 * Data representation for the 6 unified game data categories.
 */
private data class UnifiedCategoryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val bytes: Long,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconTint: Color,
    val isMicroSd: Boolean,
    val isRisk: Boolean = false,
    val mountCategory: MountPointCategory
)

private fun buildTargetMountPoints(
    selectedCategoryIds: Set<String>,
    currentMountPoints: List<MountPointConfig>,
    game: GameEntry,
    sdBase: String
): List<MountPointConfig> {
    val result = mutableListOf<MountPointConfig>()
    val pkg = game.packageName

    for (catId in selectedCategoryIds) {
        val existing = currentMountPoints.firstOrNull { pt ->
            when (catId) {
                "data" -> pt.resolveCategory() == MountPointCategory.EXTERNAL_DATA
                "obb" -> pt.resolveCategory() == MountPointCategory.OBB_STORAGE
                "apk" -> pt.resolveCategory() == MountPointCategory.APP_PACKAGE
                "lib" -> pt.targetPath.contains("/lib", ignoreCase = true)
                "private" -> pt.resolveCategory() == MountPointCategory.PRIVATE_INTERNAL
                "cache" -> pt.resolveCategory() == MountPointCategory.CACHE_SHADERS
                else -> false
            }
        }

        if (existing != null) {
            val updatedSource = when (existing.resolveCategory()) {
                MountPointCategory.EXTERNAL_DATA -> "$sdBase/Android/data/$pkg"
                MountPointCategory.OBB_STORAGE -> "$sdBase/Android/obb/$pkg"
                MountPointCategory.APP_PACKAGE -> "$sdBase/app/$pkg"
                MountPointCategory.GAME_ASSETS -> "$sdBase/Android/data/$pkg/files"
                else -> existing.sourcePath
            }
            val updatedTarget = when (existing.resolveCategory()) {
                MountPointCategory.EXTERNAL_DATA -> if (existing.targetPath.startsWith("/sdcard")) "/data/media/0/Android/data/$pkg" else existing.targetPath
                MountPointCategory.OBB_STORAGE -> if (existing.targetPath.startsWith("/sdcard")) "/data/media/0/Android/obb/$pkg" else existing.targetPath
                else -> existing.targetPath
            }
            result.add(existing.copy(sourcePath = updatedSource, targetPath = updatedTarget, enabled = true))
        } else {
            val synthesized = when (catId) {
                "data" -> MountPointConfig(
                    id = "ext_data_$pkg",
                    category = MountPointCategory.EXTERNAL_DATA,
                    sourcePath = "$sdBase/Android/data/$pkg",
                    targetPath = "/data/media/0/Android/data/$pkg",
                    enabled = true
                )
                "obb" -> MountPointConfig(
                    id = "ext_obb_$pkg",
                    category = MountPointCategory.OBB_STORAGE,
                    sourcePath = "$sdBase/Android/obb/$pkg",
                    targetPath = "/data/media/0/Android/obb/$pkg",
                    enabled = true
                )
                "apk" -> MountPointConfig(
                    id = "apk_$pkg",
                    category = MountPointCategory.APP_PACKAGE,
                    sourcePath = "$sdBase/app/$pkg",
                    targetPath = "/data/app/$pkg",
                    enabled = true
                )
                "lib" -> MountPointConfig(
                    id = "lib_$pkg",
                    category = MountPointCategory.PRIVATE_INTERNAL,
                    sourcePath = "$sdBase/lib/$pkg",
                    targetPath = "/data/app/$pkg/lib",
                    enabled = true
                )
                "private" -> MountPointConfig(
                    id = "private_$pkg",
                    category = MountPointCategory.PRIVATE_INTERNAL,
                    sourcePath = "$sdBase/data/$pkg",
                    targetPath = "/data/data/$pkg",
                    enabled = true
                )
                "cache" -> MountPointConfig(
                    id = "cache_$pkg",
                    category = MountPointCategory.CACHE_SHADERS,
                    sourcePath = "$sdBase/Android/data/$pkg/cache",
                    targetPath = "/sdcard/Android/data/$pkg/cache",
                    enabled = true
                )
                else -> null
            }
            if (synthesized != null) result.add(synthesized)
        }
    }
    return result
}

@Composable
private fun StepBadge(
    stepNumber: Int,
    label: String,
    isActive: Boolean,
    isDone: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .background(
                    color = when {
                        isDone -> CyberEmerald
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(13.dp)
                )
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Text(
                    text = stepNumber.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Tab 0: Unified Storage Tab (Concentric Chart + Kategori Data Game + In-Place Selection + Sticky Actions)
 */
@Composable
private fun StorageTabContent(
    game: GameEntry,
    mountPoints: List<MountPointConfig>,
    breakdown: AppStorageBreakdown,
    availableDisks: List<SdCardDiskInfo>,
    internalFreeBytes: Long,
    isMoving: Boolean,
    moveMessage: String?,
    sdBase: String = "/data/sdext2",
    onMove: (MoveDirection, List<MountPointConfig>, SdCardDiskInfo?, PartitionInfo?) -> Unit,
    onRestoreToInternal: () -> Unit,
    onUnmount: () -> Unit,
    onMountPointsChanged: (List<MountPointConfig>) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedCategoryIds by remember { mutableStateOf(setOf("data", "obb")) }
    var showTargetModal by remember { mutableStateOf(false) }

    val isMounted = game.mountStatus == MountStatus.MOUNTED || breakdown.isExt1Mounted
    val hasExtDataOnSd = if (isMounted) {
        breakdown.ext2DataBytes > 0L || breakdown.isExt1Mounted
    } else {
        breakdown.ext2DataBytes > 0L && breakdown.ext1DataBytes == 0L
    }
    val hasExtObbOnSd = if (isMounted) {
        breakdown.ext2ObbBytes > 0L || breakdown.isExt1Mounted
    } else {
        breakdown.ext2ObbBytes > 0L && breakdown.ext1ObbBytes == 0L
    }

    val categories = remember(breakdown, isMounted, hasExtDataOnSd, hasExtObbOnSd) {
        val (dataBytes, dataSubtitle, isDataSd) = when {
            isMounted -> Triple(breakdown.ext2DataBytes, "Data utama game", true)
            breakdown.ext2DataBytes > 0L && breakdown.ext1DataBytes > 0L -> {
                Triple(
                    breakdown.ext1DataBytes,
                    "Data utama game • ${FormatUtils.formatExactBytes(breakdown.ext2DataBytes)} di MicroSD",
                    false
                )
            }
            breakdown.ext2DataBytes > 0L && breakdown.ext1DataBytes == 0L -> {
                Triple(breakdown.ext2DataBytes, "Data utama game", true)
            }
            else -> {
                Triple(
                    if (breakdown.ext1DataBytes > 0L) breakdown.ext1DataBytes else breakdown.ext1Bytes,
                    "Data utama game",
                    false
                )
            }
        }

        val (obbBytes, obbSubtitle, isObbSd) = when {
            isMounted -> Triple(breakdown.ext2ObbBytes, "File ekspansi game", true)
            breakdown.ext2ObbBytes > 0L && breakdown.ext1ObbBytes > 0L -> {
                Triple(
                    breakdown.ext1ObbBytes,
                    "File ekspansi game • ${FormatUtils.formatExactBytes(breakdown.ext2ObbBytes)} di MicroSD",
                    false
                )
            }
            breakdown.ext2ObbBytes > 0L && breakdown.ext1ObbBytes == 0L -> {
                Triple(breakdown.ext2ObbBytes, "File ekspansi game", true)
            }
            else -> {
                Triple(breakdown.ext1ObbBytes, "File ekspansi game", false)
            }
        }

        listOf(
            UnifiedCategoryItem(
                id = "apk",
                title = "APK",
                subtitle = "File instalasi game",
                bytes = breakdown.apkBytes,
                icon = Icons.Default.Android,
                iconTint = Color(0xFF00897B),
                isMicroSd = false,
                isRisk = true,
                mountCategory = MountPointCategory.APP_PACKAGE
            ),
            UnifiedCategoryItem(
                id = "lib",
                title = "Lib",
                subtitle = "Pustaka asli game",
                bytes = breakdown.libBytes,
                icon = Icons.Default.Build,
                iconTint = Color(0xFFFB8C00),
                isMicroSd = false,
                isRisk = true,
                mountCategory = MountPointCategory.PRIVATE_INTERNAL
            ),
            UnifiedCategoryItem(
                id = "private",
                title = "Data Privat",
                subtitle = "Data aplikasi (internal)",
                bytes = breakdown.dataBytes,
                icon = Icons.Default.Lock,
                iconTint = Color(0xFF43A047),
                isMicroSd = false,
                isRisk = false,
                mountCategory = MountPointCategory.PRIVATE_INTERNAL
            ),
            UnifiedCategoryItem(
                id = "cache",
                title = "Cache",
                subtitle = "Cache sementara",
                bytes = breakdown.cacheBytes,
                icon = Icons.Default.Cached,
                iconTint = Color(0xFFFFA000),
                isMicroSd = false,
                isRisk = false,
                mountCategory = MountPointCategory.CACHE_SHADERS
            ),
            UnifiedCategoryItem(
                id = "data",
                title = "Data Game",
                subtitle = dataSubtitle,
                bytes = dataBytes,
                icon = Icons.Default.SportsEsports,
                iconTint = Color(0xFF00ACC1),
                isMicroSd = isDataSd,
                isRisk = false,
                mountCategory = MountPointCategory.EXTERNAL_DATA
            ),
            UnifiedCategoryItem(
                id = "obb",
                title = "OBB",
                subtitle = obbSubtitle,
                bytes = obbBytes,
                icon = Icons.Default.SdStorage,
                iconTint = Color(0xFF1E88E5),
                isMicroSd = isObbSd,
                isRisk = false,
                mountCategory = MountPointCategory.OBB_STORAGE
            )
        )
    }

    val chartBreakdown = breakdown

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── CONCENTRIC DONUT CHART & 3-TIER LEGEND CARD ──
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
                    breakdown = chartBreakdown,
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
                            text = FormatUtils.formatLegendBytes(chartBreakdown.phoneInternalBytes),
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
                            text = FormatUtils.formatLegendBytes(chartBreakdown.microSdBytes),
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

                    // 3. Grand Total (Total)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.game_detail_legend_total),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = FormatUtils.formatLegendBytes(chartBreakdown.totalBytes),
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

        // ── SECTION HEADER: KATEGORI DATA GAME ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.game_detail_section_categories).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            if (isSelectionMode) {
                Text(
                    text = stringResource(R.string.manage_select_categories_selected, selectedCategoryIds.size),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ── UNIFIED CATEGORY CARDS LIST ──
        categories.forEach { item ->
            CategoryCardItem(
                item = item,
                isSelectionMode = isSelectionMode,
                isChecked = selectedCategoryIds.contains(item.id),
                onToggleCheck = {
                    selectedCategoryIds = if (selectedCategoryIds.contains(item.id)) {
                        selectedCategoryIds - item.id
                    } else {
                        selectedCategoryIds + item.id
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── STICKY BOTTOM ACTIONS ──
        if (!isSelectionMode) {
            // Normal Mode: Primary Manage Button + 2 Outlined Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isSelectionMode = true
                        selectedCategoryIds = setOf("data", "obb")
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = stringResource(R.string.manage_btn_manage_storage),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left: Kembalikan ke Memori Internal
                    val canRestore = isMounted || (breakdown.ext2Bytes > 0L)
                    OutlinedButton(
                        onClick = onRestoreToInternal,
                        enabled = canRestore,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (canRestore) Color(0xFFE53935).copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = if (canRestore) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.manage_btn_restore_internal),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (canRestore) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Right: Lepaskan Mount
                    OutlinedButton(
                        onClick = onUnmount,
                        enabled = isMounted,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isMounted) Color(0xFFFFA000).copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = if (isMounted) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.manage_btn_unmount),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (isMounted) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            // Selection Mode: Batal + Lanjutkan (N)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { isSelectionMode = false },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Button(
                    onClick = { showTargetModal = true },
                    enabled = selectedCategoryIds.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        text = stringResource(R.string.manage_btn_continue_format, selectedCategoryIds.size),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // ── SMART STORAGE & PARTITION MODAL BOTTOM SHEET ──
    if (showTargetModal) {
        SmartStoragePartitionBottomSheet(
            availableDisks = availableDisks,
            internalFreeBytes = internalFreeBytes,
            selectedCategoryIds = selectedCategoryIds,
            categories = categories,
            onDismiss = { showTargetModal = false },
            onConfirmMove = { dir, targetDisk, targetPartition ->
                showTargetModal = false
                isSelectionMode = false
                val effectiveSdBase = targetPartition?.mountPoint ?: targetDisk?.mountPath ?: sdBase
                val targetPoints = buildTargetMountPoints(selectedCategoryIds, mountPoints, game, effectiveSdBase)
                onMove(dir, targetPoints, targetDisk, targetPartition)
            }
        )
    }
}

@Composable
private fun CategoryCardItem(
    item: UnifiedCategoryItem,
    isSelectionMode: Boolean,
    isChecked: Boolean,
    onToggleCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isSelectionMode && isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectionMode, onClick = onToggleCheck)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggleCheck() },
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        color = item.iconTint.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = FormatUtils.formatExactBytes(item.bytes),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isSelectionMode && item.isRisk) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0x1AE53935),
                            border = BorderStroke(0.5.dp, Color(0xFFE53935).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = stringResource(R.string.manage_badge_performance_risk),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFE53935),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (item.isMicroSd) Color(0x1A3BA71A) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            0.5.dp,
                            if (item.isMicroSd) Color(0xFF3BA71A).copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = if (item.isMicroSd) "[MicroSD]" else "[Internal]",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (item.isMicroSd) Color(0xFF3BA71A) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartStoragePartitionBottomSheet(
    availableDisks: List<SdCardDiskInfo>,
    internalFreeBytes: Long,
    selectedCategoryIds: Set<String>,
    categories: List<UnifiedCategoryItem>,
    onDismiss: () -> Unit,
    onConfirmMove: (dir: MoveDirection, targetDisk: SdCardDiskInfo?, targetPartition: PartitionInfo?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var modalStep by remember { mutableStateOf(1) } // 1: Disk, 2: Partition, 3: Confirmation

    val externalDisks = remember(availableDisks) {
        availableDisks
    }

    var selectedDisk by remember(externalDisks) {
        mutableStateOf<SdCardDiskInfo?>(externalDisks.firstOrNull())
    }

    var selectedPartition by remember(selectedDisk) {
        mutableStateOf<PartitionInfo?>(selectedDisk?.partitions?.firstOrNull())
    }

    var isTargetInternal by remember { mutableStateOf(false) }

    val selectedItems = remember(selectedCategoryIds, categories) {
        categories.filter { selectedCategoryIds.contains(it.id) }
    }
    val allSelectedOnInternal = remember(selectedItems) {
        selectedItems.all { !it.isMicroSd }
    }
    val totalSelectedBytes = remember(selectedItems) {
        selectedItems.sumOf { it.bytes }
    }
    val hasRiskSelected = remember(selectedItems) {
        selectedItems.any { it.isRisk }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row with Title and Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.manage_target_modal_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 3-Step Stepper Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StepBadge(
                    stepNumber = 1,
                    label = stringResource(R.string.manage_step_pick_disk),
                    isActive = modalStep == 1,
                    isDone = modalStep > 1,
                    modifier = Modifier.weight(1f)
                )

                HorizontalDivider(
                    color = if (modalStep > 1) CyberEmerald else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 12.dp)
                )

                StepBadge(
                    stepNumber = 2,
                    label = stringResource(R.string.manage_step_pick_partition),
                    isActive = modalStep == 2,
                    isDone = modalStep > 2,
                    modifier = Modifier.weight(1f)
                )

                HorizontalDivider(
                    color = if (modalStep > 2) CyberEmerald else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 12.dp)
                )

                StepBadge(
                    stepNumber = 3,
                    label = stringResource(R.string.manage_step_confirm),
                    isActive = modalStep == 3,
                    isDone = false,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // Step Content
            when (modalStep) {
                1 -> {
                    // ── LANGKAH 1: PILIH DISK ──
                    Text(
                        text = stringResource(R.string.manage_pick_disk_subtitle),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Option 1: Memori Internal (Frozen / Disabled if all data originates from internal)
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (allSelectedOnInternal) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (!allSelectedOnInternal && isTargetInternal) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !allSelectedOnInternal) {
                                    isTargetInternal = true
                                    selectedDisk = null
                                    selectedPartition = null
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            color = if (allSelectedOnInternal) Color.Gray.copy(alpha = 0.15f) else Color(0xFFDF4006).copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Smartphone,
                                        contentDescription = null,
                                        tint = if (allSelectedOnInternal) Color.Gray else Color(0xFFDF4006),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.manage_internal_memory_label),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (allSelectedOnInternal) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (allSelectedOnInternal) stringResource(R.string.manage_current_location_disabled)
                                               else stringResource(R.string.manage_available_space, FormatUtils.formatExactBytes(internalFreeBytes)),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = if (allSelectedOnInternal) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                RadioButton(
                                    selected = !allSelectedOnInternal && isTargetInternal,
                                    onClick = if (!allSelectedOnInternal) {
                                        {
                                            isTargetInternal = true
                                            selectedDisk = null
                                            selectedPartition = null
                                        }
                                    } else null,
                                    enabled = !allSelectedOnInternal
                                )
                            }
                        }

                        // Option 2+: External Disks (MicroSD / USB OTG)
                        if (externalDisks.isNotEmpty()) {
                            externalDisks.forEach { disk ->
                                val isSelected = !isTargetInternal && selectedDisk == disk
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isTargetInternal = false
                                            selectedDisk = disk
                                            selectedPartition = disk.partitions.firstOrNull()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(
                                                    color = if (disk.diskType == DiskType.USB_OTG) Color(0xFF7E57C2).copy(alpha = 0.12f)
                                                            else Color(0xFF3BA71A).copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = if (disk.diskType == DiskType.USB_OTG) Icons.Default.Usb else Icons.Default.SdCard,
                                                contentDescription = null,
                                                tint = if (disk.diskType == DiskType.USB_OTG) Color(0xFF7E57C2) else Color(0xFF3BA71A),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(1.dp)
                                        ) {
                                            Text(
                                                text = disk.hardwareTitle,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${FormatUtils.formatLegendBytes(disk.totalFreeBytes)} / ${FormatUtils.formatLegendBytes(disk.totalSizeBytes)}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                isTargetInternal = false
                                                selectedDisk = disk
                                                selectedPartition = disk.partitions.firstOrNull()
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            val isSelected = !isTargetInternal
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isTargetInternal = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF3BA71A).copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SdCard,
                                            contentDescription = null,
                                            tint = Color(0xFF3BA71A),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.manage_location_badge_microsd),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.manage_microsd_recommended_label),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { isTargetInternal = false }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (isTargetInternal) {
                                modalStep = 3
                            } else {
                                modalStep = 2
                            }
                        },
                        enabled = isTargetInternal || selectedDisk != null || externalDisks.isEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (isTargetInternal) stringResource(R.string.manage_btn_continue_step)
                                   else stringResource(R.string.manage_btn_continue_to_partition),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                2 -> {
                    // ── LANGKAH 2: PILIH PARTISI ──
                    Text(
                        text = stringResource(
                            R.string.manage_pick_partition_subtitle,
                            selectedDisk?.hardwareTitle ?: stringResource(R.string.manage_location_badge_microsd)
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val partitions = selectedDisk?.partitions ?: emptyList()

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (partitions.isNotEmpty()) {
                            partitions.forEach { partition ->
                                val isSelected = selectedPartition == partition
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPartition = partition }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SdStorage,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(1.dp)
                                        ) {
                                            Text(
                                                text = "Partisi ${partition.partitionNumber} (${partition.fsType.ifBlank { "EXT4" }})",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${FormatUtils.formatLegendBytes(partition.freeBytes)} / ${FormatUtils.formatLegendBytes(partition.sizeBytes)}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedPartition = partition }
                                        )
                                    }
                                }
                            }
                        } else {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SdStorage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = "Partisi 1 (EXT4)",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = selectedDisk?.hardwareTitle ?: "MicroSD Storage",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    RadioButton(
                                        selected = true,
                                        onClick = null
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { modalStep = 1 },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.manage_btn_back_step),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        Button(
                            onClick = { modalStep = 3 },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.manage_btn_continue_step),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                3 -> {
                    // ── LANGKAH 3: KONFIRMASI ──
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.manage_summary_title),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            SummaryRow(
                                label = stringResource(R.string.manage_summary_selected_data),
                                value = "${selectedCategoryIds.size} kategori"
                            )
                            SummaryRow(
                                label = stringResource(R.string.manage_summary_total_size),
                                value = FormatUtils.formatExactBytes(totalSelectedBytes)
                            )
                            SummaryRow(
                                label = stringResource(R.string.manage_summary_from),
                                value = if (isTargetInternal) stringResource(R.string.manage_location_badge_microsd) else stringResource(R.string.manage_internal_memory_label)
                            )
                            SummaryRow(
                                label = stringResource(R.string.manage_summary_to),
                                value = if (isTargetInternal) {
                                    stringResource(R.string.manage_internal_memory_label)
                                } else {
                                    val diskName = selectedDisk?.hardwareTitle ?: stringResource(R.string.manage_location_badge_microsd)
                                    val partName = selectedPartition?.let { " (Partisi ${it.partitionNumber} - ${it.fsType.ifBlank { "EXT4" }})" } ?: ""
                                    "$diskName$partName"
                                }
                            )
                        }
                    }

                    // Warning Card if APK or Lib is included in selection
                    if (hasRiskSelected) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x1AFFA000)),
                            border = BorderStroke(1.dp, Color(0xFFFFA000).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFFA000),
                                    modifier = Modifier.size(20.dp)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = stringResource(R.string.manage_warning_title),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFFFFA000)
                                    )
                                    Text(
                                        text = stringResource(R.string.manage_warning_apk_lib_desc),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (isTargetInternal) modalStep = 1 else modalStep = 2 },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.manage_btn_back_step),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        Button(
                            onClick = {
                                onConfirmMove(
                                    if (isTargetInternal) MoveDirection.TO_INTERNAL else MoveDirection.TO_SD,
                                    selectedDisk,
                                    selectedPartition
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.manage_btn_start_move),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Tab 1: Minimalist Clean Placeholder (Layar 6)
 */
@Composable
private fun ManageTabContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 60.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.manage_tab_placeholder_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.manage_tab_placeholder_desc),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun MovingProgressStepContent(
    isMoving: Boolean,
    moveMessage: String?,
    totalBytes: Long,
    onCancel: () -> Unit
) {
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

    LaunchedEffect(isMoving, moveMessage) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.manage_moving_title),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.manage_moving_subtitle),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Circular progress donut
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(110.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isMoving) {
                    Text(
                        text = stringResource(R.string.manage_moving_copying),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5-stage vertical stepper
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            steps.forEachIndexed { idx, stepName ->
                val isDone = idx < currentStep || (!isMoving && moveMessage == "SUCCESS")
                val isActive = idx == currentStep && isMoving

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                color = when {
                                    isDone -> CyberEmerald
                                    isActive -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Text(
                                text = (idx + 1).toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = stepName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = if (isActive || isDone) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = when {
                            isDone -> CyberEmerald
                            isActive -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MoveSuccessStepContent(
    movedPoints: List<MountPointConfig>,
    totalBytes: Long,
    onDone: () -> Unit,
    onViewDetail: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(CyberEmerald, shape = RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }

        Text(
            text = stringResource(R.string.manage_success_title),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = stringResource(R.string.manage_success_subtitle, movedPoints.size),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onDone,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Text(
                text = stringResource(R.string.manage_btn_done),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
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
