package io.github.samgum.aegisub.domain.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WaveformDownsampler 降采样测试。
 *
 * @author 伤感咩吖
 */
class WaveformDownsamplerTest {

    @Test fun empty_samples_returns_empty() {
        assertTrue(WaveformDownsampler.downsample(ShortArray(0), 100).isEmpty())
    }

    @Test fun zero_bucket_count_returns_empty() {
        assertTrue(WaveformDownsampler.downsample(ShortArray(10), 0).isEmpty())
    }

    @Test fun constant_amplitude_yields_uniform_peaks() {
        val samples = ShortArray(1000) { 16384 } // ≈ 0.5
        val peaks = WaveformDownsampler.downsample(samples, 10)
        assertEquals(10, peaks.size)
        peaks.forEach { assertEquals(0.5f, it, 0.01f) }
    }

    @Test fun silence_yields_zero_peaks() {
        val peaks = WaveformDownsampler.downsample(ShortArray(1000), 10)
        peaks.forEach { assertEquals(0f, it, 0.001f) }
    }

    @Test fun fewer_samples_than_buckets_pads_zero() {
        val peaks = WaveformDownsampler.downsample(ShortArray(3) { Short.MAX_VALUE }, 5)
        assertEquals(5, peaks.size)
        assertEquals(1f, peaks[0], 0.001f)
        assertEquals(1f, peaks[1], 0.001f)
        assertEquals(1f, peaks[2], 0.001f)
        assertEquals(0f, peaks[3], 0.001f)
        assertEquals(0f, peaks[4], 0.001f)
    }

    @Test fun full_scale_amplitude_normalizes_to_one() {
        val peaks = WaveformDownsampler.downsample(ShortArray(100) { Short.MAX_VALUE }, 5)
        peaks.forEach { assertEquals(1f, it, 0.001f) }
    }
}
