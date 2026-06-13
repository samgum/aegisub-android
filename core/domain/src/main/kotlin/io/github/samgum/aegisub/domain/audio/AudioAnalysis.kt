package io.github.samgum.aegisub.domain.audio

/**
 * 频谱：时间帧 × 频率 bin 的归一化能量矩阵（每值 0..1）+ 总时长（毫秒）。
 * 由 [Spectrogram] 从单声道 PCM 经 STFT 得到，供频谱热图渲染。
 *
 * @author 伤感咩吖
 */
class SpectrogramData(val frames: Array<FloatArray>, val durationMs: Long) {
    val frameCount: Int get() = frames.size
    val binCount: Int get() = if (frames.isEmpty()) 0 else frames[0].size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpectrogramData) return false
        if (durationMs != other.durationMs || frames.size != other.frames.size) return false
        for (i in frames.indices) if (!frames[i].contentEquals(other.frames[i])) return false
        return true
    }

    override fun hashCode(): Int {
        var h = durationMs.hashCode()
        for (f in frames) h = h * 31 + f.contentHashCode()
        return h
    }

    override fun toString(): String = "SpectrogramData(frames=${frames.size}, bins=$binCount, durationMs=$durationMs)"
}

/**
 * 一次音频解码的完整分析结果：峰值波形 + 频谱 + 时长。
 * 两者共享同一遍解码，避免重复 IO/解码。
 *
 * @author 伤感咩吖
 */
class AudioAnalysis(val waveform: Waveform, val spectrogram: SpectrogramData) {
    override fun toString(): String = "AudioAnalysis($waveform, $spectrogram)"
}
