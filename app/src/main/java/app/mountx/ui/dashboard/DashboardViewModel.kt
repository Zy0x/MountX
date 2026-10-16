package app.mountx.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountx.data.model.AppStatus
import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.MountStatus
import app.mountx.data.model.OperationProgress
import app.mountx.data.model.OperationType
import app.mountx.data.model.RootSolution
import app.mountx.data.repository.GameRepository
import app.mountx.data.repository.StorageRepository
import app.mountx.root.RootDetector
import app.mountx.root.RootShell
import app.mountx.util.AppPreferences
import app.mountx.root.MountManager
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveNamespaceTelemetry(
    val isMasterNamespaceActive: Boolean = true,
    val kernelMountPoints: List<String> = emptyList(),
    val mountedGamesCount: Int = 0,
    val canaryVerifiedCount: Int = 0,
    val totalCanariesExpected: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val storageRepository: StorageRepository,
    private val appPreferences: AppPreferences,
    private val mountManager: MountManager,
    private val systemSyncMonitor: app.mountx.service.SystemSyncMonitor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _operationProgress = MutableStateFlow<OperationProgress?>(null)
    val operationProgress: StateFlow<OperationProgress?> = _operationProgress.asStateFlow()

    fun clearOperationProgress() {
        _operationProgress.value = null
    }

    private val _pendingOperations = MutableStateFlow<Set<String>>(emptySet())
    val pendingOperations: StateFlow<Set<String>> = _pendingOperations.asStateFlow()

    fun cancelOperation() {
        viewModelScope.launch {
            storageRepository.cancelCurrentOperation()
        }
    }

    private val _liveTelemetry = MutableStateFlow(LiveNamespaceTelemetry())
    val liveTelemetry: StateFlow<LiveNamespaceTelemetry> = _liveTelemetry.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isRecalculating = MutableStateFlow(false)
    val isRecalculating: StateFlow<Boolean> = _isRecalculating.asStateFlow()

    private val _isRefreshingTelemetry = MutableStateFlow(false)
    val isRefreshingTelemetry: StateFlow<Boolean> = _isRefreshingTelemetry.asStateFlow()

    private val _rootSolution = MutableStateFlow(RootSolution.NONE)
    val rootSolution: StateFlow<RootSolution> = _rootSolution.asStateFlow()

    private val _isModuleInstalled = MutableStateFlow(false)
    val isModuleInstalled: StateFlow<Boolean> = _isModuleInstalled.asStateFlow()

    private val _moduleVersion = MutableStateFlow("")
    val moduleVersion: StateFlow<String> = _moduleVersion.asStateFlow()

    private val _allDisks = MutableStateFlow<List<app.mountx.data.model.SdCardDiskInfo>>(emptyList())
    val allDisks: StateFlow<List<app.mountx.data.model.SdCardDiskInfo>> = _allDisks.asStateFlow()

    private val _diskHealthStatus = MutableStateFlow(app.mountx.data.model.DiskHealthStatus.HEALTHY)
    val diskHealthStatus: StateFlow<app.mountx.data.model.DiskHealthStatus> = _diskHealthStatus.asStateFlow()

    private val _diskErrorDetails = MutableStateFlow<String?>(null)
    val diskErrorDetails: StateFlow<String?> = _diskErrorDetails.asStateFlow()

    val internalStorageInfo: StateFlow<app.mountx.data.model.InternalStorageInfo?> = storageRepository.observeInternalStorage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val games: StateFlow<List<GameEntry>> = gameRepository.observeGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offloadedStats: StateFlow<Pair<Int, Long>> = games.map { list ->
        val mountedGames = list.filter { it.mountStatus == MountStatus.MOUNTED }
        Pair(mountedGames.size, mountedGames.sumOf { it.dataSizeBytes })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0L))

    val storageInfo = storageRepository.observeStorageInfo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val appStatus: StateFlow<AppStatus> = combine(
        _rootSolution,
        _isModuleInstalled,
        _moduleVersion,
        storageInfo,
        games
    ) { root, module, ver, storage, gameList ->
        AppStatus(
            rootSolution = root,
            isModuleInstalled = module,
            moduleVersion = ver,
            storageInfo = storage,
            mountedGamesCount = gameList.count { it.mountStatus == MountStatus.MOUNTED },
            totalGamesCount = gameList.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppStatus())

    init {
        refresh()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                games.first { it.isNotEmpty() }
                val sdBase = appPreferences.sdBasePath.first()
                gameRepository.calculateDataSizesBatch(games.value, sdBase)
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            systemSyncMonitor.events.collect { event ->
                when (event) {
                    is app.mountx.service.SystemSyncEvent.DiskError -> {
                        _diskHealthStatus.value = app.mountx.data.model.DiskHealthStatus.ERROR_IO
                        _diskErrorDetails.value = event.details
                    }
                    is app.mountx.service.SystemSyncEvent.StorageDisconnected -> {
                        _diskHealthStatus.value = app.mountx.data.model.DiskHealthStatus.DISCONNECTED
                        _diskErrorDetails.value = context.getString(app.mountx.R.string.dashboard_disk_detached_desc)
                    }
                    is app.mountx.service.SystemSyncEvent.DiskHealthRestored -> {
                        _diskHealthStatus.value = app.mountx.data.model.DiskHealthStatus.HEALTHY
                        _diskErrorDetails.value = null
                    }
                    else -> {}
                }
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val info = RootDetector.getRootAndModuleInfo(forceRefresh = true)
                _rootSolution.value = info.rootSolution
                _isModuleInstalled.value = info.isModuleInstalled
                _moduleVersion.value = info.moduleVersion

                // Refresh game mount statuses first to detect detached storage
                gameRepository.refreshMountStatuses()

                val sdBase = appPreferences.sdBasePath.first()
                val disks = storageRepository.getAllDisks(sdBase)
                _allDisks.value = disks

                val worstHealth = disks.map { it.effectiveHealth }.firstOrNull { it != app.mountx.data.model.DiskHealthStatus.HEALTHY }
                val hasDiskErrorFile = RootShell.exists("/dev/.mountx_disk_error")
                val currentGames = games.value
                val hasDetachedGames = currentGames.any { it.mountStatus == MountStatus.DISK_DETACHED }
                val hasConfiguredStorage = currentGames.isNotEmpty() || RootShell.isMountpoint(sdBase)

                if (hasDiskErrorFile || worstHealth == app.mountx.data.model.DiskHealthStatus.ERROR_IO) {
                    _diskHealthStatus.value = app.mountx.data.model.DiskHealthStatus.ERROR_IO
                    _diskErrorDetails.value = disks.firstOrNull { it.healthErrorDetails != null }?.healthErrorDetails ?: "Hardware I/O error"
                } else if (disks.isEmpty() && (hasDetachedGames || hasConfiguredStorage)) {
                    _diskHealthStatus.value = app.mountx.data.model.DiskHealthStatus.DISCONNECTED
                    _diskErrorDetails.value = context.getString(app.mountx.R.string.dashboard_disk_detached_desc)
                } else if (worstHealth != null) {
                    _diskHealthStatus.value = worstHealth
                    _diskErrorDetails.value = disks.firstOrNull { it.healthErrorDetails != null }?.healthErrorDetails
                } else {
                    _diskHealthStatus.value = app.mountx.data.model.DiskHealthStatus.HEALTHY
                    _diskErrorDetails.value = null
                }

                // Background calculate game sizes so real storage is displayed accurately
                launch(Dispatchers.IO) {
                    val current = games.value
                    gameRepository.calculateDataSizesBatch(current, sdBase)
                }
                loadLiveTelemetry()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private var lastRecordedUsedBytes: Long = -1L
    private var lastForegroundResumeTime: Long = 0L

    fun onAppForegroundResume() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundResumeTime < 15_000L) return
        lastForegroundResumeTime = now
        storageRepository.refreshStorage()
        viewModelScope.launch(Dispatchers.IO) {
            val sdBase = appPreferences.sdBasePath.first()
            val current = games.value
            gameRepository.calculateDataSizesBatch(current, sdBase)
            loadLiveTelemetry()
        }
    }

    fun refreshStorageQuick() {
        storageRepository.refreshStorage()
        viewModelScope.launch(Dispatchers.IO) {
            val sdBase = appPreferences.sdBasePath.first()
            val current = games.value
            gameRepository.calculateDataSizesBatch(current, sdBase)
        }
    }

    private var livePulseJob: kotlinx.coroutines.Job? = null

    fun startLiveStoragePulse() {
        if (livePulseJob?.isActive == true) return
        livePulseJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15_000L) // 15-second gentle pulse
                try {
                    val sdBase = appPreferences.sdBasePath.first()
                    val currentStorage = storageRepository.getStorageInfo(sdBase)
                    if (currentStorage != null && currentStorage.isMounted) {
                        if (lastRecordedUsedBytes > 0L && Math.abs(currentStorage.usedBytes - lastRecordedUsedBytes) > 5 * 1024 * 1024L) {
                            // Storage capacity changed (> 5 MB, e.g. game downloaded assets)
                            storageRepository.refreshStorage()
                            val mountedGames = games.value.filter { it.mountStatus == MountStatus.MOUNTED }
                            gameRepository.calculateDataSizesBatch(mountedGames, sdBase)
                        }
                        lastRecordedUsedBytes = currentStorage.usedBytes
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun stopLiveStoragePulse() {
        livePulseJob?.cancel()
        livePulseJob = null
    }

    fun loadLiveTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingTelemetry.value = true
            try {
                val sdBase = appPreferences.sdBasePath.first()
                val mountsRes = RootShell.exec("cat /proc/mounts 2>/dev/null")
                val mountLines = if (mountsRes.isSuccess) {
                    mountsRes.stdout.filter { line ->
                        line.isNotBlank() && (
                            line.contains("/data/media/0/Android") ||
                            line.contains("/data/sdext2") ||
                            (sdBase.isNotBlank() && line.contains(sdBase))
                        )
                    }.map { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        parts.take(3).joinToString(" ")
                    }
                } else emptyList()

                val currentGames = games.value
                val mountedGames = currentGames.filter { it.mountStatus == MountStatus.MOUNTED }
                var canariesVerified = 0
                for (g in mountedGames) {
                    if (mountManager.verifyCanary(g.packageName)) {
                        canariesVerified++
                    }
                }

                _liveTelemetry.value = LiveNamespaceTelemetry(
                    isMasterNamespaceActive = Shell.isAppGrantedRoot() == true,
                    kernelMountPoints = mountLines,
                    mountedGamesCount = mountedGames.size,
                    canaryVerifiedCount = canariesVerified,
                    totalCanariesExpected = mountedGames.size
                )
            } finally {
                _isRefreshingTelemetry.value = false
            }
        }
    }

    fun recalculateAllSizes() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRecalculating.value = true
            try {
                val sdBase = appPreferences.sdBasePath.first()
                val currentGames = games.value
                gameRepository.calculateDataSizesBatch(currentGames, sdBase)
                refresh()
            } finally {
                _isRecalculating.value = false
            }
        }
    }

    fun mountAll() {
        app.mountx.service.MountService.startMountAll(context)
        refresh()
    }

    fun unmountAll() {
        app.mountx.service.MountService.startUnmountAll(context)
        refresh()
    }

    fun toggleMount(game: GameEntry) {
        if (_pendingOperations.value.contains(game.packageName)) return
        _pendingOperations.update { it + game.packageName }
        viewModelScope.launch {
            try {
                val sdBase = appPreferences.sdBasePath.first()
                if (game.mountStatus == MountStatus.MOUNTED) {
                    gameRepository.unmountGame(game) { prog ->
                        _operationProgress.value = prog
                    }
                } else {
                    gameRepository.mountGame(game, sdBase) { prog ->
                        _operationProgress.value = prog
                    }
                }
                refresh()
            } finally {
                _pendingOperations.update { it - game.packageName }
            }
        }
    }

    fun migrateGame(game: GameEntry) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            val rawPoints = if (game.mountPoints.isNotEmpty()) game.mountPoints else gameRepository.synthesizeLegacyMountPoints(game, sdBase)
            val points = if (rawPoints.none { it.enabled }) rawPoints.map { it.copy(enabled = true) } else rawPoints
            migrateGameWithOptions(game, points, sdBase)
        }
    }

    fun migrateGameWithOptions(game: GameEntry, selectedPoints: List<MountPointConfig>, targetBase: String) {
        if (_pendingOperations.value.contains(game.packageName)) return
        _pendingOperations.update { it + game.packageName }
        viewModelScope.launch {
            try {
                val activePoints = selectedPoints.map { it.copy(enabled = true) }

                // Force stop active process before migration
                RootShell.exec("am force-stop \"${game.packageName}\" 2>/dev/null")

                // Unmount if currently mounted to prevent cyclic file locks
                if (game.mountStatus == MountStatus.MOUNTED) {
                    gameRepository.unmountGame(game) { prog ->
                        _operationProgress.value = prog
                    }
                }

                val result = storageRepository.moveGameMountPoints(
                    packageName = game.packageName,
                    mountPoints = activePoints,
                    direction = app.mountx.data.model.MoveDirection.TO_SD,
                    sdBase = targetBase,
                    onProgress = { prog ->
                        _operationProgress.value = prog
                    }
                )
                if (result.isSuccess) {
                    val currentPoints = if (game.mountPoints.isNotEmpty()) game.mountPoints else activePoints
                    val existingUpdated = currentPoints.map { pt ->
                        val moved = activePoints.firstOrNull { it.id == pt.id || it.category == pt.category }
                        if (moved != null) moved.copy(enabled = true) else pt
                    }
                    val missing = activePoints.filter { ap ->
                        existingUpdated.none { it.id == ap.id || it.category == ap.category }
                    }
                    val updated = game.copy(
                        mountPoints = existingUpdated + missing,
                        mountStatus = MountStatus.MOUNTED,
                        isEnabled = true
                    )
                    gameRepository.updateGame(updated)
                    gameRepository.mountGame(updated, targetBase) { prog ->
                        _operationProgress.value = prog
                    }
                }
                refresh()
            } finally {
                _pendingOperations.update { it - game.packageName }
            }
        }
    }

    fun directMountWithOptions(game: GameEntry, selectedPoints: List<MountPointConfig>, targetBase: String) {
        if (_pendingOperations.value.contains(game.packageName)) return
        _pendingOperations.update { it + game.packageName }
        viewModelScope.launch {
            try {
                val activePoints = selectedPoints.map { it.copy(enabled = true) }
                _operationProgress.value = OperationProgress(
                    type = OperationType.MOUNT,
                    title = "Kaitkan Langsung",
                    subtitle = "${game.displayName} • ${activePoints.size} Kategori",
                    progressPercent = 0.1f
                )
                gameRepository.directMountGame(game, activePoints, targetBase) { prog ->
                    _operationProgress.value = prog
                }
                refresh()
            } finally {
                _pendingOperations.update { it - game.packageName }
            }
        }
    }
}
