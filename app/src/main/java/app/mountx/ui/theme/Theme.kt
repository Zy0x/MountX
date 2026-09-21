package app.mountx.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.mountx.util.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = ElectricIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = ElectricIndigoLight,
    secondary = HyperCyan,
    onSecondary = CyberBgDark,
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = HyperCyanBright,
    tertiary = CyberEmerald,
    onTertiary = CyberBgDark,
    error = NeonCrimson,
    onError = Color.White,
    errorContainer = Color(0xFF4C0519),
    onErrorContainer = Color(0xFFFFD1D9),
    background = CyberBgDark,
    onBackground = CyberOnBgDark,
    surface = CyberSurfaceDark,
    onSurface = CyberOnSurfaceDark,
    surfaceVariant = CyberSurfaceVariantDark,
    onSurfaceVariant = CyberOnVariantDark,
    surfaceContainer = CyberSurfaceDark,
    surfaceContainerHigh = CyberSurfaceVariantDark,
    surfaceContainerHighest = Color(0xFF222C46),
    surfaceContainerLow = CyberBgDark,
    surfaceContainerLowest = Color(0xFF060910),
    outline = CyberBorderDark,
    outlineVariant = CyberBorderHighlightDark
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricIndigoDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E4DC),
    onPrimaryContainer = SandstoneOnBgLight,
    secondary = SlateCyanLight,
    onSecondary = Color.White,
    secondaryContainer = SlateCyanContainerLight,
    onSecondaryContainer = SandstoneOnBgLight,
    tertiary = ForestGreenLight,
    onTertiary = Color.White,
    error = TerracottaRedLight,
    onError = Color.White,
    errorContainer = TerracottaRedContainerLight,
    onErrorContainer = Color(0xFF7F1D1D),
    background = SandstoneBgLight,
    onBackground = SandstoneOnBgLight,
    surface = SandstoneSurfaceLight,
    onSurface = SandstoneOnSurfaceLight,
    surfaceVariant = SandstoneSurfaceVariantLight,
    onSurfaceVariant = SandstoneOnVariantLight,
    surfaceContainer = SandstoneModalLight,
    surfaceContainerHigh = SandstoneSurfaceVariantLight,
    surfaceContainerHighest = SandstoneBorderHighlightLight,
    surfaceContainerLow = SandstoneBgLight,
    surfaceContainerLowest = SandstoneSurfaceLight,
    outline = SandstoneBorderLight,
    outlineVariant = SandstoneBorderHighlightLight
)

@Composable
fun MountXTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
