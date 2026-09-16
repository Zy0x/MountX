package app.mountify.ui.dashboard

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.mountify.R
import app.mountify.data.model.AppStatus
import app.mountify.data.model.RootSolution
import app.mountify.data.model.StorageInfo
import app.mountify.ui.components.ErrorCard
import app.mountify.ui.components.SectionHeader
import app.mountify.ui.theme.MountifyTheme
import app.mountify.ui.theme.PrimaryBlue
import app.mountify.ui.theme.SecondaryTeal
import app.mountify.ui.theme.StatusSuccess
import app.mountify.util.FormatUtils

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToGames: () -> Unit,
    onNavigateToStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status by viewModel.appStatus.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    DashboardContent(
        status = status,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
        onNavigateToGames = onNavigateToGames,
        onNavigateToStorage = onNavigateToStorage,
        onMountAll = { viewModel.mountAll() },
        onUnmountAll = { viewModel.unmountAll() },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    status: AppStatus,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNavigateToGames: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onMountAll: () -> Unit,
    onUnmountAll: () -> Unit,
    modifier: Modifier = Modifier
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (status.isModuleInstalled) {
                            Badge(
                                containerColor = StatusSuccess,
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = if (status.moduleVersion.isNotBlank()) "v${status.moduleVersion}" else "Active",
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.dashboard_refresh)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Root Warning if none
            if (status.rootSolution == RootSolution.NONE) {
                item {
                    ErrorCard(
                        title = stringResource(R.string.dashboard_no_root),
                        description = stringResource(R.string.dashboard_no_root_desc)
                    )
                }
            }

            // Module Warning if not installed
            if (!status.isModuleInstalled && status.rootSolution != RootSolution.NONE) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.dashboard_module_not_installed),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.dashboard_module_install_guide),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Status Overview Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Root Solution Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.dashboard_root_status),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
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
                        }
                    }

                    // Mounted Games Count Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.dashboard_mounted_games),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${status.mountedGamesCount} / ${status.totalGamesCount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SecondaryTeal
                            )
                        }
                    }
                }
            }

            // Storage Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    onClick = onNavigateToStorage
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.dashboard_storage_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            val storage = status.storageInfo
                            if (storage != null && storage.isMounted) {
                                Text(
                                    text = storage.filesystem.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val storage = status.storageInfo
                        if (storage != null && storage.isMounted) {
                            LinearProgressIndicator(
                                progress = { storage.usedPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Used: ${FormatUtils.formatBytes(storage.usedBytes)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Free: ${FormatUtils.formatBytes(storage.freeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryTeal
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.dashboard_storage_not_mounted),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Quick Actions
            item {
                SectionHeader(title = stringResource(R.string.dashboard_quick_actions))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onMountAll,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.dashboard_mount_all))
                    }

                    OutlinedButton(
                        onClick = onUnmountAll,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.dashboard_unmount_all))
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

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
                moduleVersion = "2.1.2",
                mountedGamesCount = 5,
                totalGamesCount = 8,
                storageInfo = StorageInfo(
                    blockDevice = "/dev/block/mmcblk1p2",
                    mountPoint = "/data/media/0/Android/obb",
                    filesystem = "ext4",
                    totalBytes = 128L * 1024 * 1024 * 1024,
                    usedBytes = 80L * 1024 * 1024 * 1024,
                    freeBytes = 48L * 1024 * 1024 * 1024,
                    isMounted = true
                )
            ),
            isRefreshing = false,
            onRefresh = {},
            onNavigateToGames = {},
            onNavigateToStorage = {},
            onMountAll = {},
            onUnmountAll = {}
        )
    }
}

