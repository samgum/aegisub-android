package io.github.samgum.aegisub.domain.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 1:1 移植自桌面 `samgum/Aegisub/tests/tests/lyric_readers.cpp` 的全部 17 条回归测试，
 * 验证增强型 LRC 与 TTML 解析器的所有边界与和声拆分逻辑与桌面端逐字符一致。
 *
 * @author 伤感咩吖
 */
class SgmyLyricReadersParityTest {

    @Test
    fun lrcIntegerSecondsAndFractions() {
        val s = LrcFormat.read("[00:12]first\n[00:13.5]second\n[00:14.25]third\n")
        assertEquals(3, s.events.size)
        assertEquals(12_000L, s.events[0].start.millis)
        assertEquals("first", s.events[0].text)
    }

    @Test
    fun lrcRejectsPartialAndOverflowingTimes() {
        val s = LrcFormat.read("[00:12x]bad\n[00:12.2x]bad\n[9223372036854775807:00.00]bad\n[00:20.00]good\n")
        assertEquals(1, s.events.size)
        assertEquals("good", s.events[0].text)
    }

    @Test
    fun lrcShortRowsDoNotOverlapOrExtendExplicitEnd() {
        val s = LrcFormat.read("[00:01.00]<00:01.00>a<00:01.10>\n[00:01.20]b\n")
        assertEquals(1_100L, s.events[0].end.millis)
    }

    @Test
    fun lrcRepeatedWordTimingsAreShifted() {
        val s = LrcFormat.read("[00:01.00][00:11.00]<00:01.00>a<00:02.00>b<00:03.00>\n")
        assertEquals(2, s.events.size)
        assertEquals("{\\kf100}a{\\kf100}b", s.events[0].text)
        assertEquals(s.events[0].text, s.events[1].text)
        assertEquals(13_000L, s.events[1].end.millis)
    }

    @Test
    fun lrcPreservesDelayBeforeFirstWord() {
        val s = LrcFormat.read("[00:01.00]<00:02.00>a<00:03.00>\n")
        assertEquals("{\\k100}{\\kf100}a", s.events[0].text)
    }

    @Test
    fun lrcOffsetAfterLyricsAppliesToWholeFile() {
        val s = LrcFormat.read("[00:02.00]a\n[offset:+500]\n")
        assertEquals(1_500L, s.events[0].start.millis)
    }

    @Test
    fun lrcNearbyRowsKeepTheirShortDuration() {
        val s = LrcFormat.read("[00:01.00]a\n[00:01.20]b\n")
        assertEquals(1_200L, s.events[0].end.millis)
    }

    @Test
    fun untimedLrcPreservesRows() {
        val s = LrcFormat.read("first\nsecond\n")
        assertEquals(2, s.events.size)
        assertEquals("first", s.events[0].text)
        assertEquals("second", s.events[1].text)
    }

    @Test
    fun lrcSkipsWordMarkerLineWithoutVisibleText() {
        val s = LrcFormat.read("[00:01.00]<00:01.50>   <00:02.00>\n[00:03.00]real\n")
        assertEquals(1, s.events.size)
        assertEquals("real", s.events[0].text)
    }

    @Test
    fun ttmlNamespacePrefixesAndBreaks() {
        val s = TtmlFormat.read("""<t:tt xmlns:t="http://www.w3.org/ns/ttml"><t:body><t:div><t:p begin="1s" end="2s">a<t:br/>b</t:p></t:div></t:body></t:tt>""")
        assertEquals("a\\Nb", s.events[0].text)
    }

    @Test
    fun ttmlUntimedRowsKeepDocumentOrder() {
        val s = TtmlFormat.read("<tt><body><div><p>first</p><p>second</p><p>third</p></div></body></tt>")
        assertEquals(3, s.events.size)
        assertEquals("first", s.events[0].text)
        assertEquals("third", s.events[2].text)
    }

    @Test
    fun ttmlUntimedWrappersDoNotDuplicateOrReorderText() {
        val s = TtmlFormat.read("""<tt><body><p begin="1s" end="2s">a<span>b</span><span begin="1s" end="2s">c</span><span>d</span></p></body></tt>""")
        assertEquals("ab{\\kf100}cd", s.events[0].text)
    }

    @Test
    fun ttmlKaraokeKeepsInitialDelayAndWordGaps() {
        val s = TtmlFormat.read("""<tt><body><p begin="1s" end="5s"><span begin="2s" end="2.5s">a</span><span begin="4s" end="5s">b</span></p></body></tt>""")
        assertEquals("{\\k100}{\\kf50}a{\\k150}{\\kf100}b", s.events[0].text)
    }

    @Test
    fun ttmlRejectsNonfiniteAndOverflowingTimes() {
        val s = TtmlFormat.read("""<tt><body><p begin="1e300s" end="2s">bad</p><p begin="nan" end="inf">bad</p><p begin="1.5s" end="2.5s">good</p></body></tt>""")
        assertEquals(1, s.events.size)
        assertEquals(1_500L, s.events[0].start.millis)
    }

    @Test
    fun ttmlUnicodeTextIsUtf8() {
        val s = TtmlFormat.read("""<tt><body><p begin="1s" end="2s">中文歌词・日本語</p></body></tt>""")
        assertEquals("中文歌词・日本語", s.events[0].text)
    }

    @Test
    fun ttmlPreambleKeepsWordSeparatingSpace() {
        val s = TtmlFormat.read("""<tt><body><p begin="1s" end="2s">Hello <span begin="1s" end="2s">world</span></p></body></tt>""")
        assertEquals("Hello {\\kf100}world", s.events[0].text)
    }

    @Test
    fun ttmlBackgroundVoiceKeepsItsOwnTiming() {
        val s = TtmlFormat.read("""<tt><body><p begin="1s" end="5s"><span begin="1s" end="3s">a</span><span begin="4s" end="5s">b</span><span xmlns:ttm="http://www.w3.org/ns/ttml#metadata" ttm:role="x-bg"><span begin="2s" end="2.5s">x</span></span></p></body></tt>""")
        assertEquals(2, s.events.size)
        assertEquals("{\\kf200}a{\\k100}{\\kf100}b", s.events[0].text)
        assertEquals("{\\kf50}x", s.events[1].text)
        assertEquals(2_000L, s.events[1].start.millis)
    }

    @Test
    fun ttmlClockAndOffsetDurations() {
        val s = TtmlFormat.read("""<tt><body><p begin="00:00:01.500" dur="500ms">a</p><p begin="00:02,50" end="3s">b</p></body></tt>""")
        assertEquals(2, s.events.size)
        assertEquals(1_500L, s.events[0].start.millis)
        assertEquals(2_000L, s.events[0].end.millis)
        assertEquals(2_500L, s.events[1].start.millis)
    }
}
