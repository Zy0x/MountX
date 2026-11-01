package app.mountx.data.diagnostics

import android.content.Context
import android.os.Build
import app.mountx.data.db.AppDao
import app.mountx.data.model.MountStatus
import app.mountx.root.RootShell
import app.mountx.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Historical process exit report extracted from Android ActivityManager.
 */
data class AppExitReport(
    val packageName: String,
    val processName: String,
    val timestamp: String,
    val reasonCode: Int,
    val reasonName: String,
    val subreasonName: String,
    val description: String,
    val pss: String,
    val rss: String,
    val isAbnormal: Boolean
)

/**
 * Live health assessment for a registered app.
 */
data class AppHealthTelemetry(
    val packageName: String,
    val isMounted: Boolean,
    val isHealthy: Boolean,
    val activeMountsCount: Int,
    val storagePermissionsGranted: Boolean,
    val recentExitReport: AppExitReport?,
    val statusSummary: String
)

/**
 * Result of comprehensive system self-test.
 */
data class SystemSelfTestResult(
    val isOverallHealthy: Boolean,
    val rootShellLatencyMs: Long,
    val selinuxMode: String,
    val isMicroSdWritable: Boolean,
    val microSdFreeMb: Long,
    val isWatchdogActive: Boolean,
    val watchdogPid: String,
    val totalApps: Int,
    val healthyAppsCount: Int,
    val issuesList: List<String>
)

@Singleton
class DiagnosticManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDao: AppDao
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Extracts the most recent ApplicationExitInfo for a given package name.
     */
    suspend fun getRecentExitReport(packageName: String): AppExitReport? = withContext(Dispatchers.IO) {
        runCatching {
            val cmd = "dumpsys activity exit-info \"$packageName\" 2>/dev/null"
            val output = RootShell.execForOutput(cmd)
            if (output.isBlank() || !output.contains("ApplicationExitInfo #")) {
                return@runCatching null
            }

            // Split by ApplicationExitInfo blocks
            val blocks = output.split(Regex("(?=ApplicationExitInfo #\\d+:)"))
            for (block in blocks) {
                if (!block.contains("ApplicationExitInfo #")) continue

                val tsMatch = Regex("""timestamp=([0-9\-]+ [0-9:.]+)""").find(block)
                val procMatch = Regex("""process=(\S+)""").find(block)
                val reasonMatch = Regex("""reason=(\d+)\s*\(([^)]+)\)""").find(block)
                val subreasonMatch = Regex("""subreason=\d+\s*\(([^)]+)\)""").find(block)
                val descMatch = Regex("""description=(.*?)(?=\s+state=|\s+trace=|\z)""").find(block)
                val pssMatch = Regex("""pss=([^\s]+)""").find(block)
                val rssMatch = Regex("""rss=([^\s]+)""").find(block)

                val proc = procMatch?.groupValues?.get(1) ?: packageName
                // We care about the main app process or services, not isolated webview sandboxes
                if (proc.contains("sandboxed_process", ignoreCase = true)) {
                    continue
                }

                val timestamp = tsMatch?.groupValues?.get(1) ?: ""
                val reasonCode = reasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val reasonName = reasonMatch?.groupValues?.get(2) ?: "UNKNOWN"
                val subreasonName = subreasonMatch?.groupValues?.get(1) ?: "UNKNOWN"
                val rawDesc = descMatch?.groupValues?.get(1)?.trim() ?: ""
                val desc = if (rawDesc == "null") "" else rawDesc
                val pss = pssMatch?.groupValues?.get(1) ?: "0"
                val rss = rssMatch?.groupValues?.get(1) ?: "0"

                // Determine abnormality:
                // Normal exits: USER REQUESTED (10), EXIT SELF (1)
                // Abnormal: CRASH (4), CRASH_NATIVE (5), ANR (6), OTHER KILLS BY SYSTEM (13) with KILL UID (11), LOW_MEMORY (3)
                val isAbnormal = when {
                    reasonName.equals("USER REQUESTED", ignoreCase = true) -> false
                    reasonName.equals("EXIT_SELF", ignoreCase = true) -> false
                    reasonName.equals("OTHER KILLS BY SYSTEM", ignoreCase = true) &&
                            subreasonName.equals("ISOLATED NOT NEEDED", ignoreCase = true) -> false
                    reasonName.equals("OTHER KILLS BY SYSTEM", ignoreCase = true) &&
                            subreasonName.equals("KILL UID", ignoreCase = true) -> true
                    reasonName.contains("CRASH", ignoreCase = true) -> true
                    reasonName.contains("ANR", ignoreCase = true) -> true
                    reasonName.contains("LOW_MEMORY", ignoreCase = true) -> true
                    desc.contains("MANAGE_EXTERNAL_STORAGE", ignoreCase = true) -> true
                    else -> false
                }

                return@runCatching AppExitReport(
                    packageName = packageName,
                    processName = proc,
                    timestamp = timestamp,
                    reasonCode = reasonCode,
                    reasonName = reasonName,
                    subreasonName = subreasonName,
                    description = desc,
                    pss = pss,
                    rss = rss,
                    isAbnormal = isAbnormal
                )
            }
            null
        }.getOrNull()
    }

    /**
     * Aggregates live health telemetry for a registered app.
     */
    suspend fun getAppHealth(packageName: String): AppHealthTelemetry = withContext(Dispatchers.IO) {
        val app = appDao.getAppByPackage(packageName)
        val isMounted = app?.mountStatus == MountStatus.MOUNTED

        // Check active /proc/mounts targets for this package
        val mountsCountRes = RootShell.exec("grep -E \"(Android/(data|obb|media)/${packageName}|/data/app/.*${packageName}|/data/user/0/${packageName})\" /proc/mounts 2>/dev/null | wc -l")
        val activeMounts = mountsCountRes.output.trim().toIntOrNull() ?: 0

        // Check storage permissions
        val appopsRes = RootShell.exec("appops get \"$packageName\" MANAGE_EXTERNAL_STORAGE 2>/dev/null; appops get \"$packageName\" LEGACY_STORAGE 2>/dev/null")
        val permissionsGranted = !appopsRes.output.contains("reject", ignoreCase = true)

        val exitReport = getRecentExitReport(packageName)

        val isHealthy = when {
            isMounted && activeMounts == 0 -> false
            exitReport?.isAbnormal == true -> false
            !permissionsGranted -> false
            else -> true
        }

        val summary = when {
            isMounted && activeMounts == 0 -> "Mount targets missing in /proc/mounts"
            exitReport != null && exitReport.isAbnormal -> {
                val detail = if (exitReport.description.isNotBlank()) ": ${exitReport.description}" else ""
                "Terminated: ${exitReport.reasonName} (${exitReport.subreasonName}$detail)"
            }
            !permissionsGranted -> "Storage permission revoked by Android"
            isMounted -> "Mounted & Healthy ($activeMounts active targets)"
            else -> "Unmounted / Idle"
        }

        AppHealthTelemetry(
            packageName = packageName,
            isMounted = isMounted,
            isHealthy = isHealthy,
            activeMountsCount = activeMounts,
            storagePermissionsGranted = permissionsGranted,
            recentExitReport = exitReport,
            statusSummary = summary
        )
    }

    /**
     * Executes an instant system self-test verifying all MountX components.
     */
    suspend fun runSelfTest(): SystemSelfTestResult = withContext(Dispatchers.IO) {
        val issues = mutableListOf<String>()

        // 1. Root Shell Latency Test
        val startNs = System.nanoTime()
        val echoRes = RootShell.exec("echo 'pong'")
        val latencyMs = ((System.nanoTime() - startNs) / 1_000_000).coerceAtLeast(1)
        if (!echoRes.isSuccess || !echoRes.output.contains("pong")) {
            issues.add("Root shell access failed or timed out.")
        }

        // 2. SELinux Mode
        val selinuxMode = RootShell.execForOutput("getenforce 2>/dev/null").trim().ifBlank { "Unknown" }

        // 3. MicroSD RW and Space
        val sdBase = "/data/sdext2"
        val dfRes = RootShell.exec("df -m \"$sdBase\" 2>/dev/null | tail -n 1")
        val freeMb = dfRes.output.trim().split(Regex("\\s+")).getOrNull(3)?.toLongOrNull() ?: 0L

        val rwCheck = RootShell.exec("touch \"$sdBase/.mountx_selftest\" 2>/dev/null && rm -f \"$sdBase/.mountx_selftest\" 2>/dev/null")
        val isWritable = rwCheck.isSuccess
        if (!isWritable) {
            issues.add("MicroSD storage partition is read-only or not writable.")
        }
        if (freeMb < 500) {
            issues.add("MicroSD storage space critical: only ${freeMb}MB remaining.")
        }

        // 4. Watchdog Process Status
        val pgrepWatchdog = RootShell.exec("pgrep -f \"service.sh\" 2>/dev/null || pgrep -f \"mount_watchdog\" 2>/dev/null")
        val isWatchdogActive = pgrepWatchdog.isSuccess && pgrepWatchdog.output.isNotBlank()
        val watchdogPid = pgrepWatchdog.output.trim().lines().firstOrNull() ?: "None"
        if (!isWatchdogActive) {
            issues.add("Watchdog daemon service.sh is not actively running.")
        }

        // 5. Apps Status
        val apps = appDao.getAllAppsSync()
        var healthyApps = 0
        for (app in apps) {
            val health = getAppHealth(app.packageName)
            if (health.isHealthy) {
                healthyApps++
            } else {
                issues.add("${app.displayName}: ${health.statusSummary}")
            }
        }

        SystemSelfTestResult(
            isOverallHealthy = issues.isEmpty(),
            rootShellLatencyMs = latencyMs,
            selinuxMode = selinuxMode,
            isMicroSdWritable = isWritable,
            microSdFreeMb = freeMb,
            isWatchdogActive = isWatchdogActive,
            watchdogPid = watchdogPid,
            totalApps = apps.size,
            healthyAppsCount = healthyApps,
            issuesList = issues
        )
    }

    /**
     * Generates a comprehensive, formatted Markdown system diagnostic report.
     */
    suspend fun generateSystemDiagnosticReport(): String = withContext(Dispatchers.IO) {
        val now = dateFormat.format(Date())
        val sb = StringBuilder()

        sb.appendLine("### MountX System Diagnostic Report")
        sb.appendLine("- **Generated**: $now")
        sb.appendLine("- **Device**: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        sb.appendLine("- **Android Version**: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("- **Security Patch**: ${Build.VERSION.SECURITY_PATCH}")

        val kernelInfo = RootShell.execForOutput("uname -r 2>/dev/null").trim().ifBlank { "Unknown" }
        sb.appendLine("- **Kernel**: $kernelInfo")

        val selinux = RootShell.execForOutput("getenforce 2>/dev/null").trim().ifBlank { "Unknown" }
        sb.appendLine("- **SELinux**: $selinux")

        val rootType = when {
            RootShell.exec("which ksu 2>/dev/null").isSuccess -> "KernelSU"
            RootShell.exec("which apd 2>/dev/null").isSuccess -> "APatch"
            RootShell.exec("which magisk 2>/dev/null").isSuccess -> "Magisk"
            else -> "Root (UID 0)"
        }
        sb.appendLine("- **Root Environment**: $rootType")

        sb.appendLine()
        sb.appendLine("#### Storage & Partition Telemetry")
        val sdBase = "/data/sdext2"
        val dfOut = RootShell.execForOutput("df -h \"$sdBase\" 2>/dev/null").trim()
        sb.appendLine("```text")
        sb.appendLine(dfOut.ifBlank { "No partition mounted at $sdBase" })
        sb.appendLine("```")

        val mountLine = RootShell.execForOutput("grep -F \" $sdBase \" /proc/mounts 2>/dev/null").trim()
        sb.appendLine("- **Base Mount Options**: `${mountLine.ifBlank { "Not in /proc/mounts" }}`")

        sb.appendLine()
        sb.appendLine("#### Registered Apps Health Matrix")
        val apps = appDao.getAllAppsSync()
        if (apps.isEmpty()) {
            sb.appendLine("_No applications currently registered in MountX._")
        } else {
            for (app in apps) {
                val health = getAppHealth(app.packageName)
                val icon = if (health.isHealthy) "🟢" else "🔴"
                sb.appendLine("- $icon **${app.displayName}** (`${app.packageName}`)")
                sb.appendLine("  * Mount Status: `${app.mountStatus}` | Active Targets: ${health.activeMountsCount}")
                sb.appendLine("  * Permissions: ${if (health.storagePermissionsGranted) "Granted" else "REVOKED/RESTRICTED"}")
                if (health.recentExitReport != null) {
                    val exit = health.recentExitReport
                    val abIcon = if (exit.isAbnormal) "⚠️" else "✓"
                    sb.appendLine("  * Recent Exit: $abIcon ${exit.reasonName} (${exit.subreasonName}) at ${exit.timestamp} [PSS=${exit.pss}]")
                    if (exit.description.isNotBlank()) {
                        sb.appendLine("  * Termination Note: `${exit.description}`")
                    }
                }
            }
        }

        sb.appendLine()
        sb.appendLine("#### Recent mountx.log (Last 35 lines)")
        sb.appendLine("```text")
        val logLines = RootShell.execForOutput("tail -n 35 /data/adb/modules/MountX/mountx.log 2>/dev/null").trim()
        sb.appendLine(logLines.ifBlank { "mountx.log is empty or inaccessible" })
        sb.appendLine("```")

        sb.toString()
    }
}
