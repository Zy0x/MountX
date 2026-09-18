package app.mountify.ui.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountify.R
import app.mountify.data.model.AppStatus
import app.mountify.data.model.GameEntry
import app.mountify.data.model.MountMode
import app.mountify.data.model.MountStatus
import app.mountify.data.model.RootSolution
import app.mountify.data.model.StorageInfo
import app.mountify.ui.theme.AmberGlow
import app.mountify.ui.theme.AmberWarn
import app.mountify.ui.theme.AuroraGradientBrush
import app.mountify.ui.theme.AuroraGradientBrushLight
import app.mountify.ui.theme.CoralError
import app.mountify.ui.theme.CrimsonGlow
import app.mountify.ui.theme.CyberEmerald
import app.mountify.ui.theme.ElectricCyan
import app.mountify.ui.theme.ElectricCyanBright
import app.mountify.ui.theme.ElectricIndigo
import app.mountify.ui.theme.EmeraldActive
import app.mountify.ui.theme.EmeraldGlow
import app.mountify.ui.theme.HyperCyan
import app.mountify.ui.theme.HyperCyanBright
import app.mountify.ui.theme.MountifyTheme
import app.mountify.ui.theme.NeonCrimson
import app.mountify.ui.theme.StorageGradientBrush
import app.mountify.ui.theme.SunsetAmber
import app.mountify.util.FormatUtils

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToGames: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val status by viewModel.appStatus.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val games by viewModel.games.collectAsState()

    DashboardContent(
        status = status,
        isRefreshing = isRefreshing,
        games = games,
        onRefresh = { viewModel.refresh() },
        onNavigateToGames = onNavigateToGames,
        onNavigateToStorage = onNavigateToStorage,
        onNavigateToLogs = onNavigateToLogs,
        onMountAll = { viewModel.mountAll() },
        onUnmountAll = { viewModel.unmountAll() },
        onToggleGameMount = { viewModel.toggleMount(it) },
        modifier = modifier
    )
}

@Composable
fun DashboardContent(
    status: AppStatus,
    isRefreshing: Boolean,
    games: List<GameEntry>,
    onRefresh: () -> Unit,
    onNavigateToGames: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onMountAll: () -> Unit,
    onUnmountAll: () -> Unit,
    onToggleGameMount: (GameEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            SleekCompactHeader(
                status = status,
                onRefresh = onRefresh
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        ContextualAlertBanner(status = status)
                    }
                    item {
                        SmartMasterControlCard(
                            games = games,
                            mountedCount = status.mountedGamesCount,
                            totalCount = status.totalGamesCount,
                            onMountAll = onMountAll,
                            onUnmountAll = onUnmountAll,
                            onNavigateToGames = onNavigateToGames
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        M3StorageCard(
                            storage = status.storageInfo,
                            onNavigateToStorage = onNavigateToStorage
                        )
                    }
                    item {
                        DashboardMetricsRow(
                            games = games,
                            mountedCount = status.mountedGamesCount
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ContextualAlertBanner(status = status)
                }
                item {
                    SmartMasterControlCard(
                        games = games,
                        mountedCount = status.mountedGamesCount,
                        totalCount = status.totalGamesCount,
                        onMountAll = onMountAll,
                        onUnmountAll = onUnmountAll,
                        onNavigateToGames = onNavigateToGames
                    )
                }
                item {
                    M3StorageCard(
                        storage = status.storageInfo,
                        onNavigateToStorage = onNavigateToStorage
                    )
                }
                item {
                    DashboardMetricsRow(
                        games = games,
                        mountedCount = status.mountedGamesCount
                    )
                }
            }
        }
    }
}

// ── 1. Sleek Compact Header Bar (~48dp height directly under status bar) ──

@Composable
private fun SleekCompactHeader(
    status: AppStatus,
    onRefresh: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Minimalist Brand Mark & Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SdStorage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Right: Micro Status Pill & Tactile Refresh Action
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val (ledColor, ledGlow, engineLabel) = when (status.rootSolution) {
                RootSolution.MAGISK -> Triple(CyberEmerald, EmeraldGlow, stringResource(R.string.root_magisk))
                RootSolution.KERNELSU -> Triple(CyberEmerald, EmeraldGlow, stringResource(R.string.root_kernelsu))
                RootSolution.APATCH -> Triple(CyberEmerald, EmeraldGlow, stringResource(R.string.root_apatch))
                RootSolution.NONE -> Triple(NeonCrimson, CrimsonGlow, stringResource(R.string.root_none))
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ledGlow,
                border = BorderStroke(1.dp, ledColor.copy(alpha = 0.45f)),
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(ledColor, CircleShape)
                    )
                    Text(
                        text = engineLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = ledColor
                    )
                }
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRefresh()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.dashboard_refresh),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── 2. Contextual Warning / Error Banner (Zero clutter on normal state) ──

@Composable
private fun ContextualAlertBanner(status: AppStatus) {
    when {
        status.rootSolution == RootSolution.NONE -> {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CrimsonGlow
                ),
                border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = NeonCrimson.copy(alpha = 0.18f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NeonCrimson,
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
                            color = NeonCrimson
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
                    containerColor = AmberGlow
                ),
                border = BorderStroke(1.dp, SunsetAmber.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SunsetAmber.copy(alpha = 0.18f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = SunsetAmber,
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
                            color = SunsetAmber
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.dashboard_module_install_guide),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
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
    onNavigateToGames: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val allMounted = totalCount > 0 && mountedCount == totalCount

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (allMounted) CyberEmerald.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
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
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        totalCount == 0 -> MaterialTheme.colorScheme.surfaceVariant
                        allMounted -> CyberEmerald.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            totalCount == 0 -> MaterialTheme.colorScheme.outlineVariant
                            allMounted -> CyberEmerald.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
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
                                    .background(CyberEmerald, CircleShape)
                            )
                        }
                        Text(
                            text = when {
                                totalCount == 0 -> "0 / 0"
                                allMounted -> "$mountedCount / $totalCount Active"
                                else -> "$mountedCount / $totalCount Mounted"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = when {
                                totalCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                allMounted -> CyberEmerald
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

            Spacer(modifier = Modifier.height(12.dp))

            // Adaptive Master Button
            when {
                totalCount == 0 -> {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToGames()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(
                                brush = AuroraGradientBrush,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
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
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUnmountAll()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = NeonCrimson
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dashboard_hero_unmount_all_cta),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                else -> {
                    val isDark = isSystemInDarkTheme()
                    val gradient = if (isDark) AuroraGradientBrush else AuroraGradientBrushLight
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMountAll()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(gradient, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
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
        }
    }
}

// ── 4. MicroSD Storage Card (Electric Gradient Gauge) ──

@Composable
private fun M3StorageCard(
    storage: StorageInfo?,
    onNavigateToStorage: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToStorage)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dashboard_storage_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (storage != null && storage.isMounted) storage.mountPoint else stringResource(R.string.dashboard_storage_not_mounted),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }

                if (storage != null && storage.isMounted && storage.filesystem.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = storage.filesystem.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (storage != null && storage.isMounted && storage.totalBytes > 0L) {
                val usedRatio = (storage.usedBytes.toFloat() / storage.totalBytes.toFloat()).coerceIn(0f, 1f)

                // Cyber Aurora Gradient Storage Gauge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = usedRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(FormatUtils.getHealthColor(usedRatio))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.dashboard_storage_used_format,
                            FormatUtils.formatBytes(storage.usedBytes),
                            FormatUtils.formatBytes(storage.totalBytes)
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = stringResource(
                            R.string.dashboard_storage_free_format,
                            FormatUtils.formatBytes(storage.freeBytes)
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.storage_not_mounted),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
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
            }
        }
    }
}

// ── 5. Telemetry Metrics Row (Side-by-Side Rounded Cyber Surface Tiles) ──

@Composable
private fun DashboardMetricsRow(
    games: List<GameEntry>,
    mountedCount: Int
) {
    val totalOffloadedBytes = games
        .filter { it.mountStatus == MountStatus.MOUNTED }
        .sumOf { it.dataSizeBytes }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tile 1: Offloaded Data
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.weight(1f)
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.dashboard_metric_offloaded),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }

        // Tile 2: Runtime Namespaces
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Surface(
                    shape = CircleShape,
                    color = if (mountedCount > 0) CyberEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (mountedCount > 0) CyberEmerald.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (mountedCount > 0) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (mountedCount > 0) {
                        stringResource(R.string.dashboard_metric_namespaces_active)
                    } else {
                        stringResource(R.string.dashboard_metric_namespaces_idle)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
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
    MountifyTheme(dynamicColor = false) {
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
