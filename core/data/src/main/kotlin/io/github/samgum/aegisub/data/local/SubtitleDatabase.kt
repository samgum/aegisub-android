package io.github.samgum.aegisub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库。version=4：新增 bookmarks 书签表。
 * 后续阶段继续升级 version。
 *
 * @author 伤感咩吖
 */
@Database(
    entities = [ProjectEntity::class, SnapshotEntity::class, BookmarkEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class SubtitleDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        /**
         * v1 → v2：projects 表增加 mediaUri 列（可空，默认 null）。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN mediaUri TEXT")
            }
        }

        /**
         * v2 → v3：新增 snapshots 历史快照表（外键级联 projects，按 projectId 索引）。
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `snapshots` (
                        `id` INTEGER NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_snapshots_projectId` ON `snapshots` (`projectId`)")
            }
        }

        /**
         * v3 → v4：新增 bookmarks 书签表（外键级联 projects，按 projectId 索引）。
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bookmarks` (
                        `id` INTEGER NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `timeMs` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_projectId` ON `bookmarks` (`projectId`)")
            }
        }
    }
}
