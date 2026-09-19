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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
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
    isMoving: Boolean,
    moveMessage: String?,
    onDismiss: () -> Unit,
    onMove: (MoveDirection, MigrationTarget) -> Unit,
    onUpdateMode: (MountMode) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    var currentMode by remember(game.mode) { mutableStateOf(game.mode) }
    var selectedTarget by remember { mutableStateOf(MigrationTarget.ALL) }

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
        Column(modifier = Modifier.fillMaxSize()) {
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
                        breakdown = breakdown,
                        currentMode = currentMode,
                        onUpdateMode = { mode ->
                            currentMode = mode
                            onUpdateMode(mode)
                        },
                        selectedTarget = selectedTarget,
                        onSelectTarget = { selectedTarget = it },
                        isMoving = isMoving,
                        moveMessage = moveMessage,
                        onMove = onMove,
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
 * Tab 1: Unified Transfer Hub & Advanced Mount Mode Settings
 */
@Composable
private fun ManageTabContent(
    game: GameEntry,
    breakdown: AppStorageBreakdown,
    currentMode: MountMode,
    onUpdateMode: (MountMode) -> Unit,
    selectedTarget: MigrationTarget,
    onSelectTarget: (MigrationTarget) -> Unit,
    isMoving: Boolean,
    moveMessage: String?,
    onMove: (MoveDirection, MigrationTarget) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    val isMounted = game.mountStatus == MountStatus.MOUNTED
    val sizeToSd = breakdown.getSizeForScope(selectedTarget, MoveDirection.TO_SD)
    val sizeToInternal = breakdown.getSizeForScope(selectedTarget, MoveDirection.TO_INTERNAL)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── UNIFIED PHYSICAL DATA TRANSFER & MOUNT HUB CARD ──
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
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Header: Title & Dynamic Storage Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.game_detail_move_title),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isMounted) CyberEmerald.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(
                            1.dp,
                            if (isMounted) CyberEmerald.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isMounted) Icons.Default.SdCard else Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = if (isMounted) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (isMounted) stringResource(R.string.game_detail_badge_mounted)
                                       else stringResource(R.string.game_detail_status_unmounted),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isMounted) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Storage location explanation
                Text(
                    text = if (isMounted) stringResource(R.string.game_detail_manage_status_sd)
                           else stringResource(R.string.game_detail_manage_status_internal),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                // Scope Selector Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.game_detail_target_scope),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            MigrationTarget.ALL to stringResource(R.string.game_detail_target_all),
                            MigrationTarget.DATA_ONLY to stringResource(R.string.game_detail_target_data),
                            MigrationTarget.OBB_ONLY to stringResource(R.string.game_detail_target_obb)
                        ).forEach { (target, label) ->
                            val isSelected = selectedTarget == target
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clickable { onSelectTarget(target) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic Size Estimation Box
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isMounted)
                                stringResource(R.string.game_detail_manage_est_restore, FormatUtils.formatBytes(sizeToInternal))
                            else
                                stringResource(R.string.game_detail_manage_est_transfer, FormatUtils.formatBytes(sizeToSd)),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Zero data loss safety note
                Text(
                    text = stringResource(R.string.game_detail_move_warning),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )

                // Loading or Result banner
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
                        // Move to MicroSD (Auto-Mount)
                        Button(
                            onClick = { onMove(MoveDirection.TO_SD, selectedTarget) },
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
                            onClick = { onMove(MoveDirection.TO_INTERNAL, selectedTarget) },
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
            }
        }

        // ── COLLAPSIBLE ADVANCED OPTIONS: MOUNT MODE CARD ──
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Clickable accordion header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = stringResource(R.string.game_detail_advanced_mode_title),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.game_detail_advanced_mode_summary, currentMode.name),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Collapsible Content
                AnimatedVisibility(
                    visible = isAdvancedExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .padding(bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // PKG Option
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (currentMode == MountMode.PKG)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    if (currentMode == MountMode.PKG) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onUpdateMode(MountMode.PKG) }
                            ) {
                                Column(modifier = Modifier.padding(9.dp)) {
                                    Text(
                                        text = "PKG Mode",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (currentMode == MountMode.PKG) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.add_game_mode_pkg_desc),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 9.5.sp,
                                            lineHeight = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            // FILES Option
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (currentMode == MountMode.FILES)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    if (currentMode == MountMode.FILES) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onUpdateMode(MountMode.FILES) }
                            ) {
                                Column(modifier = Modifier.padding(9.dp)) {
                                    Text(
                                        text = "FILES Mode",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (currentMode == MountMode.FILES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.add_game_mode_files_desc),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 9.5.sp,
                                            lineHeight = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.game_detail_mode_switch_desc),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 9.5.sp,
                                lineHeight = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }

        // ── DANGER ZONE: REMOVE GAME ──
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCrimson),
            border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonCrimson)
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.game_detail_delete_action),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCrimson
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
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
