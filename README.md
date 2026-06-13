# Aegisub Android

> Android 平台上最接近桌面 Aegisub 体验的专业级字幕编辑器。

[![Status](https://img.shields.io/badge/status-Phase%202%20(Editor%20Loop)-blue)](docs/superpowers/specs/2026-06-13-phase2-editor-design.md)
[![License](https://img.shields.io/badge/license-Aegisub%20BSD-green)](LICENCE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)]()
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF)]()

本项目参考并部分移植自 [Aegisub](https://github.com/TypesettingTools/Aegisub)（跨平台高级字幕编辑器，BSD 许可），目标是把桌面端的核心字幕工程能力完整带到 Android：字幕/样式/标签/时间轴编辑、Karaoke、打轴辅助，并充分利用 Android 平台能力（响应式布局、Media3、外接键鼠、DeX）。

**作者 / 维护**：伤感咩吖

---

## ✨ 特性目标

- **多格式**：ASS / SSA / SRT / LRC / TXT，导入导出双向，自动识别与统一格式。
- **无损精度**：内部以微秒存储时间，ASS 厘秒、SRT/LRC 毫秒往返零精度损失。
- **LRC 全格式**：支持 `[mm:ss.xx]` `[mm:ss.xxx]` `[mm:ss:xx]` `[mm:ss:xxx]` 及混合格式文件。
- **专业编辑**：撤销重做、历史版本、自动保存、查找替换（正则）、批量操作。
- **多媒体**：视频预览（Media3/ExoPlayer）、音频波形/频谱、逐帧、关键帧、打轴辅助。
- **响应式**：手机 / 平板 / 横竖屏 / 折叠屏 / DeX / Android Desktop Mode / 外接键鼠。
- **超大文件**：虚拟列表 + 异步加载 + 结构共享撤销引擎，目标 10 万行可编辑。
- **远期预留**：Lua Automation、插件系统、libass JNI 渲染、AI 辅助字幕。

## 🏗️ 架构

多模块 Gradle，业务逻辑全部下沉到 `:core:*`，UI 层纯展示（黑盒架构）：

| 模块 | 职责 | 阶段 |
|---|---|---|
| `:core:domain` | 纯 Kotlin/JVM：时间/数据模型/tokenizer/格式编解码/撤销引擎 | **Phase 0** |
| `:core:data` | Room + DataStore：项目管理 / 自动保存 / 历史版本 | Phase 1 |
| `:core:media` | Media3 / ExoPlayer + 音频波形提取 | Phase 3-4 |
| `:core:ass-render` | libass JNI 桥（预留） | 远期 |
| `:feature:*` | Compose UI：字幕列表 / 编辑底栏 / 双栏布局（`:feature:editor` 已落地） | Phase 2 |
| `:app` | 应用入口 + 导航 + Hilt DI + 响应式布局 | Phase 1+ |

详见 [核心设计 Spec](docs/superpowers/specs/2026-06-13-aegisub-android-core-design.md) 与 [Roadmap](ROADMAP.md)。

## 🧱 技术栈

Kotlin · Jetpack Compose · Material3 · MVVM · Coroutines · Flow · Room · DataStore · Media3 / ExoPlayer · Hilt · `kotlinx.collections.immutable`

## 📐 平板布局

```
┌──────────┬───────────────────────┬──────────────┐
│ 字幕列表  │      视频预览          │  属性编辑器   │
│          ├───────────────────────┤              │
│          │      时间轴 / 波形      │              │
└──────────┴───────────────────────┴──────────────┘
```

## 📜 许可证

[BSD (Aegisub License)](LICENCE)。参考 / 移植自 Aegisub 的文件保留其版权声明；原创代码署名伤感咩吖。
