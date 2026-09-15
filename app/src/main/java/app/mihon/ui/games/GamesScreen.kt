package app.mihon.ui.games

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mihon.R
import app.mihon.data.model.GameEntry
import app.mihon.data.model.MountMode
import app.mihon.data.model.MountStatus
import app.mihon.ui.components.ConfirmDialog
import app.mihon.ui.components.StatusChip
import app.mihon.util.FormatUtils

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

    val filteredGames = games.filter {
        it.packageName.contains(searchQuery, ignoreCase = true) ||
        it.displayName.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.games_title), fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.loadInstalledApps()
                    showAddSheet = true
                }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.games_add))
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.games_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                singleLine = true
            )

            if (filteredGames.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.games_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.games_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                            onToggleMount = { viewModel.toggleMount(game) },
                            onMoveData = { gameToMove = game },
                            onDelete = { gameToDelete = game }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

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

@Composable
fun GameCard(
    game: GameEntry,
    onToggleMount: () -> Unit,
    onMoveData: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.displayName.ifBlank { game.packageName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = game.packageName,
                        style = MaterialTheme.typography.bodySmall,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(text = game.mode.name, style = MaterialTheme.typography.labelSmall) }
                    )
                    if (game.dataSizeBytes > 0) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(text = FormatUtils.formatBytes(game.dataSizeBytes), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onMoveData) {
                        Icon(
                            imageVector = Icons.Default.DriveFileMove,
                            contentDescription = stringResource(R.string.games_move_data),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onToggleMount) {
                        Icon(
                            imageVector = if (game.mountStatus == MountStatus.MOUNTED) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (game.mountStatus == MountStatus.MOUNTED) stringResource(R.string.games_unmount) else stringResource(R.string.games_mount),
                            tint = if (game.mountStatus == MountStatus.MOUNTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.games_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
