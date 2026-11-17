package io.github.samgum.aegisub.feature.preview

import kotlinx.coroutines.flow.StateFlow

/**
 * 播放器状态快照（支持真实音视频与桌面 Aegisub 虚拟视频 Dummy Video 时钟）。
 *
 * @author 伤感咩吖
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val isReady: Boolean = false,
    val fps: Float = 0f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val isHdr: Boolean = false,
    val colorInfoLabel: String = "",
    val isDummyVideo: Boolean = false,
    val dummyCheckerboard: Boolean = true,
    val dummyColorArgb: Int = 0xFF181E29.toInt(),
)

/**
 * 视频/音频播放器抽象（同时支持 Media3 硬解播放与无片源 Dummy Video 虚拟帧率时钟）。
 *
 * @author 伤感咩吖
 */
interface VideoPlayer {
    val state: StateFlow<PlaybackState>
    fun setMedia(uri: String)
    fun openDummyVideo(
        width: Int = 1920,
        height: Int = 1080,
        fps: Float = 23.976f,
        durationMs: Long = 600_000L,
        colorArgb: Int = 0xFF181E29.toInt(),
        checkerboard: Boolean = true,
    ) {}
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(rate: Float)
    fun seekNextFrame()
    fun seekPreviousFrame()
    fun release()

    fun playRange(startMs: Long, endMs: Long) {
        seekTo(startMs.coerceAtLeast(0L))
        play()
    }

    fun setHdrPreviewOptimize(enabled: Boolean) {}
}
