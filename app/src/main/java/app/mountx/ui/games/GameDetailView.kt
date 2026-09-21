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
import androidx.compose.foundation.horizontalScroll
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
import android.content.Context
import app.mountx.R
import app.mountx.data.model.AppStorageBreakdown
import app.mountx.data.model.CategoryDeleteLocation
import app.mountx.data.model.ConflictStrategy
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
import app.mountx.ui.components.NeedMigrationDialog
import app.mountx.ui.theme.CyberEmerald
import app.mountx.ui.theme.NeonCrimson
import app.mountx.ui.theme.SunsetAmber
import app.mountx.util.FormatUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import app.mountx.root.RootShell
import app.mountx.root.MountManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GameDetailView(
    game: GameEntry,
    breakdown: AppStorageBreakdown? = null,
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
    onMoveMountPoints: (MoveDirection, List<MountPointConfig>, SdCardDiskInfo?, PartitionInfo?, ConflictStrategy) -> Unit = { _, _, _, _, _ -> },
    onSaveGame: ((GameEntry) -> Unit)? = null,
    onUpdateMountPoints: ((List<MountPointConfig>) -> Unit)? = null,
    onDelete: () -> Unit = {},
    onMount: (() -> Unit)? = null,
    onUnmount: (() -> Unit)? = null,
    onToggleMount: (() -> Unit)? = null,
    onDeleteCategoryData: (String, CategoryDeleteLocation, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    // Hierarchical Root BackHandlers: Step back from page 1 to 0 before closing
    BackHandler(enabled = pagerState.currentPage != 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }
    BackHandler(enabled = pagerState.currentPage == 0, onBack = onDismiss)

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
                        onMove = { dir, pts, disk, partition, strategy ->
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
                            onMoveMountPoints(dir, pts, disk, partition, strategy)
                        },
                        onMount = {
                            onMount?.invoke() ?: onToggleMount?.invoke()
                        },
                        onUnmount = {
                            onUnmount?.invoke() ?: onToggleMount?.invoke()
                        },
                        onMountPointsChanged = { updated ->
                            currentMountPoints = updated
                            onUpdateMountPoints?.invoke(updated)
                        },
                        packageInfo = packageInfo,
                        onDeleteCategoryData = onDeleteCategoryData
                    )
                    1 -> AppInfoTabContent(game = game, packageInfo = packageInfo)
                }
            }
        }

        // Overlay error dialog fallback (progress and success are handled by unified OperationProgressDialog)
        if (!isMoving && !moveMessage.isNullOrBlank() && moveMessage != "SUCCESS") {
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
        stringResource(R.string.game_detail_tab_info)
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
    val mountCategory: MountPointCategory,
    val internalPath: String = "",
    val internalBytes: Long = 0L,
    val sdPath: String = "",
    val sdBytes: Long = 0L,
    val isCategoryMounted: Boolean = false,
    val preserveMedia: Boolean = false,
    val userId: Int = 0
)

private fun resolveUserIdFromPath(path: String): Int {
    return when {
        path.contains("/emulated/999/") || path.contains("/user/999/") || path.contains("/media/999/") || path.contains("/users/999/") -> 999
        path.contains("/emulated/10/") || path.contains("/user/10/") || path.contains("/media/10/") || path.contains("/users/10/") -> 10
        else -> 0
    }
}

private fun resolveSdPath(sdBase: String, relativeMountXPath: String): String {
    val legacyRelative = relativeMountXPath.removePrefix("MountX/")
    val legacyFile = java.io.File("$sdBase/$legacyRelative")
    return if (legacyFile.exists()) {
        "$sdBase/$legacyRelative"
    } else {
        "$sdBase/$relativeMountXPath"
    }
}

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
                "media" -> pt.resolveCategory() == MountPointCategory.MEDIA_DOWNLOADS
                "apk" -> pt.resolveCategory() == MountPointCategory.APP_PACKAGE
                "lib" -> pt.targetPath.contains("/lib", ignoreCase = true)
                "private" -> pt.resolveCategory() == MountPointCategory.PRIVATE_INTERNAL
                "cache" -> pt.resolveCategory() == MountPointCategory.CACHE_SHADERS
                else -> pt.id == catId
            }
        }

        if (existing != null) {
            val updatedSource = when (existing.resolveCategory()) {
                MountPointCategory.EXTERNAL_DATA -> resolveSdPath(sdBase, "MountX/Android/data/$pkg")
                MountPointCategory.OBB_STORAGE -> resolveSdPath(sdBase, "MountX/Android/obb/$pkg")
                MountPointCategory.MEDIA_DOWNLOADS -> resolveSdPath(sdBase, "MountX/Android/media/$pkg")
                MountPointCategory.APP_PACKAGE -> "$sdBase/MountX/app/$pkg"
                MountPointCategory.GAME_ASSETS -> resolveSdPath(sdBase, "MountX/Android/data/$pkg/files")
                else -> existing.sourcePath
            }
            val updatedTarget = when (existing.resolveCategory()) {
                MountPointCategory.EXTERNAL_DATA -> if (existing.targetPath.startsWith("/sdcard")) "/data/media/0/Android/data/$pkg" else existing.targetPath
                MountPointCategory.OBB_STORAGE -> if (existing.targetPath.startsWith("/sdcard")) "/data/media/0/Android/obb/$pkg" else existing.targetPath
                MountPointCategory.MEDIA_DOWNLOADS -> if (existing.targetPath.startsWith("/sdcard")) "/data/media/0/Android/media/$pkg" else existing.targetPath
                else -> existing.targetPath
            }
            result.add(existing.copy(sourcePath = updatedSource, targetPath = updatedTarget, enabled = true))
        } else {
            val synthesized = when (catId) {
                "data" -> MountPointConfig(
                    id = "ext_data_$pkg",
                    category = MountPointCategory.EXTERNAL_DATA,
                    sourcePath = resolveSdPath(sdBase, "MountX/Android/data/$pkg"),
                    targetPath = "/data/media/0/Android/data/$pkg",
                    enabled = true
                )
                "obb" -> MountPointConfig(
                    id = "ext_obb_$pkg",
                    category = MountPointCategory.OBB_STORAGE,
                    sourcePath = resolveSdPath(sdBase, "MountX/Android/obb/$pkg"),
                    targetPath = "/data/media/0/Android/obb/$pkg",
                    enabled = true
                )
                "media" -> MountPointConfig(
                    id = "ext_media_$pkg",
                    category = MountPointCategory.MEDIA_DOWNLOADS,
                    sourcePath = resolveSdPath(sdBase, "MountX/Android/media/$pkg"),
                    targetPath = "/data/media/0/Android/media/$pkg",
                    enabled = true
                )
                "apk" -> MountPointConfig(
                    id = "apk_$pkg",
                    category = MountPointCategory.APP_PACKAGE,
                    sourcePath = "$sdBase/MountX/app/$pkg",
                    targetPath = "/data/app/$pkg",
                    enabled = true
                )
                "lib" -> MountPointConfig(
                    id = "lib_$pkg",
                    category = MountPointCategory.PRIVATE_INTERNAL,
                    sourcePath = "$sdBase/MountX/lib/$pkg",
                    targetPath = "/data/app/$pkg/lib",
                    enabled = true
                )
                "private" -> MountPointConfig(
                    id = "private_$pkg",
                    category = MountPointCategory.PRIVATE_INTERNAL,
                    sourcePath = "$sdBase/MountX/data/$pkg",
                    targetPath = "/data/data/$pkg",
                    enabled = true
                )
                "cache" -> MountPointConfig(
                    id = "cache_$pkg",
                    category = MountPointCategory.CACHE_SHADERS,
                    sourcePath = "$sdBase/MountX/Android/data/$pkg/cache",
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
    breakdown: AppStorageBreakdown?,
    availableDisks: List<SdCardDiskInfo>,
    internalFreeBytes: Long,
    isMoving: Boolean,
    moveMessage: String?,
    sdBase: String = "/data/sdext2",
    packageInfo: android.content.pm.PackageInfo? = null,
    onMove: (MoveDirection, List<MountPointConfig>, SdCardDiskInfo?, PartitionInfo?, ConflictStrategy) -> Unit,
    onMount: () -> Unit,
    onUnmount: () -> Unit,
    onMountPointsChanged: (List<MountPointConfig>) -> Unit,
    onDeleteCategoryData: (String, CategoryDeleteLocation, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val safeBreakdown = breakdown ?: AppStorageBreakdown()
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedCategoryIds by remember { mutableStateOf(setOf("data", "obb")) }
    var showTargetModal by remember { mutableStateOf(false) }
    var inspectingCategory by remember { mutableStateOf<UnifiedCategoryItem?>(null) }
    var categoryToDelete by remember { mutableStateOf<UnifiedCategoryItem?>(null) }
    var isDeletingCategory by remember { mutableStateOf(false) }
    var migrationConfirmData by remember { mutableStateOf<MigrationConfirmData?>(null) }
    var showUnmountConfirmDialog by remember { mutableStateOf(false) }
    var showNeedMigrationDialog by remember { mutableStateOf(false) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    // Hierarchical Step-by-Step Back Navigation inside Storage Tab
    BackHandler(enabled = showNeedMigrationDialog) {
        showNeedMigrationDialog = false
    }
    BackHandler(enabled = !showNeedMigrationDialog && showAddCustomDialog) {
        showAddCustomDialog = false
    }
    BackHandler(enabled = !showNeedMigrationDialog && !showAddCustomDialog && showUnmountConfirmDialog) {
        showUnmountConfirmDialog = false
    }
    BackHandler(enabled = !showNeedMigrationDialog && !showAddCustomDialog && !showUnmountConfirmDialog && migrationConfirmData != null) {
        migrationConfirmData = null
    }
    BackHandler(enabled = !showNeedMigrationDialog && !showAddCustomDialog && !showUnmountConfirmDialog && migrationConfirmData == null && categoryToDelete != null) {
        if (!isDeletingCategory) {
            categoryToDelete = null
        }
    }
    BackHandler(enabled = !showNeedMigrationDialog && !showAddCustomDialog && !showUnmountConfirmDialog && migrationConfirmData == null && categoryToDelete == null && inspectingCategory != null) {
        inspectingCategory = null
    }
    BackHandler(enabled = !showNeedMigrationDialog && !showAddCustomDialog && !showUnmountConfirmDialog && migrationConfirmData == null && categoryToDelete == null && inspectingCategory == null && showTargetModal) {
        showTargetModal = false
    }
    BackHandler(enabled = !showNeedMigrationDialog && !showAddCustomDialog && !showUnmountConfirmDialog && migrationConfirmData == null && categoryToDelete == null && inspectingCategory == null && !showTargetModal && isSelectionMode) {
        isSelectionMode = false
    }

    val isRealDataOnSd = safeBreakdown.ext2Bytes > 0L
    val isMounted = safeBreakdown.isExt1Mounted || (game.mountStatus == MountStatus.MOUNTED && isRealDataOnSd)

    val categories = remember(safeBreakdown, isMounted, mountPoints, packageInfo, sdBase) {
        val existingDataPoint = mountPoints.firstOrNull { it.resolveCategory() == MountPointCategory.EXTERNAL_DATA }
        val isDataCatMounted = safeBreakdown.isDataMounted || (isMounted && existingDataPoint?.enabled == true)
        val (dataBytes, dataSubtitle, isDataSd) = when {
            isDataCatMounted -> {
                val size = if (safeBreakdown.ext2DataBytes > 0L) safeBreakdown.ext2DataBytes else safeBreakdown.ext1DataBytes
                Triple(size, "Data utama game", true)
            }
            safeBreakdown.ext2DataBytes > 0L && safeBreakdown.ext1DataBytes > 64 * 1024L -> {
                Triple(
                    safeBreakdown.ext1DataBytes,
                    "Data utama game • ${FormatUtils.formatExactBytes(safeBreakdown.ext2DataBytes)} di MicroSD",
                    false
                )
            }
            safeBreakdown.ext2DataBytes > 0L && safeBreakdown.ext1DataBytes <= 4096L -> {
                Triple(safeBreakdown.ext2DataBytes, "Data utama game", true)
            }
            else -> {
                Triple(
                    if (safeBreakdown.ext1DataBytes > 0L) safeBreakdown.ext1DataBytes else safeBreakdown.ext1Bytes,
                    "Data utama game",
                    false
                )
            }
        }

        val existingObbPoint = mountPoints.firstOrNull { it.resolveCategory() == MountPointCategory.OBB_STORAGE }
        val isObbCatMounted = safeBreakdown.isObbMounted || (isMounted && existingObbPoint?.enabled == true)
        val (obbBytes, obbSubtitle, isObbSd) = when {
            isObbCatMounted -> {
                val size = if (safeBreakdown.ext2ObbBytes > 0L) safeBreakdown.ext2ObbBytes else safeBreakdown.ext1ObbBytes
                Triple(size, "File ekspansi game", true)
            }
            safeBreakdown.ext2ObbBytes > 0L && safeBreakdown.ext1ObbBytes > 64 * 1024L -> {
                Triple(
                    safeBreakdown.ext1ObbBytes,
                    "File ekspansi game • ${FormatUtils.formatExactBytes(safeBreakdown.ext2ObbBytes)} di MicroSD",
                    false
                )
            }
            safeBreakdown.ext2ObbBytes > 0L && safeBreakdown.ext1ObbBytes <= 4096L -> {
                Triple(safeBreakdown.ext2ObbBytes, "File ekspansi game", true)
            }
            else -> {
                Triple(safeBreakdown.ext1ObbBytes, "File ekspansi game", false)
            }
        }

        val pkg = game.packageName
        val apkSrc = packageInfo?.applicationInfo?.sourceDir ?: "/data/app"
        val apkInternalDir = if (apkSrc.endsWith(".apk")) java.io.File(apkSrc).parent ?: apkSrc else apkSrc
        val libSrc = packageInfo?.applicationInfo?.nativeLibraryDir ?: "/data/app/$pkg/lib"

        val existingMediaPoint = mountPoints.firstOrNull { it.resolveCategory() == MountPointCategory.MEDIA_DOWNLOADS }
        val hasMedia = safeBreakdown.ext1MediaBytes > 0L || safeBreakdown.ext2MediaBytes > 0L || existingMediaPoint != null
        val isMediaCatMounted = safeBreakdown.isMediaMounted || (isMounted && existingMediaPoint?.enabled == true)
        val (mediaBytes, mediaSubtitle, isMediaSd) = when {
            isMediaCatMounted -> {
                val size = if (safeBreakdown.ext2MediaBytes > 0L) safeBreakdown.ext2MediaBytes else safeBreakdown.ext1MediaBytes
                Triple(size, "Berkas media & unduhan", true)
            }
            safeBreakdown.ext2MediaBytes > 0L && safeBreakdown.ext1MediaBytes > 64 * 1024L -> {
                Triple(
                    safeBreakdown.ext1MediaBytes,
                    "Berkas media & unduhan • ${FormatUtils.formatExactBytes(safeBreakdown.ext2MediaBytes)} di MicroSD",
                    false
                )
            }
            safeBreakdown.ext2MediaBytes > 0L && safeBreakdown.ext1MediaBytes <= 4096L -> {
                Triple(safeBreakdown.ext2MediaBytes, "Berkas media & unduhan", true)
            }
            else -> {
                Triple(safeBreakdown.ext1MediaBytes, "Berkas media & unduhan", false)
            }
        }

        val mediaItem = if (hasMedia) {
            val mediaInternal = existingMediaPoint?.targetPath ?: "/data/media/0/Android/media/$pkg"
            val mediaSd = existingMediaPoint?.sourcePath ?: resolveSdPath(sdBase, "MountX/Android/media/$pkg")
            UnifiedCategoryItem(
                id = "media",
                title = "Media & Unduhan",
                subtitle = mediaSubtitle,
                bytes = mediaBytes,
                icon = Icons.Default.PermMedia,
                iconTint = Color(0xFF8E24AA),
                isMicroSd = isMediaSd,
                isRisk = false,
                mountCategory = MountPointCategory.MEDIA_DOWNLOADS,
                internalPath = mediaInternal,
                internalBytes = if (isMediaCatMounted) 0L else safeBreakdown.ext1MediaBytes,
                sdPath = mediaSd,
                sdBytes = if (isMediaCatMounted) {
                    if (safeBreakdown.ext2MediaBytes > 0L) safeBreakdown.ext2MediaBytes else safeBreakdown.ext1MediaBytes
                } else {
                    safeBreakdown.ext2MediaBytes
                },
                isCategoryMounted = isMediaCatMounted,
                preserveMedia = existingMediaPoint?.preserveMedia ?: true,
                userId = resolveUserIdFromPath(mediaInternal)
            )
        } else null

        val customItems = mountPoints.filter { it.resolveCategory() == MountPointCategory.CUSTOM }.map { pt ->
            val isCustomMounted = isMounted && pt.enabled
            val resolvedCustomBytes = if (pt.sizeBytes > 0L) pt.sizeBytes else safeBreakdown.customBytes
            UnifiedCategoryItem(
                id = pt.id,
                title = pt.label ?: "Kustom (${pt.targetPath.substringAfterLast('/').ifEmpty { pt.targetPath }})",
                subtitle = pt.targetPath,
                bytes = resolvedCustomBytes,
                icon = Icons.Default.Folder,
                iconTint = Color(0xFF00897B),
                isMicroSd = isCustomMounted,
                isRisk = false,
                mountCategory = MountPointCategory.CUSTOM,
                internalPath = pt.targetPath,
                internalBytes = if (isCustomMounted) 0L else resolvedCustomBytes,
                sdPath = pt.sourcePath,
                sdBytes = resolvedCustomBytes,
                isCategoryMounted = isCustomMounted,
                preserveMedia = pt.preserveMedia,
                userId = resolveUserIdFromPath(pt.targetPath)
            )
        }

        val dataInternal = existingDataPoint?.targetPath ?: "/data/media/0/Android/data/$pkg"
        val obbInternal = existingObbPoint?.targetPath ?: "/data/media/0/Android/obb/$pkg"

        listOfNotNull(
            UnifiedCategoryItem(
                id = "apk",
                title = "APK",
                subtitle = "File instalasi game",
                bytes = safeBreakdown.apkBytes,
                icon = Icons.Default.Android,
                iconTint = Color(0xFF00897B),
                isMicroSd = false,
                isRisk = true,
                mountCategory = MountPointCategory.APP_PACKAGE,
                internalPath = apkInternalDir,
                internalBytes = safeBreakdown.apkBytes,
                sdPath = "$sdBase/MountX/app/$pkg",
                sdBytes = 0L,
                isCategoryMounted = false,
                userId = resolveUserIdFromPath(apkInternalDir)
            ),
            UnifiedCategoryItem(
                id = "lib",
                title = "Lib",
                subtitle = "Pustaka asli game",
                bytes = safeBreakdown.libBytes,
                icon = Icons.Default.Build,
                iconTint = Color(0xFFFB8C00),
                isMicroSd = false,
                isRisk = true,
                mountCategory = MountPointCategory.PRIVATE_INTERNAL,
                internalPath = libSrc,
                internalBytes = safeBreakdown.libBytes,
                sdPath = "$sdBase/MountX/lib/$pkg",
                sdBytes = 0L,
                isCategoryMounted = false,
                userId = resolveUserIdFromPath(libSrc)
            ),
            UnifiedCategoryItem(
                id = "private",
                title = "Data Privat",
                subtitle = "Data aplikasi (internal)",
                bytes = safeBreakdown.dataBytes,
                icon = Icons.Default.Lock,
                iconTint = Color(0xFF43A047),
                isMicroSd = false,
                isRisk = false,
                mountCategory = MountPointCategory.PRIVATE_INTERNAL,
                internalPath = "/data/data/$pkg",
                internalBytes = safeBreakdown.dataBytes,
                sdPath = "$sdBase/MountX/data/$pkg",
                sdBytes = 0L,
                isCategoryMounted = false,
                userId = 0
            ),
            UnifiedCategoryItem(
                id = "cache",
                title = "Cache",
                subtitle = "Cache sementara",
                bytes = safeBreakdown.cacheBytes,
                icon = Icons.Default.Cached,
                iconTint = Color(0xFFFFA000),
                isMicroSd = false,
                isRisk = false,
                mountCategory = MountPointCategory.CACHE_SHADERS,
                internalPath = "/data/data/$pkg/cache",
                internalBytes = safeBreakdown.cacheBytes,
                sdPath = resolveSdPath(sdBase, "MountX/Android/data/$pkg/cache"),
                sdBytes = 0L,
                isCategoryMounted = false,
                userId = 0
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
                mountCategory = MountPointCategory.EXTERNAL_DATA,
                internalPath = dataInternal,
                internalBytes = if (isDataCatMounted) 0L else safeBreakdown.ext1DataBytes,
                sdPath = existingDataPoint?.sourcePath ?: resolveSdPath(sdBase, "MountX/Android/data/$pkg"),
                sdBytes = if (isDataCatMounted) {
                    if (safeBreakdown.ext2DataBytes > 0L) safeBreakdown.ext2DataBytes else safeBreakdown.ext1DataBytes
                } else {
                    safeBreakdown.ext2DataBytes
                },
                isCategoryMounted = isDataCatMounted,
                userId = resolveUserIdFromPath(dataInternal)
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
                mountCategory = MountPointCategory.OBB_STORAGE,
                internalPath = obbInternal,
                internalBytes = if (isObbCatMounted) 0L else safeBreakdown.ext1ObbBytes,
                sdPath = existingObbPoint?.sourcePath ?: resolveSdPath(sdBase, "MountX/Android/obb/$pkg"),
                sdBytes = if (isObbCatMounted) {
                    if (safeBreakdown.ext2ObbBytes > 0L) safeBreakdown.ext2ObbBytes else safeBreakdown.ext1ObbBytes
                } else {
                    safeBreakdown.ext2ObbBytes
                },
                isCategoryMounted = isObbCatMounted,
                userId = resolveUserIdFromPath(obbInternal)
            ),
            mediaItem
        ) + customItems
    }

    val chartBreakdown = safeBreakdown

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
            if (breakdown == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
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
                },
                onInspect = {
                    inspectingCategory = item
                }
            )
        }

        // [+ Tambah Direktori Kustom] Button
        OutlinedButton(
            onClick = { showAddCustomDialog = true },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.btn_add_custom_directory),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary
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
                    // Left: Kembalikan ke Memori Internal (Dynamic real size)
                    val restoreBytes = remember(safeBreakdown, mountPoints) {
                        val bytes = mountPoints.filter { it.enabled }.sumOf { pt ->
                            val cat = pt.resolveCategory()
                            when (cat) {
                                MountPointCategory.OBB_STORAGE -> safeBreakdown.ext2ObbBytes
                                MountPointCategory.EXTERNAL_DATA -> safeBreakdown.ext2DataBytes
                                MountPointCategory.MEDIA_DOWNLOADS -> safeBreakdown.ext2MediaBytes
                                else -> pt.sizeBytes
                            }
                        }
                        if (bytes > 0L) bytes else safeBreakdown.ext2Bytes
                    }
                    val canRestore = isMounted && (restoreBytes > 0L)
                    OutlinedButton(
                        onClick = {
                            val internalExistingBytes = if (isMounted) 0L else (safeBreakdown.ext1DataBytes + safeBreakdown.ext1ObbBytes)
                            migrationConfirmData = MigrationConfirmData(
                                direction = MoveDirection.TO_INTERNAL,
                                totalBytes = restoreBytes,
                                sourceName = "MicroSD",
                                destName = "Memori Internal",
                                destFreeBytes = internalFreeBytes,
                                destExistingBytes = internalExistingBytes,
                                targetDisk = null,
                                targetPartition = null,
                                pointsToMigrate = mountPoints.filter { it.enabled }.ifEmpty { mountPoints }
                            )
                        },
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
                            text = if (canRestore && restoreBytes > 0L) {
                                stringResource(R.string.manage_btn_restore_internal_with_size, FormatUtils.formatBytes(restoreBytes))
                            } else {
                                stringResource(R.string.manage_btn_restore_internal)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (canRestore) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Right: Alternating Mount / Unmount / Migrate button
                    val hasInternalData = safeBreakdown.ext1Bytes > 0L || game.dataSizeBytes > 0L
                    val isNeedMigration = game.mountStatus == MountStatus.NEED_MIGRATION || (!isMounted && !isRealDataOnSd && hasInternalData)
                    val actionBorderColor = when {
                        isNeedMigration -> SunsetAmber.copy(alpha = 0.8f)
                        isMounted -> SunsetAmber.copy(alpha = 0.7f)
                        else -> CyberEmerald.copy(alpha = 0.7f)
                    }
                    val actionContentColor = when {
                        isNeedMigration -> SunsetAmber
                        isMounted -> SunsetAmber
                        else -> CyberEmerald
                    }

                    OutlinedButton(
                        onClick = {
                            when {
                                isNeedMigration -> showNeedMigrationDialog = true
                                isMounted -> showUnmountConfirmDialog = true
                                isRealDataOnSd -> onMount()
                                else -> showNeedMigrationDialog = true
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, actionBorderColor),
                        colors = if (isNeedMigration) ButtonDefaults.outlinedButtonColors(
                            containerColor = SunsetAmber.copy(alpha = 0.12f)
                        ) else ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isNeedMigration -> Icons.Default.Warning
                                isMounted -> Icons.Default.LinkOff
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = null,
                            tint = actionContentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                isNeedMigration -> stringResource(R.string.btn_migrate_to_sd)
                                isMounted -> stringResource(R.string.manage_btn_unmount_game)
                                else -> stringResource(R.string.manage_btn_mount_game)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = actionContentColor,
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
            currentSdBase = mountPoints.firstOrNull { it.sourcePath.isNotBlank() }?.let { pt ->
                availableDisks.flatMap { it.partitions }.firstOrNull { part ->
                    part.mountPoint != null && pt.sourcePath.startsWith(part.mountPoint)
                }?.mountPoint
            } ?: sdBase,
            onDismiss = { showTargetModal = false },
            onConfirmMove = { dir, targetDisk, targetPartition ->
                showTargetModal = false
                val effectiveSdBase = targetPartition?.mountPoint ?: targetDisk?.mountPath ?: sdBase
                val targetPoints = buildTargetMountPoints(selectedCategoryIds, mountPoints, game, effectiveSdBase)
                val isToInternal = dir == MoveDirection.TO_INTERNAL
                val totalBytes = targetPoints.sumOf { pt ->
                    val cat = pt.resolveCategory()
                    when (cat) {
                        MountPointCategory.EXTERNAL_DATA -> if (isToInternal) safeBreakdown.ext2DataBytes else safeBreakdown.ext1DataBytes
                        MountPointCategory.OBB_STORAGE -> if (isToInternal) safeBreakdown.ext2ObbBytes else safeBreakdown.ext1ObbBytes
                        MountPointCategory.MEDIA_DOWNLOADS -> if (isToInternal) safeBreakdown.ext2MediaBytes else safeBreakdown.ext1MediaBytes
                        else -> pt.sizeBytes
                    }
                }.let { if (it > 0L) it else if (isToInternal) safeBreakdown.ext2Bytes else safeBreakdown.ext1Bytes }

                val freeSpace = if (isToInternal) internalFreeBytes else (targetPartition?.freeBytes ?: targetDisk?.totalFreeBytes ?: 0L)
                val targetExistingBytes = targetPoints.sumOf { pt ->
                    val cat = pt.resolveCategory()
                    when (cat) {
                        MountPointCategory.EXTERNAL_DATA -> if (isToInternal) {
                            if (isMounted) 0L else safeBreakdown.ext1DataBytes
                        } else safeBreakdown.ext2DataBytes
                        MountPointCategory.OBB_STORAGE -> if (isToInternal) {
                            if (isMounted) 0L else safeBreakdown.ext1ObbBytes
                        } else safeBreakdown.ext2ObbBytes
                        MountPointCategory.MEDIA_DOWNLOADS -> if (isToInternal) {
                            if (isMounted) 0L else safeBreakdown.ext1MediaBytes
                        } else safeBreakdown.ext2MediaBytes
                        else -> 0L
                    }
                }

                val extLocationName = targetPartition?.shortName ?: targetDisk?.displayName ?: "MicroSD"
                val sourceName = if (isToInternal) extLocationName else "Memori Internal"
                val destName = if (isToInternal) "Memori Internal" else extLocationName

                migrationConfirmData = MigrationConfirmData(
                    direction = dir,
                    totalBytes = totalBytes,
                    sourceName = sourceName,
                    destName = destName,
                    destFreeBytes = freeSpace,
                    destExistingBytes = targetExistingBytes,
                    targetDisk = targetDisk,
                    targetPartition = targetPartition,
                    pointsToMigrate = targetPoints
                )
            }
        )
    }

    // ── MIGRATION CONFIRMATION DIALOG ──
    if (migrationConfirmData != null) {
        val confData = migrationConfirmData!!
        MigrationConfirmDialog(
            data = confData,
            onDismiss = { migrationConfirmData = null },
            onConfirm = { strategy ->
                migrationConfirmData = null
                isSelectionMode = false
                onMove(confData.direction, confData.pointsToMigrate, confData.targetDisk, confData.targetPartition, strategy)
            }
        )
    }

    // ── CATEGORY INSPECTOR MODAL BOTTOM SHEET ──
    if (inspectingCategory != null) {
        val currentInspectItem = inspectingCategory!!
        CategoryInspectorBottomSheet(
            item = currentInspectItem,
            onDismiss = { inspectingCategory = null },
            onRequestDelete = {
                categoryToDelete = inspectingCategory
            },
            onTogglePreserveMedia = { checked ->
                val targetPath = currentInspectItem.sdPath.ifBlank { currentInspectItem.internalPath }
                val internalPath = currentInspectItem.internalPath
                val catId = currentInspectItem.id
                val catType = currentInspectItem.mountCategory
                coroutineScope.launch(Dispatchers.IO) {
                    if (targetPath.isNotBlank()) {
                        if (checked) {
                            RootShell.exec("rm -f '$targetPath/.nomedia' '$internalPath/.nomedia' 2>/dev/null")
                        } else {
                            RootShell.exec("touch '$targetPath/.nomedia' 2>/dev/null")
                        }
                        MountManager.triggerMediaScan(targetPath)
                        if (internalPath.isNotBlank()) {
                            MountManager.triggerMediaScan(internalPath)
                        }
                    }
                }
                val updatedPoints = mountPoints.map { mp ->
                    if (mp.id == catId || (mp.resolveCategory() == catType && catId == "media")) {
                        mp.copy(preserveMedia = checked)
                    } else mp
                }
                onMountPointsChanged(updatedPoints)
                inspectingCategory = currentInspectItem.copy(preserveMedia = checked)
            }
        )
    }

    // ── CATEGORY DELETE CONFIRMATION DIALOG ──
    if (categoryToDelete != null) {
        val catItem = categoryToDelete!!
        CategoryDeleteConfirmDialog(
            item = catItem,
            isDeleting = isDeletingCategory,
            onConfirm = { location ->
                isDeletingCategory = true
                onDeleteCategoryData(catItem.id, location) { success, errMsg ->
                    isDeletingCategory = false
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.category_inspector_delete_success), Toast.LENGTH_SHORT).show()
                        categoryToDelete = null
                        inspectingCategory = null
                    } else {
                        val msg = context.getString(R.string.category_inspector_delete_failed, errMsg ?: "Unknown error")
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = {
                if (!isDeletingCategory) {
                    categoryToDelete = null
                }
            }
        )
    }

    // ── UNMOUNT CONFIRMATION DIALOG ──
    if (showUnmountConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnmountConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = SunsetAmber
                )
            },
            title = {
                Text(
                    stringResource(R.string.unmount_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    stringResource(R.string.unmount_confirm_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnmountConfirmDialog = false
                        onUnmount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SunsetAmber)
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnmountConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // ── NEED MIGRATION FAST ACTION DIALOG ──
    if (showNeedMigrationDialog) {
        NeedMigrationDialog(
            game = game,
            onConfirmMigration = {
                showNeedMigrationDialog = false
                val targetPoints = if (mountPoints.isNotEmpty()) mountPoints else buildTargetMountPoints(setOf("data", "obb"), mountPoints, game, sdBase)
                onMove(MoveDirection.TO_SD, targetPoints, null, null, ConflictStrategy.OVERWRITE)
            },
            onDismiss = { showNeedMigrationDialog = false }
        )
    }

    // ── ADD CUSTOM DIRECTORY DIALOG ──
    if (showAddCustomDialog) {
        AddCustomDirectoryDialog(
            sdBase = sdBase,
            packageName = game.packageName,
            onDismiss = { showAddCustomDialog = false },
            onAdd = { label, targetInternalPath, sourceSdPath, preserveMedia ->
                showAddCustomDialog = false
                val newPoint = MountPointConfig(
                    id = "custom_${System.currentTimeMillis()}",
                    category = MountPointCategory.CUSTOM,
                    sourcePath = sourceSdPath,
                    targetPath = targetInternalPath,
                    label = label,
                    enabled = true,
                    preserveMedia = preserveMedia
                )
                onMountPointsChanged(mountPoints + newPoint)
                if (preserveMedia) {
                    coroutineScope.launch(Dispatchers.IO) {
                        RootShell.exec("rm -f '$sourceSdPath/.nomedia' 2>/dev/null")
                        MountManager.triggerMediaScan(sourceSdPath)
                    }
                }
            }
        )
    }
}

@Composable
private fun AddCustomDirectoryDialog(
    sdBase: String,
    packageName: String,
    onDismiss: () -> Unit,
    onAdd: (label: String, targetInternalPath: String, sourceSdPath: String, preserveMedia: Boolean) -> Unit
) {
    var labelText by remember { mutableStateOf("") }
    var internalPathText by remember { mutableStateOf("") }
    var customSdPathText by remember { mutableStateOf("") }
    var isManualSdPath by remember { mutableStateOf(false) }
    var preserveMedia by remember { mutableStateOf(true) }
    var showRootPickerForInternal by remember { mutableStateOf(false) }
    var showRootPickerForSd by remember { mutableStateOf(false) }

    val suggestions = listOf(
        Triple("Telegram", "/data/media/0/Android/media/org.telegram.messenger", "$sdBase/MountX/Custom/Telegram"),
        Triple("WhatsApp", "/data/media/0/Android/media/com.whatsapp", "$sdBase/MountX/Custom/WhatsApp"),
        Triple("Download", "/data/media/0/Download", "$sdBase/MountX/Custom/Download"),
        Triple("DCIM", "/data/media/0/DCIM", "$sdBase/MountX/Custom/DCIM"),
        Triple("Pictures", "/data/media/0/Pictures", "$sdBase/MountX/Custom/Pictures")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111726),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF6366F1).copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.dialog_add_custom_directory_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF1F5F9)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_add_custom_directory_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )

                // Quick Suggestions section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_add_custom_suggestions),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF64748B)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { (name, internal, sd) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1A233A),
                                border = BorderStroke(1.dp, Color(0xFF2E3D5C)),
                                modifier = Modifier.clickable {
                                    labelText = name
                                    internalPathText = internal
                                    customSdPathText = sd
                                    isManualSdPath = true
                                }
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFF818CF8),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                val customFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF162035),
                    unfocusedContainerColor = Color(0xFF0F1524),
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color(0xFF334366),
                    focusedLabelColor = Color(0xFF818CF8),
                    unfocusedLabelColor = Color(0xFF94A3B8),
                    focusedTextColor = Color(0xFFF1F5F9),
                    unfocusedTextColor = Color(0xFFE2E8F0),
                    cursorColor = Color(0xFF6366F1)
                )

                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text(stringResource(R.string.dialog_add_custom_name_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_add_custom_name_hint), color = Color(0xFF64748B)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = customFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = internalPathText,
                    onValueChange = { newPath ->
                        internalPathText = newPath
                        if (!isManualSdPath) {
                            val folderName = newPath.trimEnd('/').substringAfterLast('/').ifBlank { "custom" }
                            customSdPathText = "$sdBase/MountX/Custom/$folderName"
                        }
                    },
                    label = { Text(stringResource(R.string.dialog_add_custom_path_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_add_custom_directory_hint), color = Color(0xFF64748B)) },
                    trailingIcon = {
                        IconButton(onClick = { showRootPickerForInternal = true }) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Pilih Folder",
                                tint = Color(0xFF818CF8)
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = customFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customSdPathText,
                    onValueChange = {
                        customSdPathText = it
                        isManualSdPath = true
                    },
                    label = { Text(stringResource(R.string.dialog_add_custom_sd_label)) },
                    placeholder = { Text("$sdBase/MountX/Custom/...", color = Color(0xFF64748B)) },
                    trailingIcon = {
                        IconButton(onClick = { showRootPickerForSd = true }) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Pilih Folder",
                                tint = Color(0xFF818CF8)
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = customFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF162035)),
                    border = BorderStroke(1.dp, Color(0xFF334366)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = stringResource(R.string.category_preserve_media_title),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFFF1F5F9)
                            )
                            Text(
                                text = stringResource(R.string.category_preserve_media_desc),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = preserveMedia,
                            onCheckedChange = { preserveMedia = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawInternal = internalPathText.trim()
                    val finalInternal = when {
                        rawInternal.startsWith("/sdcard/") -> "/data/media/0/" + rawInternal.removePrefix("/sdcard/")
                        rawInternal == "/sdcard" -> "/data/media/0"
                        else -> rawInternal
                    }
                    val finalLabel = labelText.trim().ifEmpty { finalInternal.trimEnd('/').substringAfterLast('/') }
                    val finalSd = customSdPathText.trim().ifEmpty { "$sdBase/MountX/Custom/${finalLabel.replace(" ", "_")}" }
                    onAdd(finalLabel, finalInternal, finalSd, preserveMedia)
                },
                enabled = internalPathText.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    disabledContainerColor = Color(0xFF1E2738),
                    disabledContentColor = Color(0xFF475569)
                ),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_add_custom_confirm),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    )

    if (showRootPickerForInternal) {
        app.mountx.ui.components.RootDirectoryPickerSheet(
            initialPath = internalPathText.ifBlank { "/data/media/0" },
            sdBasePath = sdBase,
            onDismiss = { showRootPickerForInternal = false },
            onPathSelected = { pickedPath ->
                internalPathText = pickedPath
                if (labelText.isBlank()) {
                    labelText = pickedPath.trimEnd('/').substringAfterLast('/').ifBlank { "custom" }
                }
                if (!isManualSdPath) {
                    val folderName = pickedPath.trimEnd('/').substringAfterLast('/').ifBlank { "custom" }
                    customSdPathText = "$sdBase/MountX/Custom/$folderName"
                }
            }
        )
    }

    if (showRootPickerForSd) {
        app.mountx.ui.components.RootDirectoryPickerSheet(
            initialPath = customSdPathText.ifBlank { "$sdBase/MountX/Custom" },
            sdBasePath = sdBase,
            onDismiss = { showRootPickerForSd = false },
            onPathSelected = { pickedPath ->
                customSdPathText = pickedPath
                isManualSdPath = true
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
    onInspect: () -> Unit = {},
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
            .clickable(
                onClick = {
                    if (isSelectionMode) onToggleCheck() else onInspect()
                }
            )
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

            if (!isSelectionMode) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryInspectorBottomSheet(
    item: UnifiedCategoryItem,
    onDismiss: () -> Unit,
    onRequestDelete: () -> Unit,
    onTogglePreserveMedia: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── HEADER: Category Icon, Name, Subtitle, and Size ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = item.iconTint.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = FormatUtils.formatExactBytes(item.bytes),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
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
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (item.isMicroSd) Color(0xFF3BA71A) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }

                    if (item.userId > 0) {
                        val userLabel = when (item.userId) {
                            999 -> stringResource(R.string.user_profile_dual_apps)
                            10 -> stringResource(R.string.user_profile_work, item.userId)
                            else -> stringResource(R.string.user_profile_custom, item.userId)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF6366F1).copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, Color(0xFF6366F1).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = userLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF818CF8),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // ── CARD 1: LIVE MOUNT STATUS ──
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (item.isCategoryMounted) Color(0x0D3BA71A)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (item.isCategoryMounted) Color(0xFF3BA71A).copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (item.isCategoryMounted) Icons.Default.CheckCircle else Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (item.isCategoryMounted) Color(0xFF3BA71A) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.category_inspector_mount_status),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (item.isCategoryMounted) stringResource(R.string.category_inspector_mount_active)
                                   else stringResource(R.string.category_inspector_mount_inactive),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (item.isCategoryMounted) Color(0xFF3BA71A) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── CARD 1B: MEDIA VISIBILITY (NO .NOMEDIA FILTER) ──
            if (item.mountCategory == MountPointCategory.MEDIA_DOWNLOADS || item.mountCategory == MountPointCategory.CUSTOM) {
                var isMediaVisible by remember(item.id, item.preserveMedia) { mutableStateOf(item.preserveMedia) }

                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PermMedia,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.category_preserve_media_title),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = stringResource(R.string.category_preserve_media_desc),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMediaVisible,
                            onCheckedChange = { checked ->
                                isMediaVisible = checked
                                onTogglePreserveMedia?.invoke(checked)
                            }
                        )
                    }
                }
            }

            // ── CARD 2: INTERNAL STORAGE PATH ──
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = Color(0xFFDF4006),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.category_inspector_internal_title),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFDF4006)
                            )
                        }

                        Text(
                            text = if (item.isCategoryMounted) stringResource(R.string.category_inspector_internal_mounted_note)
                                   else FormatUtils.formatExactBytes(item.internalBytes),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (item.isCategoryMounted) Color(0xFF3BA71A) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.internalPath.ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    if (item.internalPath.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("MountX Path", item.internalPath))
                                    Toast.makeText(context, context.getString(R.string.category_inspector_path_copied), Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.category_inspector_copy_path),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp)
                                )
                            }
                        }
                    }
                }
            }

            // ── CARD 3: MICROSD / EXTERNAL STORAGE PATH ──
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SdCard,
                                contentDescription = null,
                                tint = Color(0xFF3BA71A),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.category_inspector_microsd_title),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF3BA71A)
                            )
                        }

                        Text(
                            text = if (item.sdBytes > 0L) FormatUtils.formatExactBytes(item.sdBytes)
                                   else stringResource(R.string.category_inspector_not_migrated),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = if (item.sdBytes > 0L) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (item.sdBytes > 0L) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.sdPath.ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    if (item.sdPath.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("MountX Path", item.sdPath))
                                    Toast.makeText(context, context.getString(R.string.category_inspector_path_copied), Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.category_inspector_copy_path),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp)
                                )
                            }
                        }
                    }
                }
            }

            // ── ACTION: DELETE CATEGORY DATA ──
            val hasDataToDelete = item.internalBytes > 0L || item.sdBytes > 0L || item.bytes > 0L
            if (hasDataToDelete) {
                OutlinedButton(
                    onClick = onRequestDelete,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.category_inspector_delete_btn),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CategoryDeleteConfirmDialog(
    item: UnifiedCategoryItem,
    isDeleting: Boolean,
    onConfirm: (CategoryDeleteLocation) -> Unit,
    onDismiss: () -> Unit
) {
    val hasBoth = item.internalBytes > 0L && item.sdBytes > 0L
    val initialLocation = when {
        hasBoth -> CategoryDeleteLocation.BOTH
        item.isCategoryMounted || item.sdBytes > 0L -> CategoryDeleteLocation.SD_ONLY
        else -> CategoryDeleteLocation.INTERNAL_ONLY
    }
    var selectedLocation by remember { mutableStateOf(initialLocation) }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFE53935),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.category_inspector_delete_dialog_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.category_inspector_delete_dialog_subtitle, item.title),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (item.id == "apk") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x1AE53935),
                        border = BorderStroke(0.5.dp, Color(0xFFE53935).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.category_inspector_delete_warning_apk),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFFE53935),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else if (item.id == "lib") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x1AE53935),
                        border = BorderStroke(0.5.dp, Color(0xFFE53935).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.category_inspector_delete_warning_lib),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFFE53935),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (!hasBoth) {
                    val locationText = if (item.isCategoryMounted || item.sdBytes > 0L) {
                        stringResource(R.string.category_inspector_delete_loc_sd_mounted_hint, FormatUtils.formatExactBytes(item.sdBytes))
                    } else {
                        stringResource(R.string.category_inspector_delete_loc_internal_hint, FormatUtils.formatExactBytes(item.internalBytes))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isCategoryMounted || item.sdBytes > 0L) Icons.Default.SdCard else Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = if (item.isCategoryMounted || item.sdBytes > 0L) Color(0xFF3BA71A) else Color(0xFFDF4006),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = locationText,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (hasBoth) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Option 1: Internal only
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLocation = CategoryDeleteLocation.INTERNAL_ONLY }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLocation == CategoryDeleteLocation.INTERNAL_ONLY,
                                onClick = { selectedLocation = CategoryDeleteLocation.INTERNAL_ONLY }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.category_inspector_delete_loc_internal,
                                    FormatUtils.formatExactBytes(item.internalBytes)
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                            )
                        }

                        // Option 2: SD only
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLocation = CategoryDeleteLocation.SD_ONLY }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLocation == CategoryDeleteLocation.SD_ONLY,
                                onClick = { selectedLocation = CategoryDeleteLocation.SD_ONLY }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.category_inspector_delete_loc_sd,
                                    FormatUtils.formatExactBytes(item.sdBytes)
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                            )
                        }

                        // Option 3: Both
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLocation = CategoryDeleteLocation.BOTH }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLocation == CategoryDeleteLocation.BOTH,
                                onClick = { selectedLocation = CategoryDeleteLocation.BOTH }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.category_inspector_delete_loc_both,
                                    FormatUtils.formatExactBytes(item.internalBytes + item.sdBytes)
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }

                if (isDeleting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFE53935)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.category_inspector_deleting),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedLocation) },
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.category_inspector_delete_confirm_action),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartStoragePartitionBottomSheet(
    availableDisks: List<SdCardDiskInfo>,
    internalFreeBytes: Long,
    selectedCategoryIds: Set<String>,
    categories: List<UnifiedCategoryItem>,
    currentSdBase: String = "/data/sdext2",
    onDismiss: () -> Unit,
    onConfirmMove: (dir: MoveDirection, targetDisk: SdCardDiskInfo?, targetPartition: PartitionInfo?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var modalStep by remember { mutableStateOf(1) } // 1: Disk, 2: Partition, 3: Confirmation

    val externalDisks = remember(availableDisks) {
        availableDisks
    }

    val selectedItems = remember(selectedCategoryIds, categories) {
        categories.filter { selectedCategoryIds.contains(it.id) }
    }
    val allSelectedOnInternal = remember(selectedItems) {
        selectedItems.isNotEmpty() && selectedItems.all { !it.isMicroSd }
    }
    val allSelectedOnMicroSd = remember(selectedItems) {
        selectedItems.isNotEmpty() && selectedItems.all { it.isMicroSd }
    }
    val totalSelectedBytes = remember(selectedItems) {
        selectedItems.sumOf { it.bytes }
    }
    val hasRiskSelected = remember(selectedItems) {
        selectedItems.any { it.isRisk }
    }

    var isTargetInternal by remember(allSelectedOnMicroSd) { mutableStateOf(allSelectedOnMicroSd) }

    var selectedDisk by remember(externalDisks) {
        mutableStateOf<SdCardDiskInfo?>(externalDisks.firstOrNull())
    }

    var selectedPartition by remember(selectedDisk, allSelectedOnMicroSd, currentSdBase) {
        val allParts = selectedDisk?.partitions ?: emptyList()
        val validPart = if (allSelectedOnMicroSd) {
            allParts.firstOrNull { it.mountPoint != null && it.mountPoint.trimEnd('/') != currentSdBase.trimEnd('/') }
        } else {
            allParts.firstOrNull()
        }
        mutableStateOf(validPart)
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
                                val hasOtherPartitions = disk.partitions.any { part ->
                                    !part.mountPoint.isNullOrBlank() && part.mountPoint.trimEnd('/') != currentSdBase.trimEnd('/')
                                }
                                val isDiskDisabled = allSelectedOnMicroSd && !hasOtherPartitions
                                val isSelected = !isTargetInternal && selectedDisk == disk
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDiskDisabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (!isDiskDisabled && isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isDiskDisabled) {
                                            isTargetInternal = false
                                            selectedDisk = disk
                                            selectedPartition = disk.partitions.firstOrNull { it.mountPoint != null && it.mountPoint.trimEnd('/') != currentSdBase.trimEnd('/') }
                                                ?: disk.partitions.firstOrNull()
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
                                                    color = if (isDiskDisabled) Color.Gray.copy(alpha = 0.15f)
                                                            else if (disk.diskType == DiskType.USB_OTG) Color(0xFF7E57C2).copy(alpha = 0.12f)
                                                            else Color(0xFF3BA71A).copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = if (disk.diskType == DiskType.USB_OTG) Icons.Default.Usb else Icons.Default.SdCard,
                                                contentDescription = null,
                                                tint = if (isDiskDisabled) Color.Gray
                                                       else if (disk.diskType == DiskType.USB_OTG) Color(0xFF7E57C2)
                                                       else Color(0xFF3BA71A),
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
                                                color = if (isDiskDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isDiskDisabled) stringResource(R.string.manage_current_location_disabled)
                                                       else "${FormatUtils.formatLegendBytes(disk.totalFreeBytes)} / ${FormatUtils.formatLegendBytes(disk.totalSizeBytes)}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = if (isDiskDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        RadioButton(
                                            selected = !isDiskDisabled && isSelected,
                                            onClick = if (!isDiskDisabled) {
                                                {
                                                    isTargetInternal = false
                                                    selectedDisk = disk
                                                    selectedPartition = disk.partitions.firstOrNull { it.mountPoint != null && it.mountPoint.trimEnd('/') != currentSdBase.trimEnd('/') }
                                                        ?: disk.partitions.firstOrNull()
                                                }
                                            } else null,
                                            enabled = !isDiskDisabled
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
                        enabled = (isTargetInternal && !allSelectedOnInternal) ||
                            (!isTargetInternal && (selectedDisk == null || !allSelectedOnMicroSd || selectedDisk!!.partitions.any { !it.mountPoint.isNullOrBlank() && it.mountPoint.trimEnd('/') != currentSdBase.trimEnd('/') })),
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
                                val isCurrentPartition = allSelectedOnMicroSd && (
                                    (!partition.mountPoint.isNullOrBlank() && partition.mountPoint.trimEnd('/') == currentSdBase.trimEnd('/')) ||
                                    (partition.mountPoint.isNullOrBlank() && currentSdBase == "/data/sdext2" && partition.partitionNumber == 3)
                                )
                                val isSelected = !isCurrentPartition && selectedPartition == partition
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrentPartition) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (!isCurrentPartition && isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isCurrentPartition) { selectedPartition = partition }
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
                                                    if (isCurrentPartition) Color.Gray.copy(alpha = 0.15f)
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SdStorage,
                                                contentDescription = null,
                                                tint = if (isCurrentPartition) Color.Gray else MaterialTheme.colorScheme.primary,
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
                                                color = if (isCurrentPartition) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isCurrentPartition) stringResource(R.string.manage_current_location_disabled)
                                                       else "${FormatUtils.formatLegendBytes(partition.freeBytes)} / ${FormatUtils.formatLegendBytes(partition.sizeBytes)}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = if (isCurrentPartition) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = if (!isCurrentPartition) { { selectedPartition = partition } } else null,
                                            enabled = !isCurrentPartition
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
                            enabled = selectedPartition != null && (!allSelectedOnMicroSd || selectedPartition?.mountPoint?.trimEnd('/') != currentSdBase.trimEnd('/')),
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
                                value = if (isTargetInternal) {
                                    val diskName = selectedDisk?.hardwareTitle ?: stringResource(R.string.manage_location_badge_microsd)
                                    val partName = selectedPartition?.let { " (Partisi ${it.partitionNumber} - ${it.fsType.ifBlank { "EXT4" }})" } ?: ""
                                    "$diskName$partName"
                                } else {
                                    stringResource(R.string.manage_internal_memory_label)
                                }
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
 * Tab 1: App Info Tab (Identitas Paket, Versi, Lingkungan Sandbox & Jalur Direktori)
 */
@Composable
private fun AppInfoTabContent(
    game: GameEntry,
    packageInfo: android.content.pm.PackageInfo?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val installTimeStr = remember(packageInfo) {
        packageInfo?.firstInstallTime?.let { if (it > 0L) dateFormat.format(Date(it)) else "-" } ?: "-"
    }
    val updateTimeStr = remember(packageInfo) {
        packageInfo?.lastUpdateTime?.let { if (it > 0L) dateFormat.format(Date(it)) else "-" } ?: "-"
    }

    val targetSdkStr = remember(packageInfo) {
        packageInfo?.applicationInfo?.targetSdkVersion?.let { target ->
            val androidName = when (target) {
                35 -> "Android 15"
                34 -> "Android 14"
                33 -> "Android 13"
                32 -> "Android 12L"
                31 -> "Android 12"
                30 -> "Android 11"
                29 -> "Android 10"
                28 -> "Android 9"
                26, 27 -> "Android 8"
                else -> "API $target"
            }
            "$target ($androidName)"
        } ?: "-"
    }

    val minSdkStr = remember(packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            packageInfo?.applicationInfo?.minSdkVersion?.let { minSdk ->
                val androidName = when (minSdk) {
                    24, 25 -> "Android 7"
                    26, 27 -> "Android 8"
                    28 -> "Android 9"
                    29 -> "Android 10"
                    30 -> "Android 11"
                    else -> "API $minSdk"
                }
                "$minSdk ($androidName)"
            } ?: "-"
        } else "-"
    }

    val uidStr = remember(packageInfo) {
        packageInfo?.applicationInfo?.uid?.toString() ?: "-"
    }

    val installerPackage = remember(packageInfo) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(game.packageName).installingPackageName ?: "Sistem / Sideload"
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(game.packageName) ?: "Sistem / Sideload"
            }
        } catch (_: Exception) {
            "Sistem / Sideload"
        }
    }

    val apkPath = remember(packageInfo) {
        packageInfo?.applicationInfo?.sourceDir ?: "-"
    }

    val nativeLibDir = remember(packageInfo) {
        packageInfo?.applicationInfo?.nativeLibraryDir ?: "-"
    }

    val copyToClipboard: (String, String) -> Unit = { label, value ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label disalin", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Action Buttons: Launch App & System App Settings ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(game.packageName)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    } else {
                        Toast.makeText(context, "Aplikasi tidak dapat dibuka langsung", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.app_info_btn_launch),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", game.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal membuka setelan aplikasi", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.app_info_btn_system_settings),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ── Section 1: Identitas Paket & Versi ──
        InfoSectionCard(
            title = stringResource(R.string.app_info_section_identity),
            icon = Icons.Default.Android
        ) {
            InfoRowItem(
                label = stringResource(R.string.app_info_pkg_name),
                value = game.packageName,
                isCopyable = true,
                onCopy = { copyToClipboard("Package Name", game.packageName) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            val verCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) }?.toString() ?: "-"
            InfoRowItem(
                label = stringResource(R.string.app_info_version),
                value = "${packageInfo?.versionName ?: "-"} (Build $verCode)"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            InfoRowItem(
                label = stringResource(R.string.app_info_sdk_levels),
                value = "$targetSdkStr / $minSdkStr"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            InfoRowItem(
                label = stringResource(R.string.app_info_installer),
                value = installerPackage
            )
        }

        // ── Section 2: Lingkungan Runtime & Keamanan ──
        InfoSectionCard(
            title = stringResource(R.string.app_info_section_runtime),
            icon = Icons.Default.Lock
        ) {
            InfoRowItem(
                label = stringResource(R.string.app_info_uid_gid),
                value = uidStr
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            InfoRowItem(
                label = stringResource(R.string.app_info_install_time),
                value = installTimeStr
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            InfoRowItem(
                label = stringResource(R.string.app_info_update_time),
                value = updateTimeStr
            )
        }

        // ── Section 3: Jalur Berkas & Penyimpanan ──
        InfoSectionCard(
            title = stringResource(R.string.app_info_section_storage),
            icon = Icons.Default.Folder
        ) {
            InfoRowItem(
                label = stringResource(R.string.app_info_apk_path),
                value = apkPath,
                isCopyable = true,
                onCopy = { copyToClipboard("APK Path", apkPath) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            InfoRowItem(
                label = stringResource(R.string.app_info_lib_path),
                value = nativeLibDir,
                isCopyable = true,
                onCopy = { copyToClipboard("Lib Path", nativeLibDir) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            val internalData = "/data/data/${game.packageName}"
            InfoRowItem(
                label = stringResource(R.string.app_info_internal_data),
                value = internalData,
                isCopyable = true,
                onCopy = { copyToClipboard("Internal Data Path", internalData) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            val extData = "/storage/emulated/0/Android/data/${game.packageName}"
            InfoRowItem(
                label = stringResource(R.string.app_info_external_data),
                value = extData,
                isCopyable = true,
                onCopy = { copyToClipboard("External Data Path", extData) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            val extObb = "/storage/emulated/0/Android/obb/${game.packageName}"
            InfoRowItem(
                label = stringResource(R.string.app_info_external_obb),
                value = extObb,
                isCopyable = true,
                onCopy = { copyToClipboard("External OBB Path", extObb) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun InfoSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun InfoRowItem(
    label: String,
    value: String,
    isCopyable: Boolean = false,
    onCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isCopyable && onCopy != null) Modifier.clickable { onCopy() }
                else Modifier
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCopyable) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { onCopy?.invoke() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(15.dp)
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
        listOfNotNull(
            ChartSlice("Dex", breakdown.dexBytes, Color(0xFFAB47BC)),
            ChartSlice("Lib", breakdown.libBytes, Color(0xFFFB8C00)),
            ChartSlice("Data", breakdown.dataBytes, Color(0xFF00ACC1)),
            ChartSlice("Cache", breakdown.cacheBytes, Color(0xFFE57373)),
            if (breakdown.physicalExt1Bytes > 0) ChartSlice("Ext1", breakdown.physicalExt1Bytes, Color(0xFF3149FF)) else null,
            if (breakdown.ext2Bytes > 0) ChartSlice("Ext2", breakdown.ext2Bytes, Color(0xFF43A047)) else null,
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
