package io.github.samgum.aegisub.feature.editor

import androidx.lifecycle.SavedStateHandle
import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.domain.time.SubTime
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * EditorViewModel 加载 + 编辑 + 撤销重做 + 防抖保存测试。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(content: String?): EditorViewModel =
        EditorViewModel(FakeProjectRepository(content), SavedStateHandle(mapOf("projectId" to "42")))

    @Test fun loads_ass_script_from_content() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE)
        advanceUntilIdle()
        val state = v.state.value
        assertTrue("expected Loaded, got $state", state is EditorUiState.Loaded)
        val script = (state as EditorUiState.Loaded).script
        assertEquals(1, script.events.size)
        assertEquals(42L, v.projectId)
    }

    @Test fun detects_plain_text_when_no_known_markers() = runTest(dispatcher) {
        val v = vm("line one\nline two")
        advanceUntilIdle()
        assertTrue(v.state.value is EditorUiState.Loaded)
    }

    @Test fun error_state_when_repo_throws() = runTest(dispatcher) {
        val v = EditorViewModel(ThrowingRepo(), SavedStateHandle(mapOf("projectId" to "1")))
        advanceUntilIdle()
        assertTrue(v.state.value is EditorUiState.Error)
    }

    @Test fun canUndo_false_initially() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE)
        advanceUntilIdle()
        assertEquals(false, v.canUndo.value)
    }

    // ---------- Task 2B.1：编辑操作 + 撤销重做 ----------

    @Test fun updateEventText_changes_text_and_enables_undo() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE)
        advanceUntilIdle()
        val id = v.currentScript()!!.events.first().id
        v.updateEventText(id, "Changed")
        assertEquals("Changed", v.currentScript()!!.events.first().text)
        assertEquals(true, v.canUndo.value)
    }

    @Test fun updateEventTimes_changes_start_end() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE)
        advanceUntilIdle()
        val id = v.currentScript()!!.events.first().id
        v.updateEventTimes(id, SubTime.ofMillis(5_000), SubTime.ofMillis(9_000))
        val e = v.currentScript()!!.events.first()
        assertEquals(5_000L, e.start.millis)
        assertEquals(9_000L, e.end.millis)
    }

    @Test fun updateEventStyle_and_layer() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE)
        advanceUntilIdle()
        val id = v.currentScript()!!.events.first().id
        v.updateEventStyle(id, "Title")
        v.setEventLayer(id, 3)
        val e = v.currentScript()!!.events.first()
        assertEquals("Title", e.style)
        assertEquals(3, e.layer)
    }

    @Test fun undo_restores_previous_and_enables_redo() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE)
        advanceUntilIdle()
        val id = v.currentScript()!!.events.first().id
        v.updateEventText(id, "Changed")
        v.undo()
        assertEquals("Hello", v.currentScript()!!.events.first().text)
        assertEquals(false, v.canUndo.value)
        assertEquals(true, v.canRedo.value)
    }

    @Test fun redo_reapplies_edit() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE)
        advanceUntilIdle()
        val id = v.currentScript()!!.events.first().id
        v.updateEventText(id, "Changed")
        v.undo()
        v.redo()
        assertEquals("Changed", v.currentScript()!!.events.first().text)
        assertEquals(true, v.canUndo.value)
        assertEquals(false, v.canRedo.value)
    }

    // ---------- Task 2B.2：防抖自动保存 ----------

    @Test fun first_version_is_not_saved() = runTest(dispatcher) {
        val repo = FakeProjectRepository(ASS_SAMPLE)
        EditorViewModel(repo, SavedStateHandle(mapOf("projectId" to "42")))
        advanceUntilIdle()
        assertEquals("加载首版本不应触发保存", 0, repo.savedContents.size)
    }

    @Test fun edits_are_debounced_then_saved() = runTest(dispatcher) {
        val repo = FakeProjectRepository(ASS_SAMPLE)
        val v = EditorViewModel(repo, SavedStateHandle(mapOf("projectId" to "42")))
        advanceUntilIdle()
        val id = v.currentScript()!!.events.first().id
        v.updateEventText(id, "Changed")
        // advanceTimeBy 推进虚拟时间但 debounce(800) 未到期，不应触发保存
        advanceTimeBy(799)
        assertEquals("debounce 未满不应保存", 0, repo.savedContents.size)
        // 再推进 2ms（累计 801 > 800）到期，触发回写
        advanceTimeBy(2)
        advanceUntilIdle()
        assertEquals(1, repo.savedContents.size)
        assertTrue(repo.savedContents.first().contains("Changed"))
    }

    private class FakeProjectRepository(private val content: String?) : ProjectRepository {
        val savedContents = mutableListOf<String>()
        override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
        override fun observeProject(id: Long): Flow<Project?> = flowOf(null)
        override suspend fun getContent(id: Long): String = content ?: ""
        override suspend fun createProject(name: String, format: String, content: String) = 0L
        override suspend fun updateContent(id: Long, content: String, now: Long) {
            savedContents.add(content)
        }
        override suspend fun delete(id: Long) {}
        override suspend fun touchLastOpened(id: Long, now: Long) {}
        override suspend fun getMediaUri(id: Long): String? = null
        override suspend fun setMediaUri(id: Long, mediaUri: String) {}
    }

    private class ThrowingRepo : ProjectRepository {
        override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
        override fun observeProject(id: Long): Flow<Project?> = flowOf(null)
        override suspend fun getContent(id: Long): String = throw RuntimeException("boom")
        override suspend fun createProject(name: String, format: String, content: String) = 0L
        override suspend fun updateContent(id: Long, content: String, now: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun touchLastOpened(id: Long, now: Long) {}
        override suspend fun getMediaUri(id: Long): String? = null
        override suspend fun setMediaUri(id: Long, mediaUri: String) {}
    }
}

private val ASS_SAMPLE = """
[Script Info]
ScriptType: v4.00+

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello
""".trimIndent()
