package io.github.samgum.aegisub.domain.audio

/**
 * 音频波形：归一化峰值数组（每柱 0..1）+ 总时长（毫秒）。
 * 由 [WaveformDownsampler] 从 16-bit PCM 降采样得到，供波形条渲染。
 *
 * @author 伤感咩吖
 */
class Waveform(val peaks: FloatArray, val durationMs: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Waveform) return false
        return durationMs == other.durationMs && peaks.contentEquals(other.peaks)
    }

    override fun hashCode(): Int = peaks.contentHashCode() * 31 + durationMs.hashCode()

    override fun toString(): String = "Waveform(peaks=${peaks.size}, durationMs=$durationMs)"
}
