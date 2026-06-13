package io.github.samgum.aegisub.domain.audio

import kotlin.math.abs
import kotlin.math.max

/**
 * PCM 采样降采样为波形峰值。纯函数，无 Android 依赖，可纯 JVM 单测。
 *
 * @author 伤感咩吖
 */
object WaveformDownsampler {

    /**
     * 把 16-bit PCM 采样降采样为 [bucketCount] 个归一化峰值（0..1）。
     * 每个 bucket 取区间内采样的最大绝对值 / [Short.MAX_VALUE]。
     *
     * 样本数少于 bucket 数时，逐样本取峰值、剩余 bucket 补 0。
     */
    fun downsample(samples: ShortArray, bucketCount: Int): FloatArray {
        if (samples.isEmpty() || bucketCount <= 0) return FloatArray(0)
        val peaks = FloatArray(bucketCount)
        val bucketSize = samples.size / bucketCount
        if (bucketSize == 0) {
            for (i in peaks.indices) {
                peaks[i] = if (i < samples.size) {
                    abs(samples[i].toInt()).toFloat() / Short.MAX_VALUE
                } else 0f
            }
            return peaks
        }
        for (b in 0 until bucketCount) {
            val start = b * bucketSize
            val end = if (b == bucketCount - 1) samples.size else start + bucketSize
            var peak = 0
            for (i in start until end) {
                peak = max(peak, abs(samples[i].toInt()))
            }
            peaks[b] = peak.toFloat() / Short.MAX_VALUE
        }
        return peaks
    }
}
