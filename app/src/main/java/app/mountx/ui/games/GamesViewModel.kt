package app.mountx.ui.games

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mountx.data.catalog.DiscoveredGame
import app.mountx.data.model.AppStorageBreakdown
import app.mountx.data.model.GameEntry
import app.mountx.data.model.InstalledAppInfo
import app.mountx.data.model.MigrationTarget
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountStatus
import app.mountx.data.model.MoveDirection
import app.mountx.data.repository.GameRepository
import app.mountx.data.repository.StorageRepository
import app.mountx.util.AppPreferences
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

    private val _discoveredGames = MutableStateFlow<List<DiscoveredGame>>(emptyList())
    val discoveredGames: StateFlow<List<DiscoveredGame>> = _discoveredGames.asStateFlow()

    private val _isScanningDiscovered = MutableStateFlow(false)
    val isScanningDiscovered: StateFlow<Boolean> = _isScanningDiscovered.asStateFlow()

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

    fun loadInstalledApps(force: Boolean = false) {
        if (!force && _installedApps.value.isNotEmpty()) return
        viewModelScope.launch {
            val apps = gameRepository.getInstalledApps(context)
            _installedApps.value = apps
            // Background pre-warming of all app icons into memory cache
            app.mountx.ui.components.AppIconManager.prewarmIcons(context, apps.map { it.packageName })
        }
    }

    fun loadStorageBreakdown(packageName: String) {
        viewModelScope.launch {
            val sdBase = appPreferences.sdBasePath.first()
            val breakdown = gameRepository.getDetailedStorageBreakdown(context, packageName, sdBase)
            _detailedStorage.value = breakdown
            _storageBreakdown.value = Pair(breakdown.ext1Bytes, breakdown.ext2Bytes)
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
            if (game.mountStatus == app.mountx.data.model.MountStatus.MOUNTED) {
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
                val breakdown = gameRepository.getDetailedStorageBreakdown(context, packageName, sdBase)
                _detailedStorage.value = breakdown
                _storageBreakdown.value = Pair(breakdown.ext1Bytes, breakdown.ext2Bytes)
            } else {
                _moveMessage.value = result.exceptionOrNull()?.message ?: "Move failed"
            }
        }
    }

    fun clearMoveMessage() {
        _moveMessage.value = null
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
            _discoveredGames.value = discovered.filter { !it.isAlreadyRegistered }
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
}
