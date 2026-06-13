package io.github.samgum.aegisub.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.domain.format.FormatRegistry
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.undo.SnapshotUndoStack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 字幕编辑器 ViewModel：加载项目内容→解析 AssScript→挂载 CoW 撤销栈→暴露状态。
 * 编辑/撤销/保存见 2B 扩展。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repo: ProjectRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = savedStateHandle.get<String>("projectId")!!.toLong()

    private val _state = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    internal var stack: SnapshotUndoStack<AssScript>? = null
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val content = repo.getContent(projectId)
                val script = FormatRegistry.detect(content)?.read(content) ?: AssScript.default()
                stack = SnapshotUndoStack(script)
                _state.value = EditorUiState.Loaded(script)
                syncUndoFlags()
            } catch (e: Exception) {
                _state.value = EditorUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    private fun syncUndoFlags() {
        val s = stack
        _canUndo.value = s?.canUndo ?: false
        _canRedo.value = s?.canRedo ?: false
    }

    /** 当前已加载脚本（未加载返回 null）。 */
    fun currentScript(): AssScript? = (state.value as? EditorUiState.Loaded)?.script
}
