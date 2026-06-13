package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime

/** 批量平移的作用对象：起止都移 / 仅起始 / 仅结束。 */
enum class ShiftTarget { BOTH, START, END }

/**
 * 批量时间偏移（复刻 Aegisub Shift Times）。
 *
 * - [deltaMs] 正=后移，负=前移；越界自动钳制到 [0, 10h]（SubTime.ofMillis 内部保证）。
 * - [target] 指定移起始/结束/两者。
 * - [fromStart] 非空时仅平移 start ≥ 游标的事件（对齐 Aegisub「平移游标之后」），其余原样保留。
 *
 * 返回新列表，事件 id 与其它字段保持不变。
 *
 * @author 伤感咩吖
 */
object TimeShift {
    fun apply(
        events: List<AssEvent>,
        deltaMs: Long,
        target: ShiftTarget = ShiftTarget.BOTH,
        fromStart: SubTime? = null,
    ): List<AssEvent> {
        // 直接在微秒层加减后交给 ofMicros 钳制（SubTime 非负，不能先构造负 delta）
        val deltaMicros = deltaMs * 1_000
        return events.map { e ->
            if (fromStart != null && e.start < fromStart) return@map e
            val newStart = if (target != ShiftTarget.END) SubTime.ofMicros(e.start.micros + deltaMicros) else e.start
            val newEnd = if (target != ShiftTarget.START) SubTime.ofMicros(e.end.micros + deltaMicros) else e.end
            e.copy(start = newStart, end = newEnd)
        }
    }
}
