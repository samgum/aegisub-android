# Phase 2 — 字幕编辑器 UI 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development / executing-plans。Steps 用 `- [ ]` 跟踪。**并行组已标注 [PARALLEL]**——可同时派发 worktree 隔离子代理。

**Goal:** 字幕编辑器主界面：打开项目→解析 AssScript→事件列表→编辑（文本/时间/样式/层）→撤销重做→防抖自动保存，响应式适配手机/平板。

**Architecture:** 见 [Phase 2 spec](../specs/2026-06-13-phase2-editor-design.md)。`EditorViewModel` 持 `SnapshotUndoStack<AssScript>`；`:feature:editor` Android lib + Compose + Hilt。

**Tech additions:** navigation-compose 2.8.x、window-size-class (material3 自带)、lifecycle-viewmodel-compose / savedstate。

**并行策略：** 模块搭建与 VM（依赖链）顺序做；UI 组件文件（EventRow / EventEditSheet / EditorTwoPane）互相独立，标 [PARALLEL] 用 worktree agent 并行写，最后统一 `:feature:editor:assembleDebug`。

---

## Task P0：`:feature:editor` 模块骨架（顺序）

**Files:** `settings.gradle.kts`(改)、`feature/editor/build.gradle.kts`、`feature/editor/src/main/AndroidManifest.xml`、`app/build.gradle.kts`(加依赖)。

- [ ] settings 加 `include(":feature:editor")`
- [ ] `feature/editor/build.gradle.kts`：AGP library + kotlin.android + compose + hilt + ksp；依赖 `:core:domain` `:core:data`；compose-bom、navigation-compose、lifecycle-viewmodel-compose、hilt-navigation-compose、material3（window-size-class 随 material3）
- [ ] 空 `AndroidManifest.xml`（library）
- [ ] app 加 `implementation(project(":feature:editor"))`
- [ ] `./gradlew :feature:editor:assembleDebug` 编译通过

---

## Task 2A.1：EditorViewModel + 加载/UndoStack（顺序，VM 核心）

**Files:** `feature/editor/.../EditorUiState.kt`、`EditorViewModel.kt`；测试 `feature/editor/src/test/.../EditorViewModelTest.kt`。

```kotlin
sealed interface EditorUiState {
    data object Loading : EditorUiState
    data class Loaded(val script: AssScript) : EditorUiState
    data class Error(val message: String) : EditorUiState
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repo: ProjectRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val projectId: Long = checkNotNull(savedStateHandle["projectId"]).toString().toLong()
    private val stack = SnapshotUndoStack<AssScript>(AssScript.default())  // 占位，加载后替换
    private val _state = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val state: StateFlow<EditorUiState> = _state
    val canUndo: StateFlow<Boolean> = /* stack.canUndo 转 StateFlow */
    val canRedo: StateFlow<Boolean> = /* 同上 */

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val project = repo.observeProject(projectId).first()   // 或新增 suspend getContent
            val content = repo ... // 取 content（见下注）
            val script = try { FormatRegistry.detect(content)?.read(content) ?: AssScript.default() }
                          catch (e: Exception) { _state.value = ErrorUi(e); return@launch }
            stack.replaceInitial(script)   // 需 UndoStack 加 reset 方法，或重构 stack 持有后赋值
            _state.value = EditorUiState.Loaded(script)
            wireAutoSave()
        }
    }
    // ... 编辑/撤销见 2B
}
```

**注：** 需 `ProjectRepository` 暴露 `suspend fun getContent(id): String`（Project 模型无 content，需补）。**子任务：在 :core:data 加 `suspend fun getContent(id): String`（DAO getById 取 content）。**

测试（假 repo）：
- 加载 ASS content → state=Loaded，events 数对。
- 加载损坏内容 → state=Error 不崩。
- canUndo 初始 false。

---

## Task 2A.2 [PARALLEL]：EventRow + EventListScreen（compact 列表）

**Files:** `feature/editor/.../components/EventRow.kt`、`compact/EventListScreen.kt`。

- `EventRow(event, onClick)`：显示起止时间（SubTime.toAssString）、style、文本片段（strippedText 截断）。
- `EventListScreen(events, onEventClick, onBack)`：TopAppBar + `LazyColumn(items(events, key={it.id}))`。

---

## Task 2A.3 [PARALLEL]：EditorScreen + 导航

**Files:** `feature/editor/.../EditorScreen.kt`、`navigation/EditorNavigation.kt`；`app` NavHost 注册。

- `EditorNavigation`：`const ROUTE = "editor/{projectId}"`、`fun editorRoute(id) = "editor/$id"`、`composable` 注册 lambda。
- `EditorScreen(vm)`：依 state 分派 Loading/Error/Loaded（2A 仅 compact：Loaded→EventListScreen）。
- `app/MainActivity`：NavHost(startDestination="home")，注册 home（HomeScreen）与 editor（EditorScreen）。HomeScreen 行点击 `onOpen = { navController.navigate(editorRoute(it.id)) }`。

---

## Task 2A 验收
- [ ] `:app:assembleDebug` 通过；HomeScreen 点项目进 EditorScreen 显示事件列表；返回可用。
- [ ] EditorViewModelTest 加载/错误/canUndo 通过。

---

## Task 2B.1：编辑操作 + 撤销重做（VM）

**Files:** `EditorViewModel.kt`(加方法)；测试补。

```kotlin
private fun commit(newScript: AssScript, desc: String) {
    stack.commit(newScript, desc)
    _state.value = EditorUiState.Loaded(stack.current)   // 触发防抖保存
}
fun updateEventText(id, text) = edit(id) { it.copy(text = text) }
fun updateEventTimes(id, s, e) = edit(id) { it.copy(start = s, end = e) }
fun updateEventStyle(id, style) = edit(id) { it.copy(style = style) }
fun setEventLayer(id, layer) = edit(id) { it.copy(layer = layer) }
private fun edit(id: Long, fn: (AssEvent)->AssEvent) {
    val cur = (state.value as? Loaded)?.script ?: return
    val newEvents = cur.events.map { if (it.id == id) fn(it) else it }.toPersistentList()
    commit(cur.copy(events = newEvents), "edit")
}
fun undo() { stack.undo()?.let { _state.value = Loaded(it) } }
fun redo() { stack.redo()?.let { _state.value = Loaded(it) } }
```

测试：每 edit 改对应字段 + canUndo=true；undo 恢复；redo 重做。

## Task 2B.2：防抖自动保存（VM）

```kotlin
private fun wireAutoSave() {
    viewModelScope.launch {
        state.filterIsInstance<Loaded>().map { it.script }
            .debounce(800).distinctUntilChanged()
            .drop(1)   // 跳过首版本
            .collect { repo.updateContent(projectId, AssFormat.write(it), System.currentTimeMillis()) }
    }
}
```
测试（runTest + advanceTime）：编辑→800ms 后 updateContent 被调；首版本不保存。

## Task 2B.3 [PARALLEL]：EventEditSheet（compact）+ 撤销重做按钮

**Files:** `feature/editor/.../compact/EventEditSheet.kt`、`components/EditorActions.kt`。
- ModalBottomSheet：文本框、起止时间输入、样式下拉、层数；保存调 vm.update*。
- EditorActions：Undo/Redo 图标按钮（依 canUndo/canRedo 启用）。
- EventListScreen 顶栏挂 EditorActions；点行展开 Sheet。

## Task 2B 验收
- [ ] 编辑→列表刷新→撤销恢复→重做；800ms 后保存回 Room。
- [ ] VM 编辑/撤销/保存单测全绿。

---

## Task 2C [PARALLEL]：WindowSizeClass 响应式

**Files:** `feature/editor/.../expanded/EditorTwoPane.kt`、`EditorScreen.kt`(改分派)。

- `EditorScreen`：`val wc = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass`；Compact→EventListScreen(+Sheet)；Medium/Expanded→EditorTwoPane(列表 | 详情常驻)。
- `EditorTwoPane`：两栏，共用 EventRow + 编辑字段组件。

## Task 2C 验收
- [ ] 手机单列、平板双栏；旋转/折叠切换布局，组件复用。

---

## 完成定义
- [ ] `:app:assembleDebug` 出 APK；编辑闭环可用（列表→编辑→撤销→自动保存）。
- [ ] EditorViewModelTest（加载/错误/编辑/撤销/保存）全绿。
- [ ] compact 与 expanded 布局都编译。
- [ ] ROADMAP Phase 2 勾选；推送；合并 main。

## 并行执行约定
- [PARALLEL] 任务：用 `Agent(isolation="worktree")` 同时派发，各自写文件 + 自检（不在 worktree 内跑 gradle 以免锁），回报后我合并分支统一构建。
- 非 [PARALLEL]：内联或单代理顺序。
- 统一在 `feat/phase2-editor` 上构建验证。
