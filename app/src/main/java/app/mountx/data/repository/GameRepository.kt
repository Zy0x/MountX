package app.mountx.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import app.mountx.data.db.GameDao
import app.mountx.data.model.AppStorageBreakdown
import app.mountx.data.model.GameEntry
import app.mountx.data.model.InstalledAppInfo
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountStatus
import app.mountx.data.catalog.DiskCatalogManager
import app.mountx.data.catalog.DiscoveredGame
import app.mountx.data.model.SmartGamePresets
import app.mountx.root.MountManager
import app.mountx.root.RootShell
import app.mountx.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val gameDao: GameDao,
    private val mountManager: MountManager,
    private val diskCatalogManager: DiskCatalogManager
) {

    fun observeGames(): Flow<List<GameEntry>> = gameDao.getAllGames()

    fun observeMountedCount(): Flow<Int> = gameDao.getMountedCount()

    fun observeTotalCount(): Flow<Int> = gameDao.getTotalCount()

    suspend fun getGame(packageName: String): GameEntry? = withContext(Dispatchers.IO) {
        gameDao.getGameByPackage(packageName)
    }

    suspend fun addGame(
        packageName: String,
        displayName: String,
        mode: MountMode = MountMode.PKG,
        mountPoints: List<app.mountx.data.model.MountPointConfig> = emptyList(),
        initialSizeBytes: Long = 0L
    ) = withContext(Dispatchers.IO) {
        val entry = GameEntry(
            packageName = packageName,
            displayName = displayName.ifBlank { packageName },
            mode = mode,
            mountStatus = MountStatus.UNMOUNTED,
            dataSizeBytes = initialSizeBytes,
            isEnabled = true,
            mountPoints = mountPoints
        )
        gameDao.insertGame(entry)
        syncModuleGamelist()
        AppLogger.info("Games", "Registered game: $displayName ($packageName) [Mode: ${mode.name}, MountPoints: ${mountPoints.size}]")
    }

    suspend fun updateGame(game: GameEntry) = withContext(Dispatchers.IO) {
        gameDao.updateGame(game)
        syncModuleGamelist()
    }

    suspend fun updateGameMode(packageName: String, mode: MountMode) = withContext(Dispatchers.IO) {
        gameDao.updateMode(packageName, mode)
        syncModuleGamelist()
        AppLogger.info("Games", "Updated game mode: $packageName -> ${mode.name}")
    }

    suspend fun setGameEnabled(packageName: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        gameDao.updateEnabled(packageName, enabled)
        syncModuleGamelist()
        AppLogger.info("Games", "Toggled game enabled: $packageName = $enabled")
    }

    suspend fun removeGame(packageName: String) = withContext(Dispatchers.IO) {
        val game = gameDao.getGameByPackage(packageName)
        if (game != null && game.mountStatus == MountStatus.MOUNTED) {
            mountManager.unmountGame(game)
        }
        gameDao.deleteGame(packageName)
        syncModuleGamelist()
        AppLogger.info("Games", "Removed game: $packageName")
    }

    suspend fun mountGame(game: GameEntry, sdBase: String = "/data/sdext2"): Result<Unit> =
        withContext(Dispatchers.IO) {
            AppLogger.info("Games", "Mounting ${game.displayName} (${game.packageName}) [${game.mode.name}]")
            val result = mountManager.mountGame(game, sdBase)
            if (result.isSuccess) {
                gameDao.updateMountStatus(game.packageName, MountStatus.MOUNTED)
                AppLogger.success("Games", "Successfully mounted ${game.displayName}")
            } else {
                gameDao.updateMountStatus(game.packageName, MountStatus.ERROR)
                AppLogger.error("Games", "Failed to mount ${game.displayName}: ${result.exceptionOrNull()?.message}")
            }
            result
        }

    suspend fun unmountGame(game: GameEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            AppLogger.info("Games", "Unmounting ${game.displayName} (${game.packageName})")
            val result = mountManager.unmountGame(game)
            if (result.isSuccess) {
                gameDao.updateMountStatus(game.packageName, MountStatus.UNMOUNTED)
                AppLogger.success("Games", "Successfully unmounted ${game.displayName}")
            } else {
                AppLogger.error("Games", "Failed to unmount ${game.displayName}: ${result.exceptionOrNull()?.message}")
            }
            result
        }

    suspend fun mountAll(sdBase: String = "/data/sdext2"): Int =
        withContext(Dispatchers.IO) {
            val games = gameDao.getAllGames().firstOrNull() ?: emptyList()
            var count = 0
            AppLogger.info("Games", "Mounting all ${games.size} registered games")
            for (g in games) {
                if (g.isEnabled) {
                    val res = mountManager.mountGame(g, sdBase)
                    if (res.isSuccess) {
                        gameDao.updateMountStatus(g.packageName, MountStatus.MOUNTED)
                        count++
                    } else {
                        gameDao.updateMountStatus(g.packageName, MountStatus.ERROR)
                    }
                }
            }
            AppLogger.success("Games", "Mounted $count/${games.size} games")
            count
        }

    suspend fun unmountAll(sdBase: String = "/data/sdext2"): Int =
        withContext(Dispatchers.IO) {
            val games = gameDao.getAllGames().firstOrNull() ?: emptyList()
            var count = 0
            AppLogger.info("Games", "Unmounting all registered games")
            for (g in games) {
                val res = mountManager.unmountGame(g)
                if (res.isSuccess) {
                    gameDao.updateMountStatus(g.packageName, MountStatus.UNMOUNTED)
                    count++
                }
            }
            mountManager.unmountAll(sdBase)
            AppLogger.success("Games", "Unmounted $count games")
            count
        }

    suspend fun syncModuleGamelist() = withContext(Dispatchers.IO) {
        val targetDirs = listOf("/data/adb/modules/MountX", "/data/adb/modules/Mountify")
        val games = gameDao.getAllGames().firstOrNull() ?: emptyList()
        val content = games.filter { it.isEnabled }.joinToString("\n") { g ->
            val modeStr = when (g.mode) {
                MountMode.PKG -> "pkg"
                MountMode.FILES -> "files"
            }
            "${g.packageName}:$modeStr"
        }
        for (dir in targetDirs) {
            if (RootShell.exists(dir)) {
                RootShell.exec("cat << 'EOF' > \"$dir/gamelist.conf\"\n$content\nEOF\n")
            }
        }
    }

    suspend fun syncDiskCatalog(sdBase: String = "/data/sdext2") = withContext(Dispatchers.IO) {
        val games = gameDao.getAllGames().firstOrNull() ?: emptyList()
        diskCatalogManager.syncCatalogFromRegisteredGames(sdBase, games)
    }

    suspend fun scanMicroSdGames(sdBase: String, installedApps: Map<String, String>): List<DiscoveredGame> =
        withContext(Dispatchers.IO) {
            val registered = (gameDao.getAllGames().firstOrNull() ?: emptyList()).map { it.packageName }.toSet()
            diskCatalogManager.scanSdCardForGames(sdBase, registered, installedApps)
        }

    suspend fun importDiscoveredGame(game: DiscoveredGame, sdBase: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val realSize = if (game.sizeBytes > 0L) game.sizeBytes else calculateDataSize(game.packageName, sdBase)
                val entry = GameEntry(
                    packageName = game.packageName,
                    displayName = game.displayName,
                    mode = game.mode,
                    mountStatus = MountStatus.UNMOUNTED,
                    dataSizeBytes = realSize,
                    isEnabled = true
                )
                gameDao.insertGame(entry)
                // Reconcile dynamic UID/GID and SELinux
                diskCatalogManager.reconcileGame(sdBase, entry)
                syncModuleGamelist()
                syncDiskCatalog(sdBase)
                AppLogger.success("Games", "Imported & reconciled portable game: ${game.displayName} (${game.packageName}), size: $realSize bytes")
            }
        }

    suspend fun refreshMountStatuses() = withContext(Dispatchers.IO) {
        val games = gameDao.getAllGames().firstOrNull() ?: emptyList()
        val mountedPaths = mountManager.getMountedPaths()

        for (g in games) {
            val targetData = when (g.mode) {
                MountMode.FILES -> "Android/data/${g.packageName}/files"
                MountMode.PKG -> "Android/data/${g.packageName}"
            }
            val targetObb = "Android/obb/${g.packageName}"
            val isMounted = mountedPaths.any { it.contains(targetData) || it.contains(targetObb) }
            val newStatus = if (isMounted) MountStatus.MOUNTED else MountStatus.UNMOUNTED
            gameDao.updateMountStatus(g.packageName, newStatus)
        }
    }

    suspend fun calculateDataSize(packageName: String, sdBase: String = "/data/sdext2"): Long =
        withContext(Dispatchers.IO) {
            val sdData = "$sdBase/Android/data/$packageName"
            val sdObb = "$sdBase/Android/obb/$packageName"
            val internalData = "/data/media/0/Android/data/$packageName"
            val internalObb = "/data/media/0/Android/obb/$packageName"

            val targetData = if (RootShell.exists(sdData)) sdData else internalData
            val targetObb = if (RootShell.exists(sdObb)) sdObb else internalObb

            val res = RootShell.exec("du -sck \"$targetData\" \"$targetObb\" 2>/dev/null | tail -n1 | cut -f1")
            val sizeKb = res.output.trim().toLongOrNull() ?: 0L
            val sizeBytes = sizeKb * 1024L
            gameDao.updateDataSize(packageName, sizeBytes)
            sizeBytes
        }

    suspend fun getInternalAndSdSizes(packageName: String, sdBase: String = "/data/sdext2"): Pair<Long, Long> =
        withContext(Dispatchers.IO) {
            val internalData = "/data/media/0/Android/data/$packageName"
            val internalObb = "/data/media/0/Android/obb/$packageName"
            val sdData = "$sdBase/Android/data/$packageName"
            val sdObb = "$sdBase/Android/obb/$packageName"

            val internalRes = RootShell.exec("du -sck \"$internalData\" \"$internalObb\" 2>/dev/null | tail -n1 | cut -f1")
            val internalKb = internalRes.output.trim().toLongOrNull() ?: 0L

            val sdRes = RootShell.exec("du -sck \"$sdData\" \"$sdObb\" 2>/dev/null | tail -n1 | cut -f1")
            val sdKb = sdRes.output.trim().toLongOrNull() ?: 0L

            Pair(internalKb * 1024L, sdKb * 1024L)
        }

    suspend fun getDetailedStorageBreakdown(
        context: Context,
        packageName: String,
        sdBase: String = "/data/sdext2"
    ): AppStorageBreakdown = withContext(Dispatchers.IO) {
        var apkBytes = 0L
        var dexBytes = 0L
        var libBytes = 0L
        var rawPrivateDataBytes = 0L
        var cacheBytes = 0L
        var ext1DataBytes = 0L
        var ext1ObbBytes = 0L
        var ext2DataBytes = 0L
        var ext2ObbBytes = 0L

        val appInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).applicationInfo
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0).applicationInfo
            }
        } catch (_: Exception) {
            null
        }

        val sourceDir = appInfo?.sourceDir ?: ""
        val libDir = appInfo?.nativeLibraryDir ?: ""

        // Fast native file calculation for APK
        if (sourceDir.isNotBlank()) {
            try {
                val srcFile = java.io.File(sourceDir)
                val parent = srcFile.parentFile
                if (parent != null && parent.exists() && parent.canRead()) {
                    val apkFiles = parent.listFiles { f -> f.extension.equals("apk", ignoreCase = true) }
                    apkBytes = apkFiles?.sumOf { it.length() } ?: srcFile.length()
                } else if (srcFile.exists()) {
                    apkBytes = srcFile.length()
                }
            } catch (_: Exception) {}
        }

        // Fast native file calculation for Lib
        if (libDir.isNotBlank()) {
            try {
                val libFile = java.io.File(libDir)
                if (libFile.exists() && libFile.canRead()) {
                    libBytes = libFile.listFiles()?.sumOf { it.length() } ?: 0L
                }
            } catch (_: Exception) {}
        }

        val oatDir = if (sourceDir.isNotBlank()) "${java.io.File(sourceDir).parent}/oat" else ""
        val dataDir = "/data/data/$packageName"
        val cacheDir = "/data/data/$packageName/cache"
        val codeCacheDir = "/data/data/$packageName/code_cache"
        val ext1Data = "/data/media/0/Android/data/$packageName"
        val ext1Obb = "/data/media/0/Android/obb/$packageName"
        val ext2Data = "$sdBase/Android/data/$packageName"
        val ext2Obb = "$sdBase/Android/obb/$packageName"

        // Single batch du invocation for all remaining directories
        val targets = listOf(oatDir, dataDir, cacheDir, codeCacheDir, ext1Data, ext1Obb, ext2Data, ext2Obb)
            .filter { it.isNotBlank() }
            .joinToString(" ") { "\"$it\"" }

        val res = RootShell.exec("du -sk $targets 2>/dev/null")
        for (line in res.stdout) {
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size >= 2) {
                val kb = parts[0].toLongOrNull() ?: continue
                val path = parts[1]
                val bytes = kb * 1024L

                when {
                    path.endsWith("/oat") || path.contains("/oat/") -> dexBytes = bytes
                    path.endsWith("/cache") || path.endsWith("/code_cache") -> cacheBytes += bytes
                    path == dataDir -> rawPrivateDataBytes = bytes
                    path == ext1Data -> ext1DataBytes = bytes
                    path == ext1Obb -> ext1ObbBytes = bytes
                    path == ext2Data -> ext2DataBytes = bytes
                    path == ext2Obb -> ext2ObbBytes = bytes
                }
            }
        }

        val dataBytes = (rawPrivateDataBytes - cacheBytes).coerceAtLeast(0L)
        val ext1Bytes = ext1DataBytes + ext1ObbBytes
        val ext2Bytes = ext2DataBytes + ext2ObbBytes

        AppStorageBreakdown(
            apkBytes = apkBytes,
            dexBytes = dexBytes,
            libBytes = libBytes,
            dataBytes = dataBytes,
            cacheBytes = cacheBytes,
            ext1Bytes = ext1Bytes,
            ext2Bytes = ext2Bytes,
            ext1DataBytes = ext1DataBytes,
            ext1ObbBytes = ext1ObbBytes,
            ext2DataBytes = ext2DataBytes,
            ext2ObbBytes = ext2ObbBytes
        )
    }

    suspend fun getInstalledApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ApplicationInfoFlags.of(0L)
        } else {
            0
        }
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(flags as PackageManager.ApplicationInfoFlags)
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        app.mountx.ui.components.AppIconManager.registerAppInfos(apps)

        apps
            .map { app ->
                val label = pm.getApplicationLabel(app).toString()
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val preset = SmartGamePresets.findPreset(app.packageName)
                val isGame = (app.category == ApplicationInfo.CATEGORY_GAME) || (preset != null)
                InstalledAppInfo(
                    packageName = app.packageName,
                    displayName = label.ifBlank { app.packageName },
                    isGame = isGame,
                    isSystemApp = isSystem,
                    hasPreset = preset != null
                )
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { it.isGame }
                    .thenBy { it.isSystemApp }
                    .thenBy { it.displayName.lowercase() }
            )
    }

    suspend fun scanCandidateDirectories(
        packageName: String,
        displayName: String,
        sdBase: String = "/data/sdext2"
    ): List<CandidateDirectory> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CandidateDirectory>()

        // 1. GAME_ASSETS: files and obb
        val internalFiles = "/data/media/0/Android/data/$packageName/files"
        val sdFiles = "$sdBase/Android/data/$packageName/files"
        val internalObb = "/data/media/0/Android/obb/$packageName"
        val sdObb = "$sdBase/Android/obb/$packageName"

        val filesSize = getDirSizeBytes(if (RootShell.exists(sdFiles)) sdFiles else internalFiles)
        val obbSize = getDirSizeBytes(if (RootShell.exists(sdObb)) sdObb else internalObb)
        val totalAssetsSize = filesSize + obbSize

        list.add(
            CandidateDirectory(
                id = "game_assets",
                category = app.mountx.data.model.MountPointCategory.GAME_ASSETS,
                title = "Game Assets & Data (99% Stabilitas)",
                description = "Memetakan subdirektori aset /Android/data/files dan berkas arsip /Android/obb. Mengosongkan ruang tanpa menyentuh cache internal.",
                relativePath = "Android/data/$packageName/files",
                internalPath = internalFiles,
                sdPath = sdFiles,
                sizeBytes = totalAssetsSize,
                defaultEnabled = true
            )
        )

        // 2. MEDIA_DOWNLOADS: Android/media or public app folder
        val internalMedia = "/data/media/0/Android/media/$packageName"
        val sdMedia = "$sdBase/Android/media/$packageName"
        val pubFolder = "/data/media/0/${displayName.replace(" ", "")}"
        val pubSize = if (RootShell.exists(pubFolder)) getDirSizeBytes(pubFolder) else 0L
        val mediaSize = getDirSizeBytes(if (RootShell.exists(sdMedia)) sdMedia else internalMedia) + pubSize

        list.add(
            CandidateDirectory(
                id = "media_downloads",
                category = app.mountx.data.model.MountPointCategory.MEDIA_DOWNLOADS,
                title = "Media & Unduhan",
                description = "Memetakan folder media publik (/Android/media/). Otomatis menyertakan berkas .nomedia agar MediaStore tidak menduplikasi galeri.",
                relativePath = "Android/media/$packageName",
                internalPath = internalMedia,
                sdPath = sdMedia,
                sizeBytes = mediaSize,
                defaultEnabled = mediaSize > 0L
            )
        )

        // 3. CACHE_SHADERS: Android/data/cache
        val internalCache = "/data/media/0/Android/data/$packageName/cache"
        val sdCache = "$sdBase/Android/data/$packageName/cache"
        val cacheSize = getDirSizeBytes(if (RootShell.exists(sdCache)) sdCache else internalCache)

        list.add(
            CandidateDirectory(
                id = "cache_shaders",
                category = app.mountx.data.model.MountPointCategory.CACHE_SHADERS,
                title = "Cache & Shaders (Opsional)",
                description = "Memetakan folder cache dan GPU shader. Disarankan tetap di memori internal UFS agar tidak terjadi stuttering kompilasi shader grafis.",
                relativePath = "Android/data/$packageName/cache",
                internalPath = internalCache,
                sdPath = sdCache,
                sizeBytes = cacheSize,
                defaultEnabled = false
            )
        )

        // 4. PRIVATE_INTERNAL: /data/data/<pkg>
        val privateDataDir = "/data/user/0/$packageName"
        val privateSize = getDirSizeBytes(privateDataDir)
        val oneGb = 1024L * 1024L * 1024L

        if (privateSize >= oneGb) {
            list.add(
                CandidateDirectory(
                    id = "private_internal",
                    category = app.mountx.data.model.MountPointCategory.PRIVATE_INTERNAL,
                    title = "Private Internal Data (Virtual Ext4 Container)",
                    description = "Data gajah > 1 GB terdeteksi di /data/data/. Menggunakan sparse image ext4 terisolasi di MicroSD untuk menjaga integritas SQLite WAL & SELinux.",
                    relativePath = "data/user/0/$packageName",
                    internalPath = privateDataDir,
                    sdPath = "$sdBase/.mountx/containers/${packageName}_data.img",
                    sizeBytes = privateSize,
                    defaultEnabled = false,
                    isLocked = false,
                    isVirtualContainer = true
                )
            )
        } else if (privateSize > 0L) {
            list.add(
                CandidateDirectory(
                    id = "private_internal_locked",
                    category = app.mountx.data.model.MountPointCategory.PRIVATE_INTERNAL,
                    title = "Private Internal Data (/data/data)",
                    description = "Terkunci: Data internal < 1 GB wajib berada di internal flash untuk mencegah error SQLite WAL database lock.",
                    relativePath = "data/user/0/$packageName",
                    internalPath = privateDataDir,
                    sdPath = "$sdBase/.mountx/containers/${packageName}_data.img",
                    sizeBytes = privateSize,
                    defaultEnabled = false,
                    isLocked = true,
                    lockReason = "Ukuran < 1 GB dikunci demi keselamatan database"
                )
            )
        }

        list
    }

    private suspend fun getDirSizeBytes(path: String): Long {
        if (!RootShell.exists(path)) return 0L
        val res = RootShell.exec("du -sk \"$path\" 2>/dev/null | cut -f1")
        val kb = res.output.trim().toLongOrNull() ?: 0L
        return kb * 1024L
    }
}

data class CandidateDirectory(
    val id: String,
    val category: app.mountx.data.model.MountPointCategory,
    val title: String,
    val description: String,
    val relativePath: String,
    val internalPath: String,
    val sdPath: String,
    val sizeBytes: Long,
    val defaultEnabled: Boolean,
    val isLocked: Boolean = false,
    val lockReason: String? = null,
    val isVirtualContainer: Boolean = false
)
