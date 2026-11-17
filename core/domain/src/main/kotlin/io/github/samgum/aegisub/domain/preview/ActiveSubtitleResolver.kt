package io.github.samgum.aegisub.domain.preview

import io.github.samgum.aegisub.domain.edit.VisualTags
import io.github.samgum.aegisub.domain.model.AssColor
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.DialogueBlock
import io.github.samgum.aegisub.domain.model.Margins
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 富文本分段渲染信息：承载行内覆盖标签（颜色/字重/字号/描边/阴影/模糊/卡拉OK扫词进度）。
 *
 * @author 伤感咩吖
 */
data class StyledTextSpan(
    val text: String,
    val font: String,
    val fontSize: Double,
    val primaryColor: AssColor,
    val secondaryColor: AssColor,
    val outlineColor: AssColor,
    val shadowColor: AssColor,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strikeout: Boolean,
    val scaleX: Double = 100.0,
    val scaleY: Double = 100.0,
    val spacing: Double = 0.0,
    val outlineWidth: Double = 2.0,
    val shadowWidth: Double = 2.0,
    val blur: Double = 0.0,
    /** 卡拉OK扫词高亮进度：null 表示非卡拉OK段；`0f..1f` 表示从 secondary 扫向 primary 的填充比。 */
    val karaokeProgress: Float? = null,
)

/**
 * 渲染一条叠加字幕所需的完整信息：
 * 既保留旧字段（`text`/`style`/`margins`/`playResX`/`playResY`/`pos`）兼容既有测试，
 * 又提供按当前时间点 `positionMs` 实时求值后的动态位置（`\move`）、旋转（`\frz`/`\frx`/`\fry` + `\org`）、
 * 缩放（`\fscx`/`\fscy`）、淡入淡出（`\fad`/`\fade`）、动态 `\t(...)` 插值、矩形/矢量 `\clip` 与行内富文本段 [spans]。
 *
 * @author 伤感咩吖
 */
data class SubtitleRenderInfo(
    val text: String,
    val style: AssStyle,
    val margins: Margins,
    val playResX: Int = 384,
    val playResY: Int = 288,
    /** 静态 {\pos(x,y)} 锚点（脚本坐标系）；null 表示无 \pos。 */
    val pos: Pair<Int, Int>? = null,
    val eventId: Long = 0L,
    val layer: Int = 0,
    /** 生效对齐（含行内 `\an` / `\a` 覆盖），范围 1..9。 */
    val alignment: Int = style.alignment,
    /** 当前时刻生效锚点（含 `\pos` 或 `\move` 时间插值结果，脚本坐标系）。 */
    val effectivePos: Pair<Float, Float>? = pos?.let { it.first.toFloat() to it.second.toFloat() },
    val rotationZ: Float = style.angle.toFloat(),
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val origin: Pair<Float, Float>? = null,
    val scaleX: Float = style.scaleX.toFloat(),
    val scaleY: Float = style.scaleY.toFloat(),
    /** `\fad` / `\fade` 计算出的整体不透明度乘子 [0f..1f]。 */
    val opacityMultiplier: Float = 1f,
    val clipRect: VisualTags.ClipRect? = null,
    val vectorClip: VisualTags.VectorClip? = null,
    val spans: List<StyledTextSpan> = emptyList(),
)

/**
 * 给定脚本与播放位置，解析当前应叠加显示的全部字幕及其动态样式。
 * 纯函数，无 Android/Compose 依赖，可纯 JVM 单测。
 *
 * @author 伤感咩吖
 */
object ActiveSubtitleResolver {

    /** 返回当前时间点应显示的首条事件（非注释、start <= t < end），无则 null。 */
    fun activeEvent(script: AssScript, positionMs: Long): AssEvent? =
        script.events.firstOrNull { event ->
            !event.comment &&
                event.start.millis <= positionMs &&
                positionMs < event.end.millis
        }

    /** 返回当前时间点所有活动事件，按层降序（兼容既有语义）。 */
    fun activeEvents(script: AssScript, positionMs: Long): List<AssEvent> =
        script.events.asSequence()
            .filter { event ->
                !event.comment &&
                    event.start.millis <= positionMs &&
                    positionMs < event.end.millis
            }
            .sortedByDescending { it.layer }
            .toList()

    /** 返回当前应叠加的首条渲染信息（无活动事件返回 null）。 */
    fun renderInfo(script: AssScript, positionMs: Long): SubtitleRenderInfo? =
        renderInfos(script, positionMs).firstOrNull()

    /** 返回当前应叠加的全部渲染信息（按层降序）。 */
    fun renderInfos(script: AssScript, positionMs: Long): List<SubtitleRenderInfo> {
        val playResX = script.getScriptInfo("PlayResX")?.toIntOrNull()?.takeIf { it > 0 } ?: 384
        val playResY = script.getScriptInfo("PlayResY")?.toIntOrNull()?.takeIf { it > 0 } ?: 288
        return activeEvents(script, positionMs).map { event ->
            evaluateEvent(script, event, positionMs, playResX, playResY)
        }
    }

    /**
     * 返回按 ASS 标准画家算法排序（低 layer 先画在底层、高 layer 后画在顶层；同 layer 按行序）的渲染列表。
     */
    fun renderInfosInDrawOrder(script: AssScript, positionMs: Long): List<SubtitleRenderInfo> {
        val playResX = script.getScriptInfo("PlayResX")?.toIntOrNull()?.takeIf { it > 0 } ?: 384
        val playResY = script.getScriptInfo("PlayResY")?.toIntOrNull()?.takeIf { it > 0 } ?: 288
        return script.events.asSequence()
            .withIndex()
            .filter { (_, event) ->
                !event.comment &&
                    event.start.millis <= positionMs &&
                    positionMs < event.end.millis
            }
            .sortedWith(compareBy<IndexedValue<AssEvent>> { it.value.layer }.thenBy { it.index })
            .map { (_, event) -> evaluateEvent(script, event, positionMs, playResX, playResY) }
            .toList()
    }

    /** 样式解析：按名匹配 → Default → 首个 → 默认 AssStyle。 */
    fun resolveStyle(script: AssScript, event: AssEvent): AssStyle =
        script.styles.firstOrNull { it.name == event.style }
            ?: script.styles.firstOrNull { it.name == "Default" }
            ?: script.styles.firstOrNull()
            ?: AssStyle()

    private fun evaluateEvent(
        script: AssScript,
        event: AssEvent,
        positionMs: Long,
        playResX: Int,
        playResY: Int,
    ): SubtitleRenderInfo {
        val baseStyle = resolveStyle(script, event)
        // ASS 标准：事件边距非 0 时覆盖样式边距，为 0 时继承样式边距
        val margins = Margins(
            left = if (event.margins.left != 0) event.margins.left else baseStyle.margins.left,
            right = if (event.margins.right != 0) event.margins.right else baseStyle.margins.right,
            vertical = if (event.margins.vertical != 0) event.margins.vertical else baseStyle.margins.vertical,
        )

        val relMs = (positionMs - event.start.millis).coerceAtLeast(0L)
        val totalDurMs = (event.end.millis - event.start.millis).coerceAtLeast(1L)

        val staticPos = VisualTags.getPos(event.text)
        val moveParams = VisualTags.getMove(event.text)
        val effectivePos: Pair<Float, Float>? = when {
            moveParams != null -> {
                val tStart = if (moveParams.t1 == 0 && moveParams.t2 == 0) 0L else moveParams.t1.toLong()
                val tEnd = if (moveParams.t1 == 0 && moveParams.t2 == 0) totalDurMs else moveParams.t2.toLong().coerceAtLeast(tStart + 1L)
                val progress = when {
                    relMs <= tStart -> 0f
                    relMs >= tEnd -> 1f
                    else -> ((relMs - tStart).toFloat() / (tEnd - tStart).toFloat()).coerceIn(0f, 1f)
                }
                val x = moveParams.x1 + (moveParams.x2 - moveParams.x1) * progress
                val y = moveParams.y1 + (moveParams.y2 - moveParams.y1) * progress
                x to y
            }
            staticPos != null -> staticPos.first.toFloat() to staticPos.second.toFloat()
            else -> null
        }

        val fadeMultiplier = computeFadeOpacity(event.text, relMs, totalDurMs)
        val alignment = VisualTags.getAlignment(event.text) ?: baseStyle.alignment
        val rot3d = VisualTags.getRotation3d(event.text)
        val origin = VisualTags.getOrigin(event.text)?.let { it.first.toFloat() to it.second.toFloat() }
        val clipRect = VisualTags.getClip(event.text)
        val vecClip = VisualTags.getVectorClip(event.text)

        // 解析逐段富文本与 \t(...) 动画插值 + 卡拉OK进度
        var curFont = baseStyle.font
        var curFs = baseStyle.fontSize
        var curPrimary = baseStyle.primary
        var curSecondary = baseStyle.secondary
        var curOutline = baseStyle.outline
        var curShadow = baseStyle.shadow
        var curBold = baseStyle.bold
        var curItalic = baseStyle.italic
        var curUnderline = baseStyle.underline
        var curStrike = baseStyle.strikeout
        var curScaleX = baseStyle.scaleX
        var curScaleY = baseStyle.scaleY
        var curSpacing = baseStyle.spacing
        var curBord = baseStyle.outlineWidth
        var curShad = baseStyle.shadowWidth
        var curBlur = 0.0
        var curRotZ = if (rot3d.frz != 0.0) rot3d.frz else baseStyle.angle

        var karaokeCursorMs = 0L
        var pendingKaraokeDurMs: Long? = null
        var pendingKaraokeSweep = false
        var drawingMode = 0

        fun applySingleTag(name: String, rawVal: String, interpFactor: Float = 1f) {
            val v = rawVal.trim()
            when (name) {
                "fn" -> if (v.isNotEmpty()) curFont = v
                "fs" -> {
                    val target = if (v.startsWith("+") || v.startsWith("-")) {
                        (curFs + (v.toDoubleOrNull() ?: 0.0)).coerceAtLeast(1.0)
                    } else {
                        (v.toDoubleOrNull() ?: curFs).coerceAtLeast(1.0)
                    }
                    curFs += (target - curFs) * interpFactor
                }
                "fscx" -> {
                    val target = (v.toDoubleOrNull() ?: curScaleX).coerceAtLeast(1.0)
                    curScaleX += (target - curScaleX) * interpFactor
                }
                "fscy" -> {
                    val target = (v.toDoubleOrNull() ?: curScaleY).coerceAtLeast(1.0)
                    curScaleY += (target - curScaleY) * interpFactor
                }
                "fsp" -> {
                    val target = v.toDoubleOrNull() ?: curSpacing
                    curSpacing += (target - curSpacing) * interpFactor
                }
                "fr", "frz" -> {
                    val target = v.toDoubleOrNull() ?: curRotZ
                    curRotZ += (target - curRotZ) * interpFactor
                }
                "bord" -> {
                    val target = (v.toDoubleOrNull() ?: curBord).coerceAtLeast(0.0)
                    curBord += (target - curBord) * interpFactor
                }
                "shad" -> {
                    val target = (v.toDoubleOrNull() ?: curShad).coerceAtLeast(0.0)
                    curShad += (target - curShad) * interpFactor
                }
                "blur", "be" -> {
                    val target = (v.toDoubleOrNull() ?: curBlur).coerceAtLeast(0.0)
                    curBlur += (target - curBlur) * interpFactor
                }
                "b" -> curBold = when (v) { "0" -> false; "1", "" -> true; else -> (v.toIntOrNull() ?: 400) >= 600 }
                "i" -> curItalic = v != "0"
                "u" -> curUnderline = v != "0"
                "s" -> curStrike = v != "0"
                "c", "1c" -> AssColor.parseAssOrNull(v)?.let { c ->
                    curPrimary = lerpColor(curPrimary, c.withAlpha(curPrimary.a), interpFactor)
                }
                "2c" -> AssColor.parseAssOrNull(v)?.let { c ->
                    curSecondary = lerpColor(curSecondary, c.withAlpha(curSecondary.a), interpFactor)
                }
                "3c" -> AssColor.parseAssOrNull(v)?.let { c ->
                    curOutline = lerpColor(curOutline, c.withAlpha(curOutline.a), interpFactor)
                }
                "4c" -> AssColor.parseAssOrNull(v)?.let { c ->
                    curShadow = lerpColor(curShadow, c.withAlpha(curShadow.a), interpFactor)
                }
                "alpha" -> AssColor.parseAssAlphaToOpacity(v)?.let { op ->
                    val newA = (curPrimary.a + (op - curPrimary.a) * interpFactor).roundToInt()
                    curPrimary = curPrimary.withAlpha(newA)
                    curSecondary = curSecondary.withAlpha(newA)
                    curOutline = curOutline.withAlpha((curOutline.a + (op - curOutline.a) * interpFactor).roundToInt())
                    curShadow = curShadow.withAlpha((curShadow.a + (op - curShadow.a) * interpFactor).roundToInt())
                }
                "1a" -> AssColor.parseAssAlphaToOpacity(v)?.let { op ->
                    curPrimary = curPrimary.withAlpha((curPrimary.a + (op - curPrimary.a) * interpFactor).roundToInt())
                }
                "2a" -> AssColor.parseAssAlphaToOpacity(v)?.let { op ->
                    curSecondary = curSecondary.withAlpha((curSecondary.a + (op - curSecondary.a) * interpFactor).roundToInt())
                }
                "3a" -> AssColor.parseAssAlphaToOpacity(v)?.let { op ->
                    curOutline = curOutline.withAlpha((curOutline.a + (op - curOutline.a) * interpFactor).roundToInt())
                }
                "4a" -> AssColor.parseAssAlphaToOpacity(v)?.let { op ->
                    curShadow = curShadow.withAlpha((curShadow.a + (op - curShadow.a) * interpFactor).roundToInt())
                }
                "k", "ko", "kf", "K" -> {
                    val cs = v.toLongOrNull() ?: 0L
                    pendingKaraokeDurMs = cs * 10L
                    pendingKaraokeSweep = (name == "kf" || name == "K")
                }
                "p" -> drawingMode = v.toIntOrNull() ?: 0
                "r" -> {
                    val rst = if (v.isEmpty()) baseStyle else script.styles.firstOrNull { it.name == v } ?: baseStyle
                    curFont = rst.font; curFs = rst.fontSize
                    curPrimary = rst.primary; curSecondary = rst.secondary
                    curOutline = rst.outline; curShadow = rst.shadow
                    curBold = rst.bold; curItalic = rst.italic
                    curUnderline = rst.underline; curStrike = rst.strikeout
                    curScaleX = rst.scaleX; curScaleY = rst.scaleY
                    curSpacing = rst.spacing; curBord = rst.outlineWidth; curShad = rst.shadowWidth
                }
            }
        }

        val spans = mutableListOf<StyledTextSpan>()
        val rawBlocks = parseBlocksPreservingAnimations(event.text)

        for (b in rawBlocks) {
            when (b) {
                is RawSeg.OverrideInner -> {
                    applyOverrideString(b.inner, relMs, totalDurMs, ::applySingleTag)
                }
                is RawSeg.PlainText -> {
                    if (drawingMode > 0) continue
                    val kDur = pendingKaraokeDurMs
                    val kProg: Float? = if (kDur != null) {
                        val segStart = karaokeCursorMs
                        val segEnd = segStart + kDur
                        karaokeCursorMs = segEnd
                        pendingKaraokeDurMs = null
                        when {
                            relMs < segStart -> 0f
                            relMs >= segEnd -> 1f
                            pendingKaraokeSweep && kDur > 0L -> ((relMs - segStart).toFloat() / kDur.toFloat()).coerceIn(0f, 1f)
                            else -> 1f
                        }
                    } else {
                        null
                    }
                    if (b.value.isNotEmpty()) {
                        spans += StyledTextSpan(
                            text = b.value,
                            font = curFont,
                            fontSize = curFs,
                            primaryColor = curPrimary,
                            secondaryColor = curSecondary,
                            outlineColor = curOutline,
                            shadowColor = curShadow,
                            bold = curBold,
                            italic = curItalic,
                            underline = curUnderline,
                            strikeout = curStrike,
                            scaleX = curScaleX,
                            scaleY = curScaleY,
                            spacing = curSpacing,
                            outlineWidth = curBord,
                            shadowWidth = curShad,
                            blur = curBlur,
                            karaokeProgress = kProg,
                        )
                    }
                }
            }
        }

        return SubtitleRenderInfo(
            text = event.strippedText,
            style = baseStyle,
            margins = margins,
            playResX = playResX,
            playResY = playResY,
            pos = staticPos,
            eventId = event.id,
            layer = event.layer,
            alignment = alignment,
            effectivePos = effectivePos,
            rotationZ = curRotZ.toFloat(),
            rotationX = rot3d.frx.toFloat(),
            rotationY = rot3d.fry.toFloat(),
            origin = origin,
            scaleX = curScaleX.toFloat(),
            scaleY = curScaleY.toFloat(),
            opacityMultiplier = fadeMultiplier,
            clipRect = clipRect,
            vectorClip = vecClip,
            spans = spans,
        )
    }

    private sealed interface RawSeg {
        data class OverrideInner(val inner: String) : RawSeg
        data class PlainText(val value: String) : RawSeg
    }

    private fun parseBlocksPreservingAnimations(text: String): List<RawSeg> {
        val out = mutableListOf<RawSeg>()
        var i = 0
        var plainStart = 0
        while (i < text.length) {
            if (text[i] == '{') {
                if (i > plainStart) out += RawSeg.PlainText(text.substring(plainStart, i))
                val close = text.indexOf('}', i + 1)
                val end = if (close < 0) text.length else close
                val inner = text.substring(i + 1, end)
                if (inner.isNotEmpty() && !inner.startsWith("*")) {
                    out += RawSeg.OverrideInner(inner)
                }
                i = if (close < 0) text.length else close + 1
                plainStart = i
            } else {
                i++
            }
        }
        if (plainStart < text.length) out += RawSeg.PlainText(text.substring(plainStart))
        return out
    }

    private fun applyOverrideString(
        inner: String,
        relMs: Long,
        totalDurMs: Long,
        applyTag: (name: String, rawValue: String, factor: Float) -> Unit,
    ) {
        var i = 0
        while (i < inner.length) {
            if (inner[i] != '\\') {
                i++
                continue
            }
            i++ // skip '\'
            if (i >= inner.length) break

            // 特殊处理 \t(t1,t2,accel,\tags...) 嵌套括号动画标签
            if (inner.startsWith("t(", i)) {
                val openParen = i + 1
                var depth = 1
                var p = openParen + 1
                while (p < inner.length && depth > 0) {
                    if (inner[p] == '(') depth++
                    else if (inner[p] == ')') depth--
                    p++
                }
                val tContent = inner.substring(openParen + 1, (p - 1).coerceAtLeast(openParen + 1))
                val firstSlash = tContent.indexOf('\\')
                if (firstSlash >= 0) {
                    val headerArgs = tContent.substring(0, firstSlash).split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    val nestedTags = tContent.substring(firstSlash)
                    val (t1, t2, accel) = when (headerArgs.size) {
                        0 -> Triple(0L, totalDurMs, 1.0)
                        1 -> Triple(0L, totalDurMs, headerArgs[0].toDoubleOrNull() ?: 1.0)
                        2 -> Triple(headerArgs[0].toLongOrNull() ?: 0L, headerArgs[1].toLongOrNull() ?: totalDurMs, 1.0)
                        else -> Triple(
                            headerArgs[0].toLongOrNull() ?: 0L,
                            headerArgs[1].toLongOrNull() ?: totalDurMs,
                            headerArgs[2].toDoubleOrNull() ?: 1.0,
                        )
                    }
                    val factor = when {
                        relMs <= t1 -> 0f
                        relMs >= t2 -> 1f
                        t2 > t1 -> ((relMs - t1).toDouble() / (t2 - t1).toDouble()).pow(accel).toFloat().coerceIn(0f, 1f)
                        else -> 1f
                    }
                    if (factor > 0f) {
                        applyOverrideString(nestedTags, relMs, totalDurMs) { n, rv, _ ->
                            applyTag(n, rv, factor)
                        }
                    }
                }
                i = p
                continue
            }

            // 常规标签：兼容 \1c..\4c 與 \1a..\4a 数字前缀标签名
            val nameStart = i
            if (i + 1 < inner.length && inner[i] in '1'..'4' && (inner[i + 1] == 'c' || inner[i + 1] == 'a')) {
                i += 2
            } else {
                while (i < inner.length && inner[i].isLetter()) i++
            }
            val name = inner.substring(nameStart, i)
            val valStart = i
            var parenDepth = 0
            while (i < inner.length) {
                val ch = inner[i]
                if (ch == '(') parenDepth++
                else if (ch == ')' && parenDepth > 0) parenDepth--
                else if (ch == '\\' && parenDepth == 0) break
                i++
            }
            val rawVal = inner.substring(valStart, i)
            if (name.isNotEmpty()) {
                applyTag(name, rawVal, 1f)
            }
        }
    }

    private fun lerpColor(a: AssColor, b: AssColor, t: Float): AssColor {
        val f = t.coerceIn(0f, 1f)
        return AssColor(
            r = (a.r + (b.r - a.r) * f).roundToInt().coerceIn(0, 255),
            g = (a.g + (b.g - a.g) * f).roundToInt().coerceIn(0, 255),
            b = (a.b + (b.b - a.b) * f).roundToInt().coerceIn(0, 255),
            a = (a.a + (b.a - a.a) * f).roundToInt().coerceIn(0, 255),
        )
    }

    private fun computeFadeOpacity(text: String, relMs: Long, totalDurMs: Long): Float {
        VisualTags.getComplexFade(text)?.let { f ->
            // ASS \fade(a1,a2,a3,t1,t2,t3,t4) alpha 0=不透明, 255=全透
            val alpha = when {
                relMs < f.t1 -> f.a1.toFloat()
                relMs < f.t2 && f.t2 > f.t1 -> f.a1 + (f.a2 - f.a1) * ((relMs - f.t1).toFloat() / (f.t2 - f.t1))
                relMs < f.t3 -> f.a2.toFloat()
                relMs < f.t4 && f.t4 > f.t3 -> f.a2 + (f.a3 - f.a2) * ((relMs - f.t3).toFloat() / (f.t4 - f.t3))
                else -> f.a3.toFloat()
            }
            return ((255f - alpha.coerceIn(0f, 255f)) / 255f).coerceIn(0f, 1f)
        }
        val fad = VisualTags.getFade(text) ?: return 1f
        var mult = 1f
        if (fad.fadeIn > 0 && relMs < fad.fadeIn) {
            mult = minOf(mult, relMs.toFloat() / fad.fadeIn.toFloat())
        }
        val fadeOutStart = totalDurMs - fad.fadeOut
        if (fad.fadeOut > 0 && relMs > fadeOutStart) {
            val rem = (totalDurMs - relMs).coerceAtLeast(0L)
            mult = minOf(mult, rem.toFloat() / fad.fadeOut.toFloat())
        }
        return mult.coerceIn(0f, 1f)
    }
}
