package io.github.samgum.aegisub.feature.preview

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.domain.edit.VisualTags
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.preview.components.NudgeTarget
import io.github.samgum.aegisub.feature.preview.components.PlayerSurface
import io.github.samgum.aegisub.feature.preview.components.SubtitleOverlay
import io.github.samgum.aegisub.feature.preview.components.AudioTimeline
import io.github.samgum.aegisub.feature.preview.components.KaraokeTimeline
import io.github.samgum.aegisub.feature.preview.components.SpectrogramView
import io.github.samgum.aegisub.feature.preview.components.TimingEditPanel
import io.github.samgum.aegisub.feature.preview.components.VisualToolMode
import io.github.samgum.aegisub.feature.preview.components.VisualTypesettingOverlay

/**
 * 预览屏入口：加载→分发（Loading/Error/Loaded）→ compact/expanded。
 * SAF 选片在本屏发起，结果回写 ViewModel.attachMedia。
 * 顶栏撤销、选中行展开时间编辑面板、视频下只读时间轴条。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            viewModel.attachMedia(uri.toString())
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预览") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { pickVideo.launch(arrayOf("video/*")) }) {
                        Icon(Icons.Filled.Movie, contentDescription = "更换视频")
                    }
                    IconButton(onClick = viewModel::undo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        val selectedId = (state as? PreviewUiState.Loaded)?.selectedEventId
                        when {
                            event.key == Key.Spacebar -> { viewModel.playPause(); true }
                            // Shift+左/右 = 逐帧后退/前进（桌面端 Ctrl+左/右 等价的帧级步进）
                            event.isShiftPressed && event.key == Key.DirectionLeft -> { viewModel.frameStepBack(); true }
                            event.isShiftPressed && event.key == Key.DirectionRight -> { viewModel.frameStepForward(); true }
                            event.key == Key.DirectionLeft -> { viewModel.seekRelative(-5_000); true }
                            event.key == Key.DirectionRight -> { viewModel.seekRelative(5_000); true }
                            event.isCtrlPressed && event.key == Key.Z -> { viewModel.undo(); true }
                            // 桌面端 Aegisub timing 热键约定：W 上行 / A 设起始 / S 设结束 / D 提交并下行
                            event.key == Key.W -> { viewModel.selectPrevEvent(); true }
                            event.key == Key.A && selectedId != null -> { viewModel.setStartToPosition(selectedId); true }
                            event.key == Key.S && selectedId != null -> { viewModel.setEndToPosition(selectedId); true }
                            event.key == Key.D -> { viewModel.selectNextEvent(); true }
                            else -> false
                        }
                    }
                },
        ) {
            when (val s = state) {
                PreviewUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is PreviewUiState.Error ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载失败：${s.message}")
                    }

                is PreviewUiState.Loaded -> {
                    val isCompact = LocalConfiguration.current.screenWidthDp < 600
                    if (isCompact) {
                        CompactPreview(
                            state = s,
                            viewModel = viewModel,
                            onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
                        )
                    } else {
                        ExpandedPreview(
                            state = s,
                            viewModel = viewModel,
                            onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoBlock(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSpectrogram by remember { mutableStateOf(false) }
    var vtMode by remember { mutableStateOf(false) }
    var vtToolMode by remember { mutableStateOf(VisualToolMode.POSITION) }
    var karaokeMode by remember { mutableStateOf(false) }
    val selectedEvent = state.script.events.firstOrNull { it.id == state.selectedEventId }
    val playResX = state.script.getScriptInfo("PlayResX")?.toIntOrNull() ?: 384
    val playResY = state.script.getScriptInfo("PlayResY")?.toIntOrNull() ?: 288
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            PlayerSurface(player = viewModel.videoPlayer, modifier = Modifier.fillMaxSize())
            ActiveSubtitleLayer(viewModel = viewModel)
            // 可视化打字：选中行 + 挂载视频时，在画面上拖拽设 {\pos}/{\move}
            if (vtMode && state.hasMedia && selectedEvent != null) {
                VisualTypesettingOverlay(
                    playResX = playResX,
                    playResY = playResY,
                    mode = vtToolMode,
                    currentPos = VisualTags.getPos(selectedEvent.text),
                    currentMove = VisualTags.getMove(selectedEvent.text),
                    onPosChange = { x, y -> viewModel.setEventPos(selectedEvent.id, x, y) },
                    onMoveChange = { x1, y1, x2, y2 ->
                        viewModel.setEventMove(selectedEvent.id, x1, y1, x2, y2)
                    },
                )
            }
            if (!state.hasMedia) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("未挂载视频", color = Color.White)
                    Button(onClick = onPickVideo) { Text("选择视频") }
                }
            }
            // 可视化打字模式徽标
            if (vtMode) {
                Text(
                    if (vtToolMode == VisualToolMode.POSITION) "可视化打字：拖拽设 \\pos"
                    else "可视化打字：拖拽起点(绿)/终点(橙)设 \\move",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                )
            }
        }
        PlaybackControls(
            playback = state.playback,
            onPlayPause = viewModel::playPause,
            onSeek = viewModel::seekTo,
            onSpeedChange = viewModel::setSpeed,
            onFrameBack = viewModel::frameStepBack,
            onFrameForward = viewModel::frameStepForward,
        )
        // 可视化打字控件：模式切换 + 旋转 {\fr} 滑块 + 淡入淡出 {\fad} + 清除
        if (vtMode && selectedEvent != null) {
            VisualTypesettingControls(
                event = selectedEvent,
                toolMode = vtToolMode,
                onToolModeChange = { vtToolMode = it },
                onRotationChange = { deg -> viewModel.setEventRotation(selectedEvent.id, deg) },
                onFadeChange = { fin, fout -> viewModel.setEventFade(selectedEvent.id, fin, fout) },
                onClearPos = { viewModel.clearEventPos(selectedEvent.id) },
                onClearMove = { viewModel.clearEventMove(selectedEvent.id) },
            )
        }
        // Karaoke 音节计时：拖拽音节边界逐音节调 {\k} 时长
        if (karaokeMode && selectedEvent != null) {
            Text(
                "Karaoke 计时：拖拽音节分隔条在相邻音节间挪动时长",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
            KaraokeTimeline(
                text = selectedEvent.text,
                onCommit = { viewModel.setEventText(selectedEvent.id, it) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        // 音频可视化切换：波形 / 频谱 + 可视化打字开关
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showSpectrogram = !showSpectrogram }) {
                Text(if (showSpectrogram) "切到波形" else "切到频谱")
            }
            TextButton(onClick = { vtMode = !vtMode }) {
                Text(if (vtMode) "退出可视化打字" else "可视化打字")
            }
            TextButton(onClick = { karaokeMode = !karaokeMode }) {
                Text(if (karaokeMode) "退出Karaoke计时" else "Karaoke计时")
            }
        }
        val waveform by viewModel.waveform.collectAsStateWithLifecycle()
        val spectrogram by viewModel.spectrogram.collectAsStateWithLifecycle()
        if (showSpectrogram) {
            SpectrogramView(
                data = spectrogram,
                positionMs = state.playback.positionMs,
                durationMs = state.playback.durationMs,
            )
        } else {
            AudioTimeline(
                waveform = waveform,
                events = state.script.events,
                selectedEventId = state.selectedEventId,
                positionMs = state.playback.positionMs,
                durationMs = state.playback.durationMs,
                onCommitDrag = { id, startMs, endMs ->
                    viewModel.editEventTimes(id, SubTime.ofMillis(startMs), SubTime.ofMillis(endMs))
                    viewModel.selectEvent(id)
                },
            )
        }
    }
}

/**
 * 可视化打字控件：模式切换（定位/移动）+ 旋转 {\fr} 滑块 + 淡入淡出 {\fad} + 清除。
 */
@Composable
private fun VisualTypesettingControls(
    event: AssEvent,
    toolMode: VisualToolMode,
    onToolModeChange: (VisualToolMode) -> Unit,
    onRotationChange: (Int) -> Unit,
    onFadeChange: (fadeIn: Int, fadeOut: Int) -> Unit,
    onClearPos: () -> Unit,
    onClearMove: () -> Unit,
) {
    var slider by remember(event.id) { mutableStateOf(VisualTags.getRotation(event.text).toFloat()) }
    val existingFade = remember(event.id, event.text) { VisualTags.getFade(event.text) }
    var fadeIn by remember(event.id) { mutableStateOf((existingFade?.fadeIn ?: 0).toString()) }
    var fadeOut by remember(event.id) { mutableStateOf((existingFade?.fadeOut ?: 0).toString()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // 模式切换
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = toolMode == VisualToolMode.POSITION,
                onClick = { onToolModeChange(VisualToolMode.POSITION) },
                label = { Text("\\pos 定位") },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = toolMode == VisualToolMode.MOVE,
                onClick = { onToolModeChange(VisualToolMode.MOVE) },
                label = { Text("\\move 移动") },
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = if (toolMode == VisualToolMode.MOVE) onClearMove else onClearPos,
            ) {
                Text(if (toolMode == VisualToolMode.MOVE) "清 \\move" else "清 \\pos")
            }
        }
        // 旋转
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\\fr", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = slider,
                onValueChange = { slider = it },
                valueRange = 0f..359f,
                onValueChangeFinished = { onRotationChange(slider.roundToInt()) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("${slider.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
        }
        // 淡入淡出
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\\fad", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = fadeIn,
                onValueChange = { fadeIn = it.filter { ch -> ch.isDigit() } },
                label = { Text("淡入 ms") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            OutlinedTextField(
                value = fadeOut,
                onValueChange = { fadeOut = it.filter { ch -> ch.isDigit() } },
                label = { Text("淡出 ms") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            TextButton(onClick = {
                onFadeChange(fadeIn.toIntOrNull() ?: 0, fadeOut.toIntOrNull() ?: 0)
            }) { Text("应用") }
        }
    }
}

/**
 * 活动字幕层：独立订阅 [PreviewViewModel.activeSubtitle]（仅事件切换时变化），
 * 与 VideoBlock 的 50ms 位置 tick 重组解耦，降低低端机每帧开销。
 */
@Composable
private fun ActiveSubtitleLayer(viewModel: PreviewViewModel) {
    val info by viewModel.activeSubtitle.collectAsStateWithLifecycle()
    SubtitleOverlay(renderInfo = info, modifier = Modifier.fillMaxSize())
}

@Composable
private fun PlaybackControls(
    playback: PlaybackState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onFrameBack: () -> Unit,
    onFrameForward: () -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var speedExpanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "播放/暂停",
                )
            }
            // 逐帧：后退 / 前进（仅挂载视频后有意义）
            IconButton(onClick = onFrameBack, enabled = playback.isReady) {
                Text("◀▏", style = MaterialTheme.typography.titleSmall)
            }
            IconButton(onClick = onFrameForward, enabled = playback.isReady) {
                Text("▕▶", style = MaterialTheme.typography.titleSmall)
            }
            Text(formatTime(playback.positionMs), style = MaterialTheme.typography.bodySmall)
            Box {
                TextButton(onClick = { speedExpanded = true }) { Text("${playback.speed}x") }
                DropdownMenu(expanded = speedExpanded, onDismissRequest = { speedExpanded = false }) {
                    speeds.forEach { rate ->
                        DropdownMenuItem(
                            text = { Text("${rate}x") },
                            onClick = { onSpeedChange(rate); speedExpanded = false },
                        )
                    }
                }
            }
            Text(" / ${formatTime(playback.durationMs)}", style = MaterialTheme.typography.bodySmall)
            // 帧率显示（未知则不显示数值）
            if (playback.fps > 0f) {
                Text(
                    "  ${"%.2f".format(playback.fps)}fps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        val ratio = if (playback.durationMs > 0) {
            (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
        } else 0f
        Slider(
            value = ratio,
            onValueChange = { v ->
                if (playback.durationMs > 0) onSeek((v * playback.durationMs).toLong())
            },
        )
    }
}

@Composable
private fun CompactPreview(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        VideoBlock(state = state, viewModel = viewModel, onPickVideo = onPickVideo)
        if (state.selectedEventId != null) {
            TimingToolbar(state = state, viewModel = viewModel)
            TimingEditLayer(state = state, viewModel = viewModel)
        }
        EventListColumn(
            events = state.script.events,
            currentEventId = state.currentEventId,
            selectedEventId = state.selectedEventId,
            onSelect = viewModel::selectEvent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ExpandedPreview(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        VideoBlock(
            state = state,
            viewModel = viewModel,
            onPickVideo = onPickVideo,
            modifier = Modifier.weight(0.6f),
        )
        Column(modifier = Modifier.weight(0.4f)) {
            if (state.selectedEventId != null) {
                TimingEditLayer(state = state, viewModel = viewModel)
            }
            EventListColumn(
                events = state.script.events,
                currentEventId = state.currentEventId,
                selectedEventId = state.selectedEventId,
                onSelect = viewModel::selectEvent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun EventListColumn(
    events: List<AssEvent>,
    currentEventId: Long?,
    selectedEventId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(events, key = { it.id }) { event ->
            PreviewEventRow(
                event = event,
                isCurrent = event.id == currentEventId,
                isSelected = event.id == selectedEventId,
                onClick = { onSelect(event.id) },
            )
        }
    }
}

@Composable
private fun PreviewEventRow(event: AssEvent, isCurrent: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    ListItem(
        headlineContent = {
            Text(
                text = event.strippedText.ifBlank { "（无文本）" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (event.comment) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = "${event.start.toAssString(false)} → ${event.end.toAssString(false)}  ·  ${event.style}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier = Modifier.background(bg).clickable(onClick = onClick),
    )
}

/**
 * 选中行的时间编辑承载：本地持有微调目标（起/止），把面板事件接到 ViewModel。
 * 选中行切换时重置微调目标为「起」。
 */
@Composable
private fun TimingEditLayer(state: PreviewUiState.Loaded, viewModel: PreviewViewModel) {
    val event = state.script.events.firstOrNull { it.id == state.selectedEventId } ?: return
    var target by remember(state.selectedEventId) { mutableStateOf(NudgeTarget.START) }
    TimingEditPanel(
        startMs = event.start.millis,
        endMs = event.end.millis,
        durationMs = state.playback.durationMs,
        nudgeTarget = target,
        onNudgeTargetChange = { target = it },
        onSeek = viewModel::seekTo,
        onCommitStart = { ms -> viewModel.editEventTimes(event.id, SubTime.ofMillis(ms), event.end) },
        onCommitEnd = { ms -> viewModel.editEventTimes(event.id, event.start, SubTime.ofMillis(ms)) },
        onNudge = { delta ->
            when (target) {
                NudgeTarget.START -> viewModel.nudgeStart(delta)
                NudgeTarget.END -> viewModel.nudgeEnd(delta)
            }
        },
    )
}

/**
 * 打轴工具栏（选中行时显示）：设起始/结束 = 当前播放位置 + 上下行导航。
 * 配合播放做踩点打轴——听到台词起止时点对应按钮。
 *
 * @author 伤感咩吖
 */
@Composable
private fun TimingToolbar(state: PreviewUiState.Loaded, viewModel: PreviewViewModel) {
    val id = state.selectedEventId ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::selectPrevEvent) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "上一行")
        }
        Button(onClick = { viewModel.setStartToPosition(id) }) { Text("设起始") }
        Button(onClick = { viewModel.setEndToPosition(id) }) { Text("设结束") }
        IconButton(onClick = viewModel::selectNextEvent) {
            Icon(Icons.Filled.SkipNext, contentDescription = "下一行")
        }
    }
}

/** ms → "M:SS" 或 "H:MM:SS"。 */
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
