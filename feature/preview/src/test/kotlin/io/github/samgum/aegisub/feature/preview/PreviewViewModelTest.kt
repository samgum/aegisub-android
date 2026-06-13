package io.github.samgum.aegisub.feature.preview

import androidx.lifecycle.SavedStateHandle
import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
 * PreviewViewModel 单测：加载/空媒体/当前行派生/seekToEvent/attachMedia/错误态。
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
    ): PreviewViewModel = PreviewViewModel(
        FakeProjectRepository(content, mediaUri),
        player,
        SavedStateHandle(mapOf("projectId" to "42")),
    )

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
        val v = PreviewViewModel(repo, fake, SavedStateHandle(mapOf("projectId" to "42")))
        advanceUntilIdle()
        v.attachMedia("content://video/9")
        advanceUntilIdle()
        assertEquals("content://video/9", repo.setMediaUriRecorded)
        assertEquals("content://video/9", fake.mediaSet)
        assertTrue((v.state.value as PreviewUiState.Loaded).hasMedia)
    }

    @Test fun error_state_when_repo_throws() = runTest(dispatcher) {
        val v = PreviewViewModel(
            FakeProjectRepository(throwOnGetContent = true),
            FakeVideoPlayer(),
            SavedStateHandle(mapOf("projectId" to "1")),
        )
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

    // ---------- fakes ----------

    private class FakeVideoPlayer : VideoPlayer {
        override val state = MutableStateFlow(PlaybackState())
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
