package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import io.github.samgum.aegisub.domain.preview.SubtitleRenderInfo

/**
 * 高保真 ASS 视频画面字幕渲染叠加层（重做版）：
 * - 按 ASS 标准图层升序绘制（高层覆盖低层）
 * - 完整支持 `\an1..9` 九宫锚点（无论是默认边距还是 `\pos` / `\move` 动态锚点）
 * - 实时支持 `\move` 动画位移、`\fad` / `\fade` 透明度乘子、`\frz` 旋转（绕 `\org` 或锚点）、`\fscx` / `\fscy` 缩放
 * - 实时支持 `\clip` / `\iclip`（矩形裁剪与 `m ... l ...` 矢量多边形正反裁剪）
 * - 实时支持行内分段变色（`\1c`）、真阴影色（`\4c`）、真描边色（`\3c`）以及 **卡拉OK `\kf` 逐音节渐进变色扫词**
 * - 不再强行把 `\pos`/`\move` 钳死在画框内，使 SGMY 滚动歌词能自然滑入滑出画幅
 *
 * @author 伤感咩吖
 */
@Composable
fun SubtitleOverlay(
    renderInfos: List<SubtitleRenderInfo>,
    modifier: Modifier = Modifier,
) {
    if (renderInfos.isEmpty()) {
        Box(modifier.fillMaxSize())
        return
    }
    val measurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.width <= 1f || size.height <= 1f) return@Canvas

        // 若输入按 layer 降序（兼容旧 VM），反转为升序使高层绘制在最上方；若已升序则按 layer 稳定升序
        val ordered = renderInfos.sortedBy { it.layer }

        ordered.forEach { info ->
            val rawVisible = info.text.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", "\u00A0")
            if (rawVisible.isBlank() || info.opacityMultiplier <= 0.005f) return@forEach

            val style = info.style
            val scaleX = size.width / info.playResX.coerceAtLeast(1).toFloat()
            val scaleY = size.height / info.playResY.coerceAtLeast(1).toFloat()

            val baseFontPx = (style.fontSize.toFloat() * scaleY).coerceIn(4f, size.height * 0.35f)
            val outlinePx = (style.outlineWidth.toFloat() * scaleY).coerceIn(0f, baseFontPx * 0.18f)
            val shadowPx = (style.shadowWidth.toFloat() * scaleY).coerceIn(0f, baseFontPx * 0.25f)

            val marginLeftPx = info.margins.left * scaleX
            val marginRightPx = info.margins.right * scaleX
            val marginVertPx = info.margins.vertical * scaleY

            val textAlign = when ((info.alignment - 1) % 3) {
                0 -> TextAlign.Left
                2 -> TextAlign.Right
                else -> TextAlign.Center
            }

            val baseTextStyle = TextStyle(
                color = style.primary.toColor(info.opacityMultiplier),
                fontSize = (baseFontPx / (density * fontScale)).sp,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = resolveComposeFontFamily(style.font),
                textAlign = textAlign,
            )

            val fillAnnotated = buildStyledAnnotatedString(info, scaleY, density * fontScale, mode = RenderPass.FILL)
            val outlineAnnotated = buildStyledAnnotatedString(info, scaleY, density * fontScale, mode = RenderPass.OUTLINE)
            val shadowAnnotated = buildStyledAnnotatedString(info, scaleY, density * fontScale, mode = RenderPass.SHADOW)

            val maxWidthPx = if (info.effectivePos != null) {
                (size.width * 1.5f).toInt().coerceAtLeast(1)
            } else {
                (size.width - marginLeftPx - marginRightPx).coerceAtLeast(16f).toInt()
            }

            val fillLayout = measurer.measure(
                text = fillAnnotated,
                style = baseTextStyle,
                constraints = Constraints(maxWidth = maxWidthPx),
                overflow = TextOverflow.Visible,
            )
            val outlineLayout = measurer.measure(
                text = outlineAnnotated,
                style = baseTextStyle,
                constraints = Constraints(maxWidth = maxWidthPx),
                overflow = TextOverflow.Visible,
            )
            val shadowLayout = measurer.measure(
                text = shadowAnnotated,
                style = baseTextStyle,
                constraints = Constraints(maxWidth = maxWidthPx),
                overflow = TextOverflow.Visible,
            )

            val anchorPx = computeAnchorPointPx(
                alignment = info.alignment,
                effectivePos = info.effectivePos,
                scaleX = scaleX,
                scaleY = scaleY,
                canvasWidth = size.width,
                canvasHeight = size.height,
                marginLeftPx = marginLeftPx,
                marginRightPx = marginRightPx,
                marginVerticalPx = marginVertPx,
            )

            val topLeft = computeTopLeftFromAnchor(
                alignment = info.alignment,
                anchor = anchorPx,
                layoutWidth = fillLayout.size.width.toFloat(),
                layoutHeight = fillLayout.size.height.toFloat(),
            )

            val pivotPx = info.origin?.let { Offset(it.first * scaleX, it.second * scaleY) } ?: anchorPx
            val sx = (info.scaleX / 100f).coerceIn(0.05f, 10f)
            val sy = (info.scaleY / 100f).coerceIn(0.05f, 10f)

            val drawSubtitleContent: DrawScope.() -> Unit = {
                withTransform({
                    if (sx != 1f || sy != 1f) {
                        scale(scaleX = sx, scaleY = sy, pivot = anchorPx)
                    }
                    if (info.rotationZ != 0f) {
                        // ASS \frz 正方向为逆时针，Canvas rotate 正方向为顺时针，故取负号对齐桌面 libass
                        rotate(degrees = -info.rotationZ, pivot = pivotPx)
                    }
                }) {
                    if (shadowPx > 0.2f) {
                        drawText(
                            textLayoutResult = shadowLayout,
                            topLeft = Offset(topLeft.x + shadowPx, topLeft.y + shadowPx),
                        )
                    }
                    if (outlinePx > 0.2f) {
                        drawText(
                            textLayoutResult = outlineLayout,
                            topLeft = topLeft,
                            drawStyle = Stroke(width = outlinePx * 1.8f),
                        )
                    }
                    drawText(
                        textLayoutResult = fillLayout,
                        topLeft = topLeft,
                    )
                }
            }

            // 应用矩形或矢量 \clip / \iclip
            val vec = info.vectorClip
            val rect = info.clipRect
            when {
                vec != null && vec.points.size >= 3 -> {
                    val div = (1 shl (vec.scale - 1).coerceIn(0, 8)).toFloat()
                    val path = Path().apply {
                        val p0 = vec.points.first()
                        moveTo((p0.x / div) * scaleX, (p0.y / div) * scaleY)
                        for (idx in 1 until vec.points.size) {
                            val pt = vec.points[idx]
                            lineTo((pt.x / div) * scaleX, (pt.y / div) * scaleY)
                        }
                        close()
                    }
                    clipPath(path, clipOp = if (vec.inverse) ClipOp.Difference else ClipOp.Intersect) {
                        drawSubtitleContent()
                    }
                }
                rect != null -> {
                    val left = minOf(rect.x1, rect.x2) * scaleX
                    val top = minOf(rect.y1, rect.y2) * scaleY
                    val right = maxOf(rect.x1, rect.x2) * scaleX
                    val bottom = maxOf(rect.y1, rect.y2) * scaleY
                    clipRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        clipOp = if (rect.inverse) ClipOp.Difference else ClipOp.Intersect,
                    ) {
                        drawSubtitleContent()
                    }
                }
                else -> drawSubtitleContent()
            }
        }
    }
}

private enum class RenderPass { FILL, OUTLINE, SHADOW }

private fun buildStyledAnnotatedString(
    info: SubtitleRenderInfo,
    scaleY: Float,
    spDivisor: Float,
    mode: RenderPass,
): AnnotatedString {
    if (info.spans.isEmpty()) {
        val clean = info.text.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", "\u00A0")
        val c = when (mode) {
            RenderPass.FILL -> info.style.primary.toColor(info.opacityMultiplier)
            RenderPass.OUTLINE -> info.style.outline.toColor(info.opacityMultiplier)
            RenderPass.SHADOW -> info.style.shadow.toColor(info.opacityMultiplier * 0.78f)
        }
        return AnnotatedString(clean, SpanStyle(color = c))
    }

    return buildAnnotatedString {
        for (span in info.spans) {
            val clean = span.text.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", "\u00A0")
            if (clean.isEmpty()) continue
            val fsPx = (span.fontSize.toFloat() * scaleY).coerceAtLeast(4f)
            val deco = when {
                span.underline && span.strikeout -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                span.underline -> TextDecoration.Underline
                span.strikeout -> TextDecoration.LineThrough
                else -> TextDecoration.None
            }
            val baseSpanStyle = SpanStyle(
                fontSize = (fsPx / spDivisor).sp,
                fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (span.italic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = resolveComposeFontFamily(span.font),
                textDecoration = deco,
            )

            when (mode) {
                RenderPass.OUTLINE -> {
                    withStyle(baseSpanStyle.copy(color = span.outlineColor.toColor(info.opacityMultiplier))) {
                        append(clean)
                    }
                }
                RenderPass.SHADOW -> {
                    withStyle(baseSpanStyle.copy(color = span.shadowColor.toColor(info.opacityMultiplier * 0.78f))) {
                        append(clean)
                    }
                }
                RenderPass.FILL -> {
                    val kProg = span.karaokeProgress
                    if (kProg != null && kProg in 0.001f..0.999f && clean.length > 1) {
                        val splitIdx = (clean.length * kProg).toInt().coerceIn(0, clean.length)
                        if (splitIdx > 0) {
                            withStyle(baseSpanStyle.copy(color = span.primaryColor.toColor(info.opacityMultiplier))) {
                                append(clean.substring(0, splitIdx))
                            }
                        }
                        if (splitIdx < clean.length) {
                            withStyle(baseSpanStyle.copy(color = span.secondaryColor.toColor(info.opacityMultiplier))) {
                                append(clean.substring(splitIdx))
                            }
                        }
                    } else {
                        val fillCol = if (kProg != null && kProg <= 0.001f) {
                            span.secondaryColor.toColor(info.opacityMultiplier)
                        } else {
                            span.primaryColor.toColor(info.opacityMultiplier)
                        }
                        withStyle(baseSpanStyle.copy(color = fillCol)) {
                            append(clean)
                        }
                    }
                }
            }
        }
    }
}

private fun resolveComposeFontFamily(name: String): FontFamily {
    val low = name.lowercase()
    return when {
        "serif" in low && "sans" !in low || "song" in low || "明" in low || "宋" in low || "楷" in low -> FontFamily.Serif
        "mono" in low || "consolas" in low || "courier" in low -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
}

private fun io.github.samgum.aegisub.domain.model.AssColor.toColor(alphaMultiplier: Float): Color =
    Color(
        red = r / 255f,
        green = g / 255f,
        blue = b / 255f,
        alpha = ((a / 255f) * alphaMultiplier).coerceIn(0f, 1f),
    )

private fun computeAnchorPointPx(
    alignment: Int,
    effectivePos: Pair<Float, Float>?,
    scaleX: Float,
    scaleY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    marginLeftPx: Float,
    marginRightPx: Float,
    marginVerticalPx: Float,
): Offset {
    if (effectivePos != null) {
        return Offset(effectivePos.first * scaleX, effectivePos.second * scaleY)
    }
    val x = when (alignment) {
        1, 4, 7 -> marginLeftPx
        3, 6, 9 -> canvasWidth - marginRightPx
        else -> (canvasWidth + marginLeftPx - marginRightPx) / 2f
    }
    val y = when (alignment) {
        7, 8, 9 -> marginVerticalPx
        4, 5, 6 -> canvasHeight / 2f
        else -> canvasHeight - marginVerticalPx
    }
    return Offset(x, y)
}

private fun computeTopLeftFromAnchor(
    alignment: Int,
    anchor: Offset,
    layoutWidth: Float,
    layoutHeight: Float,
): Offset {
    val tx = when (alignment) {
        1, 4, 7 -> anchor.x
        3, 6, 9 -> anchor.x - layoutWidth
        else -> anchor.x - layoutWidth / 2f
    }
    val ty = when (alignment) {
        7, 8, 9 -> anchor.y
        4, 5, 6 -> anchor.y - layoutHeight / 2f
        else -> anchor.y - layoutHeight
    }
    return Offset(tx, ty)
}
