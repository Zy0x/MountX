package app.mountx.ui.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Extension
import app.mountx.ui.components.ModuleInstallDialog
import app.mountx.ui.components.ConfirmDialog
import app.mountx.ui.components.NeedMigrationDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import app.mountx.ui.components.AppIconImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountx.R
import app.mountx.data.model.AppStatus
import app.mountx.data.model.DiskType
import app.mountx.data.model.GameEntry
import app.mountx.data.model.InternalStorageInfo
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountStatus
import app.mountx.data.model.RootSolution
import app.mountx.data.model.SdCardDiskInfo
import app.mountx.data.model.StorageInfo
import app.mountx.ui.theme.AmberGlow
import app.mountx.ui.theme.AmberWarn
import app.mountx.ui.theme.AuroraGradientBrush
import app.mountx.ui.theme.AuroraGradientBrushLight
import app.mountx.ui.theme.CoralError
import app.mountx.ui.theme.CrimsonGlow
import app.mountx.ui.theme.CyberEmerald
import app.mountx.ui.theme.ElectricCyan
import app.mountx.ui.theme.ElectricCyanBright
import app.mountx.ui.theme.ElectricIndigo
import app.mountx.ui.theme.EmeraldActive
import app.mountx.ui.theme.EmeraldGlow
import app.mountx.ui.theme.ForestGreenContainerLight
import app.mountx.ui.theme.ForestGreenLight
import app.mountx.ui.theme.HyperCyan
import app.mountx.ui.theme.HyperCyanBright
import app.mountx.ui.theme.MountXTheme
import app.mountx.ui.theme.NeonCrimson
import app.mountx.ui.theme.StorageGradientBrush
import app.mountx.ui.theme.SunsetAmber
import app.mountx.ui.theme.TerracottaRedContainerLight
import app.mountx.ui.theme.TerracottaRedLight
import app.mountx.ui.theme.WarmAmberContainerLight
import app.mountx.ui.theme.WarmAmberLight
import app.mountx.ui.theme.BadgeMigrationBg
import app.mountx.ui.theme.BadgeMigrationText
import app.mountx.ui.theme.BadgeMountedBgDark
import app.mountx.ui.theme.BadgeMountedBgLight
import app.mountx.ui.theme.BadgeMountedTextDark
import app.mountx.ui.theme.BadgeMountedTextLight
import app.mountx.ui.theme.OutlinedNeutralBgDark
import app.mountx.ui.theme.OutlinedNeutralBgLight
import app.mountx.ui.theme.WarmCrimson
import app.mountx.ui.theme.WarmCrimsonBorderDark
import app.mountx.ui.theme.WarmCrimsonBorderLight
import app.mountx.ui.theme.WarmCrimsonDark
import app.mountx.ui.theme.WarmCrimsonLight
import app.mountx.ui.theme.WarmStoneSlateDark
import app.mountx.ui.theme.WarmStoneSlateLight
import app.mountx.util.FormatUtils

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToGames: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    onOpenGameDetail: (GameEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val status by viewModel.appStatus.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isRecalculating by viewModel.isRecalculating.collectAsState()
    val isRefreshingTelemetry by viewModel.isRefreshingTelemetry.collectAsState()
    val games by viewModel.games.collectAsState()
    val allDisks by viewModel.allDisks.collectAsState()
    val internalStorageInfo by viewModel.internalStorageInfo.collectAsState()
    val offloadedStats by viewModel.offloadedStats.collectAsState()
    val liveTelemetry by viewModel.liveTelemetry.collectAsState()

    DashboardContent(
        status = status,
        isRefreshing = isRefreshing,
        isRecalculating = isRecalculating,
        isRefreshingTelemetry = isRefreshingTelemetry,
        games = games,
        allDisks = allDisks,
        internalStorageInfo = internalStorageInfo,
        offloadedStats = offloadedStats,
        liveTelemetry = liveTelemetry,
        onRefresh = { viewModel.refresh() },
        onNavigateToGames = onNavigateToGames,
        onNavigateToStorage = onNavigateToStorage,
        onNavigateToLogs = onNavigateToLogs,
        onOpenGameDetail = onOpenGameDetail,
        onMountAll = { viewModel.mountAll() },
        onUnmountAll = { viewModel.unmountAll() },
        onToggleGameMount = { viewModel.toggleMount(it) },
        onMigrateGame = { viewModel.migrateGame(it) },
        onRecalculateSizes = { viewModel.recalculateAllSizes() },
        onRefreshTelemetry = { viewModel.loadLiveTelemetry() },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    status: AppStatus,
    isRefreshing: Boolean,
    isRecalculating: Boolean = false,
    isRefreshingTelemetry: Boolean = false,
    games: List<GameEntry>,
    allDisks: List<SdCardDiskInfo> = emptyList(),
    internalStorageInfo: InternalStorageInfo? = null,
    offloadedStats: Pair<Int, Long> = Pair(0, 0L),
    liveTelemetry: LiveNamespaceTelemetry = LiveNamespaceTelemetry(),
    onRefresh: () -> Unit,
    onNavigateToGames: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onOpenGameDetail: (GameEntry) -> Unit = {},
    onMountAll: () -> Unit,
    onUnmountAll: () -> Unit,
    onToggleGameMount: (GameEntry) -> Unit,
    onMigrateGame: (GameEntry) -> Unit = {},
    onRecalculateSizes: () -> Unit = {},
    onRefreshTelemetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showNamespaceSheet by remember { mutableStateOf(false) }
    var showModuleInstallDialog by remember { mutableStateOf(false) }
    var gameToUnmountConfirm by remember { mutableStateOf<GameEntry?>(null) }
    var gameToMigrateConfirm by remember { mutableStateOf<GameEntry?>(null) }

    Scaffold(
        topBar = {
            SleekCompactHeader(
                status = status
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp)
                ) {
                    item {
                        ContextualAlertBanner(
                            status = status,
                            onInstallModuleClick = { showModuleInstallDialog = true }
                        )
                    }
                    item {
                        SmartMasterControlCard(
                            games = games,
                            mountedCount = status.mountedGamesCount,
                            totalCount = status.totalGamesCount,
                            onMountAll = onMountAll,
                            onUnmountAll = onUnmountAll,
                            onNavigateToGames = onNavigateToGames,
                            onOpenGameDetail = onOpenGameDetail,
                            onToggleGameMount = { game ->
                                if (game.mountStatus == MountStatus.MOUNTED) {
                                    gameToUnmountConfirm = game
                                } else if (game.mountStatus == MountStatus.NEED_MIGRATION) {
                                    gameToMigrateConfirm = game
                                } else {
                                    onToggleGameMount(game)
                                }
                            }
                        )
                    }
                    item {
                        DashboardTelemetryCard(
                            internalStorage = internalStorageInfo,
                            disks = allDisks,
                            offloadedStats = offloadedStats,
                            onNavigateToStorage = onNavigateToStorage,
                            onNavigateToGames = onNavigateToGames
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp)
                ) {
                    item {
                        DashboardMetricsRow(
                            games = games,
                            mountedCount = status.mountedGamesCount,
                            onOpenNamespaceSheet = { showNamespaceSheet = true }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ContextualAlertBanner(
                        status = status,
                        onInstallModuleClick = { showModuleInstallDialog = true }
                    )
                }
                item {
                    SmartMasterControlCard(
                        games = games,
                        mountedCount = status.mountedGamesCount,
                        totalCount = status.totalGamesCount,
                        onMountAll = onMountAll,
                        onUnmountAll = onUnmountAll,
                        onNavigateToGames = onNavigateToGames,
                        onOpenGameDetail = onOpenGameDetail,
                        onToggleGameMount = { game ->
                            if (game.mountStatus == MountStatus.MOUNTED) {
                                gameToUnmountConfirm = game
                            } else if (game.mountStatus == MountStatus.NEED_MIGRATION) {
                                gameToMigrateConfirm = game
                            } else {
                                onToggleGameMount(game)
                            }
                        }
                    )
                }
                item {
                    DashboardTelemetryCard(
                        internalStorage = internalStorageInfo,
                        disks = allDisks,
                        offloadedStats = offloadedStats,
                        onNavigateToStorage = onNavigateToStorage,
                        onNavigateToGames = onNavigateToGames
                    )
                }
                item {
                    DashboardMetricsRow(
                        games = games,
                        mountedCount = status.mountedGamesCount,
                        onOpenNamespaceSheet = { showNamespaceSheet = true }
                    )
                }
            }
        }
        }

        if (showNamespaceSheet) {
            NamespaceVerificationBottomSheet(
                telemetry = liveTelemetry,
                isRecalculating = isRecalculating,
                isRefreshingTelemetry = isRefreshingTelemetry,
                onRecalculateSizes = onRecalculateSizes,
                onRefreshTelemetry = onRefreshTelemetry,
                onDismiss = { showNamespaceSheet = false }
            )
        }

        if (showModuleInstallDialog) {
            ModuleInstallDialog(
                onDismiss = { showModuleInstallDialog = false },
                onModuleInstalledOrEnabled = {
                    onRefresh()
                }
            )
        }

        if (gameToUnmountConfirm != null) {
            val target = gameToUnmountConfirm!!
            ConfirmDialog(
                title = stringResource(R.string.unmount_confirm_title),
                message = stringResource(R.string.unmount_confirm_message),
                confirmText = stringResource(R.string.manage_btn_unmount_game),
                onConfirm = {
                    gameToUnmountConfirm = null
                    onToggleGameMount(target)
                },
                onDismiss = { gameToUnmountConfirm = null }
            )
        }

        if (gameToMigrateConfirm != null) {
            val target = gameToMigrateConfirm!!
            NeedMigrationDialog(
                game = target,
                onConfirmMigration = {
                    gameToMigrateConfirm = null
                    onMigrateGame(target)
                },
                onDismiss = { gameToMigrateConfirm = null }
            )
        }
    }
}

// ── 1. Modern Cyber Header Bar (~56dp height directly under status bar) ──

@Composable
private fun SleekCompactHeader(
    status: AppStatus
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Modern Cyber Brand Mark & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_mountx_emblem),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Right: Modern Status Pill (Clean without redundant refresh button)
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                val activeEmerald = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight
                val activeEmeraldGlow = if (isDark) BadgeMountedBgDark else BadgeMountedBgLight
                val noneCrimson = if (isDark) WarmCrimsonDark else WarmCrimsonLight
                val noneCrimsonGlow = if (isDark) WarmCrimsonBorderDark.copy(alpha = 0.3f) else WarmCrimsonBorderLight
                val (ledColor, ledGlow, engineLabel) = when (status.rootSolution) {
                    RootSolution.MAGISK -> Triple(activeEmerald, activeEmeraldGlow, stringResource(R.string.root_magisk))
                    RootSolution.KERNELSU -> Triple(activeEmerald, activeEmeraldGlow, stringResource(R.string.root_kernelsu))
                    RootSolution.APATCH -> Triple(activeEmerald, activeEmeraldGlow, stringResource(R.string.root_apatch))
                    RootSolution.NONE -> Triple(noneCrimson, noneCrimsonGlow, stringResource(R.string.root_none))
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ledGlow,
                    border = BorderStroke(1.dp, ledColor.copy(alpha = 0.45f)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(ledColor, CircleShape)
                        )
                        Text(
                            text = engineLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.Bold,
                            color = ledColor
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )
        }
    }
}

// ── 2. Contextual Warning / Error Banner (Zero clutter on normal state) ──

@Composable
private fun ContextualAlertBanner(
    status: AppStatus,
    onInstallModuleClick: () -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val errColor = if (isDark) NeonCrimson else TerracottaRedLight
    val errContainer = if (isDark) CrimsonGlow else TerracottaRedContainerLight
    val warnColor = if (isDark) SunsetAmber else WarmAmberLight
    val warnContainer = if (isDark) AmberGlow else WarmAmberContainerLight

    when {
        status.rootSolution == RootSolution.NONE -> {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = errContainer
                ),
                border = BorderStroke(1.dp, errColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = errColor.copy(alpha = 0.18f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = errColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dashboard_no_root),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = errColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.dashboard_no_root_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        !status.isModuleInstalled -> {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = warnContainer
                ),
                border = BorderStroke(1.dp, warnColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = warnColor.copy(alpha = 0.18f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = warnColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dashboard_module_not_installed),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = warnColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.dashboard_module_install_guide),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onInstallModuleClick,
                            colors = ButtonDefaults.buttonColors(containerColor = warnColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.dashboard_btn_install_module),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        else -> {
            // Normal happy path: zero banner clutter
        }
    }
}

// ── 3. Smart Adaptive Master Control Hero Card (Breathing Glow Pulse) ──

@Composable
private fun SmartMasterControlCard(
    games: List<GameEntry>,
    mountedCount: Int,
    totalCount: Int,
    onMountAll: () -> Unit,
    onUnmountAll: () -> Unit,
    onNavigateToGames: () -> Unit,
    onOpenGameDetail: (GameEntry) -> Unit = {},
    onToggleGameMount: (GameEntry) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val allMounted = totalCount > 0 && mountedCount == totalCount
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeEmerald = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight
    val warnAmber = BadgeMigrationText
    val errCrimson = if (isDark) WarmCrimsonDark else WarmCrimsonLight

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (allMounted) activeEmerald.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Category label + Status Pill Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_master_control).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (isDark) WarmStoneSlateDark else WarmStoneSlateLight
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        totalCount == 0 -> MaterialTheme.colorScheme.surfaceVariant
                        allMounted -> if (isDark) BadgeMountedBgDark else BadgeMountedBgLight
                        else -> if (isDark) Color(0xFF6366F1).copy(alpha = 0.15f) else Color(0xFF4F46E5).copy(alpha = 0.12f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            totalCount == 0 -> MaterialTheme.colorScheme.outlineVariant
                            allMounted -> if (isDark) BadgeMountedTextDark.copy(alpha = 0.4f) else BadgeMountedTextLight.copy(alpha = 0.35f)
                            else -> if (isDark) Color(0xFF6366F1).copy(alpha = 0.35f) else Color(0xFF4F46E5).copy(alpha = 0.3f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (allMounted) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(activeEmerald, CircleShape)
                            )
                        }
                        Text(
                            text = when {
                                totalCount == 0 -> "0 / 0"
                                allMounted -> stringResource(R.string.dashboard_active_count_format, mountedCount, totalCount)
                                else -> stringResource(R.string.dashboard_mounted_count_format, mountedCount, totalCount)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = when {
                                totalCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                allMounted -> activeEmerald
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Headline & Description
            Text(
                text = when {
                    totalCount == 0 -> stringResource(R.string.games_empty_title)
                    allMounted -> stringResource(R.string.dashboard_hero_all_mounted)
                    else -> stringResource(R.string.dashboard_mounted_games)
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = when {
                    totalCount == 0 -> stringResource(R.string.dashboard_hero_no_games_desc)
                    allMounted -> stringResource(R.string.dashboard_hero_all_mounted_desc)
                    else -> stringResource(R.string.dashboard_hero_unmounted_desc, mountedCount, totalCount)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            // Active / Managed Games Mini-List (up to 4 games)
            if (totalCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val previewGames = remember(games) {
                    games.sortedByDescending { it.mountStatus == MountStatus.MOUNTED }.take(4)
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (game in previewGames) {
                        val isMounted = game.mountStatus == MountStatus.MOUNTED
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isMounted) activeEmerald.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenGameDetail(game)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AppIconImage(
                                    packageName = game.packageName,
                                    size = 30.dp
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = game.displayName,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (game.dataSizeBytes > 0) {
                                            FormatUtils.formatBytes(game.dataSizeBytes)
                                        } else {
                                            game.packageName
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp
                                        ),
                                        color = when {
                                            isMounted -> activeEmerald
                                            game.mountStatus == MountStatus.NEED_MIGRATION -> warnAmber
                                            game.mountStatus == MountStatus.ERROR -> errCrimson
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                val isNeedMigration = game.mountStatus == MountStatus.NEED_MIGRATION
                                val isError = game.mountStatus == MountStatus.ERROR
                                val pillColor = when {
                                    isMounted -> activeEmerald
                                    isNeedMigration -> warnAmber
                                    isError -> errCrimson
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val pillBg = when {
                                    isMounted -> if (isDark) BadgeMountedBgDark else BadgeMountedBgLight
                                    isNeedMigration -> BadgeMigrationBg
                                    isError -> if (isDark) WarmCrimsonBorderDark.copy(alpha = 0.3f) else WarmCrimsonBorderLight
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                val pillBorder = when {
                                    isMounted -> if (isDark) BadgeMountedTextDark.copy(alpha = 0.4f) else BadgeMountedTextLight.copy(alpha = 0.35f)
                                    isNeedMigration -> BadgeMigrationText.copy(alpha = 0.4f)
                                    isError -> if (isDark) WarmCrimsonDark.copy(alpha = 0.4f) else WarmCrimsonBorderLight
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                                val pillText = when {
                                    isMounted -> stringResource(R.string.status_mounted)
                                    isNeedMigration -> stringResource(R.string.status_need_migration)
                                    isError -> stringResource(R.string.status_error)
                                    else -> stringResource(R.string.status_unmounted)
                                }

                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleGameMount(game)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = pillBg,
                                    border = BorderStroke(1.dp, pillBorder),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    if (isMounted || isNeedMigration || isError) pillColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    CircleShape
                                                )
                                        )
                                        Text(
                                            text = pillText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = pillColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Adaptive Master Button
            when {
                totalCount == 0 -> {
                    val buttonIndigo = if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5)
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToGames()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonIndigo,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.dashboard_hero_add_game_cta),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                allMounted -> {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUnmountAll()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isDark) OutlinedNeutralBgDark else OutlinedNeutralBgLight,
                            contentColor = if (isDark) WarmCrimsonDark else WarmCrimsonLight
                        ),
                        border = BorderStroke(1.5.dp, if (isDark) WarmCrimsonBorderDark else WarmCrimsonBorderLight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isDark) WarmCrimsonDark else WarmCrimsonLight
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dashboard_hero_unmount_all_cta),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isDark) WarmCrimsonDark else WarmCrimsonLight
                        )
                    }
                }

                else -> {
                    val buttonIndigo = if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5)
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMountAll()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonIndigo,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dashboard_hero_mount_all_cta),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // View All Games Link
            if (totalCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onNavigateToGames)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_view_all_games_count, totalCount),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ── 4. Dashboard Disk Telemetry Card (Internal + External Disks) ──

@Composable
private fun DashboardTelemetryCard(
    internalStorage: InternalStorageInfo?,
    disks: List<SdCardDiskInfo>,
    offloadedStats: Pair<Int, Long>,
    onNavigateToStorage: () -> Unit,
    onNavigateToGames: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToStorage)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.dashboard_disk_overview),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_manage_storage),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(10.dp))

            // Internal Storage Row
            if (internalStorage != null) {
                val intUsedRatio = internalStorage.usedPercent.coerceIn(0f, 1f)
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                DiskTelemetryRow(
                    icon = Icons.Default.PhoneAndroid,
                    iconTint = if (isDark) ElectricCyan else MaterialTheme.colorScheme.primary,
                    label = stringResource(R.string.dashboard_internal_storage),
                    subLabel = "/data",
                    usedRatio = intUsedRatio,
                    usedBytes = internalStorage.usedBytes,
                    totalBytes = internalStorage.totalBytes,
                    freeBytes = internalStorage.freeBytes
                )
                if (disks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // External Disks
            disks.forEachIndexed { idx, disk ->
                val diskUsedRatio = disk.usedPercent.coerceIn(0f, 1f)
                val diskIcon = when (disk.diskType) {
                    DiskType.USB_OTG -> Icons.Default.Usb
                    DiskType.MICRO_SD -> Icons.Default.SdCard
                    else -> Icons.Default.SdStorage
                }
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                val diskIconTint = when (disk.diskType) {
                    DiskType.USB_OTG -> if (isDark) SunsetAmber else WarmAmberLight
                    else -> if (isDark) CyberEmerald else ForestGreenLight
                }
                DiskTelemetryRow(
                    icon = diskIcon,
                    iconTint = diskIconTint,
                    label = disk.hardwareTitle,
                    subLabel = disk.devicePath,
                    usedRatio = diskUsedRatio,
                    usedBytes = disk.totalUsedBytes,
                    totalBytes = disk.totalSizeBytes,
                    freeBytes = disk.totalFreeBytes
                )
                if (idx < disks.size - 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Placeholder when no external disk
            if (internalStorage == null && disks.isEmpty()) {
                Text(
                    text = stringResource(R.string.storage_not_mounted),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Offloaded games footer
            if (offloadedStats.first > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToGames),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_games_offloaded_format, offloadedStats.first, FormatUtils.formatBytes(offloadedStats.second)),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = EmeraldActive
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = EmeraldActive,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiskTelemetryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    subLabel: String,
    usedRatio: Float,
    usedBytes: Long,
    totalBytes: Long,
    freeBytes: Long
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = iconTint.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, iconTint.copy(alpha = 0.20f)),
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (totalBytes > 0) stringResource(R.string.dashboard_free_suffix, FormatUtils.formatBytes(freeBytes)) else "—",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = if (totalBytes > 0) usedRatio else 0f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(FormatUtils.getHealthColor(usedRatio))
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (totalBytes > 0) "${(usedRatio * 100).toInt()}%" else "—",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = FormatUtils.getHealthColor(usedRatio)
                )
            }
        }
    }
}

// ── 5. Telemetry Metrics Row (Side-by-Side Rounded Cyber Surface Tiles) ──

@Composable
private fun DashboardMetricsRow(
    games: List<GameEntry>,
    mountedCount: Int,
    onOpenNamespaceSheet: () -> Unit = {}
) {
    val totalOffloadedBytes = games
        .filter { it.mountStatus == MountStatus.MOUNTED }
        .sumOf { it.dataSizeBytes }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeEmerald = if (isDark) CyberEmerald else ForestGreenLight

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tile 1: Offloaded Data (Clickable for details)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenNamespaceSheet() }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (totalOffloadedBytes > 0) FormatUtils.formatBytes(totalOffloadedBytes) else "0 B",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.dashboard_metric_offloaded),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }

        // Tile 2: Runtime Namespaces (Clickable for live kernel verification)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenNamespaceSheet() }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Surface(
                    shape = CircleShape,
                    color = if (mountedCount > 0) activeEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (mountedCount > 0) activeEmerald.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (mountedCount > 0) activeEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (mountedCount > 0) {
                        stringResource(R.string.dashboard_metric_namespaces_active_format, mountedCount)
                    } else {
                        stringResource(R.string.dashboard_metric_namespaces_standby_ready)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.dashboard_metric_namespaces),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NamespaceVerificationBottomSheet(
    telemetry: LiveNamespaceTelemetry,
    isRecalculating: Boolean = false,
    isRefreshingTelemetry: Boolean = false,
    onRecalculateSizes: () -> Unit,
    onRefreshTelemetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeEmerald = if (isDark) CyberEmerald else ForestGreenLight

    val infiniteTransition = rememberInfiniteTransition(label = "sheet_spin_anim")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sheet_spin"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(activeEmerald.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = activeEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.dashboard_live_verification_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onRefreshTelemetry,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.dashboard_refresh),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                if (isRefreshingTelemetry) rotationZ = spinAngle
                            }
                    )
                }
            }

            // Card 1: Master Mount Namespace Status
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, activeEmerald.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(activeEmerald, CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dashboard_master_namespace_ready),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.dashboard_master_namespace_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // Card 2: Kernel /proc/mounts Live Bind Mounts (Expandable Accordion)
            var isMountListExpanded by remember { mutableStateOf(false) }
            val clipboardManager = LocalClipboardManager.current
            val haptic = LocalHapticFeedback.current
            val context = LocalContext.current

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (telemetry.kernelMountPoints.isNotEmpty()) {
                                stringResource(R.string.dashboard_live_mounts_found, telemetry.kernelMountPoints.size)
                            } else {
                                stringResource(R.string.dashboard_live_mounts_none)
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (telemetry.kernelMountPoints.isNotEmpty()) activeEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (telemetry.kernelMountPoints.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val summary = telemetry.kernelMountPoints.joinToString("\n")
                                    clipboardManager.setText(AnnotatedString(summary))
                                    android.widget.Toast.makeText(context, context.getString(R.string.dashboard_mount_points_copied), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.dashboard_mount_points_copy),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (telemetry.kernelMountPoints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))

                        val displayMounts = if (isMountListExpanded) {
                            telemetry.kernelMountPoints
                        } else {
                            telemetry.kernelMountPoints.take(4)
                        }

                        Box(
                            modifier = if (isMountListExpanded) {
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                for (mount in displayMounts) {
                                    Text(
                                        text = mount,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        maxLines = if (isMountListExpanded) 2 else 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (telemetry.kernelMountPoints.size > 4) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isMountListExpanded) {
                                    stringResource(R.string.dashboard_mount_points_collapse)
                                } else {
                                    stringResource(R.string.dashboard_mount_points_expand, telemetry.kernelMountPoints.size)
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isMountListExpanded = !isMountListExpanded
                                    }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Card 3: Canary Integrity
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (telemetry.canaryVerifiedCount > 0) activeEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (telemetry.canaryVerifiedCount > 0) {
                            "${stringResource(R.string.dashboard_live_canary_verified)} (${telemetry.canaryVerifiedCount}/${telemetry.totalCanariesExpected})"
                        } else {
                            stringResource(R.string.dashboard_live_canary_not_verified)
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRecalculateSizes,
                    enabled = !isRecalculating,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer {
                                if (isRecalculating) rotationZ = spinAngle
                            }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRecalculating) stringResource(R.string.dashboard_recalculating_size_btn)
                               else stringResource(R.string.dashboard_recalculate_size_btn),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_close),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ── Previews ──

@Preview(
    name = "Portrait 1080x2460",
    device = "spec:width=1080px,height=2460px,dpi=440",
    showBackground = true
)
@Preview(
    name = "Dark Mode 1080x2460",
    device = "spec:width=1080px,height=2460px,dpi=440",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Preview(
    name = "Landscape 2460x1080",
    device = "spec:width=2460px,height=1080px,dpi=440",
    showBackground = true
)
@Composable
fun DashboardScreenPreview() {
    MountXTheme(dynamicColor = false) {
        DashboardContent(
            status = AppStatus(
                rootSolution = RootSolution.MAGISK,
                isModuleInstalled = true,
                moduleVersion = "2.1.9",
                mountedGamesCount = 5,
                totalGamesCount = 8,
                storageInfo = StorageInfo(
                    blockDevice = "/dev/block/mmcblk1p2",
                    mountPoint = "/data/sdext2",
                    filesystem = "ext4",
                    totalBytes = 128L * 1024 * 1024 * 1024,
                    usedBytes = 80L * 1024 * 1024 * 1024,
                    freeBytes = 48L * 1024 * 1024 * 1024,
                    isMounted = true
                )
            ),
            isRefreshing = false,
            games = listOf(
                GameEntry(
                    packageName = "com.miHoYo.GenshinImpact",
                    displayName = "Genshin Impact",
                    mode = MountMode.FILES,
                    mountStatus = MountStatus.MOUNTED,
                    dataSizeBytes = 28L * 1024 * 1024 * 1024
                ),
                GameEntry(
                    packageName = "com.kurogame.wutheringwaves.global",
                    displayName = "Wuthering Waves",
                    mode = MountMode.PKG,
                    mountStatus = MountStatus.MOUNTED,
                    dataSizeBytes = 24L * 1024 * 1024 * 1024
                ),
                GameEntry(
                    packageName = "com.HoYoverse.hkrpgoversea",
                    displayName = "Honkai: Star Rail",
                    mode = MountMode.FILES,
                    mountStatus = MountStatus.UNMOUNTED,
                    dataSizeBytes = 20L * 1024 * 1024 * 1024
                )
            ),
            onRefresh = {},
            onNavigateToGames = {},
            onNavigateToStorage = {},
            onNavigateToLogs = {},
            onMountAll = {},
            onUnmountAll = {},
            onToggleGameMount = {}
        )
    }
}
