package app.mountx

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
import app.mountx.data.model.AppStatus
import app.mountx.data.model.FilesystemType
import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountStatus
import app.mountx.data.model.RootSolution
import app.mountx.data.model.StorageInfo
import app.mountx.ui.components.ModernNavigationBar
import app.mountx.ui.dashboard.DashboardContent
import app.mountx.ui.games.GamesContent
import app.mountx.ui.navigation.NavGraph
import app.mountx.ui.navigation.Screen
import app.mountx.ui.storage.StorageContent
import app.mountx.ui.theme.MountXTheme
import app.mountx.util.AppPreferences
import app.mountx.util.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var systemSyncMonitor: app.mountx.service.SystemSyncMonitor

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onDestroy() {
        super.onDestroy()
        systemSyncMonitor.stopMonitoring()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        systemSyncMonitor.startMonitoring()

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
            val themeMode by appPreferences.themeMode.collectAsState(initial = app.mountx.util.ThemeMode.SYSTEM)
            val language by appPreferences.language.collectAsState(initial = "en")

            val localizedContext = remember(language) {
                val locale = if (language == "id") java.util.Locale("id", "ID") else java.util.Locale("en", "US")
                val fallbackLocale = if (language == "id") java.util.Locale("in", "ID") else java.util.Locale("en", "GB")
                java.util.Locale.setDefault(locale)

                val config = android.content.res.Configuration(this@MainActivity.resources.configuration)
                config.setLocale(locale)
                config.setLocales(android.os.LocaleList(locale, fallbackLocale))
                config.setLayoutDirection(locale)

                @Suppress("DEPRECATION")
                this@MainActivity.resources.updateConfiguration(config, this@MainActivity.resources.displayMetrics)
                @Suppress("DEPRECATION")
                this@MainActivity.applicationContext.resources.updateConfiguration(config, this@MainActivity.applicationContext.resources.displayMetrics)

                LocalizedActivityContext(this@MainActivity, config)
            }

            val darkTheme = when (themeMode) {
                app.mountx.util.ThemeMode.LIGHT -> false
                app.mountx.util.ThemeMode.DARK -> true
                app.mountx.util.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            val view = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.DisposableEffect(darkTheme) {
                val window = this@MainActivity.window
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
                onDispose {}
            }

            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides localizedContext.resources.configuration,
                androidx.compose.ui.platform.LocalContext provides localizedContext
            ) {
                MountXTheme(themeMode = themeMode) {
                    var showPermissionSheet by remember {
                        val state = app.mountx.util.PermissionManager.checkAllPermissions(this@MainActivity)
                        mutableStateOf(!state.areEssentialGranted)
                    }

                    Surface(modifier = Modifier.fillMaxSize()) {
                        NavGraph()

                        if (showPermissionSheet) {
                            app.mountx.ui.components.PermissionOnboardingSheet(
                                onDismiss = { showPermissionSheet = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

private class LocalizedActivityContext(
    base: android.app.Activity,
    private val localizedConfig: android.content.res.Configuration
) : android.content.ContextWrapper(base) {
    private val localizedResources: android.content.res.Resources by lazy {
        base.createConfigurationContext(localizedConfig).resources
    }

    override fun getResources(): android.content.res.Resources = localizedResources
}

// ── Full App Compose Previews (Android Studio Design / Split View) ──

@Preview(
    name = "MountX - Full App (Dark Theme)",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=1080px,height=2400px,dpi=420",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun MainActivityFullAppPreviewDark() {
    MountXTheme(themeMode = app.mountx.util.ThemeMode.DARK) {
        FullAppPreviewLayout()
    }
}

@Preview(
    name = "MountX - Full App (Light Theme)",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=1080px,height=2400px,dpi=420",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO
)
@Composable
private fun MainActivityFullAppPreviewLight() {
    MountXTheme(themeMode = app.mountx.util.ThemeMode.LIGHT) {
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
            app.mountx.ui.components.ModernNavigationBar(
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
                    app.mountx.ui.dashboard.DashboardContent(
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
                            app.mountx.data.model.GameEntry(
                                packageName = "com.kurogame.wutheringwaves.global",
                                displayName = "Wuthering Waves",
                                mode = app.mountx.data.model.MountMode.PKG,
                                mountStatus = app.mountx.data.model.MountStatus.MOUNTED,
                                dataSizeBytes = 25_400_000_000L
                            ),
                            app.mountx.data.model.GameEntry(
                                packageName = "com.miHoYo.GenshinImpact",
                                displayName = "Genshin Impact",
                                mode = app.mountx.data.model.MountMode.FILES,
                                mountStatus = app.mountx.data.model.MountStatus.MOUNTED,
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
                    app.mountx.ui.games.GamesContent(
                        games = listOf(
                            app.mountx.data.model.GameEntry(
                                packageName = "com.kurogame.wutheringwaves.global",
                                displayName = "Wuthering Waves",
                                mode = app.mountx.data.model.MountMode.PKG,
                                mountStatus = app.mountx.data.model.MountStatus.MOUNTED,
                                dataSizeBytes = 25_400_000_000L
                            ),
                            app.mountx.data.model.GameEntry(
                                packageName = "com.miHoYo.GenshinImpact",
                                displayName = "Genshin Impact",
                                mode = app.mountx.data.model.MountMode.FILES,
                                mountStatus = app.mountx.data.model.MountStatus.MOUNTED,
                                dataSizeBytes = 32_100_000_000L
                            )
                        ),
                        searchQuery = "",
                        filterStatus = app.mountx.ui.games.GameFilterStatus.ALL,
                        sortOption = app.mountx.ui.games.GameSortOption.SIZE_DESC,
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
                    app.mountx.ui.storage.StorageContent(
                        storage = app.mountx.data.model.StorageInfo(
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
