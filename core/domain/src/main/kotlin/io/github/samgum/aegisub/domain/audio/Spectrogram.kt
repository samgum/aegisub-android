package io.github.samgum.aegisub.domain.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 短时傅里叶频谱（STFT）：把单声道 PCM 切窗 → 逐窗 FFT → 取频率 bin 的对数幅度，
 * 产出「时间帧 × 频率 bin」的能量矩阵（每个值已归一化到 0..1）。
 *
 * 用于音频频谱热图渲染（复刻 Aegisub 频谱视图的时间×频率着色）。
 * 长度不足一帧的尾部用零补齐，保证最后一帧不丢。
 *
 * @author 伤感咩吖
 */
object Spectrogram {

    /**
     * @param pcm 单声道归一化样本 [-1,1]
     * @param fftSize 窗口长度（须为 2 的幂，默认 512）
     * @param hop 帧移（默认 fftSize/2，50% 重叠）
     * @param bins 返回的频率 bin 数（取低频前 [bins] 个，默认 64；高频对字幕用处小）
     * @return 行=时间帧、列=频率 bin 的能量矩阵，每值 0..1（对数缩放后线性归一）
     */
    fun compute(
        pcm: FloatArray,
        fftSize: Int = 512,
        hop: Int = fftSize / 2,
        bins: Int = 64,
    ): Array<FloatArray> {
        require(fftSize > 0 && fftSize and (fftSize - 1) == 0) { "fftSize 须为 2 的幂" }
        require(hop > 0) { "hop 须为正" }
        require(bins > 0 && bins <= fftSize / 2) { "bins 须在 (0, fftSize/2]" }
        if (pcm.isEmpty()) return emptyArray()

        val window = hannWindow(fftSize)
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val frameCount = if (pcm.size < fftSize) 1 else 1 + (pcm.size - fftSize) / hop
        val result = Array(frameCount) { FloatArray(bins) }

        var maxMag = 1e-10
        val raw = Array(frameCount) { DoubleArray(bins) }
        for (f in 0 until frameCount) {
            val start = f * hop
            for (i in 0 until fftSize) {
                val sample = if (start + i < pcm.size) pcm[start + i] else 0f
                real[i] = sample * window[i]
                imag[i] = 0f
            }
            Fft.fft(real, imag)
            for (b in 0 until bins) {
                val mag = sqrt((real[b] * real[b] + imag[b] * imag[b]).toDouble())
                raw[f][b] = mag
                if (mag > maxMag) maxMag = mag
            }
        }

        // 对数缩放（dB 感）后线性归一到 0..1，用全局最大值作分母
        val logMax = ln(maxMag + 1.0)
        for (f in 0 until frameCount) {
            for (b in 0 until bins) {
                val norm = ln(raw[f][b] + 1.0) / logMax
                result[f][b] = norm.coerceIn(0.0, 1.0).toFloat()
            }
        }
        return result
    }

    /** Hann 窗：0.5 - 0.5·cos(2π i/(N-1))，降低频谱泄漏。 */
    private fun hannWindow(n: Int): FloatArray = FloatArray(n) { i ->
        (0.5 - 0.5 * cos(2 * PI * i / (n - 1))).toFloat()
    }
}
