package io.github.samgum.aegisub.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.samgum.aegisub.data.local.ProjectDao
import io.github.samgum.aegisub.data.local.SubtitleDatabase
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.data.repository.RoomProjectRepository
import javax.inject.Singleton

/**
 * Hilt 依赖注入：提供 Room 数据库、DAO、仓储的单例。
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
            .addMigrations(SubtitleDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideProjectDao(database: SubtitleDatabase): ProjectDao = database.projectDao()

    @Provides
    @Singleton
    fun provideProjectRepository(dao: ProjectDao): ProjectRepository = RoomProjectRepository(dao)
}
