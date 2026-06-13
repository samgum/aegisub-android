package io.github.samgum.aegisub.data.session

import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.FormatRegistry
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.undo.SnapshotUndoStack
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * [ProjectSession] 默认实现：复刻自原 EditorViewModel 的编辑/撤销/防抖回写语义。
 *
 * - [start] 异步从 repo 读 content → 解析 → 分配稳定 event.id → 挂 SnapshotUndoStack
 * - [editEvent]/[undo]/[redo] 操作 stack 并推 [script]，触发防抖回写
 * - 防抖 [AUTOSAVE_DEBOUNCE_MS] 回写 Room，跳过加载首版本
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class ProjectSessionImpl(
    override val projectId: Long,
    private val repo: ProjectRepository,
) : ProjectSession {

    /**
     * 独立 scope（Main.immediate）：防抖收集长期运行，不挂调用方的 runTest scope，
     * 避免测试结束时「collect 永久挂起」被判为未完成协程（原 EditorViewModel 的 wireAutoSave
     * 跑在独立 viewModelScope 上同理）。测试通过 Dispatchers.setMain 注入 TestDispatcher 推进虚拟时间。
     *
     * @author 伤感咩吖
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var stack: SnapshotUndoStack<AssScript>? = null

    private val _script = MutableStateFlow<AssScript?>(null)
    override val script: StateFlow<AssScript?> = _script.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    override val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    override val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    init {
        // 防抖回写：script 变化后（跳过加载首版本）回写 Room
        scope.launch {
            _script
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .debounce(AUTOSAVE_DEBOUNCE_MS)
                .collect { script ->
                    repo.updateContent(projectId, AssFormat.write(script), System.currentTimeMillis())
                }
        }
    }

    /** 触发异步加载。由 [ProjectSessionManager.open] 调用；幂等（重复调用仅加载一次）。 */
    fun start() {
        if (stack != null || _errorMessage.value != null) return
        scope.launch {
            try {
                val content = repo.getContent(projectId)
                val parsed = FormatRegistry.detect(content)?.read(content) ?: AssScript.default()
                val script = parsed.withEvents(parsed.events.mapIndexed { i, e -> e.copy(id = i.toLong()) })
                stack = SnapshotUndoStack(script)
                _script.value = script
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "加载失败"
            }
        }
    }

    override fun editEvent(eventId: Long, transform: (AssEvent) -> AssEvent) {
        val s = stack ?: return
        val current = s.current
        val newEvents = current.events
            .map { if (it.id == eventId) transform(it) else it }
            .toPersistentList()
        commit(current.withEvents(newEvents))
    }

    override fun editAllEvents(transform: (AssEvent) -> AssEvent) {
        val s = stack ?: return
        val current = s.current
        val newEvents = current.events.map(transform).toPersistentList()
        commit(current.withEvents(newEvents))
    }

    private fun commit(newScript: AssScript) {
        val s = stack ?: return
        s.commit(newScript, "edit")
        _script.value = s.current
        syncFlags()
    }

    override fun undo() {
        val s = stack ?: return
        s.undo()?.let {
            _script.value = it
            syncFlags()
        }
    }

    override fun redo() {
        val s = stack ?: return
        s.redo()?.let {
            _script.value = it
            syncFlags()
        }
    }

    private fun syncFlags() {
        val s = stack
        _canUndo.value = s?.canUndo ?: false
        _canRedo.value = s?.canRedo ?: false
    }

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 800L
    }
}
