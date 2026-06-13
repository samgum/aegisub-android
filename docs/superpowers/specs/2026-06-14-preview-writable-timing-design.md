# 预览可写化 —— 时间轴拖拽调整起止时间 · 设计

> 日期：2026-06-14
> 阶段：Phase 3 时间轴深化的延续 / Phase 4 打轴辅助的前置
> 状态：已批准，待写实施计划
> 作者：伤感咩吖

## 1. 背景与目标

Phase 3 完成了视频预览（只读）：Media3 播放 + 字幕叠加 + 点行跳转 + 当前行高亮。本增量把预览从「只读查看」演进为「可编辑的打轴工具」——用户在预览屏直接调整选中字幕行的起止时间，边听边对，立即看到画面效果。

**目标**：

- 在预览屏选中一行字幕，通过滑块 + 微调按钮精确调整其 `start`/`end`
- 调整结果实时反映到画面（拖动即 seek）与时间轴条（空间参考）
- 撤销/重做可用，与编辑器屏共享同一编辑历史
- 防抖回写 Room（复用 Phase 2 的 800ms 机制）

## 2. 核心问题：两屏 script 副本不同步

当前 `EditorViewModel` 与 `PreviewViewModel` 各自 `load()`：从 `repo.getContent(projectId)` 读文本 → 解析 → 构建**独立的内存 `AssScript` 副本**，各自维护状态。预览一旦改时间并回写 repo，编辑器屏的内存副本仍是旧值（它的 ViewModel 在导航 back stack 里还活着，不会重新 load）。结果：

- 两屏显示的时间不一致
- 编辑器的撤销栈不知道预览做的修改，无法撤销
- 打轴（在预览调时间、回编辑器改文本/样式）这个核心场景不可用

**这是「预览可写化」必须先解决的架构地基。**

## 3. 方案选型

| 方案 | 做法 | 取舍 |
|---|---|---|
| A. 预览独立可写 | 预览自加撤销栈 + 回写 repo，编辑器返回时重载 | 改动最小，但返回编辑器丢失撤销栈、两屏不一致 |
| **B. 共享 ProjectSession** ⭐ | 编辑器的 script+撤销栈+防抖回写下沉到 `:core:data` 的 `ProjectSession`（Singleton manager 按 projectId 缓存），编辑器与预览注入同一实例 | 单一数据源、撤销栈共享、预览改时间编辑器立即可见；EditorViewModel 变薄为委托层 |
| C. 共享 ViewModel | 用 NavBackStackEntry 让两屏共用一个 ViewModel | Compose Navigation 跨目的地共享 ViewModel 违反作用域约定，hack |

**选 B**。对应 Aegisub 桌面版「编辑窗 + 视频窗同源」的体验。EditorViewModel 现有 `edit/commit/undo/redo/wireAutoSave` 已成熟，整体下沉后它成为纯委托层，更健康。

## 4. 架构：ProjectSessionManager + ProjectSession

放在 `:core:data`，包 `io.github.samgum.aegisub.data.session`。

```
ProjectSessionManager (@Singleton, @Inject)
  └─ open(projectId): ProjectSession   // 按 projectId 缓存，getOrPut 返回同一实例
       ├─ projectId: Long
       ├─ script: StateFlow<AssScript>          // 单一数据源（不可变 CoW）
       ├─ canUndo: StateFlow<Boolean>
       ├─ canRedo: StateFlow<Boolean>
       ├─ editEvent(eventId, transform: (AssEvent) -> AssEvent)  // 入撤销栈 + 刷新 + 防抖回写
       ├─ undo() / redo()
       └─ internal: SnapshotUndoStack<AssScript>，首次 open 从 repo 读解析
```

**契约要点**：

- `open(projectId)` 是 suspend（首次需读 repo）。内部 `getOrPut` 缓存，保证编辑器与预览拿到同一 `ProjectSession` 实例。
- `editEvent` 复刻 EditorViewModel 现有模式：对当前 script 的目标 event 应用 transform → `withEvents` 产出新不可变 script → `stack.commit` → 推 `script.value` → 触发内部防抖回写。
- 防抖回写：session 内部 `script` 流 `distinctUntilChanged().drop(1).debounce(800ms)` → `repo.updateContent(projectId, AssFormat.write(script), now)`。与 Phase 2 行为一致。
- event.id 稳定：加载后按行序 `mapIndexed { i, e -> e.copy(id = i.toLong()) }` 分配（与现有 Editor/Preview load 一致），保证两屏 id 对齐。
- 生命周期：session 不主动 close（@Singleton 进程级缓存，字幕工程内存占用小）。未来按需加 LRU/引用计数。

## 5. ViewModel 改造

### EditorViewModel（变薄为委托层）

- 注入 `ProjectSessionManager`
- `init { viewModelScope.launch { session = manager.open(projectId) } }`
- `state`：`session.script.map { EditorUiState.Loaded(it) }`（替代自持 `_state` + `stack`）
- `canUndo`/`canRedo`：委托 `session`
- `updateEventText/Times/Style`、`setEventLayer`、`undo`、`redo`：委托 `session.editEvent` / `session.undo`
- `exportContent`：读 `session.script.value`
- 删除内部 `stack`、`load()`、`wireAutoSave()`、`commit()`、`edit()`（逻辑下沉）

### PreviewViewModel（从只读 → 可写）

- 注入 `ProjectSessionManager`
- `init { ... launch { session = manager.open(projectId) } }`（与编辑器同一实例）
- `base` 的 `script` 来源从「自 load」改为 `session.script`
- 媒体挂载逻辑（`getMediaUri`/`setMedia`）保留（媒体不属于 script）
- 新增：
  - `editEventTimes(eventId, start: SubTime, end: SubTime)` → `session.editEvent(eventId) { it.copy(start = start, end = end) }`（内含约束，见 §7）
  - `undo()`/`redo()`、`canUndo`/`canRedo` 委托 session
- `state` 的 `currentEventId` 派生不变（仍 `ActiveSubtitleResolver.activeEvent`）
- 加载态：session 首次 open 完成前显示 Loading

## 6. 交互设计

触屏拖拽手柄精度差（手指挡视线、像素抖动）。MVP 用「点选 + 滑块 + 微调」。

### 6.1 选中与 seek 合一

点击事件行 = `seekToEvent`（跳到该行 start）**+** 标记为「编辑目标行」(`selectedEventId`)。零学习成本（当前点击本就是 seek），顺带选中。

### 6.2 时间编辑面板（编辑目标行下方）

- **起 / 止两个 `Slider`**：value 基于视频 `durationMs` 归一化位置。`onValueChange` 实时 `seekTo` 到对应时间点——边拖边看画面。`onValueChangeFinished` 提交到 session（拖拽中间态不入撤销栈，松手才提交，避免栈污染）。
- **微调按钮组**：面板顶部一个 segmented 选择「微调目标：起 ▼ / 止」（默认起），下方一组按钮作用于当前选中端：
  - `−1帧 / +1帧`（≈ ∓42ms，按 23.976fps）
  - `−0.1s / +0.1s`
  - `−1s / +1s`
  - 拖动某个 Slider 后自动把微调目标切到该端（焦点跟随），UI 高亮当前目标端。
- 每次微调立即提交 session（每次一个撤销点，符合微调的原子预期）。

### 6.3 只读时间轴条（视频下方一条带）

Compose Canvas 绘制：

- 全行 mini 块（按 start/end 在 duration 上的比例定位），选中行高亮
- 播放头竖线（随 `positionMs` 移动）
- **不做拖拽**（触屏手势/精度单独攻，留后续）

给空间参考，让用户知道选中行在整片里的位置与宽度。

### 6.4 撤销

预览顶栏加 undo 按钮（共享栈，与编辑器一致；redo 可放 overflow 或暂缓）。

## 7. 约束规则（在 session/VM 边界强制）

- `0 ≤ start < end` 恒成立（start 不小于 0，end 不小于 `start + 1ms`，最小持续 1ms 防零宽）
- `end ≤ durationMs` 仅在**有媒体时**强制（有视频才知道总长）；无媒体时不设 end 上限，仅受上一条约束
- 滑块/微调产出违反上述约束的值时 `coerceIn` 钳制（start 钳到 ≥0，end 钳到 `[start+1ms, durationMs]`）
- 微调按钮在越界方向禁用（如 start=0 时禁用 `−1帧/−0.1s/−1s`）

## 8. 数据流

```
用户拖起止滑块 / 点微调
        │
        ▼
PreviewViewModel.editEventTimes(id, start, end)
        │  (约束钳制)
        ▼
ProjectSession.editEvent(id) { copy(start,end) }
        ├─ stack.commit(newScript)        ← 入共享撤销栈
        ├─ script.value = newScript       ← StateFlow 推送
        │      ├─→ EditorViewModel.state 自动重组（显示新时间）
        │      └─→ PreviewViewModel.state 自动重组（时间轴条/面板更新）
        └─ (debounce 800ms) repo.updateContent(...)  ← 落盘
```

编辑器屏与预览屏订阅同一 `session.script`，任何一端编辑，两端 UI 自动一致。

## 9. 测试策略

| 层 | 内容 | 框架 |
|---|---|---|
| `:core:data` ProjectSession | editEvent 入栈/刷新；undo/redo；start<end 约束钳制；两处 `open` 拿同一实例；防抖回写（`runTest` + `advanceTime`）；event.id 加载后稳定分配 | JUnit4 |
| EditorViewModelTest | 改为断言 state/canUndo 委托 session（用 fake ProjectSessionManager 或直接构造 session）；edit/undo 行为透传 | JUnit4 |
| PreviewViewModelTest | 新增 `editEventTimes` → session 透传 + 约束；`selectedEventId` 派生；undo | JUnit4 |

测试替身：`ProjectSession` 设计为 interface，注入 fake；或 `ProjectSessionManager.open` 可用 fake repo 驱动真实 session。

## 10. 范围

**MVP（本增量）**：

1. ProjectSession + Manager（架构地基）
2. EditorViewModel 委托化重构
3. PreviewViewModel 可写化（editEventTimes + selectedEventId + undo）
4. 时间编辑面板（起止滑块 + 微调按钮，实时 seek）
5. 只读时间轴条（Canvas，播放头 + mini 块 + 选中高亮）
6. 预览顶栏 undo

**推迟（YAGNI）**：

- 时间轴条上的拖拽手柄（触屏手势/精度单独做）
- 多选批量改时间（Phase 6 效率工具）
- 音频波形/频谱（Phase 4 后续）
- redo 入口（undo 先行，redo 复用现有栈逻辑，UI 入口后续）

## 11. 风险与注意

- **EditorViewModel 重构回归**：下沉后须保证现有 11 个编辑器测试全绿（文本/时间/样式/层编辑、undo/redo、防抖保存语义）。这是重构的安全网。
- **session 并发 open**：编辑器与预览几乎同时 open 同一 projectId，`getOrPut` 须线程安全（`synchronized` 或 `CoroutineScope` 串行化）。
- **首次 open 时机**：session 首次 open 是 suspend（读 repo），ViewModel 须处理「session 尚未就绪」的 Loading 中间态，避免空指针。
- **媒体与 script 解耦**：媒体 URI 仍由 PreviewViewModel 直接管（不进 session），session 只管 script。
