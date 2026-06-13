package io.github.samgum.aegisub.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.FormatRegistry
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.domain.undo.SnapshotUndoStack
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 字幕编辑器 ViewModel：
 * 加载项目内容→解析 AssScript→挂载 CoW 撤销栈→暴露状态。
 * 编辑操作（文本/时间/样式/层）经 [commit] 入撤销栈并刷新状态；
 * undo/redo 在栈上回溯；脚本变化后防抖 [AUTOSAVE_DEBOUNCE_MS] 回写 Room。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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
        wireAutoSave()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val content = repo.getContent(projectId)
                val parsed = FormatRegistry.detect(content)?.read(content) ?: AssScript.default()
                // 文件写入不保留 event.id，加载后按行序分配稳定唯一 id，供 LazyColumn key 使用
                val script = parsed.withEvents(parsed.events.mapIndexed { i, e -> e.copy(id = i.toLong()) })
                stack = SnapshotUndoStack(script)
                _state.value = EditorUiState.Loaded(script)
                syncUndoFlags()
            } catch (e: Exception) {
                _state.value = EditorUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    /** 防抖自动保存：脚本变化后回写 Room（跳过加载首版本，避免无谓写库）。 */
    private fun wireAutoSave() {
        viewModelScope.launch {
            state.filterIsInstance<EditorUiState.Loaded>()
                .map { it.script }
                .distinctUntilChanged()
                .drop(1) // 跳过加载后的首版本
                .debounce(AUTOSAVE_DEBOUNCE_MS)
                .collect { script ->
                    repo.updateContent(projectId, AssFormat.write(script), System.currentTimeMillis())
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

    // ---------- 编辑操作（Task 2B.1）----------

    /** 提交新脚本到撤销栈并刷新状态（同时会触发防抖保存）。 */
    private fun commit(newScript: AssScript, description: String) {
        val s = stack ?: return
        s.commit(newScript, description)
        _state.value = EditorUiState.Loaded(s.current)
        syncUndoFlags()
    }

    /** 对指定 id 的事件应用变换，产出新不可变脚本并提交。 */
    private fun edit(eventId: Long, transform: (AssEvent) -> AssEvent) {
        val current = (state.value as? EditorUiState.Loaded)?.script ?: return
        val newEvents = current.events
            .map { if (it.id == eventId) transform(it) else it }
            .toPersistentList()
        commit(current.withEvents(newEvents), "edit")
    }

    fun updateEventText(eventId: Long, text: String) =
        edit(eventId) { it.copy(text = text) }

    fun updateEventTimes(eventId: Long, start: SubTime, end: SubTime) =
        edit(eventId) { it.copy(start = start, end = end) }

    fun updateEventStyle(eventId: Long, style: String) =
        edit(eventId) { it.copy(style = style) }

    fun setEventLayer(eventId: Long, layer: Int) =
        edit(eventId) { it.copy(layer = layer) }

    fun undo() {
        stack?.undo()?.let {
            _state.value = EditorUiState.Loaded(it)
            syncUndoFlags()
        }
    }

    fun redo() {
        stack?.redo()?.let {
            _state.value = EditorUiState.Loaded(it)
            syncUndoFlags()
        }
    }

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 800L
    }
}
