package io.github.samgum.aegisub.feature.preview

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ExoPlayer 封装：监听状态/参数 → 回填 [state]；播放时协程周期(≈50ms)采样位置。
 * [exoPlayer] 暴露给 PlayerView 绑定（仅预览模块内使用）。
 *
 * @author 伤感咩吖
 */
class Media3VideoPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : VideoPlayer {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) startPolling() else stopPolling()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val duration = exoPlayer.duration.coerceAtLeast(0L)
                    _state.value = _state.value.copy(
                        isReady = true,
                        durationMs = duration,
                        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    )
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _state.value = _state.value.copy(speed = playbackParameters.speed)
            }
        })
    }

    override fun setMedia(uri: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }

    override fun play() = exoPlayer.play()

    override fun pause() = exoPlayer.pause()

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        updatePosition()
    }

    override fun setSpeed(rate: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(rate)
    }

    override fun release() {
        stopPolling()
        scope.cancel()
        exoPlayer.release()
    }

    private fun updatePosition() {
        _state.value = _state.value.copy(positionMs = exoPlayer.currentPosition.coerceAtLeast(0L))
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                updatePosition()
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 50L
    }
}
