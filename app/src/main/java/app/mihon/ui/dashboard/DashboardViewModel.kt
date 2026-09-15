package app.mihon.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mihon.data.model.AppStatus
import app.mihon.data.model.GameEntry
import app.mihon.data.model.MountStatus
import app.mihon.data.model.RootSolution
import app.mihon.data.repository.GameRepository
import app.mihon.data.repository.StorageRepository
import app.mihon.root.RootDetector
import app.mihon.root.RootShell
import app.mihon.util.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    val games: StateFlow<List<GameEntry>> = gameRepository.observeGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                _rootSolution.value = RootDetector.detectRootSolution()
                _isModuleInstalled.value = RootDetector.isModuleInstalled()
                _moduleVersion.value = RootDetector.getModuleVersion()
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
}
