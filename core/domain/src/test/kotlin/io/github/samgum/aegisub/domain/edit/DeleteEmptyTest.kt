package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * DeleteEmpty 删除空行测试：纯空/空白/仅覆盖标签删除；绘图与有字行保留。
 *
 * @author 伤感咩吖
 */
class DeleteEmptyTest {

    private fun ev(text: String, id: Long = 0) = AssEvent(id = id, text = text)

    @Test fun removes_truly_empty() {
        val r = DeleteEmpty.apply(listOf(ev(""), ev("有字", 1)))
        assertEquals(1, r.size)
        assertEquals("有字", r[0].text)
    }

    @Test fun removes_whitespace_only() {
        val r = DeleteEmpty.apply(listOf(ev("   \t "), ev("x", 1)))
        assertEquals(1, r.size)
    }

    @Test fun removes_override_tags_only() {
        // 仅有覆盖标签、无可视文本 → 删除
        val r = DeleteEmpty.apply(listOf(ev("{\\k20}"), ev("{\\b1}{\\i1}"), ev("可见", 2)))
        assertEquals(1, r.size)
        assertEquals("可见", r[0].text)
    }

    @Test fun keeps_plain_text_with_tags() {
        val r = DeleteEmpty.apply(listOf(ev("{\\b1}粗体{\\b0}")))
        assertEquals(1, r.size)
    }

    @Test fun keeps_drawing_blocks() {
        // 绘图块不可视但不应删除
        val r = DeleteEmpty.apply(listOf(ev("{\\p1}m 0 0 l 100 100{\\p0}")))
        assertEquals(1, r.size)
    }

    @Test fun keeps_comment_blocks_with_marker_only() {
        // {*note} 注释块无可视文本 → 删除（注释块在对话文本里不可视）
        val r = DeleteEmpty.apply(listOf(ev("{*note}")))
        assertEquals(0, r.size)
    }

    @Test fun empty_list_unchanged() {
        assertEquals(emptyList<AssEvent>(), DeleteEmpty.apply(emptyList()))
    }

    @Test fun preserves_kept_event_ids() {
        val r = DeleteEmpty.apply(listOf(ev("", 0), ev("留", 5), ev("  ", 9)))
        assertEquals(1, r.size)
        assertEquals(5L, r[0].id)
    }
}
