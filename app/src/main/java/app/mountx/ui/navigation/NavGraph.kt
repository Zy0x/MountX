package app.mountx.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.mountx.ui.about.AboutScreen
import app.mountx.ui.about.AboutViewModel
import app.mountx.ui.components.ModernNavigationBar
import app.mountx.ui.dashboard.DashboardScreen
import app.mountx.ui.dashboard.DashboardViewModel
import app.mountx.ui.games.GamesScreen
import app.mountx.ui.games.GamesViewModel
import app.mountx.ui.logs.LogsScreen
import app.mountx.ui.logs.LogsViewModel
import app.mountx.ui.settings.SettingsScreen
import app.mountx.ui.settings.SettingsViewModel
import app.mountx.ui.storage.BackupRestoreScreen
import app.mountx.ui.storage.StorageScreen
import app.mountx.ui.storage.StorageViewModel
import kotlinx.coroutines.launch

@Composable
fun NavGraph(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main_tabs",
        modifier = modifier
    ) {
        composable("main_tabs") {
            MainTabsScreen(
                onNavigateToBackup = { navController.navigate("backup_restore") },
                onNavigateToAbout = { navController.navigate("about") }
            )
        }

        composable(
            route = "backup_restore",
            enterTransition = {
                fadeIn(animationSpec = tween(150)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(150)
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(150)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(150)
                    )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(150)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(150)
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(150)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(150)
                    )
            }
        ) {
            val vm = hiltViewModel<StorageViewModel>()
            BackupRestoreScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "about",
            enterTransition = {
                fadeIn(animationSpec = tween(150)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(150)
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(150)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(150)
                    )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(150)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(150)
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(150)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(150)
                    )
            }
        ) {
            val vm = hiltViewModel<AboutViewModel>()
            AboutScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainTabsScreen(
    onNavigateToBackup: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()
    val screens = Screen.bottomNavItems

    val activeIndex = pagerState.targetPage
    val currentRoute = screens.getOrNull(activeIndex)?.route ?: Screen.Dashboard.route

    var isOuterPagerScrollEnabled by remember { mutableStateOf(true) }
    var isGamesSubScreenActive by remember { mutableStateOf(false) }
    var isScrollingUp by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (isGamesSubScreenActive) return Offset.Zero
                val delta = available.y
                if (delta < -14f) {
                    isScrollingUp = false
                } else if (delta > 14f) {
                    isScrollingUp = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isScrollingUp = true
    }

    LaunchedEffect(isGamesSubScreenActive) {
        if (!isGamesSubScreenActive) {
            isScrollingUp = true
        }
    }

    val isBottomBarVisible = isScrollingUp && !isGamesSubScreenActive

    // Natural Android back gesture returns to Dashboard tab first
    BackHandler(enabled = pagerState.currentPage != 0 && isOuterPagerScrollEnabled) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    Scaffold(
        bottomBar = {
            val animatedOffset by animateFloatAsState(
                targetValue = if (isBottomBarVisible) 0f else 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "bottom_bar_offset"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = animatedOffset * size.height
                        alpha = if (animatedOffset >= 0.99f) 0f else 1f - (animatedOffset * 0.3f)
                    }
            ) {
                ModernNavigationBar(
                    screens = screens,
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        val targetIndex = screens.indexOf(screen)
                        if (targetIndex >= 0) {
                            coroutineScope.launch {
                                if (kotlin.math.abs(pagerState.currentPage - targetIndex) <= 1) {
                                    pagerState.animateScrollToPage(targetIndex)
                                } else {
                                    pagerState.scrollToPage(targetIndex)
                                }
                            }
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = isOuterPagerScrollEnabled,
            key = { it },
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    PaddingValues(
                        start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                        top = innerPadding.calculateTopPadding(),
                        end = innerPadding.calculateEndPadding(LayoutDirection.Ltr),
                        bottom = if (isGamesSubScreenActive) 0.dp else innerPadding.calculateBottomPadding()
                    )
                )
        ) { page ->
            when (page) {
                0 -> {
                    val dashboardVm = hiltViewModel<DashboardViewModel>()
                    DashboardScreen(
                        viewModel = dashboardVm,
                        onNavigateToGames = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        onNavigateToStorage = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                        onNavigateToLogs = { coroutineScope.launch { pagerState.animateScrollToPage(3) } }
                    )
                }
                1 -> {
                    val gamesVm = hiltViewModel<GamesViewModel>()
                    GamesScreen(
                        viewModel = gamesVm,
                        onPagerScrollEnabled = { isOuterPagerScrollEnabled = it },
                        onBottomBarVisibilityChanged = { isVisible -> isGamesSubScreenActive = !isVisible }
                    )
                }
                2 -> {
                    val storageVm = hiltViewModel<StorageViewModel>()
                    StorageScreen(
                        viewModel = storageVm,
                        onNavigateToBackup = onNavigateToBackup,
                        onNavigateToGames = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                    )
                }
                3 -> {
                    val logsVm = hiltViewModel<LogsViewModel>()
                    LogsScreen(viewModel = logsVm)
                }
                4 -> {
                    val settingsVm = hiltViewModel<SettingsViewModel>()
                    SettingsScreen(
                        viewModel = settingsVm,
                        onNavigateToAbout = onNavigateToAbout
                    )
                }
            }
        }
    }
}
