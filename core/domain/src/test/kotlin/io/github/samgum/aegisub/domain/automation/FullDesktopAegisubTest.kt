package io.github.samgum.aegisub.domain.automation

import io.github.samgum.aegisub.domain.edit.ExportPipeline
import io.github.samgum.aegisub.domain.edit.FullTimingProcessor
import io.github.samgum.aegisub.domain.edit.PasteOverFields
import io.github.samgum.aegisub.domain.edit.SortKey
import io.github.samgum.aegisub.domain.edit.SortOrder
import io.github.samgum.aegisub.domain.edit.SortSelectedLines
import io.github.samgum.aegisub.domain.edit.TextImportProcessor
import io.github.samgum.aegisub.domain.edit.TimecodesIo
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.EncoreFormat
import io.github.samgum.aegisub.domain.format.SrtFormat
import io.github.samgum.aegisub.domain.format.TranStationFormat
import io.github.samgum.aegisub.domain.format.TtxtFormat
import io.github.samgum.aegisub.domain.format.WriteOptions
import io.github.samgum.aegisub.domain.model.AssAttachment
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AttachmentGroup
import io.github.samgum.aegisub.domain.time.SubTime
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 桌面 Aegisub 完整功能补完单测套件：
 * 覆盖 ASS UUEncode 字体/图片内嵌附件、Karaoke Templater、Clean Tags、半角转全角、卡拉OK自动前导、
 * 选择重叠行、11 字段粘贴覆盖、四合一时间后处理器、选中行原位排序、VFR 时间码与 TTXT/TranStation/Encore 格式。
 *
 * @author 伤感咩吖
 */
class FullDesktopAegisubTest {

    @Test
    fun assAttachmentUuEncodeAndAssSectionRoundTrip() {
        val rawFontBytes = ByteArray(257) { idx -> ((idx * 37 + 13) and 0xFF).toByte() }
        val fontAtt = AssAttachment.fromBytes("SourceHanSans.ttf", AttachmentGroup.FONT, rawFontBytes)
        assertEquals("SourceHanSans_0.ttf", fontAtt.fileName)
        assertEquals("SourceHanSans.ttf", fontAtt.displayFileName)
        assertArrayEquals(rawFontBytes, fontAtt.decodeBytes())

        val script = AssScript.default()
            .withEvents(listOf(AssEvent(id = 1L, start = SubTime.ofMillis(1000), end = SubTime.ofMillis(2000), text = "测试")))
            .withAttachments(listOf(fontAtt))

        val written = AssFormat.write(script)
        assertTrue(written.contains("[Fonts]"))
        assertTrue(written.contains("fontname: SourceHanSans_0.ttf"))

        val parsed = AssFormat.read(written)
        assertEquals(1, parsed.attachments.size)
        assertArrayEquals(rawFontBytes, parsed.attachments.first().decodeBytes())
    }

    @Test
    fun karaokeTemplaterExpandsSylVariablesAndMathExpressions() {
        val tpl = AssEvent(
            id = 1L,
            comment = true,
            effect = "template syl noblank",
            layer = 5,
            text = "{\\pos(\$x,\$y)\\t(\$start,\$end,\\fscx130)\\fad(!100+50!,100)}",
        )
        val lyric = AssEvent(
            id = 2L,
            start = SubTime.ofMillis(1_000L),
            end = SubTime.ofMillis(3_000L),
            text = "{\\k100}星{\\k100}空",
        )
        val script = AssScript.default().withEvents(listOf(tpl, lyric))
        val res = AutomationEngine.applyKaraokeTemplater(script)

        assertEquals(1, res.templateCount)
        assertEquals(2, res.generatedLines)
        val fxLines = res.script.events.filter { !it.comment && it.effect == "fx" }
        assertEquals(2, fxLines.size)
        assertTrue(fxLines[0].text.contains("\\t(0,1000,\\fscx130)"))
        assertTrue(fxLines[0].text.contains("\\fad(150,100)"))
        assertTrue(fxLines[0].text.endsWith("星"))
        assertTrue(fxLines[1].text.contains("\\t(1000,2000,\\fscx130)"))
        assertTrue(fxLines[1].text.endsWith("空"))
    }

    @Test
    fun cleanTagsAndBuiltinAutomationMacrosBehaveLikeUpstreamLua() {
        val messy = "{\\b1}{\\i1}Hello {\\pos(960,540)}{\\an8}World{}"
        assertEquals("{\\an8\\pos(960,540)\\b1\\i1}Hello World", AutomationEngine.cleanTagsLine(messy))

        val events = listOf(
            AssEvent(id = 1L, start = SubTime.ofMillis(1000), end = SubTime.ofMillis(2000), text = "{\\b1}ABC 123\\Nok"),
            AssEvent(id = 2L, start = SubTime.ofMillis(1500), end = SubTime.ofMillis(3000), text = "Overlap line"),
            AssEvent(id = 3L, start = SubTime.ofMillis(3500), end = SubTime.ofMillis(5000), text = "{\\k50}ka"),
        )
        val (fw, _) = AutomationEngine.makeFullwidth(events, setOf(1L))
        assertEquals("{\\b1}ＡＢＣ\u3000１２３\\Nｏｋ", fw[0].text)

        val overlaps = AutomationEngine.selectOverlaps(events)
        assertEquals(setOf(2L), overlaps)

        val (leadJoined, modCount) = AutomationEngine.autoKaraokeLeadIn(events, setOf(2L, 3L))
        assertEquals(1, modCount)
        assertEquals(3000L, leadJoined[2].start.millis)
        assertEquals("{\\k50}{\\k50}ka", leadJoined[2].text)
    }

    @Test
    fun pasteOverFieldsSelectivelyUpdatesOnlyCheckedColumns() {
        val originals = listOf(
            AssEvent(id = 10L, start = SubTime.ofMillis(1000), end = SubTime.ofMillis(2000), style = "CN", text = "中文字幕"),
        )
        val donorLine = "Dialogue: 2,0:00:05.00,0:00:08.50,JP,Alice,10,20,30,fx,日本語テキスト"
        val updatedTimesOnly = PasteOverFields.apply(
            originals,
            listOf(10L),
            listOf(donorLine),
            PasteOverFields.PRESET_TIMES_ONLY,
        )
        assertEquals(5000L, updatedTimesOnly[0].start.millis)
        assertEquals(8500L, updatedTimesOnly[0].end.millis)
        assertEquals("CN", updatedTimesOnly[0].style)
        assertEquals("中文字幕", updatedTimesOnly[0].text)
    }

    @Test
    fun fullTimingProcessorCollisionSafeLeadAndKeyframeSnap() {
        val events = listOf(
            AssEvent(id = 1L, start = SubTime.ofMillis(1_000L), end = SubTime.ofMillis(1_950L), text = "A"),
            AssEvent(id = 2L, start = SubTime.ofMillis(2_050L), end = SubTime.ofMillis(3_900L), text = "B"),
        )
        val processed = FullTimingProcessor.process(
            events,
            FullTimingProcessor.Config(
                enableLeadIn = true,
                leadInMs = 100L,
                enableLeadOut = true,
                leadOutMs = 100L,
                enableMakeAdjacent = true,
                maxGapMs = 200L,
                adjacentBias = 1.0,
                enableKeyframeSnap = true,
                keyframesMs = listOf(920L, 4_020L),
                startsAfterKfMs = 200L,
                endsBeforeKfMs = 200L,
            ),
        )
        assertEquals(920L, processed[0].start.millis)
        assertEquals(processed[0].end.millis, processed[1].start.millis)
        assertEquals(4_020L, processed[1].end.millis)
    }

    @Test
    fun sortSelectedOnlyAndExtraFormatsRoundTrip() {
        val events = listOf(
            AssEvent(id = 1L, start = SubTime.ofMillis(5000), end = SubTime.ofMillis(6000), text = "Unselected Top"),
            AssEvent(id = 2L, start = SubTime.ofMillis(4000), end = SubTime.ofMillis(4500), text = "Sel B"),
            AssEvent(id = 3L, start = SubTime.ofMillis(1000), end = SubTime.ofMillis(2000), text = "Sel A"),
        )
        val sortedSel = SortSelectedLines.apply(events, setOf(2L, 3L), SortKey.START, SortOrder.ASCENDING)
        assertEquals(1L, sortedSel[0].id)
        assertEquals(3L, sortedSel[1].id)
        assertEquals(2L, sortedSel[2].id)

        val script = AssScript.default().withEvents(listOf(events[2], events[1]))
        val ttxtXml = TtxtFormat.write(script)
        assertEquals(2, TtxtFormat.read(ttxtXml).events.size)

        val tranTxt = TranStationFormat.write(script)
        assertEquals(2, TranStationFormat.read(tranTxt).events.size)

        val encoreTxt = EncoreFormat.write(script)
        assertEquals(2, EncoreFormat.read(encoreTxt).events.size)

        val importedActors = TextImportProcessor.parsePlainScript("# scene 1\nAlice: 你好\nBob: 早上好")
        assertEquals(3, importedActors.size)
        assertTrue(importedActors[0].comment)
        assertEquals("Alice", importedActors[1].actor)
        assertEquals("你好", importedActors[1].text)

        val vfr = TimecodesIo.parseTimecodesMs("# timecode format v2\n0\n41.708\n83.417\n")
        assertEquals(listOf(0L, 42L, 83L), vfr)

        val exportedUtf8Bom = ExportPipeline.runExport(
            script,
            SrtFormat,
            WriteOptions(),
            ExportPipeline.FilterOptions(cleanTags = true),
            ExportPipeline.ExportCharset.UTF8_BOM,
        )
        assertEquals(0xEF.toByte(), exportedUtf8Bom[0])
    }
}
