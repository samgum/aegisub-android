package io.github.samgum.aegisub.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.data.session.ProjectSessionManager
import io.github.samgum.aegisub.domain.audio.SpectrogramData
import io.github.samgum.aegisub.domain.audio.Waveform
import io.github.samgum.aegisub.domain.edit.VisualTags
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.preview.ActiveSubtitleResolver
import io.github.samgum.aegisub.domain.preview.SubtitleRenderInfo
import io.github.samgum.aegisub.domain.preview.TimingConstraints
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.preview.audio.WaveformExtractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 预览屏状态。
 *
 * @author 伤感咩吖
 */
sealed interface PreviewUiState {
    object Loading : PreviewUiState
    data class Loaded(
        val script: AssScript,
        val hasMedia: Boolean,
        val playback: PlaybackState,
        val currentEventId: Long?,
        val selectedEventId: Long?,
    ) : PreviewUiState
    data class Error(val message: String) : PreviewUiState
}

/**
 * 预览 ViewModel：与编辑器共享 [io.github.samgum.aegisub.data.session.ProjectSession]，
 * 可在预览屏直接编辑选中行的起止时间。媒体挂载与音频波形提取由本类直管。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModel @Inject constructor(
    manager: ProjectSessionManager,
    private val repo: ProjectRepository,
    private val player: VideoPlayer,
    private val extractor: WaveformExtractor,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = savedStateHandle.get<String>("projectId")!!.toLong()

    private val session = manager.open(projectId)

    private val _hasMedia = MutableStateFlow(false)
    private val _selectedEventId = MutableStateFlow<Long?>(null)

    private val _waveform = MutableStateFlow<Waveform?>(null)
    val waveform: StateFlow<Waveform?> = _waveform.asStateFlow()

    private val _spectrogram = MutableStateFlow<SpectrogramData?>(null)
    val spectrogram: StateFlow<SpectrogramData?> = _spectrogram.asStateFlow()

    private val base = combine(session.script, session.errorMessage, _hasMedia) { script, error, hasMedia ->
        when {
            error != null -> BaseState.Error(error)
            script != null -> BaseState.Ready(script, hasMedia)
            else -> BaseState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BaseState.Loading)

    val state: StateFlow<PreviewUiState> = combine(base, player.state, _selectedEventId) { b, playback, selected ->
        when (b) {
            BaseState.Loading -> PreviewUiState.Loading
            is BaseState.Error -> PreviewUiState.Error(b.message)
            is BaseState.Ready -> PreviewUiState.Loaded(
                script = b.script,
                hasMedia = b.hasMedia,
                playback = playback,
                currentEventId = ActiveSubtitleResolver.activeEvent(b.script, playback.positionMs)?.id,
                selectedEventId = selected,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PreviewUiState.Loading)

    val activeSubtitle: StateFlow<SubtitleRenderInfo?> = combine(base, player.state) { b, playback ->
        when (b) {
            is BaseState.Ready -> ActiveSubtitleResolver.renderInfo(b.script, playback.positionMs)
            else -> null
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val canUndo: StateFlow<Boolean> = session.canUndo
    val canRedo: StateFlow<Boolean> = session.canRedo

    /** 暴露播放器给 PlayerSurface 绑定（仅预览模块内做安全转型）。 */
    val videoPlayer: VideoPlayer get() = player

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            val uri = repo.getMediaUri(projectId)
            if (uri != null) {
                player.setMedia(uri)
                _hasMedia.value = true
                loadWaveform(uri)
            }
        }
    }

    fun playPause() {
        if (player.state.value.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    /** 相对当前位置 seek（键盘左右键）；越界钳到 [0, duration]。 */
    fun seekRelative(deltaMs: Long) {
        val cur = player.state.value.positionMs
        val maxMs = player.state.value.durationMs
        player.seekTo((cur + deltaMs).coerceIn(0L, maxOf(0L, maxMs)))
    }

    fun setSpeed(rate: Float) {
        player.setSpeed(rate)
    }

    /** 前进一帧（逐帧预览）。 */
    fun frameStepForward() = player.seekNextFrame()

    /** 后退一帧。 */
    fun frameStepBack() = player.seekPreviousFrame()

    /** 当前帧率（0f 表示未知），用于帧级微调。 */
    fun fps(): Float = player.state.value.fps

    /** 帧级时长（毫秒）；帧率未知时按 30fps。 */
    private fun frameDurationMs(): Long {
        val f = player.state.value.fps.let { if (it > 0f) it else 30f }
        return (1000.0f / f).toLong().coerceAtLeast(1L)
    }

    /** 选中行起始 ±1 帧。 */
    fun nudgeStartByFrame(forward: Boolean) {
        val id = _selectedEventId.value ?: return
        val event = session.script.value?.events?.firstOrNull { it.id == id } ?: return
        val delta = if (forward) frameDurationMs() else -frameDurationMs()
        editEventTimes(id, SubTime.ofMillis(event.start.millis + delta), event.end)
    }

    /** 选中行结束 ±1 帧。 */
    fun nudgeEndByFrame(forward: Boolean) {
        val id = _selectedEventId.value ?: return
        val event = session.script.value?.events?.firstOrNull { it.id == id } ?: return
        val delta = if (forward) frameDurationMs() else -frameDurationMs()
        editEventTimes(id, event.start, SubTime.ofMillis(event.end.millis + delta))
    }

    /** 选中一行：seek 到该行开始 + 标记为编辑目标。 */
    fun selectEvent(eventId: Long) {
        val script = session.script.value ?: return
        val event = script.events.firstOrNull { it.id == eventId } ?: return
        _selectedEventId.value = eventId
        player.seekTo(event.start.millis)
    }

    /** 仅 seek 到事件开始（保留兼容入口；新代码用 [selectEvent]）。 */
    fun seekToEvent(eventId: Long) {
        val script = session.script.value ?: return
        val event = script.events.firstOrNull { it.id == eventId } ?: return
        player.seekTo(event.start.millis)
    }

    /** 把指定行的起止设为给定值（经 TimingConstraints 钳制）。 */
    fun editEventTimes(eventId: Long, start: SubTime, end: SubTime) {
        val durationMs = player.state.value.durationMs
        val (s, e) = TimingConstraints.constrain(start, end, durationMs)
        session.editEvent(eventId) { it.copy(start = s, end = e) }
    }

    /** 微调选中行的起始时间 deltaMs 毫秒（负数前移）。 */
    fun nudgeStart(deltaMs: Long) {
        val id = _selectedEventId.value ?: return
        val event = session.script.value?.events?.firstOrNull { it.id == id } ?: return
        editEventTimes(id, SubTime.ofMillis(event.start.millis + deltaMs), event.end)
    }

    /** 微调选中行的结束时间 deltaMs 毫秒（负数前移）。 */
    fun nudgeEnd(deltaMs: Long) {
        val id = _selectedEventId.value ?: return
        val event = session.script.value?.events?.firstOrNull { it.id == id } ?: return
        editEventTimes(id, event.start, SubTime.ofMillis(event.end.millis + deltaMs))
    }

    /** 把指定行的起始设为当前播放位置（踩点打轴）。 */
    fun setStartToPosition(eventId: Long) {
        val pos = player.state.value.positionMs
        val event = session.script.value?.events?.firstOrNull { it.id == eventId } ?: return
        editEventTimes(eventId, SubTime.ofMillis(pos), event.end)
    }

    /** 把指定行的结束设为当前播放位置（踩点打轴）。 */
    fun setEndToPosition(eventId: Long) {
        val pos = player.state.value.positionMs
        val event = session.script.value?.events?.firstOrNull { it.id == eventId } ?: return
        editEventTimes(eventId, event.start, SubTime.ofMillis(pos))
    }

    /** 可视化打字：把 {\pos(x,y)} 写入选中行文本（已有则替换，单撤销点）。 */
    fun setEventPos(eventId: Long, x: Int, y: Int) {
        session.editEvent(eventId) { it.copy(text = VisualTags.setPos(it.text, x, y)) }
    }

    /** 可视化打字：把 {\fr<deg>} 写入选中行文本（单撤销点）。 */
    fun setEventRotation(eventId: Long, degrees: Int) {
        session.editEvent(eventId) { it.copy(text = VisualTags.setRotation(it.text, degrees)) }
    }

    /** 可视化打字：清除选中行的 {\pos}。 */
    fun clearEventPos(eventId: Long) {
        session.editEvent(eventId) { it.copy(text = VisualTags.removePos(it.text)) }
    }

    /** 可视化打字：把 {\move(x1,y1,x2,y2)} 写入选中行（整段时长移动，单撤销点）。 */
    fun setEventMove(eventId: Long, x1: Int, y1: Int, x2: Int, y2: Int) {
        session.editEvent(eventId) { it.copy(text = VisualTags.setMove(it.text, x1, y1, x2, y2)) }
    }

    /** 可视化打字：把 {\fad(fadeIn,fadeOut)} 写入选中行（毫秒，单撤销点）。 */
    fun setEventFade(eventId: Long, fadeIn: Int, fadeOut: Int) {
        session.editEvent(eventId) { it.copy(text = VisualTags.setFade(it.text, fadeIn, fadeOut)) }
    }

    /** 可视化打字：清除选中行的 {\move}。 */
    fun clearEventMove(eventId: Long) {
        session.editEvent(eventId) { it.copy(text = VisualTags.removeMove(it.text)) }
    }

    /** 直接写入选中行文本（用于 Karaoke 音节计时重建等）。 */
    fun setEventText(eventId: Long, text: String) {
        session.editEvent(eventId) { it.copy(text = text) }
    }

    /** 选中上一行（并 seek 到其起始）。 */
    fun selectPrevEvent() {
        val script = session.script.value ?: return
        val idx = script.events.indexOfFirst { it.id == _selectedEventId.value }
        if (idx > 0) selectEvent(script.events[idx - 1].id)
    }

    /** 选中下一行（并 seek 到其起始）。 */
    fun selectNextEvent() {
        val script = session.script.value ?: return
        val idx = script.events.indexOfFirst { it.id == _selectedEventId.value }
        if (idx in 0 until script.events.lastIndex) selectEvent(script.events[idx + 1].id)
    }

    fun attachMedia(uri: String) {
        viewModelScope.launch {
            repo.setMediaUri(projectId, uri)
            player.setMedia(uri)
            _hasMedia.value = true
            loadWaveform(uri)
        }
    }

    fun undo() = session.undo()
    fun redo() = session.redo()

    override fun onCleared() {
        player.release()
    }

    /** 测试专用：触发 onCleared 等价的播放器释放。 */
    internal fun releaseForTest() {
        player.release()
    }

    /** 异步提取音频波形 + 频谱（一次解码，挂载/更换视频时触发）。 */
    private fun loadWaveform(uri: String) {
        viewModelScope.launch {
            val analysis = extractor.extractFull(uri, WAVEFORM_BUCKETS, WAVEFORM_BUCKETS, SPECTROGRAM_BINS)
            if (analysis != null) {
                _waveform.value = analysis.waveform
                _spectrogram.value = analysis.spectrogram
            } else {
                _waveform.value = null
                _spectrogram.value = null
            }
        }
    }

    private companion object {
        const val WAVEFORM_BUCKETS = 300
        /** 频谱频率 bin 数（低频段，对字幕足够）。 */
        const val SPECTROGRAM_BINS = 96
    }

    private sealed interface BaseState {
        object Loading : BaseState
        data class Ready(val script: AssScript, val hasMedia: Boolean) : BaseState
        data class Error(val message: String) : BaseState
    }
}
