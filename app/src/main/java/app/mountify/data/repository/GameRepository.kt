package app.mountify.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import app.mountify.data.db.GameDao
import app.mountify.data.model.AppStorageBreakdown
import app.mountify.data.model.GameEntry
import app.mountify.data.model.InstalledAppInfo
import app.mountify.data.model.MountMode
import app.mountify.data.model.MountStatus
import app.mountify.data.model.SmartGamePresets
import app.mountify.root.MountManager
import app.mountify.root.RootShell
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
    }

    suspend fun updateGame(game: GameEntry) = withContext(Dispatchers.IO) {
        gameDao.updateGame(game)
    }

    suspend fun removeGame(packageName: String) = withContext(Dispatchers.IO) {
        val game = gameDao.getGameByPackage(packageName)
        if (game != null && game.mountStatus == MountStatus.MOUNTED) {
            mountManager.unmountGame(game)
        }
        gameDao.deleteGame(packageName)
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

    suspend fun refreshMountStatuses() = withContext(Dispatchers.IO) {
        val games = gameDao.getAllGames().firstOrNull() ?: emptyList()
        val mountedPaths = mountManager.getMountedPaths()

        for (g in games) {
            val targetFolder = when (g.mode) {
                MountMode.FILES -> "Android/data/${g.packageName}/files"
                MountMode.PKG -> "Android/data/${g.packageName}"
            }
            val isMounted = mountedPaths.any { it.contains(targetFolder) }
            val newStatus = if (isMounted) MountStatus.MOUNTED else MountStatus.UNMOUNTED
            gameDao.updateMountStatus(g.packageName, newStatus)
        }
    }

    suspend fun calculateDataSize(packageName: String, sdBase: String = "/data/sdext2"): Long =
        withContext(Dispatchers.IO) {
            val sdPath = "$sdBase/Android/data/$packageName"
            val internalPath = "/data/media/0/Android/data/$packageName"

            val targetPath = if (RootShell.exists(sdPath)) sdPath else internalPath
            val res = RootShell.exec("du -sk \"$targetPath\" 2>/dev/null | cut -f1")
            val sizeKb = res.output.trim().toLongOrNull() ?: 0L
            val sizeBytes = sizeKb * 1024L
            gameDao.updateDataSize(packageName, sizeBytes)
            sizeBytes
        }

    suspend fun updateGameMode(packageName: String, mode: MountMode) = withContext(Dispatchers.IO) {
        gameDao.updateMode(packageName, mode)
    }

    suspend fun getInternalAndSdSizes(packageName: String, sdBase: String = "/data/sdext2"): Pair<Long, Long> =
        withContext(Dispatchers.IO) {
            val internalPath = "/data/media/0/Android/data/$packageName"
            val sdPath = "$sdBase/Android/data/$packageName"

            val internalKb = if (RootShell.exists(internalPath)) {
                RootShell.exec("du -sk \"$internalPath\" 2>/dev/null | cut -f1").output.trim().toLongOrNull() ?: 0L
            } else 0L

            val sdKb = if (RootShell.exists(sdPath)) {
                RootShell.exec("du -sk \"$sdPath\" 2>/dev/null | cut -f1").output.trim().toLongOrNull() ?: 0L
            } else 0L

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
        var dataBytes = 0L
        var cacheBytes = 0L
        var ext1Bytes = 0L
        var ext2Bytes = 0L

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

        val script = buildString {
            append("PKG=\"$packageName\"\n")
            append("SRC=\"$sourceDir\"\n")
            append("LIB=\"$libDir\"\n")
            append("SDBASE=\"$sdBase\"\n")

            append("if [ -n \"\$SRC\" ] && [ -e \"\$SRC\" ]; then\n")
            append("  APP_DIR=\$(dirname \"\$SRC\")\n")
            append("  APK_KB=\$(du -sk \"\$APP_DIR\"/*.apk 2>/dev/null | awk '{sum+=\$1} END {print sum}')\n")
            append("  echo \"APK:\${APK_KB:-0}\"\n")
            append("  DEX_KB=\$(du -sk \"\$APP_DIR/oat\" 2>/dev/null | awk '{print \$1}')\n")
            append("  echo \"DEX:\${DEX_KB:-0}\"\n")
            append("else\n")
            append("  echo \"APK:0\"\n")
            append("  echo \"DEX:0\"\n")
            append("fi\n")

            append("if [ -n \"\$LIB\" ] && [ -d \"\$LIB\" ]; then\n")
            append("  LIB_KB=\$(du -sk \"\$LIB\" 2>/dev/null | awk '{print \$1}')\n")
            append("  echo \"LIB:\${LIB_KB:-0}\"\n")
            append("else\n")
            append("  echo \"LIB:0\"\n")
            append("fi\n")

            append("if [ -d \"/data/data/\$PKG\" ]; then\n")
            append("  DATA_TOT=\$(du -sk \"/data/data/\$PKG\" 2>/dev/null | awk '{print \$1}')\n")
            append("  CACHE1=\$(du -sk \"/data/data/\$PKG/cache\" 2>/dev/null | awk '{print \$1}')\n")
            append("  CACHE2=\$(du -sk \"/data/data/\$PKG/code_cache\" 2>/dev/null | awk '{print \$1}')\n")
            append("  C1=\${CACHE1:-0}\n")
            append("  C2=\${CACHE2:-0}\n")
            append("  CACHE_TOT=\$(( C1 + C2 ))\n")
            append("  DT=\${DATA_TOT:-0}\n")
            append("  DATA_NET=\$(( DT - CACHE_TOT ))\n")
            append("  if [ \"\$DATA_NET\" -lt 0 ]; then DATA_NET=0; fi\n")
            append("  echo \"DATA:\$DATA_NET\"\n")
            append("  echo \"CACHE:\$CACHE_TOT\"\n")
            append("else\n")
            append("  echo \"DATA:0\"\n")
            append("  echo \"CACHE:0\"\n")
            append("fi\n")

            append("EXT1_DIR=\"/data/media/0/Android/data/\$PKG\"\n")
            append("if [ -d \"\$EXT1_DIR\" ]; then\n")
            append("  EXT1_KB=\$(du -sk \"\$EXT1_DIR\" 2>/dev/null | awk '{print \$1}')\n")
            append("  echo \"EXT1:\${EXT1_KB:-0}\"\n")
            append("else\n")
            append("  echo \"EXT1:0\"\n")
            append("fi\n")

            append("EXT2_DIR=\"\$SDBASE/Android/data/\$PKG\"\n")
            append("if [ -d \"\$EXT2_DIR\" ]; then\n")
            append("  EXT2_KB=\$(du -sk \"\$EXT2_DIR\" 2>/dev/null | awk '{print \$1}')\n")
            append("  echo \"EXT2:\${EXT2_KB:-0}\"\n")
            append("else\n")
            append("  echo \"EXT2:0\"\n")
            append("fi\n")
        }

        val res = RootShell.exec(script)
        res.stdout.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("APK:") -> apkBytes = (trimmed.substringAfter("APK:").toLongOrNull() ?: 0L) * 1024L
                trimmed.startsWith("DEX:") -> dexBytes = (trimmed.substringAfter("DEX:").toLongOrNull() ?: 0L) * 1024L
                trimmed.startsWith("LIB:") -> libBytes = (trimmed.substringAfter("LIB:").toLongOrNull() ?: 0L) * 1024L
                trimmed.startsWith("DATA:") -> dataBytes = (trimmed.substringAfter("DATA:").toLongOrNull() ?: 0L) * 1024L
                trimmed.startsWith("CACHE:") -> cacheBytes = (trimmed.substringAfter("CACHE:").toLongOrNull() ?: 0L) * 1024L
                trimmed.startsWith("EXT1:") -> ext1Bytes = (trimmed.substringAfter("EXT1:").toLongOrNull() ?: 0L) * 1024L
                trimmed.startsWith("EXT2:") -> ext2Bytes = (trimmed.substringAfter("EXT2:").toLongOrNull() ?: 0L) * 1024L
            }
        }

        // Fallback for APK if root returned 0 but appInfo file exists
        if (apkBytes == 0L && sourceDir.isNotBlank()) {
            val srcFile = java.io.File(sourceDir)
            if (srcFile.exists()) {
                apkBytes = srcFile.length()
            }
        }

        AppStorageBreakdown(
            apkBytes = apkBytes,
            dexBytes = dexBytes,
            libBytes = libBytes,
            dataBytes = dataBytes,
            cacheBytes = cacheBytes,
            ext1Bytes = ext1Bytes,
            ext2Bytes = ext2Bytes
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
