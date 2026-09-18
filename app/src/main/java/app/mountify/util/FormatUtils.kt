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
}
