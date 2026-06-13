package io.github.samgum.aegisub.feature.preview.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.samgum.aegisub.domain.audio.Waveform
import io.github.samgum.aegisub.domain.audio.WaveformDownsampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * 音频波形提取器：从媒体 URI 解码音频并降采样为波形。
 *
 * @author 伤感咩吖
 */
interface WaveformExtractor {
    /** 提取并降采样为 bucketCount 柱波形；无音频轨/失败返回 null。 */
    suspend fun extract(uri: String, bucketCount: Int): Waveform?
}

/**
 * 基于 MediaExtractor + MediaCodec 同步解码的实现。
 *
 * 注意：当前整段 PCM 读完再降采样——短视频（打轴常用）安全；
 * 超长视频（数十分钟）有 OOM 风险，后续可改流式降采样。
 *
 * @author 伤感咩吖
 */
class MediaCodecWaveformExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) : WaveformExtractor {

    override suspend fun extract(uri: String, bucketCount: Int): Waveform? = withContext(Dispatchers.Default) {
        val durationMs = readDurationMs(uri)
        val samples = decodeToPcm(uri) ?: return@withContext null
        Waveform(WaveformDownsampler.downsample(samples, bucketCount), durationMs)
    }

    private fun decodeToPcm(uri: String): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val (trackIndex, format) = selectAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val timeoutUs = 10_000L
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
                    if (outBuf != null && info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(chunk, 0, info.size)
                        pcm.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }

            val bytes = pcm.toByteArray()
            val shorts = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            shorts
        } catch (e: Exception) {
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
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

    private fun readDurationMs(uri: String): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val (_, format) = selectAudioTrack(extractor) ?: return 0L
            if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
