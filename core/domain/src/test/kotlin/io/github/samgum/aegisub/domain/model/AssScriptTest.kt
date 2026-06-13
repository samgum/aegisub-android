package io.github.samgum.aegisub.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AssScriptTest {
    @Test fun default_script_has_script_info_and_default_style() {
        val s = AssScript.default()
        assertEquals("v4.00+", s.info.first { it.key == "ScriptType" }.value)
        assertEquals("Default", s.styles.first().name)
    }
    @Test fun script_info_helpers() {
        val s = AssScript.default()
        assertEquals("v4.00+", s.getScriptInfo("ScriptType"))
        assertEquals(null, s.getScriptInfo("Missing"))
    }
    @Test fun with_events_returns_new_immutable_instance() {
        val s = AssScript.default()
        val e = AssEvent(text = "hi")
        val s2 = s.withEvent(e)
        assertEquals(1, s2.events.size)
        assertEquals(0, s.events.size) // 原实例不变（不可变）
    }
}
