package io.github.samgum.aegisub.domain.model

import io.github.samgum.aegisub.domain.time.SubTime
import kotlin.test.Test
import kotlin.test.assertEquals

class AssEventTest {
    private val line =
        "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello {\\i1}world{\\i0}"

    @Test fun parses_dialogue_line() {
        val e = AssEvent.parse(line)
        assertEquals(0, e.layer)
        assertEquals(1_000_000L, e.start.micros)
        assertEquals(3_000_000L, e.end.micros)
        assertEquals("Default", e.style)
        assertEquals("Hello {\\i1}world{\\i0}", e.text)
        assertEquals(false, e.comment)
    }
    @Test fun parses_comment_line() {
        val e = AssEvent.parse("Comment: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,note")
        assertEquals(true, e.comment)
    }
    @Test fun round_trips_dialogue_line() {
        assertEquals(line, AssEvent.parse(line).toLine())
    }
    @Test fun stripped_text_removes_tags() {
        val e = AssEvent.parse(line)
        assertEquals("Hello world", e.strippedText)
    }
}
