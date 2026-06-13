package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.edit.VisualTags
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 可视化打字工具模式：定位 {\pos} 或 两点移动 {\move}。 */
enum class VisualToolMode { POSITION, MOVE }

/**
 * 可视化打字覆盖层（复刻桌面 Aegisub Visual Typesetting 的 \pos/\move 拖拽）：
 * 在视频画面上拖拽设选中行覆盖标签，按脚本分辨率（PlayResX/Y）映射坐标。
 *
 * - POSITION 模式：单一手柄，拖拽设 {\pos(x,y)}。
 * - MOVE 模式：两手柄（起点/终点），分别拖拽设 {\move(x1,y1,x2,y2)}。
 * - 拖拽过程实时更新手柄显示，拖拽结束才回调提交（单撤销点）。
 * - 缺省坐标：无 \pos 时手柄置画面下中；无 \move 时起点=\pos、终点=起点偏移。
 *
 * @author 伤感咩吖
 */
@Composable
fun VisualTypesettingOverlay(
    playResX: Int,
    playResY: Int,
    mode: VisualToolMode,
    currentPos: Pair<Int, Int>?,
    currentMove: VisualTags.MoveParams?,
    onPosChange: (x: Int, y: Int) -> Unit,
    onMoveChange: (x1: Int, y1: Int, x2: Int, y2: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handlePx = with(density) { 32.dp.toPx() }
    val defaultPos = remember(playResX, playResY) { (playResX / 2) to (playResY * 9 / 10) }

    // POSITION 模式本地状态
    var livePos by remember(currentPos, mode) { mutableStateOf(currentPos ?: defaultPos) }

    // MOVE 模式本地状态（起点 / 终点）
    val initA = currentMove?.let { it.x1 to it.y1 } ?: currentPos ?: defaultPos
    val initB = currentMove?.let { it.x2 to it.y2 } ?: (defaultPos.first to defaultPos.second / 2)
    var liveA by remember(currentMove, mode) { mutableStateOf(initA) }
    var liveB by remember(currentMove, mode) { mutableStateOf(initB) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(playResX, playResY, mode) {
                detectDragGestures(
                    onDragStart = { offset ->
                        when (mode) {
                            VisualToolMode.POSITION -> livePos = project(offset, boxSize, playResX, playResY)
                            VisualToolMode.MOVE -> {
                                // 选距离更近的手柄
                                val a = toPx(liveA, boxSize, playResX, playResY)
                                val b = toPx(liveB, boxSize, playResX, playResY)
                                if (hypot(offset.x - a.x, offset.y - a.y) <=
                                    hypot(offset.x - b.x, offset.y - b.y)
                                ) liveA = project(offset, boxSize, playResX, playResY)
                                else liveB = project(offset, boxSize, playResX, playResY)
                            }
                        }
                    },
                    onDragEnd = {
                        when (mode) {
                            VisualToolMode.POSITION -> onPosChange(livePos.first, livePos.second)
                            VisualToolMode.MOVE ->
                                onMoveChange(liveA.first, liveA.second, liveB.first, liveB.second)
                        }
                    },
                    onDragCancel = {
                        livePos = currentPos ?: defaultPos
                        liveA = initA; liveB = initB
                    },
                ) { change, _ ->
                    when (mode) {
                        VisualToolMode.POSITION ->
                            livePos = project(change.position, boxSize, playResX, playResY)
                        VisualToolMode.MOVE -> {
                            val a = toPx(liveA, boxSize, playResX, playResY)
                            val b = toPx(liveB, boxSize, playResX, playResY)
                            if (hypot(change.position.x - a.x, change.position.y - a.y) <=
                                hypot(change.position.x - b.x, change.position.y - b.y)
                            ) liveA = project(change.position, boxSize, playResX, playResY)
                            else liveB = project(change.position, boxSize, playResX, playResY)
                        }
                    }
                    change.consume()
                }
            },
    ) {
        if (boxSize.width > 0 && boxSize.height > 0 && playResX > 0 && playResY > 0) {
            when (mode) {
                VisualToolMode.POSITION -> Handle(livePos, boxSize, playResX, playResY, handlePx, Color.White)
                VisualToolMode.MOVE -> {
                    Handle(liveA, boxSize, playResX, playResY, handlePx, Color(0xFF4CAF50), label = "起")
                    Handle(liveB, boxSize, playResX, playResY, handlePx, Color(0xFFFF9800), label = "终")
                }
            }
        }
    }
}

/** 单个拖拽手柄（仅显示，手势由父 Box 捕获）。 */
@Composable
private fun Handle(
    pos: Pair<Int, Int>,
    boxSize: IntSize,
    resX: Int,
    resY: Int,
    handlePx: Float,
    ring: Color,
    label: String? = null,
) {
    val hx = (pos.first.toFloat() / resX) * boxSize.width - handlePx / 2f
    val hy = (pos.second.toFloat() / resY) * boxSize.height - handlePx / 2f
    Box(
        modifier = Modifier
            .offset { IntOffset(hx.roundToInt(), hy.roundToInt()) }
            .size(32.dp)
            .background(Color(0x66000000), CircleShape)
            .border(2.dp, ring, CircleShape),
    )
}

/** 脚本坐标 → 屏幕像素中心。 */
private fun toPx(
    pos: Pair<Int, Int>,
    size: IntSize,
    resX: Int,
    resY: Int,
): Offset {
    if (size.width == 0 || size.height == 0) return Offset.Zero
    return Offset(
        (pos.first.toFloat() / resX) * size.width,
        (pos.second.toFloat() / resY) * size.height,
    )
}

/** 屏幕 px → 脚本坐标，钳到分辨率范围。 */
private fun project(offset: Offset, size: IntSize, resX: Int, resY: Int): Pair<Int, Int> {
    if (size.width == 0 || size.height == 0) return (resX / 2) to resY
    val x = ((offset.x / size.width) * resX).roundToInt().coerceIn(0, resX)
    val y = ((offset.y / size.height) * resY).roundToInt().coerceIn(0, resY)
    return x to y
}
