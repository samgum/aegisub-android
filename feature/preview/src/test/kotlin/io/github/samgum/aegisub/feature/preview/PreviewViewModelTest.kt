package io.github.samgum.aegisub.feature.preview

import androidx.lifecycle.SavedStateHandle
import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.data.session.ProjectSessionManager
import io.github.samgum.aegisub.domain.preview.SubtitleRenderInfo
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * PreviewViewModel 单测：加载/空媒体/当前行派生/seekToEvent/attachMedia/错误态 +
 * 可写化（selectEvent/editEventTimes/nudge/undo）。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val sampleAss = """
        [Script Info]
        ScriptType: v4.00+

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,第一句
        Dialogue: 0,0:00:04.00,0:00:06.00,Default,,0,0,0,,第二句
    """.trimIndent()

    private fun vm(
        content: String? = sampleAss,
        mediaUri: String? = null,
        player: VideoPlayer = FakeVideoPlayer(),
    ): PreviewViewModel {
        val repo = FakeProjectRepository(content, mediaUri)
        return PreviewViewModel(ProjectSessionManager(repo), repo, player, SavedStateHandle(mapOf("projectId" to "42")))
    }

    @Test fun loads_script_into_loaded_state() = runTest(dispatcher) {
        val v = vm()
        advanceUntilIdle()
        val state = v.state.value
        assertTrue("expected Loaded, got $state", state is PreviewUiState.Loaded)
        val loaded = state as PreviewUiState.Loaded
        assertEquals(2, loaded.script.events.size)
        assertEquals(42L, v.projectId)
    }

    @Test fun empty_media_uri_means_has_media_false() = runTest(dispatcher) {
        val v = vm(mediaUri = null)
        advanceUntilIdle()
        assertFalse((v.state.value as PreviewUiState.Loaded).hasMedia)
    }

    @Test fun has_media_true_when_media_uri_present() = runTest(dispatcher) {
        val v = vm(mediaUri = "content://video/1")
        advanceUntilIdle()
        assertTrue((v.state.value as PreviewUiState.Loaded).hasMedia)
    }

    @Test fun current_event_id_advances_with_position() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val v = vm(player = fake)
        advanceUntilIdle()
        // 2s 落在第一句 [1s,3s)
        fake.emitPosition(2_000)
        advanceUntilIdle()
        assertEquals(0L, (v.state.value as PreviewUiState.Loaded).currentEventId)
        // 5s 落在第二句 [4s,6s)
        fake.emitPosition(5_000)
        advanceUntilIdle()
        assertEquals(1L, (v.state.value as PreviewUiState.Loaded).currentEventId)
    }

    @Test fun current_event_id_null_when_between_lines() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val v = vm(player = fake)
        advanceUntilIdle()
        fake.emitPosition(3_500) // 两句之间
        advanceUntilIdle()
        assertNull((v.state.value as PreviewUiState.Loaded).currentEventId)
    }

    @Test fun seek_to_event_seeks_player_to_event_start() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val v = vm(player = fake)
        advanceUntilIdle()
        v.seekToEvent(1L) // 第二句 start=4s
        assertEquals(4_000L, fake.seekedTo)
    }

    @Test fun attach_media_persists_and_sets_player() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val repo = FakeProjectRepository(sampleAss, mediaUri = null)
        val v = PreviewViewModel(ProjectSessionManager(repo), repo, fake, SavedStateHandle(mapOf("projectId" to "42")))
        advanceUntilIdle()
        v.attachMedia("content://video/9")
        advanceUntilIdle()
        assertEquals("content://video/9", repo.setMediaUriRecorded)
        assertEquals("content://video/9", fake.mediaSet)
        assertTrue((v.state.value as PreviewUiState.Loaded).hasMedia)
    }

    @Test fun error_state_when_repo_throws() = runTest(dispatcher) {
        val repo = FakeProjectRepository(throwOnGetContent = true)
        val v = PreviewViewModel(ProjectSessionManager(repo), repo, FakeVideoPlayer(), SavedStateHandle(mapOf("projectId" to "1")))
        advanceUntilIdle()
        assertTrue(v.state.value is PreviewUiState.Error)
    }

    @Test fun on_clear_releases_player() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val v = vm(player = fake)
        advanceUntilIdle()
        v.releaseForTest()
        assertTrue(fake.released)
    }

    @Test fun active_subtitle_only_emits_on_event_change() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val v = vm(player = fake)
        advanceUntilIdle()
        val emissions = mutableListOf<SubtitleRenderInfo?>()
        val job = launch { v.activeSubtitle.toList(emissions) }
        advanceUntilIdle()
        // position=0 < 1s：无活动事件
        fake.emitPosition(2_000) // 落入第一句 [1s,3s)
        advanceUntilIdle()
        assertEquals("第一句", emissions.last()?.text)
        val sizeAfterFirst = emissions.size
        fake.emitPosition(2_500) // 仍在第一句区间
        advanceUntilIdle()
        assertEquals("同事件内位置变化不应触发新发射", sizeAfterFirst, emissions.size)
        fake.emitPosition(5_000) // 进入第二句 [4s,6s)
        advanceUntilIdle()
        assertEquals("第二句", emissions.last()?.text)
        assertTrue("跨事件应发新值", emissions.size > sizeAfterFirst)
        job.cancel()
    }

    // ---------- 可写化（Task 5）----------

    @Test fun select_event_seeks_and_marks_selected() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val v = vm(player = fake)
        advanceUntilIdle()
        v.selectEvent(1L) // 第二句 start=4s
        advanceUntilIdle()
        assertEquals(4_000L, fake.seekedTo)
        assertEquals(1L, (v.state.value as PreviewUiState.Loaded).selectedEventId)
    }

    @Test fun edit_event_times_writes_session_and_clamps() = runTest(dispatcher) {
        val fake = FakeVideoPlayer(durationMs = 10_000)
        val v = vm(player = fake)
        advanceUntilIdle()
        // 第二句原 [4s,6s]，把结束拖到 12s → 钳到 10s
        v.editEventTimes(1L, SubTime.ofMillis(4_000), SubTime.ofMillis(12_000))
        advanceUntilIdle() // 等 session → state combine 传播
        val e = (v.state.value as PreviewUiState.Loaded).script.events[1]
        assertEquals(4_000L, e.start.millis)
        assertEquals(10_000L, e.end.millis)
    }

    @Test fun nudge_start_adjusts_and_keeps_end() = runTest(dispatcher) {
        val fake = FakeVideoPlayer(durationMs = 10_000)
        val v = vm(player = fake)
        advanceUntilIdle()
        v.selectEvent(0L) // 第一句 [1s,3s]
        advanceUntilIdle()
        v.nudgeStart(1_000) // 起始 +1s → 2s
        advanceUntilIdle()
        val e = (v.state.value as PreviewUiState.Loaded).script.events[0]
        assertEquals(2_000L, e.start.millis)
        assertEquals(3_000L, e.end.millis)
    }

    @Test fun undo_in_preview_restores_timing() = runTest(dispatcher) {
        val fake = FakeVideoPlayer(durationMs = 10_000)
        val v = vm(player = fake)
        advanceUntilIdle()
        v.editEventTimes(0L, SubTime.ofMillis(2_000), SubTime.ofMillis(4_000))
        advanceUntilIdle()
        assertTrue(v.canUndo.value)
        v.undo()
        advanceUntilIdle()
        val e = (v.state.value as PreviewUiState.Loaded).script.events[0]
        assertEquals("撤销应回到原 1s", 1_000L, e.start.millis)
    }

    // ---------- fakes ----------

    private class FakeVideoPlayer(private val durationMs: Long = 0L) : VideoPlayer {
        override val state = MutableStateFlow(PlaybackState(durationMs = durationMs))
        var seekedTo: Long? = null
        var mediaSet: String? = null
        var released = false
        override fun setMedia(uri: String) { mediaSet = uri }
        override fun play() { state.value = state.value.copy(isPlaying = true) }
        override fun pause() { state.value = state.value.copy(isPlaying = false) }
        override fun seekTo(positionMs: Long) { seekedTo = positionMs }
        override fun setSpeed(rate: Float) { state.value = state.value.copy(speed = rate) }
        override fun release() { released = true }
        fun emitPosition(ms: Long) { state.value = state.value.copy(positionMs = ms) }
    }

    private class FakeProjectRepository(
        private val content: String? = "",
        private val mediaUri: String? = null,
        private val throwOnGetContent: Boolean = false,
    ) : ProjectRepository {
        var setMediaUriRecorded: String? = null
        override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
        override fun observeProject(id: Long): Flow<Project?> = flowOf(null)
        override suspend fun getContent(id: Long): String {
            if (throwOnGetContent) throw RuntimeException("boom")
            return content ?: ""
        }
        override suspend fun createProject(name: String, format: String, content: String) = 0L
        override suspend fun updateContent(id: Long, content: String, now: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun touchLastOpened(id: Long, now: Long) {}
        override suspend fun getMediaUri(id: Long): String? = mediaUri
        override suspend fun setMediaUri(id: Long, mediaUri: String) { setMediaUriRecorded = mediaUri }
    }
}
