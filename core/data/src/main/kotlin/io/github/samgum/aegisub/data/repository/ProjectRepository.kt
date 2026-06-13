package io.github.samgum.aegisub.data.repository

import io.github.samgum.aegisub.data.local.ProjectDao
import io.github.samgum.aegisub.data.local.ProjectEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 给 UI 用的项目模型（不含完整内容，避免列表加载大文本）。
 */
data class Project(
    val id: Long,
    val name: String,
    val format: String,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
)

/**
 * 项目仓储：封装 DAO，对 UI 暴露领域模型 Flow 与操作。
 * clock 注入便于测试（避免直接依赖系统时间）。
 */
interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    fun observeProject(id: Long): Flow<Project?>
    suspend fun getContent(id: Long): String
    suspend fun createProject(name: String, format: String, content: String): Long
    suspend fun updateContent(id: Long, content: String, now: Long)
    suspend fun delete(id: Long)
    suspend fun touchLastOpened(id: Long, now: Long)
}

class RoomProjectRepository(
    private val dao: ProjectDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeProject(id: Long): Flow<Project?> =
        dao.observeById(id).map { it?.toModel() }

    override suspend fun getContent(id: Long): String =
        dao.getById(id)?.content ?: ""

    override suspend fun createProject(name: String, format: String, content: String): Long {
        val now = clock()
        return dao.insert(
            ProjectEntity(
                name = name, format = format, content = content,
                createdAt = now, updatedAt = now, lastOpenedAt = null,
            )
        )
    }

    override suspend fun updateContent(id: Long, content: String, now: Long) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(content = content, updatedAt = now))
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun touchLastOpened(id: Long, now: Long) = dao.touchLastOpened(id, now)

    private fun ProjectEntity.toModel() =
        Project(id = id, name = name, format = format, updatedAt = updatedAt, lastOpenedAt = lastOpenedAt)
}
