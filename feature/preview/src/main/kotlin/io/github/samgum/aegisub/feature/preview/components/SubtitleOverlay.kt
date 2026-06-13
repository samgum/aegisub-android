package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samgum.aegisub.domain.preview.SubtitleRenderInfo

/**
 * 视频画面字幕叠加（简化渲染）。
 * 当前时间无活动事件时透明；有则按样式（字体/字号/主色/粗斜体/真描边/对齐/边距）自绘。
 * PlayRes→视频分辨率缩放留 Phase 5，本阶段边距直接按 dp 计。
 *
 * @author 伤感咩吖
 */
@Composable
fun SubtitleOverlay(
    renderInfo: SubtitleRenderInfo?,
    modifier: Modifier = Modifier,
) {
    if (renderInfo == null || renderInfo.text.isBlank()) {
        // 无活动事件：透明占位（保留尺寸以覆盖视频区）
        Box(modifier.fillMaxSize())
        return
    }
    val info = renderInfo
    val measurer = rememberTextMeasurer()
    val style = info.style
    val baseTextStyle = TextStyle(
        color = style.primary.toColor(),
        fontSize = style.fontSize.toFloat().sp,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
    )
    // 仅在文本/样式变化时重测，避免每个位置 tick 重测
    val layout = remember(info.text, baseTextStyle) {
        measurer.measure(info.text, baseTextStyle)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val outlineWidthPx = style.outlineWidth.dp.toPx().coerceAtLeast(1f)
        val topLeft = computeTopLeft(
            alignment = style.alignment,
            layoutWidth = layout.size.width.toFloat(),
            layoutHeight = layout.size.height.toFloat(),
            canvasWidth = size.width,
            canvasHeight = size.height,
            marginLeft = info.margins.left.dp.toPx(),
            marginRight = info.margins.right.dp.toPx(),
            marginVertical = info.margins.vertical.dp.toPx(),
        )
        // 1) 描边层（Stroke）：先画，宽于填充
        drawText(
            textMeasurer = measurer,
            text = info.text,
            topLeft = topLeft,
            style = baseTextStyle.copy(color = style.outline.toColor(), drawStyle = Stroke(width = outlineWidthPx)),
        )
        // 2) 填充层（Fill）：覆盖描边中心
        drawText(
            textMeasurer = measurer,
            text = info.text,
            topLeft = topLeft,
            style = baseTextStyle.copy(drawStyle = Fill),
        )
    }
}

/**
 * 按 \an 1-9 对齐 + 边距计算文本左上角。numpad：1/4/7 左、2/5/8 中、3/6/9 右；
 * 7/8/9 上、4/5/6 中、1/2/3 下。
 */
private fun DrawScope.computeTopLeft(
    alignment: Int,
    layoutWidth: Float,
    layoutHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    marginLeft: Float,
    marginRight: Float,
    marginVertical: Float,
): Offset {
    val x = when (alignment) {
        1, 4, 7 -> marginLeft
        3, 6, 9 -> canvasWidth - layoutWidth - marginRight
        else -> (canvasWidth - layoutWidth) / 2f
    }
    val y = when (alignment) {
        7, 8, 9 -> marginVertical
        4, 5, 6 -> (canvasHeight - layoutHeight) / 2f
        else -> canvasHeight - layoutHeight - marginVertical
    }
    return Offset(x, y)
}
