package io.github.samgum.aegisub.feature.preview.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.audio.SpectrogramData

/**
 * 频谱热图：横轴时间（帧）、纵轴频率（低频在下），能量→颜色（黑→蓝→青→黄→红）。
 * 数据变化时构建一次 Bitmap 缓存（避免每帧重绘数万格），播放头叠加其上。
 *
 * @author 伤感咩吖
 */
@Composable
fun SpectrogramView(
    data: SpectrogramData?,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(data) { data?.let { buildHeatmap(it) } }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val bmp = bitmap ?: return@Canvas
        if (data == null || data.frameCount == 0) return@Canvas
        drawImage(
            image = bmp,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Low,
        )
        if (durationMs > 0) {
            val x = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) * size.width
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2f,
            )
        }
    }
}

/** 把频谱矩阵转成 ARGB 热图 Bitmap（帧=宽、bin=高，低频在下）。 */
private fun buildHeatmap(data: SpectrogramData): ImageBitmap {
    val w = data.frameCount
    val h = data.binCount
    val pixels = IntArray(w * h)
    for (x in 0 until w) {
        val frame = data.frames[x]
        for (y in 0 until h) {
            // y=0 顶部=高频；频谱 bin 0=低频 → 反转使低频在底部
            val bin = h - 1 - y
            val energy = if (bin < frame.size) frame[bin] else 0f
            pixels[y * w + x] = heatColor(energy)
        }
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    return bmp.asImageBitmap()
}

/** 能量 0..1 → ARGB 颜色（黑→深蓝→青→黄→红），近似 Audacity 频谱配色。 */
private fun heatColor(energy: Float): Int {
    val e = energy.coerceIn(0f, 1f)
    val r: Float
    val g: Float
    val b: Float
    when {
        e < 0.25f -> { val t = e / 0.25f; r = 0f; g = 0f; b = t * 0.5f } // 黑→深蓝
        e < 0.5f -> { val t = (e - 0.25f) / 0.25f; r = 0f; g = t; b = 0.5f + t * 0.5f } // →青
        e < 0.75f -> { val t = (e - 0.5f) / 0.25f; r = t; g = 1f; b = 1f - t } // →黄
        else -> { val t = (e - 0.75f) / 0.25f; r = 1f; g = 1f - t * 0.5f; b = 0f } // →红
    }
    val alpha = if (e <= 0f) 0 else 255
    val ri = (r * 255).toInt().coerceIn(0, 255)
    val gi = (g * 255).toInt().coerceIn(0, 255)
    val bi = (b * 255).toInt().coerceIn(0, 255)
    return (alpha shl 24) or (ri shl 16) or (gi shl 8) or bi
}
