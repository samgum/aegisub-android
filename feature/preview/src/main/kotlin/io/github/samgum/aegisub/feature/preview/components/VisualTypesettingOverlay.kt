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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 可视化打字覆盖层（复刻桌面 Aegisub Visual Typesetting 的 \pos 拖拽）：
 * 在视频画面上拖拽即设置选中行 {\pos(x,y)}，按脚本分辨率（PlayResX/Y）映射坐标。
 *
 * - 拖拽过程中实时更新手柄显示（本地状态），拖拽结束才回调提交（单撤销点）。
 * - 无现有 {\pos} 时手柄默认置于画面下中（接近 ASS 底部对齐）。
 *
 * @param playResX/Y 脚本分辨率（[Script Info] 的 PlayResX/PlayResY）
 * @param currentPos 当前 {\pos}，null 表示未设置
 * @param onPosChange 拖拽结束提交新坐标（脚本坐标系）
 *
 * @author 伤感咩吖
 */
@Composable
fun VisualTypesettingOverlay(
    playResX: Int,
    playResY: Int,
    currentPos: Pair<Int, Int>?,
    onPosChange: (x: Int, y: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val defaultPos = remember(playResX, playResY) { (playResX / 2) to (playResY * 9 / 10) }
    var livePos by remember(currentPos) { mutableStateOf(currentPos ?: defaultPos) }
    val density = LocalDensity.current
    val handlePx = with(density) { 32.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(playResX, playResY) {
                detectDragGestures(
                    onDragStart = { offset ->
                        livePos = project(offset, boxSize, playResX, playResY)
                    },
                    onDragEnd = {
                        onPosChange(livePos.first, livePos.second)
                    },
                    onDragCancel = {
                        // 取消则丢弃本次拖拽，回退到 currentPos
                        livePos = currentPos ?: defaultPos
                    },
                ) { change, _ ->
                    livePos = project(change.position, boxSize, playResX, playResY)
                    change.consume()
                }
            },
    ) {
        if (boxSize.width > 0 && boxSize.height > 0 && playResX > 0 && playResY > 0) {
            val (px, py) = livePos
            val hx = (px.toFloat() / playResX) * boxSize.width - handlePx / 2f
            val hy = (py.toFloat() / playResY) * boxSize.height - handlePx / 2f
            Box(
                modifier = Modifier
                    .offset { IntOffset(hx.roundToInt(), hy.roundToInt()) }
                    .size(32.dp)
                    .background(Color(0x66000000), CircleShape)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

/** 屏幕 px → 脚本坐标，钳到分辨率范围。 */
private fun project(
    offset: androidx.compose.ui.geometry.Offset,
    size: IntSize,
    resX: Int,
    resY: Int,
): Pair<Int, Int> {
    if (size.width == 0 || size.height == 0) return (resX / 2) to resY
    val x = ((offset.x / size.width) * resX).roundToInt().coerceIn(0, resX)
    val y = ((offset.y / size.height) * resY).roundToInt().coerceIn(0, resY)
    return x to y
}
