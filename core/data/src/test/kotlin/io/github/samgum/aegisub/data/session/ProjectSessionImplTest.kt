package io.github.samgum.aegisub.data.session

import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ProjectSessionImpl：加载/编辑/撤销重做/防抖回写/错误态。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSessionImplTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val assSample = """
        [Script Info]
        ScriptType: v4.00+

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello
    """.trimIndent()

    private fun session(repo: ProjectRepository): ProjectSessionImpl =
        ProjectSessionImpl(projectId = 42, repo = repo).also { it.start() }

    @Test fun loads_script_and_assigns_stable_ids() = runTest(dispatcher) {
        val s = session(FakeRepo(assSample))
        advanceUntilIdle()
        assertNull(s.errorMessage.value)
        assertEquals(1, s.script.value!!.events.size)
        assertEquals(0L, s.script.value!!.events.first().id)
    }

    @Test fun error_state_when_repo_throws() = runTest(dispatcher) {
        val s = session(FakeRepo(throwOnGet = true))
        advanceUntilIdle()
        assertTrue(s.errorMessage.value != null)
        assertEquals(null, s.script.value)
    }

    @Test fun editEvent_changes_event_and_enables_undo() = runTest(dispatcher) {
        val s = session(FakeRepo(assSample))
        advanceUntilIdle()
        val id = s.script.value!!.events.first().id
        s.editEvent(id) { it.copy(text = "Changed") }
        assertEquals("Changed", s.script.value!!.events.first().text)
        assertTrue(s.canUndo.value)
        assertFalse(s.canRedo.value)
    }

    @Test fun undo_then_redo() = runTest(dispatcher) {
        val s = session(FakeRepo(assSample))
        advanceUntilIdle()
        val id = s.script.value!!.events.first().id
        s.editEvent(id) { it.copy(text = "Changed") }
        s.undo()
        assertEquals("Hello", s.script.value!!.events.first().text)
        assertTrue(s.canRedo.value)
        s.redo()
        assertEquals("Changed", s.script.value!!.events.first().text)
        assertTrue(s.canUndo.value)
    }

    @Test fun first_version_not_saved() = runTest(dispatcher) {
        val repo = FakeRepo(assSample)
        val s = session(repo)
        advanceUntilIdle()
        assertEquals("加载首版本不应触发保存", 0, repo.saved.size)
    }

    @Test fun edits_debounced_then_saved() = runTest(dispatcher) {
        val repo = FakeRepo(assSample)
        val s = session(repo)
        advanceUntilIdle()
        val id = s.script.value!!.events.first().id
        s.editEvent(id) { it.copy(text = "Changed") }
        advanceTimeBy(799)
        assertEquals("debounce 未满不应保存", 0, repo.saved.size)
        advanceTimeBy(2)
        advanceUntilIdle()
        assertEquals(1, repo.saved.size)
        assertTrue(repo.saved.first().contains("Changed"))
    }

    @Test fun editEvent_ignored_before_load() = runTest(dispatcher) {
        // 未加载就编辑：不崩，script 仍 null
        val s = ProjectSessionImpl(42, FakeRepo(assSample))
        s.editEvent(0) { it.copy(text = "x") } // start() 未调用
        advanceUntilIdle()
        // script 由 start 加载；此处未 start，保持 null
        assertEquals(null, s.script.value)
    }

    private class FakeRepo(
        private val content: String = "",
        private val throwOnGet: Boolean = false,
    ) : ProjectRepository {
        val saved = mutableListOf<String>()
        override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
        override fun observeProject(id: Long): Flow<Project?> = flowOf(null)
        override suspend fun getContent(id: Long): String {
            if (throwOnGet) throw RuntimeException("boom")
            return content
        }
        override suspend fun createProject(name: String, format: String, content: String) = 0L
        override suspend fun updateContent(id: Long, content: String, now: Long) { saved.add(content) }
        override suspend fun delete(id: Long) {}
        override suspend fun touchLastOpened(id: Long, now: Long) {}
        override suspend fun getMediaUri(id: Long): String? = null
        override suspend fun setMediaUri(id: Long, mediaUri: String) {}
    }
}
