package io.github.samgum.aegisub.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AssColorTest {
    @Test fun packs_argb_components() {
        val c = AssColor(0x12, 0x34, 0x56, 0xFF)
        assertEquals(0x12, c.r)
        assertEquals(0x34, c.g)
        assertEquals(0x56, c.b)
        assertEquals(0xFF, c.a)
    }
    @Test fun parses_ass_full_alpha_inverted() {
        // &H00FFFFFF  -> AA=00(完全不透明) => a=255 ; BB=FF GG=FF RR=FF => 白
        val c = AssColor.parseAss("&H00FFFFFF")
        assertEquals(255, c.r); assertEquals(255, c.g); assertEquals(255, c.b)
        assertEquals(255, c.a)
    }
    @Test fun parses_ass_short_without_alpha() {
        // &H0000FF -> BB=00 GG=00 RR=FF => 红色，alpha 默认不透明
        val c = AssColor.parseAss("&H0000FF")
        assertEquals(255, c.r); assertEquals(0, c.g); assertEquals(0, c.b)
        assertEquals(255, c.a)
    }
    @Test fun round_trips_through_ass_string() {
        val original = AssColor(10, 20, 30, 200)
        val parsed = AssColor.parseAss(original.toAssString())
        assertEquals(original.r, parsed.r)
        assertEquals(original.g, parsed.g)
        assertEquals(original.b, parsed.b)
        assertEquals(original.a, parsed.a)
    }
}
