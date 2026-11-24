package app.mountx.data.catalog

import android.content.Context
import app.mountx.R
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val mountManager: MountManager,
    @ApplicationContext private val context: Context
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
            val writeOk = RootShell.writeFile(tmpPath, json.toString(2))
            if (!writeOk) {
                error("Failed atomic catalog write to $tmpPath")
            }
            val mvRes = RootShell.exec("mv -f \"$tmpPath\" \"$finalPath\"")
            if (!mvRes.isSuccess) {
                error("Failed atomic catalog rename: ${mvRes.output}")
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
                val hasModernData = hasValidContent("$sdBase/MountX/Android/data/${cg.packageName}", minSizeBytes = 32L * 1024L)
                val hasLegacyData = hasValidContent("$sdBase/Android/data/${cg.packageName}", minSizeBytes = 5L * 1024L * 1024L)
                val hasModernObb = hasValidContent("$sdBase/MountX/Android/obb/${cg.packageName}", minSizeBytes = 32L * 1024L)
                val hasLegacyObb = hasValidContent("$sdBase/Android/obb/${cg.packageName}", minSizeBytes = 32L * 1024L)
                val hasModernMedia = hasValidContent("$sdBase/MountX/Android/media/${cg.packageName}", minSizeBytes = 32L * 1024L)
                val hasPrivData = hasValidContent("$sdBase/MountX/data/${cg.packageName}", minSizeBytes = 32L * 1024L) ||
                    RootShell.exists("$sdBase/.mountx/containers/${cg.packageName}_data.img")
                val hasApkBackup = RootShell.exists("$sdBase/MountX/app/${cg.packageName}/base.apk") ||
                    RootShell.exists("$sdBase/.mountx/apk_backup/${cg.packageName}/base.apk") ||
                    RootShell.exists("$sdBase/.mountx/containers/${cg.packageName}_apk.img")
                val hasCustomMountPoints = cg.mountPoints.any { pt ->
                    pt.sourcePath.isNotBlank() && (
                        pt.resolveCategory() == MountPointCategory.APP_PACKAGE && !pt.isLibTarget() &&
                            (RootShell.exists("${pt.sourcePath.trimEnd('/')}/base.apk") || pt.sourcePath.endsWith(".img")) ||
                        hasValidContent(pt.sourcePath, minSizeBytes = 32L * 1024L)
                    )
                }
                val hasAnyData = hasModernData || hasLegacyData || hasModernObb || hasLegacyObb ||
                    hasModernMedia || hasPrivData || hasApkBackup || hasCustomMountPoints

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
                var resolvedName = installedApps[cg.packageName] ?: cg.displayName
                if (!isInstalled && (resolvedName.isBlank() || resolvedName == cg.packageName) && hasApkBackup) {
                    resolvedName = app.mountx.root.AppPackageInspector.inspectSdApkBundle(
                        context = context,
                        packageName = cg.packageName,
                        sdBases = listOf(sdBase),
                        mountPoints = cg.mountPoints
                    ).appLabel ?: resolvedName
                }

                detected[cg.packageName] = DiscoveredGame(
                    packageName = cg.packageName,
                    displayName = resolvedName.ifBlank { cg.packageName },
                    mode = mode,
                    source = DiscoverySource.CATALOG_ENTRY,
                    isInstalledOnDevice = isInstalled,
                    isAlreadyRegistered = isRegistered,
                    hasDataOnSd = hasModernData || hasLegacyData || hasPrivData || hasApkBackup,
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

        // 2. Lapisan 1: Standar MountX (Data, OBB, Media, Private, Cache, APK/Lib, & Containers)
        val modernPkgsScript = """
            for d in \
                "$sdBase/MountX/Android/data" \
                "$sdBase/MountX/Android/obb" \
                "$sdBase/MountX/Android/media" \
                "$sdBase/MountX/app" \
                "$sdBase/.mountx/apk_backup" \
                "$sdBase/MountX/lib" \
                "$sdBase/MountX/data" \
                "$sdBase/MountX/cache"; do
                ls -1 "${'$'}d" 2>/dev/null
            done
            ls -1 "$sdBase/.mountx/containers" 2>/dev/null | sed -n 's/_\(data\|cache\|apk\|lib\)\.img$//p'
        """.trimIndent()
        val allModernPkgs = RootShell.execScript(modernPkgsScript).stdout
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith(".") && !it.contains("/") && it.contains(".") }
            .distinct()
        for (cleanPkg in allModernPkgs) {
            if (detected.containsKey(cleanPkg)) continue

            val dataPath = "$sdBase/MountX/Android/data/$cleanPkg"
            val obbPath = "$sdBase/MountX/Android/obb/$cleanPkg"
            val mediaPath = "$sdBase/MountX/Android/media/$cleanPkg"
            val privDir = "$sdBase/MountX/data/$cleanPkg"
            val privImg = "$sdBase/.mountx/containers/${cleanPkg}_data.img"
            val cacheDir = "$sdBase/MountX/cache/$cleanPkg"
            val cacheImg = "$sdBase/.mountx/containers/${cleanPkg}_cache.img"
            val apkDir = "$sdBase/MountX/app/$cleanPkg"
            val apkBkDir = "$sdBase/.mountx/apk_backup/$cleanPkg"
            val apkImg = "$sdBase/.mountx/containers/${cleanPkg}_apk.img"
            val libDir = "$sdBase/MountX/lib/$cleanPkg"

            val hasData = hasValidContent(dataPath, minSizeBytes = 32L * 1024L)
            val hasObb = hasValidContent(obbPath, minSizeBytes = 32L * 1024L)
            val hasMedia = hasValidContent(mediaPath, minSizeBytes = 32L * 1024L)
            val hasPriv = hasValidContent(privDir, minSizeBytes = 32L * 1024L)
            val hasPrivImg = hasValidContent(privImg, minSizeBytes = 64L * 1024L)
            val hasCache = hasValidContent(cacheDir, minSizeBytes = 32L * 1024L)
            val hasCacheImg = hasValidContent(cacheImg, minSizeBytes = 64L * 1024L)
            val hasApkDir = RootShell.exists("$apkDir/base.apk")
            val hasApkBk = !hasApkDir && RootShell.exists("$apkBkDir/base.apk")
            val hasApkImg = hasValidContent(apkImg, minSizeBytes = 64L * 1024L)
            val hasApk = hasApkDir || hasApkBk || hasApkImg
            val hasLib = RootShell.exec("find \"$libDir\" -maxdepth 2 -name '*.so' 2>/dev/null | head -n 1").output.trim().isNotBlank()

            if (!hasData && !hasObb && !hasMedia && !hasPriv && !hasPrivImg && !hasCache && !hasCacheImg && !hasApk && !hasLib) continue

            val discoveredMps = mutableListOf<MountPointConfig>()
            val existingPathsToSize = mutableListOf<String>()
            if (hasPrivImg) {
                existingPathsToSize.add(privImg)
                discoveredMps.add(
                    MountPointConfig(
                        id = "private_$cleanPkg",
                        category = MountPointCategory.PRIVATE_INTERNAL,
                        sourcePath = privImg,
                        targetPath = "/data/user/0/$cleanPkg",
                        enabled = true,
                        isVirtualContainer = true,
                        containerImgPath = privImg
                    )
                )
            } else if (hasPriv) {
                existingPathsToSize.add(privDir)
                discoveredMps.add(
                    MountPointConfig(
                        id = "private_$cleanPkg",
                        category = MountPointCategory.PRIVATE_INTERNAL,
                        sourcePath = privDir,
                        targetPath = "/data/user/0/$cleanPkg",
                        enabled = true
                    )
                )
            }
            if (hasCacheImg) {
                existingPathsToSize.add(cacheImg)
                discoveredMps.add(
                    MountPointConfig(
                        id = "cache_$cleanPkg",
                        category = MountPointCategory.CACHE_SHADERS,
                        sourcePath = cacheImg,
                        targetPath = "/data/user/0/$cleanPkg/cache",
                        enabled = true,
                        isVirtualContainer = true,
                        containerImgPath = cacheImg
                    )
                )
            } else if (hasCache) {
                existingPathsToSize.add(cacheDir)
                discoveredMps.add(
                    MountPointConfig(
                        id = "cache_$cleanPkg",
                        category = MountPointCategory.CACHE_SHADERS,
                        sourcePath = cacheDir,
                        targetPath = "/data/user/0/$cleanPkg/cache",
                        enabled = true
                    )
                )
            }
            if (hasData) {
                existingPathsToSize.add(dataPath)
                discoveredMps.add(
                    MountPointConfig(
                        id = "external_data",
                        category = MountPointCategory.EXTERNAL_DATA,
                        sourcePath = dataPath,
                        targetPath = "/data/media/0/Android/data/$cleanPkg",
                        enabled = true
                    )
                )
            }
            if (hasObb) {
                existingPathsToSize.add(obbPath)
                discoveredMps.add(
                    MountPointConfig(
                        id = "ext_obb_$cleanPkg",
                        category = MountPointCategory.OBB_STORAGE,
                        sourcePath = obbPath,
                        targetPath = "/data/media/0/Android/obb/$cleanPkg",
                        enabled = true
                    )
                )
            }
            if (hasMedia) {
                existingPathsToSize.add(mediaPath)
                discoveredMps.add(
                    MountPointConfig(
                        id = "ext_media_$cleanPkg",
                        category = MountPointCategory.MEDIA_DOWNLOADS,
                        sourcePath = mediaPath,
                        targetPath = "/data/media/0/Android/media/$cleanPkg",
                        enabled = true
                    )
                )
            }
            if (hasApk) {
                val chosenApkSrc = when {
                    hasApkImg -> apkImg
                    hasApkDir -> apkDir
                    else -> apkBkDir
                }
                existingPathsToSize.add(chosenApkSrc)
                discoveredMps.add(
                    MountPointConfig(
                        id = "apk_$cleanPkg",
                        category = MountPointCategory.APP_PACKAGE,
                        sourcePath = chosenApkSrc,
                        targetPath = "/data/app/$cleanPkg",
                        enabled = false,
                        isVirtualContainer = hasApkImg,
                        containerImgPath = if (hasApkImg) apkImg else null
                    )
                )
            }
            if (hasLib) {
                existingPathsToSize.add(libDir)
            }

            val isInstalled = installedApps.containsKey(cleanPkg)
            val isRegistered = registeredPackages.contains(cleanPkg)
            val canary = RootShell.exists("$dataPath/$CANARY_FILE") || RootShell.exists("$privDir/$CANARY_FILE")
            val totalKb = if (existingPathsToSize.isNotEmpty()) {
                val arg = existingPathsToSize.joinToString(" ") { "\"$it\"" }
                RootShell.execForOutput("du -sk $arg 2>/dev/null").lines().sumOf { line ->
                    line.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
                }
            } else 0L

            var resolvedName = installedApps[cleanPkg] ?: cleanPkg
            if (!isInstalled && hasApk) {
                resolvedName = app.mountx.root.AppPackageInspector.inspectSdApkBundle(
                    context = context,
                    packageName = cleanPkg,
                    sdBases = listOf(sdBase),
                    mountPoints = discoveredMps
                ).appLabel ?: resolvedName
            }

            detected[cleanPkg] = DiscoveredGame(
                packageName = cleanPkg,
                displayName = resolvedName,
                mode = MountMode.PKG,
                source = DiscoverySource.SHALLOW_SCAN,
                isInstalledOnDevice = isInstalled,
                isAlreadyRegistered = isRegistered,
                hasDataOnSd = hasData || hasPriv || hasPrivImg || hasApk || hasMedia,
                hasObbOnSd = hasObb,
                sizeBytes = totalKb * 1024L,
                canaryPresent = canary,
                mountPoints = discoveredMps,
                needsRestructure = false
            )
        }

        // 3. Lapisan 2: Legacy Android ($sdBase/Android/data & obb)
        val legacyDataPkgs = RootShell.execForOutput("ls -1 \"$sdBase/Android/data\" 2>/dev/null").lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith(".") }
        val legacyObbPkgs = RootShell.execForOutput("ls -1 \"$sdBase/Android/obb\" 2>/dev/null").lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith(".") }
        val allLegacyPkgs = (legacyDataPkgs + legacyObbPkgs).distinct()
        for (cleanPkg in allLegacyPkgs) {
            if (cleanPkg.isBlank() || cleanPkg == ".mountx_canary" || cleanPkg == ".nomedia" || cleanPkg.contains("/")) continue

            val existing = detected[cleanPkg]
            if (existing == null) {
                val hasData = hasValidContent("$sdBase/Android/data/$cleanPkg", minSizeBytes = 5L * 1024L * 1024L)
                val hasObb = hasValidContent("$sdBase/Android/obb/$cleanPkg", minSizeBytes = 1024L * 1024L)
                if (!hasData && !hasObb) continue

                val isInstalled = installedApps.containsKey(cleanPkg)
                val isRegistered = registeredPackages.contains(cleanPkg)
                val canary = RootShell.exists("$sdBase/Android/data/$cleanPkg/$CANARY_FILE")
                val origPath = if (hasData) "$sdBase/Android/data/$cleanPkg" else "$sdBase/Android/obb/$cleanPkg"
                val sizeKbRes = RootShell.execForOutput("du -sk \"$origPath\" 2>/dev/null").split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L

                detected[cleanPkg] = DiscoveredGame(
                    packageName = cleanPkg,
                    displayName = installedApps[cleanPkg] ?: cleanPkg,
                    mode = MountMode.PKG,
                    source = DiscoverySource.SHALLOW_SCAN,
                    isInstalledOnDevice = isInstalled,
                    isAlreadyRegistered = isRegistered,
                    hasDataOnSd = hasData,
                    hasObbOnSd = hasObb,
                    sizeBytes = sizeKbRes * 1024L,
                    canaryPresent = canary,
                    needsRestructure = true,
                    originalPath = origPath
                )
            }
        }

        // 4. Lapisan 3: Direktori Games / GameData non-standar
        val outerDirs = listOf("$sdBase/Games", "$sdBase/Game", "$sdBase/GameData")
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
                        if (!hasValidContent(folderPath, minSizeBytes = 5L * 1024L * 1024L)) continue

                        val isRegistered = registeredPackages.contains(matchedPkg)
                        val sizeKbRes = RootShell.execForOutput("du -sk \"$folderPath\" 2>/dev/null").split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
                        detected[matchedPkg] = DiscoveredGame(
                            packageName = matchedPkg,
                            displayName = installedApps[matchedPkg] ?: cleanFolder,
                            mode = MountMode.PKG,
                            source = DiscoverySource.SHALLOW_SCAN,
                            isInstalledOnDevice = true,
                            isAlreadyRegistered = isRegistered,
                            hasDataOnSd = true,
                            hasObbOnSd = false,
                            sizeBytes = sizeKbRes * 1024L,
                            canaryPresent = false,
                            needsRestructure = true,
                            originalPath = folderPath
                        )
                    }
                }
            }
        }

        // 5. Lapisan 4: Folder Top-Level Package (<sdBase>/<packageName>)
        val topDirsOut = RootShell.execForOutput("ls -1 \"$sdBase\" 2>/dev/null")
        if (topDirsOut.isNotBlank()) {
            val standardNonAppDirs = setOf(
                "system volume information", "lost+found", "android", "mountx", "games", "game", "gamedata",
                "dcim", "download", "downloads", "pictures", "movies", "music", "documents", "podcasts",
                "ringtones", "alarms", "notifications", "recordings", ".fseventsd", ".spotlight-v100", ".trashes",
                "apps2sd", "apps2sdfm", "audiobooks", "backup", "data", "dubox", "upload"
            )
            for (entryName in topDirsOut.lines()) {
                val cleanEntry = entryName.trim()
                if (cleanEntry.isBlank() || cleanEntry.startsWith(".") || cleanEntry.startsWith("$") ||
                    cleanEntry.lowercase() in standardNonAppDirs) continue

                val matchedPkg = installedApps.keys.firstOrNull { pkg ->
                    pkg.equals(cleanEntry, ignoreCase = true)
                } ?: if (cleanEntry.contains(".") && (cleanEntry.startsWith("com.") || cleanEntry.startsWith("org.") ||
                    cleanEntry.startsWith("net.") || cleanEntry.startsWith("io.") || cleanEntry.startsWith("jp.") || cleanEntry.startsWith("kr."))) cleanEntry else null

                if (matchedPkg != null && !detected.containsKey(matchedPkg)) {
                    val folderPath = "$sdBase/$cleanEntry"
                    if (!hasValidContent(folderPath, minSizeBytes = 5L * 1024L * 1024L)) continue

                    val isInstalled = installedApps.containsKey(matchedPkg)
                    val isRegistered = registeredPackages.contains(matchedPkg)
                    val canary = RootShell.exists("$folderPath/$CANARY_FILE")
                    val sizeKbRes = RootShell.execForOutput("du -sk \"$folderPath\" 2>/dev/null").split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L

                    detected[matchedPkg] = DiscoveredGame(
                        packageName = matchedPkg,
                        displayName = installedApps[matchedPkg] ?: cleanEntry,
                        mode = MountMode.PKG,
                        source = DiscoverySource.SHALLOW_SCAN,
                        isInstalledOnDevice = isInstalled,
                        isAlreadyRegistered = isRegistered,
                        hasDataOnSd = true,
                        hasObbOnSd = false,
                        sizeBytes = sizeKbRes * 1024L,
                        canaryPresent = canary,
                        needsRestructure = false,
                        originalPath = folderPath
                    )
                }
            }
        }

        detected.values.toList()
    }

    /**
     * Scans ALL connected storage devices (MicroSD partition, USB OTG drives, and external volumes)
     * for game directories across standard MountX, legacy Android, and top-level root package folders.
     */
    suspend fun scanAllStorageForGames(
        primarySdBase: String,
        registeredPackages: Set<String>,
        installedApps: Map<String, String>
    ): List<DiscoveredGame> = withContext(Dispatchers.IO) {
        val storageRoots = linkedSetOf<String>()
        if (primarySdBase.isNotBlank() && RootShell.exists(primarySdBase)) {
            storageRoots.add(primarySdBase)
        }

        // 1. Discover all active mounts under /mnt/media_rw/*
        val mediaRwOut = RootShell.execForOutput("ls -d /mnt/media_rw/* 2>/dev/null")
        mediaRwOut.lines().forEach { line ->
            val p = line.trim()
            if (p.isNotBlank() && !p.endsWith("/usbotg") && RootShell.exists(p)) {
                storageRoots.add(p)
            }
        }

        // 2. Discover external volumes under /storage/* (excluding emulated, self, primary, and already-added media_rw roots)
        val storageOut = RootShell.execForOutput("ls -d /storage/* 2>/dev/null")
        storageOut.lines().forEach { line ->
            val p = line.trim()
            val name = p.substringAfterLast('/')
            if (p.isNotBlank() && name != "emulated" && name != "self" && name != "primary" && name != "sdcard0" && RootShell.exists(p)) {
                val mediaRwEquiv = "/mnt/media_rw/$name"
                if (!storageRoots.contains(mediaRwEquiv)) {
                    storageRoots.add(p)
                }
            }
        }

        val allDiscovered = mutableMapOf<String, DiscoveredGame>()

        for (basePath in storageRoots) {
            val devOut = RootShell.execForOutput("df \"$basePath\" 2>/dev/null").lines()
                .getOrNull(1)?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""
            val diskUuid = if (devOut.isNotBlank()) MountManager.getBlockDeviceUuid(devOut) else null

            val discoveredOnBase = scanSdCardForGames(basePath, registeredPackages, installedApps)
            for (game in discoveredOnBase) {
                val existing = allDiscovered[game.packageName]
                if (existing == null || (!existing.hasDataOnSd && game.hasDataOnSd)) {
                    val customPoints = if (game.mountPoints.isNotEmpty()) {
                        game.mountPoints.map { mp -> if (mp.diskUuid == null && diskUuid != null) mp.copy(diskUuid = diskUuid) else mp }
                    } else if (game.originalPath != null) {
                        listOf(
                            MountPointConfig(
                                id = "ext_${game.packageName}_${System.currentTimeMillis() % 1000}",
                                category = MountPointCategory.EXTERNAL_DATA,
                                sourcePath = game.originalPath,
                                targetPath = "/data/media/0/Android/data/${game.packageName}",
                                enabled = true,
                                diskUuid = diskUuid,
                                preserveMedia = true
                            )
                        )
                    } else {
                        emptyList()
                    }
                    allDiscovered[game.packageName] = game.copy(
                        mountPoints = customPoints,
                        originalPath = game.originalPath
                    )
                }
            }
        }

        allDiscovered.values.toList()
    }

    /**
     * Permanently wipes discovered/orphaned game data from MicroSD and removes from catalog.json.
     */
    suspend fun deleteDiscoveredGame(sdBase: String, game: DiscoveredGame): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pkg = game.packageName
            RootShell.exec("am force-stop \"$pkg\" 2>/dev/null")

            // Remove standard and legacy paths on MicroSD
            RootShell.exec("rm -rf \"$sdBase/MountX/Android/data/$pkg\" \"$sdBase/MountX/Android/obb/$pkg\" \"$sdBase/MountX/Android/media/$pkg\" 2>/dev/null")
            RootShell.exec("rm -rf \"$sdBase/MountX/app/$pkg\" \"$sdBase/MountX/lib/$pkg\" \"$sdBase/MountX/data/$pkg\" \"$sdBase/MountX/cache/$pkg\" \"$sdBase/MountX/cache/${pkg}_oat\" \"$sdBase/.mountx/apk_backup/$pkg\" 2>/dev/null")
            RootShell.exec("rm -f \"$sdBase/.mountx/containers/${pkg}_data.img\" \"$sdBase/.mountx/containers/${pkg}_cache.img\" \"$sdBase/.mountx/containers/${pkg}_apk.img\" \"$sdBase/.mountx/containers/${pkg}_lib.img\" 2>/dev/null")
            RootShell.exec("rm -rf \"$sdBase/Android/data/$pkg\" \"$sdBase/Android/obb/$pkg\" \"$sdBase/Android/media/$pkg\" 2>/dev/null")

            if (!game.originalPath.isNullOrBlank() && game.originalPath != "/" && game.originalPath != sdBase) {
                RootShell.exec("rm -rf \"${game.originalPath}\" 2>/dev/null")
            }

            for (mp in game.mountPoints) {
                if (mp.sourcePath.isNotBlank() && mp.sourcePath != "/" && mp.sourcePath != sdBase) {
                    RootShell.exec("rm -rf \"${mp.sourcePath}\" 2>/dev/null")
                }
            }

            removeGameFromCatalog(sdBase, pkg)
            AppLogger.success("DiskCatalog", "Permanently removed discovered game data from MicroSD: $pkg")
        }
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

            onProgress(0.1f, context.getString(R.string.catalog_step_prep_dirs))
            RootShell.exec("mkdir -p \"$sdBase/MountX/Android/data\" \"$sdBase/MountX/Android/obb\" 2>/dev/null")

            val origData = if (game.originalPath != null && RootShell.exists(game.originalPath)) {
                game.originalPath
            } else if (RootShell.exists("$sdBase/Android/data/$pkg")) {
                "$sdBase/Android/data/$pkg"
            } else null

            if (origData != null && origData != targetDataDir) {
                onProgress(0.35f, context.getString(R.string.catalog_step_moving_data))
                RootShell.exec("mkdir -p \"$targetDataDir\" 2>/dev/null")
                val mvRes = RootShell.exec("cp -an \"$origData/.\" \"$targetDataDir/\" && rm -rf \"$origData\" 2>/dev/null")
                if (!mvRes.isSuccess) {
                    RootShell.exec("find \"$origData\" -mindepth 1 -maxdepth 1 -exec mv -f {} \"$targetDataDir/\" \\; && rmdir \"$origData\" 2>/dev/null")
                }
            }

            val origObb = "$sdBase/Android/obb/$pkg"
            if (RootShell.exists(origObb) && origObb != targetObbDir) {
                onProgress(0.65f, context.getString(R.string.catalog_step_moving_obb))
                RootShell.exec("mkdir -p \"$targetObbDir\" 2>/dev/null")
                val mvObbRes = RootShell.exec("cp -an \"$origObb/.\" \"$targetObbDir/\" && rm -rf \"$origObb\" 2>/dev/null")
                if (!mvObbRes.isSuccess) {
                    RootShell.exec("find \"$origObb\" -mindepth 1 -maxdepth 1 -exec mv -f {} \"$targetObbDir/\" \\; && rmdir \"$origObb\" 2>/dev/null")
                }
            }

            onProgress(0.85f, context.getString(R.string.catalog_step_adjust_perms))
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

            onProgress(1.0f, context.getString(R.string.catalog_step_complete))
            AppLogger.success("DiskCatalog", "Successfully restructured $pkg to standard MountX layout.")
        }
    }

    /**
     * Automatically sanitizes misplaced legacy subdirectories (e.g. spurious Android/ or nested duplicate package folders)
     * inside $sdBase/MountX/Android/data and $sdBase/MountX/Android/obb.
     */
    suspend fun sanitizeMisplacedFolders(sdBase: String): Unit = withContext(Dispatchers.IO) {
        if (sdBase.isBlank() || !RootShell.exists(sdBase)) return@withContext
        val mountXAndroid = "$sdBase/MountX/Android"
        if (!RootShell.exists(mountXAndroid)) return@withContext

        val cleanupCmd = """
            for cat in data obb; do
                cat_dir="$mountXAndroid/${'$'}cat"
                [ ! -d "${'$'}cat_dir" ] && continue
                for pkg_dir in "${'$'}cat_dir"/*; do
                    [ ! -d "${'$'}pkg_dir" ] && continue
                    pkg_name="${'$'}(basename "${'$'}pkg_dir")"
                    [ "${'$'}pkg_name" = "*" ] && continue
                    
                    # 1. Remove rogue Android/ subfolder
                    rm -rf "${'$'}pkg_dir/Android" 2>/dev/null
                    
                    # 2. Remove nested duplicate package folder (${'$'}pkg/${'$'}pkg)
                    if [ -d "${'$'}pkg_dir/${'$'}pkg_name" ]; then
                        rm -rf "${'$'}pkg_dir/${'$'}pkg_name" 2>/dev/null
                    fi
                    
                    # 3. Remove rogue foreign package folders mistakenly nested inside
                    for sub in "${'$'}pkg_dir"/*; do
                        [ ! -d "${'$'}sub" ] && continue
                        sub_name="${'$'}(basename "${'$'}sub")"
                        case "${'$'}sub_name" in
                            files|cache|code_cache|shared_prefs|databases|no_backup)
                                ;;
                            com.*|org.*|net.*|io.*|xyz.*|idm.*|jp.*|kr.*)
                                rm -rf "${'$'}sub" 2>/dev/null
                                ;;
                        esac
                    done
                done
            done
        """.trimIndent()
        RootShell.exec(cleanupCmd)
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
    suspend fun hasValidContent(
        path: String,
        minSizeBytes: Long = 1024L * 1024L,
        requireRegularFiles: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        if (path.endsWith(".img")) {
            return@withContext RootShell.exec("[ -s \"$path\" ] && echo 1 || echo 0").output.trim() == "1"
        }
        val minKb = (minSizeBytes / 1024L).coerceAtLeast(1L)
        val isObb = path.contains("/Android/obb") || path.contains("/obb")
        val filter = if (isObb) {
            "-name \"*.obb\""
        } else {
            "! -name \".nomedia\" ! -name \".mountx_canary\" ! -name \".mountx_perms_ok\" ! -name \"*.lck\" ! -name \"$CANARY_FILE\""
        }
        val cmd = if (requireRegularFiles) {
            "[ -d \"$path\" ] && [ \"\$(find \"$path\" -type f $filter -size +0c 2>/dev/null | head -1)\" ] && du -sk \"$path\" 2>/dev/null || echo 0"
        } else {
            "[ -d \"$path\" ] && du -sk \"$path\" 2>/dev/null || echo 0"
        }
        val out = RootShell.execForOutput(cmd)
        val kb = out.split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
        kb >= minKb
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
