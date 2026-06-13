package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * TimeShift 批量时间偏移测试：前移/后移/钳零/游标过滤/起止独立。
 *
 * @author 伤感咩吖
 */
class TimeShiftTest {

    private fun ev(id: Long, startMs: Long, endMs: Long) = AssEvent(
        id = id, start = SubTime.ofMillis(startMs), end = SubTime.ofMillis(endMs), text = "t$id",
    )

    private fun List<AssEvent>.startMs(i: Int) = this[i].start.millis
    private fun List<AssEvent>.endMs(i: Int) = this[i].end.millis

    @Test fun shift_both_forward() {
        val r = TimeShift.apply(listOf(ev(0, 1_000, 3_000)), deltaMs = 500)
        assertEquals(1_500L, r.startMs(0))
        assertEquals(3_500L, r.endMs(0))
    }

    @Test fun shift_both_backward_clamped_at_zero() {
        // -1500ms：start 1000 → 钳零；end 3000 → 1500
        val r = TimeShift.apply(listOf(ev(0, 1_000, 3_000)), deltaMs = -1_500)
        assertEquals(0L, r.startMs(0))
        assertEquals(1_500L, r.endMs(0))
    }

    @Test fun from_start_only_shifts_events_at_or_after_cursor() {
        val events = listOf(ev(0, 500, 1_000), ev(1, 2_000, 3_000), ev(2, 5_000, 6_000))
        val r = TimeShift.apply(events, deltaMs = 1_000, fromStart = SubTime.ofMillis(2_000))
        // 第 0 条 start 500 < 2000：不动
        assertEquals(500L, r.startMs(0))
        assertEquals(1_000L, r.endMs(0))
        // 第 1 条 start 2000 >= 2000：后移
        assertEquals(3_000L, r.startMs(1))
        // 第 2 条同理
        assertEquals(6_000L, r.startMs(2))
    }

    @Test fun target_start_only_shifts_start() {
        val r = TimeShift.apply(listOf(ev(0, 1_000, 3_000)), deltaMs = 500, target = ShiftTarget.START)
        assertEquals(1_500L, r.startMs(0))
        assertEquals(3_000L, r.endMs(0))
    }

    @Test fun target_end_only_shifts_end() {
        val r = TimeShift.apply(listOf(ev(0, 1_000, 3_000)), deltaMs = 500, target = ShiftTarget.END)
        assertEquals(1_000L, r.startMs(0))
        assertEquals(3_500L, r.endMs(0))
    }

    @Test fun preserves_ids_and_other_fields() {
        val r = TimeShift.apply(listOf(ev(7, 1_000, 3_000)), deltaMs = 100)
        assertEquals(7L, r[0].id)
        assertEquals("t7", r[0].text)
    }
}
