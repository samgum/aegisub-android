# Phase 2 — 字幕编辑器 UI 设计 Spec

- **日期**：2026-06-13
- **项目**：Aegisub Android
- **依赖**：Phase 0（`:core:domain`）+ Phase 1（`:app` / `:core:data`）已完成
- **状态**：设计已确认，待 spec 审阅

---

## 1. 目标

构建字幕编辑器主界面：打开项目 → 解析为 `AssScript` → 渲染事件列表 → 编辑事件（文本/时间/样式/层）→ 撤销重做 → 防抖自动保存。响应式适配手机与平板。

**Phase 2 把 Phase 0 的撤销引擎与 Phase 1 的 Room 持久化真正接到 UI**，形成可用的编辑闭环。

## 2. 非目标（本阶段不做）

- 视频预览 / 时间轴 / 波形 → Phase 3、4（依赖 Media3）。
- 查找替换 / 正则 / 批量操作 → Phase 6。
- 10 万行分页 / 持久向量 → Phase 7（本阶段内存全量）。
- 平板四区中的"视频/时间轴"两栏 → Phase 3/4。Phase 2 的 expanded 仅列表+详情双栏。

## 3. 关键架构决策（已确认）

| 决策 | 选择 | 理由 |
|---|---|---|
| 响应式策略 | **移动优先自适应**（WindowSizeClass） | 同一套组件，compact 手机堆叠、expanded 平板双栏；覆盖面广、迭代快 |
| 事件数据源 | **内存全量 `AssScript` + `LazyColumn` 虚拟化渲染** | 贴合 Aegisub 模型，正常文件流畅；10 万行分页明确留 Phase 7 |
| 编辑状态 | `EditorViewModel` 持 `SnapshotUndoStack<AssScript>` | 复用 Phase 0 CoW 撤销引擎，零额外设计 |
| 保存 | 编辑后**防抖**写回 Room | 同时实现自动保存雏形 |

## 4. 模块结构

新增 **`:feature:editor`**（Android library + Compose）：

```
feature/editor/
├── build.gradle.kts                 # AGP library + compose + hilt + 依赖 :core:domain :core:data
└── src/main/kotlin/io/github/samgum/aegisub/feature/editor/
    ├── navigation/EditorNavigation.kt   # 路由常量 editor/{projectId} + composable 注册
    ├── EditorViewModel.kt               # @HiltViewModel：加载/UndoStack/编辑/保存
    ├── EditorScreen.kt                  # 顶层：WindowSizeClass 分派 compact/expanded
    ├── EditorUiState.kt                 sealed: Loading/Loaded/Error
    ├── compact/EventListScreen.kt       # compact 列表（LazyColumn）
    ├── compact/EventEditSheet.kt        # compact 事件编辑底部表单
    ├── expanded/EditorTwoPane.kt        # expanded 双栏：列表 | 详情
    └── components/EventRow.kt           # 共用事件行
```

`:app` 职责收敛为：`AegisubApplication` + `MainActivity`（NavHost）+ 主题 + 导航图（注册 `home` 与 `editor`）。

## 5. 数据流

```
打开项目
  → HomeScreen 点行 → navigate("editor/$projectId")
  → EditorViewModel.init(repo, projectId)
  → repo.observeProject / 取 content
  → FormatRegistry.detect(content).read(content)  → AssScript
  → SnapshotUndoStack(initial = script)；暴露 current 为 StateFlow

编辑
  → UI 调 vm.updateEventText(id, newText) 等
  → vm 计算 newScript = current.copy/events.map{...}
  → stack.commit(newScript, "edit text")
  → current StateFlow 推送 → UI 刷新
  → 防抖 800ms → repo.updateContent(id, AssFormat.write(current), now)

撤销/重做
  → vm.undo()/redo() → stack.undo()/redo() → current 更新 → UI 刷新
```

## 6. EditorViewModel 接口

```kotlin
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repo: ProjectRepository,
    savedStateHandle: SavedStateHandle,   // 取 projectId
) : ViewModel() {

    val state: StateFlow<EditorUiState>        // Loading/Loaded(script)/Error
    val canUndo: StateFlow<Boolean>
    val canRedo: StateFlow<Boolean>

    fun updateEventText(id: Long, text: String)
    fun updateEventTimes(id: Long, start: SubTime, end: SubTime)
    fun updateEventStyle(id: Long, style: String)
    fun setEventLayer(id: Long, layer: Int)
    fun undo(); fun redo()
}
```

> `SnapshotUndoStack` 是 `UndoStack<AssScript>`；`current` 即当前脚本。canUndo/canRedo 由栈暴露。
> 保存：`viewModelScope` 内对 `state` 做 `debounce(800ms).distinctUntilChanged { 非首版本 }` → 写回 Room。首版本（加载态）不触发保存。

## 7. 响应式布局

- `WindowSizeClass`（`currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass`）：
  - **Compact**（< 600dp，手机）：`EventListScreen`（全屏列表），点行展开 `EventEditSheet`（ModalBottomSheet）编辑。
  - **Expanded / Medium**（≥ 600dp，平板/折叠展开/DeX）：`EditorTwoPane`（列表 | 详情 双栏，详情常驻）。
- 组件复用：`EventRow`、事件编辑表单字段在两种布局共用，仅排列不同。

## 8. 编辑粒度与 AssScript 不可变编辑

`AssScript` 不可变（Phase 0）。编辑某事件：
```kotlin
fun updateEventText(id: Long, text: String) {
    val current = stack.current
    val newEvents = current.events.map { if (it.id == id) it.copy(text = text) else it }
    commit(current.copy(events = newEvents.toPersistentList()), "edit text")
}
```
> 注：`ImmutableList.map { ... }.toPersistentList()` 每次 O(n)。对成百上千行无压力；10 万行优化（持久向量 + 定位替换）留 Phase 7，接口不变。

## 9. 自动保存

- 防抖：编辑后 800ms 无新提交则写回。
- 写回内容 = `AssFormat.write(current)`（保持 ASS 格式；跨格式导入的项目统一以 ASS 内部表示编辑，导出时按用户精度选项转——Phase 0 已支持）。
- Room `updateContent` 更新 content + updatedAt。
- 失败（如 IO）降级为内存态 + 日志，不阻断编辑（后续 WorkManager 持久化重试 → Phase 1 自动保存完善）。

## 10. 测试策略

- **EditorViewModel 单测**（纯 JVM，假 `ProjectRepository`）：
  - 加载 → 解析 → state=Loaded，事件数正确。
  - updateEventText → current.events 对应事件文本变 + canUndo=true。
  - undo → 恢复原文本；redo → 重做。
  - 防抖保存触发 → repo.updateContent 被调（用 `runTest` + advanceTimeByIdle）。
- **事件编辑映射** 单测：updateEventTimes/Style/Layer 各自正确改字段。
- UI 暂以编译 + 手动验证为主（Compose UI 测试留待后续）。

## 11. 子里程碑

| 子步 | 内容 | 验收 |
|---|---|---|
| **2A** | `:feature:editor` 模块 + 导航 + EditorScreen 骨架：打开项目解析为 AssScript，LazyColumn 渲染事件列表，返回导航。UndoStack 装载（只读） | 能打开项目看到事件列表，编译通过，VM 加载单测过 |
| **2B** | 事件编辑（文本/时间/样式/层）+ 撤销重做 + 防抖自动保存 | 编辑→列表刷新→撤销恢复→保存回 Room；VM 单测覆盖编辑/撤销/保存 |
| **2C** | WindowSizeClass 响应式：compact 堆叠 / expanded 双栏 | 手机单列、平板双栏同组件；旋转/折叠切换布局 |

## 12. 许可证与署名

延续项目策略：参考/移植 Aegisub 处保留 BSD 版权（项目级 LICENCE 已覆盖）；原创代码署名 **伤感咩吖**。

## 13. 安全

纯离线编辑器，不联网；Phase 2 不引入网络/后端，CLAUDE.md 后端安全规则不触发。`.gitignore` 已屏蔽 keystore/.env/local.properties。
