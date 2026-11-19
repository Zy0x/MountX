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
import dagger.hilt.android.qualifiers.ApplicationContext
import app.mountx.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
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
        app.mountx.ui.components.AppIconManager.removeCachedIcon(app.mountx.MountXApp.instance, packageName)
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
                        val pathsToClean = mutableListOf<String>()
                        for (point in game.mountPoints) {
                            val ptUserId = app.mountx.root.MountManager.resolveUserId(point.targetPath)
                            val rel = app.mountx.root.MountManager.extractRelativePath(point.targetPath)
                            for (ns in listOf("/mnt/runtime/default", "/mnt/runtime/read", "/mnt/runtime/write", "/mnt/runtime/full", "/mnt/user/$ptUserId", "/mnt/installer/$ptUserId", "/mnt/androidwritable/$ptUserId", "/mnt/pass_through/$ptUserId", "/storage")) {
                                pathsToClean.add("$ns/emulated/$ptUserId/$rel")
                                pathsToClean.add("$ns/$rel")
                            }
                            pathsToClean.add("/data/media/$ptUserId/$rel")
                            pathsToClean.add(point.targetPath)
                        }
                        app.mountx.root.MountManager.unmountSafeAcrossNamespaces(pathsToClean)
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
                app.mountx.ui.components.AppIconManager.removeCachedIcon(context, packageName)
                AppLogger.info("Apps", "Removed app: $packageName (restoreToInternal=$restoreToInternal)")
            }
        }
    }

    suspend fun purgeGamePermanently(
        context: Context,
        packageName: String,
        sdBase: String = "/data/sdext2",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        RootShell.rootExecutionMutex.withLock {
            runCatching {
                onProgress(0.1f, context.getString(app.mountx.R.string.dialog_delete_stopping_process))
                RootShell.exec("am force-stop \"$packageName\"")

                val game = gameDao.getGameByPackage(packageName)
                if (game != null) {
                    if (game.mountStatus == MountStatus.MOUNTED) {
                        onProgress(0.3f, context.getString(app.mountx.R.string.dialog_delete_unmounting_title))
                        mountManager.unmountGame(game)
                    }

                    onProgress(0.5f, context.getString(app.mountx.R.string.dialog_delete_purging_data))
                    val purgeUnmountPaths = mutableListOf<String>()
                    for (point in game.mountPoints) {
                        val ptUserId = app.mountx.root.MountManager.resolveUserId(point.targetPath)
                        val rel = app.mountx.root.MountManager.extractRelativePath(point.targetPath)
                        for (ns in listOf("/mnt/runtime/default", "/mnt/runtime/read", "/mnt/runtime/write", "/mnt/runtime/full", "/mnt/user/$ptUserId", "/mnt/installer/$ptUserId", "/mnt/androidwritable/$ptUserId", "/mnt/pass_through/$ptUserId", "/storage")) {
                            purgeUnmountPaths.add("$ns/emulated/$ptUserId/$rel")
                            purgeUnmountPaths.add("$ns/$rel")
                        }
                        purgeUnmountPaths.add("/data/media/$ptUserId/$rel")
                        purgeUnmountPaths.add(point.targetPath)
                    }
                    app.mountx.root.MountManager.unmountSafeAcrossNamespaces(purgeUnmountPaths)
                    for (point in game.mountPoints) {
                        // Delete physical source data from MicroSD
                        if (point.sourcePath.isNotBlank() && point.sourcePath != "/" && point.sourcePath != sdBase) {
                            RootShell.exec("rm -rf \"${point.sourcePath}\" 2>/dev/null")
                        }
                    }

                    // Also remove standard MountX directories for this package on MicroSD
                    RootShell.exec("rm -rf \"$sdBase/MountX/Android/data/$packageName\" 2>/dev/null")
                    RootShell.exec("rm -rf \"$sdBase/MountX/Android/obb/$packageName\" 2>/dev/null")
                    RootShell.exec("rm -rf \"$sdBase/MountX/Android/media/$packageName\" 2>/dev/null")
                    RootShell.exec("rm -rf \"$sdBase/MountX/data/$packageName\" 2>/dev/null")
                    RootShell.exec("rm -rf \"$sdBase/MountX/cache/$packageName\" 2>/dev/null")
                    RootShell.exec("rm -rf \"$sdBase/MountX/app/$packageName\" 2>/dev/null")
                    RootShell.exec("rm -rf \"$sdBase/MountX/lib/$packageName\" 2>/dev/null")
                    RootShell.exec("rm -f \"$sdBase/.mountx/containers/${packageName}_data.img\" \"$sdBase/MountX/containers/${packageName}_data.img\" 2>/dev/null")
                    RootShell.exec("rm -f \"$sdBase/.mountx/containers/${packageName}_cache.img\" \"$sdBase/.mountx/containers/${packageName}_apk.img\" \"$sdBase/.mountx/containers/${packageName}_lib.img\" 2>/dev/null")
                    RootShell.exec("rm -rf \"$sdBase/Android/data/$packageName\" 2>/dev/null")
                    RootShell.exec("rm -rf \"$sdBase/Android/obb/$packageName\" 2>/dev/null")
                    RootShell.exec("rm -rf \"$sdBase/Android/media/$packageName\" 2>/dev/null")

                    val gameUserId = game.mountPoints.firstOrNull()?.let { app.mountx.root.MountManager.resolveUserId(it.targetPath) } ?: 0
                    // Recreate clean valid empty directories so app won't crash if opened
                    RootShell.exec("mkdir -p \"/data/media/$gameUserId/Android/data/$packageName\" 2>/dev/null")
                    RootShell.exec("mkdir -p \"/data/media/$gameUserId/Android/obb/$packageName\" 2>/dev/null")
                    RootShell.exec("chown -R 1023:1023 \"/data/media/$gameUserId/Android/data/$packageName\" 2>/dev/null")
                    RootShell.exec("chown -R 1023:1023 \"/data/media/$gameUserId/Android/obb/$packageName\" 2>/dev/null")
                    RootShell.exec("restorecon -FR \"/data/media/$gameUserId/Android/data/$packageName\" 2>/dev/null")
                    RootShell.exec("restorecon -FR \"/data/media/$gameUserId/Android/obb/$packageName\" 2>/dev/null")
                }

                onProgress(0.9f, context.getString(app.mountx.R.string.dialog_delete_cleaning_db))
                gameDao.deleteGame(packageName)
                syncModuleGamelist()
                diskCatalogManager.removeGameFromCatalog(sdBase, packageName)
                syncDiskCatalog(sdBase)
                mountNotificationManager.syncActiveMountNotification()
                app.mountx.ui.components.AppIconManager.removeCachedIcon(context, packageName)
                onProgress(1.0f, "")
                AppLogger.success("Apps", "Permanently purged app and MicroSD data: $packageName")
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
                // Never auto-enable all disabled mount points here: that would create empty folders
                // on SD and bind-mount them over intact internal APK/Lib/Private/Data.
                val effectiveGame = game
                AppLogger.info("Games", "Mounting ${effectiveGame.displayName} (${effectiveGame.packageName}) [${effectiveGame.mode.name}]")
                val result = mountManager.mountGame(effectiveGame, sdBase, onProgress, autoCreateSource = false)
                if (result.isSuccess) {
                    gameDao.updateMountStatus(effectiveGame.packageName, MountStatus.MOUNTED)
                    gameDao.updateEnabled(effectiveGame.packageName, true)
                    syncModuleGamelist()
                    AppLogger.success("Games", "Successfully mounted ${effectiveGame.displayName}")
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
                    gameDao.updateEnabled(game.packageName, false)
                    syncModuleGamelist()
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
                mountManager.forceStopApp(pkg)
                AppLogger.info("Games", "Direct-mounting ${game.displayName} ($pkg) to $targetBase")

                val currentPoints = game.mountPoints
                val updatedPoints = currentPoints.map { pt ->
                    val matching = points.firstOrNull { it.isSameTargetAs(pt) }
                    if (matching != null) matching.copy(enabled = true) else pt
                }.toMutableList()
                for (newPt in points) {
                    if (updatedPoints.none { it.isSameTargetAs(newPt) }) {
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
                mountManager.forceStopApp(pkg)
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

    private fun extractEmulatedRelativePath(targetPath: String): String {
        val clean = targetPath.trimEnd('/')
        val isExternalPrefix = clean.startsWith("/storage/emulated/") ||
            clean.startsWith("/data/media/") ||
            clean.startsWith("/mnt/user/") ||
            clean.startsWith("/sdcard/")
        return if (isExternalPrefix) {
            MountManager.extractRelativePath(clean).trimEnd('/')
        } else {
            ""
        }
    }

    private fun findMatchedActiveMount(
        mp: MountPointConfig,
        packageName: String,
        activeMounts: Set<String>
    ): String? {
        val cleanTarget = mp.targetPath.trimEnd('/')
        val rel = extractEmulatedRelativePath(cleanTarget)
        if (rel.isNotBlank()) {
            val uid = Regex("^/(?:storage/emulated|data/media|mnt/user)/(\\d+)/")
                .find(cleanTarget)?.groupValues?.getOrNull(1) ?: "0"
            val storageTarget = "/storage/emulated/$uid/$rel"
            val mntUserRoot = "/mnt/user/$uid/emulated"
            val mntUserTarget = "/mnt/user/$uid/emulated/$uid/$rel"
            if (activeMounts.contains(mntUserRoot) && !activeMounts.contains(mntUserTarget)) {
                return null
            }
            if (activeMounts.contains("/storage/emulated")) {
                return if (activeMounts.contains(storageTarget)) storageTarget else null
            }
            val candidates = listOf(
                storageTarget,
                "/mnt/runtime/default/emulated/$uid/$rel",
                "/data/media/$uid/$rel"
            )
            candidates.firstOrNull { activeMounts.contains(it) }?.let { return it }
            return null
        }

        if (cleanTarget.isNotBlank() && activeMounts.contains(cleanTarget)) return cleanTarget

        val escapedPkg = Regex.escape(packageName)
        return when (mp.resolveCategory()) {
            MountPointCategory.APP_PACKAGE -> {
                val pkgPattern = Regex("(?:^|/)$escapedPkg(?:-|/|\\z)")
                val wantLib = mp.isLibTarget()
                activeMounts.firstOrNull { m ->
                    m.startsWith("/data/app/") &&
                        pkgPattern.containsMatchIn(m) &&
                        (m.endsWith("/lib") || m.contains("/lib/")) == wantLib
                }
            }
            MountPointCategory.PRIVATE_INTERNAL -> {
                val privRegex = Regex("^/(?:data/data|data/user/\\d+|data_mirror/data_[^/]+/[^/]+/\\d+)/$escapedPkg$")
                activeMounts.firstOrNull { privRegex.matches(it) }
            }
            MountPointCategory.CACHE_SHADERS -> {
                val targetIsExternal = rel.isNotBlank() || cleanTarget.contains("/Android/")
                val cacheRegex = Regex("^/(?:data/data|data/user/\\d+|data_mirror/data_[^/]+/[^/]+/\\d+|data/media/\\d+/Android/data|storage/emulated/\\d+/Android/data)/$escapedPkg/cache$")
                activeMounts.firstOrNull { m ->
                    cacheRegex.matches(m) && (m.contains("/Android/") == targetIsExternal)
                }
            }
            else -> null
        }
    }

    private fun resolveCanaryPaths(
        mp: MountPointConfig,
        matchedTarget: String
    ): Pair<String, String>? {
        val cleanSource = mp.sourcePath.trimEnd('/')
        if (mp.isEffectivelyVirtualContainer() ||
            cleanSource.endsWith(".img", ignoreCase = true) ||
            cleanSource.isBlank() ||
            (mp.resolveCategory() == MountPointCategory.CACHE_SHADERS && !mp.targetPath.contains("/Android/"))
        ) {
            return null
        }
        val rel = extractEmulatedRelativePath(mp.targetPath)
        val checkTarget = if (rel.isNotBlank()) {
            val uid = Regex("^/(?:storage/emulated|data/media|mnt/user)/(\\d+)/")
                .find(mp.targetPath)?.groupValues?.getOrNull(1) ?: "0"
            "/storage/emulated/$uid/$rel"
        } else {
            matchedTarget
        }
        val sourceDir = if (mp.isLibTarget()) {
            val abi = MountManager.extractAbiFromLibPath(matchedTarget)
                ?: MountManager.extractAbiFromLibPath(mp.targetPath)
            if (!abi.isNullOrBlank() && !cleanSource.endsWith("/$abi")) {
                "$cleanSource/$abi"
            } else {
                cleanSource
            }
        } else {
            cleanSource
        }
        return "$sourceDir/.mountx_canary" to "$checkTarget/.mountx_canary"
    }

    private fun collectCanaryProbePaths(
        game: GameEntry,
        activeMounts: Set<String>,
        paths: MutableSet<String>
    ) {
        if (game.mountPoints.isEmpty()) return
        for (mp in game.mountPoints) {
            if (!mp.enabled) continue
            val matched = findMatchedActiveMount(mp, game.packageName, activeMounts) ?: continue
            resolveCanaryPaths(mp, matched)?.let { (srcCanary, dstCanary) ->
                paths.add(srcCanary)
                paths.add(dstCanary)
            }
        }
    }

    private suspend fun areAllEnabledMountPointsActive(
        game: GameEntry,
        activeMounts: Set<String>,
        existingPaths: Set<String>? = null
    ): Boolean {
        if (game.mountPoints.isNotEmpty()) {
            val enabledPoints = game.mountPoints.filter { it.enabled }
            if (enabledPoints.isEmpty()) return false
            for (mp in enabledPoints) {
                val matched = findMatchedActiveMount(mp, game.packageName, activeMounts) ?: return false
                val canaryPair = resolveCanaryPaths(mp, matched) ?: continue
                val (srcCanary, dstCanary) = canaryPair
                val hasSrcCanary = existingPaths?.contains(srcCanary) ?: RootShell.exists(srcCanary)
                if (hasSrcCanary) {
                    val hasDstCanary = existingPaths?.contains(dstCanary) ?: RootShell.exists(dstCanary)
                    if (!hasDstCanary) return false
                }
            }
            return true
        }

        val targetData = when (game.mode) {
            MountMode.FILES -> "/storage/emulated/0/Android/data/${game.packageName}/files"
            MountMode.PKG -> "/storage/emulated/0/Android/data/${game.packageName}"
        }
        val targetObb = "/storage/emulated/0/Android/obb/${game.packageName}"
        val relData = extractEmulatedRelativePath(targetData)
        val relObb = "Android/obb/${game.packageName}"
        val isDataMounted = if (activeMounts.contains("/storage/emulated")) {
            activeMounts.contains(targetData.trimEnd('/'))
        } else {
            activeMounts.contains(targetData.trimEnd('/')) ||
                activeMounts.contains("/data/media/0/$relData") ||
                activeMounts.contains("/mnt/runtime/default/emulated/0/$relData")
        }
        val isObbMounted = if (activeMounts.contains("/storage/emulated")) {
            activeMounts.contains(targetObb.trimEnd('/'))
        } else {
            activeMounts.contains(targetObb.trimEnd('/')) ||
                activeMounts.contains("/data/media/0/$relObb") ||
                activeMounts.contains("/mnt/runtime/default/emulated/0/$relObb")
        }
        return isDataMounted || isObbMounted
    }

    suspend fun mountAll(sdBase: String = "/data/sdext2"): Int =
        withContext(Dispatchers.IO) {
            RootShell.rootExecutionMutex.withLock {
                RootShell.exec("rm -f /dev/.mountx_unmounted 2>/dev/null")
                val games = gameDao.getAllGamesSync()
                val activeMounts = mountManager.getActiveMountpointsSet()
                val verifiedMap = mountManager.verifyCanariesBatch(games)
                val probePaths = mutableSetOf<String>()
                for (g in games) {
                    if (g.isEnabled && g.mountStatus != MountStatus.NEED_MIGRATION) {
                        collectCanaryProbePaths(g, activeMounts, probePaths)
                    }
                }
                val existingPaths = if (probePaths.isNotEmpty()) RootShell.pathsExistBatch(probePaths) else emptySet()
                var count = 0
                AppLogger.info("Games", "Mounting all ${games.size} registered games")
                for (g in games) {
                    if (g.isEnabled) {
                        if (g.mountStatus == MountStatus.NEED_MIGRATION) {
                            AppLogger.info("Games", "Skipping ${g.displayName} in mountAll: data requires migration to MicroSD")
                            continue
                        }

                        val isFullyMounted = (verifiedMap[g.packageName] == true) &&
                            areAllEnabledMountPointsActive(g, activeMounts, existingPaths)

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

    suspend fun isGameRunning(packageName: String): Boolean = withContext(Dispatchers.IO) {
        mountManager.isGameRunning(packageName)
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

            // Deduplicate enabled mount points by targetPath (prevent multi-disk collisions & double bind).
            // Sort by targetPath length ASCENDING so parent directories (e.g. APK package root) are mounted before
            // nested child directories (e.g. lib/arm64 native libraries).
            val effectivePoints = updatedPoints

            val distinctEnabledPoints = effectivePoints.filter { it.enabled }
                .sortedWith(
                    compareBy<MountPointConfig> { it.targetPath.trimEnd('/').length }
                        .thenByDescending { !it.id.startsWith("legacy_") || it.diskUuid != null }
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
                mountManager.forceStopApp(game.packageName)
                diskCatalogManager.reconcileGame(sdBase, entry)
                mountManager.forceStopApp(game.packageName)
                syncModuleGamelist()
                syncDiskCatalog(sdBase)
                AppLogger.success("Games", "Imported & reconciled portable game: ${game.displayName} (${game.packageName}), size: $realSize bytes, uuid: $preferredUuid")
            }
        }

    suspend fun reconcileApp(entry: GameEntry, sdBase: String = "/data/sdext2") = withContext(Dispatchers.IO) {
        mountManager.forceStopApp(entry.packageName)
        val res = diskCatalogManager.reconcileGame(sdBase, entry)
        mountManager.forceStopApp(entry.packageName)
        res
    }

    suspend fun restructureGame(
        game: DiscoveredGame,
        sdBase: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        diskCatalogManager.restructureGameToStandard(sdBase, game, onProgress)
    }

    suspend fun deleteDiscoveredGame(game: DiscoveredGame, sdBase: String): Result<Unit> = withContext(Dispatchers.IO) {
        diskCatalogManager.deleteDiscoveredGame(sdBase, game)
    }

    suspend fun refreshMountStatuses() = withContext(Dispatchers.IO) {
        val games = gameDao.getAllGamesSync()
        if (games.isEmpty()) return@withContext

        val activeMounts = mountManager.getActiveMountpointsSet()
        val verifiedCanaries = mountManager.verifyCanariesBatch(games)
        val blkidOut = RootShell.execForOutput("blkid 2>/dev/null")

        // Verify if backing block device for SD mount base is physically present in kernel
        val sdBaseMountLine = RootShell.execForOutput("grep -E ' (/data/sdext2|/dev/mountx/sdext2) ' /proc/mounts | head -n 1").trim()
        val activeSdBase = sdBaseMountLine.split(Regex("\\s+")).getOrNull(1) ?: "/data/sdext2"
        val sdBlockDev = if (sdBaseMountLine.isNotBlank()) {
            sdBaseMountLine.split(Regex("\\s+")).firstOrNull() ?: ""
        } else ""

        val pathsToProbe = mutableSetOf<String>()
        pathsToProbe.add("/data/adb/modules/MountX/mountpoints.conf")
        if (sdBlockDev.startsWith("/dev/block/")) {
            pathsToProbe.add(sdBlockDev)
            pathsToProbe.add("/sys/class/block/${sdBlockDev.substringAfterLast('/')}")
        }
        pathsToProbe.add("$activeSdBase/MountX")

        for (g in games) {
            val pkg = g.packageName
            pathsToProbe.add("$activeSdBase/MountX/Android/data/$pkg")
            pathsToProbe.add("$activeSdBase/MountX/Android/obb/$pkg")
            pathsToProbe.add("/data/media/0/Android/data/$pkg")
            pathsToProbe.add("/data/media/0/Android/obb/$pkg")
            g.mountPoints.forEach { mp ->
                if (mp.sourcePath.isNotBlank()) pathsToProbe.add(mp.sourcePath)
            }
            collectCanaryProbePaths(g, activeMounts, pathsToProbe)
        }

        // Single high-performance batch shell execution for all paths
        val existingPaths = RootShell.pathsExistBatch(pathsToProbe)

        val isSdBlockAlive = if (sdBlockDev.startsWith("/dev/block/")) {
            existingPaths.contains(sdBlockDev) || existingPaths.contains("/sys/class/block/${sdBlockDev.substringAfterLast('/')}")
        } else {
            true
        }

        val isSdBaseMounted = (activeMounts.contains(activeSdBase) || existingPaths.contains("$activeSdBase/MountX")) && isSdBlockAlive
        var enabledStateChanged = false

        for (g in games) {
            val targetData = when (g.mode) {
                MountMode.FILES -> "/storage/emulated/0/Android/data/${g.packageName}/files"
                MountMode.PKG -> "/storage/emulated/0/Android/data/${g.packageName}"
            }
            val targetObb = "/storage/emulated/0/Android/obb/${g.packageName}"
            val relData = MountManager.extractRelativePath(targetData)
            val relObb = "Android/obb/${g.packageName}"

            // Authoritative multi-target mount check: requires /proc/1/mountinfo + /mnt/user + POSIX dev:ino / canary + Zygote namespace
            val isMounted = (verifiedCanaries[g.packageName] == true) && areAllEnabledMountPointsActive(g, activeMounts, existingPaths)

            // Multi-disk storage check: verify if ALL enabled mountPoints have their required backing storage attached
            val enabledPoints = g.mountPoints.filter { it.enabled }
            val isStorageAttached = if (enabledPoints.isNotEmpty()) {
                enabledPoints.all { mp ->
                    val uuidPresent = mp.diskUuid.isNullOrBlank() || blkidOut.contains(mp.diskUuid, ignoreCase = true)
                    val pathPresent = existingPaths.contains(mp.sourcePath) && isSdBlockAlive
                    uuidPresent && (pathPresent || isSdBaseMounted || mp.diskUuid != null)
                }
            } else if (g.mountPoints.isNotEmpty()) {
                g.mountPoints.any { mp ->
                    (mp.diskUuid != null && blkidOut.contains(mp.diskUuid, ignoreCase = true)) ||
                    (existingPaths.contains(mp.sourcePath) && isSdBlockAlive)
                } || isSdBaseMounted
            } else {
                isSdBaseMounted
            }

            if (!isStorageAttached && (isMounted || enabledPoints.any { findMatchedActiveMount(it, g.packageName, activeMounts) != null })) {
                // Ghost mounts detected with missing backing disk! Clean up zombie mountpoints safely without -f.
                val ghostPaths = buildList {
                    add(targetData)
                    add(targetObb)
                    for (ns in listOf("/mnt/user/0", "/mnt/installer/0", "/mnt/androidwritable/0", "/mnt/pass_through/0", "/mnt/runtime/default", "/mnt/runtime/read", "/mnt/runtime/write", "/mnt/runtime/full", "/storage")) {
                        add("$ns/emulated/0/$relData")
                        add("$ns/emulated/0/$relObb")
                    }
                    add("/data/media/0/$relData")
                    add("/data/media/0/$relObb")
                    enabledPoints.forEach { mp ->
                        findMatchedActiveMount(mp, g.packageName, activeMounts)?.let { add(it) }
                    }
                }
                app.mountx.root.MountManager.unmountSafeAcrossNamespaces(ghostPaths)
            }

            val newStatus = when {
                !isStorageAttached -> MountStatus.DISK_DETACHED
                isMounted -> MountStatus.MOUNTED
                else -> {
                    if (!isSdBaseMounted) {
                        // When sdBase is unmounted, partition contents cannot be inspected.
                        // Never flag games as NEED_MIGRATION while partition is unmounted.
                        MountStatus.UNMOUNTED
                    } else {
                        val extData = "$activeSdBase/MountX/Android/data/${g.packageName}"
                        val extObb = "$activeSdBase/MountX/Android/obb/${g.packageName}"
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
            }
            if (newStatus != g.mountStatus) {
                gameDao.updateMountStatus(g.packageName, newStatus)
            }
            if (newStatus == MountStatus.MOUNTED && !g.isEnabled) {
                gameDao.updateGame(g.copy(
                    mountStatus = MountStatus.MOUNTED,
                    isEnabled = true
                ))
                enabledStateChanged = true
            }
        }
        if (enabledStateChanged || !existingPaths.contains("/data/adb/modules/MountX/mountpoints.conf")) {
            syncModuleGamelist()
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
    private fun buildProtectedSources(game: GameEntry?): Set<String> = buildSet {
        game?.mountPoints
            ?.filter { it.enabled && it.sourcePath.isNotBlank() }
            ?.forEach { mp ->
                add(mp.sourcePath.trimEnd('/'))
                val imgPath = mp.containerImgPath
                if (mp.isVirtualContainer && !imgPath.isNullOrBlank()) {
                    add(imgPath.trimEnd('/'))
                }
            }
    }

    private fun resolveExternalPartitionBytes(
        paths: List<String>,
        sizeMap: Map<String, Long>,
        protectedSources: Set<String> = emptySet()
    ): Long {
        val normalizedProtected = protectedSources.map { it.trimEnd('/') }.toSet()
        val pathsByBase = paths.filter { it.isNotBlank() }.groupBy { extractBasePartition(it) }
        val baseSizes = pathsByBase.mapValues { (_, basePaths) ->
            basePaths.maxOfOrNull { sizeMap[it] ?: 0L } ?: 0L
        }
        val protectedBases = pathsByBase.filterValues { basePaths ->
            basePaths.any { it.trimEnd('/') in normalizedProtected }
        }.keys
        val hasSubstantive = baseSizes.values.any { it > 512 * 1024L }
        val nonProtectedThreshold = if (hasSubstantive) 128 * 1024L else 32 * 1024L
        return baseSizes.entries.sumOf { (base, size) ->
            when {
                base in protectedBases && size > 4096L -> size
                size > nonProtectedThreshold -> size
                else -> 0L
            }
        }
    }

    suspend fun calculateDataSize(packageName: String, sdBase: String = "/data/sdext2"): Long =
        withContext(Dispatchers.IO) {
            val game = gameDao.getGameByPackage(packageName)
            val internalData = game?.mountPoints?.firstOrNull { it.resolveCategory() == MountPointCategory.EXTERNAL_DATA }?.targetPath?.ifBlank { null }
                ?: "/data/media/0/Android/data/$packageName"
            val internalObb = "/data/media/0/Android/obb/$packageName"
            val activeMounts = mountManager.getActiveMountpointsSet()
            val isDataMountedReal = activeMounts.any { it.startsWith(internalData) || it.startsWith("/storage/emulated/0/Android/data/$packageName") }
            val isObbMountedReal = activeMounts.any { it.startsWith(internalObb) || it.startsWith("/storage/emulated/0/Android/obb/$packageName") }

            val externalBases = getExternalStorageBases(sdBase, game)

            val candidateExtDataPaths = externalBases.flatMap { listOf("$it/MountX/Android/data/$packageName", "$it/Android/data/$packageName") }.toMutableList()
            val candidateExtObbPaths = externalBases.flatMap { listOf("$it/MountX/Android/obb/$packageName", "$it/Android/obb/$packageName") }.toMutableList()
            val candidateExtMediaPaths = externalBases.flatMap { listOf("$it/MountX/Android/media/$packageName", "$it/Android/media/$packageName") }.toMutableList()
            val candidateExtApkPaths = externalBases.flatMap { listOf("$it/MountX/app/$packageName", "$it/Android/app/$packageName") }.toMutableList()
            val candidateExtLibPaths = externalBases.flatMap { listOf("$it/MountX/lib/$packageName", "$it/Android/lib/$packageName") }.toMutableList()
            val candidateExtPrivatePaths = externalBases.flatMap { listOf("$it/MountX/data/$packageName", "$it/.mountx/containers/${packageName}_data.img", "$it/MountX/containers/${packageName}_data.img") }.toMutableList()
            val candidateExtCachePaths = externalBases.map { "$it/MountX/cache/$packageName" }.toMutableList()
            val candidateCustomPaths = mutableListOf<String>()
            game?.mountPoints?.forEach { mp ->
                if (mp.sourcePath.isNotBlank()) {
                    when (mp.resolveCategory()) {
                        MountPointCategory.EXTERNAL_DATA, MountPointCategory.GAME_ASSETS -> candidateExtDataPaths.add(mp.sourcePath)
                        MountPointCategory.OBB_STORAGE -> candidateExtObbPaths.add(mp.sourcePath)
                        MountPointCategory.MEDIA_DOWNLOADS -> candidateExtMediaPaths.add(mp.sourcePath)
                        MountPointCategory.APP_PACKAGE -> {
                            if (mp.targetPath.contains("/lib", ignoreCase = true) || mp.id == "lib" || mp.id.startsWith("lib_")) {
                                candidateExtLibPaths.add(mp.sourcePath)
                            } else {
                                candidateExtApkPaths.add(mp.sourcePath)
                            }
                        }
                        MountPointCategory.PRIVATE_INTERNAL -> candidateExtPrivatePaths.add(mp.containerImgPath ?: mp.sourcePath)
                        MountPointCategory.CACHE_SHADERS -> candidateExtCachePaths.add(mp.sourcePath)
                        MountPointCategory.CUSTOM -> candidateCustomPaths.add(mp.sourcePath)
                    }
                }
            }

            val distinctExtData = candidateExtDataPaths.distinct()
            val distinctExtObb = candidateExtObbPaths.distinct()
            val distinctExtMedia = candidateExtMediaPaths.distinct()
            val distinctExtApk = candidateExtApkPaths.distinct()
            val distinctExtLib = candidateExtLibPaths.distinct()
            val distinctExtPrivate = candidateExtPrivatePaths.distinct()
            val distinctExtCache = candidateExtCachePaths.distinct()
            val distinctCustom = candidateCustomPaths.distinct().filter { p ->
                !distinctExtData.contains(p) && !distinctExtObb.contains(p) && !distinctExtMedia.contains(p) &&
                !distinctExtApk.contains(p) && !distinctExtLib.contains(p) && !distinctExtPrivate.contains(p) && !distinctExtCache.contains(p)
            }

            val pathsToScan = mutableListOf<String>()
            pathsToScan.add(internalData)
            pathsToScan.add(internalObb)
            pathsToScan.addAll(distinctExtData)
            pathsToScan.addAll(distinctExtObb)
            pathsToScan.addAll(distinctExtMedia)
            pathsToScan.addAll(distinctExtApk)
            pathsToScan.addAll(distinctExtLib)
            pathsToScan.addAll(distinctExtPrivate)
            pathsToScan.addAll(distinctExtCache)
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

            val protectedSources = buildProtectedSources(game)
            val extDataBytesSum = resolveExternalPartitionBytes(distinctExtData, sizeMap, protectedSources)
            val extObbBytesSum = resolveExternalPartitionBytes(distinctExtObb, sizeMap, protectedSources)
            val extMediaBytesSum = resolveExternalPartitionBytes(distinctExtMedia, sizeMap, protectedSources)
            val extApkBytesSum = resolveExternalPartitionBytes(distinctExtApk, sizeMap, protectedSources)
            val extLibBytesSum = resolveExternalPartitionBytes(distinctExtLib, sizeMap, protectedSources)
            val extPrivateBytesSum = resolveExternalPartitionBytes(distinctExtPrivate, sizeMap, protectedSources)
            val extCacheBytesSum = resolveExternalPartitionBytes(distinctExtCache, sizeMap, protectedSources)
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

            var totalSize = effectiveDataSize + effectiveObbSize + extMediaBytesSum + extApkBytesSum + extLibBytesSum + extPrivateBytesSum + extCacheBytesSum + customBytesSum
            val previousKnownSize = game?.dataSizeBytes ?: 0L
            if (totalSize <= 128 * 1024L && previousKnownSize > 512 * 1024L) {
                totalSize = previousKnownSize
            }
            if (totalSize > 0L) {
                gameDao.updateDataSize(packageName, totalSize)
            }

            // Auto-detect NEED_MIGRATION only when the app is actively configured/enabled for external mounting.
            // Clean internal-only or freshly restored apps (all mountPoints disabled) must stay UNMOUNTED.
            val isSdBaseMounted = RootShell.isMountpoint(sdBase)
            val hasEnabledMountConfig = game != null && (game.isEnabled || game.mountPoints.any { it.enabled && it.targetPath.isNotBlank() })
            if (game != null && hasEnabledMountConfig && !isDataMountedReal && !isObbMountedReal && isSdBaseMounted) {
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
            } else if (game != null && !hasEnabledMountConfig && game.mountStatus == MountStatus.NEED_MIGRATION) {
                gameDao.updateMountStatus(packageName, MountStatus.UNMOUNTED)
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
                val extApkPaths: List<String>,
                val extLibPaths: List<String>,
                val extPrivatePaths: List<String>,
                val extCachePaths: List<String>,
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
                val extApk = externalBases.flatMap { listOf("$it/MountX/app/$pkg", "$it/Android/app/$pkg") }.toMutableList()
                val extLib = externalBases.flatMap { listOf("$it/MountX/lib/$pkg", "$it/Android/lib/$pkg") }.toMutableList()
                val extPrivate = externalBases.flatMap { listOf("$it/MountX/data/$pkg", "$it/.mountx/containers/${pkg}_data.img", "$it/MountX/containers/${pkg}_data.img") }.toMutableList()
                val extCache = externalBases.map { "$it/MountX/cache/$pkg" }.toMutableList()
                val custom = mutableListOf<String>()

                game.mountPoints.forEach { mp ->
                    if (mp.sourcePath.isNotBlank()) {
                        when (mp.resolveCategory()) {
                            MountPointCategory.EXTERNAL_DATA, MountPointCategory.GAME_ASSETS -> extData.add(mp.sourcePath)
                            MountPointCategory.OBB_STORAGE -> extObb.add(mp.sourcePath)
                            MountPointCategory.MEDIA_DOWNLOADS -> extMedia.add(mp.sourcePath)
                            MountPointCategory.APP_PACKAGE -> {
                                if (mp.targetPath.contains("/lib", ignoreCase = true) || mp.id == "lib" || mp.id.startsWith("lib_")) {
                                    extLib.add(mp.sourcePath)
                                } else {
                                    extApk.add(mp.sourcePath)
                                }
                            }
                            MountPointCategory.PRIVATE_INTERNAL -> extPrivate.add(mp.containerImgPath ?: mp.sourcePath)
                            MountPointCategory.CACHE_SHADERS -> extCache.add(mp.sourcePath)
                            MountPointCategory.CUSTOM -> custom.add(mp.sourcePath)
                        }
                    }
                }

                val distinctExtData = extData.distinct()
                val distinctExtObb = extObb.distinct()
                val distinctExtMedia = extMedia.distinct()
                val distinctExtApk = extApk.distinct()
                val distinctExtLib = extLib.distinct()
                val distinctExtPrivate = extPrivate.distinct()
                val distinctExtCache = extCache.distinct()
                val distinctCustom = custom.distinct().filter { p ->
                    !distinctExtData.contains(p) && !distinctExtObb.contains(p) && !distinctExtMedia.contains(p) &&
                    !distinctExtApk.contains(p) && !distinctExtLib.contains(p) && !distinctExtPrivate.contains(p) && !distinctExtCache.contains(p)
                }

                bundles[pkg] = GameBundle(
                    internalData, internalObb,
                    distinctExtData, distinctExtObb, distinctExtMedia,
                    distinctExtApk, distinctExtLib, distinctExtPrivate, distinctExtCache,
                    distinctCustom
                )

                allTargets.add(internalData)
                allTargets.add(internalObb)
                allTargets.addAll(distinctExtData)
                allTargets.addAll(distinctExtObb)
                allTargets.addAll(distinctExtMedia)
                allTargets.addAll(distinctExtApk)
                allTargets.addAll(distinctExtLib)
                allTargets.addAll(distinctExtPrivate)
                allTargets.addAll(distinctExtCache)
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

                val protectedSources = buildProtectedSources(game)
                val extDataBytesSum = resolveExternalPartitionBytes(bundle.extDataPaths, sizeMap, protectedSources)
                val extObbBytesSum = resolveExternalPartitionBytes(bundle.extObbPaths, sizeMap, protectedSources)
                val extMediaBytesSum = resolveExternalPartitionBytes(bundle.extMediaPaths, sizeMap, protectedSources)
                val extApkBytesSum = resolveExternalPartitionBytes(bundle.extApkPaths, sizeMap, protectedSources)
                val extLibBytesSum = resolveExternalPartitionBytes(bundle.extLibPaths, sizeMap, protectedSources)
                val extPrivateBytesSum = resolveExternalPartitionBytes(bundle.extPrivatePaths, sizeMap, protectedSources)
                val extCacheBytesSum = resolveExternalPartitionBytes(bundle.extCachePaths, sizeMap, protectedSources)
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

                var totalSize = effectiveDataSize + effectiveObbSize + extMediaBytesSum + extApkBytesSum + extLibBytesSum + extPrivateBytesSum + extCacheBytesSum + customBytesSum
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

            val protectedSources = buildProtectedSources(game)
            val internalBytes = (sizeMap[internalData] ?: 0L) + (sizeMap[internalObb] ?: 0L)
            val extDataSum = resolveExternalPartitionBytes(distinctExtData, sizeMap, protectedSources)
            val extObbSum = resolveExternalPartitionBytes(distinctExtObb, sizeMap, protectedSources)
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

        val currentUserId = Regex("^/data/user/(\\d+)/").find(context.filesDir.absolutePath)?.groupValues?.getOrNull(1) ?: "0"
        val apkParentDir = if (sourceDir.isNotBlank()) java.io.File(sourceDir).parent ?: "" else ""
        val oatDir = if (apkParentDir.isNotBlank()) "$apkParentDir/oat" else ""
        val dataDir = if (currentUserId == "0") "/data/data/$packageName" else "/data/user/$currentUserId/$packageName"
        val cacheDir = "$dataDir/cache"
        val codeCacheDir = "$dataDir/code_cache"
        val targetPathForData = game?.mountPoints?.firstOrNull { it.resolveCategory() == MountPointCategory.EXTERNAL_DATA }?.targetPath
        val ext1Data = targetPathForData?.ifBlank { null } ?: "/data/media/$currentUserId/Android/data/$packageName"
        val ext1Obb = "/data/media/$currentUserId/Android/obb/$packageName"
        val ext1Media = "/data/media/$currentUserId/Android/media/$packageName"
        var rawApkDirBytes = 0L
        var ext1MediaBytes = 0L
        var ext2MediaBytes = 0L

        val activeMounts = mountManager.getActiveMountpointsSet()
        val hasLibMountConfig = game?.mountPoints?.any { it.enabled && (it.targetPath.contains("/lib") || it.id.startsWith("lib")) } == true
        val hasCacheMountConfig = game?.mountPoints?.any { it.enabled && (it.category == MountPointCategory.CACHE_SHADERS || it.id.startsWith("cache")) } == true

        // Build candidate paths for all known external storages (both MountX and legacy)
        val candidateExtDataPaths = externalBases.flatMap { listOf("$it/MountX/Android/data/$packageName", "$it/Android/data/$packageName") }.toMutableList()
        val candidateExtObbPaths = externalBases.flatMap { listOf("$it/MountX/Android/obb/$packageName", "$it/Android/obb/$packageName") }.toMutableList()
        val candidateExtMediaPaths = externalBases.flatMap { listOf("$it/MountX/Android/media/$packageName", "$it/Android/media/$packageName") }.toMutableList()
        val candidateExtApkPaths = externalBases.flatMap { listOf("$it/MountX/app/$packageName", "$it/Android/app/$packageName") }.toMutableList()
        val candidateExtLibPaths = externalBases.flatMap { listOf("$it/MountX/lib/$packageName", "$it/Android/lib/$packageName") }.toMutableList()
        val candidateExtPrivatePaths = externalBases.flatMap { listOf("$it/MountX/data/$packageName", "$it/.mountx/containers/${packageName}_data.img", "$it/MountX/containers/${packageName}_data.img") }.toMutableList()
        val candidateExtCachePaths = externalBases.map { "$it/MountX/cache/$packageName" }.toMutableList()
        val candidateCustomPaths = mutableListOf<String>()
        game?.mountPoints?.forEach { mp ->
            if (mp.sourcePath.isNotBlank()) {
                when (mp.resolveCategory()) {
                    MountPointCategory.EXTERNAL_DATA, MountPointCategory.GAME_ASSETS -> candidateExtDataPaths.add(mp.sourcePath)
                    MountPointCategory.OBB_STORAGE -> candidateExtObbPaths.add(mp.sourcePath)
                    MountPointCategory.MEDIA_DOWNLOADS -> candidateExtMediaPaths.add(mp.sourcePath)
                    MountPointCategory.APP_PACKAGE -> {
                        if (mp.targetPath.contains("/lib", ignoreCase = true) || mp.id == "lib" || mp.id.startsWith("lib_")) {
                            candidateExtLibPaths.add(mp.sourcePath)
                        } else {
                            candidateExtApkPaths.add(mp.sourcePath)
                        }
                    }
                    MountPointCategory.PRIVATE_INTERNAL -> candidateExtPrivatePaths.add(mp.containerImgPath ?: mp.sourcePath)
                    MountPointCategory.CACHE_SHADERS -> if (!mp.sourcePath.contains("/Android/data/")) candidateExtCachePaths.add(mp.sourcePath)
                    MountPointCategory.CUSTOM -> candidateCustomPaths.add(mp.sourcePath)
                }
            }
        }

        val distinctExtData = candidateExtDataPaths.distinct()
        val distinctExtObb = candidateExtObbPaths.distinct()
        val distinctExtMedia = candidateExtMediaPaths.distinct()
        val distinctExtApk = candidateExtApkPaths.distinct()
        val distinctExtLib = candidateExtLibPaths.distinct()
        val distinctExtPrivate = candidateExtPrivatePaths.distinct()
        val distinctExtCache = candidateExtCachePaths.distinct()
        val distinctCustom = candidateCustomPaths.distinct().filter { p ->
            !distinctExtData.contains(p) && !distinctExtObb.contains(p) && !distinctExtMedia.contains(p) &&
            !distinctExtApk.contains(p) && !distinctExtLib.contains(p) && !distinctExtPrivate.contains(p) && !distinctExtCache.contains(p)
        }

        // Always scan ALL paths (internal + external) to get accurate sizes regardless of mountStatus.
        // When data is bind-mounted from SD to internal path, du on internal path returns SD size — correct.
        // When not mounted, internal path shows actual internal size — also correct.
        val targetsList = mutableListOf(apkParentDir, libDir, oatDir, dataDir, cacheDir, codeCacheDir, ext1Data, ext1Obb, ext1Media)
        targetsList.addAll(distinctExtData)
        targetsList.addAll(distinctExtObb)
        targetsList.addAll(distinctExtMedia)
        targetsList.addAll(distinctExtApk)
        targetsList.addAll(distinctExtLib)
        targetsList.addAll(distinctExtPrivate)
        targetsList.addAll(distinctExtCache)
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
                        path == apkParentDir -> rawApkDirBytes = bytes
                        path == libDir -> if (bytes > libBytes) libBytes = bytes
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

        val resolvedApkBytes = (rawApkDirBytes - dexBytes - libBytes).coerceAtLeast(0L)
        if (resolvedApkBytes > apkBytes) {
            apkBytes = resolvedApkBytes
        }

        val protectedSources = buildProtectedSources(game)
        ext2DataBytes = resolveExternalPartitionBytes(distinctExtData, sizeMap, protectedSources)
        ext2ObbBytes = resolveExternalPartitionBytes(distinctExtObb, sizeMap, protectedSources)
        ext2MediaBytes = resolveExternalPartitionBytes(distinctExtMedia, sizeMap, protectedSources)
        val ext2ApkBytes = resolveExternalPartitionBytes(distinctExtApk, sizeMap, protectedSources)
        val ext2LibBytes = resolveExternalPartitionBytes(distinctExtLib, sizeMap, protectedSources)
        val ext2PrivateBytes = resolveExternalPartitionBytes(distinctExtPrivate, sizeMap, protectedSources)
        val ext2CacheBytes = resolveExternalPartitionBytes(distinctExtCache, sizeMap, protectedSources)
        val customBytesSum = distinctCustom.sumOf { sizeMap[it] ?: 0L }

        val hasDataMountConfig = game?.mountPoints?.any { it.enabled && it.resolveCategory() == MountPointCategory.EXTERNAL_DATA } ?: true
        val hasObbMountConfig = game?.mountPoints?.any { it.enabled && it.resolveCategory() == MountPointCategory.OBB_STORAGE } ?: true
        val hasMediaMountConfig = game?.mountPoints?.any { it.enabled && it.resolveCategory() == MountPointCategory.MEDIA_DOWNLOADS } ?: true
        val hasApkMountConfig = game?.mountPoints?.any { it.enabled && it.resolveCategory() == MountPointCategory.APP_PACKAGE && !it.targetPath.contains("/lib") && it.id != "lib" } ?: true
        val hasPrivateMountConfig = game?.mountPoints?.any { it.enabled && (it.resolveCategory() == MountPointCategory.PRIVATE_INTERNAL || it.id in listOf("private", "private_internal")) } ?: true

        val isAppMountActive = game == null || game.mountStatus == MountStatus.MOUNTED
        val escapedPkg = Regex.escape(packageName)
        val cleanExt1Data = ext1Data.trimEnd('/')
        val cleanExt1Obb = ext1Obb.trimEnd('/')
        val cleanExt1Media = ext1Media.trimEnd('/')
        val extDataMountRegex = Regex("^/(?:storage/emulated|data/media)/\\d+/Android/data/$escapedPkg(?:/(?!cache\\z).+|\\z)")
        val extObbMountRegex = Regex("^/(?:storage/emulated|data/media)/\\d+/Android/obb/$escapedPkg(?:/|\\z)")
        val extMediaMountRegex = Regex("^/(?:storage/emulated|data/media)/\\d+/Android/media/$escapedPkg(?:/|\\z)")
        val pkgInAppDirRegex = Regex("(?:^|/)$escapedPkg(?:-|/|\\z)")
        val privMountRegex = Regex("^/(?:data/data|data/user/\\d+|data_mirror/data_[^/]+/[^/]+/\\d+)/$escapedPkg$")
        val cacheMountRegex = Regex("^/(?:data/data|data/user/\\d+|data_mirror/data_[^/]+/[^/]+/\\d+|data/media/\\d+/Android/data|storage/emulated/\\d+/Android/data)/$escapedPkg/cache$")

        val isDataMountedReal = isAppMountActive && hasDataMountConfig &&
            activeMounts.any {
                it == cleanExt1Data ||
                    (it.startsWith("$cleanExt1Data/") && !it.endsWith("/Android/data/$packageName/cache")) ||
                    extDataMountRegex.matches(it)
            }

        val isObbMountedReal = isAppMountActive && hasObbMountConfig &&
            activeMounts.any {
                it == cleanExt1Obb ||
                    it.startsWith("$cleanExt1Obb/") ||
                    extObbMountRegex.containsMatchIn(it)
            }

        val isMediaMountedReal = isAppMountActive && hasMediaMountConfig &&
            activeMounts.any {
                it == cleanExt1Media ||
                    it.startsWith("$cleanExt1Media/") ||
                    extMediaMountRegex.containsMatchIn(it)
            }

        val isApkMountedReal = isAppMountActive && hasApkMountConfig &&
            activeMounts.any { it.startsWith("/data/app/") && pkgInAppDirRegex.containsMatchIn(it) && !it.endsWith("/lib") && !it.contains("/lib/") }

        val isLibMountedReal = isAppMountActive && hasLibMountConfig &&
            activeMounts.any { it.startsWith("/data/app/") && pkgInAppDirRegex.containsMatchIn(it) && (it.endsWith("/lib") || it.contains("/lib/")) }

        val isPrivateMountedReal = isAppMountActive && hasPrivateMountConfig &&
            activeMounts.any { privMountRegex.matches(it) }

        val isCacheMountedReal = isAppMountActive && hasCacheMountConfig &&
            activeMounts.any { cacheMountRegex.matches(it) }

        val dataBytes = (rawPrivateDataBytes - cacheBytes).coerceAtLeast(0L)

        // ext1Bytes: what's at the internal Android/data path (actual size — follows bind mount when mounted)
        val ext1Bytes = ext1DataBytes + ext1ObbBytes + ext1MediaBytes

        // ext2Bytes: what's directly on external storage partitions (source paths)
        val ext2Bytes = ext2DataBytes + ext2ObbBytes + ext2MediaBytes + ext2ApkBytes + ext2LibBytes + ext2PrivateBytes + ext2CacheBytes

        AppLogger.info("GameRepo", "Breakdown[$packageName]: apk=${apkBytes/1024}KB lib=${libBytes/1024}KB data=${dataBytes/1024}KB cache=${cacheBytes/1024}KB ext1=${ext1Bytes/1024}KB(data=${ext1DataBytes/1024} obb=${ext1ObbBytes/1024} media=${ext1MediaBytes/1024} mounted=$isDataMountedReal) ext2=${ext2Bytes/1024}KB(data=${ext2DataBytes/1024} obb=${ext2ObbBytes/1024} media=${ext2MediaBytes/1024} apk=${ext2ApkBytes/1024} lib=${ext2LibBytes/1024} priv=${ext2PrivateBytes/1024} cache=${ext2CacheBytes/1024} custom=${customBytesSum/1024})")

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

        val isAnyMountReal = isDataMountedReal || isObbMountedReal || isMediaMountedReal ||
            isApkMountedReal || isLibMountedReal || isPrivateMountedReal || isCacheMountedReal
        // Self-healing: If kernel reports NO active mount point and external storage has only empty folder skeleton (<= 64KB),
        // but DB has stale MOUNTED, correct it to UNMOUNTED.
        if (game != null && game.mountStatus == MountStatus.MOUNTED && !isAnyMountReal && ext2Bytes <= 64 * 1024L) {
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
            ext2ApkBytes = ext2ApkBytes,
            ext2LibBytes = ext2LibBytes,
            ext2PrivateBytes = ext2PrivateBytes,
            ext2CacheBytes = ext2CacheBytes,
            customBytes = customBytesSum,
            isExt1Mounted = isAnyMountReal,
            isDataMounted = isDataMountedReal,
            isObbMounted = isObbMountedReal,
            isMediaMounted = isMediaMountedReal,
            isApkMounted = isApkMountedReal,
            isLibMounted = isLibMountedReal,
            isPrivateMounted = isPrivateMountedReal,
            isCacheMounted = isCacheMountedReal
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

        val internalFiles = "/data/media/0/Android/data/$packageName"
        val sdFiles = listOf(
            "$sdBase/MountX/Android/data/$packageName",
            "$sdBase/Android/data/$packageName"
        ).firstOrNull { RootShell.exists(it) } ?: "$sdBase/MountX/Android/data/$packageName"
        val internalObb = "/data/media/0/Android/obb/$packageName"
        val sdObb = listOf(
            "$sdBase/MountX/Android/obb/$packageName",
            "$sdBase/Android/obb/$packageName"
        ).firstOrNull { RootShell.exists(it) } ?: "$sdBase/MountX/Android/obb/$packageName"
        val internalMedia = "/data/media/0/Android/media/$packageName"
        val sdMedia = listOf(
            "$sdBase/MountX/Android/media/$packageName",
            "$sdBase/Android/media/$packageName"
        ).firstOrNull { RootShell.exists(it) } ?: "$sdBase/MountX/Android/media/$packageName"
        val pubFolder = "/data/media/0/${displayName.replace(" ", "")}"
        val internalCache = "/data/user/0/$packageName/cache"
        val sdCache = listOf(
            "$sdBase/MountX/cache/$packageName",
            "$sdBase/cache/$packageName"
        ).firstOrNull { RootShell.exists(it) } ?: "$sdBase/MountX/cache/$packageName"
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

        // 1. EXTERNAL_DATA: Android/data/<pkg>
        val internalFilesSize = sizeMap[internalFiles] ?: 0L
        val sdFilesSize = sizeMap[sdFiles] ?: 0L
        val filesSize = if (sdFilesSize > 4096L && sdFilesSize > internalFilesSize) sdFilesSize else internalFilesSize
        list.add(
            CandidateDirectory(
                id = "external_data",
                category = app.mountx.data.model.MountPointCategory.EXTERNAL_DATA,
                title = context.getString(R.string.candidate_data_title),
                description = context.getString(R.string.candidate_data_desc),
                relativePath = "Android/data/$packageName",
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
        val obbSize = if (sdObbSize > 4096L && sdObbSize > internalObbSize) sdObbSize else internalObbSize
        list.add(
            CandidateDirectory(
                id = "obb_storage",
                category = app.mountx.data.model.MountPointCategory.OBB_STORAGE,
                title = context.getString(R.string.candidate_obb_title),
                description = context.getString(R.string.candidate_obb_desc),
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
        val internalMediaSize = sizeMap[internalMedia] ?: 0L
        val sdMediaSize = sizeMap[sdMedia] ?: 0L
        val mediaSize = (if (sdMediaSize > 4096L && sdMediaSize > internalMediaSize) sdMediaSize else internalMediaSize) + pubSize
        list.add(
            CandidateDirectory(
                id = "media_downloads",
                category = app.mountx.data.model.MountPointCategory.MEDIA_DOWNLOADS,
                title = context.getString(R.string.candidate_media_title),
                description = context.getString(R.string.candidate_media_desc),
                relativePath = "Android/media/$packageName",
                internalPath = internalMedia,
                sdPath = sdMedia,
                sizeBytes = mediaSize,
                defaultEnabled = mediaSize > 0L,
                childItems = emptyList()
            )
        )

        // 4. CACHE_SHADERS: data/user/0/<pkg>/cache
        val internalCacheSize = sizeMap[internalCache] ?: 0L
        val sdCacheSize = sizeMap[sdCache] ?: 0L
        val cacheSize = if (sdCacheSize > 4096L && sdCacheSize > internalCacheSize) sdCacheSize else internalCacheSize
        list.add(
            CandidateDirectory(
                id = "cache_shaders",
                category = app.mountx.data.model.MountPointCategory.CACHE_SHADERS,
                title = context.getString(R.string.candidate_cache_title),
                description = context.getString(R.string.candidate_cache_desc),
                relativePath = "data/user/0/$packageName/cache",
                internalPath = internalCache,
                sdPath = sdCache,
                sizeBytes = cacheSize,
                defaultEnabled = false,
                childItems = emptyList()
            )
        )

        // 5. PRIVATE_INTERNAL: /data/data/<pkg> (Adaptive Dual-Engine: Native Folder on POSIX, Sparse .img on Non-POSIX)
        val privateSize = sizeMap[privateDataDir] ?: 0L
        val targetCap = storageManager.inspectStorageTarget(sdBase)
        val usePrivateContainer = !targetCap.isPosixCompatible
        val privateSdTarget = if (usePrivateContainer) {
            "$sdBase/.mountx/containers/${packageName}_data.img"
        } else {
            "$sdBase/MountX/data/$packageName"
        }
        val oneGb = 1024L * 1024L * 1024L
        if (privateSize >= oneGb) {
            list.add(
                CandidateDirectory(
                    id = "private_internal",
                    category = app.mountx.data.model.MountPointCategory.PRIVATE_INTERNAL,
                    title = context.getString(R.string.candidate_private_title),
                    description = context.getString(R.string.candidate_private_desc),
                    relativePath = "data/user/0/$packageName",
                    internalPath = privateDataDir,
                    sdPath = privateSdTarget,
                    sizeBytes = privateSize,
                    defaultEnabled = false,
                    isLocked = false,
                    isVirtualContainer = usePrivateContainer,
                    childItems = emptyList()
                )
            )
        } else if (privateSize > 0L) {
            list.add(
                CandidateDirectory(
                    id = "private_internal_locked",
                    category = app.mountx.data.model.MountPointCategory.PRIVATE_INTERNAL,
                    title = context.getString(R.string.candidate_private_title),
                    description = context.getString(R.string.candidate_private_locked_desc),
                    relativePath = "data/user/0/$packageName",
                    internalPath = privateDataDir,
                    sdPath = privateSdTarget,
                    sizeBytes = privateSize,
                    defaultEnabled = false,
                    isLocked = true,
                    lockReason = context.getString(R.string.candidate_private_locked_reason),
                    isVirtualContainer = usePrivateContainer,
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
                    title = context.getString(R.string.candidate_app_package_title),
                    description = context.getString(R.string.candidate_app_package_desc),
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
                            RootShell.exec("rm -rf \"$base/MountX/cache/$packageName\" 2>/dev/null")
                            RootShell.exec("rm -rf \"$base/MountX/Android/data/$packageName/cache\" 2>/dev/null")
                            RootShell.exec("rm -rf \"$base/Android/data/$packageName/cache\" 2>/dev/null")
                            RootShell.exec("rm -rf \"$base/MountX/MountX/Android/data/$packageName/cache\" 2>/dev/null")
                        }
                    }
                }
                "private" -> {
                    if (location == CategoryDeleteLocation.SD_ONLY || location == CategoryDeleteLocation.BOTH) {
                        RootShell.exec("umount -l \"/data/user/0/$packageName\" \"/data/data/$packageName\" \"/data_mirror/data_ce/null/0/$packageName\" 2>/dev/null")
                        externalBases.forEach { base ->
                            RootShell.exec("rm -rf \"$base/MountX/data/$packageName\" 2>/dev/null")
                            RootShell.exec("rm -f \"$base/.mountx/containers/${packageName}_data.img\" \"$base/MountX/containers/${packageName}_data.img\" 2>/dev/null")
                        }
                    }
                    if (location == CategoryDeleteLocation.INTERNAL_ONLY || location == CategoryDeleteLocation.BOTH) {
                        RootShell.exec("pm clear \"$packageName\"")
                    }
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

