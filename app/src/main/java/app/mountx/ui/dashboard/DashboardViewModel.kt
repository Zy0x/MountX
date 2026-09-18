package app.mountx.ui.dashboard

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
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val storageRepository: StorageRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun mountAll() {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            val blockDev = appPreferences.sdBlockDevice.first()
            storageRepository.mountSdPartition(blockDev, sdBase)
            gameRepository.mountAll(sdBase)
            refresh()
        }
    }

    fun unmountAll() {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            gameRepository.unmountAll(sdBase)
            refresh()
        }
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
