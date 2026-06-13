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
import io.github.samgum.aegisub.domain.edit.LineOps
import io.github.samgum.aegisub.domain.edit.ShiftTarget
import io.github.samgum.aegisub.domain.edit.SortKey
import io.github.samgum.aegisub.domain.edit.SortLines
import io.github.samgum.aegisub.domain.edit.SortOrder
import io.github.samgum.aegisub.domain.edit.ScriptInfoOps
import io.github.samgum.aegisub.domain.edit.StyleReplace
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
     */
    fun shiftTimes(deltaMs: Long, target: ShiftTarget, fromStartMs: Long? = null) {
        val fromStart = fromStartMs?.let { SubTime.ofMillis(it) }
        session.editEvents { events -> TimeShift.apply(events, deltaMs, target, fromStart) }
    }

    /** 删除有效内容为空的事件（一次撤销点）。 */
    fun deleteEmptyLines() {
        session.editEvents { DeleteEmpty.apply(it) }
    }

    /** 批量替换事件样式名（一次撤销点）。from 为空则 no-op。 */
    fun replaceStyles(fromStyle: String, toStyle: String) {
        session.editEvents { StyleReplace.apply(it, fromStyle, toStyle) }
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
