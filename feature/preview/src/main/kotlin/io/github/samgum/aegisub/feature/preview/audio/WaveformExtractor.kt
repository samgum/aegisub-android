package io.github.samgum.aegisub.feature.preview.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.samgum.aegisub.domain.audio.AudioAnalysis
import io.github.samgum.aegisub.domain.audio.Spectrogram
import io.github.samgum.aegisub.domain.audio.SpectrogramData
import io.github.samgum.aegisub.domain.audio.Waveform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.abs

/**
 * 音频波形/频谱与视频关键帧提取器：
 * - 流式分桶提取高精度峰值波形（零整段 PCM OOM 风险，支持 2 小时以上长片与 96kHz Hi-Res 音频）
 * - 快速扫描视频轨道 `SAMPLE_FLAG_SYNC` 提取全部关键帧毫秒时间戳，直接驱动桌面级关键帧吸附
 *
 * @author 伤感咩吖
 */
interface WaveformExtractor {
    suspend fun extract(uri: String, bucketCount: Int): Waveform?
    suspend fun extractFull(uri: String, bucketCount: Int, frameCount: Int, bins: Int): AudioAnalysis?
    suspend fun extractKeyframesMs(uri: String): List<Long> = emptyList()
}

class MediaCodecWaveformExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) : WaveformExtractor {

    override suspend fun extract(uri: String, bucketCount: Int): Waveform? = withContext(Dispatchers.Default) {
        extractStreamingAnalysis(uri, bucketCount.coerceAtLeast(256), computeSpectrogram = false, frameCount = 0, bins = 0)?.waveform
    }

    override suspend fun extractFull(
        uri: String,
        bucketCount: Int,
        frameCount: Int,
        bins: Int,
    ): AudioAnalysis? = withContext(Dispatchers.Default) {
        extractStreamingAnalysis(
            uri = uri,
            bucketCount = bucketCount.coerceAtLeast(512),
            computeSpectrogram = true,
            frameCount = frameCount.coerceAtLeast(128),
            bins = bins.coerceAtLeast(32),
        )
    }

    override suspend fun extractKeyframesMs(uri: String): List<Long> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            var videoTrack = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoTrack = i
                    break
                }
            }
            if (videoTrack < 0) return@withContext emptyList()
            extractor.selectTrack(videoTrack)
            val keyframes = ArrayList<Long>(512)
            var guard = 0
            while (guard < 500_000) {
                val flags = extractor.sampleFlags
                val timeUs = extractor.sampleTime
                if (timeUs < 0) break
                if ((flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    keyframes.add(timeUs / 1_000L)
                }
                if (!extractor.advance()) break
                guard++
            }
            keyframes.distinct().sorted()
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun extractStreamingAnalysis(
        uri: String,
        bucketCount: Int,
        computeSpectrogram: Boolean,
        frameCount: Int,
        bins: Int,
    ): AudioAnalysis? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val (trackIndex, format) = selectAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 1
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1_000L)
            } else 0L

            val peaks = FloatArray(bucketCount)
            // 频谱降采样缓冲：最多保留约 262,144 个单声道样本用于全局概览频谱，杜绝长视频 OOM
            val maxSpecSamples = 262_144
            val specRing = if (computeSpectrogram) FloatArray(maxSpecSamples) else FloatArray(0)
            var specWriteCount = 0
            var totalFramesDecoded = 0L
            var lastPtsUs = 0L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val timeoutUs = 10_000L

            // 若容器未给出 durationUs，用临时块列表收集峰值并在解码完成后重分桶
            val fallbackChunkPeaks = if (durationUs <= 0L) ArrayList<Float>(2048) else null

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, timeoutUs)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size >= 2) {
                        lastPtsUs = maxOf(lastPtsUs, info.presentationTimeUs)
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shortBuf = outBuf.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val totalShorts = shortBuf.remaining()
                        val framesHere = totalShorts / channels
                        var chunkMax = 0f

                        for (f in 0 until framesHere) {
                            var monoSum = 0
                            var maxAbs = 0
                            for (c in 0 until channels) {
                                val v = shortBuf.get(f * channels + c).toInt()
                                monoSum += v
                                val av = abs(v)
                                if (av > maxAbs) maxAbs = av
                            }
                            val normPeak = (maxAbs / 32_768f).coerceIn(0f, 1f)
                            if (normPeak > chunkMax) chunkMax = normPeak

                            if (durationUs > 0L) {
                                val estUs = info.presentationTimeUs + (f * 1_000_000L) / 48_000L
                                val bIdx = ((estUs.toDouble() / durationUs.toDouble()) * bucketCount)
                                    .toInt().coerceIn(0, bucketCount - 1)
                                if (normPeak > peaks[bIdx]) peaks[bIdx] = normPeak
                            }

                            if (computeSpectrogram && (totalFramesDecoded % 8L == 0L) && specWriteCount < maxSpecSamples) {
                                specRing[specWriteCount++] = ((monoSum / channels) / 32_768f).coerceIn(-1f, 1f)
                            }
                            totalFramesDecoded++
                        }
                        fallbackChunkPeaks?.add(chunkMax)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                }
            }

            if (fallbackChunkPeaks != null && fallbackChunkPeaks.isNotEmpty()) {
                for (b in 0 until bucketCount) {
                    val s = (b * fallbackChunkPeaks.size) / bucketCount
                    val e = (((b + 1) * fallbackChunkPeaks.size) / bucketCount).coerceAtLeast(s + 1)
                    var m = 0f
                    for (k in s until e.coerceAtMost(fallbackChunkPeaks.size)) {
                        if (fallbackChunkPeaks[k] > m) m = fallbackChunkPeaks[k]
                    }
                    peaks[b] = m
                }
            }

            val finalDurationMs = (if (durationUs > 0L) durationUs else lastPtsUs) / 1_000L
            val waveform = Waveform(peaks, finalDurationMs)
            val specData = if (computeSpectrogram && specWriteCount > SPECTROGRAM_FFT_SIZE) {
                val monoSlice = specRing.copyOf(specWriteCount)
                val hop = ((monoSlice.size - SPECTROGRAM_FFT_SIZE) / (frameCount - 1).coerceAtLeast(1)).coerceAtLeast(1)
                val frames = Spectrogram.compute(monoSlice, fftSize = SPECTROGRAM_FFT_SIZE, hop = hop, bins = bins)
                SpectrogramData(frames, finalDurationMs)
            } else {
                SpectrogramData(emptyArray(), finalDurationMs)
            }
            AudioAnalysis(waveform, specData)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i to format
        }
        return null
    }

    private companion object {
        const val SPECTROGRAM_FFT_SIZE = 512
    }
}
