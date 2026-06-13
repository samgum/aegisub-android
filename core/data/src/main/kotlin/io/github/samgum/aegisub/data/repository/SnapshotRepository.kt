package io.github.samgum.aegisub.data.repository

import io.github.samgum.aegisub.data.local.SnapshotDao
import io.github.samgum.aegisub.data.local.SnapshotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 历史快照模型（给 UI）：不含完整内容，避免列表加载大文本。
 */
data class Snapshot(
    val id: Long,
    val projectId: Long,
    val label: String,
    val createdAt: Long,
)

/**
 * 历史快照仓储：「历史版本恢复」所需的手动快照存取。
 * 独立于 [ProjectRepository]，避免污染其接口与所有假实现。
 *
 * @author 伤感咩吖
 */
interface SnapshotRepository {
    fun observeSnapshots(projectId: Long): Flow<List<Snapshot>>
    suspend fun saveSnapshot(projectId: Long, content: String, label: String, now: Long): Long
    suspend fun getSnapshotContent(snapshotId: Long): String?
    suspend fun deleteSnapshot(snapshotId: Long)
}

/**
 * 基于 Room 的实现。clock 注入便于测试。
 *
 * @author 伤感咩吖
 */
class RoomSnapshotRepository(
    private val dao: SnapshotDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : SnapshotRepository {

    override fun observeSnapshots(projectId: Long): Flow<List<Snapshot>> =
        dao.observeByProject(projectId).map { list -> list.map { it.toModel() } }

    override suspend fun saveSnapshot(projectId: Long, content: String, label: String, now: Long): Long =
        dao.insert(SnapshotEntity(projectId = projectId, content = content, label = label, createdAt = now))

    override suspend fun getSnapshotContent(snapshotId: Long): String? =
        dao.getById(snapshotId)?.content

    override suspend fun deleteSnapshot(snapshotId: Long) = dao.deleteById(snapshotId)

    private fun SnapshotEntity.toModel() =
        Snapshot(id = id, projectId = projectId, label = label, createdAt = createdAt)
}
