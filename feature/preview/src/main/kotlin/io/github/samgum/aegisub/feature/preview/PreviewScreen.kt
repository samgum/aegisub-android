package io.github.samgum.aegisub.feature.preview

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
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
import io.github.samgum.aegisub.feature.preview.R
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

/** 预览下半区分段：字幕列表 / 音频波形 / 时间打轴 / 可视化打字。 */
enum class PreviewPanel { SUBTITLES, AUDIO, TIMING, TYPES }

/**
 * 精简视频块：仅视频画面（+字幕叠加 + 可视化打字拖拽层）+ 播放控制。
 * 波形/时间面板/打字控件移入分段面板，避免在竖屏挤占字幕列表空间。
 *
 * @author 伤感咩吖
 */
@Composable
private fun VideoBlock(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
    vtActive: Boolean,
    vtToolMode: VisualToolMode,
    onVtToolModeChange: (VisualToolMode) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            if (vtActive && state.hasMedia && selectedEvent != null) {
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
            if (vtActive) {
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
    }
}

/**
 * 下半区分段条（字幕 / 音频 / 时间 / 打字）。
 */
@Composable
private fun PreviewTabs(
    panel: PreviewPanel,
    onPanelChange: (PreviewPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        PreviewPanel.SUBTITLES to stringResource(R.string.preview_tab_subtitles),
        PreviewPanel.AUDIO to stringResource(R.string.preview_tab_audio),
        PreviewPanel.TIMING to stringResource(R.string.preview_tab_timing),
        PreviewPanel.TYPES to stringResource(R.string.preview_tab_types),
    )
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { (p, label) ->
            FilterChip(selected = panel == p, onClick = { onPanelChange(p) }, label = { Text(label) })
        }
    }
}

/**
 * 下半区内容：按分段渲染，占满剩余空间（字幕列表因此总是可达且宽敞）。
 */
@Composable
private fun PreviewPanelContent(
    panel: PreviewPanel,
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    showSpectrogram: Boolean,
    onShowSpectrogramChange: (Boolean) -> Unit,
    vtToolMode: VisualToolMode,
    onVtToolModeChange: (VisualToolMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedEvent = state.script.events.firstOrNull { it.id == state.selectedEventId }
    val waveform by viewModel.waveform.collectAsStateWithLifecycle()
    val spectrogram by viewModel.spectrogram.collectAsStateWithLifecycle()
    when (panel) {
        PreviewPanel.SUBTITLES -> EventListColumn(
            events = state.script.events,
            currentEventId = state.currentEventId,
            selectedEventId = state.selectedEventId,
            onSelect = viewModel::selectEvent,
            modifier = modifier,
        )

        PreviewPanel.AUDIO -> Column(modifier) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onShowSpectrogramChange(false) }) { Text("波形") }
                TextButton(onClick = { onShowSpectrogramChange(true) }) { Text("频谱") }
            }
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

        PreviewPanel.TIMING -> Column(modifier.verticalScroll(rememberScrollState())) {
            if (selectedEvent != null) {
                TimingToolbar(state = state, viewModel = viewModel)
                TimingEditLayer(state = state, viewModel = viewModel)
            } else {
                Text(stringResource(R.string.timing_pick_row), modifier = Modifier.padding(16.dp))
            }
        }

        PreviewPanel.TYPES -> Column(modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
            if (selectedEvent != null && state.hasMedia) {
                Text(stringResource(R.string.types_title), style = MaterialTheme.typography.titleSmall)
                VisualTypesettingControls(
                    event = selectedEvent,
                    toolMode = vtToolMode,
                    onToolModeChange = onVtToolModeChange,
                    onRotationChange = { deg -> viewModel.setEventRotation(selectedEvent.id, deg) },
                    onFadeChange = { fin, fout -> viewModel.setEventFade(selectedEvent.id, fin, fout) },
                    onClearPos = { viewModel.clearEventPos(selectedEvent.id) },
                    onClearMove = { viewModel.clearEventMove(selectedEvent.id) },
                    onClipChange = { x1, y1, x2, y2, inv ->
                        viewModel.setEventClip(selectedEvent.id, x1, y1, x2, y2, inv)
                    },
                    onClearClip = { viewModel.clearEventClip(selectedEvent.id) },
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Karaoke 计时", style = MaterialTheme.typography.titleSmall)
                KaraokeTimeline(
                    text = selectedEvent.text,
                    onCommit = { viewModel.setEventText(selectedEvent.id, it) },
                )
            } else {
                Text(stringResource(R.string.types_pick_row), modifier = Modifier.padding(16.dp))
            }
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
    onClipChange: (x1: Int, y1: Int, x2: Int, y2: Int, inverse: Boolean) -> Unit,
    onClearClip: () -> Unit,
) {
    var slider by remember(event.id) { mutableStateOf(VisualTags.getRotation(event.text).toFloat()) }
    val existingFade = remember(event.id, event.text) { VisualTags.getFade(event.text) }
    var fadeIn by remember(event.id) { mutableStateOf((existingFade?.fadeIn ?: 0).toString()) }
    var fadeOut by remember(event.id) { mutableStateOf((existingFade?.fadeOut ?: 0).toString()) }
    val existingClip = remember(event.id, event.text) { VisualTags.getClip(event.text) }
    var cx1 by remember(event.id) { mutableStateOf((existingClip?.x1 ?: 0).toString()) }
    var cy1 by remember(event.id) { mutableStateOf((existingClip?.y1 ?: 0).toString()) }
    var cx2 by remember(event.id) { mutableStateOf((existingClip?.x2 ?: 0).toString()) }
    var cy2 by remember(event.id) { mutableStateOf((existingClip?.y2 ?: 0).toString()) }
    var clipInverse by remember(event.id) { mutableStateOf(existingClip?.inverse ?: false) }
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
        // 矩形裁剪 {\clip}/{\iclip}
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\\clip", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = cx1, onValueChange = { cx1 = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("x1") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            )
            OutlinedTextField(
                value = cy1, onValueChange = { cy1 = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("y1") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            )
            OutlinedTextField(
                value = cx2, onValueChange = { cx2 = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("x2") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            )
            OutlinedTextField(
                value = cy2, onValueChange = { cy2 = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("y2") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !clipInverse,
                onClick = { clipInverse = false },
                label = { Text("\\clip 显示区内") },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = clipInverse,
                onClick = { clipInverse = true },
                label = { Text("\\iclip 反向") },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClearClip) { Text("清除") }
            Button(onClick = {
                onClipChange(
                    cx1.toIntOrNull() ?: 0, cy1.toIntOrNull() ?: 0,
                    cx2.toIntOrNull() ?: 0, cy2.toIntOrNull() ?: 0, clipInverse,
                )
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
    var panel by remember { mutableStateOf(PreviewPanel.SUBTITLES) }
    var vtToolMode by remember { mutableStateOf(VisualToolMode.POSITION) }
    var showSpectrogram by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        VideoBlock(
            state = state,
            viewModel = viewModel,
            onPickVideo = onPickVideo,
            vtActive = panel == PreviewPanel.TYPES && state.hasMedia,
            vtToolMode = vtToolMode,
            onVtToolModeChange = { vtToolMode = it },
        )
        PreviewTabs(panel = panel, onPanelChange = { panel = it })
        PreviewPanelContent(
            panel = panel,
            state = state,
            viewModel = viewModel,
            showSpectrogram = showSpectrogram,
            onShowSpectrogramChange = { showSpectrogram = it },
            vtToolMode = vtToolMode,
            onVtToolModeChange = { vtToolMode = it },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExpandedPreview(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
) {
    var panel by remember { mutableStateOf(PreviewPanel.SUBTITLES) }
    var vtToolMode by remember { mutableStateOf(VisualToolMode.POSITION) }
    var showSpectrogram by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize()) {
        VideoBlock(
            state = state,
            viewModel = viewModel,
            onPickVideo = onPickVideo,
            vtActive = panel == PreviewPanel.TYPES && state.hasMedia,
            vtToolMode = vtToolMode,
            onVtToolModeChange = { vtToolMode = it },
            modifier = Modifier.weight(0.55f),
        )
        Column(modifier = Modifier.weight(0.45f)) {
            PreviewTabs(panel = panel, onPanelChange = { panel = it })
            PreviewPanelContent(
                panel = panel,
                state = state,
                viewModel = viewModel,
                showSpectrogram = showSpectrogram,
                onShowSpectrogramChange = { showSpectrogram = it },
                vtToolMode = vtToolMode,
                onVtToolModeChange = { vtToolMode = it },
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
                text = event.strippedText.ifBlank { stringResource(R.string.subtitle_no_text) },
                maxLines = 2,
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
