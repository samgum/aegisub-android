package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * TimePostProcess 测试：lead-in/out 提前起始/延后结束 + 去重叠强制最小间隙。
 *
 * @author 伤感咩吖
 */
class TimePostProcessTest {

    private fun ev(startMs: Long, endMs: Long, id: Long = 0) = AssEvent(
        id = id, start = SubTime.ofMillis(startMs), end = SubTime.ofMillis(endMs),
    )

    private fun List<AssEvent>.s(i: Int) = this[i].start.millis
    private fun List<AssEvent>.e(i: Int) = this[i].end.millis

    // ---------------- lead-in / lead-out ----------------

    @Test fun lead_in_pulls_start_back() {
        val r = TimePostProcess.applyLeadInOut(listOf(ev(2_000, 4_000)), leadInMs = 500, leadOutMs = 0)
        assertEquals(1_500L, r.s(0))
        assertEquals(4_000L, r.e(0))
    }

    @Test fun lead_in_clamped_at_zero() {
        val r = TimePostProcess.applyLeadInOut(listOf(ev(200, 1_000)), leadInMs = 500, leadOutMs = 0)
        assertEquals(0L, r.s(0))
    }

    @Test fun lead_out_extends_end() {
        val r = TimePostProcess.applyLeadInOut(listOf(ev(1_000, 3_000)), leadInMs = 0, leadOutMs = 500)
        assertEquals(3_500L, r.e(0))
    }

    @Test fun lead_out_capped_at_max_end() {
        val r = TimePostProcess.applyLeadInOut(
            listOf(ev(1_000, 9_000)), leadInMs = 0, leadOutMs = 2_000, maxEndMs = 10_000,
        )
        assertEquals(10_000L, r.e(0))
    }

    @Test fun lead_does_not_invert_timing() {
        // 起始延后不会被 lead-in 弄到 > 结束
        val r = TimePostProcess.applyLeadInOut(listOf(ev(5_000, 5_000)), leadInMs = 100, leadOutMs = 0)
        assertEquals(true, r.s(0) <= r.e(0))
    }

    // ---------------- gap / overlap removal ----------------

    @Test fun gap_removes_overlap() {
        // 行0 end=3000 与 行1 start=2500 重叠；gap=200 → 行0 end 钳到 2500-200=2300
        val events = listOf(ev(1_000, 3_000, 0), ev(2_500, 5_000, 1))
        val r = TimePostProcess.applyGap(events, gapMs = 200)
        assertEquals(2_300L, r.e(0))
    }

    @Test fun gap_keeps_non_overlapping() {
        val events = listOf(ev(1_000, 2_000, 0), ev(3_000, 5_000, 1))
        val r = TimePostProcess.applyGap(events, gapMs = 200)
        assertEquals(2_000L, r.e(0)) // 已有 1000ms 间隙 > 200，不变
    }

    @Test fun gap_enforces_minimum_when_close() {
        // 行0 end=2800，行1 start=3000，间隙 200 = gap → 刚好不钳（end ≤ start-gap = 2800）
        val events = listOf(ev(1_000, 2_800, 0), ev(3_000, 5_000, 1))
        val r = TimePostProcess.applyGap(events, gapMs = 200)
        assertEquals(2_800L, r.e(0))
    }

    @Test fun gap_does_not_pull_end_below_start() {
        // 极端：行0 占满到行1 起始，gap 大到 maxEnd < start → 钳到 start（end≥start）
        val events = listOf(ev(1_000, 3_000, 0), ev(1_500, 5_000, 1))
        val r = TimePostProcess.applyGap(events, gapMs = 2_000)
        assertEquals(1_000L, r.e(0)) // 钳到 start
    }

    @Test fun gap_single_element_unchanged() {
        val events = listOf(ev(1_000, 2_000))
        assertEquals(events, TimePostProcess.applyGap(events, gapMs = 100))
    }

    @Test fun gap_empty_unchanged() {
        assertEquals(emptyList<AssEvent>(), TimePostProcess.applyGap(emptyList(), gapMs = 100))
    }

    @Test fun gap_preserves_ids_and_order() {
        val events = listOf(ev(1_000, 3_000, 7), ev(2_500, 5_000, 9))
        val r = TimePostProcess.applyGap(events, gapMs = 100)
        assertEquals(listOf(7L, 9L), r.map { it.id })
    }

    // ---------------- 组合 ----------------

    @Test fun apply_combo_lead_then_gap() {
        // lead-in/out 后行0 end 延到 3500，与行1 start 2500 重叠 → gap 钳回
        val events = listOf(ev(1_000, 3_000, 0), ev(2_500, 5_000, 1))
        val r = TimePostProcess.apply(events, leadInMs = 0, leadOutMs = 500, gapMs = 100)
        // 行0 end: 3500 → 钳到 2500-100=2400
        assertEquals(2_400L, r.e(0))
        // 行1 是末行，lead-out 延后 5000→5500，无下一行不被 gap 钳
        assertEquals(5_500L, r.e(1))
    }

    @Test fun apply_no_op_when_all_zero() {
        val events = listOf(ev(1_000, 2_000, 0))
        val r = TimePostProcess.apply(events, leadInMs = 0, leadOutMs = 0, gapMs = 0)
        assertEquals(1_000L, r.s(0))
        assertEquals(2_000L, r.e(0))
    }
}
