package io.github.samgum.aegisub.domain.sgmy

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.DialogueBlock
import kotlin.math.max

/**
 * 字幕溢出检测器（复刻 samgum/Aegisub SGMY `subtitle_overflow.cpp`）：
 * 检测渲染宽度超出脚本画幅（`PlayResX` − 有效左右边距）或 `\pos`/`\move` 锚点越界的行，
 * 供字幕网格与编辑框实时高亮警示。
 *
 * @author 伤感咩吖
 */
object SubtitleOverflowDetector {

    data class OverflowResult(
        val isOverflowing: Boolean,
        /** 最宽单行占可用画幅宽度的比例（> 1.0 即单行超出画幅）。 */
        val widthRatio: Double,
        /** 左边界越出画幅的脚本像素（> 0 表示左侧越界）。 */
        val leftOverflowUnits: Double = 0.0,
        /** 右边界越出画幅的脚本像素（> 0 表示右侧越界）。 */
        val rightOverflowUnits: Double = 0.0,
        /** CPS 过高提醒（默认 > 20 字/秒）。 */
        val isHighCps: Boolean = false,
    )

    private val NUM_PAIR_RE = Regex("""\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""")
    private val MOVE_RE = Regex("""\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""")

    fun evaluate(
        event: AssEvent,
        script: AssScript,
        cpsThreshold: Int = 22,
    ): OverflowResult {
        if (event.comment) return OverflowResult(false, 0.0)
        val playResX = script.getScriptInfo("PlayResX")?.toIntOrNull()?.takeIf { it > 0 } ?: 384
        val wrapStyle = script.getScriptInfo("WrapStyle")?.toIntOrNull() ?: 0
        val baseStyle = script.styles.firstOrNull { it.name == event.style }
            ?: script.styles.firstOrNull { it.name == "Default" }
            ?: script.styles.firstOrNull()
            ?: AssStyle()

        var fontSize = baseStyle.fontSize
        var scaleX = baseStyle.scaleX
        var spacing = baseStyle.spacing
        var alignment = baseStyle.alignment
        var positioned = false
        var posX1 = 0.0
        var posX2 = 0.0
        var hasSecondaryX = false
        var drawingScale = 0

        val blocks = DialogueBlock.parse(event.text)
        val lineWidths = mutableListOf(0.0)

        for (block in blocks) {
            when (block) {
                is DialogueBlock.Override -> {
                    for (tag in block.tags) {
                        val raw = tag.rawValue.trim()
                        when (tag.name) {
                            "pos" -> {
                                val m = NUM_PAIR_RE.find(raw)
                                if (m != null) {
                                    positioned = true
                                    posX1 = m.groupValues[1].toDoubleOrNull() ?: posX1
                                    posX2 = posX1
                                    hasSecondaryX = false
                                }
                            }
                            "move" -> {
                                val m = MOVE_RE.find(raw)
                                if (m != null) {
                                    positioned = true
                                    posX1 = m.groupValues[1].toDoubleOrNull() ?: posX1
                                    posX2 = m.groupValues[3].toDoubleOrNull() ?: posX1
                                    hasSecondaryX = true
                                }
                            }
                            "an" -> {
                                val a = raw.toIntOrNull()
                                if (a != null && a in 1..9) alignment = a
                            }
                            "a" -> {
                                val ssa = raw.toIntOrNull()
                                if (ssa != null) alignment = AssStyle.ssaToAss(ssa)
                            }
                            "fs" -> {
                                if (raw.startsWith("+") || raw.startsWith("-")) {
                                    val delta = raw.toDoubleOrNull() ?: 0.0
                                    fontSize = (fontSize + delta).coerceAtLeast(1.0)
                                } else {
                                    fontSize = (raw.toDoubleOrNull() ?: fontSize).coerceAtLeast(1.0)
                                }
                            }
                            "fscx" -> scaleX = (raw.toDoubleOrNull() ?: scaleX).coerceAtLeast(0.0)
                            "fsp" -> spacing = raw.toDoubleOrNull() ?: spacing
                            "p" -> drawingScale = (raw.toIntOrNull() ?: 0).coerceAtLeast(0)
                            "r" -> {
                                val resetStyle = if (raw.isEmpty()) baseStyle
                                else script.styles.firstOrNull { it.name == raw } ?: baseStyle
                                fontSize = resetStyle.fontSize
                                scaleX = resetStyle.scaleX
                                spacing = resetStyle.spacing
                            }
                        }
                    }
                }
                is DialogueBlock.Plain -> {
                    if (drawingScale > 0) continue
                    var i = 0
                    val s = block.text
                    while (i < s.length) {
                        if (s.startsWith("\\N", i) || (s.startsWith("\\n", i) && wrapStyle == 2)) {
                            lineWidths += 0.0
                            i += 2
                            continue
                        }
                        if (s.startsWith("\\h", i)) {
                            val advance = (fontSize * 0.50 + spacing) * (scaleX / 100.0)
                            lineWidths[lineWidths.lastIndex] = lineWidths.last() + advance
                            i += 2
                            continue
                        }
                        val cp = s.codePointAt(i)
                        val charCount = Character.charCount(cp)
                        val glyphFactor = when {
                            cp == ' '.code -> 0.30
                            cp < 0x80 -> if (cp in '0'.code..'9'.code || cp in 'A'.code..'Z'.code) 0.58 else 0.50
                            cp in 0x3000..0x9FFF || cp in 0xF900..0xFAFF || cp in 0xFF01..0xFF60 || cp >= 0x20000 -> 0.94
                            cp in 0xAC00..0xD7AF -> 0.90
                            else -> 0.62
                        }
                        val charW = (fontSize * glyphFactor + spacing) * (scaleX / 100.0)
                        lineWidths[lineWidths.lastIndex] = lineWidths.last() + max(0.0, charW)
                        i += charCount
                    }
                }
                else -> Unit
            }
        }

        val marginL = if (event.margins.left != 0) event.margins.left else baseStyle.margins.left
        val marginR = if (event.margins.right != 0) event.margins.right else baseStyle.margins.right
        val availableWidth = if (positioned) {
            playResX.toDouble()
        } else {
            (playResX - marginL - marginR).coerceAtLeast(1).toDouble()
        }

        val maxLineW = lineWidths.maxOrNull() ?: 0.0
        val ratio = maxLineW / availableWidth
        val hor = (alignment - 1) % 3 // 0=left, 1=center, 2=right

        fun checkBoundsAt(anchorX: Double, wrapLeft: Double, wrapRight: Double): Pair<Double, Double> {
            val left = when (hor) {
                0 -> anchorX
                1 -> anchorX - maxLineW / 2.0
                else -> anchorX - maxLineW
            }
            val right = left + maxLineW
            val leftOver = (wrapLeft - left).coerceAtLeast(0.0)
            val rightOver = (right - wrapRight).coerceAtLeast(0.0)
            return leftOver to rightOver
        }

        var leftOverflow: Double
        var rightOverflow: Double
        if (positioned) {
            val (l1, r1) = checkBoundsAt(posX1, 0.0, playResX.toDouble())
            leftOverflow = l1
            rightOverflow = r1
            if (hasSecondaryX) {
                val (l2, r2) = checkBoundsAt(posX2, 0.0, playResX.toDouble())
                leftOverflow = max(leftOverflow, l2)
                rightOverflow = max(rightOverflow, r2)
            }
        } else {
            val anchorX = when (hor) {
                0 -> marginL.toDouble()
                1 -> (playResX + marginL - marginR) / 2.0
                else -> (playResX - marginR).toDouble()
            }
            val (l, r) = checkBoundsAt(anchorX, marginL.toDouble(), (playResX - marginR).toDouble())
            // 非定位行在自动换行开启时允许折成 2 行，但超过 2 行或单行强制不换行（WrapStyle=2）时标记溢出
            if (wrapStyle == 2) {
                leftOverflow = l
                rightOverflow = r
            } else {
                val explicitOverflow = lineWidths.size >= 2 && maxLineW > availableWidth * 1.03
                val totalOverTwoLines = lineWidths.sum() > availableWidth * 2.05
                leftOverflow = if (explicitOverflow || totalOverTwoLines) max(l, maxLineW - availableWidth) else 0.0
                rightOverflow = if (explicitOverflow || totalOverTwoLines) max(r, maxLineW - availableWidth) else 0.0
            }
        }

        val isOverflow = leftOverflow > 1.5 || rightOverflow > 1.5
        val highCps = event.cps > cpsThreshold
        return OverflowResult(
            isOverflowing = isOverflow,
            widthRatio = ratio,
            leftOverflowUnits = leftOverflow,
            rightOverflowUnits = rightOverflow,
            isHighCps = highCps,
        )
    }
}
