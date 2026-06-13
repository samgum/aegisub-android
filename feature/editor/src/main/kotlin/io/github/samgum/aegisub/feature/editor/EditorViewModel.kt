package io.github.samgum.aegisub.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.repository.Snapshot
import io.github.samgum.aegisub.data.repository.SnapshotRepository
import io.github.samgum.aegisub.data.session.ProjectSessionManager
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.data.settings.SettingsRepository
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.WriteOptions
import io.github.samgum.aegisub.domain.edit.DeleteEmpty
import io.github.samgum.aegisub.domain.edit.FramerateConverter
import io.github.samgum.aegisub.domain.edit.KaraokeGenerator
import io.github.samgum.aegisub.domain.edit.KaraokeMode
import io.github.samgum.aegisub.domain.edit.LineOps
import io.github.samgum.aegisub.domain.edit.ResolutionResampler
import io.github.samgum.aegisub.domain.edit.ShiftTarget
import io.github.samgum.aegisub.domain.edit.SortKey
import io.github.samgum.aegisub.domain.edit.SortLines
import io.github.samgum.aegisub.domain.edit.SortOrder
import io.github.samgum.aegisub.domain.edit.ScriptInfoOps
import io.github.samgum.aegisub.domain.edit.SelectionOps
import io.github.samgum.aegisub.domain.edit.StyleReplace
import io.github.samgum.aegisub.domain.edit.TimePostProcess
import io.github.samgum.aegisub.domain.edit.TimeShift
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.feature.editor.components.LineAction
import io.github.samgum.aegisub.domain.text.FindReplace
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 字幕编辑器 ViewModel：委托 [io.github.samgum.aegisub.data.session.ProjectSession]。
 * 编辑/撤销/自动保存语义全部下沉到共享会话，本类只做状态映射与参数适配。
 * 与预览屏共享同一 session → 两端 script/撤销栈始终一致。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    manager: ProjectSessionManager,
    private val settings: SettingsRepository,
    private val snapshots: SnapshotRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = savedStateHandle.get<String>("projectId")!!.toLong()

    private val session = manager.open(projectId)

    /** 历史快照列表（按时间倒序），用于「历史版本恢复」。 */
    val snapshotList: StateFlow<List<Snapshot>> = snapshots.observeSnapshots(projectId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<EditorUiState> =
        combine(session.script, session.errorMessage) { script, error ->
            when {
                error != null -> EditorUiState.Error(error)
                script != null -> EditorUiState.Loaded(script)
                else -> EditorUiState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, EditorUiState.Loading)

    val canUndo: StateFlow<Boolean> = session.canUndo
    val canRedo: StateFlow<Boolean> = session.canRedo

    /** 编辑器布局偏好（AUTO/COMPACT/EXPANDED），来自用户设置。 */
    val layoutMode: StateFlow<LayoutMode> = settings.settings
        .map { it.layoutMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LayoutMode.AUTO)

    /** 当前已加载脚本（未加载返回 null）。 */
    fun currentScript(): AssScript? = session.script.value

    fun updateEventText(eventId: Long, text: String) =
        session.editEvent(eventId) { it.copy(text = text) }

    fun updateEventTimes(eventId: Long, start: SubTime, end: SubTime) =
        session.editEvent(eventId) { it.copy(start = start, end = end) }

    fun updateEventStyle(eventId: Long, style: String) =
        session.editEvent(eventId) { it.copy(style = style) }

    fun setEventLayer(eventId: Long, layer: Int) =
        session.editEvent(eventId) { it.copy(layer = layer) }

    /**
     * 翻译助手写入：把原文存入 Name(actor) 字段，译文写入 Text（一次撤销点）。
     * 原文为空时仅写译文。对齐桌面 Aegisub Translation Assistant 语义。
     */
    fun setTranslation(eventId: Long, original: String, translation: String) =
        session.editEvent(eventId) { it.copy(actor = original, text = translation) }

    /**
     * Karaoke 生成：把指定行文本切成音节并均匀分配时长，生成 {\k}/{\kf} 标签（单撤销点）。
     */
    fun makeKaraoke(eventId: Long, mode: KaraokeMode, useKf: Boolean) =
        session.editEvent(eventId) { e ->
            e.copy(text = KaraokeGenerator.generateFromEvent(e, mode, useKf))
        }

    /** 全局查找替换所有事件的文本（一次撤销点）。 */
    fun replaceAll(query: String, replacement: String, useRegex: Boolean, ignoreCase: Boolean) {
        session.editAllEvents { e ->
            e.copy(text = FindReplace.replaceAll(e.text, query, replacement, useRegex, ignoreCase))
        }
    }

    /**
     * 批量时间偏移（一次撤销点）。
     * @param deltaMs 偏移量，正=后移，负=前移（越界自动钳零）
     * @param target 平移起始/结束/两者
     * @param fromStartMs 非空时仅平移 start ≥ 此值的事件；null=全部
     * @param selectedIds 非空时仅作用于命中行（多选批量）
     */
    fun shiftTimes(
        deltaMs: Long,
        target: ShiftTarget,
        fromStartMs: Long? = null,
        selectedIds: Set<Long> = emptySet(),
    ) {
        val fromStart = fromStartMs?.let { SubTime.ofMillis(it) }
        session.editEvents { events ->
            if (selectedIds.isEmpty()) {
                TimeShift.apply(events, deltaMs, target, fromStart)
            } else {
                events.map { e ->
                    if (e.id !in selectedIds) e
                    else if (fromStart != null && e.start < fromStart) e
                    else TimeShift.apply(listOf(e), deltaMs, target, fromStart).first()
                }
            }
        }
    }

    /** 删除有效内容为空的事件（一次撤销点）。 */
    fun deleteEmptyLines() {
        session.editEvents { DeleteEmpty.apply(it) }
    }

    /**
     * 批量替换事件样式名（一次撤销点）。from 为空则 no-op。
     * @param selectedIds 非空时仅作用于命中行（多选批量）
     */
    fun replaceStyles(fromStyle: String, toStyle: String, selectedIds: Set<Long> = emptySet()) {
        session.editEvents { events ->
            if (selectedIds.isEmpty()) {
                StyleReplace.apply(events, fromStyle, toStyle)
            } else {
                events.map { e ->
                    if (e.id !in selectedIds || e.style != fromStyle) e else e.copy(style = toStyle)
                }
            }
        }
    }

    // ---------- 行级操作（复制/删除/插入/分割/合并/上下移，每次一个撤销点）----------

    /**
     * 对 [eventId] 指定的事件应用行级操作。
     * 下标在变换闭包内按当前事件列表解析，保证与显示一致；id 未命中则 no-op。
     */
    fun applyLineAction(eventId: Long, action: LineAction) {
        session.editEvents { current ->
            val index = current.indexOfFirst { it.id == eventId }
            if (index < 0) return@editEvents current
            when (action) {
                LineAction.INSERT_BEFORE -> LineOps.insertBefore(current, index)
                LineAction.INSERT_AFTER -> LineOps.insertAfter(current, index)
                LineAction.DUPLICATE -> LineOps.duplicate(current, index)
                LineAction.SPLIT -> LineOps.splitAtMidpoint(current, index)
                LineAction.JOIN_NEXT ->
                    if (index in 0..current.lastIndex - 1) LineOps.joinConcatenate(current, listOf(index, index + 1))
                    else current
                LineAction.MOVE_UP -> LineOps.moveUp(current, index)
                LineAction.MOVE_DOWN -> LineOps.moveDown(current, index)
                LineAction.DELETE -> LineOps.delete(current, index)
            }
        }
    }

    /** 批量排序（一次撤销点）。 */
    fun sortLines(key: SortKey, order: SortOrder) {
        session.editEvents { SortLines.apply(it, key, order) }
    }

    /** 帧率转换：按 toFps/fromFps 等比缩放全部起止时间（一次撤销点）。 */
    fun convertFramerate(fromFps: Double, toFps: Double) {
        session.editEvents { FramerateConverter.rescale(it, fromFps, toFps) }
    }

    /**
     * 批量写入 [Script Info] 键值（一次撤销点，用于脚本属性面板）。
     * 已存在键原地更新，新键追加；空 Map 为 no-op。
     */
    fun applyScriptInfo(changes: Map<String, String>) {
        if (changes.isEmpty()) return
        session.editInfo { info ->
            var result = info
            for ((k, v) in changes) result = ScriptInfoOps.set(result, k, v)
            result
        }
    }

    fun undo() = session.undo()
    fun redo() = session.redo()

    // ---------- 多选批量（作用于 id 集合，单撤销点）----------

    /** 删除 id 命中的事件。 */
    fun deleteSelected(ids: Set<Long>) {
        session.editEvents { SelectionOps.deleteByIds(it, ids) }
    }

    /** 复制 id 命中的事件（副本紧跟原行后）。 */
    fun duplicateSelected(ids: Set<Long>) {
        session.editEvents { SelectionOps.duplicateByIds(it, ids) }
    }

    /** 连续选中块整体上移一行。 */
    fun moveSelectedUp(ids: Set<Long>) {
        session.editEvents { SelectionOps.moveUpByIds(it, ids) }
    }

    /** 连续选中块整体下移一行。 */
    fun moveSelectedDown(ids: Set<Long>) {
        session.editEvents { SelectionOps.moveDownByIds(it, ids) }
    }

    /**
     * 时间后处理（一次撤销点）：lead-in/out + 去重叠强制最小间隙，作用于全部事件（按网格顺序）。
     */
    fun applyTimingPostProcess(leadInMs: Long, leadOutMs: Long, gapMs: Long) {
        session.editEvents { TimePostProcess.apply(it, leadInMs, leadOutMs, gapMs) }
    }

    /**
     * 分辨率重采样（一次撤销点）：缩放 {\pos}/{\move} + 样式字号/描边/边距 + 更新 PlayResX/Y。
     */
    fun resampleResolution(
        fromW: Int, fromH: Int, toW: Int, toH: Int,
        scalePositions: Boolean, scaleBorders: Boolean,
    ) {
        session.editScript {
            ResolutionResampler.rescale(
                it, fromW, fromH, toW, toH,
                ResolutionResampler.Options(scalePositions, scaleBorders),
            )
        }
    }

    /** 当前脚本的 PlayResX/Y（缺省 384/288），供重采样对话框预填。 */
    fun playRes(): Pair<Int, Int> {
        val s = session.script.value ?: return 384 to 288
        val w = s.getScriptInfo("PlayResX")?.toIntOrNull() ?: 384
        val h = s.getScriptInfo("PlayResY")?.toIntOrNull() ?: 288
        return w to h
    }

    // 注：时间偏移与样式批量替换已支持 selectedIds 参数（见上方 shiftTimes/replaceStyles）。

    // ---------- 历史版本恢复 ----------

    /** 把当前脚本存为一条历史快照（手动标签）。 */
    fun takeSnapshot(label: String) {
        viewModelScope.launch {
            val content = session.script.value?.let { AssFormat.write(it) } ?: return@launch
            snapshots.saveSnapshot(projectId, content, label, System.currentTimeMillis())
        }
    }

    /** 恢复某条快照：取其内容，作为新撤销点载入（可撤销）。 */
    fun restoreSnapshot(snapshotId: Long) {
        viewModelScope.launch {
            val content = snapshots.getSnapshotContent(snapshotId) ?: return@launch
            session.restoreFromContent(content)
        }
    }

    /** 删除某条历史快照。 */
    fun deleteSnapshot(snapshotId: Long) {
        viewModelScope.launch { snapshots.deleteSnapshot(snapshotId) }
    }

    /**
     * 导出当前脚本为 ASS 文本。读取用户设置的导出精度（厘秒/毫秒/自动），
     * 无脚本时返回空串。
     */
    suspend fun exportContent(): String {
        val precision = settings.settings.first().exportPrecision
        return session.script.value?.let { AssFormat.write(it, WriteOptions(timePrecision = precision)) } ?: ""
    }
}
