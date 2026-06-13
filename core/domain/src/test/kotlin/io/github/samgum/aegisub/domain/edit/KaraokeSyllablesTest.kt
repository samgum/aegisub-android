package io.github.samgum.aegisub.domain.edit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * KaraokeSyllables 测试：解析 {\k} 音节、重建文本、边界拖拽调时。
 *
 * @author 伤感咩吖
 */
class KaraokeSyllablesTest {

    @Test fun parse_extracts_tag_cs_and_text() {
        val s = KaraokeSyllables.parse("{\\k20}Hel{\\k30}lo")
        assertEquals(2, s.size)
        assertEquals("k", s[0].tag)
        assertEquals(20L, s[0].centiseconds)
        assertEquals("Hel", s[0].text)
        assertEquals(30L, s[1].centiseconds)
        assertEquals("lo", s[1].text)
    }

    @Test fun parse_handles_kf_tag() {
        val s = KaraokeSyllables.parse("{\\kf10}A{\\kf20}B")
        assertEquals("kf", s[0].tag)
        assertEquals(10L, s[0].centiseconds)
    }

    @Test fun parse_empty_when_no_karaoke_tags() {
        assertTrue(KaraokeSyllables.parse("Hello").isEmpty())
        assertTrue(KaraokeSyllables.parse("{\\b1}Hi{\\b0}").isEmpty())
    }

    @Test fun build_round_trips() {
        val original = "{\\k20}Hel{\\k30}lo"
        val rebuilt = KaraokeSyllables.build(KaraokeSyllables.parse(original))
        assertEquals(original, rebuilt)
    }

    @Test fun build_empty_is_blank() {
        assertEquals("", KaraokeSyllables.build(emptyList()))
    }

    @Test fun total_returns_sum_of_cs() {
        assertEquals(50L, KaraokeSyllables.total(KaraokeSyllables.parse("{\\k20}A{\\k30}B")))
        assertEquals(0L, KaraokeSyllables.total(emptyList()))
    }

    @Test fun adjust_moves_cs_between_adjacent_syllables() {
        val s = KaraokeSyllables.parse("{\\k20}A{\\k30}B")
        val r = KaraokeSyllables.adjust(s, boundary = 0, deltaCs = 10)
        // A: 20+10=30, B: 30-10=20，总和不变
        assertEquals(30L, r[0].centiseconds)
        assertEquals(20L, r[1].centiseconds)
        assertEquals(50L, KaraokeSyllables.total(r))
    }

    @Test fun adjust_clamps_so_each_stays_at_least_one() {
        val s = KaraokeSyllables.parse("{\\k5}A{\\k5}B")
        // 试图从 B 取 100 给 A：A 最多取 B-1=4
        val r = KaraokeSyllables.adjust(s, boundary = 0, deltaCs = 100)
        assertEquals(9L, r[0].centiseconds)
        assertEquals(1L, r[1].centiseconds)
    }

    @Test fun adjust_negative_delta_moves_other_way() {
        val s = KaraokeSyllables.parse("{\\k20}A{\\k30}B")
        val r = KaraokeSyllables.adjust(s, boundary = 0, deltaCs = -10)
        assertEquals(10L, r[0].centiseconds)
        assertEquals(40L, r[1].centiseconds)
    }

    @Test fun adjust_invalid_boundary_is_noop() {
        val s = KaraokeSyllables.parse("{\\k20}A{\\k30}B")
        assertEquals(s, KaraokeSyllables.adjust(s, boundary = 5, deltaCs = 10))
        assertEquals(s, KaraokeSyllables.adjust(s, boundary = -1, deltaCs = 10))
    }

    @Test fun adjust_preserves_text_and_tags() {
        val s = KaraokeSyllables.parse("{\\kf20}Hel{\\kf30}lo")
        val r = KaraokeSyllables.adjust(s, boundary = 0, deltaCs = 5)
        assertEquals("kf", r[0].tag)
        assertEquals("Hel", r[0].text)
        assertEquals("lo", r[1].text)
    }

    @Test fun round_trip_after_adjust() {
        val s = KaraokeSyllables.parse("{\\k20}A{\\k30}B")
        val adjusted = KaraokeSyllables.adjust(s, 0, 10)
        val text = KaraokeSyllables.build(adjusted)
        assertEquals("{\\k30}A{\\k20}B", text)
    }
}
