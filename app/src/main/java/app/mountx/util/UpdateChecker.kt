package app.mountx.util

import app.mountx.BuildConfig
import app.mountx.data.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    private val appPreferences: AppPreferences
) {

    companion object {
        private const val JSDELIVR_URL = "https://cdn.jsdelivr.net/gh/Zy0x/MountX@main/version.json"
        private const val RAW_CDN_URL = "https://raw.githubusercontent.com/Zy0x/MountX/main/version.json"
        private const val GITHUB_API_URL = "https://api.github.com/repos/Zy0x/MountX/releases/latest"
        const val AUTO_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 hours throttle
    }

    /**
     * Checks for app updates.
     * When [force] is false, uses client-side throttling (12 hours) and checks cached static CDN first
     * to eliminate GitHub API rate limits across thousands of concurrent devices.
     */
    suspend fun checkForUpdate(force: Boolean = false): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val lastCheck = runCatching { appPreferences.lastUpdateCheckTime.first() }.getOrDefault(0L)
            val now = System.currentTimeMillis()

            if (!force && (now - lastCheck < AUTO_CHECK_INTERVAL_MS) && lastCheck > 0L) {
                return@runCatching UpdateInfo(
                    latestVersion = BuildConfig.VERSION_NAME,
                    downloadUrl = "",
                    changelog = "",
                    isUpdateAvailable = false
                )
            }

            // 1. First attempt: CDN / Raw static JSON (unlimited rate-limit edge caching)
            val cdnInfo = fetchFromStaticCdn(JSDELIVR_URL)
                ?: fetchFromStaticCdn(RAW_CDN_URL)

            val updateInfo = if (cdnInfo != null) {
                cdnInfo
            } else {
                // 2. Fallback: GitHub Releases REST API
                fetchFromGitHubApi()
            }

            // Record successful check timestamp
            runCatching { appPreferences.setLastUpdateCheckTime(now) }
            updateInfo
        }
    }

    private fun fetchFromStaticCdn(endpoint: String): UpdateInfo? {
        return try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MountX-App")
                connectTimeout = 4000
                readTimeout = 4000
            }

            if (conn.responseCode != 200) return null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val version = json.optString("version", "").removePrefix("v")
            val changelog = json.optString("changelog", "").ifBlank { json.optString("summary", "") }
            val apkUrl = json.optString("apkUrl", "")

            val currentVersion = BuildConfig.VERSION_NAME
            val isAvailable = isNewerVersion(version, currentVersion)

            UpdateInfo(
                latestVersion = version,
                downloadUrl = if (apkUrl.isNotBlank()) apkUrl else "https://github.com/Zy0x/MountX/releases/latest",
                changelog = changelog,
                isUpdateAvailable = isAvailable
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromGitHubApi(): UpdateInfo {
        val url = URL(GITHUB_API_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "MountX-App")
            connectTimeout = 7000
            readTimeout = 7000
        }

        if (conn.responseCode != 200) {
            error("GitHub API returned HTTP ${conn.responseCode}")
        }

        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)

        val tagName = json.optString("tag_name", "").removePrefix("v")
        val changelog = json.optString("body", "")

        // Direct users to GitHub latest release page to increase repository visits, stars, and engagement
        val htmlUrl = json.optString("html_url", "")
        val downloadUrl = if (htmlUrl.isNotBlank()) htmlUrl else "https://github.com/Zy0x/MountX/releases/latest"

        val currentVersion = BuildConfig.VERSION_NAME
        val isAvailable = isNewerVersion(tagName, currentVersion)

        return UpdateInfo(
            latestVersion = tagName,
            downloadUrl = downloadUrl,
            changelog = changelog,
            isUpdateAvailable = isAvailable
        )
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        if (remote.isBlank() || local.isBlank()) return false
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val lParts = local.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(rParts.size, lParts.size)
        for (i in 0 until length) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
