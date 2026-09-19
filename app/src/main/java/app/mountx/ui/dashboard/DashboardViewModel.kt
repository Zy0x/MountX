package app.mountx.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountx.data.model.AppStatus
import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountStatus
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        viewModelScope.launch {
            systemSyncMonitor.events.collect {
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
                val sdBase = appPreferences.sdBasePath.first()
                _allDisks.value = storageRepository.getAllDisks(sdBase)
                gameRepository.refreshMountStatuses()

                // Background calculate zero-size games so real storage is displayed
                launch(Dispatchers.IO) {
                    val current = games.value
                    for (g in current) {
                        if (g.dataSizeBytes == 0L) {
                            gameRepository.calculateDataSize(g.packageName, sdBase)
                        }
                    }
                }
                loadLiveTelemetry()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadLiveTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingTelemetry.value = true
            try {
                val sdBase = appPreferences.sdBasePath.first()
                val mountsRes = RootShell.exec("cat /proc/mounts 2>/dev/null | grep -E '(/data/media/0/Android|/data/sdext2|${sdBase})' | cut -d' ' -f1,2,3")
                val mountLines = if (mountsRes.isSuccess) mountsRes.stdout.filter { it.isNotBlank() } else emptyList()

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
                for (g in currentGames) {
                    gameRepository.calculateDataSize(g.packageName, sdBase)
                }
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
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            if (game.mountStatus == MountStatus.MOUNTED) {
                gameRepository.unmountGame(game)
            } else {
                gameRepository.mountGame(game, sdBase)
            }
            refresh()
        }
    }
}
