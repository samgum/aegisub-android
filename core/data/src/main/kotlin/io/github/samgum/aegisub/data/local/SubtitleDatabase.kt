package io.github.samgum.aegisub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库。version=2：projects 表加 mediaUri 列。
 * 后续阶段追加历史版本/自动保存等表时继续升级 version。
 *
 * @author 伤感咩吖
 */
@Database(entities = [ProjectEntity::class], version = 2, exportSchema = false)
abstract class SubtitleDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        /**
         * v1 → v2：projects 表增加 mediaUri 列（可空，默认 null）。
         * 仅影响从旧版本升级的既有库；新装直接建 v2 schema。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN mediaUri TEXT")
            }
        }
    }
}
