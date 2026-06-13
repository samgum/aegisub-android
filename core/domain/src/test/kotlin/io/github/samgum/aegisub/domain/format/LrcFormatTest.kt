package io.github.samgum.aegisub.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LrcFormatTest {
    private val sample = "[ti:Title]\n[ar:Artist]\n[00:01.00]first line\n[00:03.50]second line\n"

    @Test fun reads_metadata_and_events() {
        val s = LrcFormat.read(sample)
        assertEquals("Title", s.getScriptInfo("ti"))
        assertEquals("Artist", s.getScriptInfo("ar"))
        assertEquals(2, s.events.size)
        assertEquals(1_000_000L, s.events[0].start.micros)
        assertEquals("first line", s.events[0].text)
        assertEquals(3_500_000L, s.events[1].start.micros)
    }
    @Test fun writes_with_default_format() {
        val out = LrcFormat.write(LrcFormat.read(sample))
        assertTrue(out.contains("[ti:Title]"))
        assertTrue(out.contains("[00:01.00]first line"))
    }
    @Test fun reads_colon_variant() {
        val s = LrcFormat.read("[00:01:00]x\n")
        assertEquals(1_000_000L, s.events[0].start.micros)
    }
    @Test fun writes_chosen_format() {
        val s = LrcFormat.read("[00:01.00]x\n")
        val out = LrcFormat.write(s, WriteOptions(timePrecision = TimePrecision.THREE_MS))
        assertTrue(out.contains("[00:01.000]"))
    }
    @Test fun can_read_detects_lrc() {
        assertTrue(LrcFormat.canRead("[00:01.00]hi\n"))
    }
}
