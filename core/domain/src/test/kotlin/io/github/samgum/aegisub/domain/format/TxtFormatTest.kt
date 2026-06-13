package io.github.samgum.aegisub.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals

class TxtFormatTest {
    @Test fun reads_lines_as_sequential_events() {
        val s = TxtFormat.read("line one\nline two\n")
        assertEquals(2, s.events.size)
        assertEquals("line one", s.events[0].text)
        // 每行默认 2s，第二行 start 应为 2s（2000ms）
        assertEquals(2_000_000L, s.events[1].start.micros)
        assertEquals(4_000_000L, s.events[1].end.micros)
    }
    @Test fun writes_text_only() {
        val s = TxtFormat.read("a\nb\n")
        assertEquals("a\nb", TxtFormat.write(s).trim())
    }
}
