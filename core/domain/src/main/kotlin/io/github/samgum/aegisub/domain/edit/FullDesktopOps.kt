package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.automation.AutomationEngine
import io.github.samgum.aegisub.domain.format.SubtitleFormat
import io.github.samgum.aegisub.domain.format.WriteOptions
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.DialogueBlock
import io.github.samgum.aegisub.domain.model.Margins
import io.github.samgum.aegisub.domain.text.FindReplace
import io.github.samgum.aegisub.domain.time.SubTime
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 桌面 Aegisub 完整工程操作补完模块（1:1 复刻上游 Aegisub 核心对话框逻辑）：
 * 1. [PasteOverFields]：`DialogPasteOver` 11 字段独立勾选粘贴覆盖（支持完整 ASS Dialogue 行或纯文本）
 * 2. [FullTimingProcessor]：`DialogTimingProcessor` 完整四合一时间后处理器（样式过滤 + 防碰撞前后导 + 相邻连轴偏置 + 四阈值关键帧吸附）
 * 3. [SortSelectedLines]：`Sort Selected Lines` 原位仅排序选中行（未选中行保持原行位不动）
 * 4. [TextImportProcessor]：`DialogTextImport` 纯文本台词说话人分隔符（如 `Alice: 台词`）与注释前缀解析
 * 5. [TimecodesIo]：Aegisub VFR `# timecode format v1 / v2` 时间码文件读写与帧号↔毫秒精确互转
 * 6. [FindReplaceStepper]：`DialogSearchReplace` 逐条查找下一个、单步替换、字段选择与跳过 `{...}` 标签选项
 * 7. [ExportPipeline]：`DialogExport` 导出滤镜流水线（卡拉OK模板展开 / 清理标签 / 帧率变换 / 去标签）与多字符集编码输出
 *
 * @author 伤感咩吖
 */
object PasteOverFields {

    enum class Field(val labelZh: String) {
        COMMENT("注释状态 (Comment)"),
        LAYER("图层 (Layer)"),
        START_TIME("开始时间 (Start)"),
        END_TIME("结束时间 (End)"),
        STYLE("样式 (Style)"),
        ACTOR("说话人 (Actor)"),
        MARGIN_L("左边距 (MarginL)"),
        MARGIN_R("右边距 (MarginR)"),
        MARGIN_V("纵边距 (MarginV)"),
        EFFECT("特效备注 (Effect)"),
        TEXT("字幕文本 (Text)"),
    }

    val PRESET_ALL: Set<Field> = Field.entries.toSet()
    val PRESET_TEXT_ONLY: Set<Field> = setOf(Field.TEXT)
    val PRESET_TIMES_ONLY: Set<Field> = setOf(Field.START_TIME, Field.END_TIME)
    val PRESET_STYLE_MARGINS: Set<Field> = setOf(Field.STYLE, Field.ACTOR, Field.MARGIN_L, Field.MARGIN_R, Field.MARGIN_V)

    fun apply(
        events: List<AssEvent>,
        orderedTargetIds: List<Long>,
        clipboardLines: List<String>,
        selectedFields: Set<Field>,
    ): List<AssEvent> {
        if (orderedTargetIds.isEmpty() || clipboardLines.isEmpty() || selectedFields.isEmpty()) return events
        val parsedDonors = clipboardLines.map { raw ->
            val trimmed = raw.trim()
            runCatching { AssEvent.parse(trimmed) }.getOrElse { AssEvent(text = raw) }
        }
        val donorMap: Map<Long, AssEvent> = orderedTargetIds.zip(parsedDonors).toMap()

        return events.map { target ->
            val donor = donorMap[target.id] ?: return@map target
            target.copy(
                comment = if (Field.COMMENT in selectedFields) donor.comment else target.comment,
                layer = if (Field.LAYER in selectedFields) donor.layer else target.layer,
                start = if (Field.START_TIME in selectedFields) donor.start else target.start,
                end = if (Field.END_TIME in selectedFields) donor.end else target.end,
                style = if (Field.STYLE in selectedFields) donor.style else target.style,
                actor = if (Field.ACTOR in selectedFields) donor.actor else target.actor,
                margins = Margins(
                    left = if (Field.MARGIN_L in selectedFields) donor.margins.left else target.margins.left,
                    right = if (Field.MARGIN_R in selectedFields) donor.margins.right else target.margins.right,
                    vertical = if (Field.MARGIN_V in selectedFields) donor.margins.vertical else target.margins.vertical,
                ),
                effect = if (Field.EFFECT in selectedFields) donor.effect else target.effect,
                text = if (Field.TEXT in selectedFields) donor.text else target.text,
            )
        }
    }
}

object FullTimingProcessor {

    data class Config(
        val allowedStyles: Set<String> = emptySet(), // empty = 全部样式
        val selectedIds: Set<Long> = emptySet(),
        val enableLeadIn: Boolean = true,
        val leadInMs: Long = 80L,
        val enableLeadOut: Boolean = true,
        val leadOutMs: Long = 120L,
        val enableMakeAdjacent: Boolean = true,
        val maxGapMs: Long = 300L,
        val maxOverlapMs: Long = 150L,
        /** 0.0 = 完全移动后句起点，1.0 = 完全伸缩前句终点（对齐桌面 `adjacentBias` 滑杆）。 */
        val adjacentBias: Double = 0.8,
        val enableKeyframeSnap: Boolean = false,
        val keyframesMs: List<Long> = emptyList(),
        val startsBeforeKfMs: Long = 200L,
        val startsAfterKfMs: Long = 200L,
        val endsBeforeKfMs: Long = 200L,
        val endsAfterKfMs: Long = 250L,
    )

    private fun collides(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean =
        aStart < bEnd && bStart < aEnd

    fun process(events: List<AssEvent>, config: Config): List<AssEvent> {
        val targetIndices = events.indices.filter { idx ->
            val e = events[idx]
            !e.comment &&
                e.start.millis <= e.end.millis &&
                (config.selectedIds.isEmpty() || e.id in config.selectedIds) &&
                (config.allowedStyles.isEmpty() || e.style in config.allowedStyles)
        }
        if (targetIndices.isEmpty()) return events

        val starts = LongArray(events.size) { events[it].start.millis }
        val ends = LongArray(events.size) { events[it].end.millis }
        val sortedIdx = targetIndices.sortedBy { starts[it] }

        // 1. 防碰撞前导 Lead-in（绝不与原本不重叠的前序行产生新碰撞，1:1 复刻 safe_time）
        if (config.enableLeadIn && config.leadInMs > 0L) {
            for (k in sortedIdx.indices) {
                val cur = sortedIdx[k]
                var candidate = (starts[cur] - config.leadInMs).coerceAtLeast(0L)
                for (p in (k - 1) downTo 0) {
                    val prev = sortedIdx[p]
                    if (!collides(starts[cur], ends[cur], starts[prev], ends[prev])) {
                        candidate = maxOf(candidate, ends[prev])
                    }
                }
                starts[cur] = candidate
            }
        }

        // 2. 防碰撞后导 Lead-out
        if (config.enableLeadOut && config.leadOutMs > 0L) {
            for (k in sortedIdx.indices) {
                val cur = sortedIdx[k]
                var candidate = ends[cur] + config.leadOutMs
                for (n in (k + 1) until sortedIdx.size) {
                    val nxt = sortedIdx[n]
                    if (!collides(starts[cur], ends[cur], starts[nxt], ends[nxt])) {
                        candidate = minOf(candidate, starts[nxt])
                    }
                }
                ends[cur] = maxOf(starts[cur], candidate)
            }
        }

        // 3. 相邻字幕连轴与去微重叠（按 bias 比例分配边界点）
        if (config.enableMakeAdjacent && sortedIdx.size >= 2) {
            val bias = config.adjacentBias.coerceIn(0.0, 1.0)
            for (k in 1 until sortedIdx.size) {
                val prev = sortedIdx[k - 1]
                val cur = sortedIdx[k]
                val dist = starts[cur] - ends[prev]
                if ((dist < 0L && -dist <= config.maxOverlapMs) || (dist > 0L && dist <= config.maxGapMs)) {
                    val boundary = (ends[prev] + (dist * bias).roundToLong()).coerceAtLeast(0L)
                    ends[prev] = maxOf(starts[prev], boundary)
                    starts[cur] = minOf(ends[cur], boundary)
                }
            }
        }

        // 4. 四阈值关键帧吸附
        if (config.enableKeyframeSnap && config.keyframesMs.isNotEmpty()) {
            val sortedKfs = config.keyframesMs.sorted()
            for (cur in sortedIdx) {
                nearestKeyframe(starts[cur], sortedKfs)?.let { kf ->
                    val beforeDist = kf - starts[cur] // 字幕在关键帧之前开始
                    val afterDist = starts[cur] - kf  // 字幕在关键帧之后开始
                    if ((beforeDist >= 0L && beforeDist <= config.startsBeforeKfMs) ||
                        (afterDist > 0L && afterDist <= config.startsAfterKfMs)
                    ) {
                        starts[cur] = kf.coerceAtMost(ends[cur])
                    }
                }
                nearestKeyframe(ends[cur], sortedKfs)?.let { kf ->
                    val beforeDist = kf - ends[cur]
                    val afterDist = ends[cur] - kf
                    if ((beforeDist >= 0L && beforeDist <= config.endsBeforeKfMs) ||
                        (afterDist > 0L && afterDist <= config.endsAfterKfMs)
                    ) {
                        ends[cur] = kf.coerceAtLeast(starts[cur])
                    }
                }
            }
        }

        return events.mapIndexed { idx, ev ->
            if (starts[idx] == ev.start.millis && ends[idx] == ev.end.millis) ev
            else ev.copy(start = SubTime.ofMillis(starts[idx]), end = SubTime.ofMillis(ends[idx]))
        }
    }

    private fun nearestKeyframe(timeMs: Long, sortedKfs: List<Long>): Long? {
        if (sortedKfs.isEmpty()) return null
        val pos = sortedKfs.binarySearch(timeMs)
        if (pos >= 0) return sortedKfs[pos]
        val ins = -pos - 1
        val candA = sortedKfs.getOrNull(ins)
        val candB = sortedKfs.getOrNull(ins - 1)
        return when {
            candA == null -> candB
            candB == null -> candA
            abs(candA - timeMs) <= abs(candB - timeMs) -> candA
            else -> candB
        }
    }
}

object SortSelectedLines {
    /** 仅对 [selectedIds] 命中的槽位做稳定排序，未选中的行保留在原本下标位置不动（复刻 `grid/sort/.../selected`）。 */
    fun apply(events: List<AssEvent>, selectedIds: Set<Long>, key: SortKey, order: SortOrder = SortOrder.ASCENDING): List<AssEvent> {
        if (selectedIds.size < 2) return SortLines.apply(events, key, order)
        val positions = events.indices.filter { events[it].id in selectedIds }
        if (positions.size < 2) return events
        val sortedSubset = SortLines.apply(positions.map { events[it] }, key, order)
        val mut = events.toMutableList()
        positions.forEachIndexed { i, slotIdx -> mut[slotIdx] = sortedSubset[i] }
        return mut.toList()
    }
}

object TextImportProcessor {
    data class Options(
        val actorSeparator: String = ":",
        val commentStarter: String = "#",
        val includeBlankLines: Boolean = false,
    )

    fun parsePlainScript(rawText: String, options: Options = Options()): List<AssEvent> {
        val out = mutableListOf<AssEvent>()
        var nextId = 0L
        for (raw in rawText.replace("\r\n", "\n").lineSequence()) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() && !options.includeBlankLines) continue
            val isComment = options.commentStarter.isNotEmpty() && trimmed.startsWith(options.commentStarter)
            val bodyAfterComment = if (isComment) trimmed.removePrefix(options.commentStarter).trimStart() else trimmed

            var actor = ""
            var dialogueText = bodyAfterComment
            if (!isComment && options.actorSeparator.isNotEmpty()) {
                val sepIdx = bodyAfterComment.indexOf(options.actorSeparator)
                if (sepIdx in 1..24) {
                    actor = bodyAfterComment.substring(0, sepIdx).trim()
                    dialogueText = bodyAfterComment.substring(sepIdx + options.actorSeparator.length).trimStart()
                }
            }
            out += AssEvent(
                id = nextId,
                row = nextId.toInt(),
                comment = isComment,
                actor = actor,
                text = dialogueText,
            )
            nextId++
        }
        return out
    }
}

object TimecodesIo {
    /** 解析 `# timecode format v1` 或 `# timecode format v2` 返回逐帧毫秒时间表（若为 v1 则生成前 maxFrames 帧）。 */
    fun parseTimecodesMs(content: String, maxFrames: Int = 200_000): List<Long> {
        val lines = content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().lowercase()
        if (header.startsWith("# timecode format v2")) {
            return lines.drop(1)
                .filter { !it.startsWith("#") }
                .take(maxFrames)
                .mapNotNull { it.toDoubleOrNull()?.roundToLong() }
        }
        if (header.startsWith("# timecode format v1")) {
            var assumeFps = 23.976
            data class OverrideRange(val startF: Int, val endF: Int, val fps: Double)
            val overrides = mutableListOf<OverrideRange>()
            for (line in lines.drop(1)) {
                if (line.startsWith("#")) continue
                if (line.startsWith("assume", ignoreCase = true)) {
                    line.substring(6).trim().toDoubleOrNull()?.takeIf { it > 0.0 }?.let { assumeFps = it }
                } else {
                    val p = line.split(',')
                    if (p.size == 3) {
                        val s = p[0].trim().toIntOrNull()
                        val e = p[1].trim().toIntOrNull()
                        val f = p[2].trim().toDoubleOrNull()
                        if (s != null && e != null && f != null && f > 0.0) {
                            overrides += OverrideRange(s, e, f)
                        }
                    }
                }
            }
            val totalFrames = ((overrides.maxOfOrNull { it.endF } ?: 3600) + 240).coerceAtMost(maxFrames)
            val out = ArrayList<Long>(totalFrames)
            var curMs = 0.0
            for (f in 0 until totalFrames) {
                out += curMs.roundToLong()
                val fpsHere = overrides.firstOrNull { f in it.startF..it.endF }?.fps ?: assumeFps
                curMs += 1_000.0 / fpsHere
            }
            return out
        }
        return emptyList()
    }

    fun writeTimecodesV2(frameTimesMs: List<Long>): String = buildString {
        appendLine("# timecode format v2")
        frameTimesMs.forEach { appendLine(it) }
    }
}

object FindReplaceStepper {
    enum class TargetField(val labelZh: String) {
        TEXT("字幕文本"),
        STRIPPED_TEXT("纯文本（跳过 {...} 标签）"),
        STYLE("样式名"),
        ACTOR("说话人"),
        EFFECT("特效备注"),
    }

    fun findNextEventId(
        events: List<AssEvent>,
        afterEventId: Long?,
        query: String,
        field: TargetField,
        useRegex: Boolean,
        ignoreCase: Boolean,
    ): Long? {
        if (query.isEmpty() || events.isEmpty()) return null
        val startIdx = events.indexOfFirst { it.id == afterEventId }.let { if (it < 0) 0 else (it + 1) % events.size }
        for (offset in events.indices) {
            val ev = events[(startIdx + offset) % events.size]
            val hay = extractField(ev, field)
            if (FindReplace.count(hay, query, useRegex, ignoreCase) > 0) return ev.id
        }
        return null
    }

    fun replaceInEvent(
        ev: AssEvent,
        query: String,
        replacement: String,
        field: TargetField,
        useRegex: Boolean,
        ignoreCase: Boolean,
    ): AssEvent {
        if (query.isEmpty()) return ev
        return when (field) {
            TargetField.TEXT -> ev.copy(text = FindReplace.replaceAll(ev.text, query, replacement, useRegex, ignoreCase))
            TargetField.STRIPPED_TEXT -> {
                val blocks = DialogueBlock.parse(ev.text).map { blk ->
                    if (blk is DialogueBlock.Plain) {
                        DialogueBlock.Plain(FindReplace.replaceAll(blk.text, query, replacement, useRegex, ignoreCase))
                    } else blk
                }
                ev.copy(text = DialogueBlock.toText(blocks))
            }
            TargetField.STYLE -> ev.copy(style = FindReplace.replaceAll(ev.style, query, replacement, useRegex, ignoreCase))
            TargetField.ACTOR -> ev.copy(actor = FindReplace.replaceAll(ev.actor, query, replacement, useRegex, ignoreCase))
            TargetField.EFFECT -> ev.copy(effect = FindReplace.replaceAll(ev.effect, query, replacement, useRegex, ignoreCase))
        }
    }

    private fun extractField(ev: AssEvent, field: TargetField): String = when (field) {
        TargetField.TEXT -> ev.text
        TargetField.STRIPPED_TEXT -> ev.strippedText
        TargetField.STYLE -> ev.style
        TargetField.ACTOR -> ev.actor
        TargetField.EFFECT -> ev.effect
    }
}

object ExportPipeline {
    enum class ExportCharset(val label: String, val charsetName: String, val withUtf8Bom: Boolean = false) {
        UTF8("UTF-8 (推荐)", "UTF-8"),
        UTF8_BOM("UTF-8 (带 BOM)", "UTF-8", withUtf8Bom = true),
        UTF16LE("UTF-16LE (Windows 兼容)", "UTF-16LE"),
        GB18030("GB18030 / GBK (简体中文旧版)", "GB18030"),
        BIG5("Big5 (繁体中文旧版)", "Big5"),
        SHIFT_JIS("Shift_JIS (日文旧版)", "Shift_JIS"),
    }

    data class FilterOptions(
        val applyKaraokeTemplates: Boolean = false,
        val cleanTags: Boolean = false,
        val stripAllTags: Boolean = false,
        val transformFramerate: Boolean = false,
        val fromFps: Double = 23.976,
        val toFps: Double = 25.0,
    )

    fun runExport(
        script: AssScript,
        format: SubtitleFormat,
        writeOptions: WriteOptions,
        filters: FilterOptions = FilterOptions(),
        exportCharset: ExportCharset = ExportCharset.UTF8,
    ): ByteArray {
        var working = script
        if (filters.applyKaraokeTemplates) {
            working = AutomationEngine.applyKaraokeTemplater(working).script
        }
        if (filters.cleanTags) {
            working = working.withEvents(AutomationEngine.cleanTags(working.events).first)
        }
        if (filters.stripAllTags) {
            working = working.withEvents(AutomationEngine.stripTags(working.events, emptySet()).first)
        }
        if (filters.transformFramerate && filters.fromFps > 0.0 && filters.toFps > 0.0) {
            working = working.withEvents(FramerateConverter.rescale(working.events, filters.fromFps, filters.toFps))
        }

        val text = format.write(working, writeOptions)
        val cs = runCatching { java.nio.charset.Charset.forName(exportCharset.charsetName) }
            .getOrDefault(Charsets.UTF_8)
        val bodyBytes = text.toByteArray(cs)
        return if (exportCharset.withUtf8Bom) {
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bodyBytes
        } else if (exportCharset == ExportCharset.UTF16LE) {
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + bodyBytes
        } else {
            bodyBytes
        }
    }
}
