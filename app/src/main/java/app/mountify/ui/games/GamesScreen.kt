package app.mountify.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountify.R
import app.mountify.data.model.GameEntry
import app.mountify.data.model.MountMode
import app.mountify.data.model.MountStatus
import app.mountify.ui.components.CompactScreenHeader
import app.mountify.ui.components.ConfirmDialog
import app.mountify.ui.components.StatusChip
import app.mountify.ui.theme.CoralError
import app.mountify.ui.theme.ElectricCyan
import app.mountify.ui.theme.EmeraldActive
import app.mountify.ui.theme.ObsidianBg
import app.mountify.ui.theme.ObsidianBorder
import app.mountify.ui.theme.ObsidianCard
import app.mountify.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    viewModel: GamesViewModel,
    modifier: Modifier = Modifier
) {
    val games by viewModel.games.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var gameToMove by remember { mutableStateOf<GameEntry?>(null) }
    var gameToDelete by remember { mutableStateOf<GameEntry?>(null) }

    GamesContent(
        games = games,
        searchQuery = searchQuery,
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onAddClick = {
            viewModel.loadInstalledApps()
            showAddSheet = true
        },
        onToggleMount = { viewModel.toggleMount(it) },
        onMoveData = { gameToMove = it },
        onDelete = { gameToDelete = it },
        modifier = modifier
    )

    // Add Game Sheet
    if (showAddSheet) {
        AddGameSheet(
            installedApps = viewModel.installedApps.collectAsState().value,
            onDismiss = { showAddSheet = false },
            onAdd = { pkg, name, mode ->
                viewModel.addGame(pkg, name, mode)
                showAddSheet = false
            }
        )
    }

    // Move Data Sheet
    gameToMove?.let { game ->
        MoveDataSheet(
            game = game,
            isMoving = viewModel.isMovingData.collectAsState().value,
            moveMessage = viewModel.moveMessage.collectAsState().value,
            onDismiss = {
                viewModel.clearMoveMessage()
                gameToMove = null
            },
            onMove = { dir -> viewModel.moveData(game.packageName, dir) }
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
    onSearchQueryChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onToggleMount: (GameEntry) -> Unit,
    onMoveData: (GameEntry) -> Unit,
    onDelete: (GameEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredGames = games.filter {
        it.packageName.contains(searchQuery, ignoreCase = true) ||
        it.displayName.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            CompactScreenHeader(
                title = stringResource(R.string.games_title),
                subtitle = "${games.count { it.mountStatus == MountStatus.MOUNTED }}/${games.size} " + stringResource(R.string.dashboard_mounted_games)
            )
        },
        containerColor = ObsidianBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = ElectricCyan,
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.games_add),
                    modifier = Modifier.size(20.dp)
                )
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
            // Search Bar
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ObsidianCard,
                    unfocusedContainerColor = ObsidianCard,
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = ObsidianBorder
                )
            )

            if (filteredGames.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.games_empty_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.games_empty_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredGames, key = { it.packageName }) { game ->
                        GameCard(
                            game = game,
                            onToggleMount = { onToggleMount(game) },
                            onMoveData = { onMoveData(game) },
                            onDelete = { onDelete(game) }
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

@Composable
fun GameCard(
    game: GameEntry,
    onToggleMount: () -> Unit,
    onMoveData: () -> Unit,
    onDelete: () -> Unit
) {
    val isMounted = game.mountStatus == MountStatus.MOUNTED

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(
            1.dp,
            if (isMounted) EmeraldActive.copy(alpha = 0.35f) else ObsidianBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.displayName.ifBlank { game.packageName },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = game.packageName,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                StatusChip(status = game.mountStatus)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF192030),
                        border = BorderStroke(1.dp, Color(0xFF28344C))
                    ) {
                        Text(
                            text = game.mode.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (game.dataSizeBytes > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF161B26),
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Text(
                                text = FormatUtils.formatBytes(game.dataSizeBytes),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onMoveData,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DriveFileMove,
                            contentDescription = stringResource(R.string.games_move_data),
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleMount,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isMounted) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isMounted) stringResource(R.string.games_unmount) else stringResource(R.string.games_mount),
                            tint = if (isMounted) CoralError else EmeraldActive,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.games_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Games Screen - Dark Theme", showBackground = true)
@Composable
private fun GamesScreenPreviewDark() {
    app.mountify.ui.theme.MountifyTheme(dynamicColor = false) {
        GamesContent(
            games = listOf(
                GameEntry(
                    packageName = "com.kurogame.wutheringwaves.global",
                    displayName = "Wuthering Waves",
                    mode = MountMode.PKG,
                    mountStatus = MountStatus.MOUNTED,
                    dataSizeBytes = 25_400_000_000L
                ),
                GameEntry(
                    packageName = "com.miHoYo.GenshinImpact",
                    displayName = "Genshin Impact",
                    mode = MountMode.FILES,
                    mountStatus = MountStatus.UNMOUNTED,
                    dataSizeBytes = 32_100_000_000L
                )
            ),
            searchQuery = "",
            onSearchQueryChange = {},
            onAddClick = {},
            onToggleMount = {},
            onMoveData = {},
            onDelete = {}
        )
    }
}

