package io.github.samgum.aegisub.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 书签实体：某工程的视频时间点标记（打轴/校对时记录关键位置）。
 * 与 projects 表外键级联删除。
 *
 * @author 伤感咩吖
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val timeMs: Long,       // 视频时间点（毫秒）
    val label: String,      // 用户备注（可空串）
    val createdAt: Long,    // epoch millis
)
