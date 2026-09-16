package app.mountify.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.mountify.data.db.GameDao
import app.mountify.data.model.GameEntry
import app.mountify.data.model.MountMode
import app.mountify.data.model.MountStatus
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

    fun getInstalledApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps
            .filter { app -> (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // exclude system apps
            .map { app ->
                val label = pm.getApplicationLabel(app).toString()
                Pair(app.packageName, label)
            }
            .sortedBy { it.second.lowercase() }
    }
}
