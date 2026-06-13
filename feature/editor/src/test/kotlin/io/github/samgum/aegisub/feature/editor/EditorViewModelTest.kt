package io.github.samgum.aegisub.feature.editor

import androidx.lifecycle.SavedStateHandle
import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.data.repository.Snapshot
import io.github.samgum.aegisub.data.repository.SnapshotRepository
import io.github.samgum.aegisub.data.session.ProjectSessionManager
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.data.settings.SettingsRepository
import io.github.samgum.aegisub.data.settings.ThemeMode
import io.github.samgum.aegisub.data.settings.UserSettings
import io.github.samgum.aegisub.domain.format.TimePrecision
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.editor.components.LineAction
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
import org.junit.Assert.assertNull
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

    private fun vm(content: String?, precision: TimePrecision = TimePrecision.AUTO): EditorViewModel =
        EditorViewModel(
            ProjectSessionManager(FakeProjectRepository(content)),
            FakeSettingsRepository(precision),
            FakeSnapshotRepository(),
            SavedStateHandle(mapOf("projectId" to "42")),
        )

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
        val v = EditorViewModel(ProjectSessionManager(ThrowingRepo()), FakeSettingsRepository(), FakeSnapshotRepository(), SavedStateHandle(mapOf("projectId" to "1")))
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
        EditorViewModel(ProjectSessionManager(repo), FakeSettingsRepository(), FakeSnapshotRepository(), SavedStateHandle(mapOf("projectId" to "42")))
        advanceUntilIdle()
        assertEquals("加载首版本不应触发保存", 0, repo.savedContents.size)
    }

    @Test fun edits_are_debounced_then_saved() = runTest(dispatcher) {
        val repo = FakeProjectRepository(ASS_SAMPLE)
        val v = EditorViewModel(ProjectSessionManager(repo), FakeSettingsRepository(), FakeSnapshotRepository(), SavedStateHandle(mapOf("projectId" to "42")))
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

    // ---------- 导出精度（用户设置穿透到 ASS 写入）----------

    @Test fun export_default_is_centisecond() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE, TimePrecision.AUTO)
        advanceUntilIdle()
        val out = v.exportContent()
        assertTrue("AUTO 应厘秒：$out", out.contains("0:00:01.00,"))
    }

    @Test fun export_three_ms_is_millisecond() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE, TimePrecision.THREE_MS)
        advanceUntilIdle()
        val out = v.exportContent()
        assertTrue("THREE_MS 应毫秒：$out", out.contains("0:00:01.000,"))
    }

    @Test fun export_empty_when_load_failed() = runTest(dispatcher) {
        // 加载失败时 script 维持 null，导出空串而非抛异常
        val v = EditorViewModel(ProjectSessionManager(ThrowingRepo()), FakeSettingsRepository(), FakeSnapshotRepository(), SavedStateHandle(mapOf("projectId" to "1")))
        advanceUntilIdle()
        assertTrue(v.state.value is EditorUiState.Error)
        assertEquals("", v.exportContent())
    }

    // ---------- 行级操作（LineOps 经 session.editEvents 单撤销点）----------

    @Test fun line_action_duplicate_adds_line_and_is_undoable() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE_MULTI)
        advanceUntilIdle()
        val id = v.currentScript()!!.events[0].id
        v.applyLineAction(id, LineAction.DUPLICATE)
        assertEquals(3, v.currentScript()!!.events.size)
        v.undo()
        assertEquals(2, v.currentScript()!!.events.size)
    }

    @Test fun line_action_delete_removes_target() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE_MULTI)
        advanceUntilIdle()
        val events = v.currentScript()!!.events
        val firstId = events[0].id
        val secondText = events[1].text
        v.applyLineAction(firstId, LineAction.DELETE)
        val after = v.currentScript()!!.events
        assertEquals(1, after.size)
        assertEquals(secondText, after[0].text)
    }

    @Test fun line_action_move_up_swaps_order() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE_MULTI)
        advanceUntilIdle()
        val events = v.currentScript()!!.events
        val secondId = events[1].id
        v.applyLineAction(secondId, LineAction.MOVE_UP)
        // 上移后，原第二行排在第一
        assertEquals(secondId, v.currentScript()!!.events[0].id)
    }

    @Test fun line_action_join_next_merges_two_lines() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE_MULTI)
        advanceUntilIdle()
        val firstId = v.currentScript()!!.events[0].id
        v.applyLineAction(firstId, LineAction.JOIN_NEXT)
        val after = v.currentScript()!!.events
        assertEquals(1, after.size)
        // 文本拼接
        assertEquals("HelloWorld", after[0].text)
    }

    @Test fun line_action_split_doubles_count() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE_MULTI)
        advanceUntilIdle()
        val firstId = v.currentScript()!!.events[0].id
        v.applyLineAction(firstId, LineAction.SPLIT)
        assertEquals(3, v.currentScript()!!.events.size)
    }

    @Test fun line_action_insert_after_appends_blank() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE_MULTI)
        advanceUntilIdle()
        val firstId = v.currentScript()!!.events[0].id
        v.applyLineAction(firstId, LineAction.INSERT_AFTER)
        val after = v.currentScript()!!.events
        assertEquals(3, after.size)
        assertEquals("", after[1].text)
    }

    // ---------- 脚本属性（applyScriptInfo 单撤销点）----------

    @Test fun apply_script_info_writes_and_is_undoable() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE_MULTI)
        advanceUntilIdle()
        v.applyScriptInfo(mapOf("Title" to "测试标题", "PlayResX" to "1920", "WrapStyle" to "2"))
        val script = v.currentScript()!!
        assertEquals("测试标题", script.getScriptInfo("Title"))
        assertEquals("1920", script.getScriptInfo("PlayResX"))
        assertEquals("2", script.getScriptInfo("WrapStyle"))
        v.undo()
        assertNull(v.currentScript()!!.getScriptInfo("Title"))
    }

    @Test fun apply_script_info_empty_map_is_noop() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE_MULTI)
        advanceUntilIdle()
        val before = v.currentScript()!!.info.size
        v.applyScriptInfo(emptyMap())
        assertEquals(before, v.currentScript()!!.info.size)
        assertEquals(false, v.canUndo.value)
    }

    // ---------- 翻译助手（原文→Name，译文→Text）----------

    @Test fun set_translation_stores_original_in_name_translation_in_text() = runTest(dispatcher) {
        val v = vm(ASS_SAMPLE_MULTI)
        advanceUntilIdle()
        val id = v.currentScript()!!.events[0].id
        v.setTranslation(id, original = "Hello", translation = "你好")
        val e = v.currentScript()!!.events[0]
        assertEquals("Hello", e.actor)
        assertEquals("你好", e.text)
        // 可撤销
        v.undo()
        assertEquals("Hello", v.currentScript()!!.events[0].text)
        assertEquals("", v.currentScript()!!.events[0].actor)
    }

    // ---------- 历史版本恢复 ----------

    @Test fun take_snapshot_persists_content() = runTest(dispatcher) {
        val snapshotRepo = FakeSnapshotRepository()
        val v = EditorViewModel(ProjectSessionManager(FakeProjectRepository(ASS_SAMPLE)), FakeSettingsRepository(), snapshotRepo, SavedStateHandle(mapOf("projectId" to "42")))
        advanceUntilIdle()
        v.takeSnapshot("第一版")
        advanceUntilIdle()
        assertEquals(1, snapshotRepo.all.size)
        assertEquals("第一版", snapshotRepo.all.first().label)
        assertEquals(42L, snapshotRepo.all.first().projectId)
    }

    @Test fun restore_snapshot_replaces_content_undoable() = runTest(dispatcher) {
        val snapshotRepo = FakeSnapshotRepository()
        val v = EditorViewModel(ProjectSessionManager(FakeProjectRepository(ASS_SAMPLE)), FakeSettingsRepository(), snapshotRepo, SavedStateHandle(mapOf("projectId" to "42")))
        advanceUntilIdle()
        val original = v.currentScript()!!.events.first().text
        // 改文本 → 存快照 → 再改 → 恢复快照
        val id = v.currentScript()!!.events.first().id
        v.updateEventText(id, "Changed")
        v.takeSnapshot("改后")
        advanceUntilIdle()
        val snapId = snapshotRepo.all.first().id
        v.updateEventText(id, "Again")
        v.restoreSnapshot(snapId)
        advanceUntilIdle()
        assertEquals("Changed", v.currentScript()!!.events.first().text)
        // 恢复是撤销点，可撤销回 "Again"
        v.undo()
        assertEquals("Again", v.currentScript()!!.events.first().text)
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

    /** 内存版假快照仓储：记录全部快照，content 由 id 取回。 */
    private class FakeSnapshotRepository : SnapshotRepository {
        private var seq = 0L
        val all = mutableListOf<Snapshot>()
        private val contents = mutableMapOf<Long, String>()
        override fun observeSnapshots(projectId: Long) = kotlinx.coroutines.flow.flowOf(all.filter { it.projectId == projectId })
        override suspend fun saveSnapshot(projectId: Long, content: String, label: String, now: Long): Long {
            val id = ++seq
            contents[id] = content
            all.add(0, Snapshot(id, projectId, label, now))
            return id
        }
        override suspend fun getSnapshotContent(snapshotId: Long): String? = contents[snapshotId]
        override suspend fun deleteSnapshot(snapshotId: Long) {
            all.removeAll { it.id == snapshotId }
            contents.remove(snapshotId)
        }
    }

    /** 固定返回指定导出精度的假设置仓储（其余取默认）。 */
    private class FakeSettingsRepository(
        private val precision: TimePrecision = TimePrecision.AUTO,
    ) : SettingsRepository {
        override val settings: Flow<UserSettings> = flowOf(
            UserSettings(exportPrecision = precision),
        )
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setExportPrecision(p: TimePrecision) {}
        override suspend fun setLayoutMode(mode: LayoutMode) {}
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

private val ASS_SAMPLE_MULTI = """
[Script Info]
ScriptType: v4.00+

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello
Dialogue: 0,0:00:04.00,0:00:06.00,Default,,0,0,0,,World
""".trimIndent()
