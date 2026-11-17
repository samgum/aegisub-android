package io.github.samgum.aegisub.ui.workspace

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.data.hotkeys.rememberHotkeyController
import io.github.samgum.aegisub.domain.edit.HotkeyAction
import io.github.samgum.aegisub.domain.format.SubtitleFormat
import io.github.samgum.aegisub.ui.adaptive.AdaptiveWindowState
import io.github.samgum.aegisub.ui.adaptive.WorkspacePosture
import io.github.samgum.aegisub.ui.adaptive.rememberAdaptiveWindowState
import io.github.samgum.aegisub.ui.workspace.components.AegisubSubsEditBox
import io.github.samgum.aegisub.ui.workspace.components.AegisubSubtitleGrid
import io.github.samgum.aegisub.ui.workspace.components.AudioTimingDeck
import io.github.samgum.aegisub.ui.workspace.components.CompactStudioTopBar
import io.github.samgum.aegisub.ui.workspace.components.DesktopMenuAndToolbar
import io.github.samgum.aegisub.ui.workspace.components.VideoStagePane
import io.github.samgum.aegisub.ui.workspace.components.WorkspaceDialogType
import io.github.samgum.aegisub.ui.workspace.dialogs.WorkspaceDialogHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 统一自适应字幕工程工作台（Aegisub SGMY Studio Workspace）：
 * 单一屏幕同时集成视频舞台、8 工具可视化打字、可缩放音频打轴、语法高亮编辑框、高密度多列网格，
 * 并针对 **5 大硬件形态姿态** 自动切换最佳工程布局：
 * - [WorkspacePosture.DESKTOP_THREE_ZONE]：平板 / 折叠全展 / DeX → 1:1 复刻桌面 Aegisub 经典上双区 + 下全宽多列网格（带可拖拽分割条）
 * - [WorkspacePosture.BOOK_DUAL_PANE]：折叠屏书本/垂直折痕 → 左页媒体与波形打轴 + 铰链安全留白 + 右页编辑框与字幕网格
 * - [WorkspacePosture.TABLETOP]：折叠屏半开悬停/水平折痕 → 上半屏纯净视频舞台 + 折痕留白 + 下半屏触控音频打轴与网格控制台
 * - [WorkspacePosture.LANDSCAPE_SPLIT]：手机横屏 / 小平板 → 左右可调比例双栏工作台
 * - [WorkspacePosture.PHONE_COMPACT]：手机竖屏 → 可切换紧凑上舞台（影音同屏/专注打轴/专注画面/纯文本）+ 编辑框 + 列表 + 拇指底栏
 *
 * @author 伤感咩吖
 */
@Composable
fun WorkspaceScreen(
    onBack: () -> Unit,
    onOpenStyles: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val waveform by viewModel.waveform.collectAsStateWithLifecycle()
    val spectrogram by viewModel.spectrogram.collectAsStateWithLifecycle()
    val keyframesMs by viewModel.keyframesMs.collectAsStateWithLifecycle()
    val activeSubtitles by viewModel.activeSubtitles.collectAsStateWithLifecycle()
    val visualToolMode by viewModel.visualToolMode.collectAsStateWithLifecycle()
    val displayPreset by viewModel.displayPreset.collectAsStateWithLifecycle()
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val hotkeys = rememberHotkeyController()

    var activeDialog by remember { mutableStateOf<WorkspaceDialogType?>(null) }
    var visualOverlayEnabled by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<SubtitleFormat?>(null) }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    // SAF 媒体选择器（视频或纯音频皆可）
    val pickMediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.attachMedia(uri.toString())
        }
    }

    // SAF 关键帧导入选择器
    val importKeyframesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                if (content.isNotBlank()) viewModel.importKeyframesText(content)
            }
        }
    }

    // SAF 关键帧导出选择器
    val exportKeyframesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val content = viewModel.exportKeyframesText()
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
            }
            viewModel.postBanner("关键帧文件已导出")
        }
    }

    // SAF 字幕格式转换导出器
    val exportSubtitleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val fmt = pendingExportFormat
        pendingExportFormat = null
        if (uri != null && fmt != null) {
            scope.launch {
                val out = viewModel.exportAs(fmt)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(out) }
                }
                viewModel.postBanner("已导出为 ${fmt.name} 格式")
            }
        }
    }

    when (val state = uiState) {
        WorkspaceUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is WorkspaceUiState.Error -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("工程加载失败：${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }

        is WorkspaceUiState.Ready -> {
            val adaptive = rememberAdaptiveWindowState(state.settings.layoutMode)
            val activeEvent = remember(state.script.events, state.activeEventId) {
                state.script.events.firstOrNull { it.id == state.activeEventId } ?: state.script.events.firstOrNull()
            }
            val activeOverflow = activeEvent?.id?.let { state.overflowMap[it] }

            fun copyLinesToClipboard() {
                val txt = viewModel.copySelectedLinesAsAss()
                if (txt.isNotEmpty()) {
                    clipboard.setText(AnnotatedString(txt))
                    viewModel.postBanner("已复制字幕行到剪贴板")
                }
            }

            fun pasteLinesFromClipboard() {
                val raw = clipboard.getText()?.text.orEmpty()
                if (raw.isNotBlank()) {
                    viewModel.pasteAssOrTextLines(raw)
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .onPreviewKeyEvent { keyEv ->
                        val action = hotkeys.match(keyEv) ?: return@onPreviewKeyEvent false
                        when (action) {
                            HotkeyAction.UNDO -> viewModel.undo()
                            HotkeyAction.REDO -> viewModel.redo()
                            HotkeyAction.SAVE -> viewModel.takeSnapshot("Ctrl+S 快照")
                            HotkeyAction.EXPORT -> activeDialog = WorkspaceDialogType.EXPORT
                            HotkeyAction.FIND_REPLACE -> activeDialog = WorkspaceDialogType.FIND_REPLACE
                            HotkeyAction.DUPLICATE_LINE -> viewModel.duplicateTargetLines()
                            HotkeyAction.DELETE_LINE -> viewModel.deleteTargetLines()
                            HotkeyAction.SPLIT_LINE -> viewModel.splitActiveLine(atVideoTime = state.hasMedia)
                            HotkeyAction.JOIN_KEEP_FIRST -> viewModel.joinTargetLines(WorkspaceViewModel.JoinMode.KEEP_FIRST)
                            HotkeyAction.JOIN_CONCAT -> viewModel.joinTargetLines(WorkspaceViewModel.JoinMode.CONCAT)
                            HotkeyAction.JOIN_KARAOKE -> viewModel.joinTargetLines(WorkspaceViewModel.JoinMode.AS_KARAOKE)
                            HotkeyAction.MOVE_LINE_UP -> viewModel.moveTargetLinesUp()
                            HotkeyAction.MOVE_LINE_DOWN -> viewModel.moveTargetLinesDown()
                            HotkeyAction.INSERT_BEFORE -> viewModel.insertLineBefore(false)
                            HotkeyAction.INSERT_AFTER -> viewModel.insertLineAfter(false)
                            HotkeyAction.COPY_LINES -> copyLinesToClipboard()
                            HotkeyAction.PASTE_LINES -> pasteLinesFromClipboard()
                            HotkeyAction.SELECT_ALL -> viewModel.selectAll()
                            HotkeyAction.SELECT_PREV -> viewModel.selectPrevEvent()
                            HotkeyAction.SELECT_NEXT -> viewModel.selectNextEvent()
                            HotkeyAction.COMMIT_AND_NEXT -> viewModel.commitAndAdvance()
                            HotkeyAction.PLAY_PAUSE -> viewModel.playPause()
                            HotkeyAction.PLAY_LINE -> viewModel.playActiveLine()
                            HotkeyAction.SEEK_BACK -> viewModel.seekRelative(-5_000L)
                            HotkeyAction.SEEK_FORWARD -> viewModel.seekRelative(5_000L)
                            HotkeyAction.FRAME_BACK -> viewModel.frameStepBack()
                            HotkeyAction.FRAME_FORWARD -> viewModel.frameStepForward()
                            HotkeyAction.SPEED_DOWN -> viewModel.adjustSpeed(-0.25f)
                            HotkeyAction.SPEED_UP -> viewModel.adjustSpeed(0.25f)
                            HotkeyAction.SPEED_RESET -> viewModel.setSpeed(1.0f)
                            HotkeyAction.SET_START_TO_POS -> viewModel.setStartToVideo()
                            HotkeyAction.SET_END_TO_POS -> viewModel.setEndToVideo()
                            HotkeyAction.ADD_LEAD_IN -> viewModel.addLeadIn()
                            HotkeyAction.ADD_LEAD_OUT -> viewModel.addLeadOut()
                            HotkeyAction.FIX_COMMON_ERRORS -> activeDialog = WorkspaceDialogType.SGMY_FIX_COMMON_ERRORS
                            HotkeyAction.LYRIC_SCROLL -> activeDialog = WorkspaceDialogType.SGMY_LYRIC_SCROLL
                            HotkeyAction.CYCLE_TAG_HIDING -> viewModel.cycleTagDisplayMode()
                        }
                        true
                    },
            ) { innerPadding ->
                // 封装四大核心子视图，确保在 5 种姿态之间切换时状态平滑延续
                val renderTopDesktopBar: @Composable () -> Unit = {
                    DesktopMenuAndToolbar(
                        projectName = state.projectName,
                        adaptiveState = adaptive,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        tagDisplayMode = state.settings.tagDisplayMode,
                        displayPreset = displayPreset,
                        hasMedia = state.hasMedia,
                        onBack = onBack,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onPickVideo = { pickMediaLauncher.launch(arrayOf("video/*", "audio/*")) },
                        onCloseVideo = viewModel::closeMedia,
                        onImportKeyframes = { importKeyframesLauncher.launch(arrayOf("text/*", "*/*")) },
                        onExportKeyframes = { exportKeyframesLauncher.launch("${state.projectName}.keyframes.txt") },
                        onOpenStyleManager = { onOpenStyles(viewModel.projectId) },
                        onOpenSettings = onOpenSettings,
                        onOpenAbout = onOpenAbout,
                        onCycleTagMode = viewModel::cycleTagDisplayMode,
                        onSetDisplayPreset = viewModel::setDisplayPreset,
                        onOpenDialog = { activeDialog = it },
                        onQuickSnapshot = { viewModel.takeSnapshot("手动保存点") },
                        onInsertBefore = { viewModel.insertLineBefore(false) },
                        onInsertAfter = { viewModel.insertLineAfter(false) },
                        onDuplicateLines = viewModel::duplicateTargetLines,
                        onSplitLine = { viewModel.splitActiveLine(state.hasMedia) },
                        onSplitByKaraoke = viewModel::splitActiveByKaraoke,
                        onJoinConcat = { viewModel.joinTargetLines(WorkspaceViewModel.JoinMode.CONCAT) },
                        onJoinKeepFirst = { viewModel.joinTargetLines(WorkspaceViewModel.JoinMode.KEEP_FIRST) },
                        onJoinKaraoke = { viewModel.joinTargetLines(WorkspaceViewModel.JoinMode.AS_KARAOKE) },
                        onRecombine = { viewModel.joinTargetLines(WorkspaceViewModel.JoinMode.RECOMBINE) },
                        onMakeContinuousEnd = { viewModel.makeTimesContinuous(changeEnd = true) },
                        onMakeContinuousStart = { viewModel.makeTimesContinuous(changeEnd = false) },
                        onCopyLines = ::copyLinesToClipboard,
                        onPasteLines = ::pasteLinesFromClipboard,
                        onSelectAll = viewModel::selectAll,
                        onDeleteEmpty = viewModel::deleteEmptyLines,
                    )
                }

                val renderCompactTopBar: @Composable () -> Unit = {
                    CompactStudioTopBar(
                        projectName = state.projectName,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        tagDisplayMode = state.settings.tagDisplayMode,
                        onBack = onBack,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onPickVideo = { pickMediaLauncher.launch(arrayOf("video/*", "audio/*")) },
                        onOpenStyleManager = { onOpenStyles(viewModel.projectId) },
                        onOpenSettings = onOpenSettings,
                        onCycleTagMode = viewModel::cycleTagDisplayMode,
                        onOpenDialog = { activeDialog = it },
                    )
                }

                val renderVideoStage: @Composable (Modifier, Boolean) -> Unit = { mod, compact ->
                    VideoStagePane(
                        script = state.script,
                        activeEvent = activeEvent,
                        renderInfos = activeSubtitles,
                        hasMedia = state.hasMedia,
                        playback = state.playback,
                        player = viewModel.videoPlayer,
                        visualToolMode = visualToolMode,
                        visualOverlayEnabled = visualOverlayEnabled,
                        onVisualOverlayEnabledChange = { visualOverlayEnabled = it },
                        onVisualToolModeChange = viewModel::setVisualToolMode,
                        onPickVideo = { pickMediaLauncher.launch(arrayOf("video/*", "audio/*")) },
                        onPlayPause = viewModel::playPause,
                        onPlayLine = viewModel::playActiveLine,
                        onSeek = viewModel::seekTo,
                        onFrameBack = viewModel::frameStepBack,
                        onFrameForward = viewModel::frameStepForward,
                        onSpeedChange = viewModel::setSpeed,
                        onPosChange = viewModel::setEventPos,
                        onMoveChange = { x1, y1, x2, y2 -> viewModel.setEventMove(x1, y1, x2, y2) },
                        onRotationZChange = viewModel::setEventRotationZ,
                        onRotation3dChange = viewModel::setEventRotation3d,
                        onOriginChange = viewModel::setEventOrigin,
                        onScaleChange = viewModel::setEventScale,
                        onClipRectChange = viewModel::setEventClipRect,
                        onVectorClipChange = viewModel::setEventVectorClip,
                        onClearVisualTags = {
                            viewModel.clearEventPos()
                            viewModel.clearEventMove()
                            viewModel.clearEventClips()
                        },
                        compactControls = compact,
                        modifier = mod,
                    )
                }

                val renderAudioDeck: @Composable (Modifier, androidx.compose.ui.unit.Dp) -> Unit = { mod, h ->
                    AudioTimingDeck(
                        waveform = waveform,
                        spectrogram = spectrogram,
                        events = state.script.events,
                        activeEvent = activeEvent,
                        playback = state.playback,
                        keyframesMs = keyframesMs,
                        snapToKeyframes = state.settings.snapToKeyframes,
                        audioHeight = h,
                        onSelectPrev = viewModel::selectPrevEvent,
                        onSelectNext = viewModel::selectNextEvent,
                        onSetStartToPos = viewModel::setStartToVideo,
                        onSetEndToPos = { viewModel.setEndToVideo() },
                        onPlayActiveLine = viewModel::playActiveLine,
                        onPlayBefore = viewModel::playBeforeActiveLine,
                        onPlayAfter = viewModel::playAfterActiveLine,
                        onPlayFirst500 = viewModel::playFirst500ms,
                        onPlayLast500 = viewModel::playLast500ms,
                        onAddLeadIn = viewModel::addLeadIn,
                        onAddLeadOut = viewModel::addLeadOut,
                        onCommitAndNext = viewModel::commitAndAdvance,
                        onSeek = viewModel::seekTo,
                        onCommitTimes = viewModel::updateEventTimes,
                        onCommitKaraokeText = viewModel::updateEventText,
                        modifier = mod,
                    )
                }

                val renderEditBox: @Composable (Modifier, Boolean) -> Unit = { mod, compact ->
                    AegisubSubsEditBox(
                        event = activeEvent,
                        styles = state.script.styles,
                        overflowResult = activeOverflow,
                        aiEnabled = state.settings.aiEnabled,
                        compactMode = compact,
                        onCommentChange = { c -> activeEvent?.let { viewModel.updateEventComment(it.id, c) } },
                        onStyleChange = { st -> activeEvent?.let { viewModel.updateEventStyle(it.id, st) } },
                        onActorChange = { ac -> activeEvent?.let { viewModel.updateEventActor(it.id, ac) } },
                        onEffectChange = { ef -> activeEvent?.let { viewModel.updateEventEffect(it.id, ef) } },
                        onLayerChange = { ly -> activeEvent?.let { viewModel.updateEventLayer(it.id, ly) } },
                        onTimesChange = { s, e -> activeEvent?.let { viewModel.updateEventTimes(it.id, s, e) } },
                        onNudgeStart = viewModel::nudgeStart,
                        onNudgeEnd = viewModel::nudgeEnd,
                        onMarginsChange = { m -> activeEvent?.let { viewModel.updateEventMargins(it.id, m) } },
                        onTextChange = { t -> activeEvent?.let { viewModel.updateEventText(it.id, t) } },
                        onSetInlineColor = viewModel::setInlineColor,
                        onCommitAndNext = viewModel::commitAndAdvance,
                        onOpenAiAnalysis = { activeDialog = WorkspaceDialogType.SGMY_AI_ANALYSIS },
                        modifier = mod,
                    )
                }

                val renderGrid: @Composable (Modifier, Boolean) -> Unit = { mod, wide ->
                    AegisubSubtitleGrid(
                        events = state.script.events,
                        activeEventId = state.activeEventId,
                        currentVisibleEventId = state.currentVisibleEventId,
                        selectedIds = state.selectedIds,
                        tagDisplayMode = state.settings.tagDisplayMode,
                        overflowMap = state.overflowMap,
                        wideTableMode = wide,
                        onSelectRow = { id -> viewModel.selectEvent(id) },
                        onToggleSelectRow = viewModel::toggleSelection,
                        onSelectRangeTo = viewModel::selectRangeTo,
                        onSelectAll = viewModel::selectAll,
                        onClearSelection = viewModel::clearSelection,
                        onSort = viewModel::sortLines,
                        onInsertBefore = viewModel::insertLineBefore,
                        onInsertAfter = viewModel::insertLineAfter,
                        onDuplicate = viewModel::duplicateTargetLines,
                        onDelete = viewModel::deleteTargetLines,
                        onMoveUp = viewModel::moveTargetLinesUp,
                        onMoveDown = viewModel::moveTargetLinesDown,
                        onSplitMidpoint = { viewModel.splitActiveLine(false) },
                        onSplitVideoTime = { viewModel.splitActiveLine(true) },
                        onSplitKaraoke = viewModel::splitActiveByKaraoke,
                        onJoin = viewModel::joinTargetLines,
                        onSwapTwo = viewModel::swapSelectedTwoLines,
                        onMakeContinuous = viewModel::makeTimesContinuous,
                        onCopyLines = ::copyLinesToClipboard,
                        onPasteLines = ::pasteLinesFromClipboard,
                        onOpenPasteOver = { activeDialog = WorkspaceDialogType.PASTE_OVER },
                        modifier = mod,
                    )
                }

                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    when (adaptive.posture) {
                        WorkspacePosture.DESKTOP_THREE_ZONE -> DesktopThreeZoneLayout(
                            displayPreset = displayPreset,
                            topBar = renderTopDesktopBar,
                            videoStage = renderVideoStage,
                            audioDeck = renderAudioDeck,
                            editBox = renderEditBox,
                            subtitleGrid = renderGrid,
                        )

                        WorkspacePosture.BOOK_DUAL_PANE -> FoldableBookDualPaneLayout(
                            adaptive = adaptive,
                            topBar = renderTopDesktopBar,
                            videoStage = renderVideoStage,
                            audioDeck = renderAudioDeck,
                            editBox = renderEditBox,
                            subtitleGrid = renderGrid,
                        )

                        WorkspacePosture.TABLETOP -> FoldableTabletopLayout(
                            adaptive = adaptive,
                            topBar = renderCompactTopBar,
                            videoStage = renderVideoStage,
                            audioDeck = renderAudioDeck,
                            editBox = renderEditBox,
                            subtitleGrid = renderGrid,
                        )

                        WorkspacePosture.LANDSCAPE_SPLIT -> LandscapeSplitLayout(
                            topBar = renderCompactTopBar,
                            videoStage = renderVideoStage,
                            audioDeck = renderAudioDeck,
                            editBox = renderEditBox,
                            subtitleGrid = renderGrid,
                        )

                        WorkspacePosture.PHONE_COMPACT -> PhoneCompactStudioLayout(
                            topBar = renderCompactTopBar,
                            videoStage = renderVideoStage,
                            audioDeck = renderAudioDeck,
                            editBox = renderEditBox,
                            subtitleGrid = renderGrid,
                            onOpenStyles = { onOpenStyles(viewModel.projectId) },
                            onOpenFixErrors = { activeDialog = WorkspaceDialogType.SGMY_FIX_COMMON_ERRORS },
                            onOpenExport = { activeDialog = WorkspaceDialogType.EXPORT },
                        )
                    }
                }

                WorkspaceDialogHost(
                    dialogType = activeDialog,
                    script = state.script,
                    activeEvent = activeEvent,
                    selectedIds = state.selectedIds,
                    settings = state.settings,
                    snapshots = snapshots,
                    bookmarks = bookmarks,
                    viewModel = viewModel,
                    onSwitchDialog = { activeDialog = it },
                    onExportFormatPicked = { fmt ->
                        pendingExportFormat = fmt
                        val ext = fmt.extensions.firstOrNull() ?: "ass"
                        exportSubtitleLauncher.launch("${state.projectName}.$ext")
                    },
                )
            }
        }
    }
}

// ============================================================================
// 5 大硬件姿态专属布局实现
// ============================================================================

/**
 * 1. 大屏平板 / 折叠全展 / DeX 桌面模式：1:1 复刻桌面 `samgum/Aegisub` 三区+可拖拽分割器布局！
 * - 上半区：左侧视频舞台 ↔ 可拖拽垂直分割条 ↔ 右侧音频波形 + 字幕编辑框
 * - 可拖拽水平分割条
 * - 下半区：全宽 9 列专业字幕网格
 */
@Composable
private fun DesktopThreeZoneLayout(
    displayPreset: WorkspaceDisplayPreset,
    topBar: @Composable () -> Unit,
    videoStage: @Composable (Modifier, Boolean) -> Unit,
    audioDeck: @Composable (Modifier, androidx.compose.ui.unit.Dp) -> Unit,
    editBox: @Composable (Modifier, Boolean) -> Unit,
    subtitleGrid: @Composable (Modifier, Boolean) -> Unit,
) {
    var topHeightFraction by remember { mutableFloatStateOf(0.54f) }
    var leftWidthFraction by remember { mutableFloatStateOf(0.45f) }
    val density = LocalDensity.current

    Column(Modifier.fillMaxSize()) {
        topBar()

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val totalH = maxHeight
            val totalW = maxWidth
            val totalHPx = with(density) { totalH.toPx().coerceAtLeast(1f) }
            val totalWPx = with(density) { totalW.toPx().coerceAtLeast(1f) }

            Column(Modifier.fillMaxSize()) {
                if (displayPreset != WorkspaceDisplayPreset.SUBS_ONLY) {
                    val upperH = totalH * topHeightFraction
                    Row(Modifier.fillMaxWidth().height(upperH)) {
                        when (displayPreset) {
                            WorkspaceDisplayPreset.FULL -> {
                                val leftW = totalW * leftWidthFraction
                                videoStage(Modifier.width(leftW).fillMaxHeight(), false)
                                VerticalDragSplitter { dx ->
                                    leftWidthFraction = (leftWidthFraction + dx / totalWPx).coerceIn(0.25f, 0.72f)
                                }
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    audioDeck(Modifier.fillMaxWidth(), 132.dp)
                                    HorizontalDivider()
                                    editBox(Modifier.fillMaxWidth(), false)
                                }
                            }

                            WorkspaceDisplayPreset.VIDEO_SUBS -> {
                                val leftW = totalW * leftWidthFraction
                                videoStage(Modifier.width(leftW).fillMaxHeight(), false)
                                VerticalDragSplitter { dx ->
                                    leftWidthFraction = (leftWidthFraction + dx / totalWPx).coerceIn(0.28f, 0.75f)
                                }
                                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                    editBox(Modifier.fillMaxWidth(), false)
                                }
                            }

                            WorkspaceDisplayPreset.AUDIO_SUBS -> {
                                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    audioDeck(Modifier.fillMaxWidth(), 150.dp)
                                    HorizontalDivider()
                                    editBox(Modifier.fillMaxWidth(), false)
                                }
                            }

                            WorkspaceDisplayPreset.SUBS_ONLY -> Unit
                        }
                    }

                    HorizontalDragSplitter { dy ->
                        topHeightFraction = (topHeightFraction + dy / totalHPx).coerceIn(0.28f, 0.75f)
                    }
                } else {
                    editBox(Modifier.fillMaxWidth(), false)
                    HorizontalDivider()
                }

                subtitleGrid(Modifier.weight(1f).fillMaxWidth(), true)
            }
        }
    }
}

/**
 * 2. 折叠屏书本半开 / 双屏垂直铰链姿态 (Book Posture)：
 * - 左页：完整视频预览与可视化打字舞台 + 下方音频波形/卡拉OK打轴台
 * - 中间：精确避让物理折痕铰链（`hingeThicknessDp`）
 * - 右页：完整桌面菜单工具栏 + 语法高亮编辑框 + 字幕网格
 */
@Composable
private fun FoldableBookDualPaneLayout(
    adaptive: AdaptiveWindowState,
    topBar: @Composable () -> Unit,
    videoStage: @Composable (Modifier, Boolean) -> Unit,
    audioDeck: @Composable (Modifier, androidx.compose.ui.unit.Dp) -> Unit,
    editBox: @Composable (Modifier, Boolean) -> Unit,
    subtitleGrid: @Composable (Modifier, Boolean) -> Unit,
) {
    val leftW = adaptive.bookLeftWidthDp ?: (adaptive.widthDp / 2).dp
    val rightWide = (adaptive.widthDp.dp - leftW - adaptive.hingeThicknessDp) >= 520.dp

    Row(Modifier.fillMaxSize()) {
        // 左书页：沉浸影音与打轴页
        Column(Modifier.width(leftW).fillMaxHeight()) {
            videoStage(Modifier.weight(1f).fillMaxWidth(), true)
            HorizontalDivider()
            audioDeck(Modifier.fillMaxWidth(), 144.dp)
        }

        // 物理折痕避让区
        Box(
            Modifier
                .width(adaptive.hingeThicknessDp.coerceAtLeast(8.dp))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )

        // 右书页：文本编辑与网格控制台
        Column(Modifier.weight(1f).fillMaxHeight()) {
            topBar()
            editBox(Modifier.fillMaxWidth(), true)
            HorizontalDivider()
            subtitleGrid(Modifier.weight(1f).fillMaxWidth(), rightWide)
        }
    }
}

/**
 * 3. 折叠屏水平半折悬停姿态 (Tabletop / Laptop Posture)：
 * - 折痕上方屏幕立起：100% 纯净无遮挡视频舞台（含可视化打字）
 * - 折痕安全留白条
 * - 折痕下方平放桌面：像笔记本键盘面一样的触控打轴+编辑+网格控制台！
 */
@Composable
private fun FoldableTabletopLayout(
    adaptive: AdaptiveWindowState,
    topBar: @Composable () -> Unit,
    videoStage: @Composable (Modifier, Boolean) -> Unit,
    audioDeck: @Composable (Modifier, androidx.compose.ui.unit.Dp) -> Unit,
    editBox: @Composable (Modifier, Boolean) -> Unit,
    subtitleGrid: @Composable (Modifier, Boolean) -> Unit,
) {
    val topH = adaptive.tabletopTopHeightDp ?: (adaptive.heightDp * 0.45f).dp
    var consoleTab by remember { mutableIntStateOf(0) } // 0 = 音频+编辑, 1 = 字幕网格

    Column(Modifier.fillMaxSize()) {
        // 上半屏：立式视频监看舞台
        videoStage(Modifier.fillMaxWidth().height(topH), true)

        // 水平铰链避让区
        Box(
            Modifier
                .fillMaxWidth()
                .height(adaptive.hingeThicknessDp.coerceAtLeast(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )

        // 下半屏：桌面触控操作台
        Column(Modifier.weight(1f).fillMaxWidth()) {
            topBar()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(selected = consoleTab == 0, onClick = { consoleTab = 0 }, label = { Text("打轴波形 + 编辑框") })
                FilterChip(selected = consoleTab == 1, onClick = { consoleTab = 1 }, label = { Text("字幕列表网格") })
                FilterChip(selected = consoleTab == 2, onClick = { consoleTab = 2 }, label = { Text("同屏双栏") })
            }
            when (consoleTab) {
                0 -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    audioDeck(Modifier.fillMaxWidth(), 116.dp)
                    HorizontalDivider()
                    editBox(Modifier.fillMaxWidth(), true)
                }
                1 -> subtitleGrid(Modifier.weight(1f).fillMaxWidth(), adaptive.widthDp >= 700)
                else -> Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.weight(0.52f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        audioDeck(Modifier.fillMaxWidth(), 104.dp)
                        editBox(Modifier.fillMaxWidth(), true)
                    }
                    subtitleGrid(Modifier.weight(0.48f).fillMaxHeight(), false)
                }
            }
        }
    }
}

/**
 * 4. 手机横屏 / 小平板紧凑分栏姿态 (`LANDSCAPE_SPLIT`)：
 * 左栏视频 + 波形打轴，右栏顶栏 + 编辑框 + 字幕列表，支持手指拖拽调节左右栏宽。
 */
@Composable
private fun LandscapeSplitLayout(
    topBar: @Composable () -> Unit,
    videoStage: @Composable (Modifier, Boolean) -> Unit,
    audioDeck: @Composable (Modifier, androidx.compose.ui.unit.Dp) -> Unit,
    editBox: @Composable (Modifier, Boolean) -> Unit,
    subtitleGrid: @Composable (Modifier, Boolean) -> Unit,
) {
    var leftFraction by remember { mutableFloatStateOf(0.46f) }
    val density = LocalDensity.current

    Column(Modifier.fillMaxSize()) {
        topBar()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val totalW = maxWidth
            val totalWPx = with(density) { totalW.toPx().coerceAtLeast(1f) }
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(totalW * leftFraction).fillMaxHeight()) {
                    videoStage(Modifier.weight(1f).fillMaxWidth(), true)
                    HorizontalDivider()
                    audioDeck(Modifier.fillMaxWidth(), 98.dp)
                }
                VerticalDragSplitter { dx ->
                    leftFraction = (leftFraction + dx / totalWPx).coerceIn(0.30f, 0.68f)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    editBox(Modifier.fillMaxWidth(), true)
                    HorizontalDivider()
                    subtitleGrid(Modifier.weight(1f).fillMaxWidth(), false)
                }
            }
        }
    }
}

/**
 * 5. 手机竖屏工作台姿态 (`PHONE_COMPACT`)：
 * 彻底告别旧版在两个页面间来回跳转的痛苦！单屏内通过四种顶部舞台微模式自由切换：
 * - `影音同屏`（视频预览 + 精简波形 + 编辑框 + 网格）
 * - `音频快打`（大尺寸可缩放音频波形/卡拉OK + 编辑框 + 网格）
 * - `画面打字`（大尺寸 8 模式可视化打字画布 + 编辑框 + 网格）
 * - `专注文本`（收起媒体舞台，全屏高速校对编辑与网格）
 */
@Composable
private fun PhoneCompactStudioLayout(
    topBar: @Composable () -> Unit,
    videoStage: @Composable (Modifier, Boolean) -> Unit,
    audioDeck: @Composable (Modifier, androidx.compose.ui.unit.Dp) -> Unit,
    editBox: @Composable (Modifier, Boolean) -> Unit,
    subtitleGrid: @Composable (Modifier, Boolean) -> Unit,
    onOpenStyles: () -> Unit,
    onOpenFixErrors: () -> Unit,
    onOpenExport: () -> Unit,
) {
    // 0 = 影音同屏, 1 = 音频打轴, 2 = 视频打字, 3 = 专注文本
    var phoneMode by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        topBar()

        // 手机顶部舞台模式快切条
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = phoneMode == 0, onClick = { phoneMode = 0 }, label = { Text("影音同屏") })
                FilterChip(selected = phoneMode == 1, onClick = { phoneMode = 1 }, label = { Text("音频打轴") })
                FilterChip(selected = phoneMode == 2, onClick = { phoneMode = 2 }, label = { Text("视频打字") })
                FilterChip(selected = phoneMode == 3, onClick = { phoneMode = 3 }, label = { Text("专注文本") })
            }
        }

        when (phoneMode) {
            0 -> {
                videoStage(Modifier.fillMaxWidth().height(195.dp), true)
                audioDeck(Modifier.fillMaxWidth(), 86.dp)
            }
            1 -> {
                audioDeck(Modifier.fillMaxWidth(), 156.dp)
            }
            2 -> {
                videoStage(Modifier.fillMaxWidth().height(250.dp), true)
            }
            else -> Unit
        }

        HorizontalDivider()
        editBox(Modifier.fillMaxWidth(), true)
        HorizontalDivider()
        subtitleGrid(Modifier.weight(1f).fillMaxWidth(), false)

        // 底部单手大拇指快捷栏
        NavigationBar(modifier = Modifier.height(56.dp), tonalElevation = 3.dp) {
            NavigationBarItem(
                selected = phoneMode == 1,
                onClick = { phoneMode = if (phoneMode == 1) 0 else 1 },
                icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("打轴", style = MaterialTheme.typography.labelSmall) },
            )
            NavigationBarItem(
                selected = phoneMode == 2,
                onClick = { phoneMode = if (phoneMode == 2) 0 else 2 },
                icon = { Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("打字", style = MaterialTheme.typography.labelSmall) },
            )
            NavigationBarItem(
                selected = false,
                onClick = onOpenStyles,
                icon = { Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("样式", style = MaterialTheme.typography.labelSmall) },
            )
            NavigationBarItem(
                selected = false,
                onClick = onOpenFixErrors,
                icon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("修复", style = MaterialTheme.typography.labelSmall) },
            )
            NavigationBarItem(
                selected = false,
                onClick = onOpenExport,
                icon = { Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("导出", style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun VerticalDragSplitter(onDragDeltaPx: (Float) -> Unit) {
    Box(
        Modifier
            .width(8.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDeltaPx(dragAmount.x)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
private fun HorizontalDragSplitter(onDragDeltaPx: (Float) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDeltaPx(dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            Modifier
                .width(36.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}
