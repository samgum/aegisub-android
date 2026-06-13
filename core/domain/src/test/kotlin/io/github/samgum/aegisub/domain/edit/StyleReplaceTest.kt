package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * StyleReplace 样式批量替换测试：改名/保留/空源 no-op/大小写敏感。
 *
 * @author 伤感咩吖
 */
class StyleReplaceTest {

    private fun ev(style: String, id: Long = 0) = AssEvent(id = id, style = style, text = "x")

    @Test fun renames_matching_style() {
        val r = StyleReplace.apply(listOf(ev("Default"), ev("Default", 1)), "Default", "Title")
        assertEquals("Title", r[0].style)
        assertEquals("Title", r[1].style)
    }

    @Test fun leaves_non_matching() {
        val r = StyleReplace.apply(listOf(ev("Default"), ev("Other", 1)), "Default", "Title")
        assertEquals("Title", r[0].style)
        assertEquals("Other", r[1].style)
    }

    @Test fun empty_from_is_noop() {
        val src = listOf(ev("Default"))
        val r = StyleReplace.apply(src, "", "Title")
        assertEquals("Default", r[0].style)
    }

    @Test fun case_sensitive() {
        // ASS 样式名大小写敏感：default ≠ Default
        val r = StyleReplace.apply(listOf(ev("Default"), ev("default", 1)), "Default", "Title")
        assertEquals("Title", r[0].style)
        assertEquals("default", r[1].style)
    }

    @Test fun from_equals_to_is_noop_change() {
        // 同名替换：不改实质，但应原样返回（不报错）
        val r = StyleReplace.apply(listOf(ev("Default")), "Default", "Default")
        assertEquals("Default", r[0].style)
    }
}
