package io.github.samgum.aegisub.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 书签数据访问对象。
 *
 * @author 伤感咩吖
 */
@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE projectId = :projectId ORDER BY timeMs ASC")
    fun observeByProject(projectId: Long): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)
}
