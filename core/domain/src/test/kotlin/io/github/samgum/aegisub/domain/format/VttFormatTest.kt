package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * VttFormat 测试：WebVTT 头部、`.` 时间分隔、标签剥离、读回。
 *
 * @author 伤感咩吖
 */
class VttFormatTest {

    private fun script(text: String) = AssScript(
        events = persistentListOf(
            AssEvent(start = SubTime.ofMillis(1_000), end = SubTime.ofMillis(3_000), text = text),
        ),
    )

    @Test fun write_produces_webvtt_header() {
        val out = VttFormat.write(script("Hi"))
        assertTrue(out.startsWith("WEBVTT\n"))
    }

    @Test fun write_uses_period_separator() {
        val out = VttFormat.write(script("Hi"))
        assertTrue(out.contains("00:00:01.000 --> 00:00:03.000"), out)
    }

    @Test fun write_strips_override_tags() {
        val out = VttFormat.write(script("{\\b1}粗{\\b0}体"))
        assertTrue(out.contains("粗体"))
        assertTrue(!out.contains("\\b"))
    }

    @Test fun write_no_srt_sequence_numbers() {
        val out = VttFormat.write(script("Hi"))
        // 第一行是 WEBVTT，不应有 SRT 风格的序号 1
        assertEquals("WEBVTT", out.lineSequence().first().trim())
    }

    @Test fun read_parses_cues() {
        val content = """
            WEBVTT

            00:00:01.000 --> 00:00:03.000
            Hello
        """.trimIndent()
        val script = VttFormat.read(content, ReadOptions())
        assertEquals(1, script.events.size)
        assertEquals(1_000L, script.events.first().start.millis)
        assertEquals(3_000L, script.events.first().end.millis)
        assertEquals("Hello", script.events.first().text)
    }

    @Test fun canRead_detects_webvtt_header() {
        assertTrue(VttFormat.canRead("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi"))
        assertTrue(!VttFormat.canRead("1\n00:00:01,000 --> 00:00:02,000\nHi"))
    }

    @Test fun round_trip_write_then_read() {
        val src = AssScript(
            events = persistentListOf(
                AssEvent(start = SubTime.ofMillis(2_000), end = SubTime.ofMillis(4_000), text = "World"),
            ),
        )
        val out = VttFormat.write(src)
        val back = VttFormat.read(out, ReadOptions())
        assertEquals(1, back.events.size)
        assertEquals(2_000L, back.events.first().start.millis)
        assertEquals("World", back.events.first().text)
    }
}
