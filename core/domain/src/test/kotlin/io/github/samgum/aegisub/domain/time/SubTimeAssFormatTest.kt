package io.github.samgum.aegisub.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals

class SubTimeAssFormatTest {
    @Test fun formats_ass_centisecond() {
        // 1m23.45s = 83.45s = 83450ms = 83_450_000µs
        val t = SubTime.ofMillis(83_450)
        assertEquals("0:01:23.45", t.toAssString(msPrecision = false))
    }
    @Test fun formats_ass_millisecond_precision() {
        val t = SubTime.ofMillis(83_456)
        assertEquals("0:01:23.456", t.toAssString(msPrecision = true))
    }
    @Test fun formats_hours_single_digit() {
        val t = SubTime.ofMillis(3 * 3_600_000L + 0)
        assertEquals("3:00:00.00", t.toAssString(msPrecision = false))
    }
    @Test fun parses_ass_centisecond() {
        assertEquals(83_450_000L, SubTime.parseAss("0:01:23.45").micros)
    }
    @Test fun parses_ass_tolerant_comma_and_extra_colons() {
        // SRT 风格 HH:MM:SS,mmm 也能被通用解析吃下
        assertEquals(83_456_000L, SubTime.parseAss("0:01:23,456").micros)
    }
}
