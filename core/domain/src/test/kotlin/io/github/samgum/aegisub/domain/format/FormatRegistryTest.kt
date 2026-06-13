package io.github.samgum.aegisub.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatRegistryTest {
    @Test fun detects_ass_by_content() {
        assertEquals("ass", FormatRegistry.detect("[Script Info]\nScriptType: v4.00+\n[V4+ Styles]\nFormat: Name, Fontname")?.name)
    }
    @Test fun detects_srt_by_content() {
        assertEquals("srt", FormatRegistry.detect("1\n00:00:01,000 --> 00:00:02,000\nHi\n")?.name)
    }
    @Test fun detects_lrc_by_content() {
        assertEquals("lrc", FormatRegistry.detect("[ti:Song]\n[00:01.00]la la la\n")?.name)
    }
    @Test fun returns_null_for_unknown() {
        assertNull(FormatRegistry.detect(""))
    }
    @Test fun by_name_and_extension() {
        assertEquals("ass", FormatRegistry.byName("ass")?.name)
        assertEquals("lrc", FormatRegistry.detectByExtension("song.lrc")?.name)
    }
}
