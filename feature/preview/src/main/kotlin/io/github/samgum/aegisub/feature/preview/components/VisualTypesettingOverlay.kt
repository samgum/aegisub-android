package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.edit.VisualTags
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 桌面 Aegisub 全部 8 种可视化打字模式（对齐 `default_toolbar.json` 中 `visual_tools`）：
 * - [CROSSHAIR]：标准十字准星坐标探针（点按设 \pos 或查看脚本坐标）
 * - [POSITION]：拖拽定位 `\pos(x,y)`
 * - [MOVE]：两点运动轨迹 `\move(x1,y1,x2,y2)`
 * - [ROTATE_Z]：Z 轴旋转圆盘手柄 `\frz`
 * - [ROTATE_XY]：3D 透视网格旋转 `\frx`/`\fry` + 原点 `\org(x,y)`
 * - [SCALE]：XY 缩放包围框手柄 `\fscx`/`\fscy`
 * - [CLIP_RECT]：画面直接框选/拖拽四角矩形裁剪 `\clip(x1,y1,x2,y2)`
 * - [CLIP_VECTOR]：画面点选顶点/拖拽多边形矢量裁剪 `\clip(m ... l ...)`
 *
 * @author 伤感咩吖
 */
enum class VisualToolMode {
    CROSSHAIR,
    POSITION,
    MOVE,
    ROTATE_Z,
    ROTATE_XY,
    SCALE,
    CLIP_RECT,
    CLIP_VECTOR,
}

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
    currentRotation: VisualTags.Rotation3dParams = VisualTags.Rotation3dParams(),
    currentOrigin: Pair<Int, Int>? = null,
    currentScale: VisualTags.ScaleParams = VisualTags.ScaleParams(),
    currentClipRect: VisualTags.ClipRect? = null,
    currentVectorClip: VisualTags.VectorClip? = null,
    onRotationZChange: (deg: Int) -> Unit = {},
    onRotation3dChange: (frx: Int, fry: Int) -> Unit = { _, _ -> },
    onOriginChange: (x: Int, y: Int) -> Unit = { _, _ -> },
    onScaleChange: (scaleX: Int, scaleY: Int) -> Unit = { _, _ -> },
    onClipRectChange: (x1: Int, y1: Int, x2: Int, y2: Int, inverse: Boolean) -> Unit = { _, _, _, _, _ -> },
    onVectorClipChange: (VisualTags.VectorClip) -> Unit = {},
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val defaultPos = remember(playResX, playResY) { (playResX / 2) to (playResY * 85 / 100) }

    var hoverCoord by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var livePos by remember(currentPos, mode, playResX, playResY) { mutableStateOf(currentPos ?: defaultPos) }

    val initA = currentMove?.let { it.x1 to it.y1 } ?: currentPos ?: defaultPos
    val initB = currentMove?.let { it.x2 to it.y2 } ?: ((defaultPos.first) to (playResY / 2))
    var liveA by remember(currentMove, mode, playResX, playResY) { mutableStateOf(initA) }
    var liveB by remember(currentMove, mode, playResX, playResY) { mutableStateOf(initB) }

    var liveFrz by remember(currentRotation.frz, mode) { mutableStateOf(currentRotation.frz.roundToInt()) }
    var liveFrx by remember(currentRotation.frx, mode) { mutableStateOf(currentRotation.frx.roundToInt()) }
    var liveFry by remember(currentRotation.fry, mode) { mutableStateOf(currentRotation.fry.roundToInt()) }
    var liveOrg by remember(currentOrigin, currentPos, mode) { mutableStateOf(currentOrigin ?: currentPos ?: defaultPos) }
    var draggingOrg by remember { mutableStateOf(false) }

    var liveScaleX by remember(currentScale.scaleX, mode) { mutableStateOf(currentScale.scaleX.roundToInt()) }
    var liveScaleY by remember(currentScale.scaleY, mode) { mutableStateOf(currentScale.scaleY.roundToInt()) }

    val initRect = currentClipRect ?: VisualTags.ClipRect(
        playResX / 5, playResY / 5, playResX * 4 / 5, playResY * 4 / 5, false,
    )
    var liveClipA by remember(currentClipRect, mode) { mutableStateOf(initRect.x1 to initRect.y1) }
    var liveClipB by remember(currentClipRect, mode) { mutableStateOf(initRect.x2 to initRect.y2) }
    var draggingClipCornerB by remember { mutableStateOf(true) }

    var liveVecPoints by remember(currentVectorClip, mode) {
        mutableStateOf(currentVectorClip?.points ?: emptyList())
    }
    var draggingVecIdx by remember { mutableStateOf(-1) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(playResX, playResY, mode, liveVecPoints, currentVectorClip) {
                detectTapGestures { tapOffset ->
                    val pt = project(tapOffset, boxSize, playResX, playResY)
                    hoverCoord = pt
                    when (mode) {
                        VisualToolMode.CROSSHAIR -> {
                            livePos = pt
                            onPosChange(pt.first, pt.second)
                        }
                        VisualToolMode.CLIP_VECTOR -> {
                            val updated = liveVecPoints + VisualTags.VecPoint(pt.first, pt.second)
                            liveVecPoints = updated
                            onVectorClipChange(
                                VisualTags.VectorClip(
                                    points = updated,
                                    inverse = currentVectorClip?.inverse ?: false,
                                    scale = currentVectorClip?.scale ?: 1,
                                ),
                            )
                        }
                        else -> Unit
                    }
                }
            }
            .pointerInput(playResX, playResY, mode, currentClipRect, currentVectorClip) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val pt = project(offset, boxSize, playResX, playResY)
                        hoverCoord = pt
                        when (mode) {
                            VisualToolMode.CROSSHAIR, VisualToolMode.POSITION -> livePos = pt
                            VisualToolMode.MOVE -> {
                                val a = toPx(liveA, boxSize, playResX, playResY)
                                val b = toPx(liveB, boxSize, playResX, playResY)
                                if (hypot(offset.x - a.x, offset.y - a.y) <= hypot(offset.x - b.x, offset.y - b.y)) {
                                    liveA = pt
                                } else {
                                    liveB = pt
                                }
                            }
                            VisualToolMode.ROTATE_Z -> {
                                val center = toPx(liveOrg, boxSize, playResX, playResY)
                                val angleRad = atan2(-(offset.y - center.y), offset.x - center.x)
                                liveFrz = ((Math.toDegrees(angleRad.toDouble()).roundToInt()) % 360 + 360) % 360
                            }
                            VisualToolMode.ROTATE_XY -> {
                                val orgPx = toPx(liveOrg, boxSize, playResX, playResY)
                                draggingOrg = hypot(offset.x - orgPx.x, offset.y - orgPx.y) < 42f
                            }
                            VisualToolMode.SCALE -> Unit
                            VisualToolMode.CLIP_RECT -> {
                                val pa = toPx(liveClipA, boxSize, playResX, playResY)
                                val pb = toPx(liveClipB, boxSize, playResX, playResY)
                                draggingClipCornerB = hypot(offset.x - pb.x, offset.y - pb.y) <= hypot(offset.x - pa.x, offset.y - pa.y)
                            }
                            VisualToolMode.CLIP_VECTOR -> {
                                draggingVecIdx = liveVecPoints.indexOfFirst { vp ->
                                    val px = toPx(vp.x to vp.y, boxSize, playResX, playResY)
                                    hypot(offset.x - px.x, offset.y - px.y) < 44f
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        when (mode) {
                            VisualToolMode.CROSSHAIR, VisualToolMode.POSITION -> onPosChange(livePos.first, livePos.second)
                            VisualToolMode.MOVE -> onMoveChange(liveA.first, liveA.second, liveB.first, liveB.second)
                            VisualToolMode.ROTATE_Z -> onRotationZChange(liveFrz)
                            VisualToolMode.ROTATE_XY -> {
                                if (draggingOrg) onOriginChange(liveOrg.first, liveOrg.second)
                                else onRotation3dChange(liveFrx, liveFry)
                            }
                            VisualToolMode.SCALE -> onScaleChange(liveScaleX, liveScaleY)
                            VisualToolMode.CLIP_RECT -> onClipRectChange(
                                minOf(liveClipA.first, liveClipB.first),
                                minOf(liveClipA.second, liveClipB.second),
                                maxOf(liveClipA.first, liveClipB.first),
                                maxOf(liveClipA.second, liveClipB.second),
                                currentClipRect?.inverse ?: false,
                            )
                            VisualToolMode.CLIP_VECTOR -> if (liveVecPoints.isNotEmpty()) {
                                onVectorClipChange(
                                    VisualTags.VectorClip(
                                        points = liveVecPoints,
                                        inverse = currentVectorClip?.inverse ?: false,
                                        scale = currentVectorClip?.scale ?: 1,
                                    ),
                                )
                            }
                        }
                    },
                    onDragCancel = {},
                ) { change, dragAmount ->
                    val pt = project(change.position, boxSize, playResX, playResY)
                    hoverCoord = pt
                    when (mode) {
                        VisualToolMode.CROSSHAIR, VisualToolMode.POSITION -> livePos = pt
                        VisualToolMode.MOVE -> {
                            val a = toPx(liveA, boxSize, playResX, playResY)
                            val b = toPx(liveB, boxSize, playResX, playResY)
                            if (hypot(change.position.x - a.x, change.position.y - a.y) <= hypot(change.position.x - b.x, change.position.y - b.y)) {
                                liveA = pt
                            } else {
                                liveB = pt
                            }
                        }
                        VisualToolMode.ROTATE_Z -> {
                            val center = toPx(liveOrg, boxSize, playResX, playResY)
                            val angleRad = atan2(-(change.position.y - center.y), change.position.x - center.x)
                            liveFrz = ((Math.toDegrees(angleRad.toDouble()).roundToInt()) % 360 + 360) % 360
                        }
                        VisualToolMode.ROTATE_XY -> {
                            if (draggingOrg) {
                                liveOrg = pt
                            } else {
                                liveFry = (liveFry + (dragAmount.x * 0.4f).roundToInt()).coerceIn(-180, 180)
                                liveFrx = (liveFrx - (dragAmount.y * 0.4f).roundToInt()).coerceIn(-180, 180)
                            }
                        }
                        VisualToolMode.SCALE -> {
                            liveScaleX = (liveScaleX + (dragAmount.x * 0.6f).roundToInt()).coerceIn(10, 500)
                            liveScaleY = (liveScaleY - (dragAmount.y * 0.6f).roundToInt()).coerceIn(10, 500)
                        }
                        VisualToolMode.CLIP_RECT -> {
                            if (draggingClipCornerB) liveClipB = pt else liveClipA = pt
                        }
                        VisualToolMode.CLIP_VECTOR -> {
                            if (draggingVecIdx in liveVecPoints.indices) {
                                val mut = liveVecPoints.toMutableList()
                                mut[draggingVecIdx] = VisualTags.VecPoint(pt.first, pt.second)
                                liveVecPoints = mut
                            }
                        }
                    }
                    change.consume()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f || playResX <= 0 || playResY <= 0) return@Canvas
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

            when (mode) {
                VisualToolMode.CROSSHAIR -> {
                    val p = toPx(hoverCoord ?: livePos, boxSize, playResX, playResY)
                    drawLine(Color(0xAA00E5FF), Offset(0f, p.y), Offset(size.width, p.y), strokeWidth = 1.5f, pathEffect = dash)
                    drawLine(Color(0xAA00E5FF), Offset(p.x, 0f), Offset(p.x, size.height), strokeWidth = 1.5f, pathEffect = dash)
                    drawCircle(Color(0xFF00E5FF), radius = 8f, center = p, style = Stroke(width = 2f))
                }
                VisualToolMode.POSITION -> {
                    val p = toPx(livePos, boxSize, playResX, playResY)
                    drawLine(Color(0x66FFFFFF), Offset(0f, p.y), Offset(size.width, p.y), strokeWidth = 1f, pathEffect = dash)
                    drawLine(Color(0x66FFFFFF), Offset(p.x, 0f), Offset(p.x, size.height), strokeWidth = 1f, pathEffect = dash)
                    drawCircle(Color(0x88000000), radius = 16f, center = p)
                    drawCircle(Color(0xFFFF5252), radius = 16f, center = p, style = Stroke(width = 3f))
                    drawCircle(Color.White, radius = 4f, center = p)
                }
                VisualToolMode.MOVE -> {
                    val pa = toPx(liveA, boxSize, playResX, playResY)
                    val pb = toPx(liveB, boxSize, playResX, playResY)
                    drawLine(Color(0xFFFFD54F), pa, pb, strokeWidth = 2.5f, pathEffect = dash)
                    drawCircle(Color(0x88000000), radius = 15f, center = pa)
                    drawCircle(Color(0xFF4CAF50), radius = 15f, center = pa, style = Stroke(width = 3f))
                    drawCircle(Color(0x88000000), radius = 15f, center = pb)
                    drawCircle(Color(0xFFFF9800), radius = 15f, center = pb, style = Stroke(width = 3f))
                }
                VisualToolMode.ROTATE_Z -> {
                    val c = toPx(liveOrg, boxSize, playResX, playResY)
                    val radius = minOf(size.width, size.height) * 0.24f
                    drawCircle(Color(0xAA40C4FF), radius = radius, center = c, style = Stroke(width = 2f, pathEffect = dash))
                    val rad = Math.toRadians(liveFrz.toDouble())
                    val handle = Offset(c.x + (radius * cos(rad)).toFloat(), c.y - (radius * sin(rad)).toFloat())
                    drawLine(Color(0xFF40C4FF), c, handle, strokeWidth = 3f)
                    drawCircle(Color(0xFF40C4FF), radius = 12f, center = handle)
                    drawCircle(Color.White, radius = 6f, center = c)
                }
                VisualToolMode.ROTATE_XY -> {
                    val c = toPx(liveOrg, boxSize, playResX, playResY)
                    val gridHalf = minOf(size.width, size.height) * 0.22f
                    val skewX = sin(Math.toRadians(liveFry.toDouble())).toFloat() * 0.45f
                    val scaleY = cos(Math.toRadians(liveFrx.toDouble())).toFloat().coerceIn(0.25f, 1f)
                    for (g in -2..2) {
                        val t = g / 2f
                        val yOff = t * gridHalf * scaleY
                        drawLine(
                            Color(0xAAAB47BC),
                            Offset(c.x - gridHalf + yOff * skewX, c.y + yOff),
                            Offset(c.x + gridHalf + yOff * skewX, c.y + yOff),
                            strokeWidth = 1.5f,
                        )
                    }
                    // \org 原点三角手柄
                    drawCircle(Color(0xFFFFCA28), radius = 11f, center = c, style = Stroke(width = 3f))
                }
                VisualToolMode.SCALE -> {
                    val c = toPx(livePos, boxSize, playResX, playResY)
                    val bw = (size.width * 0.20f) * (liveScaleX / 100f)
                    val bh = (size.height * 0.12f) * (liveScaleY / 100f)
                    drawRect(
                        color = Color(0xFF69F0AE),
                        topLeft = Offset(c.x - bw / 2f, c.y - bh / 2f),
                        size = Size(bw, bh),
                        style = Stroke(width = 2.5f, pathEffect = dash),
                    )
                    drawCircle(Color(0xFF69F0AE), radius = 10f, center = Offset(c.x + bw / 2f, c.y - bh / 2f))
                }
                VisualToolMode.CLIP_RECT -> {
                    val pa = toPx(liveClipA, boxSize, playResX, playResY)
                    val pb = toPx(liveClipB, boxSize, playResX, playResY)
                    val left = minOf(pa.x, pb.x); val top = minOf(pa.y, pb.y)
                    val w = kotlin.math.abs(pb.x - pa.x); val h = kotlin.math.abs(pb.y - pa.y)
                    drawRect(Color(0x3300E5FF), topLeft = Offset(left, top), size = Size(w, h))
                    drawRect(Color(0xFF00E5FF), topLeft = Offset(left, top), size = Size(w, h), style = Stroke(width = 2.5f))
                    drawCircle(Color(0xFF00E5FF), radius = 12f, center = pa)
                    drawCircle(Color(0xFFFFAB40), radius = 12f, center = pb)
                }
                VisualToolMode.CLIP_VECTOR -> {
                    if (liveVecPoints.isNotEmpty()) {
                        val path = Path()
                        liveVecPoints.forEachIndexed { idx, vp ->
                            val px = toPx(vp.x to vp.y, boxSize, playResX, playResY)
                            if (idx == 0) path.moveTo(px.x, px.y) else path.lineTo(px.x, px.y)
                        }
                        if (liveVecPoints.size >= 3) {
                            path.close()
                            drawPath(path, Color(0x33FF4081))
                        }
                        drawPath(path, Color(0xFFFF4081), style = Stroke(width = 2.5f))
                        liveVecPoints.forEach { vp ->
                            val px = toPx(vp.x to vp.y, boxSize, playResX, playResY)
                            drawCircle(Color.White, radius = 9f, center = px)
                            drawCircle(Color(0xFFFF4081), radius = 9f, center = px, style = Stroke(width = 2.5f))
                        }
                    }
                }
            }
        }

        // 左上角脚本坐标与参数实时 HUD（仿桌面 Aegisub 视频坐标指示条）
        val hudText = when (mode) {
            VisualToolMode.CROSSHAIR -> {
                val c = hoverCoord ?: livePos
                "十字探针 (${c.first}, ${c.second}) · 点按设 \\pos"
            }
            VisualToolMode.POSITION -> "\\pos(${livePos.first}, ${livePos.second})"
            VisualToolMode.MOVE -> "\\move(${liveA.first},${liveA.second} → ${liveB.first},${liveB.second})"
            VisualToolMode.ROTATE_Z -> "\\frz$liveFrz°"
            VisualToolMode.ROTATE_XY -> "\\frx$liveFrx° \\fry$liveFry° · \\org(${liveOrg.first},${liveOrg.second})"
            VisualToolMode.SCALE -> "\\fscx$liveScaleX% \\fscy$liveScaleY%"
            VisualToolMode.CLIP_RECT -> "\\clip(${minOf(liveClipA.first, liveClipB.first)},${minOf(liveClipA.second, liveClipB.second)},${maxOf(liveClipA.first, liveClipB.first)},${maxOf(liveClipA.second, liveClipB.second)})"
            VisualToolMode.CLIP_VECTOR -> "矢量裁剪：${liveVecPoints.size} 顶点（点画面加点 / 拖顶点移动）"
        }
        Surface(
            color = Color(0xB8101418),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        ) {
            Text(
                text = hudText,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

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

private fun project(offset: Offset, size: IntSize, resX: Int, resY: Int): Pair<Int, Int> {
    if (size.width == 0 || size.height == 0) return (resX / 2) to resY
    val x = ((offset.x / size.width) * resX).roundToInt().coerceIn(-resX / 2, resX + resX / 2)
    val y = ((offset.y / size.height) * resY).roundToInt().coerceIn(-resY / 2, resY + resY / 2)
    return x to y
}
