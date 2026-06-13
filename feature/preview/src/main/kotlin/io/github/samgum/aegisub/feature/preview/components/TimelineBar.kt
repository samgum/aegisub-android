package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.model.AssEvent
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * 时间轴条：按视频时长把所有字幕行画成 mini 块，选中行高亮，播放头竖线随位置移动。
 *
 * **可拖拽**：按住某行的 mini 块水平拖动 = 整体平移该行（start/end 同步），松手时经
 * [onCommitDrag] 提交（拖动中间态用本地 state 实时绘制，不入撤销栈；约束由调用方 editEventTimes 钳制）。
 *
 * @param events 全部字幕行。
 * @param selectedEventId 选中的编辑目标行 id（高亮）。
 * @param positionMs 当前播放位置。
 * @param durationMs 视频时长；<=0 时不绘制有意义的刻度。
 * @param onCommitDrag 松手提交：eventId + 平移后的 start/end（毫秒）。
 *
 * @author 伤感咩吖
 */
@Composable
fun TimelineBar(
    events: List<AssEvent>,
    selectedEventId: Long?,
    positionMs: Long,
    durationMs: Long,
    onCommitDrag: (eventId: Long, newStartMs: Long, newEndMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blockColor = Color(0xFF6B7280).copy(alpha = 0.55f)
    val selectedColor = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    // 拖动中间态：正在拖的 eventId + 累计偏移 ms
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragDeltaMs by remember { mutableLongStateOf(0L) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(events, durationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val span = max(durationMs.toFloat(), 1f)
                        val w = size.width.toFloat()
                        draggingId = null
                        dragDeltaMs = 0L
                        // hit test：找 offset.x 落在 [left,right] 区间的块
                        for (e in events) {
                            val left = (e.start.millis / span) * w
                            val right = (e.end.millis / span) * w
                            if (offset.x in left..right) {
                                draggingId = e.id
                                return@detectDragGestures
                            }
                        }
                    },
                    onDragEnd = {
                        val id = draggingId
                        if (id != null) {
                            val e = events.firstOrNull { it.id == id }
                            if (e != null) {
                                onCommitDrag(id, e.start.millis + dragDeltaMs, e.end.millis + dragDeltaMs)
                            }
                        }
                        draggingId = null
                        dragDeltaMs = 0L
                    },
                    onDragCancel = {
                        draggingId = null
                        dragDeltaMs = 0L
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val span = max(durationMs.toFloat(), 1f)
                    val w = size.width.toFloat()
                    // 像素 → 毫秒
                    dragDeltaMs += (dragAmount.x * span / w).roundToLong()
                }
            },
    ) {
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

        // 各行 mini 块（错开两行避免重叠遮挡）；正在拖的块用 dragDelta 实时偏移绘制
        events.forEachIndexed { i, e ->
            val delta = if (e.id == draggingId) dragDeltaMs else 0L
            val left = ((e.start.millis + delta) / span) * w
            val right = ((e.end.millis + delta) / span) * w
            val width = (right - left).coerceAtLeast(2f)
            val row = if (i % 2 == 0) 4f else h - 14f
            drawRect(
                color = if (e.id == selectedEventId || e.id == draggingId) selectedColor else blockColor,
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
