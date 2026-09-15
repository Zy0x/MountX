package app.mihon.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import app.mihon.R

sealed class Screen(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector
) {
    object Dashboard : Screen("dashboard", R.string.nav_dashboard, Icons.Default.Dashboard)
    object Games : Screen("games", R.string.nav_games, Icons.Default.Gamepad)
    object Storage : Screen("storage", R.string.nav_storage, Icons.Default.Folder)
    object Logs : Screen("logs", R.string.nav_logs, Icons.Default.Description)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
    object About : Screen("about", R.string.about_title, Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(Dashboard, Games, Storage, Logs, Settings)
    }
}
