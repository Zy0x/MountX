package app.mountx.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.mountx.data.model.GameEntry

/** Main Room database for MountX */
@Database(
    entities = [GameEntry::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        const val DATABASE_NAME = "mountx.db"
    }
}
