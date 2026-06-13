package io.github.samgum.aegisub.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 历史快照数据访问对象。
 *
 * @author 伤感咩吖
 */
@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeByProject(projectId: Long): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshots WHERE id = :id")
    suspend fun getById(id: Long): SnapshotEntity?

    @Insert
    suspend fun insert(snapshot: SnapshotEntity): Long

    @Query("DELETE FROM snapshots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM snapshots WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)
}
