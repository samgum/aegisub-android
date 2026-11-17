package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime

/**
 * 行级操作（完整复刻桌面 Aegisub 的 Edit / Subtitle / Timing 菜单与字幕网格右键菜单）。
 *
 * 全部为纯函数：输入事件列表 → 输出新列表，不改原列表。
 *
 * @author 伤感咩吖
 */
object LineOps {

    private fun nextId(events: List<AssEvent>): Long = (events.maxOfOrNull { it.id } ?: -1L) + 1L

    fun duplicate(events: List<AssEvent>, index: Int): List<AssEvent> {
        if (index !in events.indices) return events
        val copy = events[index].copy(id = nextId(events))
        return events.toMutableList().apply { add(index + 1, copy) }
    }

    fun delete(events: List<AssEvent>, index: Int): List<AssEvent> {
        if (index !in events.indices) return events
        return events.toMutableList().apply { removeAt(index) }
    }

    fun insertBefore(events: List<AssEvent>, index: Int): List<AssEvent> {
        val nid = nextId(events)
        val base = events.getOrNull(index) ?: events.lastOrNull()
        val blank = base?.copy(id = nid, text = "") ?: AssEvent(id = nid)
        val at = index.coerceIn(0, events.size)
        return events.toMutableList().apply { add(at, blank) }
    }

    fun insertAfter(events: List<AssEvent>, index: Int): List<AssEvent> {
        val nid = nextId(events)
        val base = events.getOrNull(index) ?: events.lastOrNull()
        val blank = base?.copy(id = nid, text = "") ?: AssEvent(id = nid)
        val at = (index + 1).coerceIn(0, events.size)
        return events.toMutableList().apply { add(at, blank) }
    }

    /** 仿桌面 Aegisub `Insert at video time (before)`：在当前行前插入以视频时间为起点的 2 秒新行。 */
    fun insertBeforeAtVideoTime(events: List<AssEvent>, index: Int, videoTimeMs: Long, defaultDurMs: Long = 2_000L): List<AssEvent> {
        val nid = nextId(events)
        val base = events.getOrNull(index) ?: events.lastOrNull()
        val start = SubTime.ofMillis(videoTimeMs.coerceAtLeast(0L))
        val end = SubTime.ofMillis((videoTimeMs + defaultDurMs).coerceAtLeast(start.millis))
        val created = (base?.copy(id = nid, start = start, end = end, text = "") ?: AssEvent(id = nid, start = start, end = end))
        val at = index.coerceIn(0, events.size)
        return events.toMutableList().apply { add(at, created) }
    }

    /** 仿桌面 Aegisub `Insert at video time (after)`。 */
    fun insertAfterAtVideoTime(events: List<AssEvent>, index: Int, videoTimeMs: Long, defaultDurMs: Long = 2_000L): List<AssEvent> {
        val nid = nextId(events)
        val base = events.getOrNull(index) ?: events.lastOrNull()
        val start = SubTime.ofMillis(videoTimeMs.coerceAtLeast(0L))
        val end = SubTime.ofMillis((videoTimeMs + defaultDurMs).coerceAtLeast(start.millis))
        val created = (base?.copy(id = nid, start = start, end = end, text = "") ?: AssEvent(id = nid, start = start, end = end))
        val at = (index + 1).coerceIn(0, events.size)
        return events.toMutableList().apply { add(at, created) }
    }

    fun joinConcatenate(events: List<AssEvent>, indices: List<Int>): List<AssEvent> =
        joinImpl(events, indices, concatenateText = true)

    fun joinKeepFirst(events: List<AssEvent>, indices: List<Int>): List<AssEvent> =
        joinImpl(events, indices, concatenateText = false)

    /**
     * 仿桌面 Aegisub `Join (as Karaoke)`：把多行合并为一条带 `{\k<cs>}` 音节计时的卡拉OK行，
     * 相邻行若存在正向时间间隙则自动插入空 `{\k<gapCs>}` 保守对齐。
     */
    fun joinAsKaraoke(events: List<AssEvent>, indices: List<Int>): List<AssEvent> {
        val sorted = indices.filter { it in events.indices }.distinct().sorted()
        if (sorted.size < 2) return events
        val selected = sorted.map { events[it] }
        val minStartMs = selected.minOf { it.start.millis }
        val maxEndMs = selected.maxOf { it.end.millis }
        val karaokeText = buildString {
            var cursorMs = minStartMs
            for (ev in selected) {
                val gapCs = ((ev.start.millis - cursorMs) / 10L).coerceAtLeast(0L)
                if (gapCs > 0L) {
                    append("{\\k$gapCs}")
                    cursorMs += gapCs * 10L
                }
                val durCs = ((ev.end.millis - maxOf(cursorMs, ev.start.millis) + 5L) / 10L).coerceAtLeast(1L)
                append("{\\k$durCs}").append(ev.text)
                cursorMs += durCs * 10L
            }
        }
        val first = selected.first()
        val joined = first.copy(
            start = SubTime.ofMillis(minStartMs),
            end = SubTime.ofMillis(maxEndMs),
            text = karaokeText,
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

    /**
     * 仿桌面 Aegisub `Split Lines (by karaoke)`：将含 `{\k}`/`{\kf}`/`{\ko}` 的卡拉OK行按音节拆成多条独立时间轴行。
     */
    fun splitByKaraoke(events: List<AssEvent>, index: Int): List<AssEvent> {
        if (index !in events.indices) return events
        val target = events[index]
        val syllables = KaraokeSyllables.parse(target.text)
        if (syllables.size < 2) return events
        var nid = nextId(events)
        var cursorMs = target.start.millis
        val splitRows = mutableListOf<AssEvent>()
        for ((sIdx, syl) in syllables.withIndex()) {
            val durMs = syl.centiseconds * 10L
            val segEnd = (cursorMs + durMs).coerceAtMost(target.end.millis.coerceAtLeast(cursorMs))
            if (syl.text.isNotBlank()) {
                splitRows += target.copy(
                    id = if (splitRows.isEmpty()) target.id else nid++,
                    start = SubTime.ofMillis(cursorMs),
                    end = SubTime.ofMillis(if (sIdx == syllables.lastIndex) maxOf(segEnd, target.end.millis) else segEnd),
                    text = syl.text,
                )
            }
            cursorMs += durMs
        }
        if (splitRows.isEmpty()) return events
        return events.toMutableList().apply {
            removeAt(index)
            addAll(index, splitRows)
        }
    }

    /**
     * 仿桌面 Aegisub `Recombine Lines`：处理双人交叉重叠时间轴（A, A+B, B 拆合重组）。
     */
    fun recombineLines(events: List<AssEvent>, indices: List<Int>): List<AssEvent> {
        val sorted = indices.filter { it in events.indices }.distinct().sorted()
        if (sorted.size != 2) return events
        val a = events[sorted[0]]
        val b = events[sorted[1]]
        if (a.end.micros <= b.start.micros || b.end.micros <= a.start.micros) return events

        var nid = nextId(events)
        val out = mutableListOf<AssEvent>()
        val first = if (a.start.micros <= b.start.micros) a else b
        val second = if (first === a) b else a

        if (first.start.micros < second.start.micros) {
            out += first.copy(end = second.start)
        }
        val overlapEnd = minOf(first.end.micros, second.end.micros)
        if (second.start.micros < overlapEnd) {
            out += first.copy(
                id = if (out.isEmpty()) first.id else nid++,
                start = second.start,
                end = SubTime.ofMicros(overlapEnd),
                text = "${first.text}\\N${second.text}",
            )
        }
        if (first.end.micros > second.end.micros) {
            out += first.copy(id = nid++, start = second.end, end = first.end)
        } else if (second.end.micros > first.end.micros) {
            out += second.copy(id = second.id, start = first.end, end = second.end)
        }

        val mut = events.toMutableList()
        mut.removeAt(sorted[1])
        mut.removeAt(sorted[0])
        mut.addAll(sorted[0], out)
        return mut
    }

    /**
     * 仿桌面 Aegisub `Timing -> Make Times Continuous`：使相邻字幕行首尾无缝衔接。
     * @param changeEnd true=修改前一行结束时间对齐下一行开始；false=修改下一行开始时间对齐前一行结束。
     */
    fun makeTimesContinuous(
        events: List<AssEvent>,
        selectedIds: Set<Long> = emptySet(),
        changeEnd: Boolean = true,
    ): List<AssEvent> {
        val selectionOnly = selectedIds.isNotEmpty()
        val idxs = events.indices.filter { i ->
            !events[i].comment && (!selectionOnly || events[i].id in selectedIds)
        }
        if (idxs.size < 2) return events
        val mut = events.toMutableList()
        for (k in 0 until idxs.size - 1) {
            val curIdx = idxs[k]
            val nxtIdx = idxs[k + 1]
            val cur = mut[curIdx]
            val nxt = mut[nxtIdx]
            if (changeEnd) {
                if (nxt.start.micros > cur.start.micros) {
                    mut[curIdx] = cur.copy(end = nxt.start)
                }
            } else {
                if (cur.end.micros < nxt.end.micros) {
                    mut[nxtIdx] = nxt.copy(start = cur.end)
                }
            }
        }
        return mut
    }

    /** 仿桌面 Aegisub `Swap Lines`：交换选中的两行顺序。 */
    fun swapLines(events: List<AssEvent>, indexA: Int, indexB: Int): List<AssEvent> {
        if (indexA !in events.indices || indexB !in events.indices || indexA == indexB) return events
        return events.toMutableList().apply {
            val tmp = this[indexA]
            this[indexA] = this[indexB]
            this[indexB] = tmp
        }
    }

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

    /** 仿桌面 Aegisub `Split at video time`：在指定视频时间点与字符位置切分当前行。 */
    fun splitAtVideoTime(events: List<AssEvent>, index: Int, videoTimeMs: Long, atCharPosition: Int? = null): List<AssEvent> {
        if (index !in events.indices) return events
        val e = events[index]
        val clampedMs = videoTimeMs.coerceIn(e.start.millis, e.end.millis)
        val len = e.text.length
        val ratio = if (e.durationMs > 0) (clampedMs - e.start.millis).toDouble() / e.durationMs else 0.5
        val charPos = (atCharPosition ?: (len * ratio).toInt()).coerceIn(0, len)
        val splitTime = SubTime.ofMillis(clampedMs)
        val first = e.copy(text = e.text.substring(0, charPos), end = splitTime)
        val second = e.copy(id = nextId(events), text = e.text.substring(charPos), start = splitTime)
        return events.toMutableList().apply {
            removeAt(index)
            addAll(index, listOf(first, second))
        }
    }

    fun splitAtMidpoint(events: List<AssEvent>, index: Int): List<AssEvent> {
        val text = events.getOrNull(index)?.text ?: return events
        return splitAt(events, index, text.length / 2)
    }

    fun moveUp(events: List<AssEvent>, index: Int): List<AssEvent> {
        if (index !in 1..events.lastIndex) return events
        return events.toMutableList().apply {
            val prev = this[index - 1]
            this[index - 1] = this[index]
            this[index] = prev
        }
    }

    fun moveDown(events: List<AssEvent>, index: Int): List<AssEvent> {
        if (index !in 0 until events.lastIndex) return events
        return events.toMutableList().apply {
            val next = this[index + 1]
            this[index + 1] = this[index]
            this[index] = next
        }
    }

    fun applyTo(script: AssScript, op: (List<AssEvent>) -> List<AssEvent>): AssScript =
        script.withEvents(op(script.events))
}
