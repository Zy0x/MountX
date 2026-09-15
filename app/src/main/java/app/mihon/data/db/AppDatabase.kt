package app.mihon.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.mihon.data.model.GameEntry

/** Main Room database for Mountify */
@Database(
    entities = [GameEntry::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        const val DATABASE_NAME = "mountify.db"
    }
}
