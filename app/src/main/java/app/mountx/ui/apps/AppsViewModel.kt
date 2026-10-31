package app.mountx.ui.apps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountx.data.catalog.DiscoveredGame
import app.mountx.data.model.ConflictStrategy
import app.mountx.data.model.AppStorageBreakdown
import app.mountx.data.model.AppEntry
import app.mountx.data.model.GameEntry
import app.mountx.data.model.InstalledAppInfo
import app.mountx.data.model.MigrationTarget
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountStatus
import app.mountx.data.model.MoveDirection
import app.mountx.data.model.OperationProgress
import app.mountx.data.model.OperationType
import app.mountx.data.repository.AppRepository
import app.mountx.data.repository.StorageRepository
import app.mountx.root.RootShell
import app.mountx.util.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GameFilterStatus {
    ALL,
    MOUNTED,
    UNMOUNTED
}

enum class GameSortOption {
    SIZE_DESC,
    NAME_ASC
}

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val storageRepository: StorageRepository,
    private val appPreferences: AppPreferences,
    private val systemSyncMonitor: app.mountx.service.SystemSyncMonitor,
    private val mountNotificationManager: app.mountx.service.MountNotificationManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val gameRepository get() = appRepository

    val apps: StateFlow<List<AppEntry>> = appRepository.observeApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val games: StateFlow<List<AppEntry>> get() = apps

    fun removeApp(packageName: String, restoreToInternal: Boolean) {
        removeGameWithOption(context, packageName, restoreToInternal)
    }

    private val _discoveredGames = MutableStateFlow<List<DiscoveredGame>>(emptyList())
    val discoveredGames: StateFlow<List<DiscoveredGame>> = _discoveredGames.asStateFlow()

    private val _isRestructuring = MutableStateFlow(false)
    val isRestructuring: StateFlow<Boolean> = _isRestructuring.asStateFlow()

    private val _restructureProgressMessage = MutableStateFlow<String?>(null)
    val restructureProgressMessage: StateFlow<String?> = _restructureProgressMessage.asStateFlow()

    private val _availableDisks = MutableStateFlow<List<app.mountx.data.model.SdCardDiskInfo>>(storageRepository.getCachedDisks())
    val availableDisks: StateFlow<List<app.mountx.data.model.SdCardDiskInfo>> = _availableDisks.asStateFlow()

    val sdBasePath: StateFlow<String> = appPreferences.sdBasePath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "/data/sdext2")

    private val _isScanningDisks = MutableStateFlow(false)
    val isScanningDisks: StateFlow<Boolean> = _isScanningDisks.asStateFlow()

    val internalStorageInfo: StateFlow<app.mountx.data.model.InternalStorageInfo?> = storageRepository.observeInternalStorage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _candidateDirectories = MutableStateFlow<List<app.mountx.data.repository.CandidateDirectory>>(emptyList())
    val candidateDirectories: StateFlow<List<app.mountx.data.repository.CandidateDirectory>> = _candidateDirectories.asStateFlow()

    private val _isScanningCandidates = MutableStateFlow(false)
    val isScanningCandidates: StateFlow<Boolean> = _isScanningCandidates.asStateFlow()

    private val _isScanningDiscovered = MutableStateFlow(false)
    val isScanningDiscovered: StateFlow<Boolean> = _isScanningDiscovered.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _pendingOperations = MutableStateFlow<Set<String>>(emptySet())
    val pendingOperations: StateFlow<Set<String>> = _pendingOperations.asStateFlow()

    private val _selectedGameForDetail = MutableStateFlow<GameEntry?>(null)
    val selectedGameForDetail: StateFlow<GameEntry?> = _selectedGameForDetail.asStateFlow()

    fun selectGameForDetail(game: GameEntry?) {
        _selectedGameForDetail.value = game
    }

    init {
        loadAvailableDisks()
        viewModelScope.launch {
            systemSyncMonitor.events.collect { event ->
                when (event) {
                    is app.mountx.service.SystemSyncEvent.StorageMounted,
                    is app.mountx.service.SystemSyncEvent.StorageDisconnected,
                    is app.mountx.service.SystemSyncEvent.DiskError,
                    is app.mountx.service.SystemSyncEvent.DiskHealthRestored,
                    is app.mountx.service.SystemSyncEvent.RefreshAll -> {
                        refresh()
                    }
                    is app.mountx.service.SystemSyncEvent.PackageInstalled,
                    is app.mountx.service.SystemSyncEvent.PackageRemoved -> {
                        appRepository.invalidateInstalledAppsCache()
                        loadInstalledApps(force = true)
                        refresh()
                    }
                }
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterStatus = MutableStateFlow(GameFilterStatus.ALL)
    val filterStatus: StateFlow<GameFilterStatus> = _filterStatus.asStateFlow()

    private val _sortOption = MutableStateFlow(GameSortOption.SIZE_DESC)
    val sortOption: StateFlow<GameSortOption> = _sortOption.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isMovingData = MutableStateFlow(false)
    val isMovingData: StateFlow<Boolean> = _isMovingData.asStateFlow()

    private val _moveMessage = MutableStateFlow<String?>(null)
    val moveMessage: StateFlow<String?> = _moveMessage.asStateFlow()

    private val _storageBreakdown = MutableStateFlow<Pair<Long, Long>>(Pair(0L, 0L))
    val storageBreakdown: StateFlow<Pair<Long, Long>> = _storageBreakdown.asStateFlow()

    private val _storageBreakdownMap = MutableStateFlow<Map<String, AppStorageBreakdown>>(emptyMap())
    private val _activePackageName = MutableStateFlow<String?>(null)

    private val _detailedStorage = MutableStateFlow<AppStorageBreakdown?>(null)
    val detailedStorage: StateFlow<AppStorageBreakdown?> = _detailedStorage.asStateFlow()

    private val _operationProgress = MutableStateFlow<OperationProgress?>(null)
    val operationProgress: StateFlow<OperationProgress?> = _operationProgress.asStateFlow()

    fun clearOperationProgress() {
        _operationProgress.value = null
        mountNotificationManager.cancelOperationProgressNotification()
    }

    fun cancelOperation() {
        viewModelScope.launch {
            storageRepository.cancelCurrentOperation()
        }
    }

    fun clearDetailedStorage() {
        _activePackageName.value = null
        _detailedStorage.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterStatus(status: GameFilterStatus) {
        _filterStatus.value = status
    }

    fun setSortOption(option: GameSortOption) {
        _sortOption.value = option
    }

    fun loadInstalledApps(force: Boolean = false) {
        if (!force && _installedApps.value.isNotEmpty()) return
        viewModelScope.launch {
            val apps = gameRepository.getInstalledApps(context)
            _installedApps.value = apps
            // Background pre-warming of all app icons into memory cache
            app.mountx.ui.components.AppIconManager.prewarmIcons(context, apps.map { it.packageName })
        }
    }

    fun loadStorageBreakdown(packageName: String, force: Boolean = false) {
        _activePackageName.value = packageName
        val cached = _storageBreakdownMap.value[packageName]
        if (cached != null && !force) {
            _detailedStorage.value = cached
            _storageBreakdown.value = Pair(cached.ext1Bytes, cached.ext2Bytes)
            return
        }
        // When switching to a new package without cache, clear detailedStorage to avoid stale cross-game data leak
        _detailedStorage.value = null
        viewModelScope.launch {
            try {
                app.mountx.util.AppLogger.info("GamesVM", "loadStorageBreakdown started for $packageName")
                val sdBase = appPreferences.sdBasePath.first()
                app.mountx.util.AppLogger.info("GamesVM", "sdBase: $sdBase")
                val breakdown = gameRepository.getDetailedStorageBreakdown(context, packageName, sdBase)
                app.mountx.util.AppLogger.info("GamesVM", "breakdown: total=${breakdown.totalBytes}, ext1=${breakdown.ext1Bytes}, ext2=${breakdown.ext2Bytes}, data=${breakdown.ext1DataBytes}")
                _storageBreakdownMap.update { it + (packageName to breakdown) }
                if (_activePackageName.value == packageName) {
                    _detailedStorage.value = breakdown
                    _storageBreakdown.value = Pair(breakdown.ext1Bytes, breakdown.ext2Bytes)
                }
            } catch (e: Exception) {
                app.mountx.util.AppLogger.error("GamesVM", "loadStorageBreakdown failed for $packageName: ${e.message}")
            }
        }
    }

    private val _isRefreshingDetail = MutableStateFlow(false)
    val isRefreshingDetail: StateFlow<Boolean> = _isRefreshingDetail.asStateFlow()

    fun refreshAppDetail(packageName: String, displayName: String) {
        viewModelScope.launch {
            _isRefreshingDetail.value = true
            try {
                val sdBase = appPreferences.sdBasePath.first()
                // 1. Force reload detailed storage breakdown and update Room DB dataSizeBytes in 1 pass
                val breakdown = gameRepository.getDetailedStorageBreakdown(context, packageName, sdBase)
                _storageBreakdownMap.update { it + (packageName to breakdown) }
                if (_activePackageName.value == packageName) {
                    _detailedStorage.value = breakdown
                    _storageBreakdown.value = Pair(breakdown.ext1Bytes, breakdown.ext2Bytes)
                }
                // 2. Rescan candidate directories if candidate list is active
                if (_candidateDirectories.value.isNotEmpty()) {
                    _candidateDirectories.value = gameRepository.scanCandidateDirectories(packageName, displayName, sdBase)
                }
                // 3. Sync mount status without global multi-app rescan
                gameRepository.refreshMountStatuses()
            } catch (e: Exception) {
                app.mountx.util.AppLogger.error("GamesVM", "refreshAppDetail failed for $packageName: ${e.message}")
            } finally {
                _isRefreshingDetail.value = false
            }
        }
    }

    fun updateGameMode(packageName: String, mode: MountMode) {
        viewModelScope.launch {
            gameRepository.updateGameMode(packageName, mode)
        }
    }

    fun addGame(packageName: String, displayName: String, mode: MountMode) {
        viewModelScope.launch {
            gameRepository.addGame(packageName, displayName, mode)
            val sdBase = appPreferences.sdBasePath.first()
            gameRepository.calculateDataSize(packageName, sdBase)
        }
    }

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()
    private val _isUnmounting = MutableStateFlow(false)
    val isUnmounting: StateFlow<Boolean> = _isUnmounting.asStateFlow()
    private val _isPurging = MutableStateFlow(false)
    val isPurging: StateFlow<Boolean> = _isPurging.asStateFlow()
    private val _restoreProgress = MutableStateFlow(0f)
    val restoreProgress: StateFlow<Float> = _restoreProgress.asStateFlow()
    private val _restoreMessage = MutableStateFlow("")
    val restoreMessage: StateFlow<String> = _restoreMessage.asStateFlow()

    fun purgeGamePermanently(
        context: Context,
        packageName: String,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        if (_pendingOperations.value.contains(packageName)) return
        _pendingOperations.update { it + packageName }
        viewModelScope.launch {
            try {
                _isRestoring.value = false
                _isUnmounting.value = false
                _isPurging.value = true
                _restoreProgress.value = 0f
                _restoreMessage.value = context.getString(app.mountx.R.string.dialog_delete_purging_desc)
                val sdBase = appPreferences.sdBasePath.first()
                val result = gameRepository.purgeGamePermanently(
                    context = context,
                    packageName = packageName,
                    sdBase = sdBase
                ) { progress, msg ->
                    _restoreProgress.value = progress
                    _restoreMessage.value = msg
                }
                _isPurging.value = false
                _restoreProgress.value = 0f
                _restoreMessage.value = ""
                _storageBreakdownMap.update { it - packageName }
                refresh()
                scanDiscoveredGames()
                onComplete?.invoke(result.isSuccess, result.exceptionOrNull()?.message)
            } finally {
                _pendingOperations.update { it - packageName }
            }
        }
    }

    fun removeGame(packageName: String) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            gameRepository.removeGame(packageName, sdBase)
            refresh()
        }
    }

    fun removeGameWithOption(
        context: Context,
        packageName: String,
        restoreToInternal: Boolean,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        if (_pendingOperations.value.contains(packageName)) return
        _pendingOperations.update { it + packageName }
        viewModelScope.launch {
            try {
                if (restoreToInternal) {
                    _isRestoring.value = true
                    _isUnmounting.value = false
                } else {
                    _isRestoring.value = false
                    _isUnmounting.value = true
                }
                _restoreProgress.value = 0f
                _restoreMessage.value = ""
                val sdBase = appPreferences.sdBasePath.first()
                val result = gameRepository.removeGameWithOption(
                    context = context,
                    packageName = packageName,
                    restoreToInternal = restoreToInternal,
                    sdBase = sdBase
                ) { progress, msg ->
                    _restoreProgress.value = progress
                    _restoreMessage.value = msg
                }
                _isRestoring.value = false
                _isUnmounting.value = false
                _restoreProgress.value = 0f
                _restoreMessage.value = ""
                _storageBreakdownMap.update { it - packageName }
                refresh()
                onComplete?.invoke(result.isSuccess, result.exceptionOrNull()?.message)
            } finally {
                _pendingOperations.update { it - packageName }
            }
        }
    }

    fun toggleMount(game: GameEntry) {
        if (game.mountStatus == app.mountx.data.model.MountStatus.MOUNTED) {
            unmountGame(game)
        } else {
            mountGame(game)
        }
    }

    fun mountGame(game: GameEntry) {
        if (_pendingOperations.value.contains(game.packageName)) return
        _pendingOperations.update { it + game.packageName }
        viewModelScope.launch {
            try {
                val sdBase = appPreferences.sdBasePath.first()
                gameRepository.mountGame(game, sdBase) { prog ->
                    _operationProgress.value = prog
                    mountNotificationManager.postOperationProgressNotification(prog)
                }
                val breakdown = gameRepository.getDetailedStorageBreakdown(context, game.packageName, sdBase)
                _storageBreakdownMap.update { it + (game.packageName to breakdown) }
                if (_activePackageName.value == game.packageName) {
                    _detailedStorage.value = breakdown
                    _storageBreakdown.value = Pair(breakdown.ext1Bytes, breakdown.ext2Bytes)
                }
                refresh()
            } finally {
                _pendingOperations.update { it - game.packageName }
            }
        }
    }

    fun unmountGame(game: GameEntry) {
        if (_pendingOperations.value.contains(game.packageName)) return
        _pendingOperations.update { it + game.packageName }
        viewModelScope.launch {
            try {
                gameRepository.unmountGame(game) { prog ->
                    _operationProgress.value = prog
                    mountNotificationManager.postOperationProgressNotification(prog)
                }
                val sdBase = appPreferences.sdBasePath.first()
                val breakdown = gameRepository.getDetailedStorageBreakdown(context, game.packageName, sdBase)
                _storageBreakdownMap.update { it + (game.packageName to breakdown) }
                if (_activePackageName.value == game.packageName) {
                    _detailedStorage.value = breakdown
                    _storageBreakdown.value = Pair(breakdown.ext1Bytes, breakdown.ext2Bytes)
                }
                refresh()
            } finally {
                _pendingOperations.update { it - game.packageName }
            }
        }
    }

    fun mountAllGames() {
        app.mountx.service.MountService.startMountAll(context)
        refresh()
    }

    fun unmountAllGames() {
        app.mountx.service.MountService.startUnmountAll(context)
        refresh()
    }

    fun refreshSizes() {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            games.value.forEach { g ->
                gameRepository.calculateDataSize(g.packageName, sdBase)
            }
        }
    }

    fun loadAvailableDisks() {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            val detected = storageRepository.detectPartitions(sdBase)
            _availableDisks.value = storageRepository.getAllDisks(sdBase, detected)
        }
    }

    fun refreshDisks() {
        viewModelScope.launch {
            _isScanningDisks.value = true
            try {
                val sdBase = appPreferences.sdBasePath.first()
                val detected = storageRepository.detectPartitions(sdBase)
                _availableDisks.value = storageRepository.getAllDisks(sdBase, detected)
            } finally {
                _isScanningDisks.value = false
            }
        }
    }

    fun quickMountPartition(partition: app.mountx.data.model.PartitionInfo) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            storageRepository.mountPartition(partition, sdBase)
            val detected = storageRepository.detectPartitions(sdBase)
            _availableDisks.value = storageRepository.getAllDisks(sdBase, detected)
        }
    }

    fun quickMountDisk(disk: app.mountx.data.model.SdCardDiskInfo) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            storageRepository.mountAllPartitions(disk, sdBase)
            val detected = storageRepository.detectPartitions(sdBase)
            _availableDisks.value = storageRepository.getAllDisks(sdBase, detected)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val sdBase = appPreferences.sdBasePath.first()
                loadAvailableDisks()
                gameRepository.refreshMountStatuses()
                gameRepository.calculateDataSizesBatch(games.value, sdBase)
                scanDiscoveredGames()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    suspend fun isGameRunning(packageName: String): Boolean {
        return appRepository.isGameRunning(packageName)
    }

    fun moveMountPoints(
        packageName: String,
        mountPoints: List<app.mountx.data.model.MountPointConfig>,
        direction: MoveDirection,
        targetDiskBase: String? = null,
        conflictStrategy: ConflictStrategy? = null
    ) {
        if (_pendingOperations.value.contains(packageName)) return
        _pendingOperations.update { it + packageName }
        viewModelScope.launch {
            try {
                _isMovingData.value = true
                _moveMessage.value = null
                val defaultSdBase = appPreferences.sdBasePath.first()
                val sdBase = targetDiskBase ?: defaultSdBase
                val effectiveConflictStrategy = conflictStrategy ?: appPreferences.conflictStrategy.first()
                val game = games.value.firstOrNull { it.packageName == packageName }
                val rawPoints = if (mountPoints.isNotEmpty()) mountPoints else {
                    game?.let { gameRepository.synthesizeLegacyMountPoints(it, sdBase) } ?: emptyList()
                }
                val effectivePoints = if (direction == MoveDirection.TO_SD && rawPoints.none { it.enabled }) {
                    rawPoints.map { it.copy(enabled = true) }
                } else {
                    rawPoints
                }

                // Force stop active process before migration
                RootShell.exec("am force-stop \"$packageName\" 2>/dev/null")

                // Always unmount from runtime namespaces first before moving data in either direction
                // to avoid circular reading/writing across bind mounts
                if (game != null && game.mountStatus == MountStatus.MOUNTED) {
                    gameRepository.unmountGame(game) { prog ->
                        _operationProgress.value = prog
                    }
                }

                val result = storageRepository.moveGameMountPoints(
                    packageName = packageName,
                    mountPoints = effectivePoints,
                    direction = direction,
                    sdBase = sdBase,
                    conflictStrategy = effectiveConflictStrategy,
                    onProgress = { prog ->
                        _operationProgress.value = prog
                        mountNotificationManager.postOperationProgressNotification(prog)
                    }
                )
                _isMovingData.value = false
                if (result.isSuccess) {
                    _moveMessage.value = "SUCCESS"

                    if (game != null) {
                        if (direction == MoveDirection.TO_INTERNAL) {
                            // Mark moved points as disabled, update game status to UNMOUNTED
                            val currentPoints = if (game.mountPoints.isNotEmpty()) game.mountPoints else effectivePoints
                            val updatedPoints = currentPoints.map { pt ->
                                if (effectivePoints.any { it.isSameTargetAs(pt) }) {
                                    pt.copy(enabled = false)
                                } else {
                                    pt
                                }
                            }
                            val allDisabled = updatedPoints.none { it.enabled }
                            val updated = game.copy(
                                mountPoints = updatedPoints,
                                mountStatus = MountStatus.UNMOUNTED,
                                isEnabled = if (allDisabled) false else game.isEnabled
                            )
                            gameRepository.updateGame(updated)
                        } else {
                            // TO_SD
                            val currentPoints = if (game.mountPoints.isNotEmpty()) game.mountPoints else effectivePoints
                            val existingUpdated = currentPoints.map { pt ->
                                val moved = effectivePoints.firstOrNull { it.isSameTargetAs(pt) }
                                if (moved != null) moved.copy(enabled = true) else pt
                            }
                            val missingPoints = effectivePoints.filter { ep ->
                                existingUpdated.none { it.isSameTargetAs(ep) }
                            }.map { it.copy(enabled = true) }
                            val finalPoints = existingUpdated + missingPoints
                            val updated = game.copy(
                                mountPoints = if (finalPoints.isNotEmpty()) finalPoints else effectivePoints.map { it.copy(enabled = true) },
                                mountStatus = MountStatus.MOUNTED,
                                isEnabled = true
                            )
                            gameRepository.updateGame(updated)
                            gameRepository.mountGame(updated, sdBase) { prog ->
                                _operationProgress.value = prog
                                mountNotificationManager.postOperationProgressNotification(prog)
                            }
                        }
                    }

                    gameRepository.calculateDataSize(packageName, sdBase)
                    gameRepository.syncDiskCatalog(sdBase)
                    val breakdown = gameRepository.getDetailedStorageBreakdown(context, packageName, sdBase)
                    _storageBreakdownMap.update { it + (packageName to breakdown) }
                    if (_activePackageName.value == packageName) {
                        _detailedStorage.value = breakdown
                        _storageBreakdown.value = Pair(breakdown.ext1Bytes, breakdown.ext2Bytes)
                    }
                } else {
                    _moveMessage.value = result.exceptionOrNull()?.message ?: "Move failed"
                }
            } finally {
                _isMovingData.value = false
                _pendingOperations.update { it - packageName }
            }
        }
    }

    fun directMount(
        packageName: String,
        points: List<app.mountx.data.model.MountPointConfig>,
        targetBase: String
    ) {
        if (_pendingOperations.value.contains(packageName)) return
        _pendingOperations.update { it + packageName }
        viewModelScope.launch {
            try {
                val game = games.value.firstOrNull { it.packageName == packageName } ?: return@launch
                _isMovingData.value = true
                val initialProgress = OperationProgress(
                    type = OperationType.MOUNT,
                    title = "Kaitkan Langsung",
                    subtitle = "${game.displayName} • ${points.size} Kategori",
                    progressPercent = 0.1f
                )
                _operationProgress.value = initialProgress
                mountNotificationManager.postOperationProgressNotification(initialProgress)

                val result = gameRepository.directMountGame(game, points, targetBase) { prog ->
                    _operationProgress.value = prog
                    mountNotificationManager.postOperationProgressNotification(prog)
                }

                _isMovingData.value = false
                if (result.isSuccess) {
                    _moveMessage.value = "SUCCESS"
                } else {
                    _moveMessage.value = result.exceptionOrNull()?.message ?: "Direct mount failed"
                }

                val breakdown = gameRepository.getDetailedStorageBreakdown(context, packageName, targetBase)
                _storageBreakdownMap.update { it + (packageName to breakdown) }
                if (_activePackageName.value == packageName) {
                    _detailedStorage.value = breakdown
                    _storageBreakdown.value = Pair(breakdown.ext1Bytes, breakdown.ext2Bytes)
                }
            } finally {
                _isMovingData.value = false
                _pendingOperations.update { it - packageName }
            }
        }
    }

    fun unmountMountPoints(
        packageName: String,
        points: List<app.mountx.data.model.MountPointConfig>
    ) {
        if (_pendingOperations.value.contains(packageName)) return
        _pendingOperations.update { it + packageName }
        viewModelScope.launch {
            try {
                val game = games.value.firstOrNull { it.packageName == packageName } ?: return@launch
                val initialProgress = OperationProgress(
                    type = OperationType.UNMOUNT,
                    title = "Melepaskan Kaitan Kategori",
                    subtitle = "${game.displayName} • ${points.size} Kategori",
                    progressPercent = 0.2f
                )
                _operationProgress.value = initialProgress
                mountNotificationManager.postOperationProgressNotification(initialProgress)

                val result = gameRepository.unmountMountPoints(game, points) { prog ->
                    _operationProgress.value = prog
                    mountNotificationManager.postOperationProgressNotification(prog)
                }

                val defaultSdBase = appPreferences.sdBasePath.first()
                val breakdown = gameRepository.getDetailedStorageBreakdown(context, packageName, defaultSdBase)
                _storageBreakdownMap.update { it + (packageName to breakdown) }
                if (_activePackageName.value == packageName) {
                    _detailedStorage.value = breakdown
                    _storageBreakdown.value = Pair(breakdown.ext1Bytes, breakdown.ext2Bytes)
                }
            } finally {
                _pendingOperations.update { it - packageName }
            }
        }
    }

    fun updateMountPoints(packageName: String, mountPoints: List<app.mountx.data.model.MountPointConfig>) {
        viewModelScope.launch {
            val game = games.value.firstOrNull { it.packageName == packageName } ?: return@launch
            val updated = game.copy(mountPoints = mountPoints)
            gameRepository.updateGame(updated)
        }
    }

    fun moveData(
        packageName: String,
        direction: MoveDirection,
        target: MigrationTarget = MigrationTarget.ALL,
        conflictStrategy: ConflictStrategy? = null
    ) {
        viewModelScope.launch {
            _isMovingData.value = true
            _moveMessage.value = null
            val sdBase = appPreferences.sdBasePath.first()
            val effectiveConflictStrategy = conflictStrategy ?: appPreferences.conflictStrategy.first()
            val game = games.value.firstOrNull { it.packageName == packageName }

            // Force stop active process before migration
            RootShell.exec("am force-stop \"$packageName\" 2>/dev/null")

            // Always unmount from runtime namespaces first before moving data in either direction
            if (game != null && game.mountStatus == MountStatus.MOUNTED) {
                gameRepository.unmountGame(game) { prog ->
                    _operationProgress.value = prog
                }
            }

            val result = storageRepository.moveGameData(
                packageName = packageName,
                direction = direction,
                target = target,
                sdBase = sdBase,
                conflictStrategy = effectiveConflictStrategy,
                onProgress = { prog ->
                    _operationProgress.value = prog
                    mountNotificationManager.postOperationProgressNotification(prog)
                }
            )
            _isMovingData.value = false
            if (result.isSuccess) {
                _moveMessage.value = "SUCCESS"

                if (game != null) {
                    if (direction == MoveDirection.TO_INTERNAL) {
                        val updated = game.copy(mountStatus = MountStatus.UNMOUNTED, isEnabled = false)
                        gameRepository.updateGame(updated)
                    } else {
                        val updated = game.copy(mountStatus = MountStatus.MOUNTED, isEnabled = true)
                        gameRepository.updateGame(updated)
                        gameRepository.mountGame(updated, sdBase) { prog ->
                            _operationProgress.value = prog
                        }
                    }
                }

                gameRepository.calculateDataSize(packageName, sdBase)
                gameRepository.syncDiskCatalog(sdBase)
                val breakdown = gameRepository.getDetailedStorageBreakdown(context, packageName, sdBase)
                _storageBreakdownMap.update { it + (packageName to breakdown) }
                if (_activePackageName.value == packageName) {
                    _detailedStorage.value = breakdown
                    _storageBreakdown.value = Pair(breakdown.ext1Bytes, breakdown.ext2Bytes)
                }
            } else {
                _moveMessage.value = result.exceptionOrNull()?.message ?: "Move failed"
            }
        }
    }

    fun clearMoveMessage() {
        _moveMessage.value = null
    }

    fun ensureDiscoveredGamesLoaded() {
        if (_discoveredGames.value.isEmpty() && !_isScanningDiscovered.value) {
            scanDiscoveredGames()
        }
    }

    fun scanDiscoveredGames() {
        viewModelScope.launch {
            _isScanningDiscovered.value = true
            val sdBase = appPreferences.sdBasePath.first()
            if (_installedApps.value.isEmpty()) {
                val apps = gameRepository.getInstalledApps(context)
                _installedApps.value = apps
            }
            val installedMap = _installedApps.value.associate { it.packageName to it.displayName }
            val discovered = gameRepository.scanMicroSdGames(sdBase, installedMap)
            _discoveredGames.value = discovered.filter { !it.isAlreadyRegistered && (it.hasDataOnSd || it.hasObbOnSd) }
            _isScanningDiscovered.value = false
        }
    }

    fun importDiscoveredGame(game: DiscoveredGame) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            gameRepository.importDiscoveredGame(game, sdBase)
            scanDiscoveredGames()
        }
    }

    fun importAllDiscoveredGames() {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            for (g in _discoveredGames.value) {
                gameRepository.importDiscoveredGame(g, sdBase)
            }
            scanDiscoveredGames()
        }
    }

    fun dismissDiscovered() {
        _discoveredGames.value = emptyList()
    }

    fun deleteDiscoveredGame(game: DiscoveredGame) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            gameRepository.deleteDiscoveredGame(game, sdBase)
            scanDiscoveredGames()
        }
    }

    fun deleteAllOrphanGames(orphans: List<DiscoveredGame>, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            for (g in orphans) {
                gameRepository.deleteDiscoveredGame(g, sdBase)
            }
            scanDiscoveredGames()
            onFinished()
        }
    }

    fun restructureAllGames(games: List<DiscoveredGame>, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            _isRestructuring.value = true
            val sdBase = appPreferences.sdBasePath.first()
            for ((index, g) in games.withIndex()) {
                val prefix = "[${index + 1}/${games.size}] "
                gameRepository.restructureGame(g, sdBase) { _, msg ->
                    _restructureProgressMessage.value = "$prefix$msg"
                }
            }
            _restructureProgressMessage.value = null
            _isRestructuring.value = false
            scanDiscoveredGames()
            onFinished()
        }
    }

    fun scanCandidates(packageName: String, displayName: String) {
        viewModelScope.launch {
            _isScanningCandidates.value = true
            val sdBase = appPreferences.sdBasePath.first()
            _candidateDirectories.value = gameRepository.scanCandidateDirectories(packageName, displayName, sdBase)
            _isScanningCandidates.value = false
        }
    }

    fun addGameWithMountPoints(
        packageName: String,
        displayName: String,
        mountPoints: List<app.mountx.data.model.MountPointConfig>,
        initialSizeBytes: Long
    ) {
        viewModelScope.launch {
            gameRepository.addGame(
                packageName = packageName,
                displayName = displayName,
                mode = MountMode.PKG,
                mountPoints = mountPoints,
                initialSizeBytes = initialSizeBytes
            )
            refresh()
        }
    }

    fun deleteCategoryData(
        packageName: String,
        categoryId: String,
        location: app.mountx.data.model.CategoryDeleteLocation,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            val res = gameRepository.deleteCategoryData(context, packageName, categoryId, location, sdBase)
            if (res.isSuccess) {
                val breakdown = gameRepository.getDetailedStorageBreakdown(context, packageName, sdBase)
                _storageBreakdownMap.update { it + (packageName to breakdown) }
                if (_activePackageName.value == packageName) {
                    _detailedStorage.value = breakdown
                    _storageBreakdown.value = Pair(breakdown.ext1Bytes, breakdown.ext2Bytes)
                }
                refresh()
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }
}

typealias GamesViewModel = AppsViewModel
typealias AppFilterStatus = GameFilterStatus
typealias AppSortOption = GameSortOption

