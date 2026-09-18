package app.mountx.data.db

import androidx.room.*
import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountStatus
import kotlinx.coroutines.flow.Flow

/** Room DAO for game entry CRUD operations */
@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY addedAt DESC")
    fun getAllGames(): Flow<List<GameEntry>>

    @Query("SELECT * FROM games WHERE packageName = :pkg LIMIT 1")
    suspend fun getGameByPackage(pkg: String): GameEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntry)

    @Update
    suspend fun updateGame(game: GameEntry)

    @Query("DELETE FROM games WHERE packageName = :pkg")
    suspend fun deleteGame(pkg: String)

    @Query("UPDATE games SET mountStatus = :status WHERE packageName = :pkg")
    suspend fun updateMountStatus(pkg: String, status: MountStatus)

    @Query("UPDATE games SET dataSizeBytes = :size WHERE packageName = :pkg")
    suspend fun updateDataSize(pkg: String, size: Long)

    @Query("UPDATE games SET mode = :mode WHERE packageName = :pkg")
    suspend fun updateMode(pkg: String, mode: app.mountx.data.model.MountMode)

    @Query("SELECT COUNT(*) FROM games WHERE mountStatus = 'MOUNTED'")
    fun getMountedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM games")
    fun getTotalCount(): Flow<Int>
}
