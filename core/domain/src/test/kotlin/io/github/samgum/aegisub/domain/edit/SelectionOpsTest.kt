package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * SelectionOps 多选批量测试：按 id 集合删除/复制/块上下移。
 *
 * @author 伤感咩吖
 */
class SelectionOpsTest {

    private fun ev(id: Long, text: String = "t$id") = AssEvent(id = id, text = text)

    private fun ids(events: List<AssEvent>) = events.map { it.id }

    private fun three() = listOf(ev(0), ev(1), ev(2), ev(3), ev(4))

    // ---------------- 删除 ----------------

    @Test fun delete_by_ids_removes_matching() {
        val r = SelectionOps.deleteByIds(three(), setOf(1L, 3L))
        assertEquals(listOf(0L, 2L, 4L), ids(r))
    }

    @Test fun delete_by_empty_set_is_noop() {
        assertEquals(three(), SelectionOps.deleteByIds(three(), emptySet()))
    }

    @Test fun delete_by_nonexistent_ids_is_noop() {
        assertEquals(three(), SelectionOps.deleteByIds(three(), setOf(99L)))
    }

    // ---------------- 复制 ----------------

    @Test fun duplicate_by_ids_copies_each_right_after() {
        val r = SelectionOps.duplicateByIds(three(), setOf(1L, 3L))
        // 0, 1, dup, 2, 3, dup, 4
        assertEquals(listOf(0L, 1L, 5L, 2L, 3L, 6L, 4L), ids(r))
    }

    @Test fun duplicate_new_ids_are_max_plus_increment() {
        val r = SelectionOps.duplicateByIds(listOf(ev(0), ev(9)), setOf(9L))
        assertEquals(listOf(0L, 9L, 10L), ids(r))
    }

    @Test fun duplicate_preserves_text() {
        val r = SelectionOps.duplicateByIds(listOf(ev(0, "Hello")), setOf(0L))
        assertEquals("Hello", r[1].text)
    }

    @Test fun duplicate_empty_set_is_noop() {
        assertEquals(three(), SelectionOps.duplicateByIds(three(), emptySet()))
    }

    // ---------------- 块上移 ----------------

    @Test fun move_up_block_swaps_with_row_above() {
        // 选中 1,2（连续块 [1,2]）上移 → 与行 0 交换
        val r = SelectionOps.moveUpByIds(three(), setOf(1L, 2L))
        assertEquals(listOf(1L, 2L, 0L, 3L, 4L), ids(r))
    }

    @Test fun move_up_single_id() {
        val r = SelectionOps.moveUpByIds(three(), setOf(2L))
        assertEquals(listOf(0L, 2L, 1L, 3L, 4L), ids(r))
    }

    @Test fun move_up_at_top_is_noop() {
        assertEquals(three(), SelectionOps.moveUpByIds(three(), setOf(0L)))
        // 块含顶行也 no-op
        assertEquals(three(), SelectionOps.moveUpByIds(three(), setOf(0L, 1L)))
    }

    @Test fun move_up_non_contiguous_is_noop() {
        // 0 和 2 不连续 → no-op
        assertEquals(three(), SelectionOps.moveUpByIds(three(), setOf(0L, 2L)))
    }

    @Test fun move_up_empty_is_noop() {
        assertEquals(three(), SelectionOps.moveUpByIds(three(), emptySet()))
    }

    // ---------------- 块下移 ----------------

    @Test fun move_down_block_swaps_with_row_below() {
        // 选中 1,2 下移 → 与行 3 交换
        val r = SelectionOps.moveDownByIds(three(), setOf(1L, 2L))
        assertEquals(listOf(0L, 3L, 1L, 2L, 4L), ids(r))
    }

    @Test fun move_down_at_bottom_is_noop() {
        assertEquals(three(), SelectionOps.moveDownByIds(three(), setOf(4L)))
    }

    @Test fun move_down_non_contiguous_is_noop() {
        assertEquals(three(), SelectionOps.moveDownByIds(three(), setOf(2L, 4L)))
    }

    // ---------------- 通用 ----------------

    @Test fun all_ops_produce_unique_ids() {
        val events = three()
        val dup = SelectionOps.duplicateByIds(events, setOf(0L, 2L))
        val dids = dup.map { it.id }
        assertEquals(dids.size, dids.toSet().size)
    }

    @Test fun duplicate_keeps_unselected_timing_intact() {
        val e = AssEvent(id = 1, start = SubTime.ofMillis(100), end = SubTime.ofMillis(300), text = "x")
        val r = SelectionOps.duplicateByIds(listOf(ev(0), e), setOf(1L))
        assertEquals(100L, r[2].start.millis)
        assertEquals(300L, r[2].end.millis)
    }
}
