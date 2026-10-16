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
import kotlinx.coroutines.sync.withLock
import app.mountx.data.db.AppDao
import app.mountx.data.model.AppEntry
import app.mountx.service.MountNotificationManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val appDao: AppDao,
    private val mountManager: MountManager,
    private val diskCatalogManager: DiskCatalogManager,
    private val storageManager: app.mountx.root.StorageManager,
    private val mountNotificationManager: MountNotificationManager
) {
    private val gameDao get() = appDao

    fun observeApps(): Flow<List<AppEntry>> = observeGames()
    suspend fun getApp(packageName: String): AppEntry? = getGame(packageName)
    suspend fun addApp(
        packageName: String,
        displayName: String,
        mode: MountMode = MountMode.PKG,
        mountPoints: List<MountPointConfig> = emptyList(),
        initialSizeBytes: Long = 0L
    ) = addGame(packageName, displayName, mode, mountPoints, initialSizeBytes)
    suspend fun updateApp(app: AppEntry) = updateGame(app)
    suspend fun removeApp(packageName: String) = removeGame(packageName)
    suspend fun removeAppWithOption(
        context: Context,
        packageName: String,
        restoreToInternal: Boolean,
        sdBase: String = "/data/sdext2",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ) = removeGameWithOption(context, packageName, restoreToInternal, sdBase, onProgress)
    suspend fun syncModuleConfig() = syncModuleGamelist()

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
        if (game.mountPoints.isNotEmpty()) {
            return if (game.mountPoints.none { it.enabled }) {
                game.mountPoints.map { it.copy(enabled = true) }
            } else {
                game.mountPoints
            }
        }
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

    suspend fun getMountedGamesCount(): Int = withContext(Dispatchers.IO) {
        gameDao.getMountedCount().firstOrNull() ?: 0
    }

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

    suspend fun removeGame(packageName: String, sdBase: String = "/data/sdext2") = withContext(Dispatchers.IO) {
        val game = gameDao.getGameByPackage(packageName)
        if (game != null && game.mountStatus == MountStatus.MOUNTED) {
            mountManager.unmountGame(game)
        }
        gameDao.deleteGame(packageName)
        syncModuleGamelist()
        diskCatalogManager.removeGameFromCatalog(sdBase, packageName)
        syncDiskCatalog(sdBase)
        mountNotificationManager.syncActiveMountNotification()
        AppLogger.info("Games", "Removed game: $packageName")
    }

    suspend fun removeGameWithOption(
        context: Context,
        packageName: String,
        restoreToInternal: Boolean,
        sdBase: String = "/data/sdext2",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        RootShell.rootExecutionMutex.withLock {
            runCatching {
                // 1. Teardown Process Guard: Force-stop process immediately to prevent file locks
                RootShell.exec("am force-stop \"$packageName\"")

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
                        for (point in game.mountPoints) {
                            val ptUserId = app.mountx.root.MountManager.resolveUserId(point.targetPath)
                            val rel = app.mountx.root.MountManager.extractRelativePath(point.targetPath)
                            for (ns in listOf("/mnt/runtime/default", "/mnt/runtime/read", "/mnt/runtime/write", "/mnt/runtime/full", "/storage")) {
                                RootShell.exec("umount -f -l \"$ns/emulated/$ptUserId/$rel\" 2>/dev/null")
                                RootShell.exec("umount -f -l \"$ns/$rel\" 2>/dev/null")
                            }
                            RootShell.exec("umount -f -l \"${point.targetPath}\" 2>/dev/null")
                        }
                        val gameUserId = game.mountPoints.firstOrNull()?.let { app.mountx.root.MountManager.resolveUserId(it.targetPath) } ?: 0
                        // Recreate clean valid empty directories so app won't force close if opened
                        RootShell.exec("mkdir -p \"/data/media/$gameUserId/Android/data/$packageName\"")
                        RootShell.exec("mkdir -p \"/data/media/$gameUserId/Android/obb/$packageName\"")
                        RootShell.exec("chown -R 1023:1023 \"/data/media/$gameUserId/Android/data/$packageName\" 2>/dev/null")
                        RootShell.exec("chown -R 1023:1023 \"/data/media/$gameUserId/Android/obb/$packageName\" 2>/dev/null")
                        RootShell.exec("restorecon -FR \"/data/media/$gameUserId/Android/data/$packageName\" 2>/dev/null")
                        RootShell.exec("restorecon -FR \"/data/media/$gameUserId/Android/obb/$packageName\" 2>/dev/null")
                    }
                }

                gameDao.deleteGame(packageName)
                syncModuleGamelist()
                diskCatalogManager.removeGameFromCatalog(sdBase, packageName)
                syncDiskCatalog(sdBase)
                mountNotificationManager.syncActiveMountNotification()
                AppLogger.info("Apps", "Removed app: $packageName (restoreToInternal=$restoreToInternal)")
            }
        }
    }

    suspend fun mountGame(
        game: GameEntry,
        sdBase: String = "/data/sdext2",
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            RootShell.rootExecutionMutex.withLock {
                AppLogger.info("Games", "Mounting ${game.displayName} (${game.packageName}) [${game.mode.name}]")
                val result = mountManager.mountGame(game, sdBase, onProgress)
                if (result.isSuccess) {
                    gameDao.updateMountStatus(game.packageName, MountStatus.MOUNTED)
                    AppLogger.success("Games", "Successfully mounted ${game.displayName}")
                } else {
                    val ex = result.exceptionOrNull()
                    val newStatus = if (ex is app.mountx.root.OcclusionHazardException || ex?.message?.contains("MicroSD") == true) {
                        MountStatus.NEED_MIGRATION
                    } else if (ex is app.mountx.root.MultiDiskCollisionException) {
                        MountStatus.UNMOUNTED
                    } else {
                        MountStatus.ERROR
                    }
                    gameDao.updateMountStatus(game.packageName, newStatus)
                    AppLogger.error("Games", "Failed to mount ${game.displayName} [Status: $newStatus]: ${ex?.message}")
                }
                mountNotificationManager.syncActiveMountNotification()
                result
            }
        }

    suspend fun unmountGame(
        game: GameEntry,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            RootShell.rootExecutionMutex.withLock {
                AppLogger.info("Games", "Unmounting ${game.displayName} (${game.packageName})")
                val result = mountManager.unmountGame(game, onProgress)
                if (result.isSuccess) {
                    gameDao.updateMountStatus(game.packageName, MountStatus.UNMOUNTED)
                    AppLogger.success("Games", "Successfully unmounted ${game.displayName}")
                } else {
                    gameDao.updateMountStatus(game.packageName, MountStatus.ERROR)
                    AppLogger.error("Games", "Failed to unmount ${game.displayName}: ${result.exceptionOrNull()?.message}")
                }
                mountNotificationManager.syncActiveMountNotification()
                result
            }
        }

    suspend fun directMountGame(
        game: GameEntry,
        points: List<MountPointConfig>,
        targetBase: String,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        RootShell.rootExecutionMutex.withLock {
            runCatching {
                val pkg = game.packageName
                AppLogger.info("Games", "Direct-mounting ${game.displayName} ($pkg) to $targetBase")

                val currentPoints = game.mountPoints
                val updatedPoints = currentPoints.map { pt ->
                    val matching = points.firstOrNull { it.id == pt.id || it.resolveCategory() == pt.resolveCategory() }
                    if (matching != null) matching.copy(enabled = true) else pt
                }.toMutableList()
                for (newPt in points) {
                    if (updatedPoints.none { it.id == newPt.id || it.resolveCategory() == newPt.resolveCategory() }) {
                        updatedPoints.add(newPt.copy(enabled = true))
                    }
                }

                val updatedGame = game.copy(
                    mountPoints = updatedPoints,
                    mountStatus = MountStatus.MOUNTED,
                    isEnabled = true
                )
                gameDao.updateGame(updatedGame)
                syncModuleGamelist()

                val mountResult = mountManager.mountGame(updatedGame, targetBase, onProgress = onProgress, autoCreateSource = true)
                if (mountResult.isFailure) {
                    gameDao.updateMountStatus(pkg, MountStatus.ERROR)
                    throw mountResult.exceptionOrNull() ?: Exception("Direct mount failed")
                }

                gameDao.updateMountStatus(pkg, MountStatus.MOUNTED)
                calculateDataSize(pkg, targetBase)
                syncDiskCatalog(targetBase)
                mountNotificationManager.syncActiveMountNotification()
                AppLogger.success("Games", "Direct mount successful for ${game.displayName}")
                Unit
            }
        }
    }

    suspend fun unmountMountPoints(
        game: GameEntry,
        points: List<MountPointConfig>,
        sdBase: String = "/data/sdext2",
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        RootShell.rootExecutionMutex.withLock {
            runCatching {
                val pkg = game.packageName
                AppLogger.info("Games", "Unmounting ${points.size} categories for ${game.displayName} ($pkg)")
                val unmountResult = mountManager.unmountMountPoints(game, points, onProgress)
                if (unmountResult.isFailure) {
                    throw unmountResult.exceptionOrNull() ?: Exception("Unmount failed")
                }

                val updatedPoints = game.mountPoints.map { pt ->
                    if (points.any { it.id == pt.id || it.resolveCategory() == pt.resolveCategory() }) {
                        pt.copy(enabled = false)
                    } else {
                        pt
                    }
                }
                val anyEnabled = updatedPoints.any { it.enabled }
                val updatedGame = game.copy(
                    mountPoints = updatedPoints,
                    mountStatus = if (anyEnabled) MountStatus.MOUNTED else MountStatus.UNMOUNTED,
                    isEnabled = anyEnabled
                )
                gameDao.updateGame(updatedGame)
                syncModuleGamelist()

                calculateDataSize(pkg, sdBase)
                syncDiskCatalog(sdBase)
                mountNotificationManager.syncActiveMountNotification()
                AppLogger.success("Games", "Successfully unmounted categories for ${game.displayName}")
                Unit
            }
        }
    }

    suspend fun mountAll(sdBase: String = "/data/sdext2"): Int =
        withContext(Dispatchers.IO) {
            RootShell.rootExecutionMutex.withLock {
                RootShell.exec("rm -f /dev/.mountx_unmounted 2>/dev/null")
                val games = gameDao.getAllGamesSync()
                var count = 0
                AppLogger.info("Games", "Mounting all ${games.size} registered games")
                for (g in games) {
                    if (g.isEnabled) {
                        if (g.mountStatus == MountStatus.NEED_MIGRATION) {
                            AppLogger.info("Games", "Skipping ${g.displayName} in mountAll: data requires migration to MicroSD")
                            continue
                        }

                        val isFullyMounted = if (g.mountPoints.isNotEmpty()) {
                            val enabledPoints = g.mountPoints.filter { it.enabled }
                            enabledPoints.isNotEmpty() && enabledPoints.all { mp ->
                                val rel = MountManager.extractRelativePath(mp.targetPath)
                                RootShell.isMountpoint(mp.targetPath) ||
                                RootShell.isMountpoint("/storage/emulated/0/$rel") ||
                                RootShell.isMountpoint("/mnt/runtime/default/emulated/0/$rel")
                            }
                        } else {
                            val targetData = when (g.mode) {
                                MountMode.FILES -> "/storage/emulated/0/Android/data/${g.packageName}/files"
                                MountMode.PKG -> "/storage/emulated/0/Android/data/${g.packageName}"
                            }
                            val relData = MountManager.extractRelativePath(targetData)
                            RootShell.isMountpoint(targetData) || RootShell.isMountpoint("/mnt/runtime/default/emulated/0/$relData")
                        }

                        if (isFullyMounted) {
                            gameDao.updateMountStatus(g.packageName, MountStatus.MOUNTED)
                            count++
                            continue
                        }
                        val res = mountManager.mountGame(g, sdBase)
                        if (res.isSuccess) {
                            gameDao.updateMountStatus(g.packageName, MountStatus.MOUNTED)
                            count++
                        } else {
                            val ex = res.exceptionOrNull()
                            val newStatus = if (ex is app.mountx.root.OcclusionHazardException || ex?.message?.contains("MicroSD") == true) {
                                MountStatus.NEED_MIGRATION
                            } else if (ex is app.mountx.root.MultiDiskCollisionException) {
                                MountStatus.UNMOUNTED
                            } else {
                                MountStatus.ERROR
                            }
                            gameDao.updateMountStatus(g.packageName, newStatus)
                        }
                    }
                }
                mountNotificationManager.syncActiveMountNotification()
                AppLogger.success("Games", "Mounted $count/${games.size} games")
                count
            }
        }

    suspend fun unmountAll(sdBase: String = "/data/sdext2"): Int =
        withContext(Dispatchers.IO) {
            RootShell.rootExecutionMutex.withLock {
                val games = gameDao.getAllGamesSync()
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
                mountNotificationManager.syncActiveMountNotification()
                AppLogger.success("Games", "Unmounted $count games")
                count
            }
        }

    suspend fun syncModuleGamelist() = withContext(Dispatchers.IO) {
        val targetDirs = listOf("/data/adb/modules/MountX", "/data/adb/modules/mountx", "/data/adb/modules/Mountify", "/data/adb/modules/mountify")
        val games = gameDao.getAllGamesSync()

        // 1. Legacy gamelist.conf
        val gamelistContent = games.filter { it.isEnabled }.joinToString("\n") { g ->
            val modeStr = when (g.mode) {
                MountMode.PKG -> "pkg"
                MountMode.FILES -> "files"
            }
            "${g.packageName}:$modeStr"
        }

        // 2. Modern mountpoints.conf (POSIX pipe-delimited pipeline)
        val mountpointsLines = mutableListOf<String>()
        mountpointsLines.add("# MountX Modern Multi-Target Pipeline (v2.2.35)")
        mountpointsLines.add("# Format: pkg|category|sourcePath|targetPath|preserveMedia|diskUuid|userId")

        for (g in games.filter { it.isEnabled }) {
            var updatedG = g
            var gameModified = false
            val rawPoints = if (g.mountPoints.isNotEmpty()) g.mountPoints else synthesizeLegacyMountPoints(g)
            val updatedPoints = rawPoints.map { mp ->
                var effectiveUuid = mp.diskUuid ?: ""
                if (RootShell.exists(mp.sourcePath)) {
                    val dev = RootShell.execForOutput("df \"${mp.sourcePath}\" 2>/dev/null").lines()
                        .getOrNull(1)?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""
                    if (dev.isNotBlank()) {
                        val resolvedUuid = MountManager.getBlockDeviceUuid(dev)
                        if (!resolvedUuid.isNullOrBlank()) {
                            effectiveUuid = resolvedUuid
                        }
                    }
                }
                if (effectiveUuid.isBlank()) {
                    effectiveUuid = updatedG.preferredDiskUuid ?: ""
                }
                if (effectiveUuid.isNotBlank() && mp.diskUuid != effectiveUuid) {
                    gameModified = true
                    mp.copy(diskUuid = effectiveUuid)
                } else {
                    mp
                }
            }
            if (gameModified) {
                updatedG = updatedG.copy(mountPoints = updatedPoints)
                gameDao.updateGame(updatedG)
            }

            // Deduplicate enabled mount points by targetPath (prevent multi-disk collisions & double bind)
            val distinctEnabledPoints = updatedPoints.filter { it.enabled }
                .sortedWith(
                    compareByDescending<MountPointConfig> { !it.id.startsWith("legacy_") || it.diskUuid != null }
                        .thenByDescending { it.category == MountPointCategory.CUSTOM }
                        .thenByDescending { it.sizeBytes }
                )
                .distinctBy { it.targetPath.trimEnd('/') }

            for (mp in distinctEnabledPoints) {
                val pkg = g.packageName
                val category = mp.category.name
                val sourcePath = mp.sourcePath
                val targetPath = mp.targetPath
                val preserveMedia = if (mp.preserveMedia || mp.category == MountPointCategory.MEDIA_DOWNLOADS) "1" else "0"
                val diskUuid = mp.diskUuid ?: updatedG.preferredDiskUuid ?: ""
                val userId = when {
                    targetPath.contains("/emulated/999/") || targetPath.contains("/user/999/") || targetPath.contains("/media/999/") -> "999"
                    targetPath.contains("/emulated/10/") || targetPath.contains("/user/10/") || targetPath.contains("/media/10/") -> "10"
                    else -> "0"
                }
                mountpointsLines.add("$pkg|$category|$sourcePath|$targetPath|$preserveMedia|$diskUuid|$userId")
            }
        }
        val mountpointsContent = mountpointsLines.joinToString("\n")

        for (dir in targetDirs) {
            if (RootShell.exists(dir)) {
                RootShell.exec("cat << 'EOF' > \"$dir/gamelist.conf\"\n$gamelistContent\nEOF\n")
                RootShell.exec("cat << 'EOF' > \"$dir/mountpoints.conf\"\n$mountpointsContent\nEOF\n")
            }
        }
    }

    suspend fun syncDiskCatalog(sdBase: String = "/data/sdext2") = withContext(Dispatchers.IO) {
        val games = gameDao.getAllGamesSync()
        diskCatalogManager.syncCatalogFromRegisteredGames(sdBase, games)
    }

    suspend fun scanMicroSdGames(sdBase: String, installedApps: Map<String, String>): List<DiscoveredGame> =
        withContext(Dispatchers.IO) {
            val registered = gameDao.getAllGamesSync().map { it.packageName }.toSet()
            diskCatalogManager.scanAllStorageForGames(sdBase, registered, installedApps)
        }

    suspend fun importDiscoveredGame(game: DiscoveredGame, sdBase: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val realSize = if (game.sizeBytes > 0L) game.sizeBytes else calculateDataSize(game.packageName, sdBase)
                val initialMountPoints = if (game.mountPoints.isNotEmpty()) {
                    game.mountPoints
                } else if (!game.originalPath.isNullOrBlank()) {
                    val resolvedDev = RootShell.execForOutput("df \"${game.originalPath}\" 2>/dev/null").lines()
                        .getOrNull(1)?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""
                    val resolvedUuid = if (resolvedDev.isNotBlank()) MountManager.getBlockDeviceUuid(resolvedDev) else null
                    listOf(
                        MountPointConfig(
                            id = "imported_${game.packageName}_data",
                            category = MountPointCategory.EXTERNAL_DATA,
                            sourcePath = game.originalPath,
                            targetPath = "/data/media/0/Android/data/${game.packageName}",
                            enabled = true,
                            diskUuid = resolvedUuid,
                            preserveMedia = true
                        )
                    )
                } else {
                    synthesizeLegacyMountPoints(GameEntry(packageName = game.packageName, displayName = game.displayName, mode = game.mode))
                }
                val preferredUuid = initialMountPoints.firstNotNullOfOrNull { it.diskUuid }
                val entry = GameEntry(
                    packageName = game.packageName,
                    displayName = game.displayName,
                    mode = game.mode,
                    mountStatus = MountStatus.UNMOUNTED,
                    dataSizeBytes = realSize,
                    isEnabled = true,
                    mountPoints = initialMountPoints,
                    preferredDiskUuid = preferredUuid
                )
                gameDao.insertGame(entry)
                // Reconcile dynamic UID/GID and SELinux
                diskCatalogManager.reconcileGame(sdBase, entry)
                syncModuleGamelist()
                syncDiskCatalog(sdBase)
                AppLogger.success("Games", "Imported & reconciled portable game: ${game.displayName} (${game.packageName}), size: $realSize bytes, uuid: $preferredUuid")
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
        val games = gameDao.getAllGamesSync()
        if (games.isEmpty()) return@withContext

        val activeMounts = mountManager.getActiveMountpointsSet()
        val blkidOut = RootShell.execForOutput("blkid 2>/dev/null")

        // Verify if backing block device for /data/sdext2 is physically present in kernel
        val sdBaseMountLine = RootShell.execForOutput("grep ' /data/sdext2 ' /proc/mounts | head -n 1").trim()
        val sdBlockDev = if (sdBaseMountLine.isNotBlank()) {
            sdBaseMountLine.split(Regex("\\s+")).firstOrNull() ?: ""
        } else ""

        val pathsToProbe = mutableSetOf<String>()
        if (sdBlockDev.startsWith("/dev/block/")) {
            pathsToProbe.add(sdBlockDev)
            pathsToProbe.add("/sys/class/block/${sdBlockDev.substringAfterLast('/')}")
        } else {
            pathsToProbe.add("/sys/block/mmcblk0")
            pathsToProbe.add("/dev/block/mmcblk0")
        }
        pathsToProbe.add("/data/sdext2/MountX")

        for (g in games) {
            val pkg = g.packageName
            pathsToProbe.add("/data/sdext2/MountX/Android/data/$pkg")
            pathsToProbe.add("/data/sdext2/MountX/Android/obb/$pkg")
            pathsToProbe.add("/data/media/0/Android/data/$pkg")
            pathsToProbe.add("/data/media/0/Android/obb/$pkg")
            g.mountPoints.forEach { mp ->
                if (mp.sourcePath.isNotBlank()) pathsToProbe.add(mp.sourcePath)
            }
        }

        // Single high-performance batch shell execution for all paths
        val existingPaths = RootShell.pathsExistBatch(pathsToProbe)

        val isSdBlockAlive = if (sdBlockDev.startsWith("/dev/block/")) {
            existingPaths.contains(sdBlockDev) || existingPaths.contains("/sys/class/block/${sdBlockDev.substringAfterLast('/')}")
        } else {
            existingPaths.contains("/sys/block/mmcblk0") || existingPaths.contains("/dev/block/mmcblk0")
        }

        val isSdBaseMounted = (activeMounts.contains("/data/sdext2") || existingPaths.contains("/data/sdext2/MountX")) && isSdBlockAlive

        for (g in games) {
            val targetData = when (g.mode) {
                MountMode.FILES -> "/storage/emulated/0/Android/data/${g.packageName}/files"
                MountMode.PKG -> "/storage/emulated/0/Android/data/${g.packageName}"
            }
            val targetObb = "/storage/emulated/0/Android/obb/${g.packageName}"
            val relData = MountManager.extractRelativePath(targetData)
            val relObb = "Android/obb/${g.packageName}"
            val cleanTargetData = targetData.trimEnd('/')
            val cleanRelData = "/mnt/runtime/default/emulated/0/$relData".trimEnd('/')
            val cleanTargetObb = targetObb.trimEnd('/')
            val cleanRelObb = "/mnt/runtime/default/emulated/0/$relObb".trimEnd('/')

            // In-memory instant check (< 0.001ms) using active mounts set
            val isDataMounted = activeMounts.contains(cleanTargetData) || activeMounts.contains(cleanRelData)
            val isObbMounted = activeMounts.contains(cleanTargetObb) || activeMounts.contains(cleanRelObb)
            val areCustomMounted = g.mountPoints.isNotEmpty() && g.mountPoints.any { mp ->
                mp.enabled && (
                    activeMounts.contains(mp.targetPath.trimEnd('/')) ||
                    activeMounts.contains("/storage/emulated/0/${MountManager.extractRelativePath(mp.targetPath)}".trimEnd('/')) ||
                    activeMounts.contains("/mnt/runtime/default/emulated/0/${MountManager.extractRelativePath(mp.targetPath)}".trimEnd('/'))
                )
            }
            val isMounted = isDataMounted || isObbMounted || areCustomMounted

            // Multi-disk storage check: verify if the specific backing storage is attached
            val isStorageAttached = if (g.mountPoints.isNotEmpty()) {
                g.mountPoints.any { mp ->
                    (mp.diskUuid != null && blkidOut.contains(mp.diskUuid, ignoreCase = true)) ||
                    (existingPaths.contains(mp.sourcePath) && isSdBlockAlive)
                } || isSdBaseMounted
            } else {
                isSdBaseMounted
            }

            if (!isStorageAttached && isMounted) {
                // Ghost mounts detected with missing backing disk! Clean up zombie mountpoints to prevent app crashes.
                RootShell.exec("umount -f -l \"$targetData\" 2>/dev/null")
                RootShell.exec("umount -f -l \"$targetObb\" 2>/dev/null")
                RootShell.exec("umount -f -l \"/mnt/runtime/default/emulated/0/$relData\" 2>/dev/null")
                RootShell.exec("umount -f -l \"/mnt/runtime/default/emulated/0/$relObb\" 2>/dev/null")
            }

            val newStatus = when {
                !isStorageAttached -> MountStatus.DISK_DETACHED
                isMounted -> MountStatus.MOUNTED
                else -> {
                    val extData = "/data/sdext2/MountX/Android/data/${g.packageName}"
                    val extObb = "/data/sdext2/MountX/Android/obb/${g.packageName}"
                    val hasCustomSd = g.mountPoints.any { existingPaths.contains(it.sourcePath) }
                    val hasSd = hasCustomSd || existingPaths.contains(extData) || existingPaths.contains(extObb)
                    if (hasSd) {
                        MountStatus.UNMOUNTED
                    } else if (g.mountStatus == MountStatus.NEED_MIGRATION) {
                        MountStatus.NEED_MIGRATION
                    } else {
                        val internalData = "/data/media/0/Android/data/${g.packageName}"
                        val internalObb = "/data/media/0/Android/obb/${g.packageName}"
                        val hasInternal = existingPaths.contains(internalData) || existingPaths.contains(internalObb)
                        if (hasInternal) {
                            MountStatus.NEED_MIGRATION
                        } else if (g.mountStatus == MountStatus.ERROR) {
                            MountStatus.ERROR
                        } else {
                            MountStatus.UNMOUNTED
                        }
                    }
                }
            }
            if (newStatus != g.mountStatus) {
                gameDao.updateMountStatus(g.packageName, newStatus)
            }
        }
        mountNotificationManager.syncActiveMountNotification()
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

    /**
     * Extracts canonical root storage base from an arbitrary directory path.
     * Maps e.g. /data/sdext2/MountX/Android/... -> /data/sdext2
     * /mnt/media_rw/UUID/Android/... -> /mnt/media_rw/UUID
     */
    private fun extractBasePartition(path: String): String {
        val clean = path.trim().trimEnd('/')
        return when {
            clean.startsWith("/data/sdext") -> {
                val parts = clean.split('/')
                if (parts.size >= 3) "/${parts[1]}/${parts[2]}" else clean
            }
            clean.startsWith("/mnt/media_rw/") -> {
                val parts = clean.split('/')
                if (parts.size >= 4) "/${parts[1]}/${parts[2]}/${parts[3]}" else clean
            }
            clean.startsWith("/storage/") -> {
                val parts = clean.split('/')
                if (parts.size >= 3) "/${parts[1]}/${parts[2]}" else clean
            }
            clean.contains("/MountX/Android/") -> clean.substringBefore("/MountX/Android/")
            clean.contains("/Android/") -> clean.substringBefore("/Android/")
            else -> clean
        }
    }

    /**
     * Resolves substantive bytes across multiple candidate paths on external storage disks.
     * Groups paths by base storage partition (e.g. /data/sdext2 vs /mnt/media_rw/UUID)
     * and takes the maximum substantive size on each partition to prevent double-counting
     * MountX layout (/MountX/Android/...) and legacy layout (/Android/...) on the same disk.
     */
    private fun resolveExternalPartitionBytes(paths: List<String>, sizeMap: Map<String, Long>): Long {
        val pathsByBase = paths.filter { it.isNotBlank() }.groupBy { extractBasePartition(it) }
        val baseSizes = pathsByBase.mapValues { (_, basePaths) ->
            basePaths.maxOfOrNull { sizeMap[it] ?: 0L } ?: 0L
        }
        val hasSubstantive = baseSizes.values.any { it > 512 * 1024L }
        return if (hasSubstantive) {
            baseSizes.values.filter { it > 128 * 1024L }.sum()
        } else {
            baseSizes.values.sum()
        }
    }

    suspend fun calculateDataSize(packageName: String, sdBase: String = "/data/sdext2"): Long =
        withContext(Dispatchers.IO) {
            val game = gameDao.getGameByPackage(packageName)
            val internalData = "/data/media/0/Android/data/$packageName"
            val internalObb = "/data/media/0/Android/obb/$packageName"
            val activeMounts = mountManager.getActiveMountpointsSet()
            val isDataMountedReal = activeMounts.contains(internalData.trimEnd('/'))
            val isObbMountedReal = activeMounts.contains(internalObb.trimEnd('/'))

            val externalBases = getExternalStorageBases(sdBase, game)

            val candidateExtDataPaths = externalBases.flatMap { listOf("$it/MountX/Android/data/$packageName", "$it/Android/data/$packageName") }.toMutableList()
            val candidateExtObbPaths = externalBases.flatMap { listOf("$it/MountX/Android/obb/$packageName", "$it/Android/obb/$packageName") }.toMutableList()
            val candidateExtMediaPaths = externalBases.flatMap { listOf("$it/MountX/Android/media/$packageName", "$it/Android/media/$packageName") }.toMutableList()
            val candidateCustomPaths = mutableListOf<String>()
            game?.mountPoints?.forEach { mp ->
                if (mp.sourcePath.isNotBlank()) {
                    when (mp.resolveCategory()) {
                        MountPointCategory.EXTERNAL_DATA, MountPointCategory.GAME_ASSETS -> candidateExtDataPaths.add(mp.sourcePath)
                        MountPointCategory.OBB_STORAGE -> candidateExtObbPaths.add(mp.sourcePath)
                        MountPointCategory.MEDIA_DOWNLOADS -> candidateExtMediaPaths.add(mp.sourcePath)
                        MountPointCategory.CUSTOM -> candidateCustomPaths.add(mp.sourcePath)
                        else -> {}
                    }
                }
            }

            val distinctExtData = candidateExtDataPaths.distinct()
            val distinctExtObb = candidateExtObbPaths.distinct()
            val distinctExtMedia = candidateExtMediaPaths.distinct()
            val distinctCustom = candidateCustomPaths.distinct().filter { p ->
                !distinctExtData.contains(p) && !distinctExtObb.contains(p) && !distinctExtMedia.contains(p)
            }

            val pathsToScan = mutableListOf<String>()
            pathsToScan.add(internalData)
            pathsToScan.add(internalObb)
            pathsToScan.addAll(distinctExtData)
            pathsToScan.addAll(distinctExtObb)
            pathsToScan.addAll(distinctExtMedia)
            pathsToScan.addAll(distinctCustom)

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

            val extDataBytesSum = resolveExternalPartitionBytes(distinctExtData, sizeMap)
            val extObbBytesSum = resolveExternalPartitionBytes(distinctExtObb, sizeMap)
            val extMediaBytesSum = resolveExternalPartitionBytes(distinctExtMedia, sizeMap)
            val customBytesSum = distinctCustom.sumOf { sizeMap[it] ?: 0L }

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

            // Auto-detect NEED_MIGRATION status for unmounted game with internal data and empty SD
            if (game != null && !isDataMountedReal && !isObbMountedReal) {
                val hasInternal = internalDataSize > 512 * 1024L || internalObbSize > 512 * 1024L
                val sdEmpty = extDataBytesSum <= 128 * 1024L && extObbBytesSum <= 128 * 1024L
                if (hasInternal && sdEmpty) {
                    if (game.mountStatus != MountStatus.NEED_MIGRATION) {
                        gameDao.updateMountStatus(packageName, MountStatus.NEED_MIGRATION)
                        AppLogger.info("GameRepo", "Flagged $packageName as NEED_MIGRATION (internal=${internalDataSize/1024}KB, sd=${extDataBytesSum/1024}KB)")
                    }
                } else if (!sdEmpty && game.mountStatus == MountStatus.NEED_MIGRATION) {
                    gameDao.updateMountStatus(packageName, MountStatus.UNMOUNTED)
                }
            }

            totalSize
        }

    /**
     * High-performance batch data size calculation for all registered games.
     * Batches all candidate internal and external paths across all games into a single du -sk invocation,
     * reducing N*M root shell commands down to a single pass.
     */
    suspend fun calculateDataSizesBatch(games: List<GameEntry> = emptyList(), sdBase: String = "/data/sdext2") =
        withContext(Dispatchers.IO) {
            val targetGames = if (games.isNotEmpty()) games else gameDao.getAllGamesSync()
            if (targetGames.isEmpty()) return@withContext
            val activeMounts = mountManager.getActiveMountpointsSet()

            data class GameBundle(
                val internalData: String,
                val internalObb: String,
                val extDataPaths: List<String>,
                val extObbPaths: List<String>,
                val extMediaPaths: List<String>,
                val customPaths: List<String>
            )
            val bundles = mutableMapOf<String, GameBundle>()
            val allTargets = mutableSetOf<String>()

            for (game in targetGames) {
                val pkg = game.packageName
                val internalData = "/data/media/0/Android/data/$pkg"
                val internalObb = "/data/media/0/Android/obb/$pkg"
                val externalBases = getExternalStorageBases(sdBase, game)
                val extData = externalBases.flatMap { listOf("$it/MountX/Android/data/$pkg", "$it/Android/data/$pkg") }.toMutableList()
                val extObb = externalBases.flatMap { listOf("$it/MountX/Android/obb/$pkg", "$it/Android/obb/$pkg") }.toMutableList()
                val extMedia = externalBases.flatMap { listOf("$it/MountX/Android/media/$pkg", "$it/Android/media/$pkg") }.toMutableList()
                val custom = mutableListOf<String>()

                game.mountPoints.forEach { mp ->
                    if (mp.sourcePath.isNotBlank()) {
                        when (mp.resolveCategory()) {
                            MountPointCategory.EXTERNAL_DATA, MountPointCategory.GAME_ASSETS -> extData.add(mp.sourcePath)
                            MountPointCategory.OBB_STORAGE -> extObb.add(mp.sourcePath)
                            MountPointCategory.MEDIA_DOWNLOADS -> extMedia.add(mp.sourcePath)
                            MountPointCategory.CUSTOM -> custom.add(mp.sourcePath)
                            else -> {}
                        }
                    }
                }

                val distinctExtData = extData.distinct()
                val distinctExtObb = extObb.distinct()
                val distinctExtMedia = extMedia.distinct()
                val distinctCustom = custom.distinct().filter { p ->
                    !distinctExtData.contains(p) && !distinctExtObb.contains(p) && !distinctExtMedia.contains(p)
                }

                bundles[pkg] = GameBundle(internalData, internalObb, distinctExtData, distinctExtObb, distinctExtMedia, distinctCustom)

                allTargets.add(internalData)
                allTargets.add(internalObb)
                allTargets.addAll(distinctExtData)
                allTargets.addAll(distinctExtObb)
                allTargets.addAll(distinctExtMedia)
                allTargets.addAll(distinctCustom)
            }

            val validTargets = allTargets
                .map { it.trim().trim('\"') }
                .filter { it.isNotBlank() && it != "/" && it != "." }
                .distinct()

            val sizeMap = mutableMapOf<String, Long>()
            validTargets.chunked(40).forEach { chunk ->
                val targets = chunk.joinToString(" ") { "\"$it\"" }
                val res = RootShell.exec("du -sk $targets 2>/dev/null")
                for (line in res.stdout) {
                    val parts = line.trim().split(Regex("\\s+"), limit = 2)
                    if (parts.size >= 2) {
                        val kb = parts[0].toLongOrNull() ?: continue
                        sizeMap[parts[1]] = kb * 1024L
                    }
                }
            }

            for (game in targetGames) {
                val pkg = game.packageName
                val bundle = bundles[pkg] ?: continue
                val isDataMountedReal = activeMounts.contains(bundle.internalData.trimEnd('/'))
                val isObbMountedReal = activeMounts.contains(bundle.internalObb.trimEnd('/'))

                val internalDataSize = sizeMap[bundle.internalData] ?: 0L
                val internalObbSize = sizeMap[bundle.internalObb] ?: 0L

                val extDataBytesSum = resolveExternalPartitionBytes(bundle.extDataPaths, sizeMap)
                val extObbBytesSum = resolveExternalPartitionBytes(bundle.extObbPaths, sizeMap)
                val extMediaBytesSum = resolveExternalPartitionBytes(bundle.extMediaPaths, sizeMap)
                val customBytesSum = bundle.customPaths.sumOf { sizeMap[it] ?: 0L }

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
                val previousKnownSize = game.dataSizeBytes
                if (totalSize <= 128 * 1024L && previousKnownSize > 512 * 1024L) {
                    totalSize = previousKnownSize
                }
                if (totalSize > 0L && totalSize != game.dataSizeBytes) {
                    gameDao.updateDataSize(pkg, totalSize)
                }
            }
        }

    suspend fun getInternalAndSdSizes(packageName: String, sdBase: String = "/data/sdext2"): Pair<Long, Long> =
        withContext(Dispatchers.IO) {
            val game = gameDao.getGameByPackage(packageName)
            val internalData = "/data/media/0/Android/data/$packageName"
            val internalObb = "/data/media/0/Android/obb/$packageName"

            val externalBases = getExternalStorageBases(sdBase, game)
            val candidateExtDataPaths = externalBases.flatMap { listOf("$it/MountX/Android/data/$packageName", "$it/Android/data/$packageName") }.toMutableList()
            val candidateExtObbPaths = externalBases.flatMap { listOf("$it/MountX/Android/obb/$packageName", "$it/Android/obb/$packageName") }.toMutableList()
            game?.mountPoints?.forEach { mp ->
                if (mp.sourcePath.isNotBlank()) {
                    when (mp.resolveCategory()) {
                        MountPointCategory.EXTERNAL_DATA, MountPointCategory.GAME_ASSETS -> candidateExtDataPaths.add(mp.sourcePath)
                        MountPointCategory.OBB_STORAGE -> candidateExtObbPaths.add(mp.sourcePath)
                        else -> {}
                    }
                }
            }

            val distinctExtData = candidateExtDataPaths.distinct()
            val distinctExtObb = candidateExtObbPaths.distinct()

            val pathsToScan = mutableListOf(internalData, internalObb)
            pathsToScan.addAll(distinctExtData)
            pathsToScan.addAll(distinctExtObb)

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
            val extDataSum = resolveExternalPartitionBytes(distinctExtData, sizeMap)
            val extObbSum = resolveExternalPartitionBytes(distinctExtObb, sizeMap)
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

        val activeMounts = mountManager.getActiveMountpointsSet()
        val isDataMountedReal = activeMounts.contains(ext1Data.trimEnd('/'))
        val isObbMountedReal = activeMounts.contains(ext1Obb.trimEnd('/'))
        val isMediaMountedReal = activeMounts.contains(ext1Media.trimEnd('/'))

        // Build candidate paths for all known external storages (both MountX and legacy)
        val candidateExtDataPaths = externalBases.flatMap { listOf("$it/MountX/Android/data/$packageName", "$it/Android/data/$packageName") }.toMutableList()
        val candidateExtObbPaths = externalBases.flatMap { listOf("$it/MountX/Android/obb/$packageName", "$it/Android/obb/$packageName") }.toMutableList()
        val candidateExtMediaPaths = externalBases.flatMap { listOf("$it/MountX/Android/media/$packageName", "$it/Android/media/$packageName") }.toMutableList()
        val candidateCustomPaths = mutableListOf<String>()
        game?.mountPoints?.forEach { mp ->
            if (mp.sourcePath.isNotBlank()) {
                when (mp.resolveCategory()) {
                    MountPointCategory.EXTERNAL_DATA, MountPointCategory.GAME_ASSETS -> candidateExtDataPaths.add(mp.sourcePath)
                    MountPointCategory.OBB_STORAGE -> candidateExtObbPaths.add(mp.sourcePath)
                    MountPointCategory.MEDIA_DOWNLOADS -> candidateExtMediaPaths.add(mp.sourcePath)
                    MountPointCategory.CUSTOM -> candidateCustomPaths.add(mp.sourcePath)
                    else -> {}
                }
            }
        }

        val distinctExtData = candidateExtDataPaths.distinct()
        val distinctExtObb = candidateExtObbPaths.distinct()
        val distinctExtMedia = candidateExtMediaPaths.distinct()
        val distinctCustom = candidateCustomPaths.distinct().filter { p ->
            !distinctExtData.contains(p) && !distinctExtObb.contains(p) && !distinctExtMedia.contains(p)
        }

        // Always scan ALL paths (internal + external) to get accurate sizes regardless of mountStatus.
        // When data is bind-mounted from SD to internal path, du on internal path returns SD size — correct.
        // When not mounted, internal path shows actual internal size — also correct.
        val targetsList = mutableListOf(oatDir, dataDir, cacheDir, codeCacheDir, ext1Data, ext1Obb, ext1Media)
        targetsList.addAll(distinctExtData)
        targetsList.addAll(distinctExtObb)
        targetsList.addAll(distinctExtMedia)
        targetsList.addAll(distinctCustom)

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

        ext2DataBytes = resolveExternalPartitionBytes(distinctExtData, sizeMap)
        ext2ObbBytes = resolveExternalPartitionBytes(distinctExtObb, sizeMap)
        ext2MediaBytes = resolveExternalPartitionBytes(distinctExtMedia, sizeMap)
        val customBytesSum = distinctCustom.sumOf { sizeMap[it] ?: 0L }

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

    private var cachedInstalledApps: List<InstalledAppInfo>? = null

    fun invalidateInstalledAppsCache() {
        cachedInstalledApps = null
    }

    suspend fun getInstalledApps(context: Context, force: Boolean = false): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        if (!force && cachedInstalledApps != null) {
            return@withContext cachedInstalledApps!!
        }
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

        val mapped = apps
            .map { app ->
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val preset = SmartGamePresets.findPreset(app.packageName)
                val isGame = (app.category == ApplicationInfo.CATEGORY_GAME) || (preset != null)
                val label = if (isGame || !isSystem) {
                    pm.getApplicationLabel(app).toString()
                } else {
                    // For background system apps, avoid binder IPC bottleneck
                    app.packageName
                }
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
        cachedInstalledApps = mapped
        mapped
    }

    suspend fun scanCandidateDirectories(
        packageName: String,
        displayName: String,
        sdBase: String = "/data/sdext2"
    ): List<CandidateDirectory> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CandidateDirectory>()

        val internalFiles = "/data/media/0/Android/data/$packageName/files"
        val sdFiles = "$sdBase/Android/data/$packageName/files"
        val internalObb = "/data/media/0/Android/obb/$packageName"
        val sdObb = "$sdBase/Android/obb/$packageName"
        val internalMedia = "/data/media/0/Android/media/$packageName"
        val sdMedia = "$sdBase/Android/media/$packageName"
        val pubFolder = "/data/media/0/${displayName.replace(" ", "")}"
        val internalCache = "/data/media/0/Android/data/$packageName/cache"
        val sdCache = "$sdBase/Android/data/$packageName/cache"
        val privateDataDir = "/data/user/0/$packageName"

        // Resolve APK dir via pm path
        val apkPathRes = RootShell.exec("pm path \"$packageName\" 2>/dev/null | head -n 1")
        val apkDir = if (apkPathRes.isSuccess && apkPathRes.output.contains("package:")) {
            val fullApkPath = apkPathRes.output.substringAfter("package:").trim()
            java.io.File(fullApkPath).parent ?: ""
        } else ""

        // Fast batch du -sk calculation for all candidates in 1 single shell execution
        val allPaths = listOf(
            internalFiles, sdFiles, internalObb, sdObb,
            internalMedia, sdMedia, pubFolder, internalCache,
            sdCache, privateDataDir, apkDir
        ).filter { it.isNotBlank() }

        val sizeMap = mutableMapOf<String, Long>()
        val targets = allPaths.joinToString(" ") { "\"$it\"" }
        val duRes = RootShell.exec("du -sk $targets 2>/dev/null")
        for (line in duRes.stdout) {
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size >= 2) {
                val kb = parts[0].toLongOrNull() ?: continue
                sizeMap[parts[1]] = kb * 1024L
            }
        }

        // 1. EXTERNAL_DATA: Android/data/<pkg>/files
        val internalFilesSize = sizeMap[internalFiles] ?: 0L
        val sdFilesSize = sizeMap[sdFiles] ?: 0L
        val filesSize = if (sdFilesSize > 16384L && sdFilesSize > internalFilesSize) sdFilesSize else internalFilesSize
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
                childItems = emptyList()
            )
        )

        // 2. OBB_STORAGE: Android/obb/<pkg>
        val internalObbSize = sizeMap[internalObb] ?: 0L
        val sdObbSize = sizeMap[sdObb] ?: 0L
        val obbSize = if (sdObbSize > 16384L && sdObbSize > internalObbSize) sdObbSize else internalObbSize
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
                childItems = emptyList()
            )
        )

        // 3. MEDIA_DOWNLOADS: Android/media or public app folder
        val pubSize = sizeMap[pubFolder] ?: 0L
        val mediaSize = (sizeMap[sdMedia] ?: sizeMap[internalMedia] ?: 0L) + pubSize
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
                childItems = emptyList()
            )
        )

        // 4. CACHE_SHADERS: Android/data/cache
        val cacheSize = sizeMap[sdCache] ?: sizeMap[internalCache] ?: 0L
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
                childItems = emptyList()
            )
        )

        // 5. PRIVATE_INTERNAL: /data/data/<pkg>
        val privateSize = sizeMap[privateDataDir] ?: 0L
        val oneGb = 1024L * 1024L * 1024L
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
                    childItems = emptyList()
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
                    childItems = emptyList()
                )
            )
        }

        // 6. ADVANCED EXPERIMENTAL: App Package (APK & Libs)
        val apkSize = sizeMap[apkDir] ?: 0L
        if (apkDir.isNotBlank() && apkSize > 0L) {
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
                    childItems = emptyList()
                )
            )
        }

        list
    }

    private suspend fun scanSubItems(parentDir: String, sdParentDir: String): List<CandidateSubItem> {
        if (!RootShell.exists(parentDir)) return emptyList()
        val items = mutableListOf<CandidateSubItem>()
        // Batch calculate all children in 1 single du -sk invocation instead of sequential N root shell calls
        val duRes = RootShell.exec("du -sk \"$parentDir\"/* 2>/dev/null")
        if (duRes.isSuccess && duRes.stdout.isNotEmpty()) {
            for (line in duRes.stdout) {
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                if (parts.size >= 2) {
                    val kb = parts[0].toLongOrNull() ?: continue
                    val childPath = parts[1].trim()
                    val name = childPath.substringAfterLast('/')
                    if (name.isBlank() || name == "." || name == "..") continue
                    val childSd = "$sdParentDir/$name"
                    items.add(
                        CandidateSubItem(
                            id = name,
                            name = name,
                            internalPath = childPath,
                            sdPath = childSd,
                            sizeBytes = kb * 1024L,
                            enabled = true
                        )
                    )
                }
            }
        }
        // Fallback if wildcard had no subfolders or didn't expand
        if (items.isEmpty()) {
            val res = RootShell.exec("ls -1 \"$parentDir\" 2>/dev/null")
            if (res.isSuccess && res.stdout.isNotEmpty()) {
                for (name in res.stdout.map { it.trim() }.filter { it.isNotBlank() }) {
                    val childPath = "$parentDir/$name"
                    val childSd = "$sdParentDir/$name"
                    items.add(
                        CandidateSubItem(
                            id = name,
                            name = name,
                            internalPath = childPath,
                            sdPath = childSd,
                            sizeBytes = 0L,
                            enabled = true
                        )
                    )
                }
            }
        }
        return items
    }

    private suspend fun getDirSizeBytes(path: String): Long {
        val res = RootShell.exec("du -sk \"$path\" 2>/dev/null")
        val kb = res.stdout.firstOrNull()?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull() ?: 0L
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

typealias GameRepository = AppRepository

