# Roadmap — Aegisub Android

> 滚动更新。每完成一个 Phase 提交 Commit 并更新本文件与 README。

## 当前状态：✅ Phase 0/1/2/3 + 文件 I/O + 图标 + 预览可写化（共享 ProjectSession）。预览屏可选中行并拖滑块/微调调起止时间，与编辑器共享撤销栈，改动实时同步两端 + 防抖落盘。剩余 Phase 1：DataStore 设置；下一步 Phase 4（音频波形/打轴辅助：拖拽手柄、波形提取）。

---

## Phase 0 · 核心域模块（地基）✅
**目标**：用 TDD 建立纯 Kotlin/JVM、零 Android 依赖的字幕工程核心，全部可单测。

- [x] `SubTime`：微秒存储 + ASS/SRT/LRC(4 格式) 格式化与解析 + 范围限制 + 容错（SMPTE 延后至 Phase 3，依赖帧率）
- [x] 数据模型：`AssScript / AssEvent / AssStyle / AssInfo / AssColor / Margins`（不可变）
- [x] 对话体 tokenizer（参考 Aegisub dialogue_parser）+ 块模型 + 绘图标记
- [x] 格式编解码：ASS / SSA / SRT / TXT / **LRC（全新，4 种时间格式）**
- [x] 自动检测注册表 + 导出精度选项（厘秒 / 毫秒 / 自动）
- [x] 撤销引擎：`UndoStack` 接口 + 不可变 CoW 快照实现
- [x] 单测：77 用例全绿（往返等价、4 LRC 格式、混合检测、边界、ASS↔SSA 对齐）

**验收**：`:core:domain` 纯 JVM 测试全绿（`./gradlew :core:domain:test` → BUILD SUCCESSFUL，77 passed）。

## Phase 1 · 数据层与应用外壳（核心完成，可演示）
- [x] 多模块 Gradle 脚手架（`:app` `:core:data` `:core:domain`，AGP 8.7.3 + Kotlin 2.1.0 + compileSdk 35）
- [x] Room：项目实体 / DAO / Database + `RoomProjectRepository`（含假 DAO 单测）
- [x] Hilt DI 接线 + 应用入口（`@HiltAndroidApp` + `DataModule`）
- [x] 项目列表 UI（LazyColumn + 新建 FAB，`assembleDebug` 出 9.7M APK）
- [ ] DataStore：用户偏好（默认精度、布局、主题）— 待办
- [x] 编辑防抖自动保存（Coroutines Flow debounce，Phase 2 落地）；WorkManager 后台保命保存 — 待办
- [ ] 历史版本表 — 待办（Phase 7 深化）

## Phase 2 · 字幕编辑 UI ✅（核心闭环）
- [x] Compose 字幕列表（`LazyColumn` 虚拟列表 + 稳定 key，目标 10 万行流畅）
- [x] 事件编辑器：文本 / 起止时间 / 样式 / 层（实时回写 + 容错解析）；演员/特效字段留待样式深化
- [x] 撤销 / 重做（CoW 快照栈）+ 防抖自动保存（800ms 回写 Room，跳过首版本）
- [x] 响应式布局：compact（列表 + ModalBottomSheet 编辑底栏）/ expanded（双栏列表|详情常驻），按屏宽断点分派
- [ ] 折叠屏 / DeX / Desktop Mode 实测适配 — 待办
- [ ] 外接键盘 / 鼠标快捷键 — 待办

## Phase 3 · 视频预览与时间轴 ✅
- [x] Media3 / ExoPlayer 集成 + 倍速播放（封装 VideoPlayer 接口，可注入测试替身）
- [x] 视频画面与字幕叠加预览（简化渲染：去标签纯文本 + 样式，Compose 自绘真描边 + 对齐/边距）
- [x] 基础时间轴：播放/暂停/seek/倍速 + 点行跳转 + 当前行高亮（拖拽改时间留 Phase 4）
- [x] 起止滑块/微调改时间（预览可写化，共享 session）；[ ] 拖拽手柄精调 — 待 Phase 4

### 增量交付（Phase 3 之后）
- [x] 应用图标复用 Aegisub 原版（icon-mk2）
- [x] 文件导入/导出（SAF OpenDocument / CreateDocument；复用域编解码；SubtitleImport 解析）
- [x] 叠加层性能优化（按活动事件切换重组，缓解低端机卡顿）
- [x] 预览可写化：共享 ProjectSession（编辑器+预览同源 script/撤销栈）+ 起止滑块/微调改时间 + 只读时间轴条 + 预览撤销

## Phase 4 · 音频工程
- [ ] 音频波形提取与渲染
- [ ] 频谱显示
- [ ] 逐帧预览 + 关键帧辅助
- [ ] 打轴辅助工具

## Phase 5 · 样式与 Karaoke
- [ ] 样式编辑器（颜色 / 字体 / 边框 / 对齐 / 边距 / 编码）
- [ ] ASS 标签编辑 UI（覆盖标签可视化）
- [ ] Karaoke 音节切分与编辑

## Phase 6 · 效率工具
- [ ] 查找替换（含正则替换）
- [ ] 批量操作（时间偏移 / 样式批量替换 / 删除空行）
- [ ] 打轴辅助完整化

## Phase 7 · 性能与可靠性硬化
- [ ] 持久向量（bit-partitioned trie）支撑 10 万行级编辑
- [ ] 分页 / 异步加载策略
- [ ] 历史版本恢复 UI
- [ ] 撤销栈内存治理

## 远期预留
- [ ] Lua Automation 引擎
- [ ] 插件系统
- [ ] libass JNI 渲染（精确 ASS 渲染）
- [ ] AI 辅助字幕（听写 / 翻译 / 时间轴对齐）
