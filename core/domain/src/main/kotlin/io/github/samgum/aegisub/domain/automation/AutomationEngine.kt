package io.github.samgum.aegisub.domain.automation

import io.github.samgum.aegisub.domain.edit.KaraokeSyllables
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.DialogueBlock
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

/**
 * 桌面 Aegisub `Automation 4` 完整内置自动化引擎（1:1 复刻 `automation/autoload/` 全部 7 大官方脚本）：
 * 1. [applyKaraokeTemplater]：`kara-templater.lua` 卡拉OK模板展开器（`template line / pre-line / syl / char` + `$变量` + `!数学表达式!` + `repeat N`）
 * 2. [cleanTags]：`cleantags.lua` 合并相邻标签块、前置整行属性（`\an/\org/\pos/\move/\fad`）、移除空 `{}`
 * 3. [stripTags]：`strip-tags.lua` 批量剥离覆盖标签
 * 4. [addEdgeBlur]：`macro-1-edgeblur.lua` 为缺少模糊标签的行添加 `{\be1}`
 * 5. [makeFullwidth]：`macro-2-mkfullwitdh.lua` 将标签外的半角英文数字标点转为全角字符
 * 6. [autoKaraokeLeadIn]：`karaoke-auto-leadin.lua` 相邻选中行自动前导衔接并前置 `{\k<cs>}`
 * 7. [selectOverlaps]：`select-overlaps.moon` 扫描并返回在其它对白尚未结束时提前开始的所有重叠行 ID
 *
 * @author 伤感咩吖
 */
object AutomationEngine {

    enum class BuiltinMacro(val titleZh: String, val subtitleZh: String) {
        KARAOKE_TEMPLATER("应用卡拉OK模板 (Karaoke Templater)", "解析 Comment: template line / syl / char 并生成 fx 特效行"),
        CLEAN_TAGS("清理整理 ASS 标签 (Clean Tags)", "合并相邻标签块、前置整行定位/对齐标签、清除空括号 {}"),
        ADD_EDGE_BLUR("批量添加边缘柔化 (\\be1)", "为目标行自动前置 {\\be1} 边缘柔化标签"),
        MAKE_FULLWIDTH("半角字符转全角 (Make Fullwidth)", "跳过 {...} 与 \\N，将半角 ASCII 转为全角字符"),
        STRIP_TAGS("剥离全部覆盖标签 (Strip Tags)", "清除目标行中的所有 {...} 覆盖标签，仅保留纯文本"),
        KARAOKE_AUTO_LEADIN("卡拉OK自动前导衔接", "将后句起点吸附到前句终点并自动补入 {\\k} 间隔时长"),
        SELECT_OVERLAPS("选中所有时间重叠行 (Select Overlaps)", "自动高亮选中所有与前序台词存在时间交叉的行"),
    }

    // =========================================================================
    // 1. Clean Tags (1:1 移植自 automation/include/cleantags.lua)
    // =========================================================================

    private val kTagRe = Regex("""\\[kK][fo]?[\d.]+""")
    private val anTagRe = Regex("""\\an?\d+""")
    private val orgTagRe = Regex("""\\org\([^,)]*,[^,)]*\)""")
    private val moveTagRe = Regex("""\\move\([^)]*\)""")
    private val posTagRe = Regex("""\\pos\([^,)]*,[^,)]*\)""")
    private val fade7TagRe = Regex("""\\fade\([^)]*\)""")
    private val fad2TagRe = Regex("""\\fad\([^,)]*,[^,)]*\)""")

    fun cleanTagsLine(rawText: String): String {
        if (!rawText.contains('{')) return rawText
        var text = rawText

        // 合并相邻且不同时含 \k 的标签块 {a}{b} -> {ab}
        val adjRe = Regex("""\{([^{}]*)\}\{([^{}]*)\}""")
        var guard = 0
        while (guard++ < 64) {
            var mergedAny = false
            text = adjRe.replace(text) { m ->
                val b1 = m.groupValues[1]
                val b2 = m.groupValues[2]
                if (kTagRe.containsMatchIn(b1) && kTagRe.containsMatchIn(b2)) {
                    "{$b1}\u0001{$b2}"
                } else {
                    mergedAny = true
                    "{$b1$b2}"
                }
            }
            if (!mergedAny) break
        }
        text = text.replace("\u0001", "")

        // 将每个块中的首个 \k 标签前移到块首
        val moveKFrontRe = Regex("""\{([^{}]*?)(\\[kK][fo]?[\d.]+)([^{}]*)\}""")
        text = moveKFrontRe.replace(text) { m ->
            val pre = m.groupValues[1]
            val k = m.groupValues[2]
            val post = m.groupValues[3]
            if (pre.isEmpty() || kTagRe.containsMatchIn(pre)) m.value else "{$k$pre$post}"
        }

        // 提取整行唯一生效标签（\an, \org, 首个 \move/\pos, 首个 \fade/\fad）并统一前置到句首块
        val hoisted = StringBuilder()
        fun extractFirst(re: Regex) {
            val hit = re.find(text)
            if (hit != null) {
                hoisted.append(hit.value)
                text = text.replace(re, "")
            }
        }
        fun extractFirstOfTwo(re1: Regex, re2: Regex) {
            val h1 = re1.find(text)
            val h2 = re2.find(text)
            text = text.replace(re1, "").replace(re2, "")
            when {
                h1 != null && (h2 == null || h1.range.first < h2.range.first) -> hoisted.append(h1.value)
                h2 != null -> hoisted.append(h2.value)
            }
        }

        extractFirst(anTagRe)
        extractFirst(orgTagRe)
        extractFirstOfTwo(moveTagRe, posTagRe)
        extractFirstOfTwo(fade7TagRe, fad2TagRe)

        if (hoisted.isNotEmpty()) {
            text = if (text.startsWith("{")) {
                "{" + hoisted + text.substring(1)
            } else {
                "{$hoisted}$text"
            }
        }

        // 清除空白括号 {}
        text = text.replace(Regex("""\{\s*\}"""), "")
        return text
    }

    fun cleanTags(events: List<AssEvent>, selectedIds: Set<Long> = emptySet()): Pair<List<AssEvent>, Int> {
        var changed = 0
        val out = events.map { ev ->
            if (ev.comment || (selectedIds.isNotEmpty() && ev.id !in selectedIds) || ev.text.isEmpty()) ev
            else {
                val cleaned = cleanTagsLine(ev.text)
                if (cleaned != ev.text) {
                    changed++
                    ev.copy(text = cleaned)
                } else ev
            }
        }
        return out to changed
    }

    // =========================================================================
    // 2. Strip Tags / Add Edgeblur / Make Fullwidth
    // =========================================================================

    fun stripTags(events: List<AssEvent>, selectedIds: Set<Long>): Pair<List<AssEvent>, Int> {
        var changed = 0
        val out = events.map { ev ->
            if (ev.comment || (selectedIds.isNotEmpty() && ev.id !in selectedIds)) ev
            else {
                val plain = ev.strippedText
                if (plain != ev.text) { changed++; ev.copy(text = plain) } else ev
            }
        }
        return out to changed
    }

    fun addEdgeBlur(events: List<AssEvent>, selectedIds: Set<Long>): Pair<List<AssEvent>, Int> {
        var changed = 0
        val blurCheck = Regex("""\\(?:be|blur)[\d.]+""")
        val out = events.map { ev ->
            if (ev.comment || (selectedIds.isNotEmpty() && ev.id !in selectedIds) || ev.text.isBlank()) ev
            else if (blurCheck.containsMatchIn(ev.text)) ev
            else {
                changed++
                val nextText = if (ev.text.startsWith("{")) "{\\be1" + ev.text.substring(1) else "{\\be1}${ev.text}"
                ev.copy(text = nextText)
            }
        }
        return out to changed
    }

    fun makeFullwidth(events: List<AssEvent>, selectedIds: Set<Long>): Pair<List<AssEvent>, Int> {
        var changed = 0
        val out = events.map { ev ->
            if (ev.comment || (selectedIds.isNotEmpty() && ev.id !in selectedIds) || ev.text.isEmpty()) ev
            else {
                val blocks = DialogueBlock.parse(ev.text).map { blk ->
                    if (blk !is DialogueBlock.Plain) blk
                    else DialogueBlock.Plain(toFullwidthPreservingEscapes(blk.text))
                }
                val rebuilt = DialogueBlock.toText(blocks)
                if (rebuilt != ev.text) { changed++; ev.copy(text = rebuilt) } else ev
            }
        }
        return out to changed
    }

    private fun toFullwidthPreservingEscapes(s: String): String = buildString(s.length) {
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length && (s[i + 1] == 'N' || s[i + 1] == 'n' || s[i + 1] == 'h')) {
                append(ch).append(s[i + 1])
                i += 2
                continue
            }
            when (ch.code) {
                0x20 -> append('\u3000')
                in 0x21..0x7E -> append((ch.code + 0xFEE0).toChar())
                else -> append(ch)
            }
            i++
        }
    }

    // =========================================================================
    // 3. Automatic Karaoke Lead-in & Select Overlaps
    // =========================================================================

    fun autoKaraokeLeadIn(events: List<AssEvent>, selectedIds: Set<Long>): Pair<List<AssEvent>, Int> {
        val targetIndices = events.indices.filter { idx ->
            !events[idx].comment && (selectedIds.isEmpty() || events[idx].id in selectedIds)
        }
        if (targetIndices.size < 2) return events to 0

        val mut = events.toMutableList()
        var modified = 0
        for (i in 1 until targetIndices.size) {
            val aIdx = targetIndices[i - 1]
            val bIdx = targetIndices[i]
            val a = mut[aIdx]
            val b = mut[bIdx]
            val gapMs = b.start.millis - a.end.millis
            if (gapMs > 0L) {
                val gapCs = gapMs / 10L
                mut[bIdx] = b.copy(
                    start = a.end,
                    text = "{\\k$gapCs}${b.text}",
                )
                modified++
            }
        }
        return mut.toList() to modified
    }

    fun selectOverlaps(events: List<AssEvent>, selectedIds: Set<Long> = emptySet()): Set<Long> {
        val pool = events.mapIndexedNotNull { idx, ev ->
            if (ev.comment) null
            else if (selectedIds.size > 1 && ev.id !in selectedIds) null
            else idx to ev
        }.sortedWith(compareBy<Pair<Int, AssEvent>> { it.second.start.millis }.thenBy { it.first })

        var maxEndMs = 0L
        val overlaps = linkedSetOf<Long>()
        for ((_, line) in pool) {
            if (line.start.millis >= maxEndMs) {
                maxEndMs = line.end.millis
            } else {
                overlaps += line.id
            }
        }
        return overlaps
    }

    // =========================================================================
    // 4. Karaoke Templater (复刻 automation/autoload/kara-templater.lua 核心引擎)
    // =========================================================================

    private enum class TemplateMode { LINE, PRE_LINE, SYL, CHAR }

    private data class KaraTemplate(
        val mode: TemplateMode,
        val styleName: String?, // null = all styles
        val layer: Int,
        val loops: Int,
        val noBlank: Boolean,
        val noText: Boolean,
        val keepTags: Boolean,
        val templateText: String,
    )

    data class TemplaterResult(
        val script: AssScript,
        val templateCount: Int,
        val generatedLines: Int,
    )

    fun applyKaraokeTemplater(script: AssScript): TemplaterResult {
        val playResX = script.getScriptInfo("PlayResX")?.toIntOrNull()?.takeIf { it > 0 } ?: 1920
        val playResY = script.getScriptInfo("PlayResY")?.toIntOrNull()?.takeIf { it > 0 } ?: 1080
        val styleMap = script.styles.associateBy { it.name }

        // 1. 移除上次生成的 Effect == "fx" 行，收集所有 Comment: template ... 模板定义
        val templates = mutableListOf<KaraTemplate>()
        val templateStyles = mutableSetOf<String>()
        var applyAllStyles = false

        val cleanBaseEvents = script.events.filterNot { !it.comment && it.effect.trim().equals("fx", ignoreCase = true) }

        for (ev in cleanBaseEvents) {
            if (!ev.comment) continue
            val tokens = ev.effect.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty() || !tokens.first().equals("template", ignoreCase = true)) continue

            var mode = TemplateMode.LINE
            var allStyles = false
            var loops = 1
            var noBlank = false
            var noText = false
            var keepTags = false

            var i = 1
            while (i < tokens.size) {
                when (tokens[i].lowercase()) {
                    "line" -> mode = TemplateMode.LINE
                    "pre-line" -> mode = TemplateMode.PRE_LINE
                    "syl" -> mode = TemplateMode.SYL
                    "char" -> mode = TemplateMode.CHAR
                    "all" -> allStyles = true
                    "noblank" -> noBlank = true
                    "notext" -> noText = true
                    "keeptags" -> keepTags = true
                    "repeat", "loop" -> {
                        val n = tokens.getOrNull(i + 1)?.toIntOrNull()
                        if (n != null && n > 0) {
                            loops = n.coerceAtMost(64)
                            i++
                        }
                    }
                }
                i++
            }
            if (allStyles) applyAllStyles = true else templateStyles += ev.style
            templates += KaraTemplate(
                mode = mode,
                styleName = if (allStyles) null else ev.style,
                layer = ev.layer,
                loops = loops,
                noBlank = noBlank,
                noText = noText,
                keepTags = keepTags,
                templateText = ev.text,
            )
        }

        if (templates.isEmpty()) {
            return TemplaterResult(script.withEvents(cleanBaseEvents), 0, 0)
        }

        var nextId = (cleanBaseEvents.maxOfOrNull { it.id } ?: 0L) + 1L
        val outputEvents = mutableListOf<AssEvent>()
        var generatedCount = 0

        for (ev in cleanBaseEvents) {
            val matchesAnyTemplate = !ev.comment &&
                (ev.effect.isBlank() || ev.effect.trim().equals("karaoke", ignoreCase = true)) &&
                (applyAllStyles || ev.style in templateStyles)

            if (!matchesAnyTemplate) {
                outputEvents += ev
                continue
            }

            // 将原歌词行标记为注释（Comment: ... Effect: karaoke），对齐桌面 kara-templater 行为
            outputEvents += ev.copy(comment = true, effect = "karaoke")

            val st = styleMap[ev.style] ?: AssStyle()
            val applicable = templates.filter { it.styleName == null || it.styleName == ev.style }
            val parsedSyls = KaraokeSyllables.parse(ev.text).let { list ->
                if (list.isNotEmpty()) list
                else listOf(KaraokeSyllables.Syllable("k", ((ev.end.millis - ev.start.millis) / 10L).coerceAtLeast(1L), ev.strippedText))
            }

            val linePlain = ev.strippedText
            val estCharW = st.fontSize * 0.85 * (st.scaleX / 100.0)
            val totalWidth = max(estCharW, linePlain.length * estCharW)
            val marginL = if (ev.margins.left != 0) ev.margins.left else st.margins.left
            val marginR = if (ev.margins.right != 0) ev.margins.right else st.margins.right
            val marginV = if (ev.margins.vertical != 0) ev.margins.vertical else st.margins.vertical

            val lineCenterX = when ((st.alignment - 1) % 3) {
                0 -> marginL + totalWidth / 2.0
                2 -> playResX - marginR - totalWidth / 2.0
                else -> (playResX + marginL - marginR) / 2.0
            }
            val lineY = when {
                st.alignment >= 7 -> marginV + st.fontSize / 2.0
                st.alignment >= 4 -> playResY / 2.0
                else -> playResY - marginV - st.fontSize / 2.0
            }
            val lineLeft = lineCenterX - totalWidth / 2.0
            val lineRight = lineCenterX + totalWidth / 2.0

            val lStart = ev.start.millis
            val lEnd = ev.end.millis
            val lDur = (lEnd - lStart).coerceAtLeast(0L)
            val lMid = lStart + lDur / 2L

            for (tpl in applicable) {
                for (loopIdx in 1..tpl.loops) {
                    when (tpl.mode) {
                        TemplateMode.LINE, TemplateMode.PRE_LINE -> {
                            val baseVars = mapOf(
                                "layer" to tpl.layer.toDouble(),
                                "lstart" to lStart.toDouble(),
                                "lend" to lEnd.toDouble(),
                                "ldur" to lDur.toDouble(),
                                "lmid" to lMid.toDouble(),
                                "start" to 0.0,
                                "end" to lDur.toDouble(),
                                "dur" to lDur.toDouble(),
                                "mid" to (lDur / 2.0),
                                "kdur" to (lDur / 10.0),
                                "lleft" to lineLeft,
                                "lcenter" to lineCenterX,
                                "lright" to lineRight,
                                "x" to lineCenterX,
                                "y" to lineY,
                                "j" to loopIdx.toDouble(),
                                "maxj" to tpl.loops.toDouble(),
                                "syln" to parsedSyls.size.toDouble(),
                            )
                            val expandedPrefix = evaluateTemplateString(tpl.templateText, baseVars, ev.style)
                            val body = when {
                                tpl.noText -> expandedPrefix
                                tpl.mode == TemplateMode.PRE_LINE -> expandedPrefix + (if (tpl.keepTags) ev.text else ev.strippedText)
                                else -> {
                                    // template line: 将模板应用到每个音节前并保留歌词
                                    var curRelMs = 0L
                                    buildString {
                                        parsedSyls.forEachIndexed { sIdx, syl ->
                                            val sDur = syl.centiseconds * 10L
                                            val sVars = baseVars + mapOf(
                                                "start" to curRelMs.toDouble(),
                                                "end" to (curRelMs + sDur).toDouble(),
                                                "dur" to sDur.toDouble(),
                                                "mid" to (curRelMs + sDur / 2.0),
                                                "kdur" to syl.centiseconds.toDouble(),
                                                "si" to (sIdx + 1).toDouble(),
                                            )
                                            append(evaluateTemplateString(tpl.templateText, sVars, ev.style))
                                            append(syl.text)
                                            curRelMs += sDur
                                        }
                                    }
                                }
                            }
                            outputEvents += ev.copy(
                                id = nextId++,
                                comment = false,
                                layer = tpl.layer,
                                effect = "fx",
                                text = body,
                            )
                            generatedCount++
                        }

                        TemplateMode.SYL -> {
                            var curRelMs = 0L
                            var curXOffset = lineLeft
                            parsedSyls.forEachIndexed { sIdx, syl ->
                                val sDur = syl.centiseconds * 10L
                                val sylW = max(estCharW * 0.5, syl.text.length * estCharW)
                                val sylCenterX = curXOffset + sylW / 2.0
                                if (!(tpl.noBlank && (syl.text.isBlank() || sDur <= 0L))) {
                                    val sVars = mapOf(
                                        "layer" to tpl.layer.toDouble(),
                                        "lstart" to lStart.toDouble(),
                                        "lend" to lEnd.toDouble(),
                                        "ldur" to lDur.toDouble(),
                                        "lmid" to lMid.toDouble(),
                                        "start" to curRelMs.toDouble(),
                                        "end" to (curRelMs + sDur).toDouble(),
                                        "dur" to sDur.toDouble(),
                                        "mid" to (curRelMs + sDur / 2.0),
                                        "sstart" to curRelMs.toDouble(),
                                        "send" to (curRelMs + sDur).toDouble(),
                                        "sdur" to sDur.toDouble(),
                                        "smid" to (curRelMs + sDur / 2.0),
                                        "kdur" to syl.centiseconds.toDouble(),
                                        "si" to (sIdx + 1).toDouble(),
                                        "syln" to parsedSyls.size.toDouble(),
                                        "x" to sylCenterX,
                                        "y" to lineY,
                                        "left" to curXOffset,
                                        "center" to sylCenterX,
                                        "right" to (curXOffset + sylW),
                                        "width" to sylW,
                                        "lleft" to lineLeft,
                                        "lcenter" to lineCenterX,
                                        "lright" to lineRight,
                                        "j" to loopIdx.toDouble(),
                                        "maxj" to tpl.loops.toDouble(),
                                    )
                                    val expanded = evaluateTemplateString(tpl.templateText, sVars, ev.style)
                                    val finalTxt = if (tpl.noText) expanded else expanded + syl.text
                                    outputEvents += ev.copy(
                                        id = nextId++,
                                        comment = false,
                                        layer = tpl.layer,
                                        effect = "fx",
                                        text = finalTxt,
                                    )
                                    generatedCount++
                                }
                                curRelMs += sDur
                                curXOffset += sylW
                            }
                        }

                        TemplateMode.CHAR -> {
                            val chars = linePlain.toList()
                            chars.forEachIndexed { cIdx, ch ->
                                if (!(tpl.noBlank && ch.isWhitespace())) {
                                    val charX = lineLeft + (cIdx + 0.5) * estCharW
                                    val cVars = mapOf(
                                        "layer" to tpl.layer.toDouble(),
                                        "lstart" to lStart.toDouble(),
                                        "lend" to lEnd.toDouble(),
                                        "ldur" to lDur.toDouble(),
                                        "lmid" to lMid.toDouble(),
                                        "start" to 0.0,
                                        "end" to lDur.toDouble(),
                                        "dur" to lDur.toDouble(),
                                        "mid" to (lDur / 2.0),
                                        "ci" to (cIdx + 1).toDouble(),
                                        "charn" to chars.size.toDouble(),
                                        "x" to charX,
                                        "y" to lineY,
                                        "lleft" to lineLeft,
                                        "lcenter" to lineCenterX,
                                        "lright" to lineRight,
                                        "j" to loopIdx.toDouble(),
                                        "maxj" to tpl.loops.toDouble(),
                                    )
                                    val expanded = evaluateTemplateString(tpl.templateText, cVars, ev.style)
                                    outputEvents += ev.copy(
                                        id = nextId++,
                                        comment = false,
                                        layer = tpl.layer,
                                        effect = "fx",
                                        text = if (tpl.noText) expanded else expanded + ch,
                                    )
                                    generatedCount++
                                }
                            }
                        }
                    }
                }
            }
        }

        return TemplaterResult(
            script = script.copy(events = outputEvents.toPersistentList()),
            templateCount = templates.size,
            generatedLines = generatedCount,
        )
    }

    private val dollarVarRe = Regex("""\$([a-zA-Z_][a-zA-Z0-9_]*)""")
    private val bangExprRe = Regex("""!([^!]*)!""")

    private fun evaluateTemplateString(template: String, vars: Map<String, Double>, styleName: String): String {
        fun replaceVars(input: String): String = dollarVarRe.replace(input) { m ->
            val key = m.groupValues[1].lowercase()
            if (key == "style") styleName
            else {
                val num = vars[key] ?: return@replace m.value
                formatNumber(num)
            }
        }

        val afterBang = bangExprRe.replace(template) { m ->
            val exprWithVars = replaceVars(m.groupValues[1])
            val evaluated = runCatching { MiniMathParser(exprWithVars, vars).parse() }.getOrNull()
            if (evaluated != null && evaluated.isFinite()) formatNumber(evaluated) else exprWithVars
        }

        return replaceVars(afterBang)
    }

    private fun formatNumber(v: Double): String {
        val rounded = v.roundToLong()
        return if (abs(v - rounded) < 1e-4) rounded.toString()
        else "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
    }

    // 轻量安全的递归下降数学求值器（支持加减乘除模幂括号与常用 math 函数）。
    private class MiniMathParser(val src: String, val vars: Map<String, Double>) {
        private var pos = 0

        fun parse(): Double {
            val v = parseAddSub()
            skipSpaces()
            return v
        }

        private fun skipSpaces() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (true) {
                skipSpaces()
                if (pos >= src.length) break
                val op = src[pos]
                if (op == '+' || op == '-') {
                    pos++
                    val right = parseMulDiv()
                    left = if (op == '+') left + right else left - right
                } else break
            }
            return left
        }

        private fun parseMulDiv(): Double {
            var left = parsePow()
            while (true) {
                skipSpaces()
                if (pos >= src.length) break
                val op = src[pos]
                if (op == '*' || op == '/' || op == '%') {
                    pos++
                    val right = parsePow()
                    left = when (op) {
                        '*' -> left * right
                        '/' -> if (right == 0.0) 0.0 else left / right
                        else -> if (right == 0.0) 0.0 else left % right
                    }
                } else break
            }
            return left
        }

        private fun parsePow(): Double {
            var base = parseUnary()
            skipSpaces()
            if (pos < src.length && src[pos] == '^') {
                pos++
                val exp = parseUnary()
                base = base.pow(exp)
            }
            return base
        }

        private fun parseUnary(): Double {
            skipSpaces()
            if (pos < src.length && src[pos] == '-') {
                pos++
                return -parseUnary()
            }
            if (pos < src.length && src[pos] == '+') {
                pos++
                return parseUnary()
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            skipSpaces()
            if (pos >= src.length) return 0.0
            if (src[pos] == '(') {
                pos++
                val inner = parseAddSub()
                skipSpaces()
                if (pos < src.length && src[pos] == ')') pos++
                return inner
            }
            if (src[pos].isDigit() || src[pos] == '.') {
                val start = pos
                while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
                return src.substring(start, pos).toDoubleOrNull() ?: 0.0
            }
            if (src[pos].isLetter() || src[pos] == '_') {
                val start = pos
                while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_' || src[pos] == '.')) pos++
                val ident = src.substring(start, pos).lowercase()
                skipSpaces()
                if (pos < src.length && src[pos] == '(') {
                    pos++
                    val args = mutableListOf<Double>()
                    skipSpaces()
                    if (pos < src.length && src[pos] != ')') {
                        args += parseAddSub()
                        skipSpaces()
                        while (pos < src.length && src[pos] == ',') {
                            pos++
                            args += parseAddSub()
                            skipSpaces()
                        }
                    }
                    if (pos < src.length && src[pos] == ')') pos++
                    val a0 = args.getOrElse(0) { 0.0 }
                    val a1 = args.getOrElse(1) { 0.0 }
                    return when (ident.removePrefix("math.")) {
                        "sin" -> sin(a0)
                        "cos" -> cos(a0)
                        "abs" -> abs(a0)
                        "floor" -> floor(a0)
                        "min" -> min(a0, a1)
                        "max" -> max(a0, a1)
                        "rad" -> Math.toRadians(a0)
                        "random" -> when (args.size) {
                            0 -> Random.nextDouble()
                            1 -> Random.nextInt(1, a0.toInt().coerceAtLeast(2)).toDouble()
                            else -> Random.nextInt(a0.toInt(), (a1.toInt() + 1).coerceAtLeast(a0.toInt() + 1)).toDouble()
                        }
                        else -> 0.0
                    }
                }
                if (ident == "math.pi" || ident == "pi") return Math.PI
                return vars[ident] ?: 0.0
            }
            return 0.0
        }
    }
}
