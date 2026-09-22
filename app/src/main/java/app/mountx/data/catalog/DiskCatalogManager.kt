package app.mountx.data.catalog

import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
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
    val version: Int = 2,
    val lastUpdated: Long = System.currentTimeMillis(),
    val games: List<DiskCatalogGameEntry> = emptyList()
)

data class DiskCatalogGameEntry(
    val packageName: String,
    val displayName: String,
    val mode: String = "PKG", // "PKG" or "FILES"
    val isEnabled: Boolean = true,
    val lastKnownSizeBytes: Long = 0L,
    val lastMountedAt: Long = 0L,
    val mountPoints: List<MountPointConfig> = emptyList()
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
    val canaryPresent: Boolean = false,
    val mountPoints: List<MountPointConfig> = emptyList(),
    val needsRestructure: Boolean = false,
    val originalPath: String? = null
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
            val version = json.optInt("version", 2)
            val lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
            val gamesArray = json.optJSONArray("games") ?: JSONArray()
            val games = mutableListOf<DiskCatalogGameEntry>()
            for (i in 0 until gamesArray.length()) {
                val item = gamesArray.getJSONObject(i)
                val pkgName = item.optString("package_name", item.optString("packageName", ""))
                val appName = item.optString("app_name", item.optString("displayName", pkgName))
                val modeStr = item.optString("mode", "PKG")
                val isEnabled = item.optBoolean("isEnabled", true)
                val lastKnownSizeBytes = item.optLong("lastKnownSizeBytes", item.optLong("dataSizeBytes", 0L))
                val lastMountedAt = item.optLong("lastMountedAt", 0L)

                val mpList = mutableListOf<MountPointConfig>()
                val mpArray = item.optJSONArray("mount_points") ?: item.optJSONArray("mountPoints")
                if (mpArray != null) {
                    for (j in 0 until mpArray.length()) {
                        val mpObj = mpArray.getJSONObject(j)
                        val catStr = mpObj.optString("category", "GAME_ASSETS")
                        val cat = try {
                            MountPointCategory.valueOf(catStr)
                        } catch (_: Exception) {
                            MountPointCategory.GAME_ASSETS
                        }
                        val labelVal = if (mpObj.has("label") && !mpObj.isNull("label")) mpObj.getString("label") else null
                        mpList.add(
                            MountPointConfig(
                                id = mpObj.optString("id", "mp_$j"),
                                category = cat,
                                sourcePath = mpObj.optString("source_path", mpObj.optString("sourcePath", "")),
                                targetPath = mpObj.optString("target_path", mpObj.optString("targetPath", "")),
                                enabled = mpObj.optBoolean("enabled", true),
                                isVirtualContainer = mpObj.optBoolean("is_virtual_container", mpObj.optBoolean("isVirtualContainer", false)),
                                containerImgPath = if (mpObj.has("container_img_path") && !mpObj.isNull("container_img_path")) mpObj.getString("container_img_path") else null,
                                sizeBytes = mpObj.optLong("size_bytes", 0L),
                                diskUuid = if (mpObj.has("disk_uuid") && !mpObj.isNull("disk_uuid")) mpObj.getString("disk_uuid") else if (mpObj.has("diskUuid") && !mpObj.isNull("diskUuid")) mpObj.getString("diskUuid") else null,
                                label = labelVal
                            )
                        )
                    }
                }

                games.add(
                    DiskCatalogGameEntry(
                        packageName = pkgName,
                        displayName = appName,
                        mode = modeStr,
                        isEnabled = isEnabled,
                        lastKnownSizeBytes = lastKnownSizeBytes,
                        lastMountedAt = lastMountedAt,
                        mountPoints = mpList
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
                        put("package_name", g.packageName)
                        put("app_name", g.displayName)
                        put("mode", g.mode)
                        put("isEnabled", g.isEnabled)
                        put("lastKnownSizeBytes", g.lastKnownSizeBytes)
                        put("lastMountedAt", g.lastMountedAt)

                        val mpArr = JSONArray()
                        for (mp in g.mountPoints) {
                            mpArr.put(JSONObject().apply {
                                put("id", mp.id)
                                put("category", mp.category.name)
                                put("source_path", mp.sourcePath)
                                put("target_path", mp.targetPath)
                                put("enabled", mp.enabled)
                                put("is_virtual_container", mp.isVirtualContainer)
                                if (mp.containerImgPath != null) put("container_img_path", mp.containerImgPath)
                                put("size_bytes", mp.sizeBytes)
                                if (mp.diskUuid != null) put("disk_uuid", mp.diskUuid)
                                if (mp.label != null) put("label", mp.label)
                            })
                        }
                        put("mount_points", mpArr)
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
     * Multi-Layer Smart Scan for Game Data on MicroSD.
     * Lapisan 1: Folder Standar $sdBase/MountX/Android/data & obb
     * Lapisan 2: Folder Legacy $sdBase/Android/data & obb
     * Lapisan 3: Folder Non-Standar $sdBase/Games, $sdBase/GameData
     */
    suspend fun scanSdCardForGames(
        sdBase: String,
        registeredPackages: Set<String>,
        installedApps: Map<String, String> // packageName -> displayName
    ): List<DiscoveredGame> = withContext(Dispatchers.IO) {
        val detected = mutableMapOf<String, DiscoveredGame>()

        // 1. Read catalog.json if present
        val catalog = readCatalog(sdBase)
        var catalogNeedsPrune = false
        val validCatalogGames = mutableListOf<DiskCatalogGameEntry>()
        if (catalog != null) {
            for (cg in catalog.games) {
                val isInstalled = installedApps.containsKey(cg.packageName)
                val isRegistered = registeredPackages.contains(cg.packageName)
                val hasModernData = hasValidContent("$sdBase/MountX/Android/data/${cg.packageName}")
                val hasLegacyData = hasValidContent("$sdBase/Android/data/${cg.packageName}")
                val hasModernObb = hasValidContent("$sdBase/MountX/Android/obb/${cg.packageName}")
                val hasLegacyObb = hasValidContent("$sdBase/Android/obb/${cg.packageName}")
                val hasCustomMountPoints = cg.mountPoints.any { pt ->
                    pt.sourcePath.isNotBlank() && hasValidContent(pt.sourcePath)
                }
                val hasAnyData = hasModernData || hasLegacyData || hasModernObb || hasLegacyObb || hasCustomMountPoints

                if (!hasAnyData && !isRegistered) {
                    // App has NO data on SD card and is NOT registered in MountX Room DB.
                    // Prune stale ghost entry left over from past restore/delete.
                    catalogNeedsPrune = true
                    continue
                }

                validCatalogGames.add(cg)

                if (!hasAnyData) {
                    // Do not report app as discovered on SD if it has no data on SD
                    continue
                }

                val canary = RootShell.exists("$sdBase/MountX/Android/data/${cg.packageName}/$CANARY_FILE") ||
                        RootShell.exists("$sdBase/Android/data/${cg.packageName}/$CANARY_FILE")

                val mode = try {
                    MountMode.valueOf(cg.mode)
                } catch (_: Exception) {
                    MountMode.PKG
                }

                val needsRestructure = !hasModernData && hasLegacyData
                val originalPath = if (needsRestructure) "$sdBase/Android/data/${cg.packageName}" else null

                detected[cg.packageName] = DiscoveredGame(
                    packageName = cg.packageName,
                    displayName = installedApps[cg.packageName] ?: cg.displayName,
                    mode = mode,
                    source = DiscoverySource.CATALOG_ENTRY,
                    isInstalledOnDevice = isInstalled,
                    isAlreadyRegistered = isRegistered,
                    hasDataOnSd = hasModernData || hasLegacyData,
                    hasObbOnSd = hasModernObb || hasLegacyObb,
                    sizeBytes = cg.lastKnownSizeBytes,
                    canaryPresent = canary,
                    mountPoints = cg.mountPoints,
                    needsRestructure = needsRestructure,
                    originalPath = originalPath
                )
            }

            if (catalogNeedsPrune) {
                saveCatalog(sdBase, catalog.copy(games = validCatalogGames, lastUpdated = System.currentTimeMillis()))
                AppLogger.info("DiskCatalog", "Pruned stale ghost entries from catalog.json (${catalog.games.size} -> ${validCatalogGames.size})")
            }
        }

        // 2. Lapisan 1: Standar MountX ($sdBase/MountX/Android/data & obb)
        val modernDataOut = RootShell.execForOutput("ls -1 \"$sdBase/MountX/Android/data\" 2>/dev/null")
        if (modernDataOut.isNotBlank()) {
            for (pkg in modernDataOut.lines()) {
                val cleanPkg = pkg.trim()
                if (cleanPkg.isBlank() || cleanPkg == ".mountx_canary" || cleanPkg == ".nomedia") continue
                if (detected.containsKey(cleanPkg)) continue

                val hasData = hasValidContent("$sdBase/MountX/Android/data/$cleanPkg")
                val hasObb = hasValidContent("$sdBase/MountX/Android/obb/$cleanPkg")
                if (!hasData && !hasObb) continue

                val isInstalled = installedApps.containsKey(cleanPkg)
                val isRegistered = registeredPackages.contains(cleanPkg)
                val canary = RootShell.exists("$sdBase/MountX/Android/data/$cleanPkg/$CANARY_FILE")

                detected[cleanPkg] = DiscoveredGame(
                    packageName = cleanPkg,
                    displayName = installedApps[cleanPkg] ?: cleanPkg,
                    mode = MountMode.PKG,
                    source = DiscoverySource.SHALLOW_SCAN,
                    isInstalledOnDevice = isInstalled,
                    isAlreadyRegistered = isRegistered,
                    hasDataOnSd = hasData,
                    hasObbOnSd = hasObb,
                    canaryPresent = canary,
                    needsRestructure = false
                )
            }
        }

        // 3. Lapisan 2: Legacy Android ($sdBase/Android/data & obb)
        val legacyDataOut = RootShell.execForOutput("ls -1 \"$sdBase/Android/data\" 2>/dev/null")
        if (legacyDataOut.isNotBlank()) {
            for (pkg in legacyDataOut.lines()) {
                val cleanPkg = pkg.trim()
                if (cleanPkg.isBlank() || cleanPkg == ".mountx_canary" || cleanPkg == ".nomedia") continue

                val existing = detected[cleanPkg]
                if (existing == null) {
                    val hasData = hasValidContent("$sdBase/Android/data/$cleanPkg")
                    val hasObb = hasValidContent("$sdBase/Android/obb/$cleanPkg")
                    if (!hasData && !hasObb) continue

                    val isInstalled = installedApps.containsKey(cleanPkg)
                    val isRegistered = registeredPackages.contains(cleanPkg)
                    val canary = RootShell.exists("$sdBase/Android/data/$cleanPkg/$CANARY_FILE")

                    detected[cleanPkg] = DiscoveredGame(
                        packageName = cleanPkg,
                        displayName = installedApps[cleanPkg] ?: cleanPkg,
                        mode = MountMode.PKG,
                        source = DiscoverySource.SHALLOW_SCAN,
                        isInstalledOnDevice = isInstalled,
                        isAlreadyRegistered = isRegistered,
                        hasDataOnSd = hasData,
                        hasObbOnSd = hasObb,
                        canaryPresent = canary,
                        needsRestructure = true,
                        originalPath = "$sdBase/Android/data/$cleanPkg"
                    )
                }
            }
        }

        // 4. Lapisan 3: Direktori Games / GameData non-standar
        val outerDirs = listOf("$sdBase/Games", "$sdBase/GameData")
        for (outerBase in outerDirs) {
            val outerOut = RootShell.execForOutput("ls -1 \"$outerBase\" 2>/dev/null")
            if (outerOut.isNotBlank()) {
                for (folder in outerOut.lines()) {
                    val cleanFolder = folder.trim()
                    if (cleanFolder.isBlank() || cleanFolder.startsWith(".")) continue

                    // Match against installed apps package names or app names
                    val matchedPkg = installedApps.keys.firstOrNull { pkg ->
                        pkg.equals(cleanFolder, ignoreCase = true) ||
                                (installedApps[pkg]?.equals(cleanFolder, ignoreCase = true) == true)
                    }

                    if (matchedPkg != null && !detected.containsKey(matchedPkg)) {
                        val folderPath = "$outerBase/$cleanFolder"
                        if (!hasValidContent(folderPath)) continue

                        val isRegistered = registeredPackages.contains(matchedPkg)
                        detected[matchedPkg] = DiscoveredGame(
                            packageName = matchedPkg,
                            displayName = installedApps[matchedPkg] ?: cleanFolder,
                            mode = MountMode.PKG,
                            source = DiscoverySource.SHALLOW_SCAN,
                            isInstalledOnDevice = true,
                            isAlreadyRegistered = isRegistered,
                            hasDataOnSd = true,
                            hasObbOnSd = false,
                            canaryPresent = false,
                            needsRestructure = true,
                            originalPath = folderPath
                        )
                    }
                }
            }
        }

        detected.values.toList()
    }

    /**
     * Atomically restructures game data from non-standard or legacy locations into the
     * standard MountX directory structure ($sdBase/MountX/Android/data & obb).
     * If source and destination are on the same filesystem, this completes in 0.1 seconds via mv.
     */
    suspend fun restructureGameToStandard(
        sdBase: String,
        game: DiscoveredGame,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pkg = game.packageName
            val targetDataDir = "$sdBase/MountX/Android/data/$pkg"
            val targetObbDir = "$sdBase/MountX/Android/obb/$pkg"

            onProgress(0.1f, "Menyiapkan direktori standar MountX...")
            RootShell.exec("mkdir -p \"$sdBase/MountX/Android/data\" \"$sdBase/MountX/Android/obb\" 2>/dev/null")

            val origData = if (game.originalPath != null && RootShell.exists(game.originalPath)) {
                game.originalPath
            } else if (RootShell.exists("$sdBase/Android/data/$pkg")) {
                "$sdBase/Android/data/$pkg"
            } else null

            if (origData != null && origData != targetDataDir) {
                onProgress(0.35f, "Memindahkan struktur data game ke MountX/Android/data...")
                val mvRes = RootShell.exec("mv -f \"$origData\" \"$targetDataDir\" 2>/dev/null")
                if (!mvRes.isSuccess) {
                    RootShell.exec("cp -a \"$origData\" \"$targetDataDir\" && rm -rf \"$origData\" 2>/dev/null")
                }
            }

            val origObb = "$sdBase/Android/obb/$pkg"
            if (RootShell.exists(origObb) && origObb != targetObbDir) {
                onProgress(0.65f, "Memindahkan OBB resource ke MountX/Android/obb...")
                val mvObbRes = RootShell.exec("mv -f \"$origObb\" \"$targetObbDir\" 2>/dev/null")
                if (!mvObbRes.isSuccess) {
                    RootShell.exec("cp -a \"$origObb\" \"$targetObbDir\" && rm -rf \"$origObb\" 2>/dev/null")
                }
            }

            onProgress(0.85f, "Menyesuaikan izin akses direktori & SELinux...")
            val identity = mountManager.resolveAppIdentity(pkg)
            val uid = identity.uid
            val gid = identity.gid
            if (RootShell.exists(targetDataDir)) {
                RootShell.exec("chown -R $uid:$gid \"$targetDataDir\" 2>/dev/null")
                RootShell.exec("chmod -R 775 \"$targetDataDir\" 2>/dev/null")
                RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$targetDataDir\" 2>/dev/null")
                RootShell.exec("touch \"$targetDataDir/$CANARY_FILE\" 2>/dev/null")
            }
            if (RootShell.exists(targetObbDir)) {
                RootShell.exec("chown -R $uid:$gid \"$targetObbDir\" 2>/dev/null")
                RootShell.exec("chmod -R 775 \"$targetObbDir\" 2>/dev/null")
                RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$targetObbDir\" 2>/dev/null")
            }

            onProgress(1.0f, "Selesai merapikan data game.")
            AppLogger.success("DiskCatalog", "Successfully restructured $pkg to standard MountX layout.")
        }
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

            val candidateDirs = mutableListOf(
                "$sdBase/MountX/Android/data/${game.packageName}",
                "$sdBase/MountX/Android/obb/${game.packageName}",
                "$sdBase/MountX/Android/media/${game.packageName}",
                "$sdBase/Android/data/${game.packageName}",
                "$sdBase/Android/obb/${game.packageName}",
                "$sdBase/Android/media/${game.packageName}"
            )

            // Include multi-user isolated directories ($sdBase/MountX/users/*)
            val userDirsOut = RootShell.execForOutput("ls -1d \"$sdBase/MountX/users/\"*\"/Android/data/${game.packageName}\" \"$sdBase/MountX/users/\"*\"/Android/obb/${game.packageName}\" 2>/dev/null")
            if (userDirsOut.isNotBlank()) {
                candidateDirs.addAll(userDirsOut.lines().map { it.trim() }.filter { it.isNotBlank() })
            }

            // Include configured mount points source paths
            for (mp in game.mountPoints) {
                if (mp.sourcePath.isNotBlank() && !candidateDirs.contains(mp.sourcePath)) {
                    candidateDirs.add(mp.sourcePath)
                }
            }

            for (dir in candidateDirs.distinct()) {
                if (RootShell.exists(dir)) {
                    RootShell.exec("chown -R $uid:$gid \"$dir\" 2>/dev/null")
                    RootShell.exec("chmod -R 775 \"$dir\" 2>/dev/null")
                    RootShell.exec("chcon -R u:object_r:media_rw_data_file:s0 \"$dir\" 2>/dev/null")
                    if (dir.contains("/data/") || dir.endsWith("/data/${game.packageName}")) {
                        RootShell.exec("touch \"$dir/$CANARY_FILE\" 2>/dev/null")
                    }
                }
            }

            // Create root canary marker
            RootShell.exec("mkdir -p \"$sdBase/$CATALOG_DIR\" 2>/dev/null")
            RootShell.exec("touch \"$sdBase/$CANARY_FILE\" 2>/dev/null")

            AppLogger.success("DiskCatalog", "Reconciled permissions across all paths for ${game.packageName} [UID: $uid, GID: $gid]")
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
                lastMountedAt = if (g.mountStatus == app.mountx.data.model.MountStatus.MOUNTED) System.currentTimeMillis() else 0L,
                mountPoints = g.mountPoints
            )
        }
        val catalog = DiskCatalog(
            version = 2,
            lastUpdated = System.currentTimeMillis(),
            games = catalogEntries
        )
        saveCatalog(sdBase, catalog)
    }

    /**
     * Checks whether a path contains valid game data (not just empty directories or .nomedia/.mountx_canary stubs).
     */
    suspend fun hasValidContent(path: String): Boolean = withContext(Dispatchers.IO) {
        if (!RootShell.exists(path)) return@withContext false
        if (path.endsWith(".img")) {
            return@withContext RootShell.exec("[ -s \"$path\" ] && echo 1 || echo 0").output.trim() == "1"
        }
        val sizeKbRes = RootShell.execForOutput("du -sk \"$path\" 2>/dev/null").split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
        if (sizeKbRes <= 16L) {
            val listNonHidden = RootShell.execForOutput("ls \"$path\" 2>/dev/null")
            return@withContext listNonHidden.isNotBlank()
        }
        true
    }

    /**
     * Removes an entry from .mountx/catalog.json atomically.
     */
    suspend fun removeGameFromCatalog(sdBase: String, packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val catalog = readCatalog(sdBase) ?: return@runCatching
            val updatedGames = catalog.games.filter { it.packageName != packageName }
            if (updatedGames.size != catalog.games.size) {
                saveCatalog(sdBase, catalog.copy(games = updatedGames, lastUpdated = System.currentTimeMillis()))
                AppLogger.info("DiskCatalog", "Removed $packageName from catalog.json ($sdBase)")
            }
        }
    }
}
