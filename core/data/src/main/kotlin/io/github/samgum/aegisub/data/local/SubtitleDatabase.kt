package io.github.samgum.aegisub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room 数据库。当前仅 projects 表；后续阶段追加历史版本/自动保存等表时升级 version。
 *
 * @author 伤感咩吖
 */
@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class SubtitleDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
