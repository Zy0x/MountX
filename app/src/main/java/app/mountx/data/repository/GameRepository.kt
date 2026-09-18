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
import app.mountx.data.model.SmartGamePresets
import app.mountx.root.MountManager
import app.mountx.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val gameDao: GameDao,
    private val mountManager: MountManager
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
        mode: MountMode
    ) = withContext(Dispatchers.IO) {
        val entry = GameEntry(
            packageName = packageName,
            displayName = displayName.ifBlank { packageName },
            mode = mode,
            mountStatus = MountStatus.UNMOUNTED,
            dataSizeBytes = 0L,
            isEnabled = true
        )
        gameDao.insertGame(entry)
        syncModuleGamelist()
    }

    suspend fun updateGame(game: GameEntry) = withContext(Dispatchers.IO) {
        gameDao.updateGame(game)
        syncModuleGamelist()
    }

    suspend fun removeGame(packageName: String) = withContext(Dispatchers.IO) {
        val game = gameDao.getGameByPackage(packageName)
        if (game != null && game.mountStatus == MountStatus.MOUNTED) {
            mountManager.unmountGame(game)
        }
        gameDao.deleteGame(packageName)
        syncModuleGamelist()
    }

    suspend fun mountGame(game: GameEntry, sdBase: String = "/data/sdext2"): Result<Unit> =
        withContext(Dispatchers.IO) {
            val result = mountManager.mountGame(game, sdBase)
            if (result.isSuccess) {
                gameDao.updateMountStatus(game.packageName, MountStatus.MOUNTED)
            } else {
                gameDao.updateMountStatus(game.packageName, MountStatus.ERROR)
            }
            result
        }

    suspend fun unmountGame(game: GameEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            val result = mountManager.unmountGame(game)
            if (result.isSuccess) {
                gameDao.updateMountStatus(game.packageName, MountStatus.UNMOUNTED)
            }
            result
        }

    suspend fun mountAll(sdBase: String = "/data/sdext2"): Int =
        withContext(Dispatchers.IO) {
            val games = gameDao.getAllGames().firstOrNull() ?: emptyList()
            var count = 0
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
            count
        }

    suspend fun unmountAll(sdBase: String = "/data/sdext2"): Int =
        withContext(Dispatchers.IO) {
            val games = gameDao.getAllGames().firstOrNull() ?: emptyList()
            var count = 0
            for (g in games) {
                val res = mountManager.unmountGame(g)
                if (res.isSuccess) {
                    gameDao.updateMountStatus(g.packageName, MountStatus.UNMOUNTED)
                    count++
                }
            }
            mountManager.unmountAll(sdBase)
            count
        }

    suspend fun syncModuleGamelist() = withContext(Dispatchers.IO) {
        val moduleDir = "/data/adb/modules/Mountify"
        if (RootShell.exists(moduleDir)) {
            val games = gameDao.getAllGames().firstOrNull() ?: emptyList()
            val content = games.filter { it.isEnabled }.joinToString("\n") { g ->
                val modeStr = when (g.mode) {
                    MountMode.PKG -> "pkg"
                    MountMode.FILES -> "files"
                }
                "${g.packageName}:$modeStr"
            }
            RootShell.exec("cat << 'EOF' > \"$moduleDir/gamelist.conf\"\n$content\nEOF\n")
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

    suspend fun updateGameMode(packageName: String, mode: MountMode) = withContext(Dispatchers.IO) {
        gameDao.updateMode(packageName, mode)
        syncModuleGamelist()
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

    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps
            .map { app ->
                val label = pm.getApplicationLabel(app).toString()
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val isGame = (app.category == ApplicationInfo.CATEGORY_GAME) ||
                    (SmartGamePresets.findPreset(app.packageName) != null)
                InstalledAppInfo(
                    packageName = app.packageName,
                    displayName = label.ifBlank { app.packageName },
                    isGame = isGame,
                    isSystemApp = isSystem
                )
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { it.isGame }
                    .thenBy { it.isSystemApp }
                    .thenBy { it.displayName.lowercase() }
            )
    }
}
