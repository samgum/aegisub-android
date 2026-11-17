package io.github.samgum.aegisub.feature.preview

import android.content.Context
import android.os.SystemClock
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
 * Media3 ExoPlayer + Dummy Video 双模播放引擎：
 * - 挂载真实音视频时：支持 0.25x–4.0x 保调倍速、BT.2020/HDR10/HLG/Dolby Vision 识别与 1080p 降采样、区间自动停播
 * - 未挂载真实媒体或主动开启 Dummy Video 时：内置高精度单调虚拟时钟，使 `\move`、`\fad`、`\t`、卡拉OK与滚动歌词无需视频文件即可实时播放预览！
 *
 * @author 伤感咩吖
 */
@androidx.annotation.OptIn(UnstableApi::class)
class Media3VideoPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : VideoPlayer {

    private val trackSelector = DefaultTrackSelector(context)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .build()

    private val _state = MutableStateFlow(
        PlaybackState(
            isReady = true,
            isDummyVideo = true,
            fps = 23.976f,
            videoWidth = 1920,
            videoHeight = 1080,
            durationMs = 600_000L,
        ),
    )
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null
    private var dummyClockJob: Job? = null

    @Volatile
    private var stopAtPositionMs: Long? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (_state.value.isDummyVideo) return
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startPolling()
                } else {
                    stopAtPositionMs = null
                    stopPolling()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (_state.value.isDummyVideo) return
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
                if (_state.value.isDummyVideo) return
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
        val fps = if (formatFps > 0f) formatFps else 23.976f

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
        stopDummyClock()
        stopAtPositionMs = null
        _state.value = _state.value.copy(
            isDummyVideo = false,
            isPlaying = false,
            isHdr = false,
            colorInfoLabel = "",
        )
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }

    override fun openDummyVideo(
        width: Int,
        height: Int,
        fps: Float,
        durationMs: Long,
        colorArgb: Int,
        checkerboard: Boolean,
    ) {
        stopPolling()
        stopDummyClock()
        stopAtPositionMs = null
        exoPlayer.stop()
        _state.value = _state.value.copy(
            isReady = true,
            isPlaying = false,
            isDummyVideo = true,
            videoWidth = width.coerceIn(64, 7680),
            videoHeight = height.coerceIn(64, 4320),
            fps = fps.coerceIn(1f, 240f),
            durationMs = durationMs.coerceIn(1_000L, 35_999_990L),
            positionMs = _state.value.positionMs.coerceIn(0L, durationMs.coerceAtLeast(1_000L)),
            isHdr = false,
            colorInfoLabel = "Dummy Video",
            dummyColorArgb = colorArgb,
            dummyCheckerboard = checkerboard,
        )
    }

    override fun play() {
        stopAtPositionMs = null
        if (_state.value.isDummyVideo) {
            startDummyClock()
        } else {
            exoPlayer.play()
        }
    }

    override fun playRange(startMs: Long, endMs: Long) {
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtLeast(safeStart + 20L)
        stopAtPositionMs = safeEnd
        if (_state.value.isDummyVideo) {
            if (safeEnd > _state.value.durationMs) {
                _state.value = _state.value.copy(durationMs = safeEnd + 5_000L)
            }
            _state.value = _state.value.copy(positionMs = safeStart)
            startDummyClock()
        } else {
            exoPlayer.seekTo(safeStart)
            updatePosition()
            exoPlayer.play()
        }
    }

    override fun pause() {
        stopAtPositionMs = null
        if (_state.value.isDummyVideo) {
            stopDummyClock()
        } else {
            exoPlayer.pause()
        }
    }

    override fun seekTo(positionMs: Long) {
        stopAtPositionMs = null
        val clamped = positionMs.coerceAtLeast(0L)
        if (_state.value.isDummyVideo) {
            val dur = maxOf(_state.value.durationMs, clamped + 1_000L)
            _state.value = _state.value.copy(positionMs = clamped, durationMs = dur)
        } else {
            exoPlayer.seekTo(clamped)
            updatePosition()
        }
    }

    override fun setSpeed(rate: Float) {
        val clamped = rate.coerceIn(0.25f, 4.0f)
        _state.value = _state.value.copy(speed = clamped)
        if (!_state.value.isDummyVideo) {
            exoPlayer.playbackParameters = PlaybackParameters(clamped, 1.0f)
        }
    }

    override fun seekNextFrame() {
        stopAtPositionMs = null
        val fps = _state.value.fps.let { if (it > 0f) it else 23.976f }
        val frameMs = (1000.0f / fps).toLong().coerceAtLeast(1L)
        if (_state.value.isDummyVideo) {
            stopDummyClock()
            val maxPos = _state.value.durationMs.coerceAtLeast(0L)
            _state.value = _state.value.copy(positionMs = (_state.value.positionMs + frameMs).coerceAtMost(maxPos))
        } else {
            if (!_state.value.isReady) return
            exoPlayer.pause()
            val maxPos = exoPlayer.duration.coerceAtLeast(0L)
            val target = (exoPlayer.currentPosition + frameMs).coerceAtMost(maxPos)
            exoPlayer.seekTo(target)
            updatePosition()
        }
    }

    override fun seekPreviousFrame() {
        stopAtPositionMs = null
        val fps = _state.value.fps.let { if (it > 0f) it else 23.976f }
        val frameMs = (1000.0f / fps).toLong().coerceAtLeast(1L)
        if (_state.value.isDummyVideo) {
            stopDummyClock()
            _state.value = _state.value.copy(positionMs = (_state.value.positionMs - frameMs).coerceAtLeast(0L))
        } else {
            if (!_state.value.isReady) return
            exoPlayer.pause()
            val target = (exoPlayer.currentPosition - frameMs).coerceAtLeast(0L)
            exoPlayer.seekTo(target)
            updatePosition()
        }
    }

    override fun release() {
        stopAtPositionMs = null
        stopPolling()
        stopDummyClock()
        scope.cancel()
        exoPlayer.release()
    }

    private fun startDummyClock() {
        if (dummyClockJob?.isActive == true) return
        _state.value = _state.value.copy(isPlaying = true)
        dummyClockJob = scope.launch {
            var lastRealtime = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val now = SystemClock.elapsedRealtime()
                val elapsed = ((now - lastRealtime) * _state.value.speed).toLong()
                lastRealtime = now
                val cur = _state.value
                val nextPos = cur.positionMs + elapsed
                val limit = stopAtPositionMs
                if (limit != null && nextPos >= limit) {
                    stopAtPositionMs = null
                    _state.value = cur.copy(positionMs = limit, isPlaying = false)
                    break
                }
                if (nextPos >= cur.durationMs) {
                    _state.value = cur.copy(positionMs = cur.durationMs, isPlaying = false)
                    break
                }
                _state.value = cur.copy(positionMs = nextPos)
            }
        }
    }

    private fun stopDummyClock() {
        dummyClockJob?.cancel()
        dummyClockJob = null
        if (_state.value.isDummyVideo && _state.value.isPlaying) {
            _state.value = _state.value.copy(isPlaying = false)
        }
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
