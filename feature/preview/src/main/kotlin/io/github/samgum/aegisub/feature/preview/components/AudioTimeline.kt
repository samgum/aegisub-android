package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.audio.Waveform
import io.github.samgum.aegisub.domain.model.AssEvent
import kotlin.math.max
import kotlin.math.roundToLong

/** 拖拽模式：拖左边缘改起始 / 拖右边缘改结束 / 拖中间整体平移。 */
private enum class DragMode { MOVE, START, END }

/** 一次拖拽的中间态：目标行 + 模式 + 累计毫秒偏移。 */
private class DragState(val eventId: Long, val mode: DragMode) {
    var deltaMs: Long = 0
    fun computedStart(e: AssEvent): Long = if (mode == DragMode.END) e.start.millis else e.start.millis + deltaMs
    fun computedEnd(e: AssEvent): Long = if (mode == DragMode.START) e.end.millis else e.end.millis + deltaMs
}

/**
 * 统一音频时间轴（Aegisub 音频面板形态）：波形背景 + 字幕块叠加 + 播放头，
 * 字幕块可拖拽（左边缘改起始 / 右边缘改结束 / 中间平移）。
 *
 * 拖动中间态用 [DragState] 实时绘制（不入撤销栈），松手经 [onCommitDrag] 提交
 * （约束由调用方 editEventTimes 钳制）。
 *
 * @author 伤感咩吖
 */
@Composable
fun AudioTimeline(
    waveform: Waveform?,
    events: List<AssEvent>,
    selectedEventId: Long?,
    positionMs: Long,
    durationMs: Long,
    onCommitDrag: (eventId: Long, newStartMs: Long, newEndMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val waveColor = MaterialTheme.colorScheme.outlineVariant
    val playedWaveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val blockColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val selectedBlockColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val blockBorder = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.error

    var dragState by remember { mutableStateOf<DragState?>(null) }
    val edgePx = with(LocalDensity.current) { 18.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .pointerInput(events, durationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragState = hitTest(offset.x, events, durationMs, size.width.toFloat(), edgePx)
                    },
                    onDragEnd = {
                        dragState?.let { ds ->
                            val e = events.firstOrNull { it.id == ds.eventId }
                            if (e != null) {
                                onCommitDrag(ds.eventId, ds.computedStart(e), ds.computedEnd(e))
                            }
                        }
                        dragState = null
                    },
                    onDragCancel = { dragState = null },
                ) { change, dragAmount ->
                    change.consume()
                    dragState?.let { ds ->
                        val span = max(durationMs.toFloat(), 1f)
                        ds.deltaMs += (dragAmount.x * span / size.width).roundToLong()
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val span = max(durationMs.toFloat(), 1f)
            val mid = h / 2f
            val playX = (positionMs / span) * w

            // 1. 波形柱（已播放段高亮）
            val peaks = waveform?.peaks
            if (peaks != null && peaks.isNotEmpty()) {
                val barWidth = w / peaks.size
                for (i in peaks.indices) {
                    val x = i * barWidth
                    val barH = peaks[i] * mid * 0.85f
                    if (barH <= 0f) continue
                    drawLine(
                        color = if (x <= playX) playedWaveColor else waveColor,
                        start = Offset(x, mid - barH),
                        end = Offset(x, mid + barH),
                        strokeWidth = max(1f, barWidth),
                    )
                }
            }

            // 2. 字幕块叠加（底部带，正在拖的/选中的高亮 + 边框）
            val blockTop = h * 0.72f
            val blockHeight = h * 0.24f
            events.forEach { e ->
                val ds = if (dragState?.eventId == e.id) dragState else null
                val startMs = ds?.computedStart(e) ?: e.start.millis
                val endMs = ds?.computedEnd(e) ?: e.end.millis
                val left = (startMs / span) * w
                val right = (endMs / span) * w
                val width = (right - left).coerceAtLeast(2f)
                val highlight = e.id == selectedEventId || ds != null
                drawRect(
                    color = if (highlight) selectedBlockColor else blockColor,
                    topLeft = Offset(left, blockTop),
                    size = Size(width, blockHeight),
                )
                if (highlight) {
                    drawRect(
                        color = blockBorder,
                        topLeft = Offset(left, blockTop),
                        size = Size(width, blockHeight),
                        style = Stroke(width = 1.5f),
                    )
                }
            }

            // 3. 播放头（最上层）
            drawLine(
                color = playheadColor,
                start = Offset(playX, 0f),
                end = Offset(playX, h),
                strokeWidth = 2f,
            )
        }
    }
}

/** 找 x 落在哪个字幕块的 [left,right] 区间，并按距边缘距离判定模式。 */
private fun hitTest(
    x: Float,
    events: List<AssEvent>,
    durationMs: Long,
    widthPx: Float,
    edgePx: Float,
): DragState? {
    val span = max(durationMs.toFloat(), 1f)
    for (e in events) {
        val left = (e.start.millis / span) * widthPx
        val right = (e.end.millis / span) * widthPx
        if (x in left..right) {
            val mode = when {
                x - left < edgePx -> DragMode.START
                right - x < edgePx -> DragMode.END
                else -> DragMode.MOVE
            }
            return DragState(e.id, mode)
        }
    }
    return null
}
