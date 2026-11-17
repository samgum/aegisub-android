package io.github.samgum.aegisub.domain.sgmy

import io.github.samgum.aegisub.domain.edit.ScriptInfoOps
import io.github.samgum.aegisub.domain.model.AssColor
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.Margins
import io.github.samgum.aegisub.domain.time.SubTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 音乐滚动歌词生成器（完整复刻 samgum/Aegisub SGMY `DialogLyricScroll` / `apply_lyric_scroll`）：
 * - 支持全部行或选中行范围
 * - 支持保留原字幕 / 注释隐藏原字幕（`MusicScroll Source`）/ 删除原字幕
 * - 支持单语 / 双语自动配对（相同起始时间或相邻译文样式自动合并为主副双行）
 * - 支持 7 档分辨率预设（脚本 PlayRes / 4K 2160p / 1080p / 720p / 1080×1920 竖屏 / 720×1280 竖屏 / 自定义）
 * - 支持三次缓出平滑滚动动画（`\move` + `\t(\alpha\1c\fscx\fscy)`）与静态驻留段，自动生成 `ScrollCurrent` / `ScrollDim` 样式
 *
 * @author 伤感咩吖
 */
object LyricScrollGenerator {

    const val EFFECT_GENERATED = "MusicScroll Generated"
    const val EFFECT_SOURCE = "MusicScroll Source"

    enum class SourceAction { KEEP, COMMENT_HIDE, DELETE }
    enum class LanguageMode { SINGLE, BILINGUAL }
    enum class HorizontalAlign { LEFT, CENTER, RIGHT }
    enum class ResolutionPreset(val labelZh: String, val width: Int, val height: Int) {
        SCRIPT_PLAYRES("跟随脚本 PlayRes", 0, 0),
        UHD_2160P("4K (3840×2160)", 3840, 2160),
        FHD_1080P("1080p (1920×1080)", 1920, 1080),
        HD_720P("720p (1280×720)", 1280, 720),
        VERTICAL_1080P("竖屏 1080×1920", 1080, 1920),
        VERTICAL_720P("竖屏 720×1280", 720, 1280),
        CUSTOM("自定义分辨率", 1920, 1080),
    }

    data class Settings(
        val selectedOnly: Boolean = false,
        val sourceAction: SourceAction = SourceAction.COMMENT_HIDE,
        val languageMode: LanguageMode = LanguageMode.SINGLE,
        val clearPrevious: Boolean = true,
        val stripTags: Boolean = true,
        val preserveLineBreaks: Boolean = true,
        val animate: Boolean = true,
        val resolutionPreset: ResolutionPreset = ResolutionPreset.FHD_1080P,
        val customWidth: Int = 1920,
        val customHeight: Int = 1080,
        val horizontalAlign: HorizontalAlign = HorizontalAlign.CENTER,
        val centerX: Int = 0, // 0 = 自动居中/边距计算
        val centerY: Int = 0, // 0 = 自动居中
        val lineGap: Int = 0, // 0 = 按分辨率自适应
        val activeFontSize: Int = 0, // 0 = 按分辨率自适应
        val inactiveFontSize: Int = 0,
        val visibleNeighborLines: Int = 3,
        val transitionMs: Int = 550,
        val activeAlpha: Int = 0x00,   // ASS alpha: 00 = 不透明
        val inactiveAlpha: Int = 0x58, // ASS alpha: 58 = 半透暗化
        val layer: Int = 4,
        val outlineSize: Int = 3,
        val shadowSize: Int = 1,
        val activeColor: AssColor = AssColor.WHITE,
        val inactiveColor: AssColor = AssColor(216, 216, 216),
        val outlineColor: AssColor = AssColor.BLACK,
        val primaryFont: String = "sans-serif",
        val secondaryFont: String = "sans-serif",
        val motionSteps: Int = 8,
    )

    private data class LyricRow(
        val startMs: Long,
        var endMs: Long,
        val primary: String,
        val secondary: String,
        val sourceEvent: AssEvent,
    )

    data class Result(
        val script: AssScript,
        val generatedCount: Int,
        val sourceRowCount: Int,
    )

    private fun resolveTargetResolution(script: AssScript, settings: Settings): Pair<Int, Int> {
        val scriptW = script.getScriptInfo("PlayResX")?.toIntOrNull()?.takeIf { it > 0 } ?: 1920
        val scriptH = script.getScriptInfo("PlayResY")?.toIntOrNull()?.takeIf { it > 0 } ?: 1080
        return when (settings.resolutionPreset) {
            ResolutionPreset.SCRIPT_PLAYRES -> scriptW to scriptH
            ResolutionPreset.CUSTOM -> settings.customWidth.coerceAtLeast(320) to settings.customHeight.coerceAtLeast(240)
            else -> settings.resolutionPreset.width to settings.resolutionPreset.height
        }
    }

    private fun assAlignment(align: HorizontalAlign): Int = when (align) {
        HorizontalAlign.LEFT -> 4
        HorizontalAlign.CENTER -> 5
        HorizontalAlign.RIGHT -> 6
    }

    private fun cleanLyricText(event: AssEvent, settings: Settings): String {
        val raw = if (settings.stripTags) event.strippedText else event.text
        return if (settings.preserveLineBreaks) {
            raw.trim()
        } else {
            raw.replace("\\N", " ").replace("\\n", " ").trim()
        }
    }

    private fun easeOutCubic(v: Double): Double {
        val clamped = v.coerceIn(0.0, 1.0)
        return 1.0 - (1.0 - clamped).pow(3.0)
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun interpolatePoints(points: List<Pair<Double, Double>>, offset: Double): Double {
        val v = abs(offset)
        if (v <= points.first().first) return points.first().second
        for (i in 1 until points.size) {
            val (x0, y0) = points[i - 1]
            val (x1, y1) = points[i]
            if (v <= x1) return lerp(y0, y1, (v - x0) / (x1 - x0))
        }
        return points.last().second
    }

    private fun alphaForOffset(offset: Double, settings: Settings): Int =
        interpolatePoints(
            listOf(
                0.0 to settings.activeAlpha.toDouble(),
                1.0 to settings.inactiveAlpha.toDouble(),
                2.0 to 0x92.toDouble(),
                3.0 to 0xC0.toDouble(),
                4.0 to 0xE2.toDouble(),
            ),
            offset,
        ).roundToInt().coerceIn(0, 255)

    private fun scaleForOffset(offset: Double): Double =
        interpolatePoints(
            listOf(0.0 to 100.0, 1.0 to 78.0, 2.0 to 65.0, 3.0 to 55.0, 4.0 to 50.0),
            offset,
        )

    private fun colorTagForOffset(offset: Double, settings: Settings): String {
        val t = abs(offset).coerceIn(0.0, 1.0)
        val r = lerp(settings.activeColor.r.toDouble(), settings.inactiveColor.r.toDouble(), t).roundToInt().coerceIn(0, 255)
        val g = lerp(settings.activeColor.g.toDouble(), settings.inactiveColor.g.toDouble(), t).roundToInt().coerceIn(0, 255)
        val b = lerp(settings.activeColor.b.toDouble(), settings.inactiveColor.b.toDouble(), t).roundToInt().coerceIn(0, 255)
        return AssColor(r, g, b).toBgrTagString()
    }

    private fun wrapByUnits(text: String, maxUnits: Int): String {
        if (maxUnits <= 0 || text.isEmpty()) return text
        val sb = StringBuilder(text.length + 8)
        var units = 0.0
        var i = 0
        while (i < text.length) {
            if (text.startsWith("\\N", i)) {
                sb.append("\\N")
                units = 0.0
                i += 2
                continue
            }
            val ch = text[i]
            val w = when {
                ch.isWhitespace() -> 0.35
                ch.code < 0x80 -> 0.58
                else -> 1.0
            }
            if (units + w > maxUnits && units > 0.0) {
                sb.append("\\N")
                units = 0.0
            }
            sb.append(ch)
            units += w
            i++
        }
        return sb.toString()
    }

    private fun countAssLines(text: String): Int =
        if (text.isEmpty()) 0 else text.split("\\N").size

    fun generate(
        script: AssScript,
        settings: Settings,
        selectedIds: Set<Long> = emptySet(),
    ): Result {
        val baseEvents = if (settings.clearPrevious) {
            script.events.filterNot { it.effect == EFFECT_GENERATED }
        } else {
            script.events
        }

        val sourceCandidates = baseEvents.filter { e ->
            val eligible = (!e.comment || e.effect == EFFECT_SOURCE) && e.effect != EFFECT_GENERATED
            val inScope = !settings.selectedOnly || selectedIds.isEmpty() || e.id in selectedIds
            eligible && inScope && cleanLyricText(e, settings).isNotEmpty()
        }.sortedBy { it.start.micros }

        if (sourceCandidates.isEmpty()) {
            return Result(script.withEvents(baseEvents), 0, 0)
        }

        val rows = mutableListOf<LyricRow>()
        if (settings.languageMode == LanguageMode.BILINGUAL) {
            var i = 0
            while (i < sourceCandidates.size) {
                val cur = sourceCandidates[i]
                val curText = cleanLyricText(cur, settings)
                val nxt = sourceCandidates.getOrNull(i + 1)
                if (nxt != null && abs(nxt.start.millis - cur.start.millis) <= 120L) {
                    rows += LyricRow(cur.start.millis, maxOf(cur.end.millis, nxt.end.millis), curText, cleanLyricText(nxt, settings), cur)
                    i += 2
                } else if (curText.contains("\\N")) {
                    val parts = curText.split("\\N", limit = 2)
                    rows += LyricRow(cur.start.millis, cur.end.millis, parts[0].trim(), parts[1].trim(), cur)
                    i++
                } else {
                    rows += LyricRow(cur.start.millis, cur.end.millis, curText, "", cur)
                    i++
                }
            }
        } else {
            for (e in sourceCandidates) {
                rows += LyricRow(e.start.millis, e.end.millis, cleanLyricText(e, settings), "", e)
            }
        }

        for (idx in rows.indices) {
            val nextStart = rows.getOrNull(idx + 1)?.startMs
            if (rows[idx].endMs <= rows[idx].startMs) {
                rows[idx].endMs = nextStart ?: (rows[idx].startMs + 3_500L)
            }
        }

        val (targetW, targetH) = resolveTargetResolution(script, settings)
        val portrait = targetH > targetW
        val scaleRef = minOf(targetW, targetH) / 1080.0
        val activeSize = if (settings.activeFontSize > 0) settings.activeFontSize else (76.0 * scaleRef).roundToInt().coerceAtLeast(24)
        val inactiveSize = if (settings.inactiveFontSize > 0) settings.inactiveFontSize else (56.0 * scaleRef).roundToInt().coerceAtLeast(18)
        val lineGap = if (settings.lineGap > 0) settings.lineGap else (activeSize * 1.42).roundToInt()
        val marginLr = (targetW * if (portrait) 0.08 else 0.12).roundToInt()
        val anchorX = when {
            settings.centerX > 0 -> settings.centerX
            settings.horizontalAlign == HorizontalAlign.LEFT -> marginLr
            settings.horizontalAlign == HorizontalAlign.RIGHT -> targetW - marginLr
            else -> targetW / 2
        }
        val centerY = if (settings.centerY > 0) settings.centerY else targetH / 2
        val align = assAlignment(settings.horizontalAlign)

        val regionWidth = if (portrait) targetW * 0.84 else targetW * 0.72
        val primaryWrapUnits = floor(regionWidth / (activeSize * 0.48).coerceAtLeast(1.0)).toInt().coerceAtLeast(8)
        val secondarySizeActive = (activeSize * 0.56).roundToInt().coerceAtLeast(12)
        val secondarySizeDim = (inactiveSize * 0.56).roundToInt().coerceAtLeast(10)
        val secondaryWrapUnits = floor(regionWidth / (secondarySizeActive * 0.48).coerceAtLeast(1.0)).toInt().coerceAtLeast(10)
        val rowPadding = (lineGap * 0.18).coerceAtLeast(12.0)

        fun rowVisualHeight(row: LyricRow, current: Boolean): Double {
            val pSize = if (current) activeSize else inactiveSize
            val sSize = if (current) secondarySizeActive else secondarySizeDim
            val pLines = countAssLines(wrapByUnits(row.primary, primaryWrapUnits)).coerceAtLeast(1)
            val sLines = if (row.secondary.isEmpty()) 0 else countAssLines(wrapByUnits(row.secondary, secondaryWrapUnits)).coerceAtLeast(1)
            var h = pLines * pSize * 1.08
            if (sLines > 0) h += pSize * 0.18 + sLines * sSize * 1.05
            return h + settings.outlineSize * 2.0
        }

        fun rowCenterY(activeIndex: Int, rowIndex: Int): Double {
            if (rowIndex == activeIndex) return centerY.toDouble()
            var y = centerY.toDouble()
            var prevH = rowVisualHeight(rows[activeIndex], true)
            if (rowIndex > activeIndex) {
                for (i in activeIndex + 1..rowIndex) {
                    val h = rowVisualHeight(rows[i], false)
                    y += maxOf(lineGap.toDouble(), prevH / 2.0 + h / 2.0 + rowPadding)
                    prevH = h
                }
            } else {
                for (i in activeIndex - 1 downTo rowIndex) {
                    val h = rowVisualHeight(rows[i], false)
                    y -= maxOf(lineGap.toDouble(), prevH / 2.0 + h / 2.0 + rowPadding)
                    prevH = h
                }
            }
            return y
        }

        fun buildBody(row: LyricRow, current: Boolean): String {
            val pWrapped = wrapByUnits(row.primary, primaryWrapUnits)
            val sWrapped = wrapByUnits(row.secondary, secondaryWrapUnits)
            val blur = if (current) "0.5" else "1"
            return buildString {
                append("{\\an$align\\blur$blur\\fsp0\\fn${settings.primaryFont}}$pWrapped")
                if (sWrapped.isNotEmpty()) {
                    val sSize = if (current) secondarySizeActive else secondarySizeDim
                    val sAlpha = if (current) "42" else "78"
                    append("\\N{\\fn${settings.secondaryFont}\\fs$sSize\\alpha&H$sAlpha&}$sWrapped")
                }
            }
        }

        var nextId = (baseEvents.maxOfOrNull { it.id } ?: 0L) + 1L
        val generatedEvents = mutableListOf<AssEvent>()
        val window = settings.visibleNeighborLines.coerceIn(1, 8)
        val steps = settings.motionSteps.coerceIn(1, 16)

        for (activeIdx in rows.indices) {
            val row = rows[activeIdx]
            val segStart = row.startMs
            val segEnd = (rows.getOrNull(activeIdx + 1)?.startMs ?: row.endMs).coerceAtLeast(segStart + 250L)
            val transEnd = if (settings.animate && activeIdx > 0 && settings.transitionMs > 0) {
                minOf(segEnd, segStart + settings.transitionMs)
            } else {
                segStart
            }

            if (transEnd > segStart) {
                val dur = (transEnd - segStart).toInt()
                val actualSteps = minOf(steps, (dur / 25).coerceAtLeast(1))
                var prevMs = segStart
                for (step in 0 until actualSteps) {
                    val curMs = segStart + ((dur * (step + 1.0)) / actualSteps).roundToInt()
                    if (curMs <= prevMs) continue
                    val p0 = easeOutCubic(step.toDouble() / actualSteps)
                    val p1 = easeOutCubic((step + 1.0) / actualSteps)
                    val rStart = (activeIdx - window - 1).coerceAtLeast(0)
                    val rEnd = (activeIdx + window + 1).coerceAtMost(rows.lastIndex)
                    for (rIdx in rStart..rEnd) {
                        val targetOffset = rIdx - activeIdx
                        val prevActive = activeIdx - 1
                        val prevOffset = rIdx - prevActive
                        val wasVisible = prevActive >= 0 && abs(prevOffset) <= window
                        val isVisible = abs(targetOffset) <= window
                        if (!wasVisible && !isVisible) continue

                        val visTargetY = rowCenterY(activeIdx, rIdx)
                        val startY = if (wasVisible) rowCenterY(prevActive, rIdx) else visTargetY + if (targetOffset >= 0) lineGap else -lineGap
                        val endY = if (isVisible) visTargetY else startY + if (targetOffset < prevOffset) -lineGap else lineGap
                        val sOff = if (wasVisible) prevOffset else targetOffset + if (targetOffset >= 0) 1 else -1
                        val eOff = if (isVisible) targetOffset else prevOffset + if (targetOffset < prevOffset) -1 else 1
                        val fOff0 = lerp(sOff.toDouble(), targetOffset.toDouble(), p0)
                        val fOff1 = lerp(sOff.toDouble(), eOff.toDouble(), p1)
                        val isCurrent = isVisible && targetOffset == 0
                        val y0 = lerp(startY, endY, p0).roundToInt()
                        val y1 = lerp(startY, endY, p1).roundToInt()
                        val sc0 = String.format(Locale.US, "%.1f", scaleForOffset(fOff0))
                        val sc1 = String.format(Locale.US, "%.1f", scaleForOffset(fOff1))
                        val a0 = "%02X".format(alphaForOffset(fOff0, settings))
                        val a1 = "%02X".format(alphaForOffset(fOff1, settings))
                        val c0 = colorTagForOffset(fOff0, settings)
                        val c1 = colorTagForOffset(fOff1, settings)
                        val spanDur = (curMs - prevMs).coerceAtLeast(1L)
                        val header = "{\\an$align\\q2\\move($anchorX,$y0,$anchorX,$y1)\\alpha&H$a0&\\1c$c0\\fscx$sc0\\fscy$sc0" +
                            "\\t(0,$spanDur,1.0,\\alpha&H$a1&\\1c$c1\\fscx$sc1\\fscy$sc1)}"
                        generatedEvents += AssEvent(
                            id = nextId++,
                            comment = false,
                            layer = if (isCurrent) settings.layer else (settings.layer - 1).coerceAtLeast(0),
                            start = SubTime.ofMillis(prevMs),
                            end = SubTime.ofMillis(curMs),
                            style = if (isCurrent) "ScrollCurrent" else "ScrollDim",
                            effect = EFFECT_GENERATED,
                            text = header + buildBody(rows[rIdx], isCurrent),
                        )
                    }
                    prevMs = curMs
                }
            }

            val holdStart = if (transEnd > segStart) transEnd else segStart
            if (holdStart < segEnd) {
                val vStart = (activeIdx - window).coerceAtLeast(0)
                val vEnd = (activeIdx + window).coerceAtMost(rows.lastIndex)
                for (rIdx in vStart..vEnd) {
                    val offset = rIdx - activeIdx
                    val isCurrent = offset == 0
                    val y = rowCenterY(activeIdx, rIdx).roundToInt()
                    val sc = String.format(Locale.US, "%.1f", scaleForOffset(offset.toDouble()))
                    val aHex = "%02X".format(alphaForOffset(offset.toDouble(), settings))
                    val cTag = colorTagForOffset(offset.toDouble(), settings)
                    val header = "{\\an$align\\q2\\pos($anchorX,$y)\\alpha&H$aHex&\\1c$cTag\\fscx$sc\\fscy$sc}"
                    generatedEvents += AssEvent(
                        id = nextId++,
                        comment = false,
                        layer = if (isCurrent) settings.layer else (settings.layer - 1).coerceAtLeast(0),
                        start = SubTime.ofMillis(holdStart),
                        end = SubTime.ofMillis(segEnd),
                        style = if (isCurrent) "ScrollCurrent" else "ScrollDim",
                        effect = EFFECT_GENERATED,
                        text = header + buildBody(rows[rIdx], isCurrent),
                    )
                }
            }
        }

        val sourceIdSet = sourceCandidates.map { it.id }.toSet()
        val processedBase = when (settings.sourceAction) {
            SourceAction.KEEP -> baseEvents.map {
                if (it.id in sourceIdSet && it.effect == EFFECT_SOURCE) it.copy(comment = false, effect = "") else it
            }
            SourceAction.COMMENT_HIDE -> baseEvents.map {
                if (it.id in sourceIdSet) it.copy(comment = true, effect = EFFECT_SOURCE) else it
            }
            SourceAction.DELETE -> baseEvents.filterNot { it.id in sourceIdSet }
        }

        val currentStyle = AssStyle(
            name = "ScrollCurrent",
            font = settings.primaryFont,
            fontSize = activeSize.toDouble(),
            primary = settings.activeColor,
            outline = settings.outlineColor,
            shadow = AssColor.BLACK,
            bold = true,
            outlineWidth = settings.outlineSize.toDouble(),
            shadowWidth = settings.shadowSize.toDouble(),
            alignment = align,
            margins = Margins(marginLr, marginLr, 0),
        )
        val dimStyle = currentStyle.copy(
            name = "ScrollDim",
            fontSize = inactiveSize.toDouble(),
            primary = settings.inactiveColor,
            bold = false,
        )

        val updatedStyles = script.styles
            .filterNot { it.name == "ScrollCurrent" || it.name == "ScrollDim" } +
            listOf(currentStyle, dimStyle)

        var updatedInfo = script.info.toList()
        updatedInfo = ScriptInfoOps.set(updatedInfo, "PlayResX", targetW.toString())
        updatedInfo = ScriptInfoOps.set(updatedInfo, "PlayResY", targetH.toString())
        updatedInfo = ScriptInfoOps.set(updatedInfo, "WrapStyle", "2")

        val outScript = script
            .withInfo(updatedInfo)
            .withStyles(updatedStyles)
            .withEvents(processedBase + generatedEvents)

        return Result(
            script = outScript,
            generatedCount = generatedEvents.size,
            sourceRowCount = rows.size,
        )
    }
}
