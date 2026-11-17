# Aegisub SGMY Android

[English](README.md) | **简体中文**

[![CI](https://github.com/samgum/aegisub-android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/samgum/aegisub-android/actions/workflows/android-ci.yml)
[![Version](https://img.shields.io/badge/version-2.0.0--sgmy-2563EB)]()
[![License](https://img.shields.io/badge/license-BSD%203--Clause-16A34A)](LICENCE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)]()
[![Adaptive](https://img.shields.io/badge/adaptive-手机%20%7C%20平板%20%7C%20折叠屏%20%7C%20DeX-8B5CF6)]()

全新架构重构的 Android 专业级字幕工作站——**1:1 完整复刻桌面定制版 [samgum/Aegisub (Aegisub SGMY)](https://github.com/samgum/Aegisub)** 的全部独家工作流与经典桌面端打轴/特效打字能力，并针对 **手机（竖屏单手 / 横屏分栏）、平板 / DeX 桌面窗口、折叠屏（半折悬停 Tabletop / 书本双页 Book / 完全展开）** 提供原生级自适应工程界面。

**作者 / 维护**：伤感咩吖 (`samgum`)

---

## ✨ 核心亮点

### 1. 完整复刻 `samgum/Aegisub` (SGMY) 全部独家功能
- **修复常见错误 (`SGMY工具 → 修复常见错误`)**：对齐 Subtitle Edit / 桌面 SGMY 的一键批量检查修复器，每项可独立勾选：
  - 修复重叠显示时间（按起始时间稳定排序并跳过注释行，自动截断前句结束时间至下句开始）
  - 修复行间过短间隔（可配置最小间隔毫秒）
  - 延长过短时长 / 截断过长时长
  - 去除行尾空白（含 ASCII 空白与全角空格 `U+3000`）
  - 删除空行或仅含 ASS 不可见空白（`\h`、`\N`、空格）的行（智能保留 `template*` 模板与 `\p1` 矢量绘图行）
  - 时间、文本、增删行分阶段作为**独立撤销步**提交，随时精准回退。
- **音乐滚动歌词生成器 (`SGMY工具 → 音乐滚动歌词生成`)**：
  - 一键生成 Apple Music / 音乐播放器风格的多行平滑滚动歌词字幕
  - 支持单语歌词与**双语自动配对**（同时间轴或相邻译文行自动合并为主副双行）
  - 内置 **7 档画幅预设**（跟随脚本 `PlayRes`、`4K 3840×2160`、`1080p`、`720p`、`竖屏 1080×1920`、`竖屏 720×1280`、自定义）
  - 自动创建 `ScrollCurrent` / `ScrollDim` 样式并生成三次缓出 `\move` + `\t(\alpha\1c\fscx\fscy)` 过渡动画。
- **中日韩文本工具箱（CJK Text Tools）**：
  - **简繁中文互转**：基于 Android 原生 ICU `Transliterator` + 高频多音词组保护表，严格跳过 `{...}` ASS 标签块，支持按样式与多选范围过滤。
  - **字幕文本清理**：全角逗号转空格、句中/句末中文句号智能处理、弯引号规范化、合并连续空格，零误伤覆盖标签。
  - **配对标点符号检查**：栈式校验中英文圆括号、方括号、书名号 `《》〈〉`、直角引号 `「」『』` 及单双引号，点击问题行一键定位。
  - **日语汉字假名注音（Furigana）**：自动提取全片汉字词条并生成 `@行号#序号:汉字=读音` 与全局映射模板，支持平假名/片假名切换、上方/下方注音，以及 `{*sgmy-furigana}` 标记的幂等反复修改。
- **时间缝合器 & 同样式时间重叠检查**：
  - **时间缝合器（Stitch Timings）**：支持「以前句结束为基准」或「以下句开始为基准」，按毫秒或视频帧边界消除相邻字幕闪烁微隙。
  - **同样式重叠检查**：按样式统计完全同轴、同起点、同终点与交叉时间重叠，点击直接跳转到冲突行。
- **增强型 LRC 与 TTML 歌词/流媒体格式**：
  - **增强型 LRC**：完整支持 `[offset:+/-ms]`、`<mm:ss.xx>` 逐字/逐词卡拉OK解析为 `{\kf}`、Apple Music 行尾结束时间标记、无时间轴纯词稿兜底导入，以及卡拉OK双向写回增强 LRC。
  - **TTML / DFXP (`.ttml`)**：支持时钟与偏移时间码、`<span begin/end>` 逐词卡拉OK转 `{\kf}`，以及 Apple Music `ttm:role="x-bg"` 背景和声自动拆行。
- **字幕超出画幅高亮 & CPS 语速预警**：实时解析行内 `\fs`、`\fscx`、`\fsp`、`\pos`、`\move` 与 `WrapStyle`，在网格和编辑框醒目提示超框溢出与高语速行。
- **HDR / 杜比视界 / BT.2020 兼容与保调变速**：自动识别 HDR10 (PQ)、HLG、Dolby Vision 与 BT.2020 色彩信息并展示徽标，提供 4K HDR 片源 1080p 预览降采样提速开关，以及 **0.25×–4.0× 保调倍速播放**。
- **AI 字幕语法分析、保标签翻译与润色**：内置 **DeepSeek**、**OpenRouter**、**Gemini**、**阿里云百炼（DashScope）** 与自定义 OpenAI 兼容端点预设；API Key 仅驻留运行期内存，绝不明文落盘。

### 2. 桌面级统一工作台（彻底告别旧版割裂体验）
- **单屏全联动架构**：视频舞台、8 工具可视化打字、可缩放波形/频谱打轴、语法高亮 ASS 编辑框与多列字幕网格同屏实时同步。
- **定比视频画布 + 8 大可视化打字工具**：
  - 叠加层严格锁定视频真实宽高比或 `PlayResX:PlayResY` 画布（支持无视频棋盘格虚拟画布、`50%–300%` 双指缩放平移、安全框辅助线），彻底根治黑边拉伸导致 `\pos` 偏移的问题。
  - 左侧常驻 8 工具竖条：**十字坐标**、**`\pos` 拖拽定位**、**`\move` 双手柄路径**、**`\frz` 平面旋转**、**3D `\frx/\fry` + `\org` 原点**、**`\fscx/\fscy` 缩放**、**矩形 `\clip/\iclip`**、**矢量多边形 `\clip/\iclip`**。
  - 实时渲染 `\move` 位移、`\fad`/`\fade` 渐变、`\t(...)` 动态变换、多色文本段与 `\kf` 卡拉OK逐字扫色。
- **专业可缩放音频打轴盒（`AegisubAudioBox`）**：
  - 流式防 OOM 波形峰值提取 + 有界 STFT 频谱热图 + 视频关键帧自动扫描（支持 `# keyframe format v1` 导入导出与关键帧吸附）。
  - 支持 `2s–60s` 时间窗自由缩放、时间刻度尺、前后邻行幽灵参考框、红起/橙止拖拽手柄、前后导一键添加、首/末/前/后 500ms 切点试听，以及卡拉OK音节分隔线交互式拖拽。
- **零偏移 ASS 语法高亮编辑框 & 全套工程面板**：
  - 实时高亮 `{...}` 标签块、`\tag` 指令与 `\N`/`\h` 转义符，输入法光标零错位。
  - 内置**双栏全局样式库 + 当前脚本样式管理器**、样式助手、翻译助手、汉字计时器（Kanji Timer）、按条件选行、粘贴覆盖、字体收集检查、分辨率重采样、帧率转换、时间后处理、历史快照、书签与 34 项外接键盘热键自定义。

---

## 📱 全形态设备与折叠屏深度适配

基于 Jetpack `WindowInfoTracker` 与 `FoldingFeature` 硬件折痕感知，自动在 **5 种工程姿态**间无缝切换（亦可在设置中手动锁定）：

| 姿态模式 | 适用设备与屏幕形态 | 工作台空间排布 |
|---|---|---|
| **`DESKTOP_THREE_ZONE`** | 大屏平板 (`≥ 840dp`)、折叠屏完全展平、Samsung DeX / 桌面窗口 | 1:1 复刻桌面 Aegisub 顶栏菜单+图标工具条；上半区左右可拖拽分割（左：**视频舞台** \| 右：**音频波形 + ASS 编辑框**），下半区可拖拽全宽 **9 列专业字幕网格**。 |
| **`BOOK_DUAL_PANE`** | 折叠屏书本半开 / 垂直折痕双屏 (`HALF_OPENED` / `isSeparating`) | **左书页**：视频预览舞台 + 音频波形/卡拉OK打轴台 · **铰链折痕避让带** · **右书页**：菜单工具条 + 语法高亮编辑框 + 字幕网格。 |
| **`TABLETOP`** | 折叠屏水平半折桌面悬停 (`HALF_OPENED + HORIZONTAL`) | **上半立屏**：100% 纯净无遮挡视频监看与打字画布 · **水平折痕避让带** · **下半平放触控台**：像笔记本一样的音频打轴、编辑框与网格切换台。 |
| **`LANDSCAPE_SPLIT`** | 手机横屏 / 小尺寸平板 (`600–839dp`) | 紧凑工具顶栏 + 可拖拽左右双栏（左：**视频 + 波形打轴** \| 右：**编辑框 + 字幕列表**）。 |
| **`PHONE_COMPACT`** | 手机竖屏单手握持 (`< 600dp`) | 单屏内顶部舞台四模式秒切（**影音同屏** / **音频打轴** / **视频打字** / **专注文本**）+ 内联编辑框 + 高密度双行卡片网格 + 底部拇指快捷栏。 |

---

## 📂 支持的字幕与歌词格式

| 格式 | 扩展名 | 导入 | 导出 | 特性说明 |
|---|---|---|---|---|
| **ASS** | `.ass` | ✅ | ✅ | 全量样式、`[Script Info]`、覆盖标签、矢量绘图无损读写，内部微秒精度 |
| **SSA** | `.ssa` | ✅ | ✅ | SubStation Alpha v4.00 样式与对齐码自动互转 |
| **SRT** | `.srt` | ✅ | ✅ | SubRip 标准双语与多行时间轴字幕 |
| **WebVTT** | `.vtt` | ✅ | ✅ | HTML5 网页视频字幕 |
| **TTML / DFXP** | `.ttml`, `.dfxp`, `.xml` | ✅ | ✅ | 支持 SGMY `<span>` 逐词卡拉OK与 Apple Music `ttm:role="x-bg"` 背景和声拆行 |
| **增强型 LRC** | `.lrc` | ✅ | ✅ | 支持标准 LRC、`[offset:]`、SGMY `<mm:ss.xx>` 逐字卡拉OK与行尾结束时间标记 |
| **MicroDVD** | `.sub` | ✅ | ✅ | `{起始帧}{结束帧}` 帧号字幕 |
| **纯文本** | `.txt` | ✅ | ✅ | 纯净台词文本导入与导出 |

---

## 🏗️ 模块架构

```text
aegisub-android/
├── core/domain/      # 纯 Kotlin/JVM 核心域：不可变模型、解析器、8 种字幕/歌词格式、SGMY 全套算法、CoW 撤销栈
├── core/data/        # Room（工程/快照/书签）、DataStore 偏好与热键持久化、SGMY AI 分析服务与 ICU 简繁桥接
├── feature/preview/  # Media3 HDR 感知播放器、流式波形/频谱/关键帧提取、定比字幕与 8 模式可视化打字层、AegisubAudioBox
├── feature/editor/   # 双栏全局样式库 + 脚本样式管理器、样式助手、翻译助手
└── app/              # 5 姿态自适应 WorkspaceScreen、27 类桌面+SGMY 专业弹窗、Studio 工程主屏、偏好与热键中心
```

与桌面版 Aegisub 及 SGMY 分支的逐项功能核对表见 [docs/COMPARISON.md](docs/COMPARISON.md)，演进路线见 [ROADMAP.md](ROADMAP.md)。

---

## 🛠️ 本地构建与测试

### 环境要求
- **JDK 17**
- **Android SDK 35** (`platforms;android-35`, `build-tools;35.0.0`)

### 常用命令
```bash
# 运行全部模块单元测试（:core:domain JUnit5 + :core:data + :feature:* + :app 折叠屏姿态测试）
./gradlew test

# 构建可安装的 Debug APK（产物路径：app/build/outputs/apk/debug/app-debug.apk）
./gradlew assembleDebug
```

---

## 📜 开源许可

本项目采用 **BSD 3-Clause (Aegisub Compatible)** 许可证发布，详见 [LICENCE](LICENCE)。
功能与算法参考并移植自 [samgum/Aegisub (Aegisub SGMY)](https://github.com/samgum/Aegisub) 与上游 [Aegisub](http://aegisub.org)；Android 架构与自适应工程由 **伤感咩吖 (`samgum`)** 编写维护。
