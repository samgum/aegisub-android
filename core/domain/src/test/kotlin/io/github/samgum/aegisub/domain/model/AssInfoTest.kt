package io.github.samgum.aegisub.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssInfoTest {
    @Test fun serializes_as_key_colon_value() {
        assertEquals("ScriptType: v4.00+", AssInfo("ScriptType", "v4.00+").toLine())
    }
    @Test fun parses_line() {
        val info = AssInfo.parse("PlayResX: 1920")!!
        assertEquals("PlayResX", info.key)
        assertEquals("1920", info.value)
    }
    @Test fun parse_returns_null_when_not_a_kv_line() {
        assertNull(AssInfo.parse("[Script Info]"))
        assertNull(AssInfo.parse("no colon here"))
    }
    @Test fun parse_trims_value() {
        assertEquals("Arial", AssInfo.parse("Font:   Arial   ")!!.value)
    }
}
