package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * FramerateConverter 帧率转换测试：等比缩放起止时间，非法帧率抛错，往返近似还原。
 *
 * @author 伤感咩吖
 */
class FramerateConverterTest {

    private fun ev(startMs: Long, endMs: Long, text: String = "x") = AssEvent(
        start = SubTime.ofMillis(startMs),
        end = SubTime.ofMillis(endMs),
        text = text,
    )

    @Test fun rescale_24_to_25_stretches_time_by_ratio() {
        // 24 → 25：因子 25/24，1s 起始 → 1041.67ms（取整 1041）
        val r = FramerateConverter.rescale(listOf(ev(1_000, 2_000)), fromFps = 24.0, toFps = 25.0)
        assertEquals(1_041L, r[0].start.millis)
        assertEquals(2_083L, r[0].end.millis)
    }

    @Test fun rescale_same_fps_is_identity() {
        val r = FramerateConverter.rescale(listOf(ev(1_234, 5_678)), 30.0, 30.0)
        assertEquals(1_234L, r[0].start.millis)
        assertEquals(5_678L, r[0].end.millis)
    }

    @Test fun rescale_down_30_to_24_compresses() {
        val r = FramerateConverter.rescale(listOf(ev(10_000, 20_000)), fromFps = 30.0, toFps = 24.0)
        // 因子 24/30 = 0.8
        assertEquals(8_000L, r[0].start.millis)
        assertEquals(16_000L, r[0].end.millis)
    }

    @Test fun rescale_preserves_text_and_other_fields() {
        val e = AssEvent(start = SubTime.ofMillis(1_000), end = SubTime.ofMillis(2_000), text = "Hi", style = "Title")
        val r = FramerateConverter.rescale(listOf(e), 24.0, 25.0)
        assertEquals("Hi", r[0].text)
        assertEquals("Title", r[0].style)
    }

    @Test fun rescale_round_trip_approximates_original() {
        // 24 → 25 → 24：微秒级四舍五入，误差 < 1ms
        val original = listOf(ev(1_500, 4_500))
        val to25 = FramerateConverter.rescale(original, 24.0, 25.0)
        val back = FramerateConverter.rescale(to25, 25.0, 24.0)
        // 1500ms * 25/24 * 24/25 = 1500ms（微秒级精确，因 toLong 在末步）
        assertEquals(1_500L, back[0].start.millis)
        assertEquals(4_500L, back[0].end.millis)
    }

    @Test fun rescale_invalid_from_fps_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            FramerateConverter.rescale(listOf(ev(0, 1)), fromFps = 0.0, toFps = 24.0)
        }
    }

    @Test fun rescale_invalid_to_fps_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            FramerateConverter.rescale(listOf(ev(0, 1)), fromFps = 24.0, toFps = -5.0)
        }
    }

    @Test fun rescale_fractional_fps_ntsc() {
        // NTSC 23.976 → 29.97：因子 29.97/23.976 = 1.25
        val r = FramerateConverter.rescale(listOf(ev(4_000, 8_000)), 23.976, 29.97)
        assertEquals(5_000L, r[0].start.millis)
        assertEquals(10_000L, r[0].end.millis)
    }

    @Test fun rescale_empty_list() {
        assertEquals(emptyList<AssEvent>(), FramerateConverter.rescale(emptyList(), 24.0, 25.0))
    }
}
