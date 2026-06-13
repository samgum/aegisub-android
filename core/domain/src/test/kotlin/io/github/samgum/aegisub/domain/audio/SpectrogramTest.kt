package io.github.samgum.aegisub.domain.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos

/**
 * Spectrogram 测试：帧数/形状/静音归零/单频能量集中/空输入容错。
 *
 * @author 伤感咩吖
 */
class SpectrogramTest {

    @Test fun empty_pcm_returns_empty() {
        val sg = Spectrogram.compute(FloatArray(0))
        assertEquals(0, sg.size)
    }

    @Test fun frame_count_from_hop() {
        // fftSize=8, hop=4, pcm 长度 20 → 1 + (20-8)/4 = 4 帧
        val sg = Spectrogram.compute(FloatArray(20), fftSize = 8, hop = 4, bins = 4)
        assertEquals(4, sg.size)
        assertEquals(4, sg[0].size)
    }

    @Test fun shorter_than_window_produces_single_frame() {
        val sg = Spectrogram.compute(FloatArray(3), fftSize = 8, hop = 4, bins = 4)
        assertEquals(1, sg.size)
    }

    @Test fun silence_is_near_zero() {
        val sg = Spectrogram.compute(FloatArray(64), fftSize = 16, hop = 8, bins = 8)
        sg.forEach { frame -> frame.forEach { v -> assertTrue(v < 0.05f, "静音应近 0：$v") } }
    }

    @Test fun values_in_unit_range() {
        val pcm = FloatArray(256) { i -> cos(2 * PI * 4 * i / 16).toFloat() }
        val sg = Spectrogram.compute(pcm, fftSize = 32, hop = 16, bins = 16)
        sg.forEach { frame -> frame.forEach { v -> assertTrue(v in 0f..1f, "归一值越界：$v") } }
    }

    @Test fun tonal_signal_concentrates_energy_in_low_bins() {
        // 低频单音（每 64 样本一周期）→ 能量集中在 bin 0~2，高频 bin 显著更弱
        val pcm = FloatArray(512) { i -> cos(2 * PI * 1 * i / 64).toFloat() }
        val sg = Spectrogram.compute(pcm, fftSize = 128, hop = 64, bins = 64)
        val mid = sg[sg.size / 2]
        val lowEnergy = mid[0] + mid[1] + mid[2]
        val highEnergy = mid[40] + mid[45] + mid[50]
        assertTrue(lowEnergy > highEnergy, "低频能量应高于高频：$lowEnergy vs $highEnergy")
    }
}
