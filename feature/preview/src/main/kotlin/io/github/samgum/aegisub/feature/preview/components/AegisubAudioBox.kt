package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samgum.aegisub.domain.audio.SpectrogramData
import io.github.samgum.aegisub.domain.audio.Waveform
import io.github.samgum.aegisub.domain.edit.KaraokeSyllables
import io.github.samgum.aegisub.domain.edit.KeyframesIo
import io.github.samgum.aegisub.domain.model.AssEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * 专业级 Aegisub 可缩放音频打轴画布（彻底重做版）：
 * - 支持 **时间窗口缩放**（`2s..60s` 视口，默认 `10s`，告别旧版整部 24 分钟塞满一屏导致无法拖拽的问题！）
 * - 支持 **自动跟随播放头 (`autoScroll`)** 与画布平移
 * - 支持 **波形 / 频谱热图** 同视口无缝渲染
 * - 绘制顶部 **时间标尺**、青色 **关键帧吸附线 (`keyframesMs`)**、前后相邻行幽灵块（直观查看重叠与间隙）
 * - 选中行采用经典桌面 Aegisub **左红起始线 (`Start`) + 右橙结束线 (`End`)** 粗手柄：
 *   拖左线改起始、拖右线改结束、拖中间平移整行、拖空白区平移视口、点按空白区定位播放头！
 * - 开启 `karaokeMode` 时直接在当前行区间内绘制黄色音节切分虚线并支持拖拽音节边界！
 *
 * @author 伤感咩吖
 */
@Composable
fun AegisubAudioBox(
    waveform: Waveform?,
    spectrogram: SpectrogramData?,
    showSpectrogram: Boolean,
    events: List<AssEvent>,
    selectedEventId: Long?,
    positionMs: Long,
    durationMs: Long,
    windowDurationMs: Long = 10_000L,
    autoScroll: Boolean = true,
    snapToKeyframes: Boolean = true,
    keyframesMs: List<Long> = emptyList(),
    karaokeMode: Boolean = false,
    onSeek: (Long) -> Unit,
    onCommitTimes: (eventId: Long, newStartMs: Long, newEndMs: Long) -> Unit,
    onCommitKaraokeText: (eventId: Long, newText: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val safeTotalMs = maxOf(durationMs, waveform?.durationMs ?: 0L, 10_000L)
    val visibleSpanMs = windowDurationMs.coerceIn(2_000L, safeTotalMs.coerceAtLeast(2_000L))

    var windowStartMs by remember { mutableLongStateOf(0L) }
    val selectedEvent = remember(events, selectedEventId) {
        events.firstOrNull { it.id == selectedEventId }
    }

    // 选中行变化时，若选中行不在当前视口内则自动将视口居中到选中行起始点
    LaunchedEffect(selectedEventId) {
        selectedEvent?.let { ev ->
            if (ev.start.millis < windowStartMs || ev.start.millis > windowStartMs + visibleSpanMs) {
                windowStartMs = (ev.start.millis - visibleSpanMs / 4).coerceIn(0L, (safeTotalMs - visibleSpanMs).coerceAtLeast(0L))
            }
        }
    }

    // 播放时自动滚屏跟随播放头
    LaunchedEffect(positionMs, autoScroll, visibleSpanMs) {
        if (autoScroll) {
            val rightMargin = windowStartMs + (visibleSpanMs * 85 / 100)
            if (positionMs < windowStartMs || positionMs > rightMargin) {
                windowStartMs = (positionMs - visibleSpanMs / 5).coerceIn(0L, (safeTotalMs - visibleSpanMs).coerceAtLeast(0L))
            }
        }
    }

    val handleHitPx = with(LocalDensity.current) { 22.dp.toPx() }

    // 拖拽模式：起始线 / 结束线 / 整句平移 / 卡拉OK音节线 / 视口平移
    var dragKind by remember { mutableStateOf<AudioDragKind?>(null) }
    var liveStartMs by remember { mutableLongStateOf(0L) }
    var liveEndMs by remember { mutableLongStateOf(0L) }
    var liveSyllables by remember { mutableStateOf<List<KaraokeSyllables.Syllable>>(emptyList()) }
    var dragAccumPx by remember { mutableFloatStateOf(0f) }

    val bgDark = Color(0xFF12161F)
    val rulerBg = Color(0xFF1B2230)
    val waveIdle = Color(0xFF4ADE80).copy(alpha = 0.62f)
    val waveActive = Color(0xFF86EFAC)
    val activeShade = Color(0x3838BDF8)
    val neighborShade = Color(0x2294A3B8)
    val startLineColor = Color(0xFFFF5252)
    val endLineColor = Color(0xFFFFAB40)
    val keyframeColor = Color(0xAAEC4899)
    val playheadColor = Color(0xFFFFFFFF)
    val karaokeLineColor = Color(0xFFFFEA00)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgDark)
            .pointerInput(selectedEvent, visibleSpanMs, windowStartMs, safeTotalMs) {
                detectTapGestures { offset ->
                    if (size.width <= 0) return@detectTapGestures
                    val tappedMs = windowStartMs + ((offset.x / size.width) * visibleSpanMs).roundToLong()
                    onSeek(tappedMs.coerceIn(0L, safeTotalMs))
                }
            }
            .pointerInput(selectedEvent, visibleSpanMs, safeTotalMs, karaokeMode, snapToKeyframes, keyframesMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (size.width <= 0) return@detectDragGestures
                        dragAccumPx = 0f
                        val ev = selectedEvent
                        if (ev != null) {
                            val sX = ((ev.start.millis - windowStartMs).toFloat() / visibleSpanMs) * size.width
                            val eX = ((ev.end.millis - windowStartMs).toFloat() / visibleSpanMs) * size.width

                            if (karaokeMode) {
                                val parsed = KaraokeSyllables.parse(ev.text)
                                if (parsed.size >= 2) {
                                    var accCs = 0L
                                    var hitBoundary = -1
                                    for (b in 0 until parsed.size - 1) {
                                        accCs += parsed[b].centiseconds
                                        val bMs = ev.start.millis + accCs * 10L
                                        val bX = ((bMs - windowStartMs).toFloat() / visibleSpanMs) * size.width
                                        if (abs(offset.x - bX) <= handleHitPx) {
                                            hitBoundary = b
                                            break
                                        }
                                    }
                                    if (hitBoundary >= 0) {
                                        liveSyllables = parsed
                                        dragKind = AudioDragKind.KaraokeBoundary(ev.id, hitBoundary)
                                        return@detectDragGestures
                                    }
                                }
                            }

                            when {
                                abs(offset.x - sX) <= handleHitPx -> {
                                    liveStartMs = ev.start.millis
                                    liveEndMs = ev.end.millis
                                    dragKind = AudioDragKind.StartHandle(ev.id)
                                }
                                abs(offset.x - eX) <= handleHitPx -> {
                                    liveStartMs = ev.start.millis
                                    liveEndMs = ev.end.millis
                                    dragKind = AudioDragKind.EndHandle(ev.id)
                                }
                                offset.x in sX..eX && offset.y > size.height * 0.68f -> {
                                    liveStartMs = ev.start.millis
                                    liveEndMs = ev.end.millis
                                    dragKind = AudioDragKind.MoveEvent(ev.id)
                                }
                                else -> dragKind = AudioDragKind.PanViewport
                            }
                        } else {
                            dragKind = AudioDragKind.PanViewport
                        }
                    },
                    onDragEnd = {
                        when (val k = dragKind) {
                            is AudioDragKind.StartHandle -> {
                                val snapped = if (snapToKeyframes) KeyframesIo.snapToNearest(liveStartMs, keyframesMs, 140L) else liveStartMs
                                onCommitTimes(k.eventId, snapped.coerceIn(0L, liveEndMs), liveEndMs)
                            }
                            is AudioDragKind.EndHandle -> {
                                val snapped = if (snapToKeyframes) KeyframesIo.snapToNearest(liveEndMs, keyframesMs, 140L) else liveEndMs
                                onCommitTimes(k.eventId, liveStartMs, snapped.coerceAtLeast(liveStartMs))
                            }
                            is AudioDragKind.MoveEvent -> {
                                onCommitTimes(k.eventId, liveStartMs.coerceAtLeast(0L), liveEndMs.coerceAtLeast(liveStartMs))
                            }
                            is AudioDragKind.KaraokeBoundary -> {
                                if (liveSyllables.isNotEmpty()) {
                                    onCommitKaraokeText(k.eventId, KaraokeSyllables.build(liveSyllables))
                                }
                            }
                            else -> Unit
                        }
                        dragKind = null
                    },
                    onDragCancel = { dragKind = null },
                ) { change, dragAmount ->
                    change.consume()
                    if (size.width <= 0) return@detectDragGestures
                    val deltaMs = ((dragAmount.x / size.width) * visibleSpanMs).roundToLong()
                    when (val k = dragKind) {
                        is AudioDragKind.StartHandle -> {
                            liveStartMs = (liveStartMs + deltaMs).coerceIn(0L, liveEndMs - 20L)
                        }
                        is AudioDragKind.EndHandle -> {
                            liveEndMs = (liveEndMs + deltaMs).coerceIn(liveStartMs + 20L, safeTotalMs)
                        }
                        is AudioDragKind.MoveEvent -> {
                            val dur = liveEndMs - liveStartMs
                            val ns = (liveStartMs + deltaMs).coerceIn(0L, (safeTotalMs - dur).coerceAtLeast(0L))
                            liveStartMs = ns
                            liveEndMs = ns + dur
                        }
                        is AudioDragKind.KaraokeBoundary -> {
                            dragAccumPx += dragAmount.x
                            val csStepPx = (10f / visibleSpanMs.toFloat()) * size.width
                            if (csStepPx > 0.05f && abs(dragAccumPx) >= csStepPx) {
                                val deltaCs = (dragAccumPx / csStepPx).toLong()
                                if (deltaCs != 0L) {
                                    liveSyllables = KaraokeSyllables.adjust(liveSyllables, k.boundaryIndex, deltaCs)
                                    dragAccumPx -= deltaCs * csStepPx
                                }
                            }
                        }
                        AudioDragKind.PanViewport, null -> {
                            windowStartMs = (windowStartMs - deltaMs).coerceIn(0L, (safeTotalMs - visibleSpanMs).coerceAtLeast(0L))
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 1f || h <= 1f) return@Canvas

            val rulerH = 20.dp.toPx().coerceAtMost(h * 0.22f)
            val bodyTop = rulerH
            val bodyH = (h - rulerH).coerceAtLeast(1f)
            val midY = bodyTop + bodyH / 2f
            val windowEndMs = windowStartMs + visibleSpanMs

            fun msToX(ms: Long): Float = ((ms - windowStartMs).toFloat() / visibleSpanMs.toFloat()) * w

            // 1. 顶部时间标尺背景与刻度
            drawRect(rulerBg, topLeft = Offset.Zero, size = Size(w, rulerH))
            val tickStepMs = when {
                visibleSpanMs <= 4_000L -> 500L
                visibleSpanMs <= 12_000L -> 1_000L
                visibleSpanMs <= 30_000L -> 2_000L
                else -> 5_000L
            }
            val firstTick = (windowStartMs / tickStepMs) * tickStepMs
            var tMs = firstTick
            val labelStyle = TextStyle(color = Color(0xFF94A3B8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            while (tMs <= windowEndMs) {
                if (tMs >= windowStartMs) {
                    val tx = msToX(tMs)
                    drawLine(Color(0xFF475569), Offset(tx, rulerH * 0.55f), Offset(tx, rulerH), strokeWidth = 1f)
                    drawLine(Color(0x1894A3B8), Offset(tx, bodyTop), Offset(tx, h), strokeWidth = 1f)
                    val sec = tMs / 1000
                    val tenth = (tMs % 1000) / 100
                    val txt = "%d:%02d.%d".format(sec / 60, sec % 60, tenth)
                    val m = measurer.measure(txt, style = labelStyle)
                    if (tx + m.size.width + 4f < w) {
                        drawText(m, topLeft = Offset(tx + 3f, 2f))
                    }
                }
                tMs += tickStepMs
            }

            // 2. 频谱或波形绘制（仅截取当前视口区间 [windowStartMs, windowEndMs] 高刷切片）
            if (showSpectrogram && spectrogram != null && spectrogram.frames.isNotEmpty()) {
                val frames = spectrogram.frames
                val totalDur = max(spectrogram.durationMs, safeTotalMs).toFloat()
                val fStart = (((windowStartMs / totalDur) * frames.size).toInt() - 1).coerceAtLeast(0)
                val fEnd = (((windowEndMs / totalDur) * frames.size).toInt() + 1).coerceAtMost(frames.size - 1)
                if (fEnd >= fStart) {
                    val cols = (fEnd - fStart + 1).coerceAtLeast(1)
                    val colW = w / cols
                    for (fi in fStart..fEnd) {
                        val bins = frames[fi]
                        if (bins.isEmpty()) continue
                        val x = ((fi - fStart).toFloat() / cols) * w
                        val binH = bodyH / bins.size
                        for (bi in bins.indices) {
                            val mag = bins[bi].coerceIn(0f, 1f)
                            if (mag < 0.03f) continue
                            val col = spectrogramPalette(mag)
                            val y = h - (bi + 1) * binH
                            drawRect(col, topLeft = Offset(x, y), size = Size(colW + 1f, binH + 0.5f))
                        }
                    }
                }
            } else {
                // 中心零电平线
                drawLine(Color(0x3364748B), Offset(0f, midY), Offset(w, midY), strokeWidth = 1f)
                val peaks = waveform?.peaks
                if (peaks != null && peaks.isNotEmpty()) {
                    val wfTotal = max(waveform.durationMs, safeTotalMs).toFloat()
                    val cols = w.toInt().coerceIn(64, 600)
                    val barW = w / cols
                    val selS = if (dragKind is AudioDragKind.StartHandle || dragKind is AudioDragKind.EndHandle || dragKind is AudioDragKind.MoveEvent) liveStartMs else selectedEvent?.start?.millis ?: -1L
                    val selE = if (dragKind is AudioDragKind.StartHandle || dragKind is AudioDragKind.EndHandle || dragKind is AudioDragKind.MoveEvent) liveEndMs else selectedEvent?.end?.millis ?: -1L

                    for (c in 0 until cols) {
                        val ms0 = windowStartMs + (c.toDouble() / cols * visibleSpanMs).toLong()
                        val ms1 = windowStartMs + ((c + 1).toDouble() / cols * visibleSpanMs).toLong()
                        val idx0 = ((ms0 / wfTotal) * peaks.size).toInt().coerceIn(0, peaks.lastIndex)
                        val idx1 = ((ms1 / wfTotal) * peaks.size).toInt().coerceIn(idx0, peaks.lastIndex)
                        var pMax = 0f
                        for (pi in idx0..idx1) {
                            if (peaks[pi] > pMax) pMax = peaks[pi]
                        }
                        val barH = pMax * (bodyH * 0.46f)
                        if (barH <= 0.5f) continue
                        val x = c * barW
                        val inSel = ms0 in selS..selE
                        drawLine(
                            color = if (inSel) waveActive else waveIdle,
                            start = Offset(x, midY - barH),
                            end = Offset(x, midY + barH),
                            strokeWidth = max(1.2f, barW * 0.88f),
                        )
                    }
                }
            }

            // 3. 关键帧竖线（青粉色虚线）
            if (keyframesMs.isNotEmpty()) {
                val kfDash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
                for (kf in keyframesMs) {
                    if (kf in windowStartMs..windowEndMs) {
                        val kx = msToX(kf)
                        drawLine(keyframeColor, Offset(kx, bodyTop), Offset(kx, h), strokeWidth = 1.5f, pathEffect = kfDash)
                    }
                }
            }

            // 4. 邻近字幕行阴影带 + 当前选中行 Aegisub 起始红线/结束橙线
            events.forEach { ev ->
                if (ev.comment) return@forEach
                val isSel = ev.id == selectedEventId
                val sMs = if (isSel && (dragKind is AudioDragKind.StartHandle || dragKind is AudioDragKind.EndHandle || dragKind is AudioDragKind.MoveEvent)) liveStartMs else ev.start.millis
                val eMs = if (isSel && (dragKind is AudioDragKind.StartHandle || dragKind is AudioDragKind.EndHandle || dragKind is AudioDragKind.MoveEvent)) liveEndMs else ev.end.millis
                if (eMs < windowStartMs || sMs > windowEndMs) return@forEach

                val left = msToX(sMs).coerceIn(-4f, w + 4f)
                val right = msToX(eMs).coerceIn(-4f, w + 4f)
                val boxW = (right - left).coerceAtLeast(2f)

                if (isSel) {
                    drawRect(activeShade, topLeft = Offset(left, bodyTop), size = Size(boxW, bodyH))
                    // 底部拖拽搬移带
                    drawRect(Color(0x5538BDF8), topLeft = Offset(left, h - 14.dp.toPx()), size = Size(boxW, 14.dp.toPx()))
                    // 左红起始手柄线 + 顶部三角翼
                    drawLine(startLineColor, Offset(left, bodyTop), Offset(left, h), strokeWidth = 3.2f)
                    drawRect(startLineColor, topLeft = Offset(left, bodyTop), size = Size(8.dp.toPx(), 12.dp.toPx()))
                    // 右橙结束手柄线 + 顶部三角翼
                    drawLine(endLineColor, Offset(right, bodyTop), Offset(right, h), strokeWidth = 3.2f)
                    drawRect(endLineColor, topLeft = Offset(right - 8.dp.toPx(), bodyTop), size = Size(8.dp.toPx(), 12.dp.toPx()))

                    // 卡拉OK音节切分线
                    if (karaokeMode) {
                        val syls = if (dragKind is AudioDragKind.KaraokeBoundary && liveSyllables.isNotEmpty()) {
                            liveSyllables
                        } else {
                            KaraokeSyllables.parse(ev.text)
                        }
                        if (syls.isNotEmpty()) {
                            val kDash = PathEffect.dashPathEffect(floatArrayOf(7f, 4f), 0f)
                            val sylTextStyle = TextStyle(color = karaokeLineColor, fontSize = 10.sp)
                            var curSylMs = sMs
                            syls.forEachIndexed { idx, syl ->
                                val nextSylMs = curSylMs + syl.centiseconds * 10L
                                val sx = msToX(curSylMs)
                                if (syl.text.isNotBlank() && sx in 0f..(w - 16f)) {
                                    val m = measurer.measure(syl.text.take(6), style = sylTextStyle)
                                    drawText(m, topLeft = Offset(sx + 4f, bodyTop + 14.dp.toPx()))
                                }
                                if (idx < syls.lastIndex && nextSylMs in windowStartMs..windowEndMs) {
                                    val bx = msToX(nextSylMs)
                                    drawLine(karaokeLineColor, Offset(bx, bodyTop), Offset(bx, h), strokeWidth = 2.2f, pathEffect = kDash)
                                }
                                curSylMs = nextSylMs
                            }
                        }
                    }
                } else {
                    drawRect(neighborShade, topLeft = Offset(left, h - 18.dp.toPx()), size = Size(boxW, 18.dp.toPx()))
                    drawRect(Color(0x5594A3B8), topLeft = Offset(left, h - 18.dp.toPx()), size = Size(boxW, 18.dp.toPx()), style = Stroke(width = 1f))
                }
            }

            // 5. 白色实时播放头
            if (positionMs in windowStartMs..windowEndMs) {
                val px = msToX(positionMs)
                drawLine(playheadColor, Offset(px, 0f), Offset(px, h), strokeWidth = 2.2f)
                drawCircle(playheadColor, radius = 4.5f, center = Offset(px, rulerH / 2f))
            }
        }
    }
}

private sealed interface AudioDragKind {
    data class StartHandle(val eventId: Long) : AudioDragKind
    data class EndHandle(val eventId: Long) : AudioDragKind
    data class MoveEvent(val eventId: Long) : AudioDragKind
    data class KaraokeBoundary(val eventId: Long, val boundaryIndex: Int) : AudioDragKind
    data object PanViewport : AudioDragKind
}

private fun spectrogramPalette(v: Float): Color = when {
    v < 0.33f -> {
        val t = v / 0.33f
        Color(red = 0.08f * t, green = 0.12f * t, blue = 0.45f * t + 0.12f)
    }
    v < 0.66f -> {
        val t = (v - 0.33f) / 0.33f
        Color(red = 0.08f + 0.72f * t, green = 0.12f + 0.25f * t, blue = 0.57f - 0.15f * t)
    }
    else -> {
        val t = (v - 0.66f) / 0.34f
        Color(red = 0.80f + 0.20f * t, green = 0.37f + 0.58f * t, blue = 0.42f * (1f - t) + 0.3f * t)
    }
}
