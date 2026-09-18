package app.mountify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.mountify.data.model.AppStatus
import app.mountify.data.model.FilesystemType
import app.mountify.data.model.GameEntry
import app.mountify.data.model.MountMode
import app.mountify.data.model.MountStatus
import app.mountify.data.model.RootSolution
import app.mountify.data.model.StorageInfo
import app.mountify.ui.components.ModernNavigationBar
import app.mountify.ui.dashboard.DashboardContent
import app.mountify.ui.games.GamesContent
import app.mountify.ui.navigation.NavGraph
import app.mountify.ui.navigation.Screen
import app.mountify.ui.storage.StorageContent
import app.mountify.ui.theme.MountifyTheme
import app.mountify.util.AppPreferences
import app.mountify.util.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ask for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val themeMode by appPreferences.themeMode.collectAsState(initial = app.mountify.util.ThemeMode.SYSTEM)

            MountifyTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph()
                }
            }
        }
    }
}

// ── Full App Compose Previews (Android Studio Design / Split View) ──

@Preview(
    name = "Mountify - Full App (Dark Theme)",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=1080px,height=2400px,dpi=420",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun MainActivityFullAppPreviewDark() {
    MountifyTheme(themeMode = app.mountify.util.ThemeMode.DARK) {
        FullAppPreviewLayout()
    }
}

@Preview(
    name = "Mountify - Full App (Light Theme)",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=1080px,height=2400px,dpi=420",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO
)
@Composable
private fun MainActivityFullAppPreviewLight() {
    MountifyTheme(themeMode = app.mountify.util.ThemeMode.LIGHT) {
        FullAppPreviewLayout()
    }
}

@Composable
fun FullAppPreviewLayout(
    initialRoute: String = Screen.Dashboard.route
) {
    var currentRoute by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialRoute) }

    androidx.compose.material3.Scaffold(
        bottomBar = {
            app.mountify.ui.components.ModernNavigationBar(
                screens = Screen.bottomNavItems,
                currentRoute = currentRoute,
                onNavigate = { screen -> currentRoute = screen.route }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentRoute) {
                Screen.Dashboard.route -> {
                    app.mountify.ui.dashboard.DashboardContent(
                        status = AppStatus(
                            rootSolution = RootSolution.MAGISK,
                            isModuleInstalled = true,
                            moduleVersion = "2.1.6",
                            storageInfo = StorageInfo(
                                blockDevice = "/dev/block/mmcblk0p2",
                                mountPoint = "/data/sdext2",
                                filesystem = "f2fs",
                                totalBytes = 64_000_000_000L,
                                usedBytes = 28_000_000_000L,
                                freeBytes = 36_000_000_000L,
                                isMounted = true
                            ),
                            mountedGamesCount = 2,
                            totalGamesCount = 3
                        ),
                        isRefreshing = false,
                        games = listOf(
                            app.mountify.data.model.GameEntry(
                                packageName = "com.kurogame.wutheringwaves.global",
                                displayName = "Wuthering Waves",
                                mode = app.mountify.data.model.MountMode.PKG,
                                mountStatus = app.mountify.data.model.MountStatus.MOUNTED,
                                dataSizeBytes = 25_400_000_000L
                            ),
                            app.mountify.data.model.GameEntry(
                                packageName = "com.miHoYo.GenshinImpact",
                                displayName = "Genshin Impact",
                                mode = app.mountify.data.model.MountMode.FILES,
                                mountStatus = app.mountify.data.model.MountStatus.MOUNTED,
                                dataSizeBytes = 32_100_000_000L
                            )
                        ),
                        onRefresh = {},
                        onNavigateToGames = { currentRoute = Screen.Games.route },
                        onNavigateToStorage = { currentRoute = Screen.Storage.route },
                        onNavigateToLogs = { currentRoute = Screen.Logs.route },
                        onMountAll = {},
                        onUnmountAll = {},
                        onToggleGameMount = {}
                    )
                }
                Screen.Games.route -> {
                    app.mountify.ui.games.GamesContent(
                        games = listOf(
                            app.mountify.data.model.GameEntry(
                                packageName = "com.kurogame.wutheringwaves.global",
                                displayName = "Wuthering Waves",
                                mode = app.mountify.data.model.MountMode.PKG,
                                mountStatus = app.mountify.data.model.MountStatus.MOUNTED,
                                dataSizeBytes = 25_400_000_000L
                            ),
                            app.mountify.data.model.GameEntry(
                                packageName = "com.miHoYo.GenshinImpact",
                                displayName = "Genshin Impact",
                                mode = app.mountify.data.model.MountMode.FILES,
                                mountStatus = app.mountify.data.model.MountStatus.MOUNTED,
                                dataSizeBytes = 32_100_000_000L
                            )
                        ),
                        searchQuery = "",
                        filterStatus = app.mountify.ui.games.GameFilterStatus.ALL,
                        sortOption = app.mountify.ui.games.GameSortOption.SIZE_DESC,
                        onSearchQueryChange = {},
                        onFilterStatusChange = {},
                        onSortOptionChange = {},
                        onAddClick = {},
                        onToggleMount = {},
                        onMountAll = {},
                        onUnmountAll = {},
                        onSelectGameForDetail = {}
                    )
                }
                Screen.Storage.route -> {
                    app.mountify.ui.storage.StorageContent(
                        storage = app.mountify.data.model.StorageInfo(
                            blockDevice = "/dev/block/mmcblk0p2",
                            mountPoint = "/data/sdext2",
                            filesystem = "f2fs",
                            totalBytes = 64_000_000_000L,
                            usedBytes = 28_000_000_000L,
                            freeBytes = 36_000_000_000L,
                            isMounted = true
                        ),
                        internalStorage = null,
                        diskInfo = null,
                        allDisks = emptyList(),
                        isScanning = false,
                        statusMessage = null,
                        offloadedStats = 0 to 0L
                    )
                }
                else -> {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Text(
                            text = "Layar: $currentRoute",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
