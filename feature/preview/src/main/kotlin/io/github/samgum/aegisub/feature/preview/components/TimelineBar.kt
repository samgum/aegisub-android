package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.model.AssEvent
import kotlin.math.max

/**
 * 只读时间轴条：按视频时长把所有字幕行画成 mini 块，选中行高亮，播放头竖线随位置移动。
 * 不做拖拽（触屏精度单独攻，留后续）。
 *
 * @param events 全部字幕行。
 * @param selectedEventId 选中的编辑目标行 id（高亮）。
 * @param positionMs 当前播放位置。
 * @param durationMs 视频时长；<=0 时不绘制有意义的刻度。
 *
 * @author 伤感咩吖
 */
@Composable
fun TimelineBar(
    events: List<AssEvent>,
    selectedEventId: Long?,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val blockColor = Color(0xFF6B7280).copy(alpha = 0.55f)
    val selectedColor = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth().height(36.dp)) {
        val w = size.width
        val h = size.height
        val span = max(durationMs.toFloat(), 1f)

        // 底线
        drawLine(
            color = trackColor,
            start = Offset(0f, h / 2f),
            end = Offset(w, h / 2f),
            strokeWidth = 1f,
        )

        // 各行 mini 块（错开两行避免重叠遮挡）
        events.forEachIndexed { i, e ->
            val left = (e.start.millis / span) * w
            val right = (e.end.millis / span) * w
            val width = (right - left).coerceAtLeast(2f)
            val row = if (i % 2 == 0) 4f else h - 14f
            drawRect(
                color = if (e.id == selectedEventId) selectedColor else blockColor,
                topLeft = Offset(left, row),
                size = Size(width, 10f),
            )
        }

        // 播放头
        val px = (positionMs / span) * w
        drawLine(
            color = playheadColor,
            start = Offset(px, 0f),
            end = Offset(px, h),
            strokeWidth = 2f,
        )
    }
}
