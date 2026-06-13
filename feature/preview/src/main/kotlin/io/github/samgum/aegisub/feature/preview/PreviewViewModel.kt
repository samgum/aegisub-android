package io.github.samgum.aegisub.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.domain.format.FormatRegistry
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.preview.ActiveSubtitleResolver
import io.github.samgum.aegisub.domain.preview.SubtitleRenderInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    ) : PreviewUiState
    data class Error(val message: String) : PreviewUiState
}

/**
 * 预览 ViewModel：只读加载脚本 + 挂载媒体 + 派生当前活动行 + seek/媒体控制。
 * 不写脚本，与编辑器的撤销/自动保存完全解耦。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModel @Inject constructor(
    private val repo: ProjectRepository,
    private val player: VideoPlayer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = savedStateHandle.get<String>("projectId")!!.toLong()

    private val base = MutableStateFlow<BaseState>(BaseState.Loading)

    val state: StateFlow<PreviewUiState> = combine(base, player.state) { b, playback ->
        when (b) {
            BaseState.Loading -> PreviewUiState.Loading
            is BaseState.Error -> PreviewUiState.Error(b.message)
            is BaseState.Ready -> PreviewUiState.Loaded(
                script = b.script,
                hasMedia = b.hasMedia,
                playback = playback,
                currentEventId = ActiveSubtitleResolver.activeEvent(b.script, playback.positionMs)?.id,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PreviewUiState.Loading)

    /**
     * 当前活动字幕渲染信息（distinctUntilChanged：仅活动事件切换时变化）。
     * 叠加层订阅本流而非 50ms 位置 tick，降低低端机每帧重组开销。
     */
    val activeSubtitle: StateFlow<SubtitleRenderInfo?> = combine(base, player.state) { b, playback ->
        when (b) {
            is BaseState.Ready -> ActiveSubtitleResolver.renderInfo(b.script, playback.positionMs)
            else -> null
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 暴露播放器给 PlayerSurface 绑定（仅预览模块内做安全转型）。 */
    val videoPlayer: VideoPlayer get() = player

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val content = repo.getContent(projectId)
                val parsed = FormatRegistry.detect(content)?.read(content) ?: AssScript.default()
                val script = parsed.withEvents(parsed.events.mapIndexed { i, e -> e.copy(id = i.toLong()) })
                val mediaUri = repo.getMediaUri(projectId)
                if (mediaUri != null) player.setMedia(mediaUri)
                base.value = BaseState.Ready(script, hasMedia = mediaUri != null)
            } catch (e: Exception) {
                base.value = BaseState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun playPause() {
        if (player.state.value.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun setSpeed(rate: Float) {
        player.setSpeed(rate)
    }

    fun seekToEvent(eventId: Long) {
        val script = (base.value as? BaseState.Ready)?.script ?: return
        val event = script.events.firstOrNull { it.id == eventId } ?: return
        player.seekTo(event.start.millis)
    }

    fun attachMedia(uri: String) {
        viewModelScope.launch {
            repo.setMediaUri(projectId, uri)
            player.setMedia(uri)
            (base.value as? BaseState.Ready)?.let { base.value = it.copy(hasMedia = true) }
        }
    }

    override fun onCleared() {
        player.release()
    }

    /** 测试专用：触发 onCleared 等价的播放器释放。 */
    internal fun releaseForTest() {
        player.release()
    }

    private sealed interface BaseState {
        object Loading : BaseState
        data class Ready(val script: AssScript, val hasMedia: Boolean) : BaseState
        data class Error(val message: String) : BaseState
    }
}
