package io.github.samgum.aegisub.data.repository

import io.github.samgum.aegisub.data.local.ProjectDao
import io.github.samgum.aegisub.data.local.ProjectEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RoomProjectRepository 逻辑测试（用内存假 DAO，纯 JVM，不依赖 Room 生成代码）。
 * 真实 SQL/查询的集成验证留待 Robolectric/仪器测试阶段。
 *
 * @author 伤感咩吖
 */
class ProjectRepositoryTest {

    private fun repo(dao: FakeProjectDao, clock: () -> Long = { 0L }) =
        RoomProjectRepository(dao, clock)

    @Test fun create_then_observe_maps_to_model() = runTest {
        val dao = FakeProjectDao()
        val r = repo(dao, clock = { 100L })
        val id = r.createProject(name = "song.ass", format = "ass", content = "[Script Info]")
        val list = r.observeProjects().first()
        assertEquals(1, list.size)
        assertEquals("song.ass", list[0].name)
        assertEquals("ass", list[0].format)
        assertEquals(100L, list[0].updatedAt)
        assertEquals(id, list[0].id)
    }

    @Test fun update_content_changes_updatedAt_not_createdAt() = runTest {
        val dao = FakeProjectDao()
        val r = repo(dao, clock = { 100L })
        val id = r.createProject("a", "srt", "1\n00:00:01,000 --> 00:00:02,000\nHi")
        r.updateContent(id, "changed", now = 200L)
        val entity = dao.store[id]!!
        assertEquals("changed", entity.content)
        assertEquals(200L, entity.updatedAt)
        assertEquals(100L, entity.createdAt)
    }

    @Test fun delete_removes_project() = runTest {
        val dao = FakeProjectDao()
        val r = repo(dao)
        val id = r.createProject("a", "ass", "x")
        r.delete(id)
        assertEquals(0, r.observeProjects().first().size)
    }

    @Test fun observe_orders_recently_opened_first() = runTest {
        val dao = FakeProjectDao()
        val r = repo(dao)
        val a = r.createProject("a", "ass", "x")
        r.createProject("b", "ass", "y")
        r.touchLastOpened(a, now = 50L) // a 最近打开
        val list = r.observeProjects().first()
        assertEquals("a", list[0].name)
        assertEquals("b", list[1].name)
    }

    @Test fun set_then_get_media_uri() = runTest {
        val dao = FakeProjectDao()
        val r = repo(dao)
        val id = r.createProject("a", "ass", "x")
        assertEquals(null, r.getMediaUri(id))
        r.setMediaUri(id, "content://video/1")
        assertEquals("content://video/1", r.getMediaUri(id))
    }
}

/** 内存假 DAO，实现 ProjectDao 接口用于测试仓储逻辑。 */
private class FakeProjectDao : ProjectDao {
    val store = mutableMapOf<Long, ProjectEntity>()
    private var nextId = 1L

    private fun snapshot(): List<ProjectEntity> =
        store.values.sortedWith(
            compareByDescending<ProjectEntity> { it.lastOpenedAt ?: it.updatedAt }
                .thenByDescending { it.updatedAt }
        )

    override fun observeAll(): Flow<List<ProjectEntity>> = flow { emit(snapshot()) }
    override fun observeById(id: Long): Flow<ProjectEntity?> = flow { emit(store[id]) }
    override suspend fun getById(id: Long): ProjectEntity? = store[id]
    override suspend fun insert(project: ProjectEntity): Long {
        val id = if (project.id == 0L) nextId++ else project.id
        store[id] = project.copy(id = id)
        return id
    }
    override suspend fun update(project: ProjectEntity) { store[project.id] = project }
    override suspend fun deleteById(id: Long) { store.remove(id) }
    override suspend fun touchLastOpened(id: Long, ts: Long) {
        store[id]?.let { store[id] = it.copy(lastOpenedAt = ts) }
    }

    override suspend fun updateMediaUri(id: Long, uri: String) {
        store[id]?.let { store[id] = it.copy(mediaUri = uri) }
    }
}
