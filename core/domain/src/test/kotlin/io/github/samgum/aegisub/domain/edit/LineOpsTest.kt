package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.Margins
import io.github.samgum.aegisub.domain.time.SubTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * LineOps 行级操作测试：复制/删除/插入/分割/合并/上下移。
 * 语义对齐桌面 Aegisub 的 Edit 菜单与字幕网格右键菜单。
 *
 * @author 伤感咩吖
 */
class LineOpsTest {

    private fun ev(
        id: Long,
        startMs: Long = 0L,
        endMs: Long = 5_000L,
        text: String = "t$id",
        style: String = "Default",
        layer: Int = 0,
    ) = AssEvent(
        id = id,
        start = SubTime.ofMillis(startMs),
        end = SubTime.ofMillis(endMs),
        text = text,
        style = style,
        layer = layer,
    )

    // ---------------- 复制 ----------------

    @Test fun duplicate_inserts_identical_copy_after_with_fresh_id() {
        val r = LineOps.duplicate(listOf(ev(0, 1_000, 3_000, "Hello"), ev(1)), index = 0)
        assertEquals(3, r.size)
        assertEquals("Hello", r[0].text)
        // 副本紧随其后，内容一致，id 为新分配
        assertEquals("Hello", r[1].text)
        assertEquals(1_000L, r[1].start.millis)
        assertEquals(3_000L, r[1].end.millis)
        assertNotEquals(0L, r[1].id)
        // 其余行顺延
        assertEquals(1L, r[2].id)
    }

    @Test fun duplicate_out_of_bounds_unchanged() {
        val events = listOf(ev(0), ev(1))
        assertEquals(events, LineOps.duplicate(events, index = 5))
        assertEquals(events, LineOps.duplicate(events, index = -1))
    }

    @Test fun duplicate_fresh_id_uses_max_plus_one() {
        // id 非连续：max=9 → 新 id=10
        val events = listOf(ev(0), ev(9))
        val r = LineOps.duplicate(events, index = 1)
        assertEquals(10L, r[2].id)
    }

    // ---------------- 删除 ----------------

    @Test fun delete_removes_target_keeps_others() {
        val r = LineOps.delete(listOf(ev(0), ev(1), ev(2)), index = 1)
        assertEquals(2, r.size)
        assertEquals(0L, r[0].id)
        assertEquals(2L, r[1].id)
    }

    @Test fun delete_out_of_bounds_unchanged() {
        val events = listOf(ev(0))
        assertEquals(events, LineOps.delete(events, index = 3))
    }

    // ---------------- 插入（前/后） ----------------

    @Test fun insert_before_puts_blank_at_index_inheriting_style() {
        val events = listOf(ev(0, style = "Title", layer = 2), ev(1, style = "Default"))
        val r = LineOps.insertBefore(events, index = 0)
        assertEquals(3, r.size)
        // 新行落在 index 0
        assertEquals("", r[0].text)
        assertEquals("Title", r[0].style)
        assertEquals(2, r[0].layer)
        assertNotEquals(0L, r[0].id)
        // 原行顺延
        assertEquals(0L, r[1].id)
        assertEquals(1L, r[2].id)
    }

    @Test fun insert_after_puts_blank_at_index_plus_one() {
        val events = listOf(ev(0, style = "Title"), ev(1))
        val r = LineOps.insertAfter(events, index = 0)
        assertEquals(3, r.size)
        assertEquals(0L, r[0].id)
        assertEquals("", r[1].text)
        assertEquals("Title", r[1].style) // 继承被点行样式
        assertEquals(1L, r[2].id)
    }

    @Test fun insert_into_empty_list_yields_single_blank() {
        val r = LineOps.insertBefore(emptyList(), index = 0)
        assertEquals(1, r.size)
        assertEquals("", r[0].text)
        // 空列表时新 id 退化为 0（max(-1)+1），与 session 加载首行 id=0 约定一致
        assertEquals(0L, r[0].id)
        // 再插一条则 id 递增，保证唯一
        val r2 = LineOps.insertAfter(r, index = 0)
        assertEquals(1L, r2[1].id)
    }

    @Test fun insert_after_at_last_index_appends() {
        val events = listOf(ev(0), ev(1))
        val r = LineOps.insertAfter(events, index = 1)
        assertEquals(3, r.size)
        assertEquals(1L, r[1].id)
        assertEquals("", r[2].text)
    }

    // ---------------- 合并（拼接 / 留首） ----------------

    @Test fun join_concatenate_merges_text_and_unions_time() {
        val a = ev(0, startMs = 1_000, endMs = 3_000, text = "Foo")
        val b = ev(1, startMs = 2_000, endMs = 5_000, text = "Bar")
        val r = LineOps.joinConcatenate(listOf(a, b), indices = listOf(0, 1))
        assertEquals(1, r.size)
        assertEquals("FooBar", r[0].text)
        assertEquals(1_000L, r[0].start.millis) // min start
        assertEquals(5_000L, r[0].end.millis)   // max end
        assertEquals(0L, r[0].id)               // 留首 id
        assertEquals("Default", r[0].style)
    }

    @Test fun join_concatenate_keeps_first_metadata() {
        val a = ev(0, text = "A", style = "Title", layer = 3)
        val b = ev(1, text = "B", style = "Other", layer = 9)
        val r = LineOps.joinConcatenate(listOf(a, b), indices = listOf(0, 1))
        assertEquals(1, r.size)
        assertEquals("Title", r[0].style)
        assertEquals(3, r[0].layer)
    }

    @Test fun join_concatenate_non_contiguous_indices() {
        // 选中 index 0 和 2，跳过 1
        val events = listOf(ev(0, text = "A"), ev(1, text = "X"), ev(2, text = "B"))
        val r = LineOps.joinConcatenate(events, indices = listOf(0, 2))
        assertEquals(2, r.size)
        assertEquals("AB", r[0].text)
        // 中间未选中行保留
        assertEquals("X", r[1].text)
    }

    @Test fun join_concatenate_single_index_is_noop() {
        val events = listOf(ev(0), ev(1))
        assertEquals(events, LineOps.joinConcatenate(events, indices = listOf(0)))
    }

    @Test fun join_keep_first_uses_first_text_only() {
        val a = ev(0, startMs = 1_000, endMs = 3_000, text = "Foo")
        val b = ev(1, startMs = 2_000, endMs = 5_000, text = "Bar")
        val r = LineOps.joinKeepFirst(listOf(a, b), indices = listOf(0, 1))
        assertEquals(1, r.size)
        assertEquals("Foo", r[0].text)
        assertEquals(1_000L, r[0].start.millis)
        assertEquals(5_000L, r[0].end.millis)
    }

    // ---------------- 分割 ----------------

    @Test fun split_at_position_divides_text_and_time_proportionally() {
        // "ABCD" 长度 4，在 pos=2 处分割 → 时间对半
        val e = ev(0, startMs = 1_000, endMs = 3_000, text = "ABCD")
        val r = LineOps.splitAt(listOf(e), index = 0, atCharPosition = 2)
        assertEquals(2, r.size)
        assertEquals("AB", r[0].text)
        assertEquals(1_000L, r[0].start.millis)
        assertEquals(2_000L, r[0].end.millis) // 中点 2000
        assertEquals("CD", r[1].text)
        assertEquals(2_000L, r[1].start.millis)
        assertEquals(3_000L, r[1].end.millis)
        assertNotEquals(r[0].id, r[1].id)
    }

    @Test fun split_clamps_position_to_text_bounds() {
        val e = ev(0, text = "Hi")
        val r = LineOps.splitAt(listOf(e), index = 0, atCharPosition = 99)
        assertEquals(2, r.size)
        assertEquals("Hi", r[0].text)
        assertEquals("", r[1].text)
    }

    @Test fun split_at_midpoint_convenience() {
        val e = ev(0, startMs = 0, endMs = 4_000, text = "1234")
        val r = LineOps.splitAtMidpoint(listOf(e), index = 0)
        assertEquals(2, r.size)
        assertEquals("12", r[0].text)
        assertEquals(2_000L, r[0].end.millis)
        assertEquals("34", r[1].text)
        assertEquals(2_000L, r[1].start.millis)
    }

    @Test fun split_keeps_first_line_metadata_on_both_halves() {
        val e = ev(0, text = "AB", style = "Title", layer = 2, startMs = 0, endMs = 4_000)
        val r = LineOps.splitAtMidpoint(listOf(e), index = 0)
        assertEquals("Title", r[1].style)
        assertEquals(2, r[1].layer)
    }

    @Test fun split_out_of_bounds_unchanged() {
        val events = listOf(ev(0))
        assertEquals(events, LineOps.splitAtMidpoint(events, index = 5))
    }

    // ---------------- 上移 / 下移 ----------------

    @Test fun move_up_swaps_with_predecessor() {
        val events = listOf(ev(0), ev(1), ev(2))
        val r = LineOps.moveUp(events, index = 1)
        assertEquals(listOf(1L, 0L, 2L), r.map { it.id })
    }

    @Test fun move_up_at_top_is_noop() {
        val events = listOf(ev(0), ev(1))
        assertEquals(events, LineOps.moveUp(events, index = 0))
    }

    @Test fun move_down_swaps_with_successor() {
        val events = listOf(ev(0), ev(1), ev(2))
        val r = LineOps.moveDown(events, index = 0)
        assertEquals(listOf(1L, 0L, 2L), r.map { it.id })
    }

    @Test fun move_down_at_bottom_is_noop() {
        val events = listOf(ev(0), ev(1))
        assertEquals(events, LineOps.moveDown(events, index = 1))
    }

    @Test fun move_preserves_event_fields() {
        val events = listOf(ev(0, text = "A"), ev(1, text = "B"))
        val r = LineOps.moveUp(events, index = 1)
        // 下标 0 现在是原 id=1 那条，内容随 id 走
        assertEquals("B", r[0].text)
        assertEquals("A", r[1].text)
    }

    // ---------------- 通用不变量 ----------------

    @Test fun all_ops_produce_only_unique_ids() {
        val events = listOf(ev(0), ev(1), ev(2, startMs = 0, endMs = 2_000, text = "XY"))
        for (op in listOf(
            LineOps.duplicate(events, 1),
            LineOps.insertBefore(events, 1),
            LineOps.insertAfter(events, 1),
            LineOps.joinConcatenate(events, listOf(0, 1, 2)),
            LineOps.splitAtMidpoint(events, 2),
            LineOps.moveUp(events, 2),
            LineOps.moveDown(events, 0),
        )) {
            val ids = op.map { it.id }
            assertEquals(ids.size, ids.toSet().size, "发现重复 id：$ids")
        }
    }

    @Test fun delete_then_list_still_consistent() {
        val events = listOf(ev(0, text = "A"), ev(1, text = "B"), ev(2, text = "C"))
        val r = LineOps.delete(events, index = 1)
        assertTrue(r.map { it.text } == listOf("A", "C"))
    }

    @Test fun duplicate_copy_has_independent_margins() {
        val e = AssEvent(id = 0, text = "x", margins = Margins(10, 20, 30))
        val r = LineOps.duplicate(listOf(e), index = 0)
        assertEquals(Margins(10, 20, 30), r[1].margins)
    }
}
