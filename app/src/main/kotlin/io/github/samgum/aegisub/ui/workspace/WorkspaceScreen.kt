package io.github.samgum.aegisub.ui.workspace

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Subtitles
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
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
 * 统一自适应字幕工程工作台：全面支持手机单手触控、平板/DeX 键鼠协同与折叠屏悬停/双页姿态。
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
    var isTextInputFocused by remember { mutableStateOf(false) }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.attachMedia(uri.toString())
        }
    }

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

    val exportKeyframesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val content = viewModel.exportKeyframesText()
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
            }
            viewModel.postBanner("关键帧文件已导出")
        }
    }

    val exportSubtitleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val fmt = pendingExportFormat
        pendingExportFormat = null
        if (uri != null && fmt != null) {
            scope.launch {
                val out = viewModel.exportAs(fmt)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(out) }
                }
                viewModel.postBanner("已导出为 ${fmt.name.uppercase()} 格式")
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
                    viewModel.postBanner("已复制字幕行")
                }
            }

            fun pasteLinesFromClipboard() {
                val raw = clipboard.getText()?.text.orEmpty()
                if (raw.isNotBlank()) {
                    viewModel.pasteAssOrTextLines(raw)
                }
            }

            fun dispatchHotkey(action: HotkeyAction) {
                when (action) {
                    HotkeyAction.UNDO -> viewModel.undo()
                    HotkeyAction.REDO -> viewModel.redo()
                    HotkeyAction.SAVE -> viewModel.takeSnapshot("手动保存")
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
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .onPreviewKeyEvent { keyEv ->
                        if (keyEv.type != KeyEventType.KeyDown || activeDialog != null) return@onPreviewKeyEvent false

                        // 当用户正在编辑框打字时，仅响应带 Ctrl / Alt 修饰键的全局热键，绝不吞噬普通字母、空格与方向键！
                        if (isTextInputFocused) {
                            if (!keyEv.isCtrlPressed && !keyEv.isAltPressed) return@onPreviewKeyEvent false
                            val action = hotkeys.match(keyEv) ?: return@onPreviewKeyEvent false
                            dispatchHotkey(action)
                            return@onPreviewKeyEvent true
                        }

                        // 焦点在网格/视频/波形区时：支持全部单键打轴快捷键 + 标准桌面方向键/Delete/Esc/Ctrl+C/V/A
                        val matched = hotkeys.match(keyEv)
                        if (matched != null) {
                            dispatchHotkey(matched)
                            return@onPreviewKeyEvent true
                        }

                        when {
                            keyEv.key == Key.DirectionUp && !keyEv.isAltPressed -> {
                                viewModel.selectPrevEvent(); true
                            }
                            keyEv.key == Key.DirectionDown && !keyEv.isAltPressed -> {
                                viewModel.selectNextEvent(); true
                            }
                            keyEv.key == Key.Delete || keyEv.key == Key.Backspace -> {
                                viewModel.deleteTargetLines(); true
                            }
                            keyEv.key == Key.Escape && state.selectedIds.isNotEmpty() -> {
                                viewModel.clearSelection(); true
                            }
                            keyEv.isCtrlPressed && keyEv.key == Key.A -> {
                                viewModel.selectAll(); true
                            }
                            keyEv.isCtrlPressed && keyEv.key == Key.C -> {
                                copyLinesToClipboard(); true
                            }
                            keyEv.isCtrlPressed && keyEv.key == Key.V -> {
                                pasteLinesFromClipboard(); true
                            }
                            else -> false
                        }
                    },
            ) { innerPadding ->
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
                        onQuickSnapshot = { viewModel.takeSnapshot("手动保存") },
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

                val renderVideoStage: @Composable (Modifier, Boolean, Boolean) -> Unit = { mod, compact, showStrip ->
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
                        showTypesettingStrip = showStrip,
                        compactControls = compact,
                        modifier = mod,
                    )
                }

                val renderAudioDeck: @Composable (Modifier, Dp) -> Unit = { mod, h ->
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
                        onInputFocusChanged = { isTextInputFocused = it },
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
                            hasMedia = state.hasMedia,
                            topBar = renderCompactTopBar,
                            videoStage = renderVideoStage,
                            audioDeck = renderAudioDeck,
                            editBox = renderEditBox,
                            subtitleGrid = renderGrid,
                            onPickMedia = { pickMediaLauncher.launch(arrayOf("video/*", "audio/*")) },
                            onSetVisualOverlay = { visualOverlayEnabled = it },
                            onOpenStyles = { onOpenStyles(viewModel.projectId) },
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
                        val ext = (fmt.extensions.firstOrNull() ?: ".ass").trimStart('.')
                        exportSubtitleLauncher.launch("${state.projectName}.$ext")
                    },
                )
            }
        }
    }
}

// ============================================================================
// 5 种设备形态专属布局
// ============================================================================

@Composable
private fun DesktopThreeZoneLayout(
    displayPreset: WorkspaceDisplayPreset,
    topBar: @Composable () -> Unit,
    videoStage: @Composable (Modifier, Boolean, Boolean) -> Unit,
    audioDeck: @Composable (Modifier, Dp) -> Unit,
    editBox: @Composable (Modifier, Boolean) -> Unit,
    subtitleGrid: @Composable (Modifier, Boolean) -> Unit,
) {
    var topHeightFraction by remember { mutableFloatStateOf(0.52f) }
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
                                videoStage(Modifier.width(leftW).fillMaxHeight(), false, true)
                                VerticalDragSplitter { dx ->
                                    leftWidthFraction = (leftWidthFraction + dx / totalWPx).coerceIn(0.25f, 0.72f)
                                }
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    audioDeck(Modifier.fillMaxWidth(), 130.dp)
                                    HorizontalDivider()
                                    editBox(Modifier.fillMaxWidth(), false)
                                }
                            }

                            WorkspaceDisplayPreset.VIDEO_SUBS -> {
                                val leftW = totalW * leftWidthFraction
                                videoStage(Modifier.width(leftW).fillMaxHeight(), false, true)
                                VerticalDragSplitter { dx ->
                                    leftWidthFraction = (leftWidthFraction + dx / totalWPx).coerceIn(0.28f, 0.75f)
                                }
                                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                    editBox(Modifier.fillMaxWidth(), false)
                                }
                            }

                            WorkspaceDisplayPreset.AUDIO_SUBS -> {
                                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    audioDeck(Modifier.fillMaxWidth(), 148.dp)
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

@Composable
private fun FoldableBookDualPaneLayout(
    adaptive: AdaptiveWindowState,
    topBar: @Composable () -> Unit,
    videoStage: @Composable (Modifier, Boolean, Boolean) -> Unit,
    audioDeck: @Composable (Modifier, Dp) -> Unit,
    editBox: @Composable (Modifier, Boolean) -> Unit,
    subtitleGrid: @Composable (Modifier, Boolean) -> Unit,
) {
    val leftW = adaptive.bookLeftWidthDp ?: (adaptive.widthDp / 2).dp
    val rightWide = (adaptive.widthDp.dp - leftW - adaptive.hingeThicknessDp) >= 520.dp

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(leftW).fillMaxHeight()) {
            videoStage(Modifier.weight(1f).fillMaxWidth(), true, true)
            HorizontalDivider()
            audioDeck(Modifier.fillMaxWidth(), 140.dp)
        }

        Box(
            Modifier
                .width(adaptive.hingeThicknessDp.coerceAtLeast(8.dp))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )

        Column(Modifier.weight(1f).fillMaxHeight()) {
            topBar()
            editBox(Modifier.fillMaxWidth(), true)
            HorizontalDivider()
            subtitleGrid(Modifier.weight(1f).fillMaxWidth(), rightWide)
        }
    }
}

@Composable
private fun FoldableTabletopLayout(
    adaptive: AdaptiveWindowState,
    topBar: @Composable () -> Unit,
    videoStage: @Composable (Modifier, Boolean, Boolean) -> Unit,
    audioDeck: @Composable (Modifier, Dp) -> Unit,
    editBox: @Composable (Modifier, Boolean) -> Unit,
    subtitleGrid: @Composable (Modifier, Boolean) -> Unit,
) {
    val topH = adaptive.tabletopTopHeightDp ?: (adaptive.heightDp * 0.45f).dp
    var consoleTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        videoStage(Modifier.fillMaxWidth().height(topH), true, true)

        Box(
            Modifier
                .fillMaxWidth()
                .height(adaptive.hingeThicknessDp.coerceAtLeast(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )

        Column(Modifier.weight(1f).fillMaxWidth()) {
            topBar()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(selected = consoleTab == 0, onClick = { consoleTab = 0 }, label = { Text("打轴与编辑") })
                FilterChip(selected = consoleTab == 1, onClick = { consoleTab = 1 }, label = { Text("字幕列表") })
                FilterChip(selected = consoleTab == 2, onClick = { consoleTab = 2 }, label = { Text("双栏同屏") })
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

@Composable
private fun LandscapeSplitLayout(
    topBar: @Composable () -> Unit,
    videoStage: @Composable (Modifier, Boolean, Boolean) -> Unit,
    audioDeck: @Composable (Modifier, Dp) -> Unit,
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
                    videoStage(Modifier.weight(1f).fillMaxWidth(), true, true)
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
 * 手机竖屏清爽布局：消除重复切换栏与空黑框，按任务专注度自由切换「字幕 / 打轴 / 排版」。
 */
@Composable
private fun PhoneCompactStudioLayout(
    hasMedia: Boolean,
    topBar: @Composable () -> Unit,
    videoStage: @Composable (Modifier, Boolean, Boolean) -> Unit,
    audioDeck: @Composable (Modifier, Dp) -> Unit,
    editBox: @Composable (Modifier, Boolean) -> Unit,
    subtitleGrid: @Composable (Modifier, Boolean) -> Unit,
    onPickMedia: () -> Unit,
    onSetVisualOverlay: (Boolean) -> Unit,
    onOpenStyles: () -> Unit,
    onOpenExport: () -> Unit,
) {
    // 0 = 字幕浏览与编辑, 1 = 音频波形打轴, 2 = 画面视觉排版
    var phoneTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        topBar()

        when (phoneTab) {
            0 -> {
                if (hasMedia) {
                    videoStage(Modifier.fillMaxWidth().height(196.dp), true, false)
                } else {
                    // 未加载视频时不堆砌大黑框，仅保留一条清爽的关联提示条，将全屏空间让给字幕列表！
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onPickMedia),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "点击关联视频或音频文件，开启画面预览与波形打轴",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            1 -> {
                audioDeck(Modifier.fillMaxWidth(), 160.dp)
            }
            else -> {
                videoStage(Modifier.fillMaxWidth().height(236.dp), true, true)
            }
        }

        HorizontalDivider()
        editBox(Modifier.fillMaxWidth(), true)
        HorizontalDivider()
        subtitleGrid(Modifier.weight(1f).fillMaxWidth(), false)

        NavigationBar(modifier = Modifier.height(58.dp), tonalElevation = 2.dp) {
            NavigationBarItem(
                selected = phoneTab == 0,
                onClick = { phoneTab = 0; onSetVisualOverlay(false) },
                icon = { Icon(Icons.Filled.Subtitles, contentDescription = null, modifier = Modifier.size(19.dp)) },
                label = { Text("字幕", style = MaterialTheme.typography.labelSmall) },
            )
            NavigationBarItem(
                selected = phoneTab == 1,
                onClick = { phoneTab = 1; onSetVisualOverlay(false) },
                icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(19.dp)) },
                label = { Text("打轴", style = MaterialTheme.typography.labelSmall) },
            )
            NavigationBarItem(
                selected = phoneTab == 2,
                onClick = { phoneTab = 2; onSetVisualOverlay(true) },
                icon = { Icon(Icons.Filled.CropFree, contentDescription = null, modifier = Modifier.size(19.dp)) },
                label = { Text("排版", style = MaterialTheme.typography.labelSmall) },
            )
            NavigationBarItem(
                selected = false,
                onClick = onOpenStyles,
                icon = { Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(19.dp)) },
                label = { Text("样式", style = MaterialTheme.typography.labelSmall) },
            )
            NavigationBarItem(
                selected = false,
                onClick = onOpenExport,
                icon = { Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(19.dp)) },
                label = { Text("导出", style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun VerticalDragSplitter(onDragDeltaPx: (Float) -> Unit) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    Box(
        Modifier
            .width(10.dp)
            .fillMaxHeight()
            .hoverable(hoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .background(
                if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
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
                .background(if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
private fun HorizontalDragSplitter(onDragDeltaPx: (Float) -> Unit) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .hoverable(hoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .background(
                if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
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
                .background(if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        )
    }
}
