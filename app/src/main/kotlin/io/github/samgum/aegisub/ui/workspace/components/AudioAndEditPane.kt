package io.github.samgum.aegisub.ui.workspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.audio.SpectrogramData
import io.github.samgum.aegisub.domain.audio.Waveform
import io.github.samgum.aegisub.domain.edit.VisualTags
import io.github.samgum.aegisub.domain.model.AssColor
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.Margins
import io.github.samgum.aegisub.domain.sgmy.SubtitleOverflowDetector
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.preview.PlaybackState
import io.github.samgum.aegisub.feature.preview.components.AegisubAudioBox

/**
 * 音频打轴操作台：清爽的主打轴按钮 + 次级试听菜单 + 可捏合/滚轮缩放波形频谱画布。
 *
 * @author 伤感咩吖
 */
@Composable
fun AudioTimingDeck(
    waveform: Waveform?,
    spectrogram: SpectrogramData?,
    events: List<AssEvent>,
    activeEvent: AssEvent?,
    playback: PlaybackState,
    keyframesMs: List<Long>,
    snapToKeyframes: Boolean,
    audioHeight: Dp = 132.dp,
    onSelectPrev: () -> Unit,
    onSelectNext: () -> Unit,
    onSetStartToPos: () -> Unit,
    onSetEndToPos: () -> Unit,
    onPlayActiveLine: () -> Unit,
    onPlayBefore: () -> Unit,
    onPlayAfter: () -> Unit,
    onPlayFirst500: () -> Unit,
    onPlayLast500: () -> Unit,
    onAddLeadIn: () -> Unit,
    onAddLeadOut: () -> Unit,
    onCommitAndNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onCommitTimes: (Long, Long, Long) -> Unit,
    onCommitKaraokeText: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSpectrogram by remember { mutableStateOf(false) }
    var karaokeMode by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    var windowSpanMs by remember { mutableLongStateOf(10_000L) }
    var moreTimingMenu by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        // 整洁易读的打轴控制条（高频按钮突出，次级切点试听收纳进菜单）
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = onSelectPrev, modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一句", modifier = Modifier.size(19.dp))
            }

            Button(
                onClick = onSetStartToPos,
                enabled = activeEvent != null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier.height(32.dp).pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text("设为起点", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }

            FilledTonalButton(
                onClick = onPlayActiveLine,
                enabled = activeEvent != null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp).pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text("本句", style = MaterialTheme.typography.labelMedium)
            }

            Button(
                onClick = onSetEndToPos,
                enabled = activeEvent != null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                modifier = Modifier.height(32.dp).pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text("设为终点", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }

            IconButton(onClick = onSelectNext, modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一句", modifier = Modifier.size(19.dp))
            }

            FilterChip(
                selected = showSpectrogram,
                onClick = { showSpectrogram = !showSpectrogram },
                label = { Text(if (showSpectrogram) "频谱图" else "波形图", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(30.dp),
            )

            FilterChip(
                selected = karaokeMode,
                onClick = { karaokeMode = !karaokeMode },
                label = { Text("卡拉OK切音", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(30.dp),
            )

            Box {
                IconButton(
                    onClick = { moreTimingMenu = true },
                    modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "更多打轴微调", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = moreTimingMenu, onDismissRequest = { moreTimingMenu = false }) {
                    DropdownMenuItem(text = { Text("延长前导 (Lead-In)") }, onClick = { moreTimingMenu = false; onAddLeadIn() })
                    DropdownMenuItem(text = { Text("延长后导 (Lead-Out)") }, onClick = { moreTimingMenu = false; onAddLeadOut() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("试听句首 0.5 秒") }, onClick = { moreTimingMenu = false; onPlayFirst500() })
                    DropdownMenuItem(text = { Text("试听句末 0.5 秒") }, onClick = { moreTimingMenu = false; onPlayLast500() })
                    DropdownMenuItem(text = { Text("试听切入前 0.5 秒") }, onClick = { moreTimingMenu = false; onPlayBefore() })
                    DropdownMenuItem(text = { Text("试听切出后 0.5 秒") }, onClick = { moreTimingMenu = false; onPlayAfter() })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (autoScroll) "✓ 播放头自动翻页跟随" else "播放头自动翻页跟随") },
                        onClick = { autoScroll = !autoScroll; moreTimingMenu = false },
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            IconButton(
                onClick = { windowSpanMs = (windowSpanMs * 2 / 3).coerceAtLeast(2_000L) },
                modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(Icons.Filled.ZoomIn, contentDescription = "放大时间窗", modifier = Modifier.size(16.dp))
            }
            Text(
                "${windowSpanMs / 1000}s",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = { windowSpanMs = (windowSpanMs * 3 / 2).coerceAtMost(60_000L) },
                modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(Icons.Filled.ZoomOut, contentDescription = "缩小时间窗", modifier = Modifier.size(16.dp))
            }
        }

        AegisubAudioBox(
            waveform = waveform,
            spectrogram = spectrogram,
            showSpectrogram = showSpectrogram,
            events = events,
            selectedEventId = activeEvent?.id,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            windowDurationMs = windowSpanMs,
            autoScroll = autoScroll,
            snapToKeyframes = snapToKeyframes,
            keyframesMs = keyframesMs,
            karaokeMode = karaokeMode,
            onSeek = onSeek,
            onCommitTimes = onCommitTimes,
            onCommitKaraokeText = onCommitKaraokeText,
            onWindowDurationChange = { windowSpanMs = it },
            modifier = Modifier.fillMaxWidth().height(audioHeight),
        )
    }
}

/**
 * 清爽直观的字幕编辑框（按需展开详细属性与格式条，完美兼容外接物理键盘回车提交与焦点隔离）。
 */
@Composable
fun AegisubSubsEditBox(
    event: AssEvent?,
    styles: List<AssStyle>,
    overflowResult: SubtitleOverflowDetector.OverflowResult?,
    aiEnabled: Boolean,
    compactMode: Boolean = false,
    onCommentChange: (Boolean) -> Unit,
    onStyleChange: (String) -> Unit,
    onActorChange: (String) -> Unit,
    onEffectChange: (String) -> Unit,
    onLayerChange: (Int) -> Unit,
    onTimesChange: (Long, Long) -> Unit,
    onNudgeStart: (Long) -> Unit,
    onNudgeEnd: (Long) -> Unit,
    onMarginsChange: (Margins) -> Unit,
    onTextChange: (String) -> Unit,
    onSetInlineColor: (Int, AssColor) -> Unit,
    onCommitAndNext: () -> Unit,
    onOpenAiAnalysis: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (event == null) {
        Box(modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            Text("点击列表选择字幕行", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    var showDetailFields by remember { mutableStateOf(!compactMode) }
    var showFormatStrip by remember { mutableStateOf(!compactMode) }
    var styleDropdownOpen by remember { mutableStateOf(false) }
    var pickingColorSlot by remember { mutableIntStateOf(0) }

    var startInput by remember(event.id, event.start) { mutableStateOf(event.start.toAssString(false)) }
    var endInput by remember(event.id, event.end) { mutableStateOf(event.end.toAssString(false)) }

    val baseStyle = styles.firstOrNull { it.name == event.style } ?: AssStyle()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 顶部简洁状态与开关栏
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { styleDropdownOpen = true },
                        label = { Text(event.style, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp),
                    )
                    DropdownMenu(expanded = styleDropdownOpen, onDismissRequest = { styleDropdownOpen = false }) {
                        styles.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st.name) },
                                onClick = { onStyleChange(st.name); styleDropdownOpen = false },
                            )
                        }
                    }
                }

                Text(
                    "${event.start.toAssString(false)} – ${event.end.toAssString(false)} (${"%.1f".format(event.durationMs / 1000.0)}s)",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FilterChip(
                    selected = event.comment,
                    onClick = { onCommentChange(!event.comment) },
                    label = { Text("注释", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                )

                if (compactMode) {
                    IconButton(
                        onClick = { showDetailFields = !showDetailFields },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = "时间与边距属性",
                            tint = if (showDetailFields) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    IconButton(
                        onClick = { showFormatStrip = !showFormatStrip },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.FormatSize,
                            contentDescription = "文字格式与颜色",
                            tint = if (showFormatStrip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // 字数与溢出提示（仅正常显示精简字数，溢出时变红）
                val isOver = overflowResult?.isOverflowing == true
                val highCps = overflowResult?.isHighCps == true
                if (event.visibleCharCount > 0 || isOver) {
                    Surface(
                        color = when {
                            isOver -> MaterialTheme.colorScheme.errorContainer
                            highCps -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        val infoText = when {
                            isOver -> "${event.visibleCharCount}字 · 超出画幅"
                            highCps -> "${event.visibleCharCount}字 · ${event.cps}字/秒"
                            else -> "${event.visibleCharCount}字"
                        }
                        Text(
                            text = infoText,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isOver -> MaterialTheme.colorScheme.onErrorContainer
                                highCps -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                if (aiEnabled) {
                    IconButton(
                        onClick = onOpenAiAnalysis,
                        modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = "AI 润色与分析",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Button(
                    onClick = onCommitAndNext,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("下一句", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 详细时间微调与层/说话人/边距面板（桌面常驻，手机按需展开）
            if (showDetailFields) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CompactMetaField(
                        label = "开始",
                        value = startInput,
                        width = 100.dp,
                        mono = true,
                        onFocusChanged = onInputFocusChanged,
                        onValueChange = { raw ->
                            startInput = raw
                            runCatching { SubTime.parseAss(raw) }.onSuccess { s ->
                                onTimesChange(s.millis, event.end.millis)
                            }
                        },
                    )
                    SmallToolbarChip("-50ms") { onNudgeStart(-50L) }
                    SmallToolbarChip("+50ms") { onNudgeStart(50L) }

                    CompactMetaField(
                        label = "结束",
                        value = endInput,
                        width = 100.dp,
                        mono = true,
                        onFocusChanged = onInputFocusChanged,
                        onValueChange = { raw ->
                            endInput = raw
                            runCatching { SubTime.parseAss(raw) }.onSuccess { e ->
                                onTimesChange(event.start.millis, e.millis)
                            }
                        },
                    )
                    SmallToolbarChip("-50ms") { onNudgeEnd(-50L) }
                    SmallToolbarChip("+50ms") { onNudgeEnd(50L) }

                    CompactMetaField(
                        label = "图层",
                        value = event.layer.toString(),
                        width = 56.dp,
                        numberOnly = true,
                        onFocusChanged = onInputFocusChanged,
                        onValueChange = { it.toIntOrNull()?.let(onLayerChange) },
                    )
                    CompactMetaField(
                        label = "说话人",
                        value = event.actor,
                        width = 92.dp,
                        onFocusChanged = onInputFocusChanged,
                        onValueChange = onActorChange,
                    )
                    CompactMetaField(
                        label = "特效备注",
                        value = event.effect,
                        width = 92.dp,
                        onFocusChanged = onInputFocusChanged,
                        onValueChange = onEffectChange,
                    )
                    CompactMetaField(
                        label = "左距",
                        value = event.margins.left.toString(),
                        width = 54.dp,
                        numberOnly = true,
                        onFocusChanged = onInputFocusChanged,
                        onValueChange = { v -> v.toIntOrNull()?.let { onMarginsChange(event.margins.copy(left = it)) } },
                    )
                    CompactMetaField(
                        label = "右距",
                        value = event.margins.right.toString(),
                        width = 54.dp,
                        numberOnly = true,
                        onFocusChanged = onInputFocusChanged,
                        onValueChange = { v -> v.toIntOrNull()?.let { onMarginsChange(event.margins.copy(right = it)) } },
                    )
                    CompactMetaField(
                        label = "纵距",
                        value = event.margins.vertical.toString(),
                        width = 54.dp,
                        numberOnly = true,
                        onFocusChanged = onInputFocusChanged,
                        onValueChange = { v -> v.toIntOrNull()?.let { onMarginsChange(event.margins.copy(vertical = it)) } },
                    )
                }
            }

            // 行内格式与颜色条（桌面常驻，手机按需展开）
            if (showFormatStrip) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = { onTextChange(VisualTags.setInlineToggle(event.text, "b", !event.text.contains("\\b1"))) },
                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) { Icon(Icons.Filled.FormatBold, contentDescription = "加粗", modifier = Modifier.size(17.dp)) }

                    IconButton(
                        onClick = { onTextChange(VisualTags.setInlineToggle(event.text, "i", !event.text.contains("\\i1"))) },
                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) { Icon(Icons.Filled.FormatItalic, contentDescription = "斜体", modifier = Modifier.size(17.dp)) }

                    IconButton(
                        onClick = { onTextChange(VisualTags.setInlineToggle(event.text, "u", !event.text.contains("\\u1"))) },
                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) { Icon(Icons.Filled.FormatUnderlined, contentDescription = "下划线", modifier = Modifier.size(17.dp)) }

                    IconButton(
                        onClick = { onTextChange(VisualTags.setInlineToggle(event.text, "s", !event.text.contains("\\s1"))) },
                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) { Icon(Icons.Filled.FormatStrikethrough, contentDescription = "删除线", modifier = Modifier.size(17.dp)) }

                    ColorSlotDot("主色", baseStyle.primary) { pickingColorSlot = 1 }
                    ColorSlotDot("次色", baseStyle.secondary) { pickingColorSlot = 2 }
                    ColorSlotDot("描边", baseStyle.outline) { pickingColorSlot = 3 }
                    ColorSlotDot("阴影", baseStyle.shadow) { pickingColorSlot = 4 }

                    SmallToolbarChip("换行 \\N") { onTextChange(event.text + "\\N") }
                    SmallToolbarChip("淡入淡出") { onTextChange(VisualTags.setFade(event.text, 200, 200)) }
                    SmallToolbarChip("柔化边缘") { onTextChange(VisualTags.setBlur(event.text, 1.2)) }

                    IconButton(
                        onClick = { onTextChange(event.strippedText) },
                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) { Icon(Icons.Filled.FormatClear, contentDescription = "清除样式标签", modifier = Modifier.size(17.dp)) }
                }
            }

            // 台词输入框：支持键盘 Enter 提交下一句、Shift+Enter 插入 \N、Ctrl+B/I/U 快捷加粗斜体
            OutlinedTextField(
                value = event.text,
                onValueChange = onTextChange,
                visualTransformation = remember { AssSyntaxVisualTransformation() },
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = if (compactMode) 1 else 2,
                maxLines = if (compactMode) 3 else 4,
                placeholder = { Text("输入字幕内容…") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onInputFocusChanged(it.isFocused) }
                    .onPreviewKeyEvent { ke ->
                        if (ke.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            (ke.key == Key.Enter || ke.key == Key.NumPadEnter) && !ke.isShiftPressed && !ke.isCtrlPressed -> {
                                onCommitAndNext()
                                true
                            }
                            (ke.key == Key.Enter || ke.key == Key.NumPadEnter) && ke.isShiftPressed -> {
                                onTextChange(event.text + "\\N")
                                true
                            }
                            ke.isCtrlPressed && ke.key == Key.B -> {
                                onTextChange(VisualTags.setInlineToggle(event.text, "b", !event.text.contains("\\b1")))
                                true
                            }
                            ke.isCtrlPressed && ke.key == Key.I -> {
                                onTextChange(VisualTags.setInlineToggle(event.text, "i", !event.text.contains("\\i1")))
                                true
                            }
                            ke.isCtrlPressed && ke.key == Key.U -> {
                                onTextChange(VisualTags.setInlineToggle(event.text, "u", !event.text.contains("\\u1")))
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
    }

    if (pickingColorSlot in 1..4) {
        val initial = when (pickingColorSlot) {
            1 -> baseStyle.primary
            2 -> baseStyle.secondary
            3 -> baseStyle.outline
            else -> baseStyle.shadow
        }
        QuickAssColorDialog(
            slot = pickingColorSlot,
            initial = initial,
            onDismiss = { pickingColorSlot = 0 },
            onApply = { c ->
                onSetInlineColor(pickingColorSlot, c)
                pickingColorSlot = 0
            },
        )
    }
}

@Composable
private fun ColorSlotDot(label: String, color: AssColor, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 3.dp),
    ) {
        Box(
            Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(Color(color.r / 255f, color.g / 255f, color.b / 255f, 1f))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 3.dp))
    }
}

@Composable
private fun SmallToolbarChip(
    label: String,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .height(28.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CompactMetaField(
    label: String,
    value: String,
    width: Dp,
    mono: Boolean = false,
    numberOnly: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.let {
            if (mono) it.copy(fontFamily = FontFamily.Monospace) else it
        },
        keyboardOptions = if (numberOnly) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.width(width).onFocusChanged { onFocusChanged(it.isFocused) },
    )
}

/**
 * 零偏移 ASS 标签语法高亮器（保持 1:1 字符索引映射，光标零漂移）。
 */
private class AssSyntaxVisualTransformation : VisualTransformation {
    private val braceStyle = SpanStyle(color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
    private val tagCmdStyle = SpanStyle(color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
    private val tagArgStyle = SpanStyle(color = Color(0xFFA7F3D0))
    private val escapeStyle = SpanStyle(color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val builder = AnnotatedString.Builder(raw)
        var i = 0
        while (i < raw.length) {
            when {
                raw[i] == '{' -> {
                    val close = raw.indexOf('}', i + 1)
                    val end = if (close < 0) raw.length else close + 1
                    builder.addStyle(braceStyle, i, i + 1)
                    if (close >= 0) builder.addStyle(braceStyle, close, close + 1)
                    val bodyEnd = if (close < 0) raw.length else close
                    if (bodyEnd > i + 1) {
                        builder.addStyle(tagArgStyle, i + 1, bodyEnd)
                        var p = i + 1
                        while (p < bodyEnd) {
                            if (raw[p] == '\\') {
                                val cmdStart = p
                                p++
                                while (p < bodyEnd && (raw[p].isLetter() || (p == cmdStart + 1 && raw[p] in '1'..'4'))) {
                                    p++
                                }
                                builder.addStyle(tagCmdStyle, cmdStart, p)
                            } else {
                                p++
                            }
                        }
                    }
                    i = end
                }
                raw[i] == '\\' && i + 1 < raw.length && (raw[i + 1] == 'N' || raw[i + 1] == 'n' || raw[i + 1] == 'h') -> {
                    builder.addStyle(escapeStyle, i, i + 2)
                    i += 2
                }
                else -> i++
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
private fun QuickAssColorDialog(
    slot: Int,
    initial: AssColor,
    onDismiss: () -> Unit,
    onApply: (AssColor) -> Unit,
) {
    var r by remember { mutableIntStateOf(initial.r) }
    var g by remember { mutableIntStateOf(initial.g) }
    var b by remember { mutableIntStateOf(initial.b) }
    val slotName = when (slot) {
        1 -> "主填充色 (\\1c)"
        2 -> "次填充色 (\\2c)"
        3 -> "描边色 (\\3c)"
        else -> "阴影色 (\\4c)"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择$slotName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color(r / 255f, g / 255f, b / 255f, 1f)),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("R", Modifier.width(20.dp))
                    Slider(value = r.toFloat(), onValueChange = { r = it.toInt() }, valueRange = 0f..255f, modifier = Modifier.weight(1f))
                    Text("$r", Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("G", Modifier.width(20.dp))
                    Slider(value = g.toFloat(), onValueChange = { g = it.toInt() }, valueRange = 0f..255f, modifier = Modifier.weight(1f))
                    Text("$g", Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("B", Modifier.width(20.dp))
                    Slider(value = b.toFloat(), onValueChange = { b = it.toInt() }, valueRange = 0f..255f, modifier = Modifier.weight(1f))
                    Text("$b", Modifier.width(32.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(AssColor(r, g, b, 255)) }) { Text("应用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
