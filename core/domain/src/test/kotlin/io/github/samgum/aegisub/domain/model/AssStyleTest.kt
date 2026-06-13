package io.github.samgum.aegisub.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AssStyleTest {
    private val sampleLine =
        "Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000," +
        "-1,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1"

    @Test fun parses_all_fields() {
        val s = AssStyle.parse(sampleLine)
        assertEquals("Default", s.name)
        assertEquals("Arial", s.font)
        assertEquals(48.0, s.fontSize)
        assertEquals(AssColor.WHITE, s.primary)
        assertEquals(AssColor.RED, s.secondary)
        assertEquals(AssColor.BLACK, s.outline)
        assertEquals(true, s.bold)
        assertEquals(false, s.italic)
        assertEquals(100.0, s.scaleX)
        assertEquals(1, s.borderStyle)
        assertEquals(2.0, s.outlineWidth)
        assertEquals(2, s.alignment)
        assertEquals(Margins(10, 10, 10), s.margins)
        assertEquals(1, s.encoding)
    }
    @Test fun round_trips_through_style_line() {
        val s = AssStyle.parse(sampleLine)
        assertEquals(sampleLine, s.toStyleLine())
    }
    @Test fun ass_to_ssa_matches_aegisub_table() {
        // 对齐 Aegisub 源码 AssStyle::AssToSsa
        assertEquals(1, AssStyle.assToSsa(1))
        assertEquals(2, AssStyle.assToSsa(2))
        assertEquals(3, AssStyle.assToSsa(3))
        assertEquals(9, AssStyle.assToSsa(4))
        assertEquals(10, AssStyle.assToSsa(5))
        assertEquals(11, AssStyle.assToSsa(6))
        assertEquals(5, AssStyle.assToSsa(7))
        assertEquals(6, AssStyle.assToSsa(8))
        assertEquals(7, AssStyle.assToSsa(9))
    }
    @Test fun ssa_ass_round_trip_is_identity_for_all_numpad() {
        // ssaToAss(assToSsa(x)) == x 对所有有效 ASS 对齐 1..9 成立
        for (x in 1..9) assertEquals(x, AssStyle.ssaToAss(AssStyle.assToSsa(x)))
    }
}
