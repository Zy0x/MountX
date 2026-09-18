package app.mountify.util

import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {

    /**
     * Format byte sizes to human-readable strings (B, KB, MB, GB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / 1024.0.pow(index.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[index]
    }

    /**
     * Format byte sizes with 2 decimal places and no space before unit (e.g. 9.50MB, 4.00KB).
     * Defaults to KB minimum for compact telemetry readouts.
     */
    fun formatExactBytes(bytes: Long): String {
        if (bytes <= 0) return "0.00KB"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(1, units.size - 1)
        val value = bytes / 1024.0.pow(index.toDouble())
        return DecimalFormat("#,##0.00").format(value) + units[index]
    }

    /**
     * Format byte sizes with 1 decimal place and no space before unit (e.g. 25.9MB, 4.0KB).
     * Defaults to KB minimum for chart legend.
     */
    fun formatLegendBytes(bytes: Long): String {
        if (bytes <= 0) return "0.0KB"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(1, units.size - 1)
        val value = bytes / 1024.0.pow(index.toDouble())
        return DecimalFormat("#,##0.0").format(value) + units[index]
    }

    // ── 4-Tier Health Gradient (Traffic Light Dynamics) ───────────────────
    // 0–60%   : Stable Green (#10B981)
    // 60–80%  : Green -> Yellow (#EAB308)
    // 80–90%  : Yellow -> Orange (#F97316)
    // 90–100% : Orange -> Red (#EF4444)

    private val HealthGreen = androidx.compose.ui.graphics.Color(0xFF10B981)
    private val HealthYellow = androidx.compose.ui.graphics.Color(0xFFEAB308)
    private val HealthOrange = androidx.compose.ui.graphics.Color(0xFFF97316)
    private val HealthRed = androidx.compose.ui.graphics.Color(0xFFEF4444)

    private fun interpolateColor(
        c1: androidx.compose.ui.graphics.Color,
        c2: androidx.compose.ui.graphics.Color,
        fraction: Float
    ): androidx.compose.ui.graphics.Color {
        val f = fraction.coerceIn(0f, 1f)
        return androidx.compose.ui.graphics.Color(
            red = c1.red + (c2.red - c1.red) * f,
            green = c1.green + (c2.green - c1.green) * f,
            blue = c1.blue + (c2.blue - c1.blue) * f,
            alpha = c1.alpha + (c2.alpha - c1.alpha) * f
        )
    }

    fun getHealthColor(usedFraction: Float): androidx.compose.ui.graphics.Color {
        val f = usedFraction.coerceIn(0f, 1f)
        return when {
            f <= 0.60f -> HealthGreen
            f <= 0.80f -> interpolateColor(HealthGreen, HealthYellow, (f - 0.60f) / 0.20f)
            f <= 0.90f -> interpolateColor(HealthYellow, HealthOrange, (f - 0.80f) / 0.10f)
            else -> interpolateColor(HealthOrange, HealthRed, (f - 0.90f) / 0.10f)
        }
    }

    /**
     * Generates a continuous 4-tier traffic-light health gradient mapped to the filled portion of a bar:
     * - 0–60%   : Stable Green (#10B981)
     * - 60–80%  : Smooth transition Green -> Yellow (#EAB308)
     * - 80–90%  : Smooth transition Yellow -> Orange (#F97316)
     * - 90–100% : Smooth transition Orange -> Red (#EF4444)
     */
    fun getHealthBrush(usedFraction: Float): androidx.compose.ui.graphics.Brush {
        val f = usedFraction.coerceIn(0.001f, 1f)
        val endColor = getHealthColor(f)

        val stops = when {
            f <= 0.60f -> arrayOf(
                0.0f to HealthGreen,
                1.0f to HealthGreen
            )
            f <= 0.80f -> arrayOf(
                0.0f to HealthGreen,
                (0.60f / f).coerceIn(0f, 1f) to HealthGreen,
                1.0f to endColor
            )
            f <= 0.90f -> arrayOf(
                0.0f to HealthGreen,
                (0.60f / f).coerceIn(0f, 1f) to HealthGreen,
                (0.80f / f).coerceIn(0f, 1f) to HealthYellow,
                1.0f to endColor
            )
            else -> arrayOf(
                0.0f to HealthGreen,
                (0.60f / f).coerceIn(0f, 1f) to HealthGreen,
                (0.80f / f).coerceIn(0f, 1f) to HealthYellow,
                (0.90f / f).coerceIn(0f, 1f) to HealthOrange,
                1.0f to endColor
            )
        }
        return androidx.compose.ui.graphics.Brush.horizontalGradient(*stops)
    }

    /**
     * Full-span horizontal gradient covering the entire 0% to 100% scale.
     */
    fun getFullHealthBrush(): androidx.compose.ui.graphics.Brush {
        return androidx.compose.ui.graphics.Brush.horizontalGradient(
            0.0f to HealthGreen,
            0.60f to HealthGreen,
            0.80f to HealthYellow,
            0.90f to HealthOrange,
            1.0f to HealthRed
        )
    }
}
