package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.audio.Waveform
import kotlin.math.max

/**
 * 音频波形条：镜像柱状（中线对称）+ 播放头左侧高亮已播放段。
 *
 * @param waveform 波形数据（null/空则不绘制）。
 * @param positionMs 当前播放位置（用于已播放高亮）。
 * @param durationMs 视频时长（归一化播放头位置）。
 *
 * @author 伤感咩吖
 */
@Composable
fun WaveformCanvas(
    waveform: Waveform?,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val playedColor = MaterialTheme.colorScheme.primary
    val unplayedColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.fillMaxWidth().height(56.dp)) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val peaks = waveform?.peaks
        if (peaks == null || peaks.isEmpty()) {
            return@Canvas
        }
        val barWidth = w / peaks.size
        val playProgress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val playX = playProgress * w
        for (i in peaks.indices) {
            val x = i * barWidth
            val barH = peaks[i] * mid * 0.95f
            if (barH <= 0f) continue
            val color = if (x <= playX) playedColor else unplayedColor
            drawLine(
                color = color,
                start = Offset(x, mid - barH),
                end = Offset(x, mid + barH),
                strokeWidth = max(1f, barWidth),
            )
        }
    }
}
