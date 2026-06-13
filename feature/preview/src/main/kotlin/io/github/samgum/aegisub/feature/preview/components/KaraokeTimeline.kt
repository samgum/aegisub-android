package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.edit.KaraokeSyllables
import kotlin.math.roundToInt

/**
 * Karaoke 音节时间轴（复刻桌面 Aegisub Karaoke 模式逐音节计时）：
 * 把选中行 {\k}/{\kf} 音节渲染为按时长成比例的色块，拖拽块间分隔条即在相邻音节间
 * 挪动时长（厘秒），松手提交重建文本（单撤销点）。每音节至少 1cs。
 *
 * 无 karaoke 标签时显示提示（提示先用工具箱生成）。
 *
 * @author 伤感咩吖
 */
@Composable
fun KaraokeTimeline(
    text: String,
    onCommit: (newText: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(text) { KaraokeSyllables.parse(text) }
    if (parsed.isEmpty()) {
        Text(
            "当前行无 karaoke 音节（{\\k}/{\\kf}），先在工具箱用「卡拉OK生成」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth().padding(8.dp),
        )
        return
    }
    var durs by remember(text) { mutableStateOf(parsed) }
    var widthPx by remember { mutableStateOf(0f) }
    val totalCs = KaraokeSyllables.total(durs).coerceAtLeast(1L).toFloat()
    val density = LocalDensity.current
    val handlePx = with(density) { 12.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .onSizeChanged { widthPx = it.width.toFloat() },
    ) {
        if (widthPx > 0f) {
            var cumCs = 0L
            durs.forEachIndexed { i, syl ->
                val leftPx = (cumCs / totalCs) * widthPx
                val segPx = (syl.centiseconds / totalCs) * widthPx
                cumCs += syl.centiseconds
                // 音节色块
                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx.roundToInt(), 0) }
                        .width(with(density) { segPx.toDp() })
                        .fillMaxHeight()
                        .background(syllableColor(i)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (syl.text.isNotBlank()) {
                        Text(
                            syl.text,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            // 分隔拖拽条（音节 j 的左缘 = boundary j-1）
            cumCs = 0L
            for (j in 1 until durs.size) {
                cumCs += durs[j - 1].centiseconds
                val handleLeftPx = (cumCs / totalCs) * widthPx - handlePx / 2f
                val boundary = j - 1
                Box(
                    modifier = Modifier
                        .offset { IntOffset(handleLeftPx.roundToInt(), 0) }
                        .width(with(density) { handlePx.toDp() })
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onSurface)
                        .pointerInput(durs, widthPx, totalCs) {
                            detectDragGestures(
                                onDragEnd = { onCommit(KaraokeSyllables.build(durs)) },
                            ) { change, dragAmount ->
                                val deltaCs = ((dragAmount.x / widthPx) * totalCs).roundToInt()
                                if (deltaCs != 0) {
                                    durs = KaraokeSyllables.adjust(durs, boundary, deltaCs.toLong())
                                }
                                change.consume()
                            }
                        },
                )
            }
        }
    }
}

/** 音节配色循环（深色调，白字可读）。 */
private fun syllableColor(i: Int): Color = listOf(
    Color(0xFF3949AB), Color(0xFF00897B), Color(0xFFC0CA33),
    Color(0xFFFB8C00), Color(0xFF6D4C41), Color(0xFF8E24AA),
    Color(0xFF43A047), Color(0xFFE53935),
)[i % 8]
