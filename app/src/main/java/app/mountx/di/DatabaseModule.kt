package app.mountx.di

import android.content.Context
import androidx.room.Room
import app.mountx.data.db.AppDatabase
import app.mountx.data.db.GameDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val targetDbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        if (!targetDbFile.exists()) {
            val oldLocalDb = context.getDatabasePath("mountify.db")
            if (oldLocalDb.exists()) {
                runCatching {
                    targetDbFile.parentFile?.mkdirs()
                    oldLocalDb.copyTo(targetDbFile, overwrite = true)
                }
            } else {
                val legacyPkgDb = java.io.File("/data/data/app.mountify/databases/mountify.db")
                if (legacyPkgDb.exists()) {
                    runCatching {
                        targetDbFile.parentFile?.mkdirs()
                        legacyPkgDb.copyTo(targetDbFile, overwrite = true)
                    }
                }
            }
        }

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideAppDao(database: AppDatabase): app.mountx.data.db.AppDao {
        return database.appDao()
    }
}
