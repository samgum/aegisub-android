package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime

/**
 * 行级操作（复刻桌面 Aegisub 的 Edit 菜单与字幕网格右键菜单）。
 *
 * 全部为纯函数：输入事件列表 → 输出新列表，不改原列表。
 * - 新增行（复制 / 插入 / 分割的第二半）的 id 取「当前列表最大 id + 1」，保证唯一。
 * - 其余行的 id 保持不变（撤销栈依 id 识别身份）。
 * - 时间一律在微秒层运算后经 [SubTime.ofMicros] 钳制到 [0, 10h]。
 *
 * 通过 `session.editEvents { LineOps.xxx(it, ...) }` 接入，每次调用 = 一个撤销点。
 *
 * @author 伤感咩吖
 */
object LineOps {

    /** 当前列表的下一个可用 id（max + 1；空列表返回 0）。 */
    private fun nextId(events: List<AssEvent>): Long = (events.maxOfOrNull { it.id } ?: -1L) + 1L

    // ---------------- 复制（Aegisub: Duplicate Lines） ----------------

    /**
     * 复制 [index] 行并插入到其紧后位置（副本内容、样式、时间完全一致，仅 id 不同）。
     * 越界时原样返回。
     */
    fun duplicate(events: List<AssEvent>, index: Int): List<AssEvent> {
        if (index !in events.indices) return events
        val copy = events[index].copy(id = nextId(events))
        return events.toMutableList().apply { add(index + 1, copy) }
    }

    // ---------------- 删除（Aegisub: Delete Lines） ----------------

    /** 删除 [index] 行。越界时原样返回。 */
    fun delete(events: List<AssEvent>, index: Int): List<AssEvent> {
        if (index !in events.indices) return events
        return events.toMutableList().apply { removeAt(index) }
    }

    // ---------------- 插入空行（Aegisub 网格右键: Insert Before / After） ----------------

    /**
     * 在 [index] 处插入一行空白（继承被点行的样式/层/边距/效果/时间，清空文本），返回新列表。
     * [index] 钳制到 [0, size]；空列表时插入一条默认事件。
     */
    fun insertBefore(events: List<AssEvent>, index: Int): List<AssEvent> {
        val nid = nextId(events)
        val base = events.getOrNull(index) ?: events.lastOrNull()
        val blank = base?.copy(id = nid, text = "") ?: AssEvent(id = nid)
        val at = index.coerceIn(0, events.size)
        return events.toMutableList().apply { add(at, blank) }
    }

    /**
     * 在 [index] + 1 处插入一行空白（语义同 [insertBefore]）。
     * 插入位置钳制到 [0, size]。
     */
    fun insertAfter(events: List<AssEvent>, index: Int): List<AssEvent> {
        val nid = nextId(events)
        val base = events.getOrNull(index) ?: events.lastOrNull()
        val blank = base?.copy(id = nid, text = "") ?: AssEvent(id = nid)
        val at = (index + 1).coerceIn(0, events.size)
        return events.toMutableList().apply { add(at, blank) }
    }

    // ---------------- 合并（Aegisub: Join concatenate / keep first） ----------------

    /**
     * 把选中的多行合并为一行（拼接文本）：保留首行的全部元数据，
     * 起止时间取选中行的并集（min start / max end），文本为各行文本顺序拼接。
     *
     * 合并产物落在「最小选中下标」处，其余选中行删除。
     * 选中行数 < 2 或含越界下标过滤后 < 2 → 原样返回。
     *
     * @param indices 选中行下标（无须有序、可能含重复/越界，内部去重排序）
     */
    fun joinConcatenate(events: List<AssEvent>, indices: List<Int>): List<AssEvent> =
        joinImpl(events, indices, concatenateText = true)

    /**
     * 同 [joinConcatenate]，但文本仅保留首行（Aegisub: Join (keep first)）。
     */
    fun joinKeepFirst(events: List<AssEvent>, indices: List<Int>): List<AssEvent> =
        joinImpl(events, indices, concatenateText = false)

    private fun joinImpl(
        events: List<AssEvent>,
        indices: List<Int>,
        concatenateText: Boolean,
    ): List<AssEvent> {
        val sorted = indices.filter { it in events.indices }.distinct().sorted()
        if (sorted.size < 2) return events
        val selected = sorted.map { events[it] }
        val first = selected.first()
        val joined = first.copy(
            start = SubTime.ofMicros(selected.minOf { it.start.micros }),
            end = SubTime.ofMicros(selected.maxOf { it.end.micros }),
            text = if (concatenateText) selected.joinToString("") { it.text } else first.text,
        )
        val selectedSet = sorted.toSet()
        val result = ArrayList<AssEvent>(events.size - sorted.size + 1)
        var emitted = false
        for ((i, e) in events.withIndex()) {
            if (i in selectedSet) {
                if (!emitted) { result.add(joined); emitted = true }
            } else {
                result.add(e)
            }
        }
        return result
    }

    // ---------------- 分割（Aegisub: Split Lines at cursor / 按文本位置） ----------------

    /**
     * 把 [index] 行按文本字符位置 [atCharPosition] 一分为二：
     * - 前半：`text[0, pos)`，起止 = 原起 → 时间比例中点。
     * - 后半：`text[pos, end)`，新 id，时间比例中点 → 原止。
     *
     * 时间按文本长度比例线性分配（pos / length），文本为空时中点退化为原起。
     * [atCharPosition] 钳制到 [0, length]；[index] 越界原样返回。
     * 两半均继承原行的样式 / 层 / 边距 / 效果。
     */
    fun splitAt(events: List<AssEvent>, index: Int, atCharPosition: Int): List<AssEvent> {
        if (index !in events.indices) return events
        val e = events[index]
        val len = e.text.length
        val pos = atCharPosition.coerceIn(0, len)
        val totalMicros = e.end.micros - e.start.micros
        val midMicros = if (len == 0) e.start.micros
            else e.start.micros + totalMicros * pos / len
        val first = e.copy(text = e.text.substring(0, pos), end = SubTime.ofMicros(midMicros))
        val second = e.copy(id = nextId(events), text = e.text.substring(pos), start = SubTime.ofMicros(midMicros))
        return events.toMutableList().apply {
            removeAt(index)
            addAll(index, listOf(first, second))
        }
    }

    /** 便捷：在文本长度中点处分割（向下取整）。 */
    fun splitAtMidpoint(events: List<AssEvent>, index: Int): List<AssEvent> {
        val text = events.getOrNull(index)?.text ?: return events
        return splitAt(events, index, text.length / 2)
    }

    // ---------------- 上移 / 下移（交换相邻） ----------------

    /** 把 [index] 行与其前一行交换。[index] 在 [1, lastIndex] 之外原样返回。 */
    fun moveUp(events: List<AssEvent>, index: Int): List<AssEvent> {
        if (index !in 1..events.lastIndex) return events
        return events.toMutableList().apply {
            val prev = this[index - 1]
            this[index - 1] = this[index]
            this[index] = prev
        }
    }

    /** 把 [index] 行与其后一行交换。[index] 在 [0, lastIndex-1] 之外原样返回。 */
    fun moveDown(events: List<AssEvent>, index: Int): List<AssEvent> {
        if (index !in 0..events.lastIndex - 1) return events
        return events.toMutableList().apply {
            val next = this[index + 1]
            this[index + 1] = this[index]
            this[index] = next
        }
    }

    /** 便捷：对 [AssScript.events] 应用任意行操作，返回新脚本（结构共享）。 */
    fun applyTo(script: AssScript, op: (List<AssEvent>) -> List<AssEvent>): AssScript =
        script.withEvents(op(script.events))
}
