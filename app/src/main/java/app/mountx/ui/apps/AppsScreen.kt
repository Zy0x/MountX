package app.mountx.ui.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountx.R
import app.mountx.data.catalog.DiscoveredGame
import app.mountx.data.model.AppEntry
import app.mountx.data.model.GameEntry
import app.mountx.data.model.InstalledAppInfo
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountStatus
import app.mountx.ui.components.AppIconImage
import app.mountx.ui.components.CompactScreenHeader
import app.mountx.ui.components.ConfirmDialog
import app.mountx.ui.components.NeedMigrationDialog
import app.mountx.ui.theme.AuroraGradientBrush
import app.mountx.ui.theme.CyberEmerald
import app.mountx.ui.theme.ElectricIndigoLight
import app.mountx.ui.theme.NeonCrimson
import app.mountx.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    viewModel: AppsViewModel,
    modifier: Modifier = Modifier,
    isBottomBarVisible: Boolean = true,
    onPagerScrollEnabled: (Boolean) -> Unit = {},
    onBottomBarVisibilityChanged: (Boolean) -> Unit = {}
) = AppsScreen(
    viewModel = viewModel,
    modifier = modifier,
    isBottomBarVisible = isBottomBarVisible,
    onPagerScrollEnabled = onPagerScrollEnabled,
    onBottomBarVisibilityChanged = onBottomBarVisibilityChanged
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    viewModel: AppsViewModel,
    modifier: Modifier = Modifier,
    isBottomBarVisible: Boolean = true,
    onPagerScrollEnabled: (Boolean) -> Unit = {},
    onBottomBarVisibilityChanged: (Boolean) -> Unit = {}
) {
    val games by viewModel.games.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val discoveredGames by viewModel.discoveredGames.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val detailedStorage by viewModel.detailedStorage.collectAsState()
    val isMovingData by viewModel.isMovingData.collectAsState()
    val moveMessage by viewModel.moveMessage.collectAsState()
    val availableDisks by viewModel.availableDisks.collectAsState()
    val isScanningDisks by viewModel.isScanningDisks.collectAsState()
    val internalStorageInfo by viewModel.internalStorageInfo.collectAsState()
    val operationProgress by viewModel.operationProgress.collectAsState()
    val isRestructuring by viewModel.isRestructuring.collectAsState()
    val restructureProgressMessage by viewModel.restructureProgressMessage.collectAsState()
    var showRestructureDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.scanDiscoveredGames()
    }

    val vmSelectedGame by viewModel.selectedGameForDetail.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedGameForDetail by remember { mutableStateOf<GameEntry?>(null) }
    var gameToDelete by remember { mutableStateOf<GameEntry?>(null) }
    var configuringApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    LaunchedEffect(vmSelectedGame) {
        if (vmSelectedGame != null) {
            selectedGameForDetail = vmSelectedGame
        }
    }

    LaunchedEffect(showAddSheet, selectedGameForDetail, configuringApp) {
        val isPickerOrDetail = showAddSheet || selectedGameForDetail != null || configuringApp != null
        onPagerScrollEnabled(!isPickerOrDetail)
        onBottomBarVisibilityChanged(!isPickerOrDetail)
    }

    if (configuringApp != null) {
        val app = configuringApp!!
        val candidateDirectories by viewModel.candidateDirectories.collectAsState()
        val isScanningCandidates by viewModel.isScanningCandidates.collectAsState()

        LaunchedEffect(app.packageName) {
            viewModel.scanCandidates(app.packageName, app.displayName)
            viewModel.loadStorageBreakdown(app.packageName)
        }

        val draftGame = remember(app.packageName) {
            GameEntry(
                packageName = app.packageName,
                displayName = app.displayName,
                mountStatus = MountStatus.UNMOUNTED
            )
        }

        LaunchedEffect(configuringApp?.packageName) {
            val pkg = configuringApp?.packageName
            if (pkg != null) {
                viewModel.loadStorageBreakdown(pkg)
            }
        }

        GameDetailView(
            game = draftGame,
            breakdown = detailedStorage,
            isMoving = false,
            moveMessage = null,
            isDraftMode = true,
            candidateDirectories = candidateDirectories,
            isLoadingCandidates = isScanningCandidates,
            availableDisks = availableDisks,
            internalFreeBytes = internalStorageInfo?.freeBytes ?: 0L,
            isScanningDisks = isScanningDisks,
            onQuickMountDisk = { disk -> viewModel.quickMountDisk(disk) },
            onQuickMountPartition = { part -> viewModel.quickMountPartition(part) },
            onRefreshDisks = { viewModel.refreshDisks() },
            onDismiss = {
                viewModel.clearDetailedStorage()
                configuringApp = null
            },
            onSaveGame = { newGame ->
                viewModel.addGameWithMountPoints(
                    packageName = newGame.packageName,
                    displayName = newGame.displayName,
                    mountPoints = newGame.mountPoints,
                    initialSizeBytes = newGame.dataSizeBytes
                )
                configuringApp = null
            },
            onDeleteCategoryData = { catId, loc, callback ->
                viewModel.deleteCategoryData(draftGame.packageName, catId, loc, callback)
            },
            modifier = modifier
        )
    } else if (showAddSheet) {
        AddAppPicker(
            installedApps = installedApps,
            addedPackageNames = games.map { it.packageName }.toSet(),
            onDismiss = { showAddSheet = false },
            onAdd = { pkg, name, mode ->
                viewModel.addGame(pkg, name, mode)
                showAddSheet = false
            },
            onConfigureApp = { appInfo ->
                showAddSheet = false
                configuringApp = appInfo
            },
            modifier = modifier
        )
    } else if (selectedGameForDetail != null) {
        val game = selectedGameForDetail!!
        val updatedGame = games.firstOrNull { it.packageName == game.packageName } ?: game

        LaunchedEffect(updatedGame.packageName) {
            viewModel.loadStorageBreakdown(updatedGame.packageName)
        }

        GameDetailView(
            game = updatedGame,
            breakdown = detailedStorage,
            isMoving = isMovingData,
            moveMessage = moveMessage,
            onClearMoveMessage = { viewModel.clearMoveMessage() },
            isDraftMode = false,
            availableDisks = availableDisks,
            internalFreeBytes = internalStorageInfo?.freeBytes ?: 0L,
            isScanningDisks = isScanningDisks,
            onQuickMountDisk = { disk -> viewModel.quickMountDisk(disk) },
            onQuickMountPartition = { part -> viewModel.quickMountPartition(part) },
            onRefreshDisks = { viewModel.refreshDisks() },
            onDismiss = {
                viewModel.clearMoveMessage()
                viewModel.clearDetailedStorage()
                selectedGameForDetail = null
                viewModel.selectGameForDetail(null)
            },
            onMount = { viewModel.mountGame(updatedGame) },
            onUnmount = { viewModel.unmountGame(updatedGame) },
            onToggleMount = { viewModel.toggleMount(updatedGame) },
            onMoveMountPoints = { dir, pts, targetDisk, targetPartition, conflictStrategy ->
                val basePath = targetPartition?.mountPoint ?: targetDisk?.mountPath
                viewModel.moveMountPoints(updatedGame.packageName, pts, dir, basePath, conflictStrategy)
            },
            onUpdateMountPoints = { pts ->
                viewModel.updateMountPoints(updatedGame.packageName, pts)
            },
            onDelete = {
                gameToDelete = updatedGame
                selectedGameForDetail = null
                viewModel.selectGameForDetail(null)
            },
            onDeleteCategoryData = { catId, loc, callback ->
                viewModel.deleteCategoryData(updatedGame.packageName, catId, loc, callback)
            },
            modifier = modifier
        )
    } else {
        GamesContent(
            games = games,
            isRefreshing = isRefreshing,
            isBottomBarVisible = isBottomBarVisible,
            onRefresh = { viewModel.refresh() },
            discoveredGames = discoveredGames,
            onImportAllDiscovered = { viewModel.importAllDiscoveredGames() },
            onDismissDiscovered = { viewModel.dismissDiscovered() },
            onRestructureDiscovered = { showRestructureDialog = true },
            searchQuery = searchQuery,
            filterStatus = filterStatus,
            sortOption = sortOption,
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            onFilterStatusChange = { viewModel.setFilterStatus(it) },
            onSortOptionChange = { viewModel.setSortOption(it) },
            onAddClick = {
                viewModel.loadInstalledApps()
                showAddSheet = true
            },
            onToggleMount = { viewModel.toggleMount(it) },
            onMigrateGame = { viewModel.moveMountPoints(it.packageName, it.mountPoints, app.mountx.data.model.MoveDirection.TO_SD) },
            onMountAll = { viewModel.mountAllGames() },
            onUnmountAll = { viewModel.unmountAllGames() },
            onSelectGameForDetail = { game ->
                selectedGameForDetail = game
            },
            onSelectGameForDelete = { game ->
                gameToDelete = game
            },
            modifier = modifier
        )
    }

    // Delete Confirmation Dialog
    val isRestoring by viewModel.isRestoring.collectAsState()
    val restoreProgress by viewModel.restoreProgress.collectAsState()
    val restoreMessage by viewModel.restoreMessage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    gameToDelete?.let { game ->
        DeleteAppConfirmDialog(
            appName = game.displayName.ifBlank { game.packageName },
            packageName = game.packageName,
            requiredRestoreBytes = game.dataSizeBytes,
            internalFreeBytes = android.os.Environment.getDataDirectory().freeSpace,
            isRestoring = isRestoring,
            restoreProgress = restoreProgress,
            restoreMessage = restoreMessage,
            onConfirmRestoreAndDelete = {
                viewModel.removeGameWithOption(context, game.packageName, restoreToInternal = true)
                gameToDelete = null
            },
            onConfirmUnmountAndDelete = {
                viewModel.removeGameWithOption(context, game.packageName, restoreToInternal = false)
                gameToDelete = null
            },
            onDismiss = { if (!isRestoring) gameToDelete = null }
        )
    }

    // Real-time Operation Progress Dialog (Mount, Unmount, Move to SD, Restore to Internal)
    operationProgress?.let { prog ->
        OperationProgressDialog(
            progress = prog,
            onDismiss = {
                viewModel.clearOperationProgress()
                if (prog.isSuccess && (prog.type == app.mountx.data.model.OperationType.MOVE_TO_SD || prog.type == app.mountx.data.model.OperationType.RESTORE_TO_INTERNAL)) {
                    viewModel.clearMoveMessage()
                }
            }
        )
    }

    if (showRestructureDialog) {
        val needsRestructureList = discoveredGames.filter { it.needsRestructure }
        SmartRestructureDialog(
            gamesToRestructure = needsRestructureList,
            isRestructuring = isRestructuring,
            progressMessage = restructureProgressMessage,
            onConfirm = {
                viewModel.restructureAllGames(needsRestructureList) {
                    showRestructureDialog = false
                }
            },
            onDismiss = { showRestructureDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesContent(
    games: List<GameEntry>,
    searchQuery: String,
    filterStatus: GameFilterStatus,
    sortOption: GameSortOption,
    isRefreshing: Boolean = false,
    isBottomBarVisible: Boolean = true,
    onRefresh: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onFilterStatusChange: (GameFilterStatus) -> Unit,
    onSortOptionChange: (GameSortOption) -> Unit,
    onAddClick: () -> Unit,
    onToggleMount: (GameEntry) -> Unit,
    onMigrateGame: (GameEntry) -> Unit = {},
    onMountAll: () -> Unit,
    onUnmountAll: () -> Unit,
    onSelectGameForDetail: (GameEntry) -> Unit,
    onSelectGameForDelete: (GameEntry) -> Unit = {},
    modifier: Modifier = Modifier,
    discoveredGames: List<DiscoveredGame> = emptyList(),
    onImportAllDiscovered: () -> Unit = {},
    onDismissDiscovered: () -> Unit = {},
    onRestructureDiscovered: () -> Unit = {}
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var gameToUnmount by remember { mutableStateOf<GameEntry?>(null) }
    var gameForNeedMigration by remember { mutableStateOf<GameEntry?>(null) }

    val mountedCount = games.count { it.mountStatus == MountStatus.MOUNTED }
    val unmountedCount = games.count { it.mountStatus != MountStatus.MOUNTED }

    // Filtering & Sorting pipeline
    val processedGames = remember(games, searchQuery, filterStatus, sortOption) {
        games
            .filter { game ->
                val matchesFilter = when (filterStatus) {
                    GameFilterStatus.ALL -> true
                    GameFilterStatus.MOUNTED -> game.mountStatus == MountStatus.MOUNTED
                    GameFilterStatus.UNMOUNTED -> game.mountStatus != MountStatus.MOUNTED
                }
                val matchesSearch = game.packageName.contains(searchQuery, ignoreCase = true) ||
                    game.displayName.contains(searchQuery, ignoreCase = true)
                matchesFilter && matchesSearch
            }
            .let { list ->
                when (sortOption) {
                    GameSortOption.SIZE_DESC -> list.sortedByDescending { it.dataSizeBytes }
                    GameSortOption.NAME_ASC -> list.sortedBy { it.displayName.lowercase() }
                }
            }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactScreenHeader(
                title = stringResource(R.string.games_title),
                subtitle = "$mountedCount/${games.size} " + stringResource(R.string.dashboard_mounted_games),
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (games.isNotEmpty()) {
                            // Mount All Action
                            IconButton(
                                onClick = onMountAll,
                                enabled = unmountedCount > 0,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .background(
                                            color = if (unmountedCount > 0) CyberEmerald.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.games_batch_mount_all),
                                        tint = if (unmountedCount > 0) CyberEmerald
                                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Unmount All Action
                            IconButton(
                                onClick = onUnmountAll,
                                enabled = mountedCount > 0,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .background(
                                            color = if (mountedCount > 0) NeonCrimson.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = stringResource(R.string.games_batch_unmount_all),
                                        tint = if (mountedCount > 0) NeonCrimson
                                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Sort Menu Action
                            Box {
                                IconButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier.size(28.dp)
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
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = stringResource(R.string.games_sort_title),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.games_sort_size_desc),
                                                fontSize = 12.sp,
                                                fontWeight = if (sortOption == GameSortOption.SIZE_DESC) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            onSortOptionChange(GameSortOption.SIZE_DESC)
                                            showSortMenu = false
                                        },
                                        modifier = Modifier.heightIn(min = 32.dp, max = 34.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.games_sort_name_asc),
                                                fontSize = 12.sp,
                                                fontWeight = if (sortOption == GameSortOption.NAME_ASC) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            onSortOptionChange(GameSortOption.NAME_ASC)
                                            showSortMenu = false
                                        },
                                        modifier = Modifier.heightIn(min = 32.dp, max = 34.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            val density = LocalDensity.current
            val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val navBarsBottomPx = with(density) { navBarsBottom.toPx() }

            // When bottom bar is visible: NavGraph's HorizontalPager is already padded by
            // animatedBottomPadding (equal to ModernNavigationBar + navBars insets).
            // So GamesScreen's inner Scaffold bottom is already placed directly on top of ModernNavigationBar.
            // Therefore, targetOffsetY is 0f (FAB rests cleanly with standard padding right above the bottom bar).
            // When bottom bar is hidden: NavGraph's animatedBottomPadding animates to 0dp, extending
            // GamesScreen to the screen bottom. We then apply -navBarsBottomPx so the FAB glides down
            // with the bar, resting cleanly above the Android system gesture navigation bar.
            val targetOffsetY = if (isBottomBarVisible) {
                0f
            } else {
                -navBarsBottomPx
            }
            val animatedOffsetY by animateFloatAsState(
                targetValue = targetOffsetY,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "fab_bottom_bar_offset"
            )

            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = animatedOffsetY.roundToInt()) }
                    .padding(end = 16.dp, bottom = 12.dp)
                    .size(52.dp),
                shape = CircleShape,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brush = AuroraGradientBrush, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.games_add),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp)
            ) {
            if (discoveredGames.isNotEmpty()) {
                DiscoveredGamesBanner(
                    discoveredGames = discoveredGames,
                    onImportAll = onImportAllDiscovered,
                    onDismiss = onDismissDiscovered,
                    onRestructure = onRestructureDiscovered
                )
            }

            if (games.isEmpty()) {
                // ── CLEAN EMPTY STATE WITH GUIDANCE TEXT ──
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = stringResource(R.string.games_empty_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.games_empty_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            } else {
                // ── COMPACT NATURAL SEARCH BAR ──
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.games_search),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    ),
                                    maxLines = 1
                                )
                            }

                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ── FILTER CHIPS ROW ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple(GameFilterStatus.ALL, stringResource(R.string.games_filter_all) + " (${games.size})", filterStatus == GameFilterStatus.ALL),
                        Triple(GameFilterStatus.MOUNTED, stringResource(R.string.games_filter_mounted) + " ($mountedCount)", filterStatus == GameFilterStatus.MOUNTED),
                        Triple(GameFilterStatus.UNMOUNTED, stringResource(R.string.games_filter_unmounted) + " ($unmountedCount)", filterStatus == GameFilterStatus.UNMOUNTED)
                    ).forEach { (status, label, selected) ->
                        Surface(
                            onClick = { onFilterStatusChange(status) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(7.dp))

                // ── GAMES LIST ──
                if (processedGames.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.add_game_no_apps_found),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(processedGames, key = { it.packageName }) { game ->
                            ModernGameCard(
                                game = game,
                                onToggleMount = {
                                    if (game.mountStatus == MountStatus.DISK_DETACHED) {
                                        // Ignore toggle when disk is detached
                                    } else if (game.mountStatus == MountStatus.MOUNTED) {
                                        gameToUnmount = game
                                    } else if (game.mountStatus == MountStatus.NEED_MIGRATION) {
                                        gameForNeedMigration = game
                                    } else {
                                        onToggleMount(game)
                                    }
                                },
                                onCardClick = { onSelectGameForDetail(game) },
                                onCardLongClick = { onSelectGameForDelete(game) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(76.dp))
                        }
                    }
                }
            }
        }
    }

    if (gameToUnmount != null) {
        val target = gameToUnmount!!
        ConfirmDialog(
            title = stringResource(R.string.unmount_confirm_title),
            message = stringResource(R.string.unmount_confirm_message),
            confirmText = stringResource(R.string.manage_btn_unmount_game),
            onConfirm = {
                gameToUnmount = null
                onToggleMount(target)
            },
            onDismiss = { gameToUnmount = null }
        )
    }

    if (gameForNeedMigration != null) {
        val target = gameForNeedMigration!!
        NeedMigrationDialog(
            game = target,
            onConfirmMigration = {
                gameForNeedMigration = null
                onMigrateGame(target)
            },
            onDismiss = { gameForNeedMigration = null }
        )
    }
}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernAppCard(
    game: GameEntry,
    onToggleMount: () -> Unit,
    onCardClick: () -> Unit,
    onCardLongClick: (() -> Unit)? = null
) = ModernGameCard(
    game = game,
    onToggleMount = onToggleMount,
    onCardClick = onCardClick,
    onCardLongClick = onCardLongClick
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernGameCard(
    game: GameEntry,
    onToggleMount: () -> Unit,
    onCardClick: () -> Unit,
    onCardLongClick: (() -> Unit)? = null
) {
    val isMounted = game.mountStatus == MountStatus.MOUNTED

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isMounted) CyberEmerald.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = onCardLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // App Icon
            AppIconImage(
                packageName = game.packageName,
                size = 36.dp
            )

            // Titles & Metadata inline
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = game.displayName.ifBlank { game.packageName },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = game.packageName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )



                    if (game.dataSizeBytes > 0) {
                        Text(
                            text = "•",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = FormatUtils.formatBytes(game.dataSizeBytes),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }

                    // Need Migration badge inline
                    if (game.mountStatus == MountStatus.NEED_MIGRATION) {
                        val amberColor = Color(0xFFFF9800)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = amberColor.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, amberColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = stringResource(R.string.status_need_migration),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = amberColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (game.mountStatus == MountStatus.DISK_DETACHED) {
                        val slateColor = Color(0xFF90A4AE)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = slateColor.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, slateColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = stringResource(R.string.status_disk_detached),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = slateColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (game.mountStatus == MountStatus.ERROR) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeonCrimson.copy(alpha = 0.14f),
                            border = BorderStroke(0.8.dp, NeonCrimson.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = stringResource(R.string.status_error),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = NeonCrimson,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Compact Switch (Toggle Tactile)
            Switch(
                checked = isMounted,
                enabled = game.mountStatus != MountStatus.DISK_DETACHED,
                onCheckedChange = { onToggleMount() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CyberEmerald,
                    checkedBorderColor = CyberEmerald,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.5f),
                    disabledCheckedTrackColor = CyberEmerald.copy(alpha = 0.3f),
                    disabledUncheckedThumbColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}

@Composable
private fun DiscoveredGamesBanner(
    discoveredGames: List<DiscoveredGame>,
    onImportAll: () -> Unit,
    onDismiss: () -> Unit,
    onRestructure: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(CyberEmerald.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = CyberEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.portability_banner_title, discoveredGames.size),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.portability_banner_desc),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Mini chips of discovered packages
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                discoveredGames.forEach { dg ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = dg.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.portability_btn_dismiss),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val hasRestructure = discoveredGames.any { it.needsRestructure }
                if (hasRestructure) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onRestructure,
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigoLight),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.portability_btn_restructure),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onImportAll,
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.portability_btn_restore),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    )
                }
            }
        }
    }
}

