package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.edit.KanjiTimer
import io.github.samgum.aegisub.domain.edit.KeyframesIo
import io.github.samgum.aegisub.domain.edit.LineOps
import io.github.samgum.aegisub.domain.edit.SelectLines
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.preview.ActiveSubtitleResolver
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnhancedFormatsAndDesktopToolsTest {

    @Test
    fun enhancedLrc_parses_word_karaoke_offset_and_apple_music_end_marker() {
        val lrc = """
            [ti:Song]
            [offset:100]
            [00:01.10]<00:01.10>Hello <00:01.60>world<00:02.10>
            [00:05.10]Next line
        """.trimIndent()
        val script = LrcFormat.read(lrc)
        assertEquals(2, script.events.size)
        // offset:100 -> 1100 - 100 = 1000ms, end marker 2100 - 100 = 2000ms
        assertEquals(1_000L, script.events[0].start.millis)
        assertEquals(2_000L, script.events[0].end.millis)
        assertEquals("{\\kf50}Hello {\\kf50}world", script.events[0].text)
    }

    @Test
    fun ttmlFormat_reads_word_spans_bg_harmony_and_roundtrips() {
        val ttml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:00:01.000" end="00:00:03.000">
                    <span begin="00:00:01.000" end="00:00:01.800">Lead </span>
                    <span begin="00:00:01.800" end="00:00:03.000">vocal</span>
                    <span ttm:role="x-bg">
                      <span begin="00:00:02.000" end="00:00:03.000">(Harmony)</span>
                    </span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        assertTrue(TtmlFormat.canRead(ttml))
        val script = TtmlFormat.read(ttml)
        // 主唱 + 背景和声拆成两行同时叠加
        assertEquals(2, script.events.size)
        assertTrue(script.events[0].text.contains("\\kf80"))
        assertTrue(script.events[1].text.contains("(Harmony)"))

        val exported = TtmlFormat.write(script)
        assertTrue(TtmlFormat.canRead(exported))
        val reRead = TtmlFormat.read(exported)
        assertEquals(2, reRead.events.size)
    }

    @Test
    fun activeSubtitleResolver_interpolates_move_fade_t_and_karaoke() {
        val script = AssScript.default().withEvents(
            persistentListOf(
                AssEvent(
                    id = 1,
                    start = SubTime.ofMillis(1_000),
                    end = SubTime.ofMillis(3_000),
                    text = "{\\move(100,200,300,400)\\fad(500,500)\\t(0,2000,\\fscx200)}{\\kf100}Ka{\\kf100}ra",
                ),
            ),
        )
        val mid = ActiveSubtitleResolver.renderInfosInDrawOrder(script, 2_000).single()
        assertEquals(200f, mid.effectivePos?.first ?: 0f, 0.5f)
        assertEquals(300f, mid.effectivePos?.second ?: 0f, 0.5f)
        assertEquals(1f, mid.opacityMultiplier, 0.01f)
        assertEquals(150.0, mid.spans.first().scaleX, 1.0)
        assertEquals(1f, mid.spans[0].karaokeProgress ?: 0f, 0.01f)
        assertEquals(0f, mid.spans[1].karaokeProgress ?: 1f, 0.01f)
    }

    @Test
    fun desktopTools_joinKaraoke_splitKaraoke_selectLines_kanjiTimer_and_keyframes() {
        val rows = listOf(
            AssEvent(id = 1, start = SubTime.ofMillis(1_000), end = SubTime.ofMillis(1_500), style = "JP", text = "あ"),
            AssEvent(id = 2, start = SubTime.ofMillis(1_500), end = SubTime.ofMillis(2_200), style = "JP", text = "い"),
        )
        val joined = LineOps.joinAsKaraoke(rows, listOf(0, 1))
        assertEquals(1, joined.size)
        assertEquals("{\\k50}あ{\\k70}い", joined[0].text)

        val splitBack = LineOps.splitByKaraoke(joined, 0)
        assertEquals(2, splitBack.size)
        assertEquals(1_500L, splitBack[0].end.millis)

        val projected = KanjiTimer.projectKaraokeToTarget("{\\k40}tou{\\k60}kyou", "東京")
        assertEquals("{\\k40}東{\\k60}京", projected)

        val sel = SelectLines.evaluate(
            rows,
            emptySet(),
            SelectLines.Criteria(pattern = "い", field = SelectLines.Field.TEXT),
        )
        assertEquals(setOf(2L), sel)

        val kfTxt = KeyframesIo.writeKeyframesV1(listOf(0L, 1_000L, 2_500L), 25.0)
        val parsed = KeyframesIo.parseKeyframesMs(kfTxt, 25.0)
        assertEquals(1_000L, KeyframesIo.snapToNearest(1_090L, parsed, thresholdMs = 150L))
    }
}
