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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
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
 * 桌面级 Aegisub 音频打轴区（Audio Box）：
 * 顶部音频操作工具栏（上/下句、设起/设止、播放句/首500ms/末500ms/前后500ms、加前导/后导、波形/频谱、卡拉OK切分、视口缩放）
 * + 下方 [AegisubAudioBox] 可缩放波形/频谱画布。
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
    audioHeight: Dp = 128.dp,
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

    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        // 音频专用控制栏（对齐桌面 Aegisub Audio Toolbar）
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onSelectPrev, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一行", modifier = Modifier.size(18.dp))
            }
            Button(
                onClick = onSetStartToPos,
                enabled = activeEvent != null,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier.height(28.dp),
            ) {
                Text("[ 设起", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            Button(
                onClick = onSetEndToPos,
                enabled = activeEvent != null,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                modifier = Modifier.height(28.dp),
            ) {
                Text("设止 ]", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            IconButton(onClick = onSelectNext, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一行", modifier = Modifier.size(18.dp))
            }

            SmallToolbarChip("▶本句", onClick = onPlayActiveLine)
            SmallToolbarChip("前导+IN", onClick = onAddLeadIn)
            SmallToolbarChip("后导+OUT", onClick = onAddLeadOut)
            SmallToolbarChip("首0.5s", onClick = onPlayFirst500)
            SmallToolbarChip("末0.5s", onClick = onPlayLast500)
            SmallToolbarChip("前0.5s", onClick = onPlayBefore)
            SmallToolbarChip("后0.5s", onClick = onPlayAfter)
            SmallToolbarChip("✔提交[G]", highlight = true, onClick = onCommitAndNext)

            FilterChip(
                selected = showSpectrogram,
                onClick = { showSpectrogram = !showSpectrogram },
                label = { Text(if (showSpectrogram) "频谱" else "波形", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp),
            )
            FilterChip(
                selected = karaokeMode,
                onClick = { karaokeMode = !karaokeMode },
                label = { Text("卡拉OK切音", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp),
            )
            FilterChip(
                selected = autoScroll,
                onClick = { autoScroll = !autoScroll },
                label = { Text("跟手", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp),
            )

            // 视口时间宽度缩放（2s .. 60s）
            IconButton(
                onClick = { windowSpanMs = (windowSpanMs * 2 / 3).coerceAtLeast(2_000L) },
                modifier = Modifier.size(26.dp),
            ) {
                Icon(Icons.Filled.ZoomIn, contentDescription = "放大音频时间窗", modifier = Modifier.size(16.dp))
            }
            Text(
                "${windowSpanMs / 1000}s窗",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            )
            IconButton(
                onClick = { windowSpanMs = (windowSpanMs * 3 / 2).coerceAtMost(60_000L) },
                modifier = Modifier.size(26.dp),
            ) {
                Icon(Icons.Filled.ZoomOut, contentDescription = "缩小音频时间窗", modifier = Modifier.size(16.dp))
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
            modifier = Modifier.fillMaxWidth().height(audioHeight),
        )
    }
}

/**
 * 桌面级 Aegisub 字幕编辑框（SubsEditBox 完整复刻版 + SGMY AI 按钮 + ASS 标签语法着色）：
 * - 完整字段：注释勾选、样式选择、说话人 Actor、特效 Effect、图层 Layer、开始/结束/时长微调、L/R/V 边距
 * - 快捷格式栏：`\b` 粗体、`\i` 斜体、`\u` 下划线、`\s` 删除线、`1c..4c` 四色调色器、`\N` 换行、`\h` 硬空格、`清标签`
 * - 右侧醒目集成 **SGMY `✨ AI 分析` 按钮**（对齐桌面 `dialog_ai_analysis.cpp` 文本框旁按钮位置！）
 * - 底部实时显示可见字数、CPS 语速与 SGMY 超画幅溢出状态提示。
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
    modifier: Modifier = Modifier,
) {
    if (event == null) {
        Box(modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            Text("当前无选中字幕行", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    var expandMetaInCompact by remember { mutableStateOf(!compactMode) }
    var styleDropdownOpen by remember { mutableStateOf(false) }
    var pickingColorSlot by remember { mutableIntStateOf(0) } // 1..4

    var startInput by remember(event.id, event.start) { mutableStateOf(event.start.toAssString(false)) }
    var endInput by remember(event.id, event.end) { mutableStateOf(event.end.toAssString(false)) }

    val baseStyle = styles.firstOrNull { it.name == event.style } ?: AssStyle()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 第一行：注释勾选 + 样式选择 + 说话人/层 + 展开切换 + 提交按钮 + SGMY AI 按钮
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onCommentChange(!event.comment) },
                ) {
                    Checkbox(
                        checked = event.comment,
                        onCheckedChange = onCommentChange,
                        modifier = Modifier.size(24.dp),
                    )
                    Text("注释", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp))
                }

                // 样式下拉按钮
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
                    "${event.start.toAssString(false)} → ${event.end.toAssString(false)} (${"%.2f".format(event.durationMs / 1000.0)}s)",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextButton(
                    onClick = { expandMetaInCompact = !expandMetaInCompact },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.height(26.dp),
                ) {
                    Text(if (expandMetaInCompact) "收起属性▲" else "详细属性▼", style = MaterialTheme.typography.labelSmall)
                }

                if (aiEnabled) {
                    FilledTonalButton(
                        onClick = onOpenAiAnalysis,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AI 分析", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Button(
                    onClick = onCommitAndNext,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("提交[G]", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 展开元数据行：精确时间输入 + ±50ms 微调 + Layer / Actor / Effect / L-R-V 边距
            if (expandMetaInCompact) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CompactMetaField(
                        label = "起始",
                        value = startInput,
                        width = 102.dp,
                        mono = true,
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
                        width = 102.dp,
                        mono = true,
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
                        label = "层L",
                        value = event.layer.toString(),
                        width = 54.dp,
                        numberOnly = true,
                        onValueChange = { it.toIntOrNull()?.let(onLayerChange) },
                    )
                    CompactMetaField(
                        label = "说话人 Actor",
                        value = event.actor,
                        width = 98.dp,
                        onValueChange = onActorChange,
                    )
                    CompactMetaField(
                        label = "特效 Effect",
                        value = event.effect,
                        width = 98.dp,
                        onValueChange = onEffectChange,
                    )
                    CompactMetaField(
                        label = "左距",
                        value = event.margins.left.toString(),
                        width = 54.dp,
                        numberOnly = true,
                        onValueChange = { v -> v.toIntOrNull()?.let { onMarginsChange(event.margins.copy(left = it)) } },
                    )
                    CompactMetaField(
                        label = "右距",
                        value = event.margins.right.toString(),
                        width = 54.dp,
                        numberOnly = true,
                        onValueChange = { v -> v.toIntOrNull()?.let { onMarginsChange(event.margins.copy(right = it)) } },
                    )
                    CompactMetaField(
                        label = "纵距",
                        value = event.margins.vertical.toString(),
                        width = 54.dp,
                        numberOnly = true,
                        onValueChange = { v -> v.toIntOrNull()?.let { onMarginsChange(event.margins.copy(vertical = it)) } },
                    )
                }
            }

            // ASS 行内快捷格式工具条（B / I / U / S / 四色 1c..4c / \N / \h / \blur / 去标签）
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = {
                        val on = !event.text.contains("\\b1")
                        onTextChange(VisualTags.setInlineToggle(event.text, "b", on))
                    },
                    modifier = Modifier.size(28.dp),
                ) { Icon(Icons.Filled.FormatBold, contentDescription = "\\b 粗体", modifier = Modifier.size(17.dp)) }

                IconButton(
                    onClick = {
                        val on = !event.text.contains("\\i1")
                        onTextChange(VisualTags.setInlineToggle(event.text, "i", on))
                    },
                    modifier = Modifier.size(28.dp),
                ) { Icon(Icons.Filled.FormatItalic, contentDescription = "\\i 斜体", modifier = Modifier.size(17.dp)) }

                IconButton(
                    onClick = {
                        val on = !event.text.contains("\\u1")
                        onTextChange(VisualTags.setInlineToggle(event.text, "u", on))
                    },
                    modifier = Modifier.size(28.dp),
                ) { Icon(Icons.Filled.FormatUnderlined, contentDescription = "\\u 下划线", modifier = Modifier.size(17.dp)) }

                IconButton(
                    onClick = {
                        val on = !event.text.contains("\\s1")
                        onTextChange(VisualTags.setInlineToggle(event.text, "s", on))
                    },
                    modifier = Modifier.size(28.dp),
                ) { Icon(Icons.Filled.FormatStrikethrough, contentDescription = "\\s 删除线", modifier = Modifier.size(17.dp)) }

                ColorSlotDot("1c", baseStyle.primary) { pickingColorSlot = 1 }
                ColorSlotDot("2c", baseStyle.secondary) { pickingColorSlot = 2 }
                ColorSlotDot("3c", baseStyle.outline) { pickingColorSlot = 3 }
                ColorSlotDot("4c", baseStyle.shadow) { pickingColorSlot = 4 }

                SmallToolbarChip("\\N 换行") { onTextChange(event.text + "\\N") }
                SmallToolbarChip("\\h 硬空") { onTextChange(event.text + "\\h") }
                SmallToolbarChip("\\blur1") { onTextChange(VisualTags.setBlur(event.text, 1.0)) }
                SmallToolbarChip("\\fad") { onTextChange(VisualTags.setFade(event.text, 200, 200)) }
                IconButton(
                    onClick = { onTextChange(event.strippedText) },
                    modifier = Modifier.size(28.dp),
                ) { Icon(Icons.Filled.FormatClear, contentDescription = "剥离全部覆盖标签", modifier = Modifier.size(17.dp)) }

                Spacer(Modifier.weight(1f))

                // 实时字数 / CPS / SGMY 超画幅指示器
                val isOver = overflowResult?.isOverflowing == true
                val highCps = overflowResult?.isHighCps == true
                Surface(
                    color = when {
                        isOver -> MaterialTheme.colorScheme.errorContainer
                        highCps -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    val statusLabel = buildString {
                        append("${event.visibleCharCount}字 · ${event.cps} CPS")
                        if (isOver) append(" · ⚠超出画幅")
                        else if (highCps) append(" · 语速偏快")
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = when {
                            isOver -> MaterialTheme.colorScheme.onErrorContainer
                            highCps -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // 主字幕文本编辑框（带 ASS {...} 标签实时语法高亮，1:1 复刻桌面 Scintilla 高亮体验）
            OutlinedTextField(
                value = event.text,
                onValueChange = onTextChange,
                visualTransformation = remember { AssSyntaxVisualTransformation() },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                minLines = if (compactMode) 2 else 3,
                maxLines = if (compactMode) 3 else 5,
                placeholder = { Text("输入字幕文本（支持 {\\pos(x,y)\\fad(200,200)} 等全部 ASS 标签）…") },
                modifier = Modifier.fillMaxWidth(),
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
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(color.r / 255f, color.g / 255f, color.b / 255f, 1f))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp))
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
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.height(26.dp).clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
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
        modifier = Modifier.width(width),
    )
}

/**
 * 零偏移 ASS 覆盖标签语法着色器（严格保持 1:1 字符长度与 [OffsetMapping.Identity]，输入法光标永不错位）：
 * - `{` 与 `}` 括号：柔和青灰
 * - `\tag` 命令名：亮天蓝加粗
 * - `\N` / `\n` / `\h` 转义：琥珀橙高亮
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
        1 -> "\\1c 主填充色"
        2 -> "\\2c 卡拉OK次色"
        3 -> "\\3c 描边色"
        else -> "\\4c 阴影色"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置行内颜色 ($slotName)") },
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
            TextButton(onClick = { onApply(AssColor(r, g, b, 255)) }) { Text("插入标签") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
