package app.mountx.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import app.mountx.R

sealed class Screen(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
    val unselectedIcon: ImageVector = icon
) {
    object Dashboard : Screen(
        route = "dashboard",
        titleRes = R.string.nav_dashboard,
        icon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )
    object Games : Screen(
        route = "games",
        titleRes = R.string.nav_games,
        icon = Icons.Filled.SportsEsports,
        unselectedIcon = Icons.Outlined.SportsEsports
    )
    object Storage : Screen(
        route = "storage",
        titleRes = R.string.nav_storage,
        icon = Icons.Filled.SdStorage,
        unselectedIcon = Icons.Outlined.SdStorage
    )
    object Logs : Screen(
        route = "logs",
        titleRes = R.string.nav_logs,
        icon = Icons.Filled.Terminal,
        unselectedIcon = Icons.Outlined.Terminal
    )
    object Settings : Screen(
        route = "settings",
        titleRes = R.string.nav_settings,
        icon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
    object About : Screen(
        route = "about",
        titleRes = R.string.about_title,
        icon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val bottomNavItems = listOf(Dashboard, Games, Storage, Logs, Settings)
    }
}
