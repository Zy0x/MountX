package app.mountx.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.mountx.data.model.AppEntry

/** Main Room database for MountX */
@Database(
    entities = [AppEntry::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    fun gameDao(): AppDao = appDao()

    companion object {
        const val DATABASE_NAME = "mountx.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN mountPoints TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN preferredDiskUuid TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS apps (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL DEFAULT '',
                        mode TEXT NOT NULL DEFAULT 'PKG',
                        mountStatus TEXT NOT NULL DEFAULT 'UNKNOWN',
                        dataSizeBytes INTEGER NOT NULL DEFAULT 0,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        addedAt INTEGER NOT NULL DEFAULT 0,
                        mountPoints TEXT NOT NULL DEFAULT '[]',
                        preferredDiskUuid TEXT DEFAULT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO apps (packageName, displayName, mode, mountStatus, dataSizeBytes, isEnabled, addedAt, mountPoints, preferredDiskUuid)
                    SELECT packageName, displayName, mode, mountStatus, dataSizeBytes, isEnabled, addedAt, mountPoints, preferredDiskUuid FROM games
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS games")
            }
        }
    }
}
