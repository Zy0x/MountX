package app.mountify.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.mountify.ui.about.AboutScreen
import app.mountify.ui.about.AboutViewModel
import app.mountify.ui.dashboard.DashboardScreen
import app.mountify.ui.dashboard.DashboardViewModel
import app.mountify.ui.games.GamesScreen
import app.mountify.ui.games.GamesViewModel
import app.mountify.ui.logs.LogsScreen
import app.mountify.ui.logs.LogsViewModel
import app.mountify.ui.settings.SettingsScreen
import app.mountify.ui.settings.SettingsViewModel
import app.mountify.ui.storage.BackupRestoreScreen
import app.mountify.ui.storage.StorageScreen
import app.mountify.ui.storage.StorageViewModel

@Composable
fun NavGraph(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isTopLevelRoute = Screen.bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isTopLevelRoute) {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.titleRes)) }
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(250)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(250)
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(250)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(250)
                    )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(250)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(250)
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(250)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(250)
                    )
            }
        ) {
            composable(Screen.Dashboard.route) {
                val vm = hiltViewModel<DashboardViewModel>()
                DashboardScreen(
                    viewModel = vm,
                    onNavigateToGames = { navController.navigate(Screen.Games.route) },
                    onNavigateToStorage = { navController.navigate(Screen.Storage.route) },
                    onNavigateToLogs = { navController.navigate(Screen.Logs.route) }
                )
            }

            composable(Screen.Games.route) {
                val vm = hiltViewModel<GamesViewModel>()
                GamesScreen(viewModel = vm)
            }

            composable(Screen.Storage.route) {
                val vm = hiltViewModel<StorageViewModel>()
                StorageScreen(
                    viewModel = vm,
                    onNavigateToBackup = { navController.navigate("backup_restore") }
                )
            }

            composable("backup_restore") {
                val vm = hiltViewModel<StorageViewModel>()
                BackupRestoreScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Logs.route) {
                val vm = hiltViewModel<LogsViewModel>()
                LogsScreen(viewModel = vm)
            }

            composable(Screen.Settings.route) {
                val vm = hiltViewModel<SettingsViewModel>()
                SettingsScreen(
                    viewModel = vm,
                    onNavigateToAbout = { navController.navigate(Screen.About.route) }
                )
            }

            composable(Screen.About.route) {
                val vm = hiltViewModel<AboutViewModel>()
                AboutScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
