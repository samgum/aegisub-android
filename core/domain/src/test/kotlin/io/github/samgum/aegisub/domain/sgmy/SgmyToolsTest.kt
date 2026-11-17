package io.github.samgum.aegisub.domain.sgmy

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SgmyToolsTest {

    @Test
    fun fixCommonErrors_fixes_overlaps_gaps_durations_whitespace_and_empty_rows() {
        val events = listOf(
            AssEvent(id = 1, start = SubTime.ofMillis(0), end = SubTime.ofMillis(400), text = "短句　 "),
            AssEvent(id = 2, start = SubTime.ofMillis(950), end = SubTime.ofMillis(2_500), text = "重叠测试"),
            AssEvent(id = 3, start = SubTime.ofMillis(2_530), end = SubTime.ofMillis(12_000), text = "过长测试"),
            AssEvent(id = 4, start = SubTime.ofMillis(13_000), end = SubTime.ofMillis(14_000), text = "{\\pos(100,100)}\\h \\N　"),
        )
        val res = FixCommonErrors.apply(
            events,
            FixCommonErrors.Config(
                fixOverlaps = true,
                fixShortGaps = true,
                fixShortDurations = true,
                fixLongDurations = true,
                removeEmptyLines = true,
                stripTrailingWhitespace = true,
                minGapMs = 100L,
                minDurationMs = 1_000L,
                maxDurationMs = 7_000L,
            ),
        )
        assertEquals(1, res.shortDurationsExtended)
        assertEquals(1, res.longDurationsTrimmed)
        assertTrue(res.overlapsTrimmed >= 1)
        assertTrue(res.shortGapsFixed >= 1)
        assertEquals(2, res.trailingSpacesStripped)
        assertEquals(1, res.emptyLinesRemoved)
        assertEquals(3, res.finalEvents.size)
        assertEquals("短句", res.finalEvents[0].text)
        // 第1句结束应被截断并留出 100ms 最小间隙：950 - 100 = 850ms
        assertEquals(850L, res.finalEvents[0].end.millis)
        // 第3句时长截断为 7000ms -> 2530 + 7000 = 9530ms
        assertEquals(9_530L, res.finalEvents[2].end.millis)
    }

    @Test
    fun stitchTimings_stitches_small_gaps_by_previous_or_next() {
        val events = listOf(
            AssEvent(id = 1, start = SubTime.ofMillis(1_000), end = SubTime.ofMillis(2_000), text = "A"),
            AssEvent(id = 2, start = SubTime.ofMillis(2_180), end = SubTime.ofMillis(3_500), text = "B"),
        )
        val rPrev = StitchTimings.apply(events, StitchTimings.Config(anchor = StitchTimings.Anchor.PREVIOUS_LINE_END, maxGapMs = 250))
        assertEquals(1, rPrev.changedCount)
        assertEquals(2_000L, rPrev.events[1].start.millis)

        val rNext = StitchTimings.apply(events, StitchTimings.Config(anchor = StitchTimings.Anchor.NEXT_LINE_START, maxGapMs = 250))
        assertEquals(1, rNext.changedCount)
        assertEquals(2_180L, rNext.events[0].end.millis)
    }

    @Test
    fun styleOverlapChecker_detects_same_style_crossing_overlaps() {
        val events = listOf(
            AssEvent(id = 1, start = SubTime.ofMillis(1_000), end = SubTime.ofMillis(3_000), style = "Main", text = "1"),
            AssEvent(id = 2, start = SubTime.ofMillis(2_500), end = SubTime.ofMillis(4_000), style = "Main", text = "2"),
            AssEvent(id = 3, start = SubTime.ofMillis(2_500), end = SubTime.ofMillis(4_000), style = "Top", text = "3"),
        )
        val report = StyleOverlapChecker.analyze(events)
        assertTrue(report.hasAnyIssue)
        val mainStats = report.styles.first { it.styleName == "Main" }
        assertEquals(1, mainStats.crossingOverlapPairs)
        val topStats = report.styles.first { it.styleName == "Top" }
        assertFalse(topStats.hasIssues)
    }

    @Test
    fun cjkTools_clean_convert_pairCheck_and_furigana_preserve_ass_tags() {
        val events = listOf(
            AssEvent(id = 1, text = "{\\pos(960,1000)}你好，世界。下一句“测试”   完毕。"),
        )
        val cleaned = SubtitleTextCleaner.apply(
            events,
            SubtitleTextCleaner.Config(
                replaceChineseCommas = true,
                cleanChinesePeriods = true,
                replaceSmartQuotes = true,
                checkDoubleSpaces = true,
                fixDoubleSpaces = true,
            ),
        )
        assertEquals("{\\pos(960,1000)}你好 世界 下一句\"测试\" 完毕", cleaned.events[0].text)

        val trad = ChineseConverter.apply(
            listOf(AssEvent(id = 1, text = "{\\b1}时间轴编辑与视频预览{\\b0}")),
            ChineseConverter.Config(direction = ChineseConverter.Direction.TO_TRADITIONAL),
        )
        assertEquals("{\\b1}時間軸編輯與視頻預覽{\\b0}", trad.events[0].text)

        val pairIssues = PairedPunctuationChecker.check(
            listOf(
                AssEvent(id = 1, text = "正常《书名》「对话」"),
                AssEvent(id = 2, text = "未闭合《书名」错误"),
            ),
        )
        assertEquals(1, pairIssues.issues.size)
        assertEquals(2, pairIssues.issues[0].rowNumber)

        val jpScript = AssScript.default().withEvents(
            persistentListOf(AssEvent(id = 1, text = "東京の空")),
        )
        val furi = FuriganaAnnotator.apply(jpScript, "東京=とうきょう\n空=そら", FuriganaAnnotator.Config())
        assertEquals(1, furi.annotatedLines)
        assertTrue(furi.events[0].text.contains("とうきょう"))
        assertTrue(furi.events[0].text.contains("東京の空"))
        val strippedBack = FuriganaAnnotator.stripExistingFurigana(furi.events[0].text)
        assertEquals("東京の空", strippedBack.first)
    }

    @Test
    fun lyricScrollGenerator_generates_animated_scroll_events_and_styles() {
        val script = AssScript.default().withEvents(
            persistentListOf(
                AssEvent(id = 1, start = SubTime.ofMillis(1_000), end = SubTime.ofMillis(3_000), text = "第一句歌词"),
                AssEvent(id = 2, start = SubTime.ofMillis(3_000), end = SubTime.ofMillis(5_500), text = "第二句歌词"),
                AssEvent(id = 3, start = SubTime.ofMillis(5_500), end = SubTime.ofMillis(8_000), text = "第三句歌词"),
            ),
        )
        val res = LyricScrollGenerator.generate(script, LyricScrollGenerator.Settings())
        assertTrue(res.generatedCount > 3)
        assertEquals(3, res.sourceRowCount)
        assertTrue(res.script.styles.any { it.name == "ScrollCurrent" })
        assertTrue(res.script.styles.any { it.name == "ScrollDim" })
        // 原字幕默认被注释隐藏并打上 MusicScroll Source
        assertEquals(3, res.script.events.count { it.comment && it.effect == LyricScrollGenerator.EFFECT_SOURCE })
    }

    @Test
    fun overflowDetector_flags_positioned_and_unwrapped_overflow() {
        val script = AssScript(
            info = persistentListOf(
                io.github.samgum.aegisub.domain.model.AssInfo("PlayResX", "1920"),
                io.github.samgum.aegisub.domain.model.AssInfo("PlayResY", "1080"),
                io.github.samgum.aegisub.domain.model.AssInfo("WrapStyle", "2"),
            ),
            styles = persistentListOf(AssStyle(name = "Default", fontSize = 80.0)),
            events = persistentListOf(),
        )
        val normal = AssEvent(id = 1, text = "短字幕")
        val tooLong = AssEvent(id = 2, text = "这是一条非常非常长且禁用了自动换行一定会超出1920画幅左右边界的超长测试字幕文本内容啊")
        assertFalse(SubtitleOverflowDetector.evaluate(normal, script).isOverflowing)
        assertTrue(SubtitleOverflowDetector.evaluate(tooLong, script).isOverflowing)
    }
}
