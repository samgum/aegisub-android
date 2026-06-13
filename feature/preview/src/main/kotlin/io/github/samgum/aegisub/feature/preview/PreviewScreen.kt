package io.github.samgum.aegisub.feature.preview

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.preview.components.NudgeTarget
import io.github.samgum.aegisub.feature.preview.components.PlayerSurface
import io.github.samgum.aegisub.feature.preview.components.SubtitleOverlay
import io.github.samgum.aegisub.feature.preview.components.TimelineBar
import io.github.samgum.aegisub.feature.preview.components.TimingEditPanel

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
                        when {
                            event.key == Key.Spacebar -> { viewModel.playPause(); true }
                            event.key == Key.DirectionLeft -> { viewModel.seekRelative(-5_000); true }
                            event.key == Key.DirectionRight -> { viewModel.seekRelative(5_000); true }
                            event.isCtrlPressed && event.key == Key.Z -> { viewModel.undo(); true }
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
            if (!state.hasMedia) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("未挂载视频", color = Color.White)
                    Button(onClick = onPickVideo) { Text("选择视频") }
                }
            }
        }
        PlaybackControls(
            playback = state.playback,
            onPlayPause = viewModel::playPause,
            onSeek = viewModel::seekTo,
            onSpeedChange = viewModel::setSpeed,
        )
        TimelineBar(
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

/** ms → "M:SS" 或 "H:MM:SS"。 */
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
