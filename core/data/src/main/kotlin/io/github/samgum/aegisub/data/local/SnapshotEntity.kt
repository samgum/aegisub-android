package io.github.samgum.aegisub.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 历史快照实体：某时刻某工程的字幕全文快照，用于「历史版本恢复」。
 * 与 projects 表外键级联删除（工程删则快照删）。
 *
 * @author 伤感咩吖
 */
@Entity(
    tableName = "snapshots",
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
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val content: String,   // 序列化的字幕全文
    val label: String,     // 用户备注 / 自动标签（如「手动快照」）
    val createdAt: Long,   // epoch millis
)
