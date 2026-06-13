package io.github.samgum.aegisub.feature.editor.styles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.session.ProjectSessionManager
import io.github.samgum.aegisub.domain.model.AssStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 样式编辑器 ViewModel：复用与编辑器/预览同源的 [io.github.samgum.aegisub.data.session.ProjectSession]。
 * 所有增删改经 session.editStyles，单次提交一个撤销点 → 与正文编辑共用撤销栈。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
class StyleEditorViewModel @Inject constructor(
    manager: ProjectSessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = savedStateHandle.get<String>("projectId")!!.toLong()

    private val session = manager.open(projectId)

    val styles: StateFlow<List<AssStyle>> = session.script
        .filterNotNull()
        .map { it.styles }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val canUndo: StateFlow<Boolean> = session.canUndo
    val canRedo: StateFlow<Boolean> = session.canRedo

    /** 追加一个新样式（以默认值为模板，名字自动去重）。 */
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

    /** 按当前列表下标更新单个样式。 */
    fun updateStyle(index: Int, transform: (AssStyle) -> AssStyle) {
        session.editStyles { list ->
            list.mapIndexed { i, s -> if (i == index) transform(s) else s }
        }
    }

    /** 删除指定下标的样式。 */
    fun deleteStyle(index: Int) {
        session.editStyles { list ->
            list.toMutableList().apply { if (index in indices) removeAt(index) }
        }
    }

    fun undo() = session.undo()
    fun redo() = session.redo()
}
