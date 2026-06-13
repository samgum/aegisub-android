# Roadmap — Aegisub Android

> 滚动更新。每完成一个 Phase 提交 Commit 并更新本文件与 README。

## 当前状态：✅ Phase 0 完成 — 核心域模块 `:core:domain`（77 单测全绿）。下一步 Phase 1（数据层 + 应用外壳）。

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

## Phase 1 · 数据层与应用外壳
- [ ] 多模块 Gradle 脚手架（`:app` `:core:data` `:core:domain`）
- [ ] Room：项目 / 历史版本 / 自动保存实体与 DAO
- [ ] DataStore：用户偏好（默认精度、布局、主题）
- [ ] Hilt DI 接线 + 应用入口
- [ ] 自动保存调度（Coroutines + WorkManager）

## Phase 2 · 字幕编辑 UI
- [ ] Compose 字幕列表（`LazyColumn` 虚拟列表，目标 10 万行流畅）
- [ ] 事件编辑器（时间 / 样式 / 文本 / 演员 / 特效）
- [ ] 响应式布局：手机竖屏、平板四区、折叠屏、DeX、Desktop Mode
- [ ] 外接键盘 / 鼠标快捷键

## Phase 3 · 视频预览与时间轴
- [ ] Media3 / ExoPlayer 集成 + 倍速播放
- [ ] 视频画面与字幕叠加预览
- [ ] 基础时间轴（拖拽调整起止点）

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
