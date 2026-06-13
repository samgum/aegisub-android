package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent

/** 排序键（对齐桌面 Aegisub Sort Lines）。 */
enum class SortKey { START, END, STYLE, ACTOR, EFFECT, TEXT, LAYER }

/** 排序方向。 */
enum class SortOrder { ASCENDING, DESCENDING }

/**
 * 事件排序（复刻桌面 Aegisub Sort Lines）。
 *
 * - 按指定 [SortKey] 升/降序排列；相等项保持原序（稳定排序）。
 * - 纯函数：不改原列表，仅返回新顺序；事件本身字段不变。
 *
 * 通过 `session.editEvents { SortLines.apply(it, key, order) }` 接入，一次撤销点。
 *
 * @author 伤感咩吖
 */
object SortLines {
    fun apply(events: List<AssEvent>, key: SortKey, order: SortOrder = SortOrder.ASCENDING): List<AssEvent> {
        val base: Comparator<AssEvent> = when (key) {
            SortKey.START -> compareBy { it.start }
            SortKey.END -> compareBy { it.end }
            SortKey.STYLE -> compareBy { it.style }
            SortKey.ACTOR -> compareBy { it.actor }
            SortKey.EFFECT -> compareBy { it.effect }
            SortKey.TEXT -> compareBy { it.strippedText }
            SortKey.LAYER -> compareBy { it.layer }
        }
        val cmp = if (order == SortOrder.DESCENDING) base.reversed() else base
        // sortedWith 稳定：相等元素保留原相对顺序
        return events.sortedWith(cmp)
    }
}
