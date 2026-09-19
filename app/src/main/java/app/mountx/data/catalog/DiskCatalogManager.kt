package app.mountx.data.catalog

import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountMode
import app.mountx.root.MountManager
import app.mountx.root.RootShell
import app.mountx.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data structure representing the portable disk catalog stored at $sdBase/.mountx/catalog.json
 */
data class DiskCatalog(
    val version: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis(),
    val games: List<DiskCatalogGameEntry> = emptyList()
)

data class DiskCatalogGameEntry(
    val packageName: String,
    val displayName: String,
    val mode: String, // "PKG" or "FILES"
    val isEnabled: Boolean = true,
    val lastKnownSizeBytes: Long = 0L,
    val lastMountedAt: Long = 0L
)

enum class DiscoverySource {
    CATALOG_ENTRY,
    SHALLOW_SCAN
}

data class DiscoveredGame(
    val packageName: String,
    val displayName: String,
    val mode: MountMode,
    val source: DiscoverySource,
    val isInstalledOnDevice: Boolean,
    val isAlreadyRegistered: Boolean,
    val hasDataOnSd: Boolean,
    val hasObbOnSd: Boolean,
    val sizeBytes: Long = 0L,
    val canaryPresent: Boolean = false
)

/**
 * Manages portable MicroSD game discovery, atomic catalog file persistence,
 * and 1-click dynamic UID/GID & SELinux reconciliation across Android devices.
 */
@Singleton
class DiskCatalogManager @Inject constructor(
    private val mountManager: MountManager
) {

    companion object {
        const val CATALOG_DIR = ".mountx"
        const val CATALOG_FILE = "catalog.json"
        const val CATALOG_TMP_FILE = "catalog.json.tmp"
        const val CANARY_FILE = ".mountx_canary"
    }

    /**
     * Reads the portable catalog file from the MicroSD root.
     */
    suspend fun readCatalog(sdBase: String): DiskCatalog? = withContext(Dispatchers.IO) {
        val catalogPath = "$sdBase/$CATALOG_DIR/$CATALOG_FILE"
        val contentRes = RootShell.exec("cat \"$catalogPath\" 2>/dev/null")
        if (!contentRes.isSuccess || contentRes.output.isBlank()) {
            return@withContext null
        }
        try {
            val json = JSONObject(contentRes.output.trim())
            val version = json.optInt("version", 1)
            val lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
            val gamesArray = json.optJSONArray("games") ?: JSONArray()
            val games = mutableListOf<DiskCatalogGameEntry>()
            for (i in 0 until gamesArray.length()) {
                val item = gamesArray.getJSONObject(i)
                games.add(
                    DiskCatalogGameEntry(
                        packageName = item.getString("packageName"),
                        displayName = item.optString("displayName", item.getString("packageName")),
                        mode = item.optString("mode", "PKG"),
                        isEnabled = item.optBoolean("isEnabled", true),
                        lastKnownSizeBytes = item.optLong("lastKnownSizeBytes", 0L),
                        lastMountedAt = item.optLong("lastMountedAt", 0L)
                    )
                )
            }
            DiskCatalog(version = version, lastUpdated = lastUpdated, games = games)
        } catch (e: Exception) {
            AppLogger.error("DiskCatalog", "Failed to parse catalog: ${e.message}")
            null
        }
    }

    /**
     * Atomically writes the portable catalog file using temporary file and atomic rename (mv -f).
     * Prevents partial writes or corruption if phone is powered off or SD is ejected during write.
     */
    suspend fun saveCatalog(sdBase: String, catalog: DiskCatalog): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dirPath = "$sdBase/$CATALOG_DIR"
            val tmpPath = "$sdBase/$CATALOG_DIR/$CATALOG_TMP_FILE"
            val finalPath = "$sdBase/$CATALOG_DIR/$CATALOG_FILE"

            val json = JSONObject().apply {
                put("version", catalog.version)
                put("lastUpdated", catalog.lastUpdated)
                val gamesArr = JSONArray()
                for (g in catalog.games) {
                    val gObj = JSONObject().apply {
                        put("packageName", g.packageName)
                        put("displayName", g.displayName)
                        put("mode", g.mode)
                        put("isEnabled", g.isEnabled)
                        put("lastKnownSizeBytes", g.lastKnownSizeBytes)
                        put("lastMountedAt", g.lastMountedAt)
                    }
                    gamesArr.put(gObj)
                }
                put("games", gamesArr)
            }

            RootShell.exec("mkdir -p \"$dirPath\" 2>/dev/null")
            val escapedJson = json.toString(2).replace("'", "'\\''")
            val writeCmd = "echo '$escapedJson' > \"$tmpPath\" && sync && mv -f \"$tmpPath\" \"$finalPath\""
            val result = RootShell.exec(writeCmd)
            if (!result.isSuccess) {
                error("Failed atomic catalog write: ${result.output}")
            }
            AppLogger.info("DiskCatalog", "Atomically saved catalog (${catalog.games.size} games) to $finalPath")
        }
    }

    /**
     * Fast shallow scan of MicroSD Android/data and Android/obb directories.
     * Merges with catalog.json entries without deep recursive file system traversal.
     */
    suspend fun scanSdCardForGames(
        sdBase: String,
        registeredPackages: Set<String>,
        installedApps: Map<String, String> // packageName -> displayName
    ): List<DiscoveredGame> = withContext(Dispatchers.IO) {
        val detected = mutableMapOf<String, DiscoveredGame>()

        // 1. Read catalog.json if present
        val catalog = readCatalog(sdBase)
        if (catalog != null) {
            for (cg in catalog.games) {
                val isInstalled = installedApps.containsKey(cg.packageName)
                val isRegistered = registeredPackages.contains(cg.packageName)
                val hasData = RootShell.exists("$sdBase/Android/data/${cg.packageName}")
                val hasObb = RootShell.exists("$sdBase/Android/obb/${cg.packageName}")
                val canary = RootShell.exists("$sdBase/Android/data/${cg.packageName}/$CANARY_FILE")

                val mode = try {
                    MountMode.valueOf(cg.mode)
                } catch (_: Exception) {
                    MountMode.PKG
                }

                detected[cg.packageName] = DiscoveredGame(
                    packageName = cg.packageName,
                    displayName = installedApps[cg.packageName] ?: cg.displayName,
                    mode = mode,
                    source = DiscoverySource.CATALOG_ENTRY,
                    isInstalledOnDevice = isInstalled,
                    isAlreadyRegistered = isRegistered,
                    hasDataOnSd = hasData,
                    hasObbOnSd = hasObb,
                    sizeBytes = cg.lastKnownSizeBytes,
                    canaryPresent = canary
                )
            }
        }

        // 2. Shallow scan of Android/data
        val dataRes = RootShell.exec("ls -1 \"$sdBase/Android/data\" 2>/dev/null")
        if (dataRes.isSuccess) {
            for (pkg in dataRes.stdout) {
                val cleanPkg = pkg.trim()
                if (cleanPkg.isBlank() || cleanPkg.startsWith(".")) continue
                if (!cleanPkg.contains(".")) continue // filter non-packages

                val isInstalled = installedApps.containsKey(cleanPkg)
                val isRegistered = registeredPackages.contains(cleanPkg)
                val hasObb = RootShell.exists("$sdBase/Android/obb/$cleanPkg")
                val canary = RootShell.exists("$sdBase/Android/data/$cleanPkg/$CANARY_FILE")

                val existing = detected[cleanPkg]
                if (existing == null) {
                    detected[cleanPkg] = DiscoveredGame(
                        packageName = cleanPkg,
                        displayName = installedApps[cleanPkg] ?: cleanPkg,
                        mode = MountMode.PKG,
                        source = DiscoverySource.SHALLOW_SCAN,
                        isInstalledOnDevice = isInstalled,
                        isAlreadyRegistered = isRegistered,
                        hasDataOnSd = true,
                        hasObbOnSd = hasObb,
                        canaryPresent = canary
                    )
                } else {
                    detected[cleanPkg] = existing.copy(hasDataOnSd = true, canaryPresent = canary || existing.canaryPresent)
                }
            }
        }

        // 3. Shallow scan of Android/obb
        val obbRes = RootShell.exec("ls -1 \"$sdBase/Android/obb\" 2>/dev/null")
        if (obbRes.isSuccess) {
            for (pkg in obbRes.stdout) {
                val cleanPkg = pkg.trim()
                if (cleanPkg.isBlank() || cleanPkg.startsWith(".")) continue
                if (!cleanPkg.contains(".")) continue

                val isInstalled = installedApps.containsKey(cleanPkg)
                val isRegistered = registeredPackages.contains(cleanPkg)
                val hasData = RootShell.exists("$sdBase/Android/data/$cleanPkg")
                val canary = RootShell.exists("$sdBase/Android/data/$cleanPkg/$CANARY_FILE")

                val existing = detected[cleanPkg]
                if (existing == null) {
                    detected[cleanPkg] = DiscoveredGame(
                        packageName = cleanPkg,
                        displayName = installedApps[cleanPkg] ?: cleanPkg,
                        mode = MountMode.PKG,
                        source = DiscoverySource.SHALLOW_SCAN,
                        isInstalledOnDevice = isInstalled,
                        isAlreadyRegistered = isRegistered,
                        hasDataOnSd = hasData,
                        hasObbOnSd = true,
                        canaryPresent = canary
                    )
                } else {
                    detected[cleanPkg] = existing.copy(hasObbOnSd = true)
                }
            }
        }

        detected.values.toList()
    }

    /**
     * Reconciles game ownership and SELinux context on the current device.
     * Uses dynamic UID/GID resolution from stat /data/data/$packageName.
     */
    suspend fun reconcileGame(sdBase: String, game: GameEntry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val identity = mountManager.resolveAppIdentity(game.packageName)
            val uid = identity.uid
            val gid = identity.gid

            val dataDir = "$sdBase/Android/data/${game.packageName}"
            val obbDir = "$sdBase/Android/obb/${game.packageName}"

            // Fix permissions with dynamic UID & GID
            if (RootShell.exists(dataDir)) {
                RootShell.exec("chown -R $uid:$gid \"$dataDir\" 2>/dev/null")
                RootShell.exec("chmod -R 775 \"$dataDir\" 2>/dev/null")
                RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$dataDir\" 2>/dev/null")
                RootShell.exec("touch \"$dataDir/$CANARY_FILE\" 2>/dev/null")
            }

            if (RootShell.exists(obbDir)) {
                RootShell.exec("chown -R $uid:$gid \"$obbDir\" 2>/dev/null")
                RootShell.exec("chmod -R 775 \"$obbDir\" 2>/dev/null")
                RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$obbDir\" 2>/dev/null")
            }

            // Create root canary marker
            RootShell.exec("mkdir -p \"$sdBase/$CATALOG_DIR\" 2>/dev/null")
            RootShell.exec("touch \"$sdBase/$CANARY_FILE\" 2>/dev/null")

            AppLogger.success("DiskCatalog", "Reconciled permissions for ${game.packageName} [UID: $uid, GID: $gid]")
        }
    }

    /**
     * Synchronizes all registered games from Room DB to .mountx/catalog.json atomically.
     */
    suspend fun syncCatalogFromRegisteredGames(sdBase: String, games: List<GameEntry>): Result<Unit> = withContext(Dispatchers.IO) {
        val catalogEntries = games.map { g ->
            DiskCatalogGameEntry(
                packageName = g.packageName,
                displayName = g.displayName,
                mode = g.mode.name,
                isEnabled = g.isEnabled,
                lastKnownSizeBytes = g.dataSizeBytes,
                lastMountedAt = if (g.mountStatus == app.mountx.data.model.MountStatus.MOUNTED) System.currentTimeMillis() else 0L
            )
        }
        val catalog = DiskCatalog(
            version = 1,
            lastUpdated = System.currentTimeMillis(),
            games = catalogEntries
        )
        saveCatalog(sdBase, catalog)
    }
}
