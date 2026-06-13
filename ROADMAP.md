# Roadmap — Aegisub Android

> 滚动更新。每完成一个 Phase 提交 Commit 并更新本文件与 README。

## 当前状态：✅ Phase 0–8 核心闭环 + 桌面端功能复刻完成。Phase 8 复刻桌面 Aegisub 七大功能域：行级操作 / 排序 + 帧率转换 / 脚本属性 / 样式助手 / 翻译助手 / Karaoke 音节 / 可视化打字（{\pos}/{\fr} 拖拽）。功能覆盖比对见 [docs/COMPARISON.md](docs/COMPARISON.md)。下一阶段为第二轮复刻（多选批量、{\move}/{\fad}/{\clip}、Karaoke 交互计时、分辨率重采样、时间后处理等，详见比对文档「缺口」）。

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

## Phase 8 · 桌面端功能复刻 ✅（七大功能域）
**目标**：完整对比桌面 Aegisub，把核心编辑/打轴/样式/翻译/Karaoke/可视化打字能力复刻到 Android。

- [x] 行级操作（`LineOps`）：复制 / 删除 / 前插 / 后插 / 分割（按文本位置）/ 合并（拼接 / 留首）/ 上下移；行操作工具条嵌入编辑底栏与详情面板；新行 id = max+1 保证唯一
- [x] 排序（`SortLines`）：按起始/结束/样式/演员/效果/文本/层，升降序，稳定排序
- [x] 帧率转换（`FramerateConverter`）：按 to/from 帧率等比缩放全部时间（含 NTSC 分数帧率预设）
- [x] 脚本属性（`ScriptInfoOps` + `session.editInfo`）：编辑 Title / PlayResX/Y / WrapStyle / ScaledBorderAndShadow / Collisions / Timer，批量提交单撤销点
- [x] 样式助手（`StylingAssistantSheet`）：逐行浏览，点选样式色块快速分配并自动前进
- [x] 翻译助手（`TranslationAssistantSheet`）：原文存 Name、译文存 Text，逐行前进
- [x] Karaoke 音节生成（`KaraokeGenerator`）：按词/按字切分，时长均匀分配（余数前置），{\k}/{\kf} 标签
- [x] 可视化打字（`VisualTags` + `VisualTypesettingOverlay`）：视频画面拖拽设 {\pos}（脚本坐标系映射，松手提交），\fr 旋转滑块，清除 \pos
- [x] 工具箱统一入口：查找替换 / 时间偏移 / 排序 / 帧率转换 / 脚本属性 / 样式助手 / 翻译助手 / 卡拉OK / 删除空行 / 样式批量替换 / 样式管理器 / 历史版本

**验收**：`:core:domain:test` + `:feature:editor:testDebugUnitTest` 全绿；`assembleDebug` 出 APK。TDD 覆盖所有域纯函数。

## Phase 9 · 第二轮复刻（进行中）
依据 [docs/COMPARISON.md](docs/COMPARISON.md) 的缺口清单逐项补齐：

- [x] 字幕网格**多选**：批量复制/删除/块上下移/全选 + 平移/样式替换限定选中行（P9-1）
- [x] `{\move}` 两点动画（双手柄）+ `{\fad}` 淡入淡出字段（P9-2）
- [x] **时间后处理**：去重叠 / lead-in/out / 最小间隙（P9-4）
- [x] **分辨率重采样器**（Resolution Resampler）：改 PlayResX/Y 并按比例缩放 {\pos}/{\move} / 样式字号 / 边距 / 描边（P9-5）
- [x] 导出转换（ASS/SRT/WebVTT，新增 VttFormat）（P9-6）
- [ ] `{\clip}`/`{\iclip}` 矩形与矢量裁剪工具
- [ ] Karaoke **交互式计时**：拖拽音节边界调整逐音节时长（P9-3）
- [ ] 粘贴覆盖（Paste Over）
- [ ] 关键帧导入与吸附；书签

## Phase 10 · 第三轮：多语言 i18n + 关于页 ✅（壳层）
- [x] strings.xml（默认简体中文）+ values-en 双语资源
- [x] 语言偏好 system/zh/en，经 AppCompatDelegate 应用 per-app locale（minSdk 26 兼容）
- [x] 主屏 / 设置 / 关于 stringResource 化
- [x] 关于页（作者伤感咩吖 / 许可证 / 版本 / GitHub 链接）
- [ ] feature 模块（editor/preview）字符串全面 i18n 化（Phase 11 续）

## Phase 11 · 第四轮（规划）：补齐剩余缺口
- [ ] Karaoke 交互式计时（P9-3）
- [ ] `{\clip}`/`{\iclip}` 裁剪工具
- [ ] 粘贴覆盖 + 各类作者字段 / YCbCr Matrix 编辑
- [ ] 关键帧导入/检测 + 吸附；书签
- [ ] feature 模块全面 i18n 化

## Phase 12 · 第五轮（规划）：优化与适配
- [ ] 性能（libass JNI 渲染预备 / 持久向量深化）
- [ ] 折叠屏 / DeX / Desktop Mode 实测适配
- [ ] 第二轮桌面端完整比对 + 优化


