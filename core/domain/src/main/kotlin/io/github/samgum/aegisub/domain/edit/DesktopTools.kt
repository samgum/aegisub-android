package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.DialogueBlock
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 桌面 Aegisub 进阶工程工具集：
 * 1. [SelectLines]：按条件批量选择行（`Subtitle -> Select Lines...`）
 * 2. [KanjiTimer]：卡拉OK汉字计时复制器（`Timing -> Kanji Timer...`）
 * 3. [KeyframesIo]：Aegisub `# keyframe format v1` 关键帧读写与时间吸附（`Snap to Keyframes`）
 * 4. [FontsCollectorAnalyzer]：脚本引用字体清单统计（`File -> Fonts Collector...`）
 *
 * @author 伤感咩吖
 */
object SelectLines {

    enum class Field { TEXT, STRIPPED_TEXT, STYLE, ACTOR, EFFECT }
    enum class MatchMode { CONTAINS, EXACT, REGEX }
    enum class CommentFilter { DIALOGUES_ONLY, COMMENTS_ONLY, BOTH }
    enum class Action { SET, ADD, SUBTRACT, INTERSECT }

    data class Criteria(
        val pattern: String = "",
        val field: Field = Field.TEXT,
        val matchMode: MatchMode = MatchMode.CONTAINS,
        val caseSensitive: Boolean = false,
        val invertMatch: Boolean = false,
        val commentFilter: CommentFilter = CommentFilter.DIALOGUES_ONLY,
        val action: Action = Action.SET,
    )

    fun evaluate(
        events: List<AssEvent>,
        currentSelectedIds: Set<Long>,
        criteria: Criteria,
    ): Set<Long> {
        val regex = if (criteria.matchMode == MatchMode.REGEX && criteria.pattern.isNotEmpty()) {
            runCatching {
                if (criteria.caseSensitive) Regex(criteria.pattern)
                else Regex(criteria.pattern, RegexOption.IGNORE_CASE)
            }.getOrNull()
        } else null

        val matched = events.asSequence().filter { e ->
            when (criteria.commentFilter) {
                CommentFilter.DIALOGUES_ONLY -> !e.comment
                CommentFilter.COMMENTS_ONLY -> e.comment
                CommentFilter.BOTH -> true
            }
        }.filter { e ->
            val hay = when (criteria.field) {
                Field.TEXT -> e.text
                Field.STRIPPED_TEXT -> e.strippedText
                Field.STYLE -> e.style
                Field.ACTOR -> e.actor
                Field.EFFECT -> e.effect
            }
            val rawHit = when (criteria.matchMode) {
                MatchMode.CONTAINS -> hay.contains(criteria.pattern, ignoreCase = !criteria.caseSensitive)
                MatchMode.EXACT -> hay.equals(criteria.pattern, ignoreCase = !criteria.caseSensitive)
                MatchMode.REGEX -> regex?.containsMatchIn(hay) ?: false
            }
            if (criteria.invertMatch) !rawHit else rawHit
        }.map { it.id }.toSet()

        return when (criteria.action) {
            Action.SET -> matched
            Action.ADD -> currentSelectedIds + matched
            Action.SUBTRACT -> currentSelectedIds - matched
            Action.INTERSECT -> currentSelectedIds intersect matched
        }
    }
}

/**
 * 汉字计时器（复刻桌面 Aegisub `DialogKanjiTimer`）：
 * 将源样式（如罗马音/原文）已打好 `{\k}`/`{\kf}` 的音节时长按比例或分组映射到目标译文/汉字行。
 */
object KanjiTimer {

    data class PairPreview(
        val sourceEventId: Long,
        val targetEventId: Long,
        val sourceText: String,
        val targetPlainText: String,
        val previewKaraokeText: String,
    )

    /** 把一条源卡拉OK行的音节切分按均匀字符分组投射到目标纯文本。 */
    fun projectKaraokeToTarget(sourceKaraokeText: String, targetPlainText: String): String {
        val syllables = KaraokeSyllables.parse(sourceKaraokeText)
        val cleanTarget = targetPlainText.trim()
        if (syllables.isEmpty() || cleanTarget.isEmpty()) return targetPlainText

        val nonEmptySyls = syllables.filter { it.centiseconds > 0 }
        if (nonEmptySyls.isEmpty()) return targetPlainText

        val chars = cleanTarget.toList()
        if (chars.size <= nonEmptySyls.size) {
            // 目标字数 <= 音节数：把多个音节合并分配给每个目标字
            return buildString {
                for (cIdx in chars.indices) {
                    val sStart = (cIdx * nonEmptySyls.size) / chars.size
                    val sEnd = (((cIdx + 1) * nonEmptySyls.size) / chars.size).coerceAtLeast(sStart + 1)
                    val sumCs = nonEmptySyls.subList(sStart, sEnd.coerceAtMost(nonEmptySyls.size)).sumOf { it.centiseconds }
                    val tag = nonEmptySyls[sStart].tag
                    append("{\\$tag$sumCs}").append(chars[cIdx])
                }
            }
        } else {
            // 目标字数 > 音节数：每个音节覆盖若干字符
            return buildString {
                for (sIdx in nonEmptySyls.indices) {
                    val cStart = (sIdx * chars.size) / nonEmptySyls.size
                    val cEnd = if (sIdx == nonEmptySyls.lastIndex) chars.size else ((sIdx + 1) * chars.size) / nonEmptySyls.size
                    val slice = if (cEnd > cStart) cleanTarget.substring(cStart, cEnd) else ""
                    val syl = nonEmptySyls[sIdx]
                    append("{\\${syl.tag}${syl.centiseconds}}").append(slice)
                }
            }
        }
    }

    /** 按源样式与目标样式自动配对（相同起始时间或按行序一一对应）并批量应用汉字计时。 */
    fun applyByStyles(
        events: List<AssEvent>,
        sourceStyle: String,
        targetStyle: String,
    ): Pair<List<AssEvent>, Int> {
        val sources = events.filter { !it.comment && it.style == sourceStyle && KaraokeSyllables.parse(it.text).isNotEmpty() }
        val targetIndices = events.indices.filter { !events[it].comment && events[it].style == targetStyle }
        if (sources.isEmpty() || targetIndices.isEmpty()) return events to 0

        val mut = events.toMutableList()
        var applied = 0
        for ((ord, tIdx) in targetIndices.withIndex()) {
            val target = mut[tIdx]
            val matchedSource = sources.firstOrNull { abs(it.start.millis - target.start.millis) <= 150L }
                ?: sources.getOrNull(ord)
                ?: continue
            val projected = projectKaraokeToTarget(matchedSource.text, target.strippedText)
            if (projected != target.text) {
                mut[tIdx] = target.copy(text = projected)
                applied++
            }
        }
        return mut.toList() to applied
    }
}

/**
 * 关键帧文件读写与吸附（复刻 Aegisub `# keyframe format v1` + XviD 关键帧格式）。
 */
object KeyframesIo {

    /** 解析关键帧文本为毫秒时间戳升序列表。若文本存的是帧号，按 [fps] 换算为毫秒。 */
    fun parseKeyframesMs(content: String, fps: Double = 23.976): List<Long> {
        val safeFps = if (fps > 0.0) fps else 23.976
        var fileFps = safeFps
        val frames = mutableListOf<Long>()
        val directMs = mutableListOf<Long>()

        for (raw in content.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("fps", ignoreCase = true)) {
                line.substring(3).trim().toDoubleOrNull()?.takeIf { it > 0.0 }?.let { fileFps = it }
                continue
            }
            if (line.endsWith("ms", ignoreCase = true)) {
                line.dropLast(2).trim().toLongOrNull()?.takeIf { it >= 0L }?.let { directMs += it }
                continue
            }
            val frameNum = line.toLongOrNull()
            if (frameNum != null && frameNum >= 0L) {
                frames += frameNum
            }
        }

        val converted = frames.map { ((it * 1_000.0) / fileFps).roundToLong() }
        return (directMs + converted).distinct().sorted()
    }

    /** 导出为标准 Aegisub `# keyframe format v1` 文本。 */
    fun writeKeyframesV1(keyframesMs: List<Long>, fps: Double = 23.976): String {
        val safeFps = if (fps > 0.0) fps else 23.976
        return buildString {
            appendLine("# keyframe format v1")
            appendLine("fps %.3f".format(java.util.Locale.US, safeFps))
            keyframesMs.sorted().map { ((it * safeFps) / 1_000.0).roundToLong() }.distinct().forEach { f ->
                appendLine(f)
            }
        }
    }

    /** 若 [timeMs] 距离最近关键帧不超过 [thresholdMs]，则吸附到该关键帧；否则原样返回。 */
    fun snapToNearest(timeMs: Long, keyframesMs: List<Long>, thresholdMs: Long = 180L): Long {
        if (keyframesMs.isEmpty() || thresholdMs <= 0L) return timeMs
        val idx = keyframesMs.binarySearch(timeMs)
        if (idx >= 0) return timeMs
        val ins = -idx - 1
        var best = timeMs
        var bestDist = thresholdMs + 1L
        if (ins < keyframesMs.size) {
            val d = abs(keyframesMs[ins] - timeMs)
            if (d <= thresholdMs && d < bestDist) { best = keyframesMs[ins]; bestDist = d }
        }
        if (ins > 0) {
            val d = abs(keyframesMs[ins - 1] - timeMs)
            if (d <= thresholdMs && d < bestDist) { best = keyframesMs[ins - 1] }
        }
        return best
    }
}

/**
 * 字体检查与收集分析器（复刻桌面 Aegisub `Fonts Collector` 核心诊断逻辑）：
 * 扫描全部样式与行内 `\fn` 标签，输出每个字体的使用次数、涉及样式与示例行号。
 */
object FontsCollectorAnalyzer {

    data class FontUsage(
        val fontName: String,
        val usedInStyles: List<String>,
        val inlineOverrideCount: Int,
        val activeDialogueCount: Int,
        val sampleRows: List<Int>,
    )

    fun analyze(script: AssScript): List<FontUsage> {
        val styleMap = script.styles.associateBy { it.name }
        val stylesPerFont = mutableMapOf<String, MutableSet<String>>()
        val inlineCount = mutableMapOf<String, Int>()
        val dialogueCount = mutableMapOf<String, Int>()
        val samples = mutableMapOf<String, MutableList<Int>>()

        for (st in script.styles) {
            val fn = st.font.trim().ifEmpty { "Arial" }
            stylesPerFont.getOrPut(fn) { linkedSetOf() } += st.name
        }

        script.events.forEachIndexed { idx, ev ->
            if (ev.comment) return@forEachIndexed
            val baseFont = (styleMap[ev.style]?.font ?: "Arial").trim().ifEmpty { "Arial" }
            val usedHere = linkedSetOf(baseFont)
            for (block in DialogueBlock.parse(ev.text)) {
                if (block is DialogueBlock.Override) {
                    for (tag in block.tags) {
                        if (tag.name == "fn" && tag.rawValue.isNotBlank()) {
                            val fn = tag.rawValue.trim().removePrefix("@")
                            usedHere += fn
                            inlineCount[fn] = (inlineCount[fn] ?: 0) + 1
                        }
                    }
                }
            }
            for (fn in usedHere) {
                dialogueCount[fn] = (dialogueCount[fn] ?: 0) + 1
                val list = samples.getOrPut(fn) { mutableListOf() }
                if (list.size < 6) list += (idx + 1)
            }
        }

        val allFonts = (stylesPerFont.keys + inlineCount.keys + dialogueCount.keys).distinct().sorted()
        return allFonts.map { fn ->
            FontUsage(
                fontName = fn,
                usedInStyles = stylesPerFont[fn]?.toList().orEmpty(),
                inlineOverrideCount = inlineCount[fn] ?: 0,
                activeDialogueCount = dialogueCount[fn] ?: 0,
                sampleRows = samples[fn].orEmpty(),
            )
        }
    }
}
