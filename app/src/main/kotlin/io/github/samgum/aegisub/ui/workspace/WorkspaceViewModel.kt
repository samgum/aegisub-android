package io.github.samgum.aegisub.ui.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.ai.AiAnalysisService
import io.github.samgum.aegisub.data.ai.PlatformChineseTransliterator
import io.github.samgum.aegisub.data.repository.Bookmark
import io.github.samgum.aegisub.data.repository.BookmarkRepository
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.data.repository.Snapshot
import io.github.samgum.aegisub.data.repository.SnapshotRepository
import io.github.samgum.aegisub.data.session.ProjectSessionManager
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.data.settings.SettingsRepository
import io.github.samgum.aegisub.data.settings.TagDisplayMode
import io.github.samgum.aegisub.data.settings.UserSettings
import io.github.samgum.aegisub.domain.audio.SpectrogramData
import io.github.samgum.aegisub.domain.audio.Waveform
import io.github.samgum.aegisub.domain.edit.DeleteEmpty
import io.github.samgum.aegisub.domain.edit.FontsCollectorAnalyzer
import io.github.samgum.aegisub.domain.edit.FramerateConverter
import io.github.samgum.aegisub.domain.edit.KanjiTimer
import io.github.samgum.aegisub.domain.edit.KaraokeGenerator
import io.github.samgum.aegisub.domain.edit.KaraokeMode
import io.github.samgum.aegisub.domain.edit.KeyframesIo
import io.github.samgum.aegisub.domain.edit.LineOps
import io.github.samgum.aegisub.domain.edit.PasteOver
import io.github.samgum.aegisub.domain.edit.ResolutionResampler
import io.github.samgum.aegisub.domain.edit.ScriptInfoOps
import io.github.samgum.aegisub.domain.edit.SelectLines
import io.github.samgum.aegisub.domain.edit.SelectionOps
import io.github.samgum.aegisub.domain.edit.ShiftTarget
import io.github.samgum.aegisub.domain.edit.SortKey
import io.github.samgum.aegisub.domain.edit.SortLines
import io.github.samgum.aegisub.domain.edit.SortOrder
import io.github.samgum.aegisub.domain.edit.StyleReplace
import io.github.samgum.aegisub.domain.edit.TimePostProcess
import io.github.samgum.aegisub.domain.edit.TimeShift
import io.github.samgum.aegisub.domain.edit.VisualTags
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.SubtitleFormat
import io.github.samgum.aegisub.domain.format.WriteOptions
import io.github.samgum.aegisub.domain.model.AssColor
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.Margins
import io.github.samgum.aegisub.domain.preview.ActiveSubtitleResolver
import io.github.samgum.aegisub.domain.preview.SubtitleRenderInfo
import io.github.samgum.aegisub.domain.preview.TimingConstraints
import io.github.samgum.aegisub.domain.sgmy.ChineseConverter
import io.github.samgum.aegisub.domain.sgmy.FixCommonErrors
import io.github.samgum.aegisub.domain.sgmy.FuriganaAnnotator
import io.github.samgum.aegisub.domain.sgmy.LyricScrollGenerator
import io.github.samgum.aegisub.domain.sgmy.PairedPunctuationChecker
import io.github.samgum.aegisub.domain.sgmy.StitchTimings
import io.github.samgum.aegisub.domain.sgmy.StyleOverlapChecker
import io.github.samgum.aegisub.domain.sgmy.SubtitleOverflowDetector
import io.github.samgum.aegisub.domain.sgmy.SubtitleTextCleaner
import io.github.samgum.aegisub.domain.text.FindReplace
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.preview.PlaybackState
import io.github.samgum.aegisub.feature.preview.VideoPlayer
import io.github.samgum.aegisub.feature.preview.audio.WaveformExtractor
import io.github.samgum.aegisub.feature.preview.components.VisualToolMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 桌面 Aegisub 视图区域显示预设（对齐 `View -> Subs Only / Video+Subs / Audio+Subs / Full`）。
 */
enum class WorkspaceDisplayPreset {
    FULL,
    VIDEO_SUBS,
    AUDIO_SUBS,
    SUBS_ONLY,
}

sealed interface WorkspaceUiState {
    data object Loading : WorkspaceUiState
    data class Error(val message: String) : WorkspaceUiState
    data class Ready(
        val projectName: String,
        val script: AssScript,
        val hasMedia: Boolean,
        val playback: PlaybackState,
        val activeEventId: Long?,
        val currentVisibleEventId: Long?,
        val selectedIds: Set<Long>,
        val settings: UserSettings,
        val overflowMap: Map<Long, SubtitleOverflowDetector.OverflowResult>,
    ) : WorkspaceUiState
}

/**
 * 统一字幕工程工作台 ViewModel（彻底合并旧版割裂的 EditorViewModel + PreviewViewModel）：
 * 单一状态树同时驱动视频预览、8 模式可视化打字、可缩放音频打轴、语法高亮 ASS 编辑框、多列字幕网格，
 * 以及 `samgum/Aegisub` (SGMY) 全部独家工具与桌面端专业工具集。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    manager: ProjectSessionManager,
    private val projectRepo: ProjectRepository,
    private val settingsRepo: SettingsRepository,
    private val snapshotRepo: SnapshotRepository,
    private val bookmarkRepo: BookmarkRepository,
    private val player: VideoPlayer,
    private val extractor: WaveformExtractor,
    val aiService: AiAnalysisService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = savedStateHandle.get<String>("projectId")!!.toLong()
    private val session = manager.open(projectId)

    val videoPlayer: VideoPlayer get() = player

    val canUndo: StateFlow<Boolean> = session.canUndo
    val canRedo: StateFlow<Boolean> = session.canRedo

    val snapshots: StateFlow<List<Snapshot>> = snapshotRepo.observeSnapshots(projectId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkRepo.observeBookmarks(projectId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _hasMedia = MutableStateFlow(false)
    private val _activeEventId = MutableStateFlow<Long?>(null)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _waveform = MutableStateFlow<Waveform?>(null)
    val waveform: StateFlow<Waveform?> = _waveform.asStateFlow()

    private val _spectrogram = MutableStateFlow<SpectrogramData?>(null)
    val spectrogram: StateFlow<SpectrogramData?> = _spectrogram.asStateFlow()

    private val _keyframesMs = MutableStateFlow<List<Long>>(emptyList())
    val keyframesMs: StateFlow<List<Long>> = _keyframesMs.asStateFlow()

    private val _visualToolMode = MutableStateFlow(VisualToolMode.POSITION)
    val visualToolMode: StateFlow<VisualToolMode> = _visualToolMode.asStateFlow()

    private val _displayPreset = MutableStateFlow(WorkspaceDisplayPreset.FULL)
    val displayPreset: StateFlow<WorkspaceDisplayPreset> = _displayPreset.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val projectNameFlow = projectRepo.observeProject(projectId)
        .map { it?.name ?: "字幕工程 #$projectId" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "字幕工程")

    private val settingsFlow = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    /** 按脚本与设置预计算 SGMY 溢出检测表（仅脚本或溢出开关变化时重算，解耦 25ms 播放 tick！）。 */
    private val overflowMapFlow = combine(session.script, settingsFlow) { script, settings ->
        if (script == null || !settings.highlightOverflow) {
            emptyMap()
        } else {
            buildMap(script.events.size) {
                for (ev in script.events) {
                    val res = SubtitleOverflowDetector.evaluate(ev, script, settings.cpsWarningThreshold)
                    if (res.isOverflowing || res.isHighCps) put(ev.id, res)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 实时叠加字幕列表（支持 \move / \fad / \t / \kf 逐帧动态求值）。 */
    val activeSubtitles: StateFlow<List<SubtitleRenderInfo>> = combine(session.script, player.state) { script, pb ->
        if (script == null) emptyList()
        else ActiveSubtitleResolver.renderInfosInDrawOrder(script, pb.positionMs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private data class CoreScriptBundle(
        val name: String,
        val script: AssScript?,
        val error: String?,
        val hasMedia: Boolean,
        val settings: UserSettings,
    )

    private val coreBundle = combine(
        projectNameFlow,
        session.script,
        session.errorMessage,
        _hasMedia,
        settingsFlow,
    ) { name, script, error, hasMedia, settings ->
        CoreScriptBundle(name, script, error, hasMedia, settings)
    }

    val uiState: StateFlow<WorkspaceUiState> = combine(
        coreBundle,
        player.state,
        _activeEventId,
        _selectedIds,
        overflowMapFlow,
    ) { bundle, pb, activeId, selected, overflows ->
        when {
            bundle.error != null -> WorkspaceUiState.Error(bundle.error)
            bundle.script == null -> WorkspaceUiState.Loading
            else -> {
                val events = bundle.script.events
                val resolvedActive = activeId?.takeIf { id -> events.any { it.id == id } }
                    ?: events.firstOrNull()?.id
                WorkspaceUiState.Ready(
                    projectName = bundle.name,
                    script = bundle.script,
                    hasMedia = bundle.hasMedia,
                    playback = pb,
                    activeEventId = resolvedActive,
                    currentVisibleEventId = ActiveSubtitleResolver.activeEvent(bundle.script, pb.positionMs)?.id,
                    selectedIds = selected.intersect(events.map { it.id }.toSet()),
                    settings = bundle.settings,
                    overflowMap = overflows,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkspaceUiState.Loading)

    init {
        viewModelScope.launch {
            projectRepo.touchLastOpened(projectId, System.currentTimeMillis())
            val settings = settingsRepo.settings.first()
            player.setHdrPreviewOptimize(settings.hdrPreviewOptimize)
            val uri = projectRepo.getMediaUri(projectId)
            if (uri != null) {
                player.setMedia(uri)
                _hasMedia.value = true
                extractAudioAndKeyframes(uri)
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun postBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun setVisualToolMode(mode: VisualToolMode) {
        _visualToolMode.value = mode
    }

    fun setDisplayPreset(preset: WorkspaceDisplayPreset) {
        _displayPreset.value = preset
    }

    fun cycleTagDisplayMode() {
        viewModelScope.launch {
            val cur = settingsFlow.value.tagDisplayMode
            val next = when (cur) {
                TagDisplayMode.SHOW_TAGS -> TagDisplayMode.SIMPLIFY_TAGS
                TagDisplayMode.SIMPLIFY_TAGS -> TagDisplayMode.HIDE_TAGS
                TagDisplayMode.HIDE_TAGS -> TagDisplayMode.SHOW_TAGS
            }
            settingsRepo.setTagDisplayMode(next)
            val label = when (next) {
                TagDisplayMode.SHOW_TAGS -> "完整显示标签 {...}"
                TagDisplayMode.SIMPLIFY_TAGS -> "简化标签为 ☼"
                TagDisplayMode.HIDE_TAGS -> "隐藏全部覆盖标签"
            }
            postBanner("网格标签模式：$label")
        }
    }

    fun setLayoutMode(mode: LayoutMode) {
        viewModelScope.launch { settingsRepo.setLayoutMode(mode) }
    }

    // ---------------- 媒体与播放控制 ----------------

    fun attachMedia(uri: String) {
        viewModelScope.launch {
            projectRepo.setMediaUri(projectId, uri)
            player.setMedia(uri)
            _hasMedia.value = true
            extractAudioAndKeyframes(uri)
        }
    }

    fun closeMedia() {
        player.pause()
        _hasMedia.value = false
        _waveform.value = null
        _spectrogram.value = null
    }

    private fun extractAudioAndKeyframes(uri: String) {
        viewModelScope.launch {
            val kfs = extractor.extractKeyframesMs(uri)
            if (kfs.isNotEmpty()) _keyframesMs.value = kfs
        }
        viewModelScope.launch {
            val analysis = extractor.extractFull(uri, bucketCount = 4096, frameCount = 512, bins = 64)
            _waveform.value = analysis?.waveform
            _spectrogram.value = analysis?.spectrogram
        }
    }

    fun importKeyframesText(content: String) {
        val fps = player.state.value.fps.let { if (it > 0f) it.toDouble() else 23.976 }
        val parsed = KeyframesIo.parseKeyframesMs(content, fps)
        _keyframesMs.value = parsed
        postBanner("已载入 ${parsed.size} 个关键帧")
    }

    fun exportKeyframesText(): String {
        val fps = player.state.value.fps.let { if (it > 0f) it.toDouble() else 23.976 }
        return KeyframesIo.writeKeyframesV1(_keyframesMs.value, fps)
    }

    fun playPause() {
        if (player.state.value.isPlaying) player.pause() else player.play()
    }

    fun seekTo(ms: Long) = player.seekTo(ms)

    fun seekRelative(deltaMs: Long) {
        val cur = player.state.value.positionMs
        val maxMs = player.state.value.durationMs.let { if (it > 0L) it else 36_000_000L }
        player.seekTo((cur + deltaMs).coerceIn(0L, maxMs))
    }

    fun frameStepForward() = player.seekNextFrame()
    fun frameStepBack() = player.seekPreviousFrame()

    fun setSpeed(rate: Float) = player.setSpeed(rate.coerceIn(0.25f, 4.0f))

    fun adjustSpeed(delta: Float) {
        val next = ((player.state.value.speed + delta) * 100f).toInt() / 100f
        setSpeed(next.coerceIn(0.25f, 4.0f))
    }

    /** 仿桌面 Aegisub `Play current line [R]`。 */
    fun playActiveLine() {
        val ev = activeEvent() ?: return
        player.playRange(ev.start.millis, ev.end.millis)
    }

    /** 播放当前行起始前 500ms（检查切入点）。 */
    fun playBeforeActiveLine() {
        val ev = activeEvent() ?: return
        player.playRange((ev.start.millis - 500L).coerceAtLeast(0L), ev.start.millis)
    }

    /** 播放当前行结束后 500ms（检查切出点）。 */
    fun playAfterActiveLine() {
        val ev = activeEvent() ?: return
        player.playRange(ev.end.millis, ev.end.millis + 500L)
    }

    /** 播放当前行首 500ms。 */
    fun playFirst500ms() {
        val ev = activeEvent() ?: return
        player.playRange(ev.start.millis, minOf(ev.end.millis, ev.start.millis + 500L))
    }

    /** 播放当前行末 500ms。 */
    fun playLast500ms() {
        val ev = activeEvent() ?: return
        player.playRange(maxOf(ev.start.millis, ev.end.millis - 500L), ev.end.millis)
    }

    // ---------------- 选择与行导航 ----------------

    private fun activeEvent(): AssEvent? {
        val s = session.script.value ?: return null
        val id = _activeEventId.value
        return s.events.firstOrNull { it.id == id } ?: s.events.firstOrNull()
    }

    fun selectEvent(eventId: Long, seekMedia: Boolean = true) {
        _activeEventId.value = eventId
        if (seekMedia && _hasMedia.value) {
            session.script.value?.events?.firstOrNull { it.id == eventId }?.let { ev ->
                player.seekTo(ev.start.millis)
            }
        }
    }

    fun selectPrevEvent() {
        val events = session.script.value?.events ?: return
        if (events.isEmpty()) return
        val idx = events.indexOfFirst { it.id == _activeEventId.value }
        val target = if (idx > 0) events[idx - 1] else events.first()
        selectEvent(target.id)
    }

    fun selectNextEvent() {
        val events = session.script.value?.events ?: return
        if (events.isEmpty()) return
        val idx = events.indexOfFirst { it.id == _activeEventId.value }
        val target = if (idx in 0 until events.lastIndex) events[idx + 1] else events.last()
        selectEvent(target.id)
    }

    /** 仿桌面 Aegisub `Commit & Next [G / Enter]`：确认当前行并前进到下一行（若在末行则自动后插新行）。 */
    fun commitAndAdvance() {
        val events = session.script.value?.events ?: return
        if (events.isEmpty()) return
        val idx = events.indexOfFirst { it.id == _activeEventId.value }.let { if (it < 0) 0 else it }
        if (idx < events.lastIndex) {
            val next = events[idx + 1]
            _activeEventId.value = next.id
            if (_hasMedia.value && next.start.millis > 0L) player.seekTo(next.start.millis)
        } else {
            val prevEnd = events.last().end.millis
            session.editEvents { list ->
                LineOps.insertAfterAtVideoTime(list, list.lastIndex, prevEnd, 2_500L)
            }
            session.script.value?.events?.lastOrNull()?.let { _activeEventId.value = it.id }
        }
    }

    fun toggleSelection(eventId: Long) {
        val cur = _selectedIds.value
        _selectedIds.value = if (eventId in cur) cur - eventId else cur + eventId
        if (_activeEventId.value == null) _activeEventId.value = eventId
    }

    fun selectRangeTo(targetEventId: Long) {
        val events = session.script.value?.events ?: return
        val anchorId = _activeEventId.value ?: targetEventId
        val aIdx = events.indexOfFirst { it.id == anchorId }.takeIf { it >= 0 } ?: return
        val bIdx = events.indexOfFirst { it.id == targetEventId }.takeIf { it >= 0 } ?: return
        val range = minOf(aIdx, bIdx)..maxOf(aIdx, bIdx)
        _selectedIds.value = _selectedIds.value + range.map { events[it].id }
        _activeEventId.value = targetEventId
    }

    fun selectAll() {
        _selectedIds.value = session.script.value?.events?.map { it.id }?.toSet().orEmpty()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun applySelectLinesCriteria(criteria: SelectLines.Criteria): Int {
        val events = session.script.value?.events ?: return 0
        val res = SelectLines.evaluate(events, _selectedIds.value, criteria)
        _selectedIds.value = res
        res.firstOrNull()?.let { _activeEventId.value = it }
        postBanner("已匹配选中 ${res.size} 行")
        return res.size
    }

    // ---------------- 单行全字段编辑（Edit Box） ----------------

    fun updateActiveEvent(transform: (AssEvent) -> AssEvent) {
        val id = activeEvent()?.id ?: return
        session.editEvent(id, transform)
    }

    fun updateEventText(eventId: Long, text: String) {
        session.editEvent(eventId) { it.copy(text = text) }
    }

    fun updateEventTimes(eventId: Long, startMs: Long, endMs: Long) {
        val durLimit = player.state.value.durationMs
        val (s, e) = TimingConstraints.constrain(SubTime.ofMillis(startMs), SubTime.ofMillis(endMs), durLimit)
        session.editEvent(eventId) { it.copy(start = s, end = e) }
    }

    fun updateEventStyle(eventId: Long, style: String) = session.editEvent(eventId) { it.copy(style = style) }
    fun updateEventActor(eventId: Long, actor: String) = session.editEvent(eventId) { it.copy(actor = actor) }
    fun updateEventEffect(eventId: Long, effect: String) = session.editEvent(eventId) { it.copy(effect = effect) }
    fun updateEventLayer(eventId: Long, layer: Int) = session.editEvent(eventId) { it.copy(layer = layer.coerceAtLeast(0)) }
    fun updateEventComment(eventId: Long, comment: Boolean) = session.editEvent(eventId) { it.copy(comment = comment) }
    fun updateEventMargins(eventId: Long, margins: Margins) = session.editEvent(eventId) { it.copy(margins = margins) }

    // ---------------- 打轴快捷操作 ----------------

    fun setStartToVideo() {
        val ev = activeEvent() ?: return
        val rawPos = player.state.value.positionMs
        val pos = if (settingsFlow.value.snapToKeyframes) {
            KeyframesIo.snapToNearest(rawPos, _keyframesMs.value, 140L)
        } else rawPos
        val newEnd = maxOf(ev.end.millis, pos + 200L)
        updateEventTimes(ev.id, pos, newEnd)
    }

    fun setEndToVideo(advanceAfter: Boolean = settingsFlow.value.autoNextOnCommit) {
        val ev = activeEvent() ?: return
        val rawPos = player.state.value.positionMs
        val pos = if (settingsFlow.value.snapToKeyframes) {
            KeyframesIo.snapToNearest(rawPos, _keyframesMs.value, 140L)
        } else rawPos
        val newStart = minOf(ev.start.millis, pos)
        updateEventTimes(ev.id, newStart, pos.coerceAtLeast(newStart + 50L))
        if (advanceAfter) selectNextEvent()
    }

    fun addLeadIn() {
        val ev = activeEvent() ?: return
        val leadIn = settingsFlow.value.defaultLeadInMs.toLong()
        updateEventTimes(ev.id, (ev.start.millis - leadIn).coerceAtLeast(0L), ev.end.millis)
    }

    fun addLeadOut() {
        val ev = activeEvent() ?: return
        val leadOut = settingsFlow.value.defaultLeadOutMs.toLong()
        updateEventTimes(ev.id, ev.start.millis, ev.end.millis + leadOut)
    }

    fun nudgeStart(deltaMs: Long) {
        val ev = activeEvent() ?: return
        updateEventTimes(ev.id, (ev.start.millis + deltaMs).coerceAtLeast(0L), ev.end.millis)
    }

    fun nudgeEnd(deltaMs: Long) {
        val ev = activeEvent() ?: return
        updateEventTimes(ev.id, ev.start.millis, (ev.end.millis + deltaMs).coerceAtLeast(ev.start.millis + 20L))
    }

    // ---------------- 可视化打字（8 模式全接入） ----------------

    fun setEventPos(x: Int, y: Int) = updateActiveEvent { it.copy(text = VisualTags.setPos(it.text, x, y)) }
    fun clearEventPos() = updateActiveEvent { it.copy(text = VisualTags.removePos(it.text)) }
    fun setEventMove(x1: Int, y1: Int, x2: Int, y2: Int, t1: Int = 0, t2: Int = 0) =
        updateActiveEvent { it.copy(text = VisualTags.setMove(it.text, x1, y1, x2, y2, t1, t2)) }
    fun clearEventMove() = updateActiveEvent { it.copy(text = VisualTags.removeMove(it.text)) }
    fun setEventRotationZ(deg: Int) = updateActiveEvent { it.copy(text = VisualTags.setRotation(it.text, deg)) }
    fun setEventRotation3d(frx: Int, fry: Int) = updateActiveEvent { it.copy(text = VisualTags.setRotation3d(it.text, frx, fry)) }
    fun setEventOrigin(x: Int, y: Int) = updateActiveEvent { it.copy(text = VisualTags.setOrigin(it.text, x, y)) }
    fun setEventScale(sx: Int, sy: Int) = updateActiveEvent { it.copy(text = VisualTags.setScale(it.text, sx, sy)) }
    fun setEventFade(fadeIn: Int, fadeOut: Int) = updateActiveEvent { it.copy(text = VisualTags.setFade(it.text, fadeIn, fadeOut)) }
    fun setEventClipRect(x1: Int, y1: Int, x2: Int, y2: Int, inverse: Boolean) =
        updateActiveEvent { it.copy(text = VisualTags.setClip(it.text, x1, y1, x2, y2, inverse)) }
    fun setEventVectorClip(clip: VisualTags.VectorClip) =
        updateActiveEvent { it.copy(text = VisualTags.setVectorClip(it.text, clip)) }
    fun clearEventClips() = updateActiveEvent {
        it.copy(text = VisualTags.removeVectorClip(VisualTags.removeClip(it.text)))
    }
    fun setInlineColor(slot: Int, color: AssColor) =
        updateActiveEvent { it.copy(text = VisualTags.setColorOverride(it.text, slot, color)) }

    // ---------------- 行级与多选操作（桌面右键菜单全齐） ----------------

    private fun targetIndices(): List<Int> {
        val events = session.script.value?.events ?: return emptyList()
        val sel = _selectedIds.value
        if (sel.isNotEmpty()) {
            return events.indices.filter { events[it].id in sel }
        }
        val actId = activeEvent()?.id ?: return emptyList()
        val idx = events.indexOfFirst { it.id == actId }
        return if (idx >= 0) listOf(idx) else emptyList()
    }

    fun insertLineBefore(atVideoTime: Boolean = false) {
        val posMs = player.state.value.positionMs
        session.editEvents { list ->
            val idx = list.indexOfFirst { it.id == _activeEventId.value }.let { if (it < 0) 0 else it }
            if (atVideoTime) LineOps.insertBeforeAtVideoTime(list, idx, posMs) else LineOps.insertBefore(list, idx)
        }
    }

    fun insertLineAfter(atVideoTime: Boolean = false) {
        val posMs = player.state.value.positionMs
        session.editEvents { list ->
            val idx = list.indexOfFirst { it.id == _activeEventId.value }.let { if (it < 0) list.lastIndex else it }
            if (atVideoTime) LineOps.insertAfterAtVideoTime(list, idx, posMs) else LineOps.insertAfter(list, idx)
        }
    }

    fun duplicateTargetLines() {
        val sel = _selectedIds.value
        if (sel.size >= 2) {
            session.editEvents { SelectionOps.duplicateByIds(it, sel) }
        } else {
            val id = activeEvent()?.id ?: return
            session.editEvents { list ->
                val idx = list.indexOfFirst { it.id == id }
                if (idx >= 0) LineOps.duplicate(list, idx) else list
            }
        }
    }

    fun deleteTargetLines() {
        val sel = _selectedIds.value
        if (sel.isNotEmpty()) {
            session.editEvents { SelectionOps.deleteByIds(it, sel) }
            _selectedIds.value = emptySet()
        } else {
            val id = activeEvent()?.id ?: return
            session.editEvents { list ->
                val idx = list.indexOfFirst { it.id == id }
                if (idx >= 0) LineOps.delete(list, idx) else list
            }
        }
    }

    fun moveTargetLinesUp() {
        val sel = _selectedIds.value
        if (sel.isNotEmpty()) session.editEvents { SelectionOps.moveUpByIds(it, sel) }
        else {
            val id = activeEvent()?.id ?: return
            session.editEvents { list ->
                val idx = list.indexOfFirst { it.id == id }
                if (idx >= 0) LineOps.moveUp(list, idx) else list
            }
        }
    }

    fun moveTargetLinesDown() {
        val sel = _selectedIds.value
        if (sel.isNotEmpty()) session.editEvents { SelectionOps.moveDownByIds(it, sel) }
        else {
            val id = activeEvent()?.id ?: return
            session.editEvents { list ->
                val idx = list.indexOfFirst { it.id == id }
                if (idx >= 0) LineOps.moveDown(list, idx) else list
            }
        }
    }

    fun splitActiveLine(atVideoTime: Boolean = false) {
        val id = activeEvent()?.id ?: return
        val vMs = player.state.value.positionMs
        session.editEvents { list ->
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) list
            else if (atVideoTime) LineOps.splitAtVideoTime(list, idx, vMs)
            else LineOps.splitAtMidpoint(list, idx)
        }
    }

    fun splitActiveByKaraoke() {
        val id = activeEvent()?.id ?: return
        session.editEvents { list ->
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) LineOps.splitByKaraoke(list, idx) else list
        }
    }

    fun joinTargetLines(mode: JoinMode) {
        session.editEvents { list ->
            val idxs = targetIndices().let {
                if (it.size >= 2) it
                else {
                    val first = it.firstOrNull() ?: return@editEvents list
                    if (first + 1 < list.size) listOf(first, first + 1) else return@editEvents list
                }
            }
            when (mode) {
                JoinMode.CONCAT -> LineOps.joinConcatenate(list, idxs)
                JoinMode.KEEP_FIRST -> LineOps.joinKeepFirst(list, idxs)
                JoinMode.AS_KARAOKE -> LineOps.joinAsKaraoke(list, idxs)
                JoinMode.RECOMBINE -> LineOps.recombineLines(list, idxs)
            }
        }
        _selectedIds.value = emptySet()
    }

    enum class JoinMode { CONCAT, KEEP_FIRST, AS_KARAOKE, RECOMBINE }

    fun swapSelectedTwoLines() {
        val idxs = targetIndices()
        if (idxs.size == 2) {
            session.editEvents { LineOps.swapLines(it, idxs[0], idxs[1]) }
        }
    }

    fun makeTimesContinuous(changeEnd: Boolean) {
        val scopeIds = _selectedIds.value
        session.editEvents { LineOps.makeTimesContinuous(it, scopeIds, changeEnd) }
        postBanner("已使相邻字幕时间连续")
    }

    fun copySelectedLinesAsAss(): String {
        val events = session.script.value?.events ?: return ""
        val idxs = targetIndices()
        return idxs.mapNotNull { events.getOrNull(it)?.toLine() }.joinToString("\n")
    }

    fun pasteAssOrTextLines(clipboardText: String) {
        if (clipboardText.isBlank()) return
        session.editEvents { list ->
            var nextId = (list.maxOfOrNull { it.id } ?: -1L) + 1L
            val parsed = clipboardText.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { raw ->
                    runCatching { AssEvent.parse(raw).copy(id = nextId++) }
                        .getOrElse { AssEvent(id = nextId++, text = raw) }
                }.toList()
            if (parsed.isEmpty()) return@editEvents list
            val insertAfterIdx = list.indexOfFirst { it.id == _activeEventId.value }.let { if (it < 0) list.lastIndex else it }
            list.toMutableList().apply { addAll((insertAfterIdx + 1).coerceIn(0, size), parsed) }
        }
    }

    fun sortLines(key: SortKey, order: SortOrder) {
        session.editEvents { SortLines.apply(it, key, order) }
    }

    // ---------------- SGMY 特色工具全接入 ----------------

    fun applyFixCommonErrors(config: FixCommonErrors.Config): FixCommonErrors.Result {
        val current = session.script.value?.events.orEmpty()
        val scopedConfig = if (config.selectedIds.isEmpty() && _selectedIds.value.isNotEmpty()) {
            config
        } else config
        val result = FixCommonErrors.apply(current, scopedConfig)
        // 严格复刻桌面 samgum/Aegisub：时间、文本、删空行分别作为独立撤销步提交
        if (result.timingChanged) session.editEvents { result.afterTimingEvents }
        if (result.textChanged) session.editEvents { result.afterTextEvents }
        if (result.rowsRemoved) {
            session.editEvents { result.finalEvents }
            val surviving = result.finalEvents.map { it.id }.toSet()
            _selectedIds.value = _selectedIds.value.intersect(surviving)
            if (_activeEventId.value !in surviving) {
                _activeEventId.value = result.finalEvents.firstOrNull()?.id
            }
        }
        postBanner(if (result.totalFixed > 0) "SGMY 修复完成：共处理 ${result.totalFixed} 处问题" else "未发现需要修复的常见错误")
        return result
    }

    fun applyStitchTimings(config: StitchTimings.Config): Int {
        val current = session.script.value?.events.orEmpty()
        val fps = player.state.value.fps.toDouble()
        val res = StitchTimings.apply(current, config.copy(fps = fps))
        if (res.changedCount > 0) session.editEvents { res.events }
        postBanner(if (res.changedCount > 0) "时间缝合完成：共缝合 ${res.changedCount} 处间隙" else "没有符合条件的字幕间隙")
        return res.changedCount
    }

    fun analyzeStyleOverlaps(): StyleOverlapChecker.Report =
        StyleOverlapChecker.analyze(session.script.value?.events.orEmpty())

    fun applyTextCleanup(config: SubtitleTextCleaner.Config): SubtitleTextCleaner.Stats {
        val current = session.script.value?.events.orEmpty()
        val res = SubtitleTextCleaner.apply(current, config)
        if (res.stats.changedLines > 0) session.editEvents { res.events }
        postBanner("文本清理：检查 ${res.stats.checkedLines} 行，修改 ${res.stats.changedLines} 行")
        return res.stats
    }

    fun applyChineseConversion(config: ChineseConverter.Config): ChineseConverter.Result {
        val current = session.script.value?.events.orEmpty()
        val res = ChineseConverter.apply(current, config, PlatformChineseTransliterator::convert)
        if (res.changedLines > 0) session.editEvents { res.events }
        postBanner("简繁转换：检查 ${res.checkedLines} 行，转换 ${res.changedLines} 行")
        return res
    }

    fun checkPairedPunctuation(selectedOnly: Boolean, styleFilter: String?): PairedPunctuationChecker.Report {
        val ids = if (selectedOnly) _selectedIds.value else emptySet()
        return PairedPunctuationChecker.check(session.script.value?.events.orEmpty(), ids, styleFilter)
    }

    fun applyFurigana(readingsText: String, config: FuriganaAnnotator.Config): FuriganaAnnotator.Result {
        val s = session.script.value ?: return FuriganaAnnotator.Result(emptyList(), 0, 0, 0, 0)
        val res = FuriganaAnnotator.apply(s, readingsText, config)
        if (res.changedLines > 0) session.editEvents { res.events }
        postBanner("假名注音：已标注 ${res.annotatedLines} 行")
        return res
    }

    fun generateLyricScroll(settings: LyricScrollGenerator.Settings): LyricScrollGenerator.Result {
        val s = session.script.value ?: return LyricScrollGenerator.Result(AssScript.default(), 0, 0)
        val res = LyricScrollGenerator.generate(s, settings, _selectedIds.value)
        if (res.generatedCount > 0) {
            session.editScript { res.script }
            postBanner("滚动歌词生成完成：由 ${res.sourceRowCount} 句歌词生成 ${res.generatedCount} 条动态字幕")
        } else {
            postBanner("没有可用于生成滚动歌词的有效行")
        }
        return res
    }

    // ---------------- 桌面经典批量与工程工具 ----------------

    fun replaceAll(query: String, replacement: String, useRegex: Boolean, ignoreCase: Boolean, selectedOnly: Boolean = false) {
        val sel = _selectedIds.value
        session.editAllEvents { e ->
            if (selectedOnly && sel.isNotEmpty() && e.id !in sel) e
            else e.copy(text = FindReplace.replaceAll(e.text, query, replacement, useRegex, ignoreCase))
        }
        postBanner("已完成查找替换")
    }

    fun shiftTimes(deltaMs: Long, target: ShiftTarget, onlyAfterActive: Boolean, selectedOnly: Boolean) {
        val fromStart = if (onlyAfterActive) activeEvent()?.start else null
        val sel = if (selectedOnly) _selectedIds.value else emptySet()
        session.editEvents { events ->
            if (sel.isEmpty()) TimeShift.apply(events, deltaMs, target, fromStart)
            else events.map { e ->
                if (e.id !in sel) e
                else if (fromStart != null && e.start < fromStart) e
                else TimeShift.apply(listOf(e), deltaMs, target, fromStart).first()
            }
        }
    }

    fun applyTimingPostProcess(leadInMs: Long, leadOutMs: Long, minGapMs: Long) {
        session.editEvents { list ->
            TimePostProcess.apply(list, leadInMs, leadOutMs, minGapMs)
        }
        postBanner("时间后处理已应用")
    }

    fun resampleResolution(toW: Int, toH: Int, scalePos: Boolean, scaleBorders: Boolean) {
        val s = session.script.value ?: return
        val fromW = s.getScriptInfo("PlayResX")?.toIntOrNull()?.takeIf { it > 0 } ?: 384
        val fromH = s.getScriptInfo("PlayResY")?.toIntOrNull()?.takeIf { it > 0 } ?: 288
        session.editScript {
            ResolutionResampler.rescale(
                it,
                fromW,
                fromH,
                toW,
                toH,
                ResolutionResampler.Options(scalePositions = scalePos, scaleBorders = scaleBorders),
            )
        }
        postBanner("分辨率重采样：$fromW×$fromH → $toW×$toH")
    }

    fun convertFramerate(fromFps: Double, toFps: Double) {
        session.editEvents { FramerateConverter.rescale(it, fromFps, toFps) }
        postBanner("帧率时间轴转换完成")
    }

    fun applyScriptInfo(changes: Map<String, String>) {
        if (changes.isEmpty()) return
        session.editInfo { info ->
            var res = info
            for ((k, v) in changes) res = ScriptInfoOps.set(res, k, v)
            res
        }
    }

    fun makeKaraokeOnActive(mode: KaraokeMode, useKf: Boolean) {
        val ev = activeEvent() ?: return
        session.editEvent(ev.id) { e -> e.copy(text = KaraokeGenerator.generateFromEvent(e, mode, useKf)) }
    }

    fun applyKanjiTimer(sourceStyle: String, targetStyle: String): Int {
        val current = session.script.value?.events.orEmpty()
        val (updated, count) = KanjiTimer.applyByStyles(current, sourceStyle, targetStyle)
        if (count > 0) session.editEvents { updated }
        postBanner("汉字计时器：已复制应用 $count 行卡拉OK时间")
        return count
    }

    fun analyzeFonts(): List<FontsCollectorAnalyzer.FontUsage> =
        session.script.value?.let { FontsCollectorAnalyzer.analyze(it) }.orEmpty()

    fun deleteEmptyLines() = session.editEvents { DeleteEmpty.apply(it) }

    fun replaceStyles(from: String, to: String, selectedOnly: Boolean) {
        val sel = if (selectedOnly) _selectedIds.value else emptySet()
        session.editEvents { list ->
            if (sel.isEmpty()) StyleReplace.apply(list, from, to)
            else list.map { if (it.id in sel && it.style == from) it.copy(style = to) else it }
        }
    }

    fun pasteOver(text: String) {
        val events = session.script.value?.events ?: return
        val ids = if (_selectedIds.value.isNotEmpty()) {
            events.filter { it.id in _selectedIds.value }.map { it.id }
        } else {
            events.map { it.id }
        }
        val lines = text.replace("\r\n", "\n").split("\n")
        session.editEvents { PasteOver.apply(it, ids, lines) }
    }

    fun setTranslation(eventId: Long, original: String, translation: String) {
        session.editEvent(eventId) { it.copy(actor = original, text = translation) }
    }

    // ---------------- 快照、书签、导出与撤销 ----------------

    fun takeSnapshot(label: String) {
        val s = session.script.value ?: return
        viewModelScope.launch {
            val content = AssFormat.write(s, WriteOptions(timePrecision = settingsFlow.value.exportPrecision))
            snapshotRepo.saveSnapshot(projectId, content, label, System.currentTimeMillis())
            postBanner("已保存历史快照：$label")
        }
    }

    fun restoreSnapshot(snapshotId: Long) {
        viewModelScope.launch {
            val content = snapshotRepo.getSnapshotContent(snapshotId) ?: return@launch
            session.restoreFromContent(content)
            postBanner("已恢复到选定历史快照（可撤销）")
        }
    }

    fun deleteSnapshot(snapshotId: Long) {
        viewModelScope.launch { snapshotRepo.deleteSnapshot(snapshotId) }
    }

    fun addBookmark(label: String) {
        val pos = player.state.value.positionMs
        viewModelScope.launch { bookmarkRepo.addBookmark(projectId, pos, label, System.currentTimeMillis()) }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarkRepo.deleteBookmark(id) }
    }

    suspend fun exportAs(format: SubtitleFormat): String {
        val s = session.script.value ?: return ""
        val precision = settingsRepo.settings.first().exportPrecision
        return format.write(s, WriteOptions(timePrecision = precision))
    }

    fun saveAiSettings(
        enabled: Boolean,
        providerIndex: Int,
        baseUrl: String,
        model: String,
        apiKey: String,
        targetLang: String,
        temperature: Float,
        maxTokens: Int,
        thinking: Boolean,
        cacheSession: Boolean,
    ) {
        aiService.sessionApiKey = apiKey.trim()
        viewModelScope.launch {
            settingsRepo.updateAiConfig(
                enabled = enabled,
                providerIndex = providerIndex,
                baseUrl = baseUrl,
                model = model,
                targetLang = targetLang,
                temperature = temperature,
                maxTokens = maxTokens,
                thinking = thinking,
                cacheSession = cacheSession,
            )
        }
    }

    fun undo() = session.undo()
    fun redo() = session.redo()

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
