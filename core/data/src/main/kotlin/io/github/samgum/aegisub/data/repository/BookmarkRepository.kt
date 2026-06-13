package io.github.samgum.aegisub.data.repository

import io.github.samgum.aegisub.data.local.BookmarkDao
import io.github.samgum.aegisub.data.local.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 书签领域模型。
 *
 * @author 伤感咩吖
 */
data class Bookmark(
    val id: Long,
    val projectId: Long,
    val timeMs: Long,
    val label: String,
    val createdAt: Long,
)

/**
 * 书签仓储：按工程观察 / 增 / 删。
 *
 * @author 伤感咩吖
 */
interface BookmarkRepository {
    fun observeBookmarks(projectId: Long): Flow<List<Bookmark>>
    suspend fun addBookmark(projectId: Long, timeMs: Long, label: String, now: Long): Long
    suspend fun deleteBookmark(id: Long)
}

/**
 * 基于 Room 的书签仓储实现。
 *
 * @author 伤感咩吖
 */
class RoomBookmarkRepository(
    private val dao: BookmarkDao,
) : BookmarkRepository {
    override fun observeBookmarks(projectId: Long): Flow<List<Bookmark>> =
        dao.observeByProject(projectId).map { list ->
            list.map { it.toModel() }
        }

    override suspend fun addBookmark(projectId: Long, timeMs: Long, label: String, now: Long): Long =
        dao.insert(BookmarkEntity(projectId = projectId, timeMs = timeMs, label = label, createdAt = now))

    override suspend fun deleteBookmark(id: Long) = dao.deleteById(id)
}

private fun BookmarkEntity.toModel() = Bookmark(id, projectId, timeMs, label, createdAt)
