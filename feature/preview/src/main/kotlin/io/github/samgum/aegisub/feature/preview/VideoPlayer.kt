package io.github.samgum.aegisub.feature.preview

import kotlinx.coroutines.flow.StateFlow

/**
 * 播放器状态快照（驱动 UI 与当前行派生）。
 *
 * @author 伤感咩吖
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val isReady: Boolean = false,
)

/**
 * 视频播放器抽象。真实实现封装 ExoPlayer；测试用假实现。
 * 位置采样在实现内部以协程周期更新 [state]，UI 与 VM 只订阅不反写。
 *
 * @author 伤感咩吖
 */
interface VideoPlayer {
    val state: StateFlow<PlaybackState>
    fun setMedia(uri: String)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(rate: Float)
    fun release()
}
