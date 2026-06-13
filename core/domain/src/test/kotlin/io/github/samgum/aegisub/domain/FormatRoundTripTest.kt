package io.github.samgum.aegisub.domain

import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.FormatRegistry
import io.github.samgum.aegisub.domain.format.LrcFormat
import io.github.samgum.aegisub.domain.format.SrtFormat
import io.github.samgum.aegisub.domain.format.TimePrecision
import io.github.samgum.aegisub.domain.format.WriteOptions
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 跨格式往返集成测试：验证解析→序列化→再解析的精度与一致性。
 *
 * @author 伤感咩吖
 */
class FormatRoundTripTest {
    private fun sampleScript() = AssScript.default().withEvents(
        listOf(
            AssEvent(start = SubTime.ofMillis(1_000), end = SubTime.ofMillis(3_000), text = "Hello"),
            AssEvent(start = SubTime.ofMillis(3_500), end = SubTime.ofMillis(6_500), text = "World"),
        )
    )

    @Test fun ass_round_trip_preserves_events() {
        val s = sampleScript()
        val reparsed = AssFormat.read(AssFormat.write(s))
        assertEquals(2, reparsed.events.size)
        assertEquals("Hello", reparsed.events[0].text)
        assertEquals(s.events[0].start, reparsed.events[0].start)
        assertEquals(s.events[1].end, reparsed.events[1].end)
    }

    @Test fun srt_round_trip_preserves_times() {
        val s = sampleScript()
        val reparsed = SrtFormat.read(SrtFormat.write(s))
        assertEquals(6_500_000L, reparsed.events[1].end.micros)
    }

    @Test fun lrc_round_trip_three_ms_precision() {
        val s = sampleScript()
        val out = LrcFormat.write(s, WriteOptions(timePrecision = TimePrecision.THREE_MS))
        val reparsed = LrcFormat.read(out)
        assertEquals(s.events[0].start, reparsed.events[0].start)
    }

    @Test fun registry_round_trips_each_format_by_name() {
        val names = listOf("ass", "srt", "lrc")
        names.forEach { n ->
            val fmt = assertNotNull(FormatRegistry.byName(n))
            val out = fmt.write(sampleScript())
            val reparsed = fmt.read(out)
            assertEquals(2, reparsed.events.size, "round-trip failed for $n")
        }
    }

    @Test fun mixed_lrc_format_detection() {
        // 混合 4 种时间格式的 LRC 仍能被识别并解析
        val mixed = "[00:01.00]a\n[00:02.000]b\n[00:03:00]c\n[00:04:000]d\n"
        val fmt = assertNotNull(FormatRegistry.detect(mixed))
        assertEquals("lrc", fmt.name)
        val s = fmt.read(mixed)
        assertEquals(4, s.events.size)
    }
}
