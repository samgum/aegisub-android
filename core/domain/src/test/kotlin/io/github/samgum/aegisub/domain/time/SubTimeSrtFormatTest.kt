package io.github.samgum.aegisub.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals

class SubTimeSrtFormatTest {
    @Test fun formats_srt() {
        assertEquals("00:01:23,456", SubTime.ofMillis(83_456).toSrtString())
        assertEquals("03:00:00,000", SubTime.ofMillis(3 * 3_600_000L).toSrtString())
    }
    @Test fun parses_srt() {
        assertEquals(83_456_000L, SubTime.parseSrt("00:01:23,456").micros)
    }
    @Test fun round_trip_srt() {
        val t = SubTime.ofMillis(7_123)
        assertEquals(t.micros, SubTime.parseSrt(t.toSrtString()).micros)
    }
}
