package io.github.samgum.aegisub.feature.editor

import androidx.lifecycle.SavedStateHandle
import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * EditorViewModel 加载逻辑测试。
 *
 * @author 伤感咩吖
 */
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

    private class FakeProjectRepository(private val content: String?) : ProjectRepository {
        override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
        override fun observeProject(id: Long): Flow<Project?> = flowOf(null)
        override suspend fun getContent(id: Long): String = content ?: ""
        override suspend fun createProject(name: String, format: String, content: String) = 0L
        override suspend fun updateContent(id: Long, content: String, now: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun touchLastOpened(id: Long, now: Long) {}
    }

    private class ThrowingRepo : ProjectRepository {
        override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
        override fun observeProject(id: Long): Flow<Project?> = flowOf(null)
        override suspend fun getContent(id: Long): String = throw RuntimeException("boom")
        override suspend fun createProject(name: String, format: String, content: String) = 0L
        override suspend fun updateContent(id: Long, content: String, now: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun touchLastOpened(id: Long, now: Long) {}
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
