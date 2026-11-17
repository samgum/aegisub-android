package io.github.samgum.aegisub.domain.sgmy

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * 时间缝合器与同样式重叠检查器（完整复刻 samgum/Aegisub SGMY `DialogStitchTimings` 与 `BuildOverlapReport`）。
 *
 * @author 伤感咩吖
 */
object StitchTimings {

    enum class Anchor {
        /** 以前一行结束为锚点：把下一行开始吸附到前一行结束。 */
        PREVIOUS_LINE_END,
        /** 以下一行开始为锚点：把前一行结束延伸到下一行开始。 */
        NEXT_LINE_START,
    }

    enum class UnitMode { TIME_MS, FRAMES }

    data class Config(
        val anchor: Anchor = Anchor.PREVIOUS_LINE_END,
        val unitMode: UnitMode = UnitMode.TIME_MS,
        val maxGapMs: Long = 250L,
        val fps: Double = 0.0,
        val selectedIds: Set<Long> = emptySet(),
    )

    data class Result(
        val events: List<AssEvent>,
        val changedCount: Int,
    )

    fun apply(events: List<AssEvent>, config: Config): Result {
        if (config.maxGapMs < 0L) return Result(events, 0)
        val selectionOnly = config.selectedIds.isNotEmpty()
        val targetIndices = events.indices.filter { idx ->
            val e = events[idx]
            !e.comment && (!selectionOnly || e.id in config.selectedIds)
        }
        if (targetIndices.size < 2) return Result(events, 0)

        val mutable = events.toMutableList()
        val useFrames = config.unitMode == UnitMode.FRAMES && config.fps > 0.0
        var changed = 0

        for (k in 1 until targetIndices.size) {
            val prevIdx = targetIndices[k - 1]
            val nextIdx = targetIndices[k]
            val prev = mutable[prevIdx]
            val next = mutable[nextIdx]
            val gap = next.start.millis - prev.end.millis
            if (gap <= 0L || gap > config.maxGapMs) continue

            if (useFrames) {
                val frameMs = 1_000.0 / config.fps
                val prevEndFrame = floor(prev.end.millis / frameMs).toLong() - 1L
                val nextStartFrame = floor(next.start.millis / frameMs).toLong()
                if (nextStartFrame <= prevEndFrame + 1L) continue
                if (config.anchor == Anchor.PREVIOUS_LINE_END) {
                    val snappedStart = ((prevEndFrame + 1L) * frameMs).roundToLong().coerceAtLeast(0L)
                    mutable[nextIdx] = next.copy(start = SubTime.ofMillis(snappedStart))
                } else {
                    val snappedEnd = (nextStartFrame * frameMs).roundToLong().coerceAtLeast(prev.start.millis)
                    mutable[prevIdx] = prev.copy(end = SubTime.ofMillis(snappedEnd))
                }
            } else {
                if (config.anchor == Anchor.PREVIOUS_LINE_END) {
                    mutable[nextIdx] = next.copy(start = prev.end)
                } else {
                    mutable[prevIdx] = prev.copy(end = next.start)
                }
            }
            changed++
        }

        return Result(mutable.toList(), changed)
    }
}

/**
 * 同样式时间重叠检查器（复刻 samgum/Aegisub SGMY `ShowStyleOverlapCheckDialog`）。
 */
object StyleOverlapChecker {

    data class OverlapPair(
        val firstRowNumber: Int,
        val secondRowNumber: Int,
        val firstEventId: Long,
        val secondEventId: Long,
    )

    data class StyleOverlapStats(
        val styleName: String,
        val totalLines: Int,
        val exactSameTimePairs: Int,
        val exactLinesCount: Int,
        val sameStartPairs: Int,
        val sameStartLinesCount: Int,
        val sameEndPairs: Int,
        val sameEndLinesCount: Int,
        val crossingOverlapPairs: Int,
        val crossingLinesCount: Int,
        val crossingExamples: List<OverlapPair>,
    ) {
        val hasIssues: Boolean
            get() = exactSameTimePairs > 0 || sameStartPairs > 0 || sameEndPairs > 0 || crossingOverlapPairs > 0
    }

    data class Report(
        val styles: List<StyleOverlapStats>,
    ) {
        val hasAnyIssue: Boolean get() = styles.any { it.hasIssues }
    }

    fun analyze(events: List<AssEvent>): Report {
        val indexedNonComment = events.mapIndexedNotNull { idx, e ->
            if (e.comment) null else (idx + 1) to e
        }
        val grouped = indexedNonComment.groupBy { it.second.style }.toSortedMap()
        val styleReports = grouped.map { (styleName, entries) ->
            val sorted = entries.sortedWith(
                compareBy<Pair<Int, AssEvent>> { it.second.start.micros }
                    .thenBy { it.second.end.micros }
                    .thenBy { it.first },
            )
            var exactPairs = 0
            var sameStart = 0
            var sameEnd = 0
            var crossing = 0
            val exactLines = mutableSetOf<Int>()
            val sameStartLines = mutableSetOf<Int>()
            val sameEndLines = mutableSetOf<Int>()
            val crossingLines = mutableSetOf<Int>()
            val examples = mutableListOf<OverlapPair>()

            for (i in sorted.indices) {
                val (rowA, a) = sorted[i]
                for (j in i + 1 until sorted.size) {
                    val (rowB, b) = sorted[j]
                    val isExact = a.start.micros == b.start.micros && a.end.micros == b.end.micros
                    val isSameStart = a.start.micros == b.start.micros
                    val isSameEnd = a.end.micros == b.end.micros
                    val isCrossing = a.start.micros < b.end.micros && b.start.micros < a.end.micros

                    if (isExact) {
                        exactPairs++
                        exactLines += rowA; exactLines += rowB
                    }
                    if (isSameStart) {
                        sameStart++
                        sameStartLines += rowA; sameStartLines += rowB
                    }
                    if (isSameEnd) {
                        sameEnd++
                        sameEndLines += rowA; sameEndLines += rowB
                    }
                    if (isCrossing) {
                        crossing++
                        crossingLines += rowA; crossingLines += rowB
                        if (examples.size < 12) {
                            examples += OverlapPair(rowA, rowB, a.id, b.id)
                        }
                    }
                }
            }

            StyleOverlapStats(
                styleName = styleName,
                totalLines = sorted.size,
                exactSameTimePairs = exactPairs,
                exactLinesCount = exactLines.size,
                sameStartPairs = sameStart,
                sameStartLinesCount = sameStartLines.size,
                sameEndPairs = sameEnd,
                sameEndLinesCount = sameEndLines.size,
                crossingOverlapPairs = crossing,
                crossingLinesCount = crossingLines.size,
                crossingExamples = examples,
            )
        }
        return Report(styleReports)
    }
}
