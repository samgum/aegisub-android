package io.github.samgum.aegisub.domain.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * SubtitleImport 导入解析纯函数测试。
 *
 * @author 伤感咩吖
 */
class SubtitleImportTest {

    @Test fun resolves_ass_by_content() {
        val r = SubtitleImport.resolve("a.ass", "[Script Info]\nScriptType: v4.00+\n")
        assertEquals("ass", r.format)
        assertEquals("a", r.name)
    }

    @Test fun resolves_srt_by_content() {
        val r = SubtitleImport.resolve("sub.srt", "1\n00:00:01,000 --> 00:00:02,000\nHi\n")
        assertEquals("srt", r.format)
        assertEquals("sub", r.name)
    }

    @Test fun falls_back_to_extension_when_content_ambiguous() {
        // 纯文本无已知标记 → 内容嗅探为空/兜底，按扩展名 .txt → txt
        val r = SubtitleImport.resolve("notes.txt", "just some plain text without markers")
        assertEquals("txt", r.format)
        assertEquals("notes", r.name)
    }

    @Test fun falls_back_to_txt_when_extension_unknown() {
        val r = SubtitleImport.resolve("data.unknown", "???")
        assertEquals("txt", r.format)
    }

    @Test fun uses_fallback_name_when_filename_blank() {
        val r = SubtitleImport.resolve("", "???")
        assertEquals("导入的字幕", r.name)
    }

    @Test fun name_keeps_inner_dots_before_last_extension() {
        val r = SubtitleImport.resolve("my.show.ep01.srt", "1\n00:00:01,000 --> 00:00:02,000\nHi\n")
        assertEquals("my.show.ep01", r.name)
        assertEquals("srt", r.format)
    }
}
