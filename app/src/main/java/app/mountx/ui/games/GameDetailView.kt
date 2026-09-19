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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
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
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.MountStatus
import app.mountx.data.model.MoveDirection
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
    sdBase: String = "/data/sdext2",
    onDismiss: () -> Unit,
    onMoveMountPoints: (MoveDirection, List<MountPointConfig>) -> Unit = { _, _ -> },
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
                        onMountPointsChanged = { updated ->
                            currentMountPoints = updated
                            onUpdateMountPoints?.invoke(updated)
                        },
                        sdBase = sdBase,
                        isMoving = isMoving,
                        moveMessage = moveMessage,
                        onMove = { dir -> onMoveMountPoints(dir, currentMountPoints) },
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
                    customIconTint = Color(0xFF3149FF)
                )

                BreakdownRow(
                    label = stringResource(R.string.game_detail_cat_ext2),
                    subLabel = stringResource(R.string.game_detail_cat_ext2_sub),
                    labelColor = Color(0xFF43A047),
                    sizeText = FormatUtils.formatExactBytes(breakdown.ext2Bytes),
                    bytes = breakdown.ext2Bytes,
                    vectorIcon = Icons.Default.SdCard,
                    customIconTint = Color(0xFF3BA71A),
                    statusBadge = if (breakdown.ext2Bytes > 0L) stringResource(R.string.game_detail_badge_mounted) else null
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
    onMountPointsChanged: (List<MountPointConfig>) -> Unit,
    sdBase: String,
    isMoving: Boolean,
    moveMessage: String?,
    onMove: (MoveDirection) -> Unit,
    onSaveDraft: () -> Unit,
    onCancelDraft: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomPathDialog by remember { mutableStateOf(false) }

    val isMounted = game.mountStatus == MountStatus.MOUNTED
    val activeMountPoints = mountPoints.filter { it.enabled }
    val totalActiveSizeBytes = activeMountPoints.sumOf { it.sizeBytes }

    val cyberEmerald = CyberEmerald
    val electricAmber = Color(0xFFFFB300)
    val neonCrimson = NeonCrimson

    if (showCustomPathDialog) {
        CustomPathDialog(
            sdBase = sdBase,
            onDismiss = { showCustomPathDialog = false },
            onAdd = { customPoint ->
                onMountPointsChanged(mountPoints + customPoint)
                showCustomPathDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── TOP HEADER / STATUS CARD ──
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isDraftMode) "Pratinjau Konfigurasi" else "Status Penyimpanan",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDraftMode) {
                            electricAmber.copy(alpha = 0.12f)
                        } else if (isMounted) {
                            cyberEmerald.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isDraftMode) electricAmber.copy(alpha = 0.4f)
                            else if (isMounted) cyberEmerald.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isDraftMode) Icons.Default.Tune
                                else if (isMounted) Icons.Default.SdCard else Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = if (isDraftMode) electricAmber
                                else if (isMounted) cyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (isDraftMode) "Mode Draft"
                                else if (isMounted) stringResource(R.string.game_detail_badge_mounted)
                                else stringResource(R.string.game_detail_status_unmounted),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isDraftMode) electricAmber
                                else if (isMounted) cyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = if (isDraftMode) {
                        "Tentukan direktori target untuk dipindahkan dan dimount ke MicroSD. Konfigurasi hanya akan disimpan setelah menekan 'Terapkan & Tambahkan ke MountX'."
                    } else if (isMounted) {
                        stringResource(R.string.game_detail_manage_status_sd)
                    } else {
                        stringResource(R.string.game_detail_manage_status_internal)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                // Total estimated size row
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Total Target Terpilih (${activeMountPoints.size} aktif)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = FormatUtils.formatBytes(totalActiveSizeBytes),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = cyberEmerald
                        )
                    }
                }
            }
        }

        // ── MULTI-TARGET MOUNT DIRECTORIES CARD ──
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Direktori Target Mount",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${activeMountPoints.size} / ${mountPoints.size} Aktif",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (mountPoints.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada direktori terkonfigurasi.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    mountPoints.forEachIndexed { index, point ->
                        val catLabel = when (point.category) {
                            MountPointCategory.GAME_ASSETS -> "Game Assets & Data (files/obb)"
                            MountPointCategory.MEDIA_DOWNLOADS -> "Media & Unduhan (.nomedia)"
                            MountPointCategory.CACHE_SHADERS -> "Cache & Shaders"
                            MountPointCategory.PRIVATE_INTERNAL -> "Large Private Data"
                            MountPointCategory.CUSTOM -> "Kustom: ${point.id}"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (point.enabled) cyberEmerald.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = when (point.category) {
                                            MountPointCategory.GAME_ASSETS -> Icons.Default.SportsEsports
                                            MountPointCategory.MEDIA_DOWNLOADS -> Icons.Default.PermMedia
                                            MountPointCategory.CACHE_SHADERS -> Icons.Default.Cached
                                            MountPointCategory.PRIVATE_INTERNAL -> Icons.Default.Storage
                                            MountPointCategory.CUSTOM -> Icons.Default.Folder
                                        },
                                        contentDescription = null,
                                        tint = if (point.enabled) cyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = catLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (point.isVirtualContainer) {
                                                Surface(
                                                    shape = RoundedCornerShape(3.dp),
                                                    color = electricAmber.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "ext4 loop",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                                        color = electricAmber,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = point.targetPath,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 9.5.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (point.sizeBytes > 0) {
                                        Text(
                                            text = FormatUtils.formatBytes(point.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Switch(
                                        checked = point.enabled,
                                        onCheckedChange = { checked ->
                                            val updatedList = mountPoints.toMutableList()
                                            updatedList[index] = point.copy(enabled = checked)
                                            onMountPointsChanged(updatedList)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = cyberEmerald,
                                            checkedTrackColor = cyberEmerald.copy(alpha = 0.35f)
                                        )
                                    )

                                    if (point.category == MountPointCategory.CUSTOM) {
                                        IconButton(
                                            onClick = {
                                                val updatedList = mountPoints.toMutableList()
                                                updatedList.removeAt(index)
                                                onMountPointsChanged(updatedList)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Hapus",
                                                tint = neonCrimson.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.mount_add_custom_path),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── ACTION BUTTONS ──
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
            // Existing Game Action Hub
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
                            cyberEmerald.copy(alpha = 0.12f)
                        else
                            neonCrimson.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            if (moveMessage == "SUCCESS") cyberEmerald.copy(alpha = 0.4f) else neonCrimson.copy(alpha = 0.4f)
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
                                tint = if (moveMessage == "SUCCESS") cyberEmerald else neonCrimson,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (moveMessage == "SUCCESS")
                                    stringResource(R.string.move_data_success)
                                else
                                    stringResource(R.string.move_data_error, moveMessage),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                color = if (moveMessage == "SUCCESS") cyberEmerald else neonCrimson,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Move to MicroSD (Auto-Mount)
                    Button(
                        onClick = { onMove(MoveDirection.TO_SD) },
                        enabled = activeMountPoints.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.game_detail_move_to_sd),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Restore to Internal (Auto-Unmount + Permission fix)
                    OutlinedButton(
                        onClick = { onMove(MoveDirection.TO_INTERNAL) },
                        enabled = activeMountPoints.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.game_detail_move_to_internal),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Delete Game action
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, neonCrimson.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = neonCrimson
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = neonCrimson
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.game_detail_delete_action),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = neonCrimson
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.mount_add_custom_path), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Tentukan direktori kustom yang ingin dipetakan secara presisi ke MicroSD.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { customLabel = it },
                    label = { Text("Label / Nama Direktori (misal: Downloads)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = internalPath,
                    onValueChange = {
                        internalPath = it
                        errorMsg = null
                    },
                    label = { Text("Path Internal Lengkap (misal: /data/media/0/Telegram)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isPathSafe && rawInternal.isNotBlank()) {
                    Text(
                        "Keamanan Sistem: Direktori /data/app dan /system dilindungi dan tidak dapat dimount.",
                        color = NeonCrimson,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                errorMsg?.let {
                    Text(it, color = NeonCrimson, fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
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
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Simpan", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", fontSize = 12.sp)
            }
        }
    )
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
