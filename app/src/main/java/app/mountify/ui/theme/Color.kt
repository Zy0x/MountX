package app.mountify.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── 1. Vibrant Brand & Accent Palette (Cyber Aurora) ──
val ElectricIndigo = Color(0xFF6366F1)
val ElectricIndigoDark = Color(0xFF4F46E5)
val ElectricIndigoLight = Color(0xFF818CF8)

val HyperCyan = Color(0xFF06B6D4)
val HyperCyanBright = Color(0xFF00E5FF)
val HyperCyanDark = Color(0xFF0891B2)

// ── 2. Dynamic Status Accents ──
val CyberEmerald = Color(0xFF00F59B)
val EmeraldActive = Color(0xFF10B981)
val EmeraldGlow = Color(0x2600F59B)

val SunsetAmber = Color(0xFFF59E0B)
val AmberWarn = Color(0xFFF59E0B)
val AmberGlow = Color(0x26F59E0B)

val NeonCrimson = Color(0xFFFF3B5C)
val CoralError = Color(0xFFEF4444)
val CrimsonGlow = Color(0x26FF3B5C)

// ── 3. Cyber Midnight System (Dark Theme) ──
val CyberBgDark = Color(0xFF090D16)
val CyberSurfaceDark = Color(0xFF111726)
val CyberSurfaceVariantDark = Color(0xFF1A2238)
val CyberBorderDark = Color(0xFF222C44)
val CyberBorderHighlightDark = Color(0xFF334266)
val CyberOnBgDark = Color(0xFFF1F5F9)
val CyberOnSurfaceDark = Color(0xFFE2E8F0)
val CyberOnVariantDark = Color(0xFF94A3B8)

// ── 4. Frost Slate System (Light Theme) ──
val FrostBgLight = Color(0xFFF6F8FC)
val FrostSurfaceLight = Color(0xFFFFFFFF)
val FrostSurfaceVariantLight = Color(0xFFEDF2F9)
val FrostBorderLight = Color(0xFFE2E8F0)
val FrostBorderHighlightLight = Color(0xFFCBD5E1)
val FrostOnBgLight = Color(0xFF0F172A)
val FrostOnSurfaceLight = Color(0xFF1E293B)
val FrostOnVariantLight = Color(0xFF64748B)

// ── 5. Reusable Gradient Brushes ──
val AuroraGradientBrush = Brush.horizontalGradient(
    colors = listOf(ElectricIndigo, HyperCyan)
)

val AuroraGradientBrushLight = Brush.horizontalGradient(
    colors = listOf(ElectricIndigoDark, HyperCyanDark)
)

val StorageGradientBrush = Brush.horizontalGradient(
    colors = listOf(ElectricIndigo, HyperCyan, CyberEmerald)
)

val CardGlowTopBrush = Brush.verticalGradient(
    colors = listOf(CyberBorderHighlightDark, CyberSurfaceDark)
)

// ── 6. Legacy & Semantic Aliases (Maintained for Backward Compatibility) ──
val PrimaryBlue = ElectricIndigoDark
val PrimaryBlueDark = Color(0xFF3730A3)
val PrimaryBlueLight = ElectricIndigoLight

val SecondaryTeal = HyperCyan
val SecondaryTealDark = HyperCyanDark
val SecondaryTealLight = HyperCyanBright

val StatusSuccess = EmeraldActive
val StatusSuccessContainer = Color(0xFFD3F9D8)
val StatusError = CoralError
val StatusErrorContainer = Color(0xFFFFE3E3)
val StatusWarning = AmberWarn
val StatusWarningContainer = Color(0xFFFFF3BF)

val DarkBackground = CyberBgDark
val DarkSurface = CyberSurfaceDark
val DarkSurfaceVariant = CyberSurfaceVariantDark
val DarkOutline = CyberBorderDark
val DarkOnBackground = CyberOnBgDark
val DarkOnSurface = CyberOnSurfaceDark

val LightBackground = FrostBgLight
val LightSurface = FrostSurfaceLight
val LightSurfaceVariant = FrostSurfaceVariantLight
val LightOutline = FrostBorderLight
val LightOnBackground = FrostOnBgLight
val LightOnSurface = FrostOnSurfaceLight

val FigmaNavSurface = CyberSurfaceDark
val FigmaNavBlue = HyperCyan
val FigmaNavInactive = CyberOnVariantDark
val FigmaNavBorder = CyberBorderDark

// Obsidian aliases remapped to high-end Cyber Modern tokens
val ObsidianBg = CyberBgDark
val ObsidianCard = CyberSurfaceDark
val ObsidianBorder = CyberBorderDark
val ElectricCyan = HyperCyan
val ElectricCyanBright = HyperCyanBright

