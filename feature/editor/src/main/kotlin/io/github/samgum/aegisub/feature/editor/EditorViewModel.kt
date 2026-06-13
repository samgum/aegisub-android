package io.github.samgum.aegisub.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.session.ProjectSessionManager
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.data.settings.SettingsRepository
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.WriteOptions
import io.github.samgum.aegisub.domain.edit.DeleteEmpty
import io.github.samgum.aegisub.domain.edit.ShiftTarget
import io.github.samgum.aegisub.domain.edit.StyleReplace
import io.github.samgum.aegisub.domain.edit.TimeShift
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.text.FindReplace
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = savedStateHandle.get<String>("projectId")!!.toLong()

    private val session = manager.open(projectId)

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

    fun undo() = session.undo()
    fun redo() = session.redo()

    /**
     * 导出当前脚本为 ASS 文本。读取用户设置的导出精度（厘秒/毫秒/自动），
     * 无脚本时返回空串。
     */
    suspend fun exportContent(): String {
        val precision = settings.settings.first().exportPrecision
        return session.script.value?.let { AssFormat.write(it, WriteOptions(timePrecision = precision)) } ?: ""
    }
}
