package app.mountx.data.db

import androidx.room.*
import app.mountx.data.model.AppEntry
import app.mountx.data.model.MountStatus
import kotlinx.coroutines.flow.Flow

/** Room DAO for app entry CRUD operations */
@Dao
interface AppDao {

    @Query("SELECT * FROM apps ORDER BY addedAt DESC")
    fun getAllApps(): Flow<List<AppEntry>>

    @Query("SELECT * FROM apps ORDER BY addedAt DESC")
    suspend fun getAllAppsSync(): List<AppEntry>

    @Query("SELECT * FROM apps WHERE packageName = :pkg LIMIT 1")
    suspend fun getAppByPackage(pkg: String): AppEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppEntry)

    @Update
    suspend fun updateApp(app: AppEntry)

    @Query("DELETE FROM apps WHERE packageName = :pkg")
    suspend fun deleteApp(pkg: String)

    @Query("UPDATE apps SET mountStatus = :status WHERE packageName = :pkg")
    suspend fun updateMountStatus(pkg: String, status: MountStatus)

    @Query("UPDATE apps SET dataSizeBytes = :size WHERE packageName = :pkg")
    suspend fun updateDataSize(pkg: String, size: Long)

    @Query("UPDATE apps SET mode = :mode WHERE packageName = :pkg")
    suspend fun updateMode(pkg: String, mode: app.mountx.data.model.MountMode)

    @Query("UPDATE apps SET isEnabled = :enabled WHERE packageName = :pkg")
    suspend fun updateEnabled(pkg: String, enabled: Boolean)

    @Query("SELECT COUNT(*) FROM apps WHERE mountStatus = 'MOUNTED'")
    fun getMountedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM apps")
    fun getTotalCount(): Flow<Int>

    // ── Backward Compatibility Bridge for Legacy Calls ──
    fun getAllGames(): Flow<List<AppEntry>> = getAllApps()
    suspend fun getAllGamesSync(): List<AppEntry> = getAllAppsSync()
    suspend fun getGameByPackage(pkg: String): AppEntry? = getAppByPackage(pkg)
    suspend fun insertGame(game: AppEntry) = insertApp(game)
    suspend fun updateGame(game: AppEntry) = updateApp(game)
    suspend fun deleteGame(pkg: String) = deleteApp(pkg)
}

typealias GameDao = AppDao
