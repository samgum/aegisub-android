package io.github.samgum.aegisub.data.repository

import io.github.samgum.aegisub.data.local.SnapshotDao
import io.github.samgum.aegisub.data.local.SnapshotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RoomSnapshotRepository 逻辑测试（内存假 DAO，纯 JVM）。
 * 真实 SQL/级联验证留待 Robolectric/仪器测试阶段。
 *
 * @author 伤感咩吖
 */
class SnapshotRepositoryTest {

    private fun repo(dao: FakeSnapshotDao, clock: () -> Long = { 0L }) =
        RoomSnapshotRepository(dao, clock)

    @Test fun save_then_observe_maps_to_model() = runTest {
        val dao = FakeSnapshotDao()
        val r = repo(dao, clock = { 1_000L })
        val id = r.saveSnapshot(projectId = 42, content = "[Script Info]", label = "v1", now = 1_000L)
        val list = r.observeSnapshots(42).first()
        assertEquals(1, list.size)
        assertEquals(id, list[0].id)
        assertEquals(42L, list[0].projectId)
        assertEquals("v1", list[0].label)
        assertEquals(1_000L, list[0].createdAt)
    }

    @Test fun get_content_by_id() = runTest {
        val dao = FakeSnapshotDao()
        val r = repo(dao)
        val id = r.saveSnapshot(42, "CONTENT", "v1", 0L)
        assertEquals("CONTENT", r.getSnapshotContent(id))
    }

    @Test fun get_content_unknown_id_returns_null() = runTest {
        val r = repo(FakeSnapshotDao())
        assertNull(r.getSnapshotContent(999L))
    }

    @Test fun delete_removes_snapshot() = runTest {
        val dao = FakeSnapshotDao()
        val r = repo(dao)
        val id = r.saveSnapshot(42, "x", "v1", 0L)
        r.deleteSnapshot(id)
        assertTrue(r.observeSnapshots(42).first().isEmpty())
        assertNull(r.getSnapshotContent(id))
    }

    @Test fun observe_filters_by_project() = runTest {
        val dao = FakeSnapshotDao()
        val r = repo(dao)
        r.saveSnapshot(42, "a", "v1", 0L)
        r.saveSnapshot(7, "b", "v2", 0L)
        assertEquals(1, r.observeSnapshots(42).first().size)
        assertEquals(1, r.observeSnapshots(7).first().size)
    }

    private class FakeSnapshotDao : SnapshotDao {
        private var seq = 0L
        private val store = mutableMapOf<Long, SnapshotEntity>()
        private val flow = MutableStateFlow<List<SnapshotEntity>>(emptyList())

        private fun emit() { flow.value = store.values.sortedByDescending { it.createdAt } }

        override fun observeByProject(projectId: Long): Flow<List<SnapshotEntity>> =
            flow.asStateFlow().map { list -> list.filter { it.projectId == projectId } }

        override suspend fun getById(id: Long): SnapshotEntity? = store[id]

        override suspend fun insert(snapshot: SnapshotEntity): Long {
            val id = ++seq
            store[id] = snapshot.copy(id = id)
            emit()
            return id
        }

        override suspend fun deleteById(id: Long) {
            store.remove(id)
            emit()
        }

        override suspend fun deleteByProject(projectId: Long) {
            store.values.removeAll { it.projectId == projectId }
            emit()
        }
    }
}
