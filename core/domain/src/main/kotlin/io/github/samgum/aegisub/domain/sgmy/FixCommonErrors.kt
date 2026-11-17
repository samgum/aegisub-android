package io.github.samgum.aegisub.domain.sgmy

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.DialogueBlock
import io.github.samgum.aegisub.domain.time.SubTime

/**
 * 修复常见字幕错误（完整复刻 samgum/Aegisub SGMY `DialogFixCommonErrors`）：
 * 1. 修复过短时长（延长至 `minDurationMs`）
 * 2. 修复过长时长（截断至 `maxDurationMs`）
 * 3. 修复重叠显示时间（按起始时间稳定排序并忽略注释行，将前一行结束截断到下一行开始）
 * 4. 修复行间过短间隔（间隔 `< minGapMs` 时回缩前一行结束）
 * 5. 去除行尾空白（含 ASCII 空白与全角空格 `U+3000`）
 * 6. 删除空行或仅含 ASS 不可见空白（`\h`、`\N`、`\n`、空格、`U+3000`）的行（保留 `template*` 与 `\p1` 绘图行）
 *
 * 返回分阶段快照以便调用方按桌面 SGMY 语义分别提交「时间」「文本」「增删行」独立撤销点。
 *
 * @author 伤感咩吖
 */
object FixCommonErrors {

    data class Config(
        val fixOverlaps: Boolean = true,
        val fixShortGaps: Boolean = true,
        val fixShortDurations: Boolean = true,
        val fixLongDurations: Boolean = false,
        val removeEmptyLines: Boolean = false,
        val stripTrailingWhitespace: Boolean = false,
        val minGapMs: Long = 100L,
        val minDurationMs: Long = 1_000L,
        val maxDurationMs: Long = 7_000L,
        val selectedIds: Set<Long> = emptySet(),
    )

    data class Result(
        val afterTimingEvents: List<AssEvent>,
        val timingChanged: Boolean,
        val afterTextEvents: List<AssEvent>,
        val textChanged: Boolean,
        val finalEvents: List<AssEvent>,
        val rowsRemoved: Boolean,
        val overlapsTrimmed: Int,
        val shortGapsFixed: Int,
        val shortDurationsExtended: Int,
        val longDurationsTrimmed: Int,
        val trailingSpacesStripped: Int,
        val emptyLinesRemoved: Int,
    ) {
        val totalFixed: Int
            get() = overlapsTrimmed + shortGapsFixed + shortDurationsExtended +
                longDurationsTrimmed + trailingSpacesStripped + emptyLinesRemoved
    }

    /** 判定 ASS 事件是否仅含不可见空白（复刻 `is_ass_whitespace_only`）。 */
    fun isAssWhitespaceOnly(event: AssEvent): Boolean {
        if (event.effect.trimStart().startsWith("template", ignoreCase = true)) return false
        val blocks = DialogueBlock.parse(event.text)
        var inDrawing = false
        for (block in blocks) {
            when (block) {
                is DialogueBlock.Drawing -> return false
                is DialogueBlock.Override -> {
                    for (tag in block.tags) {
                        if (tag.name == "p") {
                            val scale = tag.rawValue.trim().toIntOrNull() ?: 0
                            inDrawing = scale > 0
                        }
                    }
                }
                is DialogueBlock.Comment -> Unit
                is DialogueBlock.Plain -> {
                    if (inDrawing && block.text.isNotBlank()) return false
                    val s = block.text
                    var i = 0
                    while (i < s.length) {
                        val c = s[i]
                        if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\u3000') {
                            i++
                            continue
                        }
                        if (c == '\\' && i + 1 < s.length && (s[i + 1] == 'h' || s[i + 1] == 'N' || s[i + 1] == 'n')) {
                            i += 2
                            continue
                        }
                        return false
                    }
                }
            }
        }
        return true
    }

    fun apply(events: List<AssEvent>, config: Config): Result {
        val allRows = config.selectedIds.isEmpty()
        fun inTarget(e: AssEvent): Boolean = allRows || e.id in config.selectedIds

        var nOverlap = 0
        var nGap = 0
        var nShort = 0
        var nLong = 0
        var nTrim = 0
        var nEmpty = 0

        // 阶段 1：时间修复（先单行时长，再按起始时间稳定排序处理相邻重叠与最小间隙）
        val mutableTiming = events.toMutableList()
        for (i in mutableTiming.indices) {
            val e = mutableTiming[i]
            if (e.comment || !inTarget(e)) continue
            val startMs = e.start.millis
            val dur = e.end.millis - startMs
            if (config.fixShortDurations && dur < config.minDurationMs) {
                mutableTiming[i] = e.copy(end = SubTime.ofMillis(startMs + config.minDurationMs))
                nShort++
            } else if (config.fixLongDurations && dur > config.maxDurationMs) {
                mutableTiming[i] = e.copy(end = SubTime.ofMillis(startMs + config.maxDurationMs))
                nLong++
            }
        }

        if (config.fixOverlaps || config.fixShortGaps) {
            val nonCommentOrder = mutableTiming.indices
                .filter { !mutableTiming[it].comment }
                .sortedWith(compareBy { mutableTiming[it].start.micros })

            for (k in 0 until nonCommentOrder.size - 1) {
                val curIdx = nonCommentOrder[k]
                val nxtIdx = nonCommentOrder[k + 1]
                var cur = mutableTiming[curIdx]
                val nxt = mutableTiming[nxtIdx]
                var curEndMs = cur.end.millis
                val nxtStartMs = nxt.start.millis

                if (config.fixOverlaps && curEndMs > nxtStartMs) {
                    if (nxtStartMs > cur.start.millis && inTarget(cur)) {
                        cur = cur.copy(end = SubTime.ofMillis(nxtStartMs))
                        mutableTiming[curIdx] = cur
                        curEndMs = nxtStartMs
                        nOverlap++
                    }
                }

                val gap = nxtStartMs - curEndMs
                if (config.fixShortGaps && gap >= 0L && gap < config.minGapMs) {
                    val newEnd = nxtStartMs - config.minGapMs
                    if (newEnd > cur.start.millis && inTarget(cur)) {
                        mutableTiming[curIdx] = cur.copy(end = SubTime.ofMillis(newEnd))
                        nGap++
                    }
                }
            }
        }

        val afterTiming = mutableTiming.toList()
        val timingChanged = (nOverlap + nGap + nShort + nLong) > 0

        // 阶段 2：去除尾部空白（含全角空格 U+3000）
        val afterText = if (config.stripTrailingWhitespace) {
            afterTiming.map { e ->
                if (e.comment || !inTarget(e)) {
                    e
                } else {
                    val trimmed = e.text.trimEnd { ch ->
                        ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n' || ch == '\u3000'
                    }
                    if (trimmed.length != e.text.length) {
                        nTrim++
                        e.copy(text = trimmed)
                    } else {
                        e
                    }
                }
            }
        } else {
            afterTiming
        }
        val textChanged = nTrim > 0

        // 阶段 3：删除空白行
        val finalEvents = if (config.removeEmptyLines) {
            afterText.filter { e ->
                if (!e.comment && inTarget(e) && isAssWhitespaceOnly(e)) {
                    nEmpty++
                    false
                } else {
                    true
                }
            }
        } else {
            afterText
        }
        val rowsRemoved = nEmpty > 0

        return Result(
            afterTimingEvents = afterTiming,
            timingChanged = timingChanged,
            afterTextEvents = afterText,
            textChanged = textChanged,
            finalEvents = finalEvents,
            rowsRemoved = rowsRemoved,
            overlapsTrimmed = nOverlap,
            shortGapsFixed = nGap,
            shortDurationsExtended = nShort,
            longDurationsTrimmed = nLong,
            trailingSpacesStripped = nTrim,
            emptyLinesRemoved = nEmpty,
        )
    }
}
