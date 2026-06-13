package io.github.samgum.aegisub.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 项目库数据访问对象。
 *
 * @author 伤感咩吖
 */
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE projects SET lastOpenedAt = :ts WHERE id = :id")
    suspend fun touchLastOpened(id: Long, ts: Long)

    @Query("UPDATE projects SET mediaUri = :uri WHERE id = :id")
    suspend fun updateMediaUri(id: Long, uri: String)
}
