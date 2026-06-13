# 预览可写化（共享 ProjectSession + 滑块/微调调时间）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让预览屏可编辑字幕行起止时间——引入共享 `ProjectSession`（编辑器+预览同源），预览选中行后用起止滑块+微调按钮调时间，加只读时间轴条与撤销。

**Architecture:** 把 `EditorViewModel` 现有的「script + SnapshotUndoStack + 防抖回写」下沉到 `:core:data` 的 `ProjectSession`（`@Singleton` 的 `ProjectSessionManager` 按 projectId 缓存，编辑器与预览拿到同一实例 → script StateFlow + 撤销栈共享）。`EditorViewModel` 变薄为委托层；`PreviewViewModel` 从只读变为可写（`editEventTimes`/`nudge`/`selectEvent`/`undo`）。时间约束抽成 domain 纯函数 `TimingConstraints`。

**Tech Stack:** Kotlin 2.1.0 / Compose / Hilt / Coroutines StateFlow / Room / Media3；domain=JUnit5，data/preview=JUnit4。

---

## 文件结构

**新建：**
- `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/preview/TimingConstraints.kt` — 时间约束纯函数（start<end、上界钳制）
- `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/preview/TimingConstraintsTest.kt` — JUnit5
- `core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSession.kt` — 接口
- `core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionImpl.kt` — 实现（持有 stack + 防抖回写）
- `core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionManager.kt` — @Singleton，按 projectId 缓存
- `core/data/src/test/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionImplTest.kt` — JUnit4
- `core/data/src/test/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionManagerTest.kt` — JUnit4
- `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/TimingEditPanel.kt` — 起止滑块+微调
- `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/TimelineBar.kt` — 只读时间轴条（Canvas）

**修改：**
- `feature/editor/.../EditorViewModel.kt` — 委托 session（删 stack/load/wireAutoSave/commit/edit）
- `feature/editor/src/test/.../EditorViewModelTest.kt` — 构造改传 `ProjectSessionManager`
- `feature/preview/.../PreviewViewModel.kt` — 注入 manager+repo+player，加 selectedEventId/editEventTimes/nudge/selectEvent/undo
- `feature/preview/.../PreviewUiState.kt`（在 PreviewViewModel.kt 内）— Loaded 加 `selectedEventId`
- `feature/preview/src/test/.../PreviewViewModelTest.kt` — 构造改 + 新增编辑测试
- `feature/preview/.../PreviewScreen.kt` — 接入 selectEvent/TimingEditPanel/TimelineBar/undo
- `ROADMAP.md` — 状态更新

---

## Task 1: TimingConstraints 纯函数（domain）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/preview/TimingConstraints.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/preview/TimingConstraintsTest.kt`

约束规则（spec §7）：`0 ≤ start`，`end ≥ start + 1ms`，`end ≤ durationMs`（仅 durationMs > 0 时强制）。

- [ ] **Step 1: 写失败测试（JUnit5）**

`core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/preview/TimingConstraintsTest.kt`:

```kotlin
package io.github.samgum.aegisub.domain.preview

import io.github.samgum.aegisub.domain.time.SubTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * TimingConstraints 约束规则测试。
 *
 * @author 伤感咩吖
 */
class TimingConstraintsTest {

    @Test fun normal_pair_unchanged() {
        val (s, e) = TimingConstraints.constrain(SubTime.ofMillis(1_000), SubTime.ofMillis(3_000), 10_000)
        assertEquals(1_000L, s.millis)
        assertEquals(3_000L, e.millis)
    }

    @Test fun start_clamped_to_zero() {
        val (s, _) = TimingConstraints.constrain(SubTime.ofMillis(-500), SubTime.ofMillis(3_000), 10_000)
        assertEquals(0L, s.millis)
    }

    @Test fun end_clamped_to_duration_when_media_present() {
        val (_, e) = TimingConstraints.constrain(SubTime.ofMillis(9_000), SubTime.ofMillis(12_000), 10_000)
        assertEquals(10_000L, e.millis)
    }

    @Test fun end_not_clamped_when_no_media() {
        // durationMs <= 0 表示无媒体/未知时长，end 不设上界
        val (_, e) = TimingConstraints.constrain(SubTime.ofMillis(9_000), SubTime.ofMillis(120_000), 0)
        assertEquals(120_000L, e.millis)
    }

    @Test fun end_raised_to_start_plus_1ms_when_too_close() {
        val (s, e) = TimingConstraints.constrain(SubTime.ofMillis(5_000), SubTime.ofMillis(5_000), 10_000)
        assertEquals(5_000L, s.millis)
        assertEquals(5_001L, e.millis, "最小持续 1ms")
    }

    @Test fun end_raised_when_before_start() {
        val (s, e) = TimingConstraints.constrain(SubTime.ofMillis(8_000), SubTime.ofMillis(2_000), 10_000)
        assertEquals(8_000L, s.millis)
        assertEquals(8_001L, e.millis)
    }

    @Test fun both_clamped_together_start_near_duration() {
        // start=9500, end=9600, duration=10000：合法，不动
        val (s, e) = TimingConstraints.constrain(SubTime.ofMillis(9_500), SubTime.ofMillis(9_600), 10_000)
        assertEquals(9_500L, s.millis)
        assertEquals(9_600L, e.millis)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `"d:/VS Code Project/aegisub-android/gradlew" -p "d:/VS Code Project/aegisub-android" --no-daemon :core:domain:test --tests "*TimingConstraintsTest*"`
Expected: FAIL（`TimingConstraints` 未解析）

- [ ] **Step 3: 写实现**

`core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/preview/TimingConstraints.kt`:

```kotlin
package io.github.samgum.aegisub.domain.preview

import io.github.samgum.aegisub.domain.time.SubTime

/**
 * 字幕行起止时间的合法化约束（spec §7）：
 * - start 不小于 0
 * - end 不小于 start + 1ms（最小持续，防零宽）
 * - end 不大于 durationMs（仅在有媒体/已知时长，即 durationMs > 0 时强制）
 *
 * 纯函数，无副作用，可纯 JVM 单测。
 *
 * @author 伤感咩吖
 */
object TimingConstraints {

    private val MIN_DURATION = SubTime.ofMillis(1)

    /** 把期望的 start/end 钳制为合法对，返回 (start, end)。 */
    fun constrain(start: SubTime, end: SubTime, durationMs: Long): Pair<SubTime, SubTime> {
        val s = if (start.millis < 0) SubTime.ZERO else start
        var e = end
        if (durationMs > 0 && e.millis > durationMs) {
            e = SubTime.ofMillis(durationMs)
        }
        if (e <= s) {
            e = s + MIN_DURATION
        }
        return s to e
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS（7 tests）

- [ ] **Step 5: 提交**

```bash
git -C "d:/VS Code Project/aegisub-android" add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/preview/TimingConstraints.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/preview/TimingConstraintsTest.kt
git -C "d:/VS Code Project/aegisub-android" commit -m "feat(domain): TimingConstraints 起止时间合法化纯函数 + 7 单测"
```

---

## Task 2: ProjectSession 接口与实现

**Files:**
- Create: `core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSession.kt`
- Create: `core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionImpl.kt`
- Test: `core/data/src/test/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionImplTest.kt`

> **执行调整（已验证）**：`ProjectSessionImpl` 改用**内部自建 scope（`Main.immediate`）**，不再接收 `scope` 构造参数。原计划把防抖 collect 跑在传入的 runTest scope 上，实测触发 `UncompletedCoroutinesError`（collect 无限挂起，runTest 判定未完成）；与原 `EditorViewModel.wireAutoSave` 跑在独立 `viewModelScope` 同理改用独立 scope。测试需 `Dispatchers.setMain(dispatcher)` + `runTest(dispatcher)`，`session(repo)` 不再传 scope——以下 ProjectSessionImplTest 代码块以**实际文件**为准（含 setMain/runTest(dispatcher)），文字描述的 Step 1-6 逻辑不变。

- [ ] **Step 1: 写接口**

`core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSession.kt`:

```kotlin
package io.github.samgum.aegisub.data.session

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import kotlinx.coroutines.flow.StateFlow

/**
 * 一个字幕工程的共享编辑会话：单一 script 数据源 + 撤销栈 + 防抖回写。
 * 编辑器屏与预览屏通过 [ProjectSessionManager.open] 拿到同一实例，
 * 任何一端编辑，两端订阅的 [script] 自动一致。
 *
 * 加载是异步的：构造后 [script] 为 null，加载完成才有值；[errorMessage] 非空表示加载失败。
 *
 * @author 伤感咩吖
 */
interface ProjectSession {
    val projectId: Long

    /** 当前脚本；null 表示尚未加载完成。 */
    val script: StateFlow<AssScript?>

    /** 加载错误信息；null 表示无错误。 */
    val errorMessage: StateFlow<String?>

    val canUndo: StateFlow<Boolean>
    val canRedo: StateFlow<Boolean>

    /** 对指定 id 的事件应用变换，产出新不可变脚本并入撤销栈（触发防抖回写）。 */
    fun editEvent(eventId: Long, transform: (AssEvent) -> AssEvent)

    fun undo()
    fun redo()
}
```

- [ ] **Step 2: 写失败测试（JUnit4）**

`core/data/src/test/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionImplTest.kt`:

```kotlin
package io.github.samgum.aegisub.data.session

import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ProjectSessionImpl：加载/编辑/撤销重做/防抖回写/错误态。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSessionImplTest {

    private val assSample = """
        [Script Info]
        ScriptType: v4.00+

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello
    """.trimIndent()

    private fun session(
        repo: ProjectRepository,
        scope: CoroutineScope,
    ): ProjectSessionImpl = ProjectSessionImpl(projectId = 42, repo = repo, scope = scope).also { it.start() }

    @Test fun loads_script_and_assigns_stable_ids() = runTest {
        val s = session(FakeRepo(assSample), this)
        advanceUntilIdle()
        assertNull(s.errorMessage.value)
        assertEquals(1, s.script.value!!.events.size)
        assertEquals(0L, s.script.value!!.events.first().id)
    }

    @Test fun error_state_when_repo_throws() = runTest {
        val s = session(FakeRepo(throwOnGet = true), this)
        advanceUntilIdle()
        assertTrue(s.errorMessage.value != null)
        assertEquals(null, s.script.value)
    }

    @Test fun editEvent_changes_event_and_enables_undo() = runTest {
        val s = session(FakeRepo(assSample), this)
        advanceUntilIdle()
        val id = s.script.value!!.events.first().id
        s.editEvent(id) { it.copy(text = "Changed") }
        assertEquals("Changed", s.script.value!!.events.first().text)
        assertTrue(s.canUndo.value)
        assertFalse(s.canRedo.value)
    }

    @Test fun undo_then_redo() = runTest {
        val s = session(FakeRepo(assSample), this)
        advanceUntilIdle()
        val id = s.script.value!!.events.first().id
        s.editEvent(id) { it.copy(text = "Changed") }
        s.undo()
        assertEquals("Hello", s.script.value!!.events.first().text)
        assertTrue(s.canRedo.value)
        s.redo()
        assertEquals("Changed", s.script.value!!.events.first().text)
        assertTrue(s.canUndo.value)
    }

    @Test fun first_version_not_saved() = runTest {
        val repo = FakeRepo(assSample)
        val s = session(repo, this)
        advanceUntilIdle()
        assertEquals("加载首版本不应触发保存", 0, repo.saved.size)
    }

    @Test fun edits_debounced_then_saved() = runTest {
        val repo = FakeRepo(assSample)
        val s = session(repo, this)
        advanceUntilIdle()
        val id = s.script.value!!.events.first().id
        s.editEvent(id) { it.copy(text = "Changed") }
        advanceTimeBy(799)
        assertEquals("debounce 未满不应保存", 0, repo.saved.size)
        advanceTimeBy(2)
        advanceUntilIdle()
        assertEquals(1, repo.saved.size)
        assertTrue(repo.saved.first().contains("Changed"))
    }

    @Test fun editEvent_ignored_before_load() = runTest {
        // 未加载就编辑：不崩，script 仍 null
        val s = ProjectSessionImpl(42, FakeRepo(assSample), this)
        s.editEvent(0) { it.copy(text = "x") } // start() 未调用
        advanceUntilIdle()
        // script 由 start 加载；此处未 start，保持 null
        assertEquals(null, s.script.value)
    }

    private class FakeRepo(
        private val content: String = "",
        private val throwOnGet: Boolean = false,
    ) : ProjectRepository {
        val saved = mutableListOf<String>()
        override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
        override fun observeProject(id: Long): Flow<Project?> = flowOf(null)
        override suspend fun getContent(id: Long): String {
            if (throwOnGet) throw RuntimeException("boom")
            return content
        }
        override suspend fun createProject(name: String, format: String, content: String) = 0L
        override suspend fun updateContent(id: Long, content: String, now: Long) { saved.add(content) }
        override suspend fun delete(id: Long) {}
        override suspend fun touchLastOpened(id: Long, now: Long) {}
        override suspend fun getMediaUri(id: Long): String? = null
        override suspend fun setMediaUri(id: Long, mediaUri: String) {}
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `"d:/VS Code Project/aegisub-android/gradlew" -p "d:/VS Code Project/aegisub-android" --no-daemon :core:data:test --tests "*ProjectSessionImplTest*"`
Expected: FAIL（`ProjectSessionImpl` 未解析）

- [ ] **Step 4: 写实现**

`core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionImpl.kt`:

```kotlin
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
```

> **说明：** `scope.launch(EmptyCoroutineContext)` 显式传空 context，确保协程跑在传入 scope 自带的 dispatcher 上（测试传 `runTest` 的 TestScope → Main=TestDispatcher；生产由 manager 传 `Dispatchers.Main.immediate`）。这保证 `debounce` 的虚拟时间在测试里受 `advanceTimeBy` 控制。

- [ ] **Step 5: 跑测试确认通过**

Run: 同 Step 3 命令
Expected: PASS（7 tests）

- [ ] **Step 6: 提交**

```bash
git -C "d:/VS Code Project/aegisub-android" add core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSession.kt core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionImpl.kt core/data/src/test/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionImplTest.kt
git -C "d:/VS Code Project/aegisub-android" commit -m "feat(data): ProjectSession 共享编辑会话（script+撤销栈+防抖回写）+ 7 单测"
```

---

## Task 3: ProjectSessionManager

**Files:**
- Create: `core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionManager.kt`
- Test: `core/data/src/test/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionManagerTest.kt`

- [ ] **Step 1: 写失败测试**

`core/data/src/test/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionManagerTest.kt`:

```kotlin
package io.github.samgum.aegisub.data.session

import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ProjectSessionManager：同 projectId 返回同一实例；不同 projectId 不同实例。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSessionManagerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun open_same_id_returns_same_instance() = runTest(dispatcher) {
        val manager = ProjectSessionManager(FakeRepo())
        val a = manager.open(42)
        val b = manager.open(42)
        assertTrue("同 projectId 应返回同一实例", a === b)
    }

    @Test fun open_different_ids_return_different_instances() = runTest(dispatcher) {
        val manager = ProjectSessionManager(FakeRepo())
        val a = manager.open(1)
        val b = manager.open(2)
        assertTrue("不同 projectId 应是不同实例", a !== b)
        assertEquals(1L, a.projectId)
        assertEquals(2L, b.projectId)
    }

    @Test fun shared_session_reflects_edits_across_openers() = runTest(dispatcher) {
        val manager = ProjectSessionManager(FakeRepo(assSample))
        val editor = manager.open(7)
        val preview = manager.open(7)
        advanceUntilIdle()
        val id = editor.script.value!!.events.first().id
        editor.editEvent(id) { it.copy(text = "FromEditor") }
        // 预览拿到的是同一 session，立即可见
        assertEquals("FromEditor", preview.script.value!!.events.first().text)
    }

    private val assSample = """
        [Script Info]
        ScriptType: v4.00+

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello
    """.trimIndent()

    private class FakeRepo(private val content: String = "") : ProjectRepository {
        override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
        override fun observeProject(id: Long): Flow<Project?> = flowOf(null)
        override suspend fun getContent(id: Long): String = content
        override suspend fun createProject(name: String, format: String, content: String) = 0L
        override suspend fun updateContent(id: Long, content: String, now: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun touchLastOpened(id: Long, now: Long) {}
        override suspend fun getMediaUri(id: Long): String? = null
        override suspend fun setMediaUri(id: Long, mediaUri: String) {}
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `"d:/VS Code Project/aegisub-android/gradlew" -p "d:/VS Code Project/aegisub-android" --no-daemon :core:data:test --tests "*ProjectSessionManagerTest*"`
Expected: FAIL（`ProjectSessionManager` 未解析）

- [ ] **Step 3: 写实现**

`core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionManager.kt`:

```kotlin
package io.github.samgum.aegisub.data.session

import io.github.samgum.aegisub.data.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按 projectId 缓存 [ProjectSession]：编辑器屏与预览屏 [open] 同一 projectId 拿到同一实例，
 * 从而共享 script 数据源与撤销栈。@Singleton 进程级缓存，字幕工程内存占用小，暂不主动回收。
 *
 * @author 伤感咩吖
 */
@Singleton
class ProjectSessionManager @Inject constructor(
    private val repo: ProjectRepository,
) {
    // session 内部协程跑在此 scope（Main dispatcher，测试可由 Dispatchers.setMain 替换）
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessions = mutableMapOf<Long, ProjectSession>()

    /** 返回（或创建并启动）projectId 对应的共享会话。线程安全。 */
    fun open(projectId: Long): ProjectSession = synchronized(sessions) {
        sessions.getOrPut(projectId) {
            ProjectSessionImpl(projectId, repo, scope).also { it.start() }
        }
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS（3 tests）

- [ ] **Step 5: 跑整个 :core:data 测试确认无回归**

Run: `"d:/VS Code Project/aegisub-android/gradlew" -p "d:/VS Code Project/aegisub-android" --no-daemon :core:data:test`
Expected: PASS（含既有 Room 假 DAO 测试 + 新增 10 个 session 测试）

- [ ] **Step 6: 提交**

```bash
git -C "d:/VS Code Project/aegisub-android" add core/data/src/main/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionManager.kt core/data/src/test/kotlin/io/github/samgum/aegisub/data/session/ProjectSessionManagerTest.kt
git -C "d:/VS Code Project/aegisub-android" commit -m "feat(data): ProjectSessionManager 按 projectId 缓存共享会话 + 3 单测"
```

---

## Task 4: EditorViewModel 委托化重构

**Files:**
- Modify: `feature/editor/src/main/kotlin/io/github/samgum/aegisub/feature/editor/EditorViewModel.kt`
- Modify: `feature/editor/src/test/kotlin/io/github/samgum/aegisub/feature/editor/EditorViewModelTest.kt`

安全网：现有 11 个编辑器测试必须保持全绿。public API（`state/projectId/canUndo/canRedo/currentScript/updateEventText/updateEventTimes/updateEventStyle/setEventLayer/undo/redo/exportContent`）不变，只换数据源。

- [ ] **Step 1: 重写 EditorViewModel 为委托层**

完整替换 `feature/editor/src/main/kotlin/io/github/samgum/aegisub/feature/editor/EditorViewModel.kt`:

```kotlin
package io.github.samgum.aegisub.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.session.ProjectSessionManager
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    fun undo() = session.undo()
    fun redo() = session.redo()

    /** 导出当前脚本为 ASS 文本。无脚本时返回空串。 */
    fun exportContent(): String = session.script.value?.let { AssFormat.write(it) } ?: ""
}
```

> `currentScript()` 返回 `AssScript?`，与重构前签名一致，保证测试里的 `currentScript()!!` 用法不破。

- [ ] **Step 2: 改 EditorViewModelTest 构造点**

`feature/editor/src/test/.../EditorViewModelTest.kt` 修改：

(a) 顶部 import 区追加：
```kotlin
import io.github.samgum.aegisub.data.session.ProjectSessionManager
```

(b) `vm()` 工厂改为：
```kotlin
    private fun vm(content: String?): EditorViewModel =
        EditorViewModel(ProjectSessionManager(FakeProjectRepository(content)), SavedStateHandle(mapOf("projectId" to "42")))
```

(c) `error_state_when_repo_throws` 改为：
```kotlin
    @Test fun error_state_when_repo_throws() = runTest(dispatcher) {
        val v = EditorViewModel(ProjectSessionManager(ThrowingRepo()), SavedStateHandle(mapOf("projectId" to "1")))
        advanceUntilIdle()
        assertTrue(v.state.value is EditorUiState.Error)
    }
```

(d) `first_version_is_not_saved` 改为：
```kotlin
    @Test fun first_version_is_not_saved() = runTest(dispatcher) {
        val repo = FakeProjectRepository(ASS_SAMPLE)
        EditorViewModel(ProjectSessionManager(repo), SavedStateHandle(mapOf("projectId" to "42")))
        advanceUntilIdle()
        assertEquals("加载首版本不应触发保存", 0, repo.savedContents.size)
    }
```

(e) `edits_are_debounced_then_saved` 改为：
```kotlin
    @Test fun edits_are_debounced_then_saved() = runTest(dispatcher) {
        val repo = FakeProjectRepository(ASS_SAMPLE)
        val v = EditorViewModel(ProjectSessionManager(repo), SavedStateHandle(mapOf("projectId" to "42")))
        advanceUntilIdle()
        val id = v.currentScript()!!.events.first().id
        v.updateEventText(id, "Changed")
        advanceTimeBy(799)
        assertEquals("debounce 未满不应保存", 0, repo.savedContents.size)
        advanceTimeBy(2)
        advanceUntilIdle()
        assertEquals(1, repo.savedContents.size)
        assertTrue(repo.savedContents.first().contains("Changed"))
    }
```

> 其余测试（`loads_ass_script_from_content`、`detects_plain_text_when_no_known_markers`、`canUndo_false_initially`、`updateEventText_*`、`updateEventTimes_*`、`updateEventStyle_and_layer`、`undo_restores_*`、`redo_reapplies_edit`）逻辑不变——它们用 `vm()` 工厂，工厂改了即自动适配。

- [ ] **Step 3: 跑编辑器测试确认全绿**

Run: `"d:/VS Code Project/aegisub-android/gradlew" -p "d:/VS Code Project/aegisub-android" --no-daemon :feature:editor:test"`
Expected: PASS（11 tests，与重构前一致）

- [ ] **Step 4: 提交**

```bash
git -C "d:/VS Code Project/aegisub-android" add feature/editor/src/main/kotlin/io/github/samgum/aegisub/feature/editor/EditorViewModel.kt feature/editor/src/test/kotlin/io/github/samgum/aegisub/feature/editor/EditorViewModelTest.kt
git -C "d:/VS Code Project/aegisub-android" commit -m "refactor(editor): EditorViewModel 委托 ProjectSession，编辑/撤销/保存下沉（11 测试保持绿）"
```

---

## Task 5: PreviewViewModel 可写化

**Files:**
- Modify: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/PreviewViewModel.kt`
- Modify: `feature/preview/src/test/kotlin/io/github/samgum/aegisub/feature/preview/PreviewViewModelTest.kt`

- [ ] **Step 1: 重写 PreviewViewModel**

完整替换 `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/PreviewViewModel.kt`:

```kotlin
package io.github.samgum.aegisub.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.data.session.ProjectSessionManager
import io.github.samgum.aegisub.domain.preview.ActiveSubtitleResolver
import io.github.samgum.aegisub.domain.preview.SubtitleRenderInfo
import io.github.samgum.aegisub.domain.preview.TimingConstraints
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 预览屏状态。
 *
 * @author 伤感咩吖
 */
sealed interface PreviewUiState {
    object Loading : PreviewUiState
    data class Loaded(
        val script: AssScript,
        val hasMedia: Boolean,
        val playback: PlaybackState,
        val currentEventId: Long?,
        val selectedEventId: Long?,
    ) : PreviewUiState
    data class Error(val message: String) : PreviewUiState
}

/**
 * 预览 ViewModel：与编辑器共享 [io.github.samgum.aegisub.data.session.ProjectSession]，
 * 因此可在预览屏直接编辑选中行的起止时间（editEventTimes/nudge），改动同步回编辑器并防抖落盘。
 * 媒体挂载仍由本类直管（不进 session）。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModel @Inject constructor(
    manager: ProjectSessionManager,
    private val repo: ProjectRepository,
    private val player: VideoPlayer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = savedStateHandle.get<String>("projectId")!!.toLong()

    private val session = manager.open(projectId)

    private val _hasMedia = MutableStateFlow(false)
    private val _selectedEventId = MutableStateFlow<Long?>(null)

    private val base = combine(session.script, session.errorMessage, _hasMedia) { script, error, hasMedia ->
        when {
            error != null -> BaseState.Error(error)
            script != null -> BaseState.Ready(script, hasMedia)
            else -> BaseState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BaseState.Loading)

    val state: StateFlow<PreviewUiState> = combine(base, player.state, _selectedEventId) { b, playback, selected ->
        when (b) {
            BaseState.Loading -> PreviewUiState.Loading
            is BaseState.Error -> PreviewUiState.Error(b.message)
            is BaseState.Ready -> PreviewUiState.Loaded(
                script = b.script,
                hasMedia = b.hasMedia,
                playback = playback,
                currentEventId = ActiveSubtitleResolver.activeEvent(b.script, playback.positionMs)?.id,
                selectedEventId = selected,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PreviewUiState.Loading)

    val activeSubtitle: StateFlow<SubtitleRenderInfo?> = combine(base, player.state) { b, playback ->
        when (b) {
            is BaseState.Ready -> ActiveSubtitleResolver.renderInfo(b.script, playback.positionMs)
            else -> null
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val canUndo: StateFlow<Boolean> = session.canUndo
    val canRedo: StateFlow<Boolean> = session.canRedo

    val videoPlayer: VideoPlayer get() = player

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            val uri = repo.getMediaUri(projectId)
            if (uri != null) {
                player.setMedia(uri)
                _hasMedia.value = true
            }
        }
    }

    fun playPause() {
        if (player.state.value.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun setSpeed(rate: Float) {
        player.setSpeed(rate)
    }

    /** 选中一行：seek 到该行开始 + 标记为编辑目标。 */
    fun selectEvent(eventId: Long) {
        val script = session.script.value ?: return
        val event = script.events.firstOrNull { it.id == eventId } ?: return
        _selectedEventId.value = eventId
        player.seekTo(event.start.millis)
    }

    /** 兼容旧入口：仅 seek 不改选中（保留以防遗漏调用点；新代码用 selectEvent）。 */
    fun seekToEvent(eventId: Long) {
        val script = session.script.value ?: return
        val event = script.events.firstOrNull { it.id == eventId } ?: return
        player.seekTo(event.start.millis)
    }

    /** 把选中行/指定行的起止设为给定值（经 TimingConstraints 钳制）。 */
    fun editEventTimes(eventId: Long, start: SubTime, end: SubTime) {
        val durationMs = player.state.value.durationMs
        val (s, e) = TimingConstraints.constrain(start, end, durationMs)
        session.editEvent(eventId) { it.copy(start = s, end = e) }
    }

    /** 微调选中行的起始时间 deltaMs 毫秒（负数前移）。 */
    fun nudgeStart(deltaMs: Long) {
        val id = _selectedEventId.value ?: return
        val event = session.script.value?.events?.firstOrNull { it.id == id } ?: return
        editEventTimes(id, SubTime.ofMillis(event.start.millis + deltaMs), event.end)
    }

    /** 微调选中行的结束时间 deltaMs 毫秒（负数前移）。 */
    fun nudgeEnd(deltaMs: Long) {
        val id = _selectedEventId.value ?: return
        val event = session.script.value?.events?.firstOrNull { it.id == id } ?: return
        editEventTimes(id, event.start, SubTime.ofMillis(event.end.millis + deltaMs))
    }

    fun attachMedia(uri: String) {
        viewModelScope.launch {
            repo.setMediaUri(projectId, uri)
            player.setMedia(uri)
            _hasMedia.value = true
        }
    }

    fun undo() = session.undo()
    fun redo() = session.redo()

    override fun onCleared() {
        player.release()
    }

    internal fun releaseForTest() {
        player.release()
    }

    private sealed interface BaseState {
        object Loading : BaseState
        data class Ready(val script: AssScript, val hasMedia: Boolean) : BaseState
        data class Error(val message: String) : BaseState
    }
}
```

- [ ] **Step 2: 改 PreviewViewModelTest**

`feature/preview/src/test/.../PreviewViewModelTest.kt` 修改：

(a) import 区追加：
```kotlin
import io.github.samgum.aegisub.data.session.ProjectSessionManager
import io.github.samgum.aegisub.domain.time.SubTime
```

(b) `vm()` 工厂改为注入 manager + repo（同一 fake repo 实例）：
```kotlin
    private fun vm(
        content: String? = sampleAss,
        mediaUri: String? = null,
        player: VideoPlayer = FakeVideoPlayer(),
    ): PreviewViewModel {
        val repo = FakeProjectRepository(content, mediaUri)
        return PreviewViewModel(ProjectSessionManager(repo), repo, player, SavedStateHandle(mapOf("projectId" to "42")))
    }
```

(c) `attach_media_persists_and_sets_player` 改为：
```kotlin
    @Test fun attach_media_persists_and_sets_player() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val repo = FakeProjectRepository(sampleAss, mediaUri = null)
        val v = PreviewViewModel(ProjectSessionManager(repo), repo, fake, SavedStateHandle(mapOf("projectId" to "42")))
        advanceUntilIdle()
        v.attachMedia("content://video/9")
        advanceUntilIdle()
        assertEquals("content://video/9", repo.setMediaUriRecorded)
        assertEquals("content://video/9", fake.mediaSet)
        assertTrue((v.state.value as PreviewUiState.Loaded).hasMedia)
    }
```

(d) `error_state_when_repo_throws` 改为：
```kotlin
    @Test fun error_state_when_repo_throws() = runTest(dispatcher) {
        val repo = FakeProjectRepository(throwOnGetContent = true)
        val v = PreviewViewModel(ProjectSessionManager(repo), repo, FakeVideoPlayer(), SavedStateHandle(mapOf("projectId" to "1")))
        advanceUntilIdle()
        assertTrue(v.state.value is PreviewUiState.Error)
    }
```

(e) **新增**编辑相关测试（追加到 `active_subtitle_only_emits_on_event_change` 之后、`// ---------- fakes ----------` 之前）：
```kotlin
    @Test fun select_event_seeks_and_marks_selected() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val v = vm(player = fake)
        advanceUntilIdle()
        v.selectEvent(1L) // 第二句 start=4s
        advanceUntilIdle()
        assertEquals(4_000L, fake.seekedTo)
        assertEquals(1L, (v.state.value as PreviewUiState.Loaded).selectedEventId)
    }

    @Test fun edit_event_times_writes_session_and_clamps() = runTest(dispatcher) {
        val fake = FakeVideoPlayer(durationMs = 10_000)
        val v = vm(player = fake)
        advanceUntilIdle()
        // 第二句原 [4s,6s]，把结束拖到 12s → 钳到 10s
        v.editEventTimes(1L, SubTime.ofMillis(4_000), SubTime.ofMillis(12_000))
        advanceUntilIdle() // 等 session → state combine 传播
        val e = (v.state.value as PreviewUiState.Loaded).script.events[1]
        assertEquals(4_000L, e.start.millis)
        assertEquals(10_000L, e.end.millis)
    }

    @Test fun nudge_start_adjusts_and_keeps_end() = runTest(dispatcher) {
        val fake = FakeVideoPlayer(durationMs = 10_000)
        val v = vm(player = fake)
        advanceUntilIdle()
        v.selectEvent(0L) // 第一句 [1s,3s]
        advanceUntilIdle()
        v.nudgeStart(1_000) // 起始 +1s → 2s
        advanceUntilIdle()
        val e = (v.state.value as PreviewUiState.Loaded).script.events[0]
        assertEquals(2_000L, e.start.millis)
        assertEquals(3_000L, e.end.millis)
    }

    @Test fun undo_in_preview_restores_timing() = runTest(dispatcher) {
        val fake = FakeVideoPlayer(durationMs = 10_000)
        val v = vm(player = fake)
        advanceUntilIdle()
        v.editEventTimes(0L, SubTime.ofMillis(2_000), SubTime.ofMillis(4_000))
        advanceUntilIdle()
        assertTrue(v.canUndo.value)
        v.undo()
        advanceUntilIdle()
        val e = (v.state.value as PreviewUiState.Loaded).script.events[0]
        assertEquals("撤销应回到原 1s", 1_000L, e.start.millis)
    }
```

(f) **FakeVideoPlayer 加 duration 参数**——把 `FakeVideoPlayer` 类定义改为：
```kotlin
    private class FakeVideoPlayer(private val durationMs: Long = 0L) : VideoPlayer {
        override val state = MutableStateFlow(PlaybackState(durationMs = durationMs))
        var seekedTo: Long? = null
        var mediaSet: String? = null
        var released = false
        override fun setMedia(uri: String) { mediaSet = uri }
        override fun play() { state.value = state.value.copy(isPlaying = true) }
        override fun pause() { state.value = state.value.copy(isPlaying = false) }
        override fun seekTo(positionMs: Long) { seekedTo = positionMs }
        override fun setSpeed(rate: Float) { state.value = state.value.copy(speed = rate) }
        override fun release() { released = true }
        fun emitPosition(ms: Long) { state.value = state.value.copy(positionMs = ms) }
    }
```

> 现有 `PlaybackControls`/`current_event_id_*`/`seek_to_event_*`/`attach_media_*`/`on_clear_*`/`active_subtitle_*` 测试逻辑不变，靠新 `vm()` 工厂自动适配。

- [ ] **Step 3: 跑预览测试确认全绿**

Run: `"d:/VS Code Project/aegisub-android/gradlew" -p "d:/VS Code Project/aegisub-android" --no-daemon :feature:preview:test`
Expected: PASS（原 10 + 新 4 = 14 tests）

- [ ] **Step 4: 提交**

```bash
git -C "d:/VS Code Project/aegisub-android" add feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/PreviewViewModel.kt feature/preview/src/test/kotlin/io/github/samgum/aegisub/feature/preview/PreviewViewModelTest.kt
git -C "d:/VS Code Project/aegisub-android" commit -m "feat(preview): PreviewViewModel 可写化（共享 session + editEventTimes/nudge/selectEvent/undo）+ 4 新测"
```

---

## Task 6: TimingEditPanel 组件

**Files:**
- Create: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/TimingEditPanel.kt`

UI 组件，无单测（与现有项目 UI 一致，靠编译 + assembleDebug + 手测）。

- [ ] **Step 1: 写组件**

`feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/TimingEditPanel.kt`:

```kotlin
package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 微调目标端。 */
enum class NudgeTarget { START, END }

/**
 * 选中行的时间编辑面板：起/止滑块（拖动实时 seek，松手提交）+ 微调按钮组。
 *
 * @param durationMs 视频时长；<=0 时滑块按 0 处理（仅微调可用）。
 * @param nudgeTarget 当前微调作用端，拖动滑块会自动切换。
 * @param onNudgeTargetChange 切换微调端。
 * @param onSeek 拖动滑块时实时 seek（看画面，不提交）。
 * @param onCommitStart 松手时把起始提交到该毫秒。
 * @param onCommitEnd 松手时把结束提交到该毫秒。
 * @param onNudge 微调按钮：对当前 nudgeTarget 加减 deltaMs。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimingEditPanel(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    nudgeTarget: NudgeTarget,
    onNudgeTargetChange: (NudgeTarget) -> Unit,
    onSeek: (Long) -> Unit,
    onCommitStart: (Long) -> Unit,
    onCommitEnd: (Long) -> Unit,
    onNudge: (deltaMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        StartSlider(startMs, durationMs, onSeek, onCommitStart, onSelect = { onNudgeTargetChange(NudgeTarget.START) })
        EndSlider(endMs, durationMs, onSeek, onCommitEnd, onSelect = { onNudgeTargetChange(NudgeTarget.END) })

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TargetChip("起", selected = nudgeTarget == NudgeTarget.START, onClick = { onNudgeTargetChange(NudgeTarget.START) })
            TargetChip("止", selected = nudgeTarget == NudgeTarget.END, onClick = { onNudgeTargetChange(NudgeTarget.END) })
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NudgeButton("−1帧") { onNudge(-FRAME_MS) }
            NudgeButton("−0.1s") { onNudge(-100) }
            NudgeButton("−1s") { onNudge(-1_000) }
            NudgeButton("+1帧") { onNudge(FRAME_MS) }
            NudgeButton("+0.1s") { onNudge(100) }
            NudgeButton("+1s") { onNudge(1_000) }
        }
    }
}

@Composable
private fun StartSlider(
    valueMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onCommit: (Long) -> Unit,
    onSelect: () -> Unit,
) {
    var dragging by remember { mutableStateOf(valueMs.toFloat()) }
    Column {
        Text("起 ${formatMs(valueMs)}", style = MaterialTheme.typography.labelSmall)
        val max = durationMs.coerceAtLeast(1)
        Slider(
            value = dragging.coerceIn(0f, max.toFloat()),
            onValueChange = {
                dragging = it
                onSeek(it.toLong())
                onSelect()
            },
            valueRange = 0f..max.toFloat(),
            onValueChangeFinished = { onCommit(dragging.toLong()) },
        )
    }
}

@Composable
private fun EndSlider(
    valueMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onCommit: (Long) -> Unit,
    onSelect: () -> Unit,
) {
    var dragging by remember { mutableStateOf(valueMs.toFloat()) }
    Column {
        Text("止 ${formatMs(valueMs)}", style = MaterialTheme.typography.labelSmall)
        val max = durationMs.coerceAtLeast(1)
        Slider(
            value = dragging.coerceIn(0f, max.toFloat()),
            onValueChange = {
                dragging = it
                onSeek(it.toLong())
                onSelect()
            },
            valueRange = 0f..max.toFloat(),
            onValueChangeFinished = { onCommit(dragging.toLong()) },
        )
    }
}

@Composable
private fun TargetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = if (selected) AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) else AssistChipDefaults.assistChipColors(),
    )
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatMs(ms: Long): String {
    val total = ms.coerceAtLeast(0)
    val m = total / 60_000
    val s = (total % 60_000) / 1_000
    val mm = total % 1_000
    return "%d:%02d.%03d".format(m, s, mm)
}

/** 23.976fps 一帧 ≈ 42ms。 */
private const val FRAME_MS: Long = 42L
```

- [ ] **Step 2: 编译确认**

Run: `"d:/VS Code Project/aegisub-android/gradlew" -p "d:/VS Code Project/aegisub-android" --no-daemon :feature:preview:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git -C "d:/VS Code Project/aegisub-android" add feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/TimingEditPanel.kt
git -C "d:/VS Code Project/aegisub-android" commit -m "feat(preview): TimingEditPanel 起止滑块+微调按钮组件"
```

---

## Task 7: TimelineBar 只读时间轴条

**Files:**
- Create: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/TimelineBar.kt`

UI 组件，无单测。

- [ ] **Step 1: 写组件**

`feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/TimelineBar.kt`:

```kotlin
package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.model.AssEvent
import kotlin.math.max

/**
 * 只读时间轴条：按视频时长把所有字幕行画成 mini 块，选中行高亮，播放头竖线随位置移动。
 * 不做拖拽（触屏精度单独攻，留后续）。
 *
 * @param events 全部字幕行。
 * @param selectedEventId 选中的编辑目标行 id（高亮）。
 * @param positionMs 当前播放位置。
 * @param durationMs 视频时长；<=0 时不绘制刻度。
 *
 * @author 伤感咩吖
 */
@Composable
fun TimelineBar(
    events: List<AssEvent>,
    selectedEventId: Long?,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val blockColor = androidx.compose.ui.graphics.Color(0xFF6B7280).copy(alpha = 0.55f)
    val selectedColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val playheadColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val trackColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth().height(36.dp)) {
        val w = size.width
        val h = size.height
        val span = max(durationMs.toFloat(), 1f)

        // 底线
        drawLine(
            color = trackColor,
            start = Offset(0f, h / 2f),
            end = Offset(w, h / 2f),
            strokeWidth = 1f,
        )

        // 各行 mini 块（错开两行避免重叠遮挡）
        events.forEachIndexed { i, e ->
            val left = (e.start.millis / span) * w
            val right = (e.end.millis / span) * w
            val width = (right - left).coerceAtLeast(2f)
            val row = if (i % 2 == 0) 4f else h - 14f
            drawRect(
                color = if (e.id == selectedEventId) selectedColor else blockColor,
                topLeft = Offset(left, row),
                size = Size(width, 10f),
            )
        }

        // 播放头
        val px = (positionMs / span) * w
        drawLine(
            color = playheadColor,
            start = Offset(px, 0f),
            end = Offset(px, h),
            strokeWidth = 2f,
        )
    }
}
```

> TimelineBar 只用 `event.start.millis`/`event.end.millis`，无需 `SubTime` import。

- [ ] **Step 2: 编译确认**

Run: `"d:/VS Code Project/aegisub-android/gradlew" -p "d:/VS Code Project/aegisub-android" --no-daemon :feature:preview:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git -C "d:/VS Code Project/aegisub-android" add feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/TimelineBar.kt
git -C "d:/VS Code Project/aegisub-android" commit -m "feat(preview): TimelineBar 只读时间轴条（mini块+播放头+选中高亮）"
```

---

## Task 8: PreviewScreen 集成

**Files:**
- Modify: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/PreviewScreen.kt`

改动点：①顶栏加 undo 按钮 ②PlaybackControls 下方加 TimelineBar ③事件行点击改 selectEvent + 高亮 selectedEventId ④选中行下方显示 TimingEditPanel。

- [ ] **Step 1: 改 PreviewScreen**

对 `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/PreviewScreen.kt` 做以下修改（其余不变）：

(a) import 区追加（PreviewScreen.kt 顶部）：
```kotlin
import androidx.compose.material.icons.automirrored.filled.Undo
import io.github.samgum.aegisub.feature.preview.components.NudgeTarget
import io.github.samgum.aegisub.feature.preview.components.TimelineBar
import io.github.samgum.aegisub.feature.preview.components.TimingEditPanel
import io.github.samgum.aegisub.domain.time.SubTime
```
> `getValue`/`remember`/`mutableStateOf`/`collectAsStateWithLifecycle`/`Icons` 均已在 PreviewScreen.kt 现有 import 中，无需重复。

(b) 在 `PreviewScreen` 顶层、`val state by viewModel.state.collectAsStateWithLifecycle()` 之后，追加一行收集撤销可用态：
```kotlin
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
```
然后给 `TopAppBar` 新增 `actions` 参数（原 TopAppBar 只有 title + navigationIcon）：
```kotlin
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
                    }
                }
```

(c) `VideoBlock` 末尾（`PlaybackControls(...)` 之后）追加时间轴条：
```kotlin
        TimelineBar(
            events = state.script.events,
            selectedEventId = state.selectedEventId,
            positionMs = state.playback.positionMs,
            durationMs = state.playback.durationMs,
        )
```

(d) `EventListColumn` 签名加 `selectedEventId` + `onSelect`，并传给行：
```kotlin
@Composable
private fun EventListColumn(
    events: List<AssEvent>,
    currentEventId: Long?,
    selectedEventId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(events, key = { it.id }) { event ->
            PreviewEventRow(
                event = event,
                isCurrent = event.id == currentEventId,
                isSelected = event.id == selectedEventId,
                onClick = { onSelect(event.id) },
            )
        }
    }
}
```

(e) `PreviewEventRow` 加 `isSelected` 参数，背景区分当前/选中：
```kotlin
@Composable
private fun PreviewEventRow(event: AssEvent, isCurrent: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    ListItem(
        headlineContent = {
            Text(
                text = event.strippedText.ifBlank { "（无文本）" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (event.comment) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = "${event.start.toAssString(false)} → ${event.end.toAssString(false)}  ·  ${event.style}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier = Modifier.background(bg).clickable(onClick = onClick),
    )
}
```

(f) `CompactPreview` 改为：事件列表上方插入编辑面板（选中时），事件列用 `selectEvent`：
```kotlin
@Composable
private fun CompactPreview(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        VideoBlock(state = state, viewModel = viewModel, onPickVideo = onPickVideo)
        if (state.selectedEventId != null) {
            TimingEditLayer(state = state, viewModel = viewModel)
        }
        EventListColumn(
            events = state.script.events,
            currentEventId = state.currentEventId,
            selectedEventId = state.selectedEventId,
            onSelect = viewModel::selectEvent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
```

(g) `ExpandedPreview` 同理（右侧列表上方加面板）：
```kotlin
@Composable
private fun ExpandedPreview(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        VideoBlock(state = state, viewModel = viewModel, onPickVideo = onPickVideo, modifier = Modifier.weight(0.6f))
        Column(modifier = Modifier.weight(0.4f)) {
            if (state.selectedEventId != null) {
                TimingEditLayer(state = state, viewModel = viewModel)
            }
            EventListColumn(
                events = state.script.events,
                currentEventId = state.currentEventId,
                selectedEventId = state.selectedEventId,
                onSelect = viewModel::selectEvent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
```

(h) 新增 `TimingEditLayer`（承载面板的本地状态：微调目标）：
```kotlin
@Composable
private fun TimingEditLayer(state: PreviewUiState.Loaded, viewModel: PreviewViewModel) {
    val event = state.script.events.firstOrNull { it.id == state.selectedEventId } ?: return
    var target by remember(state.selectedEventId) { mutableStateOf(NudgeTarget.START) }
    TimingEditPanel(
        startMs = event.start.millis,
        endMs = event.end.millis,
        durationMs = state.playback.durationMs,
        nudgeTarget = target,
        onNudgeTargetChange = { target = it },
        onSeek = viewModel::seekTo,
        onCommitStart = { ms -> viewModel.editEventTimes(event.id, SubTime.ofMillis(ms), event.end) },
        onCommitEnd = { ms -> viewModel.editEventTimes(event.id, event.start, SubTime.ofMillis(ms)) },
        onNudge = { delta ->
            when (target) {
                NudgeTarget.START -> viewModel.nudgeStart(delta)
                NudgeTarget.END -> viewModel.nudgeEnd(delta)
            }
        },
    )
}
```

> `mutableStateOf`/`remember`/`getValue`/`setValue` 已在 PreviewScreen.kt 顶部 import（现有代码用到了）。`SubTime` 已在 (a) 追加 import。

- [ ] **Step 2: 编译确认**

Run: `"d:/VS Code Project/aegisub-android/gradlew" -p "d:/VS Code Project/aegisub-android" --no-daemon :feature:preview:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（若有 import 冲突/未用，按编译提示修）

- [ ] **Step 3: 提交**

```bash
git -C "d:/VS Code Project/aegisub-android" add feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/PreviewScreen.kt
git -C "d:/VS Code Project/aegisub-android" commit -m "feat(preview): 集成时间编辑面板+时间轴条+撤销（选中行可改起止时间）"
```

---

## Task 9: 全量验证 + ROADMAP

**Files:**
- Modify: `ROADMAP.md`

- [ ] **Step 1: 全量单测**

Run: `"d:/VS Code Project/aegisub-android/gradlew" -p "d:/VS Code Project/aegisub-android" --no-daemon test`
Expected: BUILD SUCCESSFUL（domain 含 TimingConstraints；data 含 ProjectSession*；editor 11；preview 14）

- [ ] **Step 2: 构建 APK**

Run: `"d:/VS Code Project/aegisub-android/gradlew" -p "d:/VS Code Project/aegisub-android" --no-daemon :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 更新 ROADMAP**

`ROADMAP.md` 第 5 行「当前状态」改为：
```
## 当前状态：✅ Phase 0/1/2/3 + 文件 I/O + 图标 + 预览可写化（共享 ProjectSession）。预览屏可选中行并拖滑块/微调调起止时间，与编辑器共享撤销栈，改动实时同步两端 + 防抖落盘。下一步 Phase 4（音频波形/打轴辅助：拖拽手柄、波形提取）。
```

「Phase 3 增量交付」小节追加：
```
- [x] 预览可写化：共享 ProjectSession（编辑器+预览同源 script/撤销栈）+ 起止滑块/微调改时间 + 只读时间轴条 + 预览撤销
```

「Phase 4 · 音频工程」首项之前补一条（标记拖拽手柄为 Phase 4）：
```
- [ ] 时间轴条上的拖拽手柄（触屏精调，承接预览可写化）
```

- [ ] **Step 4: 提交**

```bash
git -C "d:/VS Code Project/aegisub-android" add ROADMAP.md
git -C "d:/VS Code Project/aegisub-android" commit -m "docs: ROADMAP 预览可写化完成"
```

---

## 验收

- 全模块单测绿：domain(+7 TimingConstraints) / data(+10 ProjectSession) / editor(11 不回归) / preview(14)
- `:app:assembleDebug` 出 APK
- 真机手测清单（交付给用户）：
  1. 编辑器改文本 → 进预览 → 时间显示一致（同源验证）
  2. 预览选中一行 → 起止滑块拖动 → 画面跟随 seek
  3. 松手 → 时间提交 → 返回编辑器看到新时间（共享验证）
  4. 微调 ±1帧/±0.1s/±1s → 时间按预期变化且不越界
  5. 预览点撤销 → 时间回到上一版
  6. 时间轴条：播放头移动、选中行高亮
