package app.mountify.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountify.R
import app.mountify.data.model.GameEntry
import app.mountify.data.model.MountMode
import app.mountify.data.model.MountStatus
import app.mountify.ui.components.AppIconImage
import app.mountify.ui.components.CompactScreenHeader
import app.mountify.ui.components.ConfirmDialog
import app.mountify.ui.components.StatusChip
import app.mountify.ui.theme.AuroraGradientBrush
import app.mountify.ui.theme.CyberEmerald
import app.mountify.ui.theme.NeonCrimson
import app.mountify.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    viewModel: GamesViewModel,
    modifier: Modifier = Modifier
) {
    val games by viewModel.games.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val storageBreakdown by viewModel.storageBreakdown.collectAsState()
    val isMovingData by viewModel.isMovingData.collectAsState()
    val moveMessage by viewModel.moveMessage.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var selectedGameForDetail by remember { mutableStateOf<GameEntry?>(null) }
    var gameToDelete by remember { mutableStateOf<GameEntry?>(null) }

    GamesContent(
        games = games,
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
        onMountAll = { viewModel.mountAllGames() },
        onUnmountAll = { viewModel.unmountAllGames() },
        onSelectGameForDetail = { game ->
            selectedGameForDetail = game
            viewModel.loadStorageBreakdown(game.packageName)
        },
        modifier = modifier
    )

    // Add Game Sheet (Visual App Picker)
    if (showAddSheet) {
        AddGameSheet(
            installedApps = installedApps,
            onDismiss = { showAddSheet = false },
            onAdd = { pkg, name, mode ->
                viewModel.addGame(pkg, name, mode)
                showAddSheet = false
            }
        )
    }

    // Integrated Game Detail & Storage Sheet
    selectedGameForDetail?.let { game ->
        // Keep selected game reference in sync with Room state
        val updatedGame = games.firstOrNull { it.packageName == game.packageName } ?: game

        GameDetailSheet(
            game = updatedGame,
            storageBreakdown = storageBreakdown,
            isMoving = isMovingData,
            moveMessage = moveMessage,
            onDismiss = {
                viewModel.clearMoveMessage()
                selectedGameForDetail = null
            },
            onMove = { dir -> viewModel.moveData(updatedGame.packageName, dir) },
            onUpdateMode = { newMode -> viewModel.updateGameMode(updatedGame.packageName, newMode) },
            onDelete = {
                gameToDelete = updatedGame
                selectedGameForDetail = null
            }
        )
    }

    // Delete Confirmation Dialog
    gameToDelete?.let { game ->
        ConfirmDialog(
            title = stringResource(R.string.games_delete),
            message = stringResource(R.string.games_delete_confirm, game.displayName.ifBlank { game.packageName }),
            isDestructive = true,
            confirmText = stringResource(R.string.games_delete),
            onConfirm = {
                viewModel.removeGame(game.packageName)
                gameToDelete = null
            },
            onDismiss = { gameToDelete = null }
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
    onSearchQueryChange: (String) -> Unit,
    onFilterStatusChange: (GameFilterStatus) -> Unit,
    onSortOptionChange: (GameSortOption) -> Unit,
    onAddClick: () -> Unit,
    onToggleMount: (GameEntry) -> Unit,
    onMountAll: () -> Unit,
    onUnmountAll: () -> Unit,
    onSelectGameForDetail: (GameEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

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
        topBar = {
            CompactScreenHeader(
                title = stringResource(R.string.games_title),
                subtitle = "$mountedCount/${games.size} " + stringResource(R.string.dashboard_mounted_games),
                actions = {
                    if (games.isNotEmpty()) {
                        // Mount All Action
                        IconButton(
                            onClick = onMountAll,
                            modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.games_batch_mount_all),
                                tint = CyberEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Unmount All Action
                        IconButton(
                            onClick = onUnmountAll,
                            modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = stringResource(R.string.games_batch_unmount_all),
                                tint = NeonCrimson,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Sort Menu Action
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.games_sort_title),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.games_sort_size_desc),
                                            fontWeight = if (sortOption == GameSortOption.SIZE_DESC) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onSortOptionChange(GameSortOption.SIZE_DESC)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.games_sort_name_asc),
                                            fontWeight = if (sortOption == GameSortOption.NAME_ASC) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onSortOptionChange(GameSortOption.NAME_ASC)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (games.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onAddClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.games_add),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp)
        ) {
            if (games.isEmpty()) {
                // ── WELCOMING EMPTY STATE WITH AURORA CTA ──
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

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onAddClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp, max = 48.dp)
                                .background(
                                    brush = AuroraGradientBrush,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.games_empty_cta),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // ── SEARCH BAR ──
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            stringResource(R.string.games_search),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── FILTER CHIPS ROW ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filterStatus == GameFilterStatus.ALL,
                        onClick = { onFilterStatusChange(GameFilterStatus.ALL) },
                        label = {
                            Text(
                                stringResource(R.string.games_filter_all) + " (${games.size})",
                                fontSize = 11.sp
                            )
                        }
                    )
                    FilterChip(
                        selected = filterStatus == GameFilterStatus.MOUNTED,
                        onClick = { onFilterStatusChange(GameFilterStatus.MOUNTED) },
                        label = {
                            Text(
                                stringResource(R.string.games_filter_mounted) + " ($mountedCount)",
                                fontSize = 11.sp
                            )
                        }
                    )
                    FilterChip(
                        selected = filterStatus == GameFilterStatus.UNMOUNTED,
                        onClick = { onFilterStatusChange(GameFilterStatus.UNMOUNTED) },
                        label = {
                            Text(
                                stringResource(R.string.games_filter_unmounted) + " ($unmountedCount)",
                                fontSize = 11.sp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                                onToggleMount = { onToggleMount(game) },
                                onCardClick = { onSelectGameForDetail(game) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernGameCard(
    game: GameEntry,
    onToggleMount: () -> Unit,
    onCardClick: () -> Unit
) {
    val isMounted = game.mountStatus == MountStatus.MOUNTED

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isMounted) CyberEmerald.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: App Icon, Titles, and More Details Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIconImage(
                    packageName = game.packageName,
                    size = 42.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.displayName.ifBlank { game.packageName },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = game.packageName,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = onCardClick,
                    modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.game_detail_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Badges (Mode, Size) & Tactile Mount Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badges
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = game.mode.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Size Badge
                    if (game.dataSizeBytes > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = FormatUtils.formatBytes(game.dataSizeBytes),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Status Chip
                    StatusChip(status = game.mountStatus)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Tactile Mount Button
                if (isMounted) {
                    FilledTonalButton(
                        onClick = onToggleMount,
                        modifier = Modifier.heightIn(min = 36.dp, max = 38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NeonCrimson.copy(alpha = 0.12f),
                            contentColor = NeonCrimson
                        ),
                        border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.35f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = NeonCrimson
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.games_unmount),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCrimson,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    Button(
                        onClick = onToggleMount,
                        modifier = Modifier.heightIn(min = 36.dp, max = 38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberEmerald.copy(alpha = 0.15f),
                            contentColor = CyberEmerald
                        ),
                        border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = CyberEmerald
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.games_mount),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
