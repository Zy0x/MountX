package app.mountify.ui.games

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountify.data.model.AppStorageBreakdown
import app.mountify.data.model.GameEntry
import app.mountify.data.model.InstalledAppInfo
import app.mountify.data.model.MigrationTarget
import app.mountify.data.model.MountMode
import app.mountify.data.model.MountStatus
import app.mountify.data.model.MoveDirection
import app.mountify.data.repository.GameRepository
import app.mountify.data.repository.StorageRepository
import app.mountify.util.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
class GamesViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val storageRepository: StorageRepository,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val games: StateFlow<List<GameEntry>> = gameRepository.observeGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _detailedStorage = MutableStateFlow(AppStorageBreakdown())
    val detailedStorage: StateFlow<AppStorageBreakdown> = _detailedStorage.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterStatus(status: GameFilterStatus) {
        _filterStatus.value = status
    }

    fun setSortOption(option: GameSortOption) {
        _sortOption.value = option
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = gameRepository.getInstalledApps(context)
        }
    }

    fun loadStorageBreakdown(packageName: String) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            _storageBreakdown.value = gameRepository.getInternalAndSdSizes(packageName, sdBase)
            _detailedStorage.value = gameRepository.getDetailedStorageBreakdown(context, packageName, sdBase)
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

    fun removeGame(packageName: String) {
        viewModelScope.launch {
            gameRepository.removeGame(packageName)
        }
    }

    fun toggleMount(game: GameEntry) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            if (game.mountStatus == app.mountify.data.model.MountStatus.MOUNTED) {
                gameRepository.unmountGame(game)
            } else {
                gameRepository.mountGame(game, sdBase)
            }
        }
    }

    fun mountAllGames() {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            gameRepository.mountAll(sdBase)
        }
    }

    fun unmountAllGames() {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            gameRepository.unmountAll(sdBase)
        }
    }

    fun refreshSizes() {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            games.value.forEach { g ->
                gameRepository.calculateDataSize(g.packageName, sdBase)
            }
        }
    }

    fun moveData(
        packageName: String,
        direction: MoveDirection,
        target: MigrationTarget = MigrationTarget.ALL
    ) {
        viewModelScope.launch {
            _isMovingData.value = true
            _moveMessage.value = null
            val sdBase = appPreferences.sdBasePath.first()
            val game = games.value.firstOrNull { it.packageName == packageName }

            // If restoring to internal, unmount from runtime namespaces first
            if (direction == MoveDirection.TO_INTERNAL && game != null && game.mountStatus == MountStatus.MOUNTED) {
                gameRepository.unmountGame(game)
            }

            val result = storageRepository.moveGameData(packageName, direction, target, sdBase)
            _isMovingData.value = false
            if (result.isSuccess) {
                _moveMessage.value = "SUCCESS"

                // If moved to SD card, auto-mount immediately to Android runtime namespaces
                if (direction == MoveDirection.TO_SD && game != null) {
                    gameRepository.mountGame(game, sdBase)
                }

                gameRepository.calculateDataSize(packageName, sdBase)
                _storageBreakdown.value = gameRepository.getInternalAndSdSizes(packageName, sdBase)
                _detailedStorage.value = gameRepository.getDetailedStorageBreakdown(context, packageName, sdBase)
            } else {
                _moveMessage.value = result.exceptionOrNull()?.message ?: "Move failed"
            }
        }
    }

    fun clearMoveMessage() {
        _moveMessage.value = null
    }
}
