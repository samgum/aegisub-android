package io.github.samgum.aegisub.domain.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Fft 测试：直流/单频正弦/能量守恒（帕塞瓦尔）。
 *
 * @author 伤感咩吖
 */
class FftTest {

    @Test fun dc_signal_concentrates_at_bin_zero() {
        val n = 8
        val real = FloatArray(n) { 1f }
        val imag = FloatArray(n)
        Fft.fft(real, imag)
        // 直流 → bin 0 = n，其余 ≈ 0
        assertEquals(8f, real[0], 1e-4f)
        for (i in 1 until n) assertEquals(0f, real[i], 1e-4f)
    }

    @Test fun pure_sine_peaks_at_correct_bin() {
        // n=16，频率 k=2（一个完整周期 8 样本，整窗含 2 周期）→ 峰在 bin 2 与 bin 14
        val n = 16
        val real = FloatArray(n) { i -> cos(2 * PI * 2 * i / n).toFloat() }
        val imag = FloatArray(n)
        Fft.fft(real, imag)
        val mag = FloatArray(n) { sqrt(real[it] * real[it] + imag[it] * imag[it]) }
        val peak = (1 until n / 2).maxByOrNull { mag[it] }!!
        assertEquals(2, peak)
        // bin 2 幅度应明显大于相邻 bin
        assertTrue(mag[2] > mag[1] && mag[2] > mag[3])
    }

    @Test fun parseval_energy_conservation() {
        // 时域能量 Σ|x|² ≈ 频域能量 Σ|X|²/n
        val n = 32
        val real = FloatArray(n) { i -> cos(2 * PI * 3 * i / n).toFloat() }
        val imag = FloatArray(n)
        val timeEnergy = real.indices.sumOf { (real[it] * real[it]).toDouble() }
        Fft.fft(real, imag)
        val freqEnergy = real.indices.sumOf {
            (real[it] * real[it] + imag[it] * imag[it]).toDouble()
        } / n
        assertEquals(timeEnergy, freqEnergy, 1e-3)
    }
}
