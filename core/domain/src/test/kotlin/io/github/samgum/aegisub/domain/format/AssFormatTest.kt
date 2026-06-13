package io.github.samgum.aegisub.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssFormatTest {
    private val sample = """
        [Script Info]
        ScriptType: v4.00+
        PlayResX: 1920
        PlayResY: 1080

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello
    """.trimIndent()

    @Test fun reads_ass_sections() {
        val s = AssFormat.read(sample)
        assertEquals("v4.00+", s.getScriptInfo("ScriptType"))
        assertEquals("1920", s.getScriptInfo("PlayResX"))
        assertEquals(1, s.styles.size)
        assertEquals("Default", s.styles[0].name)
        assertEquals(1, s.events.size)
        assertEquals(1_000_000L, s.events[0].start.micros)
        assertEquals("Hello", s.events[0].text)
    }
    @Test fun writes_ass_round_trip() {
        val original = AssFormat.read(sample)
        val rewritten = AssFormat.write(original)
        val reparsed = AssFormat.read(rewritten)
        assertEquals(original.events.size, reparsed.events.size)
        assertEquals(original.events[0].text, reparsed.events[0].text)
        assertEquals(original.events[0].start, reparsed.events[0].start)
    }
    @Test fun can_read_detects_ass() {
        assertTrue(AssFormat.canRead("[Script Info]"))
    }
    @Test fun does_not_misparse_format_line_as_info() {
        // 回归：[V4+ Styles] 段的 Format: 描述行不应被当成 AssInfo
        val s = AssFormat.read(sample)
        assertTrue(s.info.none { it.key.equals("Format", true) })
    }
}
