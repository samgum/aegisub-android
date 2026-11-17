package io.github.samgum.aegisub.feature.editor.styles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.session.ProjectSessionManager
import io.github.samgum.aegisub.data.settings.SettingsRepository
import io.github.samgum.aegisub.domain.model.AssStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 样式管理器 ViewModel（完整复刻桌面 Aegisub Styles Manager 双栏：全局样式库 Storage ↔ 当前脚本 Current Script）：
 * - 支持新增、复制、删除、编辑当前脚本样式
 * - 支持「从字幕行提取未定义样式」自动补齐缺失样式
 * - 支持将当前脚本样式存入全局样式库（Storage）或从全局样式库导入到当前脚本
 *
 * @author 伤感咩吖
 */
@HiltViewModel
class StyleEditorViewModel @Inject constructor(
    manager: ProjectSessionManager,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = savedStateHandle.get<String>("projectId")!!.toLong()

    private val session = manager.open(projectId)

    val styles: StateFlow<List<AssStyle>> = session.script
        .filterNotNull()
        .map { it.styles }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 全局样式库（跨工程持久化，仿桌面 Aegisub Styles Manager 左栏 Storage）。 */
    val storageStyles: StateFlow<List<AssStyle>> = settingsRepository.settings
        .map { s -> s.styleStorageLines.mapNotNull { line -> runCatching { AssStyle.parse(line) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val canUndo: StateFlow<Boolean> = session.canUndo
    val canRedo: StateFlow<Boolean> = session.canRedo

    fun addStyle() {
        session.editStyles { list ->
            val base = "New Style"
            val existing = list.map { it.name }.toSet()
            var n = 1
            var name = base
            while (name in existing) {
                name = "$base $n"; n++
            }
            list + AssStyle(name = name)
        }
    }

    fun duplicateStyle(index: Int) {
        session.editStyles { list ->
            val src = list.getOrNull(index) ?: return@editStyles list
            val existing = list.map { it.name }.toSet()
            var n = 1
            var copyName = "${src.name} Copy"
            while (copyName in existing) {
                copyName = "${src.name} Copy $n"; n++
            }
            list.toMutableList().apply { add(index + 1, src.copy(name = copyName)) }
        }
    }

    /** 扫描事件行中引用但尚未在 [styles] 定义的样式名并自动生成默认样式。 */
    fun extractMissingStylesFromEvents(): Int {
        val script = session.script.value ?: return 0
        val defined = script.styles.map { it.name }.toSet()
        val missing = script.events.map { it.style.trim() }
            .filter { it.isNotEmpty() && it !in defined }
            .distinct()
        if (missing.isEmpty()) return 0
        session.editStyles { list ->
            val template = list.firstOrNull { it.name == "Default" } ?: list.firstOrNull() ?: AssStyle()
            list + missing.map { name -> template.copy(name = name) }
        }
        return missing.size
    }

    fun saveToStorage(style: AssStyle) {
        viewModelScope.launch {
            val cur = storageStyles.first().filterNot { it.name == style.name } + style
            settingsRepository.setStyleStorageLines(cur.map { it.toStyleLine() })
        }
    }

    fun importFromStorage(style: AssStyle) {
        session.editStyles { list ->
            val idx = list.indexOfFirst { it.name == style.name }
            if (idx >= 0) {
                list.mapIndexed { i, s -> if (i == idx) style else s }
            } else {
                list + style
            }
        }
    }

    fun deleteFromStorage(styleName: String) {
        viewModelScope.launch {
            val cur = storageStyles.first().filterNot { it.name == styleName }
            settingsRepository.setStyleStorageLines(cur.map { it.toStyleLine() })
        }
    }

    fun updateStyle(index: Int, transform: (AssStyle) -> AssStyle) {
        session.editStyles { list ->
            list.mapIndexed { i, s -> if (i == index) transform(s) else s }
        }
    }

    fun deleteStyle(index: Int) {
        session.editStyles { list ->
            list.toMutableList().apply { if (index in indices) removeAt(index) }
        }
    }

    fun undo() = session.undo()
    fun redo() = session.redo()
}
