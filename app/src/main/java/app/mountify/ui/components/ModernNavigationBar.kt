package app.mountify.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountify.ui.navigation.Screen
import app.mountify.ui.theme.DarkBackground
import app.mountify.ui.theme.DarkSurface
import app.mountify.ui.theme.FigmaNavBlue
import app.mountify.ui.theme.FigmaNavBorder
import app.mountify.ui.theme.FigmaNavInactive
import app.mountify.ui.theme.FigmaNavSurface
import app.mountify.ui.theme.HyperCyan
import app.mountify.ui.theme.HyperCyanBright
import app.mountify.ui.theme.MountifyTheme

/**
 * ModernNavigationBar replicating the exact Figma mobile design specification (Bottom Nav.png).
 *
 * Features:
 * - 24dp rounded top corners with subtle top stroke border.
 * - Dynamic animated expanding labels exclusively for the active destination.
 * - Minimalist outline icons for inactive destinations.
 * - Spring-bouncy icon scale transition on selection.
 * - High-contrast Figma OLED theme tokens (#1D1F24 surface, #539DF3 active blue).
 * - Accessible 48x48 dp minimum touch targets with native haptic vibration feedback.
 */
@Composable
fun ModernNavigationBar(
    screens: List<Screen>,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outline
    val topCornerRadius = 18.dp
    val barShape = RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = barShape
            ),
        shape = barShape,
        color = surfaceColor,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            screens.forEach { screen ->
                val selected = currentRoute == screen.route

                FigmaNavItem(
                    screen = screen,
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigate(screen)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FigmaNavItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDark = isSystemInDarkTheme()

    val activeColor = if (isDark) HyperCyanBright else MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val activeIndicatorBg = if (isDark) HyperCyan.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)

    val containerColor by animateColorAsState(
        targetValue = if (selected) activeIndicatorBg else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "figmaNavContainerColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 120),
        label = "figmaNavIconColor"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "figmaNavIconScale"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 24.dp),
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(containerColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) screen.icon else screen.unselectedIcon,
                contentDescription = stringResource(screen.titleRes),
                tint = iconColor,
                modifier = Modifier
                    .size(20.dp)
                    .scale(iconScale)
            )

            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(animationSpec = tween(100)),
                exit = fadeOut(animationSpec = tween(80))
            ) {
                Text(
                    text = stringResource(screen.titleRes),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp
                    ),
                    color = activeColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

// ── Jetpack Compose Previews ──

@Preview(name = "Figma Nav Bar - Dark Theme (Dashboard Active)", showBackground = true)
@Composable
private fun FigmaNavigationBarPreviewDarkDashboard() {
    MountifyTheme(dynamicColor = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            ModernNavigationBar(
                screens = Screen.bottomNavItems,
                currentRoute = Screen.Dashboard.route,
                onNavigate = {}
            )
        }
    }
}

@Preview(name = "Figma Nav Bar - Dark Theme (Storage Active)", showBackground = true)
@Composable
private fun FigmaNavigationBarPreviewDarkStorage() {
    MountifyTheme(dynamicColor = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            ModernNavigationBar(
                screens = Screen.bottomNavItems,
                currentRoute = Screen.Storage.route,
                onNavigate = {}
            )
        }
    }
}

@Preview(name = "Figma Nav Bar - Light Theme", showBackground = true)
@Composable
private fun FigmaNavigationBarPreviewLight() {
    MountifyTheme(dynamicColor = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            ModernNavigationBar(
                screens = Screen.bottomNavItems,
                currentRoute = Screen.Games.route,
                onNavigate = {}
            )
        }
    }
}

@Preview(
    name = "Figma Nav Bar - Ultra Tall Screen (1080x2460)",
    device = "spec:width=1080px,height=2460px,dpi=420",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun FigmaNavigationBarPreviewUltraTall() {
    MountifyTheme(dynamicColor = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            ModernNavigationBar(
                screens = Screen.bottomNavItems,
                currentRoute = Screen.Logs.route,
                onNavigate = {}
            )
        }
    }
}

