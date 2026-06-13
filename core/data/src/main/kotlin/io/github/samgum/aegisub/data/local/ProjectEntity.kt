package io.github.samgum.aegisub.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 字幕项目持久化实体：一份 ASS/SSA/SRT/LRC/TXT 工程的元数据 + 序列化内容。
 *
 * @author 伤感咩吖
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val format: String,      // ass / ssa / srt / lrc / txt
    val content: String,     // 序列化的字幕全文（由 :core:domain 格式编码器产出）
    val createdAt: Long,     // epoch millis
    val updatedAt: Long,     // epoch millis
    val lastOpenedAt: Long?, // epoch millis，null 表示从未打开
    val mediaUri: String? = null, // SAF 持久化视频 content URI，null 表示未挂载视频
)
