package io.github.samgum.aegisub.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.samgum.aegisub.data.local.BookmarkDao
import io.github.samgum.aegisub.data.local.ProjectDao
import io.github.samgum.aegisub.data.local.SnapshotDao
import io.github.samgum.aegisub.data.local.SubtitleDatabase
import io.github.samgum.aegisub.data.repository.BookmarkRepository
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.data.repository.RoomBookmarkRepository
import io.github.samgum.aegisub.data.repository.RoomProjectRepository
import io.github.samgum.aegisub.data.repository.RoomSnapshotRepository
import io.github.samgum.aegisub.data.repository.SnapshotRepository
import io.github.samgum.aegisub.data.settings.DataStoreSettingsRepository
import io.github.samgum.aegisub.data.settings.SettingsRepository
import javax.inject.Singleton

/** 设置 DataStore 单例（应用级，name=settings）。 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Hilt 依赖注入：提供 Room 数据库、DAO、仓储、设置 DataStore 的单例。
 *
 * @author 伤感咩吖
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SubtitleDatabase =
        Room.databaseBuilder(context, SubtitleDatabase::class.java, "subtitle.db")
            .addMigrations(
                SubtitleDatabase.MIGRATION_1_2,
                SubtitleDatabase.MIGRATION_2_3,
                SubtitleDatabase.MIGRATION_3_4,
            )
            .build()

    @Provides
    fun provideProjectDao(database: SubtitleDatabase): ProjectDao = database.projectDao()

    @Provides
    fun provideSnapshotDao(database: SubtitleDatabase): SnapshotDao = database.snapshotDao()

    @Provides
    fun provideBookmarkDao(database: SubtitleDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    @Singleton
    fun provideProjectRepository(dao: ProjectDao): ProjectRepository = RoomProjectRepository(dao)

    @Provides
    @Singleton
    fun provideSnapshotRepository(dao: SnapshotDao): SnapshotRepository = RoomSnapshotRepository(dao)

    @Provides
    @Singleton
    fun provideBookmarkRepository(dao: BookmarkDao): BookmarkRepository = RoomBookmarkRepository(dao)

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(store: DataStore<Preferences>): SettingsRepository =
        DataStoreSettingsRepository(store)
}
