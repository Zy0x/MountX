package app.mountify.ui.dashboard

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountify.R
import app.mountify.data.model.*
import app.mountify.ui.components.ErrorCard
import app.mountify.ui.components.SectionHeader
import app.mountify.ui.theme.*
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

@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )

                        // Engine Status Pill
                        val (engineColor, engineText) = when {
                            status.rootSolution == RootSolution.NONE -> Pair(MaterialTheme.colorScheme.error, stringResource(R.string.root_none))
                            status.isModuleInstalled -> Pair(SecondaryTeal, if (status.moduleVersion.isNotBlank()) "v${status.moduleVersion}" else "Active")
                            else -> Pair(StatusWarning, "No Module")
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = engineColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, engineColor.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(engineColor, CircleShape)
                                )
                                Text(
                                    text = engineText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = engineColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.dashboard_refresh),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (isLandscape) {
            // Two-column layout for wide displays
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Telemetry, Storage & Quick Actions
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (status.rootSolution == RootSolution.NONE) {
                        item {
                            ErrorCard(
                                title = stringResource(R.string.dashboard_no_root),
                                description = stringResource(R.string.dashboard_no_root_desc)
                            )
                        }
                    }

                    item {
                        TelemetryCards(status = status)
                    }

                    item {
                        MicroSdStorageCard(
                            storage = status.storageInfo,
                            onNavigateToStorage = onNavigateToStorage
                        )
                    }

                    item {
                        QuickActionControls(
                            onMountAll = onMountAll,
                            onUnmountAll = onUnmountAll
                        )
                    }
                }

                // Right Column: Managed Games & Engine Log Preview
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        ManagedGamesSection(
                            games = games,
                            onNavigateToGames = onNavigateToGames,
                            onToggleGameMount = onToggleGameMount
                        )
                    }

                    item {
                        EngineLogTerminalBox(onNavigateToLogs = onNavigateToLogs)
                    }
                }
            }
        } else {
            // Single-column mobile-first vertical layout
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Root Alert Banner
                if (status.rootSolution == RootSolution.NONE) {
                    item {
                        ErrorCard(
                            title = stringResource(R.string.dashboard_no_root),
                            description = stringResource(R.string.dashboard_no_root_desc)
                        )
                    }
                }

                // Module Alert Banner
                if (!status.isModuleInstalled && status.rootSolution != RootSolution.NONE) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryTeal.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = SecondaryTeal,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.dashboard_module_not_installed),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.dashboard_module_install_guide),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 1: KPI Telemetry Grid
                item {
                    TelemetryCards(status = status)
                }

                // Section 2: Storage Gauge Card
                item {
                    MicroSdStorageCard(
                        storage = status.storageInfo,
                        onNavigateToStorage = onNavigateToStorage
                    )
                }

                // Section 3: Quick Action Command Center
                item {
                    QuickActionControls(
                        onMountAll = onMountAll,
                        onUnmountAll = onUnmountAll
                    )
                }

                // Section 4: Managed Games Summary
                item {
                    ManagedGamesSection(
                        games = games,
                        onNavigateToGames = onNavigateToGames,
                        onToggleGameMount = onToggleGameMount
                    )
                }

                // Section 5: Engine Activity Log Preview
                item {
                    EngineLogTerminalBox(onNavigateToLogs = onNavigateToLogs)
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

// ── Component: Telemetry Cards ──

@Composable
private fun TelemetryCards(status: AppStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Root Engine Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROOT ENGINE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 0.8.sp
                    )
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PrimaryBlue
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val rootText = when (status.rootSolution) {
                    RootSolution.MAGISK -> stringResource(R.string.root_magisk)
                    RootSolution.KERNELSU -> stringResource(R.string.root_kernelsu)
                    RootSolution.APATCH -> stringResource(R.string.root_apatch)
                    RootSolution.NONE -> stringResource(R.string.root_none)
                }
                Text(
                    text = rootText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (status.rootSolution != RootSolution.NONE) PrimaryBlue else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (status.rootSolution != RootSolution.NONE) "SELinux: Enforcing" else "Access Restricted",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = if (status.rootSolution != RootSolution.NONE) SecondaryTeal else MaterialTheme.colorScheme.error
                )
            }
        }

        // Active Mounts Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE MOUNTS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 0.8.sp
                    )
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = SecondaryTeal
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${status.mountedGamesCount} / ${status.totalGamesCount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryTeal
                )
                Spacer(modifier = Modifier.height(2.dp))
                val percentage = if (status.totalGamesCount > 0) {
                    (status.mountedGamesCount * 100) / status.totalGamesCount
                } else 0
                Text(
                    text = "$percentage% of library bound",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ── Component: MicroSD Storage Card ──

@Composable
private fun MicroSdStorageCard(
    storage: StorageInfo?,
    onNavigateToStorage: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToStorage)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.dashboard_storage_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (storage != null && storage.isMounted) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SecondaryTeal.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryTeal.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = storage.filesystem.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryTeal,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (storage != null && storage.isMounted) {
                // Device Paths Chip
                Text(
                    text = "${storage.blockDevice}  ->  ${storage.mountPoint}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // High-precision Gauge Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(storage.usedPercent.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(PrimaryBlue, SecondaryTeal)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Usage Metrics Breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Used: ${FormatUtils.formatBytes(storage.usedBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${(storage.usedPercent * 100).toInt()}% Capacity",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Free: ${FormatUtils.formatBytes(storage.freeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SecondaryTeal
                        )
                        Text(
                            text = "Total: ${FormatUtils.formatBytes(storage.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.dashboard_storage_not_mounted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = if (storage?.isMounted == true) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (storage?.isMounted == true) StatusSuccess else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (storage?.isMounted == true) "Partition active" else "Storage unmounted",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (storage?.isMounted == true) StatusSuccess else MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Manage",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PrimaryBlue
                    )
                }
            }
        }
    }
}

// ── Component: Quick Action Controls ──

@Composable
private fun QuickActionControls(
    onMountAll: () -> Unit,
    onUnmountAll: () -> Unit
) {
    Column {
        SectionHeader(title = stringResource(R.string.dashboard_quick_actions))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onMountAll,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.dashboard_mount_all),
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onUnmountAll,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.dashboard_unmount_all),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Component: Managed Games Section ──

@Composable
private fun ManagedGamesSection(
    games: List<GameEntry>,
    onNavigateToGames: () -> Unit,
    onToggleGameMount: (GameEntry) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title = "Managed Game Mounts")
            TextButton(
                onClick = onNavigateToGames,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text(
                    text = "View All (${games.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PrimaryBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (games.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToGames)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "No Games Registered",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap here to browse installed games and set up bind mounts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Show top 3 games in summary
                games.take(3).forEach { game ->
                    GameMountSummaryItem(
                        game = game,
                        onToggle = { onToggleGameMount(game) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameMountSummaryItem(
    game: GameEntry,
    onToggle: () -> Unit
) {
    val isMounted = game.mountStatus == MountStatus.MOUNTED

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isMounted) SecondaryTeal.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Game Initial Avatar Badge
            val initial = game.displayName.firstOrNull()?.uppercase() ?: "G"
            val badgeGradient = when (game.mode) {
                MountMode.FILES -> Brush.linearGradient(listOf(SecondaryTeal, PrimaryBlue))
                MountMode.PKG -> Brush.linearGradient(listOf(PrimaryBlue, PrimaryBlueDark))
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(badgeGradient, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Game Meta
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Mode Tag
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (game.mode == MountMode.FILES) SecondaryTeal.copy(alpha = 0.15f) else PrimaryBlue.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = game.mode.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (game.mode == MountMode.FILES) SecondaryTeal else PrimaryBlue,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }

                    if (game.dataSizeBytes > 0) {
                        Text(
                            text = FormatUtils.formatBytes(game.dataSizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Mount Switch Toggle
            Switch(
                checked = isMounted,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SecondaryTeal,
                    uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            )
        }
    }
}

// ── Component: Engine Activity Log Terminal ──

@Composable
private fun EngineLogTerminalBox(onNavigateToLogs: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title = "Engine Activity Log")
            TextButton(
                onClick = onNavigateToLogs,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text(
                    text = "View Logs",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PrimaryBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0A0D15),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2336)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToLogs)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Console Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF5F56), CircleShape))
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFBD2E), CircleShape))
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF27C93F), CircleShape))
                    }
                    Text(
                        text = "mountify.log - live stream",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF6B7280)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Console Output Lines
                Text(
                    text = "[12:00:15] [INFO] Ext4 storage mounted on /data/sdext2",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF60A5FA)
                )
                Text(
                    text = "[12:00:16] [SUCCESS] Bind mounted com.kurogame.wutheringwaves.global (PKG)",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF34D399)
                )
                Text(
                    text = "[12:00:17] [SUCCESS] SELinux context applied: u:object_r:media_rw_data_file:s0",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF34D399)
                )
                Text(
                    text = "[12:00:18] [READY] 5 game namespaces active and ready",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFA78BFA)
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
                moduleVersion = "2.1.5",
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
