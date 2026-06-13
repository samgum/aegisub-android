package io.github.samgum.aegisub.data.session

import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * ProjectSessionManager：同 projectId 返回同一实例；不同 projectId 不同实例。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSessionManagerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun open_same_id_returns_same_instance() = runTest(dispatcher) {
        val manager = ProjectSessionManager(FakeRepo())
        val a = manager.open(42)
        val b = manager.open(42)
        assertTrue("同 projectId 应返回同一实例", a === b)
    }

    @Test fun open_different_ids_return_different_instances() = runTest(dispatcher) {
        val manager = ProjectSessionManager(FakeRepo())
        val a = manager.open(1)
        val b = manager.open(2)
        assertTrue("不同 projectId 应是不同实例", a !== b)
        assertEquals(1L, a.projectId)
        assertEquals(2L, b.projectId)
    }

    @Test fun shared_session_reflects_edits_across_openers() = runTest(dispatcher) {
        val manager = ProjectSessionManager(FakeRepo(assSample))
        val editor = manager.open(7)
        val preview = manager.open(7)
        advanceUntilIdle()
        val id = editor.script.value!!.events.first().id
        editor.editEvent(id) { it.copy(text = "FromEditor") }
        // 预览拿到的是同一 session，立即可见
        assertEquals("FromEditor", preview.script.value!!.events.first().text)
    }

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

    private class FakeRepo(private val content: String = "") : ProjectRepository {
        override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
        override fun observeProject(id: Long): Flow<Project?> = flowOf(null)
        override suspend fun getContent(id: Long): String = content
        override suspend fun createProject(name: String, format: String, content: String) = 0L
        override suspend fun updateContent(id: Long, content: String, now: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun touchLastOpened(id: Long, now: Long) {}
        override suspend fun getMediaUri(id: Long): String? = null
        override suspend fun setMediaUri(id: Long, mediaUri: String) {}
    }
}
