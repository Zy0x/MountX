package app.mountify.di

import app.mountify.root.MountManager
import app.mountify.root.StorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMountManager(): MountManager {
        return MountManager()
    }

    @Provides
    @Singleton
    fun provideStorageManager(): StorageManager {
        return StorageManager()
    }
}
