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
import app.mountx.data.model.CategoryDeleteLocation
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.OperationProgress
import app.mountx.root.MountManager
import app.mountx.root.RootShell
import app.mountx.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val gameDao: GameDao,
    private val mountManager: MountManager,
    private val diskCatalogManager: DiskCatalogManager,
    private val storageManager: app.mountx.root.StorageManager
) {

    suspend fun resolveSdSourcePath(sdBase: String, relativeMountXPath: String): String {
        val modernPath = "$sdBase/$relativeMountXPath"
        val legacyRelative = relativeMountXPath.removePrefix("MountX/")
        val legacyPath = "$sdBase/$legacyRelative"
        return if (!RootShell.exists(modernPath) && RootShell.exists(legacyPath)) {
            val sizeKb = RootShell.exec("du -sk \"$legacyPath\" 2>/dev/null").output.trim().split(Regex("\\s+")).getOrNull(0)?.toLongOrNull() ?: 0L
            if (sizeKb > 128L) legacyPath else modernPath
        } else {
            modernPath
        }
    }

    suspend fun synthesizeLegacyMountPoints(game: GameEntry, sdBase: String = "/data/sdext2"): List<MountPointConfig> {
        if (game.mountPoints.isNotEmpty()) return game.mountPoints
        val list = mutableListOf<MountPointConfig>()
        val pkg = game.packageName
        when (game.mode) {
            MountMode.FILES -> {
                list.add(
                    MountPointConfig(
                        id = "legacy_${pkg}_files",
                        category = MountPointCategory.EXTERNAL_DATA,
                        sourcePath = resolveSdSourcePath(sdBase, "MountX/Android/data/$pkg/files"),
                        targetPath = "/data/media/0/Android/data/$pkg/files",
                        enabled = true,
                        sizeBytes = game.dataSizeBytes
                    )
                )
                list.add(
                    MountPointConfig(
                        id = "legacy_${pkg}_obb",
                        category = MountPointCategory.OBB_STORAGE,
                        sourcePath = resolveSdSourcePath(sdBase, "MountX/Android/obb/$pkg"),
                        targetPath = "/data/media/0/Android/obb/$pkg",
                        enabled = true,
                        sizeBytes = 0L
                    )
                )
            }
            MountMode.PKG -> {
                list.add(
                    MountPointConfig(
                        id = "legacy_${pkg}_pkg",
                        category = MountPointCategory.EXTERNAL_DATA,
                        sourcePath = resolveSdSourcePath(sdBase, "MountX/Android/data/$pkg"),
                        targetPath = "/data/media/0/Android/data/$pkg",
                        enabled = true,
                        sizeBytes = game.dataSizeBytes
                    )
                )
                list.add(
                    MountPointConfig(
                        id = "legacy_${pkg}_obb",
                        category = MountPointCategory.OBB_STORAGE,
                        sourcePath = resolveSdSourcePath(sdBase, "MountX/Android/obb/$pkg"),
                        targetPath = "/data/media/0/Android/obb/$pkg",
                        enabled = true,
                        sizeBytes = 0L
                    )
                )
            }
        }

        // Auto-detect Media & Downloads if available
        val mediaInternal = "/data/media/0/Android/media/$pkg"
        if (RootShell.exists(mediaInternal)) {
            list.add(
                MountPointConfig(
                    id = "media_$pkg",
                    category = MountPointCategory.MEDIA_DOWNLOADS,
                    sourcePath = resolveSdSourcePath(sdBase, "MountX/Android/media/$pkg"),
                    targetPath = mediaInternal,
                    enabled = true,
                    sizeBytes = 0L
                )
            )
        }

        return list
    }

    fun observeGames(): Flow<List<GameEntry>> = gameDao.getAllGames().map { list ->
        list.map { g ->
            if (g.mountPoints.isEmpty()) {
                g.copy(mountPoints = synthesizeLegacyMountPoints(g))
            } else {
                val normalizedPoints = g.mountPoints.map { mp ->
                    val resolved = mp.resolveCategory()
                    val canonicalSource = if (!mp.sourcePath.contains("/MountX/Android/") && mp.sourcePath.contains("/Android/")) {
                        mp.sourcePath.replace("/Android/", "/MountX/Android/")
                    } else {
                        mp.sourcePath
                    }
                    var item = mp
                    if (resolved != item.category) item = item.copy(category = resolved)
                    if (canonicalSource != item.sourcePath) item = item.copy(sourcePath = canonicalSource)
                    item
                }
                if (normalizedPoints != g.mountPoints) {
                    g.copy(mountPoints = normalizedPoints)
                } else {
                    g
                }
            }
        }
    }

    fun observeMountedCount(): Flow<Int> = gameDao.getMountedCount()

    fun observeTotalCount(): Flow<Int> = gameDao.getTotalCount()

    suspend fun getGame(packageName: String): GameEntry? = withContext(Dispatchers.IO) {
        val game = gameDao.getGameByPackage(packageName) ?: return@withContext null
        if (game.mountPoints.isEmpty()) {
            val synthesized = synthesizeLegacyMountPoints(game)
            val updated = game.copy(mountPoints = synthesized)
            gameDao.updateGame(updated)
            updated
        } else {
            val normalizedPoints = game.mountPoints.map { mp ->
                val resolved = mp.resolveCategory()
                val canonicalSource = if (!mp.sourcePath.contains("/MountX/Android/") && mp.sourcePath.contains("/Android/")) {
                    mp.sourcePath.replace("/Android/", "/MountX/Android/")
                } else {
                    mp.sourcePath
                }
                var item = mp
                if (resolved != item.category) item = item.copy(category = resolved)
                if (canonicalSource != item.sourcePath) item = item.copy(sourcePath = canonicalSource)
                item
            }
            if (normalizedPoints != game.mountPoints) {
                val updated = game.copy(mountPoints = normalizedPoints)
                gameDao.updateGame(updated)
                updated
            } else {
                game
            }
        }
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

    suspend fun removeGameWithOption(
        context: Context,
        packageName: String,
        restoreToInternal: Boolean,
        sdBase: String = "/data/sdext2",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val game = gameDao.getGameByPackage(packageName)
            if (game != null) {
                if (game.mountStatus == MountStatus.MOUNTED) {
                    mountManager.unmountGame(game)
                }

                if (restoreToInternal) {
                    val points = game.mountPoints.ifEmpty { synthesizeLegacyMountPoints(game, sdBase) }
                    storageManager.restoreAndCleanGame(
                        context = context,
                        packageName = packageName,
                        mountPoints = points,
                        sdBase = sdBase,
                        onProgress = onProgress
                    ).getOrThrow()
                } else {
                    onProgress(0.5f, "Melepaskan mount sistem...")
                    RootShell.exec("am force-stop \"$packageName\"")
                    for (point in game.mountPoints) {
                        RootShell.exec("umount -l \"${point.targetPath}\" 2>/dev/null")
                    }
                    RootShell.exec("restorecon -FR \"/data/media/0/Android/data/$packageName\" 2>/dev/null")
                    RootShell.exec("restorecon -FR \"/data/media/0/Android/obb/$packageName\" 2>/dev/null")
                }
            }

            gameDao.deleteGame(packageName)
            syncModuleGamelist()
            AppLogger.info("Games", "Removed game: $packageName (restoreToInternal=$restoreToInternal)")
        }
    }

    suspend fun mountGame(
        game: GameEntry,
        sdBase: String = "/data/sdext2",
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            AppLogger.info("Games", "Mounting ${game.displayName} (${game.packageName}) [${game.mode.name}]")
            val result = mountManager.mountGame(game, sdBase, onProgress)
            if (result.isSuccess) {
                gameDao.updateMountStatus(game.packageName, MountStatus.MOUNTED)
                AppLogger.success("Games", "Successfully mounted ${game.displayName}")
            } else {
                val ex = result.exceptionOrNull()
                val newStatus = if (ex is app.mountx.root.OcclusionHazardException) {
                    MountStatus.NEED_MIGRATION
                } else {
                    MountStatus.ERROR
                }
                gameDao.updateMountStatus(game.packageName, newStatus)
                AppLogger.error("Games", "Failed to mount ${game.displayName} [Status: $newStatus]: ${ex?.message}")
            }
            result
        }

    suspend fun unmountGame(
        game: GameEntry,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            AppLogger.info("Games", "Unmounting ${game.displayName} (${game.packageName})")
            val result = mountManager.unmountGame(game, onProgress)
            if (result.isSuccess) {
                gameDao.updateMountStatus(game.packageName, MountStatus.UNMOUNTED)
                AppLogger.success("Games", "Successfully unmounted ${game.displayName}")
            } else {
                gameDao.updateMountStatus(game.packageName, MountStatus.ERROR)
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

    suspend fun restructureGame(
        game: DiscoveredGame,
        sdBase: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        diskCatalogManager.restructureGameToStandard(sdBase, game, onProgress)
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
            val newStatus = when {
                isMounted -> MountStatus.MOUNTED
                g.mountStatus == MountStatus.NEED_MIGRATION -> MountStatus.NEED_MIGRATION
                g.mountStatus == MountStatus.ERROR -> MountStatus.ERROR
                else -> MountStatus.UNMOUNTED
            }
            gameDao.updateMountStatus(g.packageName, newStatus)
        }
    }

    private suspend fun getExternalStorageBases(defaultSdBase: String, game: GameEntry?): List<String> {
        val bases = linkedSetOf<String>()
        if (defaultSdBase.isNotBlank()) bases.add(defaultSdBase.trimEnd('/'))

        // 1. Add bases from configured mount points in game
        game?.mountPoints?.forEach { mp ->
            if (mp.sourcePath.isNotBlank()) {
                val p = mp.sourcePath
                val idx = p.indexOf("/Android/")
                if (idx > 0) {
                    bases.add(p.substring(0, idx).trimEnd('/'))
                } else {
                    val parent = java.io.File(p).parentFile?.parentFile?.parent
                    if (!parent.isNullOrBlank()) bases.add(parent.trimEnd('/'))
                }
            }
        }

        // 2. Discover sdext and vold external storage mounts
        val res = RootShell.exec("ls -d /data/sdext* /mnt/media_rw/* 2>/dev/null")
        if (res.isSuccess) {
            res.stdout.forEach { line ->
                val p = line.trim().trimEnd('/')
                if (p.isNotBlank() && !p.contains("emulated") && !p.endsWith("/usbotg")) {
                    bases.add(p)
                }
            }
        }

        return bases.toList()
    }

    suspend fun calculateDataSize(packageName: String, sdBase: String = "/data/sdext2"): Long =
        withContext(Dispatchers.IO) {
            val game = gameDao.getGameByPackage(packageName)
            val internalData = "/data/media/0/Android/data/$packageName"
            val internalObb = "/data/media/0/Android/obb/$packageName"
            val isDataMountedReal = RootShell.isMountpoint(internalData)
            val isObbMountedReal = RootShell.isMountpoint(internalObb)

            val externalBases = getExternalStorageBases(sdBase, game)

            val candidateExtDataPaths = externalBases.flatMap { listOf("$it/MountX/Android/data/$packageName", "$it/Android/data/$packageName") }.toMutableList()
            val candidateExtObbPaths = externalBases.flatMap { listOf("$it/MountX/Android/obb/$packageName", "$it/Android/obb/$packageName") }.toMutableList()
            val candidateExtMediaPaths = externalBases.flatMap { listOf("$it/MountX/Android/media/$packageName", "$it/Android/media/$packageName") }.toMutableList()
            val candidateCustomPaths = mutableListOf<String>()
            game?.mountPoints?.forEach { mp ->
                if (mp.sourcePath.isNotBlank()) {
                    when (mp.category) {
                        MountPointCategory.EXTERNAL_DATA, MountPointCategory.GAME_ASSETS -> candidateExtDataPaths.add(mp.sourcePath)
                        MountPointCategory.OBB_STORAGE -> candidateExtObbPaths.add(mp.sourcePath)
                        MountPointCategory.MEDIA_DOWNLOADS -> candidateExtMediaPaths.add(mp.sourcePath)
                        MountPointCategory.CUSTOM -> candidateCustomPaths.add(mp.sourcePath)
                        else -> {}
                    }
                }
            }

            val pathsToScan = mutableListOf<String>()
            pathsToScan.add(internalData)
            pathsToScan.add(internalObb)
            pathsToScan.addAll(candidateExtDataPaths)
            pathsToScan.addAll(candidateExtObbPaths)
            pathsToScan.addAll(candidateExtMediaPaths)
            pathsToScan.addAll(candidateCustomPaths)

            val validTargets = pathsToScan
                .map { it.trim().trim('\"') }
                .filter { it.isNotBlank() && it != "/" && it != "." }
                .distinct()

            val sizeMap = mutableMapOf<String, Long>()
            if (validTargets.isNotEmpty()) {
                val targets = validTargets.joinToString(" ") { "\"$it\"" }
                val res = RootShell.exec("du -sk $targets 2>/dev/null")
                for (line in res.stdout) {
                    val parts = line.trim().split(Regex("\\s+"), limit = 2)
                    if (parts.size >= 2) {
                        val kb = parts[0].toLongOrNull() ?: continue
                        val path = parts[1]
                        sizeMap[path] = kb * 1024L
                    }
                }
            }

            val internalDataSize = sizeMap[internalData] ?: 0L
            val internalObbSize = sizeMap[internalObb] ?: 0L

            var extDataBytesSum = 0L
            val seenExtBases = mutableSetOf<String>()
            candidateExtDataPaths.distinct().forEach { p ->
                val baseKey = p.substringBefore("/Android/")
                if (seenExtBases.add(baseKey)) {
                    val b = sizeMap[p] ?: 0L
                    extDataBytesSum += b
                }
            }

            var extObbBytesSum = 0L
            val seenExtObbBases = mutableSetOf<String>()
            candidateExtObbPaths.distinct().forEach { p ->
                val baseKey = p.substringBefore("/Android/")
                if (seenExtObbBases.add(baseKey)) {
                    val b = sizeMap[p] ?: 0L
                    extObbBytesSum += b
                }
            }

            var extMediaBytesSum = 0L
            val seenExtMediaBases = mutableSetOf<String>()
            candidateExtMediaPaths.distinct().forEach { p ->
                val baseKey = p.substringBefore("/Android/")
                if (seenExtMediaBases.add(baseKey)) {
                    val b = sizeMap[p] ?: 0L
                    extMediaBytesSum += b
                }
            }

            var customBytesSum = 0L
            candidateCustomPaths.distinct().forEach { p ->
                customBytesSum += (sizeMap[p] ?: 0L)
            }

            // Smart Data Size Resolution:
            // A canary / empty directory skeleton is typically <= 128 KB.
            // When game data is in internal storage (e.g. 141 MB) and MicroSD only has an empty skeleton (20 KB),
            // or vice versa, always capture the substantive data size rather than overwriting with a 20 KB skeleton.
            val effectiveDataSize = when {
                extDataBytesSum > 512 * 1024L && internalDataSize <= 128 * 1024L -> extDataBytesSum
                internalDataSize > 512 * 1024L && extDataBytesSum <= 128 * 1024L -> internalDataSize
                isDataMountedReal -> if (extDataBytesSum > 128 * 1024L) extDataBytesSum else maxOf(internalDataSize, extDataBytesSum)
                else -> maxOf(internalDataSize, extDataBytesSum)
            }

            val effectiveObbSize = when {
                extObbBytesSum > 512 * 1024L && internalObbSize <= 128 * 1024L -> extObbBytesSum
                internalObbSize > 512 * 1024L && extObbBytesSum <= 128 * 1024L -> internalObbSize
                isObbMountedReal -> if (extObbBytesSum > 128 * 1024L) extObbBytesSum else maxOf(internalObbSize, extObbBytesSum)
                else -> maxOf(internalObbSize, extObbBytesSum)
            }

            var totalSize = effectiveDataSize + effectiveObbSize + extMediaBytesSum + customBytesSum
            val previousKnownSize = game?.dataSizeBytes ?: 0L
            if (totalSize <= 128 * 1024L && previousKnownSize > 512 * 1024L) {
                totalSize = previousKnownSize
            }
            if (totalSize > 0L) {
                gameDao.updateDataSize(packageName, totalSize)
            }

            // Auto-detect NEED_MIGRATION status for unmounted game with substantive internal data and empty SD
            if (game != null && !isDataMountedReal && !isObbMountedReal) {
                if (internalDataSize > 5 * 1024 * 1024L && extDataBytesSum <= 128 * 1024L) {
                    if (game.mountStatus == MountStatus.ERROR || game.mountStatus == MountStatus.UNMOUNTED) {
                        gameDao.updateMountStatus(packageName, MountStatus.NEED_MIGRATION)
                        AppLogger.info("GameRepo", "Flagged $packageName as NEED_MIGRATION (internal=${internalDataSize/1024}KB, sd=${extDataBytesSum/1024}KB)")
                    }
                }
            }

            totalSize
        }

    suspend fun getInternalAndSdSizes(packageName: String, sdBase: String = "/data/sdext2"): Pair<Long, Long> =
        withContext(Dispatchers.IO) {
            val game = gameDao.getGameByPackage(packageName)
            val internalData = "/data/media/0/Android/data/$packageName"
            val internalObb = "/data/media/0/Android/obb/$packageName"

            val externalBases = getExternalStorageBases(sdBase, game)
            val candidateExtDataPaths = externalBases.map { "$it/Android/data/$packageName" }.toMutableList()
            val candidateExtObbPaths = externalBases.map { "$it/Android/obb/$packageName" }.toMutableList()
            game?.mountPoints?.forEach { mp ->
                if (mp.sourcePath.isNotBlank()) {
                    if (mp.category == MountPointCategory.EXTERNAL_DATA || mp.category == MountPointCategory.GAME_ASSETS) {
                        candidateExtDataPaths.add(mp.sourcePath)
                    } else if (mp.category == MountPointCategory.OBB_STORAGE) {
                        candidateExtObbPaths.add(mp.sourcePath)
                    }
                }
            }

            val pathsToScan = mutableListOf(internalData, internalObb)
            pathsToScan.addAll(candidateExtDataPaths)
            pathsToScan.addAll(candidateExtObbPaths)

            val validTargets = pathsToScan
                .map { it.trim().trim('\"') }
                .filter { it.isNotBlank() && it != "/" && it != "." }
                .distinct()

            val sizeMap = mutableMapOf<String, Long>()
            if (validTargets.isNotEmpty()) {
                val targets = validTargets.joinToString(" ") { "\"$it\"" }
                val res = RootShell.exec("du -sk $targets 2>/dev/null")
                for (line in res.stdout) {
                    val parts = line.trim().split(Regex("\\s+"), limit = 2)
                    if (parts.size >= 2) {
                        val kb = parts[0].toLongOrNull() ?: continue
                        val path = parts[1]
                        sizeMap[path] = kb * 1024L
                    }
                }
            }

            val internalBytes = (sizeMap[internalData] ?: 0L) + (sizeMap[internalObb] ?: 0L)

            var extDataSum = 0L
            val seenExtBases = mutableSetOf<String>()
            candidateExtDataPaths.distinct().forEach { p ->
                val baseKey = p.substringBefore("/Android/")
                if (seenExtBases.add(baseKey)) {
                    extDataSum += (sizeMap[p] ?: 0L)
                }
            }

            var extObbSum = 0L
            val seenExtObbBases = mutableSetOf<String>()
            candidateExtObbPaths.distinct().forEach { p ->
                val baseKey = p.substringBefore("/Android/")
                if (seenExtObbBases.add(baseKey)) {
                    extObbSum += (sizeMap[p] ?: 0L)
                }
            }

            val sdBytes = extDataSum + extObbSum
            Pair(internalBytes, sdBytes)
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

        // Fallback: use root to get apkBytes if PackageManager returned null (e.g. missing QUERY_ALL_PACKAGES)
        if (apkBytes == 0L) {
            try {
                val pmPathRes = RootShell.exec("pm path \"$packageName\" 2>/dev/null | head -1")
                val apkPath = pmPathRes.output.trim().removePrefix("package:").trim()
                if (apkPath.isNotBlank()) {
                    val apkParent = java.io.File(apkPath).parent
                    if (apkParent != null) {
                        val duRes = RootShell.exec("du -sk \"$apkParent\" 2>/dev/null | head -1")
                        val parts = duRes.output.trim().split(Regex("\\s+"), limit = 2)
                        apkBytes = (parts.getOrNull(0)?.toLongOrNull() ?: 0L) * 1024L
                    }
                }
            } catch (_: Exception) {}
        }

        val game = gameDao.getGameByPackage(packageName)
        val externalBases = getExternalStorageBases(sdBase, game)

        val oatDir = if (sourceDir.isNotBlank()) "${java.io.File(sourceDir).parent}/oat" else ""
        val dataDir = "/data/data/$packageName"
        val cacheDir = "/data/data/$packageName/cache"
        val codeCacheDir = "/data/data/$packageName/code_cache"
        val ext1Data = "/data/media/0/Android/data/$packageName"
        val ext1Obb = "/data/media/0/Android/obb/$packageName"
        val ext1Media = "/data/media/0/Android/media/$packageName"
        var ext1MediaBytes = 0L
        var ext2MediaBytes = 0L

        // Use REAL mountpoint check (not DB status) to prevent stale-status false zeroing
        val isDataMountedReal = RootShell.isMountpoint(ext1Data)
        val isObbMountedReal = RootShell.isMountpoint(ext1Obb)
        val isMediaMountedReal = RootShell.isMountpoint(ext1Media)

        // Build candidate paths for all known external storages (both MountX and legacy)
        val candidateExtDataPaths = externalBases.flatMap { listOf("$it/MountX/Android/data/$packageName", "$it/Android/data/$packageName") }.toMutableList()
        val candidateExtObbPaths = externalBases.flatMap { listOf("$it/MountX/Android/obb/$packageName", "$it/Android/obb/$packageName") }.toMutableList()
        val candidateExtMediaPaths = externalBases.flatMap { listOf("$it/MountX/Android/media/$packageName", "$it/Android/media/$packageName") }.toMutableList()
        val candidateCustomPaths = mutableListOf<String>()
        game?.mountPoints?.forEach { mp ->
            if (mp.sourcePath.isNotBlank()) {
                when (mp.category) {
                    MountPointCategory.EXTERNAL_DATA, MountPointCategory.GAME_ASSETS -> candidateExtDataPaths.add(mp.sourcePath)
                    MountPointCategory.OBB_STORAGE -> candidateExtObbPaths.add(mp.sourcePath)
                    MountPointCategory.MEDIA_DOWNLOADS -> candidateExtMediaPaths.add(mp.sourcePath)
                    MountPointCategory.CUSTOM -> candidateCustomPaths.add(mp.sourcePath)
                    else -> {}
                }
            }
        }

        // Always scan ALL paths (internal + external) to get accurate sizes regardless of mountStatus.
        // When data is bind-mounted from SD to internal path, du on internal path returns SD size — correct.
        // When not mounted, internal path shows actual internal size — also correct.
        val targetsList = mutableListOf(oatDir, dataDir, cacheDir, codeCacheDir, ext1Data, ext1Obb, ext1Media)
        targetsList.addAll(candidateExtDataPaths)
        targetsList.addAll(candidateExtObbPaths)
        targetsList.addAll(candidateExtMediaPaths)
        targetsList.addAll(candidateCustomPaths)

        val validTargets = targetsList
            .map { it.trim().trim('\"') }
            .filter { it.isNotBlank() && it != "/" && it != "." }
            .distinct()

        val sizeMap = mutableMapOf<String, Long>()
        if (validTargets.isNotEmpty()) {
            val targets = validTargets.joinToString(" ") { "\"$it\"" }
            AppLogger.info("GameRepo", "du targets (${validTargets.size}): $targets")
            val res = RootShell.exec("du -sk $targets 2>/dev/null")
            AppLogger.info("GameRepo", "du res code=${res.code}, outLines=${res.stdout.size}")
            for (line in res.stdout) {
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                if (parts.size >= 2) {
                    val kb = parts[0].toLongOrNull() ?: continue
                    val path = parts[1]
                    val bytes = kb * 1024L
                    sizeMap[path] = bytes
                    AppLogger.info("GameRepo", "  du result: $path = ${kb}KB")

                    when {
                        path.endsWith("/oat") || path.contains("/oat/") -> dexBytes = bytes
                        path.endsWith("/cache") || path.endsWith("/code_cache") -> cacheBytes += bytes
                        path == dataDir -> rawPrivateDataBytes = bytes
                        path == ext1Data -> ext1DataBytes = bytes
                        path == ext1Obb -> ext1ObbBytes = bytes
                        path == ext1Media -> ext1MediaBytes = bytes
                    }
                }
            }
        }

        // Accumulate all external partition sizes per unique storage base.
        // Deduplication by base path prevents counting the same physical data twice when
        // accessed via different bind-mount paths (e.g., /storage/UUID vs /mnt/media_rw/UUID).
        var extDataBytesSum = 0L
        var extObbBytesSum = 0L
        var extMediaBytesSum = 0L
        val seenExtBases = mutableSetOf<String>()
        candidateExtDataPaths.distinct().forEach { p ->
            val baseKey = p.substringBefore("/Android/")
            if (seenExtBases.add(baseKey)) {
                val b = sizeMap[p] ?: 0L
                extDataBytesSum += b
                AppLogger.info("GameRepo", "  ext data $p = ${b/1024}KB (base=$baseKey)")
            }
        }
        val seenExtObbBases = mutableSetOf<String>()
        candidateExtObbPaths.distinct().forEach { p ->
            val baseKey = p.substringBefore("/Android/")
            if (seenExtObbBases.add(baseKey)) {
                val b = sizeMap[p] ?: 0L
                extObbBytesSum += b
                AppLogger.info("GameRepo", "  ext obb $p = ${b/1024}KB (base=$baseKey)")
            }
        }
        val seenExtMediaBases = mutableSetOf<String>()
        candidateExtMediaPaths.distinct().forEach { p ->
            val baseKey = p.substringBefore("/Android/")
            if (seenExtMediaBases.add(baseKey)) {
                val b = sizeMap[p] ?: 0L
                extMediaBytesSum += b
                AppLogger.info("GameRepo", "  ext media $p = ${b/1024}KB (base=$baseKey)")
            }
        }
        var customBytesSum = 0L
        candidateCustomPaths.distinct().forEach { p ->
            val b = sizeMap[p] ?: 0L
            customBytesSum += b
            AppLogger.info("GameRepo", "  custom $p = ${b/1024}KB")
        }
        ext2DataBytes = extDataBytesSum
        ext2ObbBytes = extObbBytesSum
        ext2MediaBytes = extMediaBytesSum

        val dataBytes = (rawPrivateDataBytes - cacheBytes).coerceAtLeast(0L)

        // ext1Bytes: what's at the internal Android/data path (actual size — follows bind mount when mounted)
        val ext1Bytes = ext1DataBytes + ext1ObbBytes + ext1MediaBytes

        // ext2Bytes: what's directly on external storage partitions (source paths)
        val ext2Bytes = ext2DataBytes + ext2ObbBytes + ext2MediaBytes

        AppLogger.info("GameRepo", "Breakdown[$packageName]: apk=${apkBytes/1024}KB lib=${libBytes/1024}KB data=${dataBytes/1024}KB cache=${cacheBytes/1024}KB ext1=${ext1Bytes/1024}KB(data=${ext1DataBytes/1024} obb=${ext1ObbBytes/1024} media=${ext1MediaBytes/1024} mounted=$isDataMountedReal) ext2=${ext2Bytes/1024}KB(data=${ext2DataBytes/1024} obb=${ext2ObbBytes/1024} media=${ext2MediaBytes/1024} custom=${customBytesSum/1024})")

        // Directly update Room DB dataSizeBytes with accurate effective game data size
        val effectiveSize = when {
            ext2Bytes > 512 * 1024L && ext1Bytes <= 128 * 1024L -> ext2Bytes + customBytesSum
            ext1Bytes > 512 * 1024L && ext2Bytes <= 128 * 1024L -> ext1Bytes + customBytesSum
            isDataMountedReal || isObbMountedReal -> if (ext2Bytes > 128 * 1024L) ext2Bytes + customBytesSum else maxOf(ext1Bytes, ext2Bytes) + customBytesSum
            else -> maxOf(ext1Bytes, ext2Bytes) + customBytesSum
        }
        val previousKnownSize = game?.dataSizeBytes ?: 0L
        val resolvedSize = if (effectiveSize <= 128 * 1024L && previousKnownSize > 512 * 1024L) {
            previousKnownSize
        } else {
            effectiveSize
        }
        if (game != null && resolvedSize > 0L) {
            gameDao.updateDataSize(packageName, resolvedSize)
        }

        val isExt1MountedReal = isDataMountedReal || isObbMountedReal || isMediaMountedReal
        // Self-healing: If kernel reports NO active mount point and external storage has only empty folder skeleton (<= 64KB),
        // but DB has stale MOUNTED, correct it to UNMOUNTED.
        if (game != null && game.mountStatus == MountStatus.MOUNTED && !isExt1MountedReal && ext2Bytes <= 64 * 1024L) {
            AppLogger.warn("GameRepo", "Self-healing: Correcting stale MOUNTED status for $packageName to UNMOUNTED (no active VFS mount & ext2 is skeleton).")
            gameDao.updateMountStatus(packageName, MountStatus.UNMOUNTED)
        }

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
            ext1MediaBytes = ext1MediaBytes,
            ext2DataBytes = ext2DataBytes,
            ext2ObbBytes = ext2ObbBytes,
            ext2MediaBytes = ext2MediaBytes,
            customBytes = customBytesSum,
            isExt1Mounted = isExt1MountedReal,
            isDataMounted = isDataMountedReal,
            isObbMounted = isObbMountedReal,
            isMediaMounted = isMediaMountedReal
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

        // 1. EXTERNAL_DATA: Android/data/<pkg>/files
        val internalFiles = "/data/media/0/Android/data/$packageName/files"
        val sdFiles = "$sdBase/Android/data/$packageName/files"
        val internalFilesSize = getDirSizeBytes(internalFiles)
        val sdFilesSize = getDirSizeBytes(sdFiles)
        val filesSize = if (sdFilesSize > 16384L && sdFilesSize > internalFilesSize) sdFilesSize else internalFilesSize
        val filesChildren = scanSubItems(if (RootShell.exists(internalFiles)) internalFiles else sdFiles, sdFiles)
        list.add(
            CandidateDirectory(
                id = "external_data",
                category = app.mountx.data.model.MountPointCategory.EXTERNAL_DATA,
                title = "Data (Android/data)",
                description = "Data aset dan unduhan in-game (/Android/data). Komponen terbesar game.",
                relativePath = "Android/data/$packageName/files",
                internalPath = internalFiles,
                sdPath = sdFiles,
                sizeBytes = filesSize,
                defaultEnabled = true,
                childItems = filesChildren
            )
        )

        // 2. OBB_STORAGE: Android/obb/<pkg>
        val internalObb = "/data/media/0/Android/obb/$packageName"
        val sdObb = "$sdBase/Android/obb/$packageName"
        val internalObbSize = getDirSizeBytes(internalObb)
        val sdObbSize = getDirSizeBytes(sdObb)
        val obbSize = if (sdObbSize > 16384L && sdObbSize > internalObbSize) sdObbSize else internalObbSize
        val obbChildren = scanSubItems(if (RootShell.exists(internalObb)) internalObb else sdObb, sdObb)
        list.add(
            CandidateDirectory(
                id = "obb_storage",
                category = app.mountx.data.model.MountPointCategory.OBB_STORAGE,
                title = "OBB (Android/obb)",
                description = "Berkas arsip paket instalasi game (/Android/obb). Aman dimount ke MicroSD.",
                relativePath = "Android/obb/$packageName",
                internalPath = internalObb,
                sdPath = sdObb,
                sizeBytes = obbSize,
                defaultEnabled = true,
                childItems = obbChildren
            )
        )

        // 3. MEDIA_DOWNLOADS: Android/media or public app folder
        val internalMedia = "/data/media/0/Android/media/$packageName"
        val sdMedia = "$sdBase/Android/media/$packageName"
        val pubFolder = "/data/media/0/${displayName.replace(" ", "")}"
        val pubSize = if (RootShell.exists(pubFolder)) getDirSizeBytes(pubFolder) else 0L
        val mediaSize = getDirSizeBytes(if (RootShell.exists(sdMedia)) sdMedia else internalMedia) + pubSize
        val mediaChildren = scanSubItems(if (RootShell.exists(internalMedia)) internalMedia else sdMedia, sdMedia)
        list.add(
            CandidateDirectory(
                id = "media_downloads",
                category = app.mountx.data.model.MountPointCategory.MEDIA_DOWNLOADS,
                title = "Media & Unduhan (Android/media & Publik)",
                description = "Folder media publik dan unduhan. Otomatis menyertakan berkas .nomedia di MicroSD.",
                relativePath = "Android/media/$packageName",
                internalPath = internalMedia,
                sdPath = sdMedia,
                sizeBytes = mediaSize,
                defaultEnabled = mediaSize > 0L,
                childItems = mediaChildren
            )
        )

        // 4. CACHE_SHADERS: Android/data/cache
        val internalCache = "/data/media/0/Android/data/$packageName/cache"
        val sdCache = "$sdBase/Android/data/$packageName/cache"
        val cacheSize = getDirSizeBytes(if (RootShell.exists(sdCache)) sdCache else internalCache)
        val cacheChildren = scanSubItems(if (RootShell.exists(internalCache)) internalCache else sdCache, sdCache)
        list.add(
            CandidateDirectory(
                id = "cache_shaders",
                category = app.mountx.data.model.MountPointCategory.CACHE_SHADERS,
                title = "Cache & Temporary (Android/data/cache)",
                description = "Cache dan file sementara. Disarankan tetap di internal UFS agar tidak memicu micro-stuttering.",
                relativePath = "Android/data/$packageName/cache",
                internalPath = internalCache,
                sdPath = sdCache,
                sizeBytes = cacheSize,
                defaultEnabled = false,
                childItems = cacheChildren
            )
        )

        // 5. PRIVATE_INTERNAL: /data/data/<pkg>
        val privateDataDir = "/data/user/0/$packageName"
        val privateSize = getDirSizeBytes(privateDataDir)
        val oneGb = 1024L * 1024L * 1024L
        val privateChildren = scanSubItems(privateDataDir, "$sdBase/.mountx/containers/${packageName}_data.img")

        if (privateSize >= oneGb) {
            list.add(
                CandidateDirectory(
                    id = "private_internal",
                    category = app.mountx.data.model.MountPointCategory.PRIVATE_INTERNAL,
                    title = "Private Data (/data/data)",
                    description = "Data internal aplikasi. Folder gajah (> 1 GB) menggunakan Virtual Ext4 Loop Container.",
                    relativePath = "data/user/0/$packageName",
                    internalPath = privateDataDir,
                    sdPath = "$sdBase/.mountx/containers/${packageName}_data.img",
                    sizeBytes = privateSize,
                    defaultEnabled = false,
                    isLocked = false,
                    isVirtualContainer = true,
                    childItems = privateChildren
                )
            )
        } else if (privateSize > 0L) {
            list.add(
                CandidateDirectory(
                    id = "private_internal_locked",
                    category = app.mountx.data.model.MountPointCategory.PRIVATE_INTERNAL,
                    title = "Private Data (/data/data)",
                    description = "Terkunci: Data internal < 1 GB wajib berada di internal flash untuk mencegah error SQLite WAL database lock.",
                    relativePath = "data/user/0/$packageName",
                    internalPath = privateDataDir,
                    sdPath = "$sdBase/.mountx/containers/${packageName}_data.img",
                    sizeBytes = privateSize,
                    defaultEnabled = false,
                    isLocked = true,
                    lockReason = "Ukuran < 1 GB dikunci demi keselamatan database",
                    childItems = privateChildren
                )
            )
        }

        // 6. ADVANCED EXPERIMENTAL: App Package (APK & Libs)
        val apkPathRes = RootShell.exec("pm path \"$packageName\" 2>/dev/null | head -n 1")
        if (apkPathRes.isSuccess && apkPathRes.output.contains("package:")) {
            val fullApkPath = apkPathRes.output.substringAfter("package:").trim()
            val apkDir = java.io.File(fullApkPath).parent ?: ""
            if (apkDir.isNotBlank() && RootShell.exists(apkDir)) {
                val apkSize = getDirSizeBytes(apkDir)
                val apkChildren = scanSubItems(apkDir, "$sdBase/Android/app/$packageName")
                list.add(
                    CandidateDirectory(
                        id = "app_package_experimental",
                        category = app.mountx.data.model.MountPointCategory.APP_PACKAGE,
                        title = "App Package (APK & Native Libs)",
                        description = "Biner APK dan file library (.so) di /data/app/. Membutuhkan partisi MicroSD bertipe Linux (ext4/f2fs) dan proteksi SELinux.",
                        relativePath = "data/app/$packageName",
                        internalPath = apkDir,
                        sdPath = "$sdBase/Android/app/$packageName",
                        sizeBytes = apkSize,
                        defaultEnabled = false,
                        isExperimental = true,
                        childItems = apkChildren
                    )
                )
            }
        }

        list
    }

    private suspend fun scanSubItems(parentDir: String, sdParentDir: String): List<CandidateSubItem> {
        if (!RootShell.exists(parentDir)) return emptyList()
        val res = RootShell.exec("ls -1 \"$parentDir\" 2>/dev/null")
        if (!res.isSuccess || res.stdout.isEmpty()) return emptyList()
        val items = mutableListOf<CandidateSubItem>()
        for (name in res.stdout.map { it.trim() }.filter { it.isNotBlank() }) {
            val childPath = "$parentDir/$name"
            val childSd = "$sdParentDir/$name"
            val sz = getDirSizeBytes(childPath)
            items.add(
                CandidateSubItem(
                    id = name,
                    name = name,
                    internalPath = childPath,
                    sdPath = childSd,
                    sizeBytes = sz,
                    enabled = true
                )
            )
        }
        return items
    }

    private suspend fun getDirSizeBytes(path: String): Long {
        if (!RootShell.exists(path)) return 0L
        val res = RootShell.exec("du -sk \"$path\" 2>/dev/null | cut -f1")
        val kb = res.output.trim().toLongOrNull() ?: 0L
        return kb * 1024L
    }

    suspend fun deleteCategoryData(
        context: Context,
        packageName: String,
        categoryId: String,
        location: CategoryDeleteLocation,
        sdBase: String = "/data/sdext2"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val game = gameDao.getGameByPackage(packageName)
            val externalBases = getExternalStorageBases(sdBase, game)

            // Terminate application before deleting data to prevent file locks or corruption
            RootShell.exec("am force-stop \"$packageName\"")

            when (categoryId) {
                "cache" -> {
                    if (location == CategoryDeleteLocation.INTERNAL_ONLY || location == CategoryDeleteLocation.BOTH) {
                        RootShell.exec("rm -rf \"/data/data/$packageName/cache\"/* \"/data/data/$packageName/code_cache\"/* 2>/dev/null")
                        RootShell.exec("rm -rf \"/data/user/0/$packageName/cache\"/* \"/data/user/0/$packageName/code_cache\"/* 2>/dev/null")
                        RootShell.exec("rm -rf \"/data/media/0/Android/data/$packageName/cache\"/* 2>/dev/null")
                    }
                    if (location == CategoryDeleteLocation.SD_ONLY || location == CategoryDeleteLocation.BOTH) {
                        externalBases.forEach { base ->
                            RootShell.exec("rm -rf \"$base/MountX/Android/data/$packageName/cache\" 2>/dev/null")
                            RootShell.exec("rm -rf \"$base/Android/data/$packageName/cache\" 2>/dev/null")
                            RootShell.exec("rm -rf \"$base/MountX/MountX/Android/data/$packageName/cache\" 2>/dev/null")
                        }
                    }
                }
                "private" -> {
                    RootShell.exec("pm clear \"$packageName\"")
                }
                "data" -> {
                    val internalPath = "/data/media/0/Android/data/$packageName"
                    val isMounted = RootShell.isMountpoint(internalPath)

                    if (isMounted) {
                        RootShell.exec("umount -l \"$internalPath\" 2>/dev/null")
                    }

                    if (location == CategoryDeleteLocation.SD_ONLY || location == CategoryDeleteLocation.BOTH) {
                        val sdPaths = mutableListOf<String>()
                        externalBases.forEach { base ->
                            sdPaths.add("$base/MountX/Android/data/$packageName")
                            sdPaths.add("$base/Android/data/$packageName")
                            sdPaths.add("$base/MountX/MountX/Android/data/$packageName")
                        }
                        game?.mountPoints?.filter {
                            it.category == MountPointCategory.EXTERNAL_DATA || it.category == MountPointCategory.GAME_ASSETS
                        }?.forEach { mp ->
                            if (mp.sourcePath.isNotBlank()) sdPaths.add(mp.sourcePath)
                        }
                        sdPaths.distinct().forEach { path ->
                            RootShell.exec("rm -rf \"$path\" 2>/dev/null")
                        }

                        if (game != null) {
                            val updatedPoints = game.mountPoints.map { mp ->
                                if (mp.resolveCategory() == MountPointCategory.EXTERNAL_DATA || mp.category == MountPointCategory.GAME_ASSETS) {
                                    mp.copy(enabled = false)
                                } else {
                                    mp
                                }
                            }
                            gameDao.updateGame(game.copy(mountPoints = updatedPoints))
                        }
                    }

                    if (location == CategoryDeleteLocation.INTERNAL_ONLY || location == CategoryDeleteLocation.BOTH) {
                        RootShell.exec("rm -rf \"$internalPath\" 2>/dev/null")
                    }

                    // Always ensure clean, accessible internal directory exists
                    RootShell.exec("mkdir -p \"$internalPath\" 2>/dev/null")
                    val uid = try {
                        context.packageManager.getPackageUid(packageName, 0)
                    } catch (_: Exception) {
                        1000
                    }
                    RootShell.exec("chown $uid:1023 \"$internalPath\" 2>/dev/null")
                    RootShell.exec("chmod 775 \"$internalPath\" 2>/dev/null")
                    RootShell.exec("restorecon -FR \"$internalPath\" 2>/dev/null")

                    if (location == CategoryDeleteLocation.INTERNAL_ONLY && isMounted && game != null && game.mountStatus == MountStatus.MOUNTED) {
                        mountGame(game, sdBase)
                    }
                }
                "obb" -> {
                    val internalPath = "/data/media/0/Android/obb/$packageName"
                    val isMounted = RootShell.isMountpoint(internalPath)

                    if (isMounted) {
                        RootShell.exec("umount -l \"$internalPath\" 2>/dev/null")
                    }

                    if (location == CategoryDeleteLocation.SD_ONLY || location == CategoryDeleteLocation.BOTH) {
                        val sdPaths = mutableListOf<String>()
                        externalBases.forEach { base ->
                            sdPaths.add("$base/MountX/Android/obb/$packageName")
                            sdPaths.add("$base/Android/obb/$packageName")
                            sdPaths.add("$base/MountX/MountX/Android/obb/$packageName")
                        }
                        game?.mountPoints?.filter {
                            it.category == MountPointCategory.OBB_STORAGE
                        }?.forEach { mp ->
                            if (mp.sourcePath.isNotBlank()) sdPaths.add(mp.sourcePath)
                        }
                        sdPaths.distinct().forEach { path ->
                            RootShell.exec("rm -rf \"$path\" 2>/dev/null")
                        }

                        if (game != null) {
                            val updatedPoints = game.mountPoints.map { mp ->
                                if (mp.resolveCategory() == MountPointCategory.OBB_STORAGE) {
                                    mp.copy(enabled = false)
                                } else {
                                    mp
                                }
                            }
                            gameDao.updateGame(game.copy(mountPoints = updatedPoints))
                        }
                    }

                    if (location == CategoryDeleteLocation.INTERNAL_ONLY || location == CategoryDeleteLocation.BOTH) {
                        RootShell.exec("rm -rf \"$internalPath\" 2>/dev/null")
                    }

                    // Always ensure clean, accessible internal directory exists
                    RootShell.exec("mkdir -p \"$internalPath\" 2>/dev/null")
                    val uid = try {
                        context.packageManager.getPackageUid(packageName, 0)
                    } catch (_: Exception) {
                        1000
                    }
                    RootShell.exec("chown $uid:1023 \"$internalPath\" 2>/dev/null")
                    RootShell.exec("chmod 775 \"$internalPath\" 2>/dev/null")
                    RootShell.exec("restorecon -FR \"$internalPath\" 2>/dev/null")

                    if (location == CategoryDeleteLocation.INTERNAL_ONLY && isMounted && game != null && game.mountStatus == MountStatus.MOUNTED) {
                        mountGame(game, sdBase)
                    }
                }
                "media" -> {
                    val internalPath = "/data/media/0/Android/media/$packageName"
                    val isMounted = RootShell.isMountpoint(internalPath)

                    if (isMounted) {
                        RootShell.exec("umount -l \"$internalPath\" 2>/dev/null")
                    }

                    if (location == CategoryDeleteLocation.SD_ONLY || location == CategoryDeleteLocation.BOTH) {
                        val sdPaths = mutableListOf<String>()
                        externalBases.forEach { base ->
                            sdPaths.add("$base/MountX/Android/media/$packageName")
                            sdPaths.add("$base/Android/media/$packageName")
                            sdPaths.add("$base/MountX/MountX/Android/media/$packageName")
                        }
                        game?.mountPoints?.filter {
                            it.category == MountPointCategory.MEDIA_DOWNLOADS
                        }?.forEach { mp ->
                            if (mp.sourcePath.isNotBlank()) sdPaths.add(mp.sourcePath)
                        }
                        sdPaths.distinct().forEach { path ->
                            RootShell.exec("rm -rf \"$path\" 2>/dev/null")
                        }

                        if (game != null) {
                            val updatedPoints = game.mountPoints.map { mp ->
                                if (mp.resolveCategory() == MountPointCategory.MEDIA_DOWNLOADS) {
                                    mp.copy(enabled = false)
                                } else {
                                    mp
                                }
                            }
                            gameDao.updateGame(game.copy(mountPoints = updatedPoints))
                        }
                    }

                    if (location == CategoryDeleteLocation.INTERNAL_ONLY || location == CategoryDeleteLocation.BOTH) {
                        RootShell.exec("rm -rf \"$internalPath\" 2>/dev/null")
                    }

                    RootShell.exec("mkdir -p \"$internalPath\" 2>/dev/null")
                    val uid = try {
                        context.packageManager.getPackageUid(packageName, 0)
                    } catch (_: Exception) {
                        1000
                    }
                    RootShell.exec("chown $uid:1023 \"$internalPath\" 2>/dev/null")
                    RootShell.exec("chmod 775 \"$internalPath\" 2>/dev/null")
                    RootShell.exec("restorecon -FR \"$internalPath\" 2>/dev/null")

                    if (location == CategoryDeleteLocation.INTERNAL_ONLY && isMounted && game != null && game.mountStatus == MountStatus.MOUNTED) {
                        mountGame(game, sdBase)
                    }
                }
                "apk" -> {
                    RootShell.exec("pm uninstall \"$packageName\"")
                    gameDao.deleteGame(packageName)
                }
                "lib" -> {
                    val appInfo = try {
                        context.packageManager.getPackageInfo(packageName, 0).applicationInfo
                    } catch (_: Exception) {
                        null
                    }
                    val libDir = appInfo?.nativeLibraryDir
                    if (!libDir.isNullOrBlank()) {
                        RootShell.exec("rm -rf \"$libDir\"/* 2>/dev/null")
                    }
                }
                else -> {
                    // Custom mount point deletion
                    val customMp = game?.mountPoints?.firstOrNull { it.id == categoryId }
                    if (customMp != null) {
                        val src = customMp.sourcePath
                        val tgt = customMp.targetPath
                        if (RootShell.isMountpoint(tgt)) {
                            RootShell.exec("umount -l \"$tgt\" 2>/dev/null")
                        }
                        if (location == CategoryDeleteLocation.SD_ONLY || location == CategoryDeleteLocation.BOTH) {
                            if (src.isNotBlank()) {
                                RootShell.exec("rm -rf \"$src\" 2>/dev/null")
                            }
                            val updatedPoints = game.mountPoints.filter { it.id != customMp.id }
                            gameDao.updateGame(game.copy(mountPoints = updatedPoints))
                        }
                        if (location == CategoryDeleteLocation.INTERNAL_ONLY || location == CategoryDeleteLocation.BOTH) {
                            if (tgt.isNotBlank()) {
                                RootShell.exec("rm -rf \"$tgt\" 2>/dev/null")
                                RootShell.exec("mkdir -p \"$tgt\" 2>/dev/null")
                                RootShell.exec("restorecon -FR \"$tgt\" 2>/dev/null")
                            }
                        }
                        if (location == CategoryDeleteLocation.INTERNAL_ONLY && game.mountStatus == MountStatus.MOUNTED && src.isNotBlank() && tgt.isNotBlank()) {
                            RootShell.exec("mount -o bind \"$src\" \"$tgt\" 2>/dev/null")
                        }
                    }
                }
            }

            // After deletion, refresh mount status to accurately reflect unmounted state
            refreshMountStatuses()
            calculateDataSize(packageName, sdBase)
            syncModuleGamelist()
            syncDiskCatalog(sdBase)
            AppLogger.success("Storage", "Category $categoryId deleted for $packageName ($location)")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.error("Storage", "Failed to delete category $categoryId for $packageName: ${e.message}")
            Result.failure(e)
        }
    }
}

data class CandidateSubItem(
    val id: String,
    val name: String,
    val internalPath: String,
    val sdPath: String,
    val sizeBytes: Long,
    val enabled: Boolean = true
)

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
    val isVirtualContainer: Boolean = false,
    val isExperimental: Boolean = false,
    val childItems: List<CandidateSubItem> = emptyList()
)
