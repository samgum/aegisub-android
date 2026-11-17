package io.github.samgum.aegisub.feature.preview

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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
 * Media3 ExoPlayer 专业版封装（复刻 samgum/Aegisub SGMY 视频与音频引擎特性）：
 * - 支持 **0.25x–4.0x 保调变速**（`PlaybackParameters(rate, 1.0f)`）
 * - 自动识别 **BT.2020 / HDR10 (PQ) / HLG / Dolby Vision** 色彩空间并支持 1080p 预览降采样优化
 * - 支持桌面 Aegisub 标志性 **区间自动停播 (`playRange`)**（播放选中行、首尾 500ms、前后导试听）
 * - 25ms 高刷采样驱动 `\move` / `\t` / 卡拉OK `\kf` 扫词与音频波形游标顺滑跟手
 *
 * @author 伤感咩吖
 */
@OptIn(UnstableApi::class)
class Media3VideoPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : VideoPlayer {

    private val trackSelector = DefaultTrackSelector(context)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null

    @Volatile
    private var stopAtPositionMs: Long? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startPolling()
                } else {
                    stopAtPositionMs = null
                    stopPolling()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val duration = exoPlayer.duration.coerceAtLeast(0L)
                    _state.value = _state.value.copy(
                        isReady = true,
                        durationMs = duration,
                        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    )
                    inspectVideoFormat()
                } else if (playbackState == Player.STATE_ENDED) {
                    stopAtPositionMs = null
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _state.value = _state.value.copy(speed = playbackParameters.speed)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _state.value = _state.value.copy(
                    videoWidth = videoSize.width,
                    videoHeight = videoSize.height,
                )
                inspectVideoFormat()
            }
        })
    }

    private fun inspectVideoFormat() {
        val vf = exoPlayer.videoFormat
        val formatFps = vf?.frameRate ?: Format.NO_VALUE.toFloat()
        val fps = if (formatFps > 0f) formatFps else 0f

        val colorInfo = vf?.colorInfo
        val isBt2020 = colorInfo?.colorSpace == C.COLOR_SPACE_BT2020
        val transfer = colorInfo?.colorTransfer ?: Format.NO_VALUE
        val isPq = transfer == C.COLOR_TRANSFER_ST2084
        val isHlg = transfer == C.COLOR_TRANSFER_HLG
        val isDv = vf?.sampleMimeType?.contains("dolby-vision", ignoreCase = true) == true
        val isHdr = isBt2020 || isPq || isHlg || isDv
        val colorLabel = when {
            isDv -> "Dolby Vision"
            isPq -> "HDR10 (PQ / BT.2020)"
            isHlg -> "HLG (BT.2020)"
            isBt2020 -> "BT.2020"
            vf != null && (vf.width > 0 || vf.height > 0) -> "SDR (BT.709)"
            else -> ""
        }

        _state.value = _state.value.copy(
            fps = fps,
            videoWidth = vf?.width?.takeIf { it > 0 } ?: _state.value.videoWidth,
            videoHeight = vf?.height?.takeIf { it > 0 } ?: _state.value.videoHeight,
            isHdr = isHdr,
            colorInfoLabel = colorLabel,
        )
    }

    override fun setHdrPreviewOptimize(enabled: Boolean) {
        trackSelector.parameters = if (enabled) {
            trackSelector.buildUponParameters()
                .setMaxVideoSize(1920, 1080)
                .build()
        } else {
            trackSelector.buildUponParameters()
                .clearVideoSizeConstraints()
                .build()
        }
    }

    override fun setMedia(uri: String) {
        stopAtPositionMs = null
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }

    override fun play() {
        stopAtPositionMs = null
        exoPlayer.play()
    }

    override fun playRange(startMs: Long, endMs: Long) {
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtLeast(safeStart + 20L)
        stopAtPositionMs = safeEnd
        exoPlayer.seekTo(safeStart)
        updatePosition()
        exoPlayer.play()
    }

    override fun pause() {
        stopAtPositionMs = null
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        stopAtPositionMs = null
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
        updatePosition()
    }

    override fun setSpeed(rate: Float) {
        // SGMY 保调变速：0.25x–4.0x，pitch 固定 1.0f 保持原调
        val clamped = rate.coerceIn(0.25f, 4.0f)
        exoPlayer.playbackParameters = PlaybackParameters(clamped, 1.0f)
    }

    override fun seekNextFrame() {
        if (!_state.value.isReady) return
        stopAtPositionMs = null
        exoPlayer.pause()
        val fps = _state.value.fps.let { if (it > 0f) it else 30f }
        val frameMs = (1000.0f / fps).toLong().coerceAtLeast(1L)
        val maxPos = exoPlayer.duration.coerceAtLeast(0L)
        val target = (exoPlayer.currentPosition + frameMs).coerceAtMost(maxPos)
        exoPlayer.seekTo(target)
        updatePosition()
    }

    override fun seekPreviousFrame() {
        if (!_state.value.isReady) return
        stopAtPositionMs = null
        exoPlayer.pause()
        val fps = _state.value.fps.let { if (it > 0f) it else 30f }
        val frameMs = (1000.0f / fps).toLong().coerceAtLeast(1L)
        val target = (exoPlayer.currentPosition - frameMs).coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        updatePosition()
    }

    override fun release() {
        stopAtPositionMs = null
        stopPolling()
        scope.cancel()
        exoPlayer.release()
    }

    private fun updatePosition() {
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        _state.value = _state.value.copy(positionMs = pos)
        val limit = stopAtPositionMs
        if (limit != null && pos >= limit) {
            stopAtPositionMs = null
            exoPlayer.pause()
            exoPlayer.seekTo(limit)
            _state.value = _state.value.copy(positionMs = limit, isPlaying = false)
        }
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
        const val POLL_INTERVAL_MS = 25L
    }
}
