package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent

/**
 * 多选批量操作（复刻桌面 Aegisub 字幕网格多选 + 批量编辑）。
 *
 * 全部按事件 **id 集合** 操作（与列表下标解耦，UI 选中态以 id 为准）。
 * - 复制新行 id = 当前最大 id 递增分配，保证唯一。
 * - 块上下移仅对**连续**选中块生效（非连续 no-op，对齐 Aegisub）。
 *
 * 纯函数：不改原列表。通过 `session.editEvents { SelectionOps.xxx(it, ids) }` 接入，单撤销点。
 *
 * @author 伤感咩吖
 */
object SelectionOps {

    /** 删除 id 命中的事件。 */
    fun deleteByIds(events: List<AssEvent>, ids: Set<Long>): List<AssEvent> =
        events.filterNot { it.id in ids }

    /**
     * 复制 id 命中的事件：每个副本紧跟原行之后，按出现顺序分配新 id（max+1 递增）。
     */
    fun duplicateByIds(events: List<AssEvent>, ids: Set<Long>): List<AssEvent> {
        if (ids.isEmpty()) return events
        var next = (events.maxOfOrNull { it.id } ?: -1L) + 1L
        val result = ArrayList<AssEvent>(events.size + ids.size)
        for (e in events) {
            result.add(e)
            if (e.id in ids) result.add(e.copy(id = next++))
        }
        return result
    }

    /**
     * 把连续选中块整体上移一行（与块上方单行交换）。
     * 选中不连续、块已在顶部、空集 → 原样返回。
     */
    fun moveUpByIds(events: List<AssEvent>, ids: Set<Long>): List<AssEvent> {
        val range = contiguousRange(events, ids) ?: return events
        val (lo, hi) = range
        if (lo == 0) return events
        val result = events.toMutableList()
        val mover = result.removeAt(lo - 1)
        result.add(hi, mover)
        return result
    }

    /**
     * 把连续选中块整体下移一行（与块下方单行交换）。
     * 选中不连续、块已在底部、空集 → 原样返回。
     */
    fun moveDownByIds(events: List<AssEvent>, ids: Set<Long>): List<AssEvent> {
        val range = contiguousRange(events, ids) ?: return events
        val (lo, hi) = range
        if (hi >= events.lastIndex) return events
        val result = events.toMutableList()
        val mover = result.removeAt(hi + 1)
        result.add(lo, mover)
        return result
    }

    /**
     * 选中下标是否构成连续区间 [lo, hi]；连续则返回，否则 null。
     */
    private fun contiguousRange(events: List<AssEvent>, ids: Set<Long>): Pair<Int, Int>? {
        if (ids.isEmpty()) return null
        val indices = events.indices.filter { events[it].id in ids }
        if (indices.isEmpty()) return null
        val lo = indices.first()
        val hi = indices.last()
        // 连续当且仅当数量 == hi-lo+1 且无间隔
        if (indices.size != hi - lo + 1) return null
        return lo to hi
    }
}
