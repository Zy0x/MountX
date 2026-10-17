package app.mountx.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.mountx.R
import app.mountx.data.db.AppDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Android Quick Settings Tile service for MountX.
 * Provides a single-tap toggle to Mount All / Unmount All from the notification shade.
 */
@AndroidEntryPoint
class MountTileService : TileService() {

    @Inject
    lateinit var appDao: AppDao

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var tileJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        tileJob?.cancel()
        tileJob = serviceScope.launch {
            combine(appDao.getMountedCount(), appDao.getTotalCount()) { mounted, total ->
                Pair(mounted, total)
            }.collect { (mounted, total) ->
                updateTileState(mounted, total)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        tileJob?.cancel()
        tileJob = null
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val mountedCount = appDao.getMountedCount().first()
            if (mountedCount > 0) {
                MountService.startUnmountAll(this@MountTileService)
            } else {
                MountService.startMountAll(this@MountTileService)
            }
        }
    }

    private fun updateTileState(mountedCount: Int, totalCount: Int) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        tile.label = getString(R.string.tile_mount_label)
        if (mountedCount > 0) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.tile_mount_subtitle_active, mountedCount, totalCount)
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.tile_mount_subtitle_inactive)
            }
        }
        tile.updateTile()
    }
}
