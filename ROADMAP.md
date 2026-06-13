# Roadmap — Aegisub Android

> 滚动更新。每完成一个 Phase 提交 Commit 并更新本文件与 README。

## 当前状态：✅ Phase 0–7 核心闭环全部完成。首选项（主题/导出精度/布局）持久化；编辑器+预览共享 session；打轴（WASD 键盘 + 拖拽手柄 + 逐帧）；音频波形/频谱双视图；样式编辑器（颜色/字体/描边/对齐/边距/编码全字段）；批量工具（查找替换/时间偏移/删除空行/样式批量替换）；历史版本恢复；撤销栈有界内存治理。剩余仅远期预留（Lua/libass JNI/AI）。

---

## Phase 0 · 核心域模块（地基）✅
**目标**：用 TDD 建立纯 Kotlin/JVM、零 Android 依赖的字幕工程核心，全部可单测。

- [x] `SubTime`：微秒存储 + ASS/SRT/LRC(4 格式) 格式化与解析 + 范围限制 + 容错（SMPTE 延后至 Phase 3，依赖帧率）
- [x] 数据模型：`AssScript / AssEvent / AssStyle / AssInfo / AssColor / Margins`（不可变）
- [x] 对话体 tokenizer（参考 Aegisub dialogue_parser）+ 块模型 + 绘图标记
- [x] 格式编解码：ASS / SSA / SRT / TXT / **LRC（全新，4 种时间格式）**
- [x] 自动检测注册表 + 导出精度选项（厘秒 / 毫秒 / 自动）——精度真正穿透到写入
- [x] 撤销引擎：`UndoStack` 接口 + 不可变 CoW 快照实现（有界，limit 丢最旧）
- [x] 单测：往返等价、4 LRC 格式、混合检测、边界、ASS↔SSA 对齐

**验收**：`:core:domain` 纯 JVM 测试全绿（`./gradlew :core:domain:test` → BUILD SUCCESSFUL）。

## Phase 1 · 数据层与应用外壳（核心完成）✅
- [x] 多模块 Gradle 脚手架（`:app` `:core:data` `:core:domain`，AGP 8.7.3 + Kotlin 2.1.0 + compileSdk 35）
- [x] Room：项目实体 / DAO / Database（v3：projects + snapshots 表）+ `RoomProjectRepository` + `RoomSnapshotRepository`（含假 DAO 单测）
- [x] Hilt DI 接线 + 应用入口（`@HiltAndroidApp` + `DataModule`）
- [x] 项目列表 UI（LazyColumn + 新建 FAB，`assembleDebug` 出 APK）
- [x] DataStore：用户偏好持久化（主题：跟随系统/浅/深；**导出精度：厘秒/毫秒/自动；布局：自动/紧凑/双栏**）——接线到导出与编辑器断点
- [x] 编辑防抖自动保存（Coroutines Flow debounce，800ms 回写 Room，跳过首版本）
- [x] 历史版本表（snapshots，外键级联）+ 恢复 UI — Phase 7 落地
- [ ] WorkManager 后台保命保存 — 远期

## Phase 2 · 字幕编辑 UI ✅（核心闭环）
- [x] Compose 字幕列表（`LazyColumn` 虚拟列表 + 稳定 key，目标 10 万行流畅）
- [x] 事件编辑器：文本 / 起止时间 / 样式 / 层（实时回写 + 容错解析）
- [x] 撤销 / 重做（CoW 快照栈，有界）+ 防抖自动保存
- [x] 响应式布局：compact（列表 + 底栏）/ expanded（双栏列表|详情常驻），**按用户布局偏好或屏宽断点分派**
- [x] 外接键盘快捷键（预览：空格/左右 seek/Ctrl+Z/Shift+左右逐帧/WASD 打轴）
- [ ] 折叠屏 / DeX / Desktop Mode 实测适配 — 远期

## Phase 3 · 视频预览与时间轴 ✅
- [x] Media3 / ExoPlayer 集成 + 倍速播放（封装 VideoPlayer 接口，可注入测试替身）
- [x] 视频画面与字幕叠加预览（简化渲染：去标签纯文本 + 样式，Compose 自绘真描边 + 对齐/边距）
- [x] 基础时间轴：播放/暂停/seek/倍速 + 点行跳转 + 当前行高亮
- [x] 起止滑块/微调改时间（预览可写化，共享 session）；拖拽手柄（拖行块平移/边缘改时长）

### 增量交付（Phase 3 之后）
- [x] 应用图标复用 Aegisub 原版（icon-mk2）
- [x] 文件导入/导出（SAF；复用域编解码；SubtitleImport 解析）
- [x] 叠加层性能优化（按活动事件切换重组，缓解低端机卡顿）
- [x] 预览可写化：共享 ProjectSession + 起止滑块/微调 + 拖拽手柄 + 预览撤销

## Phase 4 · 音频工程 ✅
- [x] 音频波形提取与渲染（MediaExtractor+MediaCodec 解码 → 降采样 → Canvas 柱状 + 播放头高亮）
- [x] **频谱热图显示**（radix-2 FFT + Hann 窗 STFT → 时间×频率能量矩阵 → ARGB 热图，Bitmap 缓存；波形/频谱一键切换）
- [x] **逐帧预览 + FPS 检测**（videoFormat 读帧率；逐帧按钮 + Shift+左/右 键盘；帧级起止微调）
- [x] 打轴辅助工具（WASD 踩点 + 拖拽手柄 + 逐帧微调）

## Phase 5 · 样式与 Karaoke ✅（样式编辑器完成）
- [x] 样式编辑器（名称/字体/字号、四色 RGBA 滑块、粗斜下划删除线、九宫对齐、L/R/V 边距、描边/阴影宽、边框类型、缩放/间距/旋转、编码）—— session.editStyles 单撤销点
- [x] ASS 标签编辑：覆盖标签可视化（dialogue tokenizer + strippedText）— 渲染层去标签纯文本 + 样式
- [ ] Karaoke 音节切分与编辑（{\k} 标签可视化编辑）— 远期

## Phase 6 · 效率工具 ✅
- [x] 查找替换（含正则替换，一次撤销点）
- [x] 批量操作（时间偏移 / 样式批量替换 / 删除空行）—— 域纯函数 + session.editEvents，工具箱 FAB 统一入口
- [x] 打轴辅助完整化（逐帧 + 帧级微调 + 拖拽 + 键盘）

## Phase 7 · 性能与可靠性硬化 ✅（核心完成）
- [x] 撤销栈内存治理（SnapshotUndoStack 有界环形缓冲，limit 丢最旧，结构共享每次提交仅 O(差分)）
- [x] 历史版本恢复 UI（snapshots 表 + SnapshotRepository + HistorySheet 保存/恢复/删除，恢复为新撤销点）
- [x] 列表虚拟化（LazyColumn 稳定 key）
- [ ] 持久向量（bit-partitioned trie）支撑 10 万行级编辑 — 远期深化（当前 PersistentList 结构共享已支撑常规规模）
- [ ] 分页 / 异步加载策略 — 远期（当前虚拟列表足够）

## 远期预留
- [ ] Lua Automation 引擎
- [ ] 插件系统
- [ ] libass JNI 渲染（精确 ASS 渲染）
- [ ] AI 辅助字幕（听写 / 翻译 / 时间轴对齐）
