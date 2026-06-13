package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime

/**
 * 时间后处理（复刻桌面 Aegisub Timing Post-Processor）。
 *
 * 按字幕网格顺序处理（调用方应确保事件按时间/显示顺序排列）：
 * - **lead-in/out**：每行起始提前 [leadInMs]、结束延后 [leadOutMs]（起始钳零，结束钳到 maxEndMs）。
 * - **去重叠 / 最小间隙**：相邻行若 [events[i].end] > [events[i+1].start - gapMs]，把 end 收回到
 *   `next.start - gapMs`，且不低于自身 start（保证 end ≥ start）。
 * - [apply] 组合：先 lead-in/out 再去间隙。
 *
 * 纯函数；通过 `session.editEvents { TimePostProcess.xxx(it, ...) }` 接入，单撤销点。
 *
 * @author 伤感咩吖
 */
object TimePostProcess {

    /** 对每行应用 lead-in/out（起始提前、结束延后，分别钳界）。 */
    fun applyLeadInOut(
        events: List<AssEvent>,
        leadInMs: Long,
        leadOutMs: Long,
        maxEndMs: Long = SubTime.MAX_MICROS / 1_000,
    ): List<AssEvent> {
        if (leadInMs == 0L && leadOutMs == 0L) return events
        val leadInMicros = leadInMs * 1_000
        val leadOutMicros = leadOutMs * 1_000
        val maxEndMicros = (maxEndMs.coerceAtLeast(0L)) * 1_000
        return events.map { e ->
            val newStart = SubTime.ofMicros(e.start.micros - leadInMicros)
            val newEndRaw = (e.end.micros + leadOutMicros).coerceAtMost(maxEndMicros)
            // 保证 end ≥ start（lead-in 不应导致倒置）
            val newEnd = SubTime.ofMicros(maxOf(newEndRaw, newStart.micros))
            e.copy(start = newStart, end = newEnd)
        }
    }

    /** 相邻行去重叠并强制最小间隙 [gapMs]。 */
    fun applyGap(events: List<AssEvent>, gapMs: Long): List<AssEvent> {
        if (events.size < 2 || gapMs <= 0L) return events
        val gapMicros = gapMs * 1_000
        return events.mapIndexed { i, e ->
            if (i == events.lastIndex) return@mapIndexed e
            val nextStart = events[i + 1].start.micros
            val maxEnd = (nextStart - gapMicros).coerceAtLeast(e.start.micros)
            e.copy(end = SubTime.ofMicros(minOf(e.end.micros, maxEnd)))
        }
    }

    /** 组合：先 lead-in/out 再去间隙。 */
    fun apply(
        events: List<AssEvent>,
        leadInMs: Long,
        leadOutMs: Long,
        gapMs: Long,
        maxEndMs: Long = SubTime.MAX_MICROS / 1_000,
    ): List<AssEvent> = applyGap(applyLeadInOut(events, leadInMs, leadOutMs, maxEndMs), gapMs)
}
