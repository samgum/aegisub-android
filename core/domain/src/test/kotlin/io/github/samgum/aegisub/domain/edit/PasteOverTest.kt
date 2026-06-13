package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * PasteOver 粘贴覆盖测试：按选中行顺序用粘贴文本逐行覆盖。
 *
 * @author 伤感咩吖
 */
class PasteOverTest {

    private fun ev(id: Long, text: String) = AssEvent(id = id, text = text)

    @Test fun overwrites_selected_lines_in_order() {
        val events = listOf(ev(0, "a"), ev(1, "b"), ev(2, "c"))
        val r = PasteOver.apply(events, orderedIds = listOf(0L, 2L), texts = listOf("X", "Y"))
        assertEquals("X", r[0].text)
        assertEquals("b", r[1].text) // 未选中不变
        assertEquals("Y", r[2].text)
    }

    @Test fun fewer_texts_than_ids_leaves_tail_unchanged() {
        val events = listOf(ev(0, "a"), ev(1, "b"), ev(2, "c"))
        val r = PasteOver.apply(events, orderedIds = listOf(0L, 1L, 2L), texts = listOf("X"))
        assertEquals("X", r[0].text)
        assertEquals("b", r[1].text) // 无对应文本，不变
        assertEquals("c", r[2].text)
    }

    @Test fun more_texts_than_ids_ignores_extra() {
        val events = listOf(ev(0, "a"))
        val r = PasteOver.apply(events, orderedIds = listOf(0L), texts = listOf("X", "Y", "Z"))
        assertEquals("X", r[0].text)
        assertEquals(1, r.size)
    }

    @Test fun preserves_timing_style_and_other_fields() {
        val e = AssEvent(id = 0, text = "old", style = "Title", layer = 3)
        val r = PasteOver.apply(listOf(e), listOf(0L), listOf("new"))
        assertEquals("new", r[0].text)
        assertEquals("Title", r[0].style)
        assertEquals(3, r[0].layer)
    }

    @Test fun empty_ids_is_noop() {
        val events = listOf(ev(0, "a"))
        assertEquals(events, PasteOver.apply(events, emptyList(), listOf("X")))
    }

    @Test fun empty_texts_is_noop() {
        val events = listOf(ev(0, "a"))
        assertEquals(events, PasteOver.apply(events, listOf(0L), emptyList()))
    }

    @Test fun non_existent_ids_are_skipped() {
        val events = listOf(ev(0, "a"), ev(1, "b"))
        // id 9 不存在；按顺序，0 拿 X，9 跳过（消耗一个 text 但无匹配），1 拿 Y
        val r = PasteOver.apply(events, orderedIds = listOf(0L, 9L, 1L), texts = listOf("X", "Y", "Z"))
        assertEquals("X", r[0].text)
        assertEquals("Z", r[1].text)
    }
}
