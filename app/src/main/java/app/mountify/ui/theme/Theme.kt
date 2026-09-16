package app.mountify.ui.theme

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
import app.mountify.util.ThemeMode

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
    outline = CyberBorderDark,
    outlineVariant = CyberBorderHighlightDark
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricIndigoDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = ElectricIndigoDark,
    secondary = HyperCyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = EmeraldActive,
    onTertiary = Color.White,
    error = CoralError,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF9F1239),
    background = FrostBgLight,
    onBackground = FrostOnBgLight,
    surface = FrostSurfaceLight,
    onSurface = FrostOnSurfaceLight,
    surfaceVariant = FrostSurfaceVariantLight,
    onSurfaceVariant = FrostOnVariantLight,
    outline = FrostBorderLight,
    outlineVariant = FrostBorderHighlightLight
)

@Composable
fun MountifyTheme(
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
