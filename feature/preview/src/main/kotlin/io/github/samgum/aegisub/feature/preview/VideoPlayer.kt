package io.github.samgum.aegisub.feature.preview

import kotlinx.coroutines.flow.StateFlow

/**
 * 播放器状态快照（驱动视频画面、HDR 色域徽标、音频播放头与活动字幕求值）。
 *
 * @author 伤感咩吖
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val isReady: Boolean = false,
    /** 视频帧率；0f 表示未知（用于逐帧步进与帧级微调）。 */
    val fps: Float = 0f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** SGMY HDR / BT.2020 / Dolby Vision 检测标记。 */
    val isHdr: Boolean = false,
    val colorInfoLabel: String = "",
)

/**
 * 视频/音频播放器抽象。真实实现封装 Media3 ExoPlayer；单测使用假实现。
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
    fun seekNextFrame()
    fun seekPreviousFrame()
    fun release()

    /**
     * 仿桌面 Aegisub 区间试听（播放当前行 / 前 500ms / 后 500ms / 行外前后试听）：
     * 跳转到 [startMs] 开始播放，到达 [endMs] 时自动暂停。
     */
    fun playRange(startMs: Long, endMs: Long) {
        seekTo(startMs.coerceAtLeast(0L))
        play()
    }

    /** SGMY 4K HDR 预览分辨率降采样开关（默认开启 1920×1080 上限降低解码与合成负载）。 */
    fun setHdrPreviewOptimize(enabled: Boolean) {}
}
