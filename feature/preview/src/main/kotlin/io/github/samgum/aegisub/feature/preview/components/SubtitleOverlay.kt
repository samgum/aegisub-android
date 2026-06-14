package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import io.github.samgum.aegisub.domain.preview.SubtitleRenderInfo

/**
 * 视频画面字幕叠加（自绘渲染）。
 *
 * 关键：ASS 的字号/边距/描边都是**脚本分辨率（PlayResX/Y）单位**，必须先按
 * `canvasHeight / playResY` 缩放到屏幕像素，否则 48pt 会渲染成巨大的 48sp。
 * 本组件统一在像素层换算：字号 px = style.fontSize × scaleY 经 Density.toSp() 转 sp；
 * 边距/描边/阴影 px = 对应字段 × 缩放比；{\pos(x,y)} 锚点（脚本坐标）→ 屏幕坐标居中文本块；
 * 长文本按可用宽度（画布宽 − 左右边距）换行。无活动事件或空文本时透明占位。
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
        renderInfos.forEach { info ->
            if (info.text.isBlank()) return@forEach
            val style = info.style
            val scaleY = (size.height / info.playResY.coerceAtLeast(1)).toFloat()
            val scaleX = (size.width / info.playResX.coerceAtLeast(1)).toFloat()
            // 字号保守：按 PlayResY 缩放，但封顶到画布高 11%（多数底部字幕视觉占比），
            // 避免 PlayResY 失配（缺省 288 配 HD 内容）时字号爆炸。
            val rawFontPx = (style.fontSize * scaleY).toFloat()
            val fontPx = rawFontPx.coerceIn(1f, size.height * 0.11f)
            val fontScaleUsed = fontPx / (style.fontSize.coerceAtLeast(0.0001).toFloat())
            // 描边宽度直接用 outlineWidth*scale（不 *2），且不粗于字号 14%，确保填充主导、不"中空"
            val outlinePx = (style.outlineWidth * fontScaleUsed).toFloat()
                .coerceIn(0f, fontPx * 0.14f)
            val shadowPx = (style.shadowWidth * fontScaleUsed).toFloat()
                .coerceAtMost(fontPx * 0.3f)
            val marginLeftPx = info.margins.left * scaleX
            val marginRightPx = info.margins.right * scaleX
            val marginTopPx = info.margins.vertical * scaleY
            val baseStyle = TextStyle(
                color = style.primary.toColor(),
                fontSize = (fontPx / (density * fontScale)).sp,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
            )
            val maxWidthPx = (size.width - marginLeftPx - marginRightPx).coerceAtLeast(1f).toInt()
            val layout = measurer.measure(
                text = info.text,
                style = baseStyle,
                constraints = Constraints(maxWidth = maxWidthPx),
                overflow = TextOverflow.Visible,
            )
            val topLeft = computeTopLeft(
                alignment = style.alignment,
                pos = info.pos,
                scaleX = scaleX,
                scaleY = scaleY,
                layoutWidth = layout.size.width.toFloat(),
                layoutHeight = layout.size.height.toFloat(),
                canvasWidth = size.width,
                canvasHeight = size.height,
                marginLeftPx = marginLeftPx,
                marginRightPx = marginRightPx,
                marginVerticalPx = marginTopPx,
            )
            // 钳制到画面内：避免 {\pos} 锚点或 PlayResY 失配导致字幕跑出画面（顶部/底部）看不见。
            val maxTx = (size.width - layout.size.width).coerceAtLeast(0f)
            val maxTy = (size.height - layout.size.height).coerceAtLeast(0f)
            val drawAt = Offset(topLeft.x.coerceIn(0f, maxTx), topLeft.y.coerceIn(0f, maxTy))
            if (shadowPx > 0f) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(drawAt.x + shadowPx, drawAt.y + shadowPx),
                )
            }
            if (outlinePx > 0f) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = drawAt,
                    color = style.outline.toColor(),
                    drawStyle = Stroke(width = outlinePx),
                )
            }
            drawText(textLayoutResult = layout, topLeft = drawAt)
        }
    }
}

/**
 * 计算文本块左上角（屏幕像素）。有 {\pos} 时锚点居中文本块；否则按 \an 对齐 + 边距。
 * numpad：1/4/7 左、2/5/8 中、3/6/9 右；7/8/9 上、4/5/6 中、1/2/3 下。
 */
private fun DrawScope.computeTopLeft(
    alignment: Int,
    pos: Pair<Int, Int>?,
    scaleX: Float,
    scaleY: Float,
    layoutWidth: Float,
    layoutHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    marginLeftPx: Float,
    marginRightPx: Float,
    marginVerticalPx: Float,
): Offset {
    if (pos != null) {
        val ax = pos.first * scaleX
        val ay = pos.second * scaleY
        return Offset(ax - layoutWidth / 2f, ay - layoutHeight / 2f)
    }
    val x = when (alignment) {
        1, 4, 7 -> marginLeftPx
        3, 6, 9 -> canvasWidth - layoutWidth - marginRightPx
        else -> (canvasWidth - layoutWidth) / 2f
    }
    val y = when (alignment) {
        7, 8, 9 -> marginVerticalPx
        4, 5, 6 -> (canvasHeight - layoutHeight) / 2f
        else -> canvasHeight - layoutHeight - marginVerticalPx
    }
    return Offset(x, y)
}
