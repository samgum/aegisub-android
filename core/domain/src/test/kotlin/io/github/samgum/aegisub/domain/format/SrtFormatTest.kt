package io.github.samgum.aegisub.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SrtFormatTest {
    private val sample = "1\n00:00:01,000 --> 00:00:03,000\nHello world\n\n2\n00:00:04,000 --> 00:00:06,500\nSecond\n"

    @Test fun reads_srt_blocks() {
        val s = SrtFormat.read(sample)
        assertEquals(2, s.events.size)
        assertEquals(1_000_000L, s.events[0].start.micros)
        assertEquals(3_000_000L, s.events[0].end.micros)
        assertEquals("Hello world", s.events[0].text)
        assertEquals(6_500_000L, s.events[1].end.micros)
    }
    @Test fun writes_srt_round_trip() {
        val reparsed = SrtFormat.read(SrtFormat.write(SrtFormat.read(sample)))
        assertEquals(2, reparsed.events.size)
        assertEquals("Second", reparsed.events[1].text)
        assertEquals(6_500_000L, reparsed.events[1].end.micros)
    }
    @Test fun can_read_detects_srt() {
        assertTrue(SrtFormat.canRead("1\n00:00:01,000 --> 00:00:02,000\n"))
    }
}
