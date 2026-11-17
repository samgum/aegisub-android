# Aegisub Android

[English](README.md) | **简体中文**

[![CI](https://github.com/samgum/aegisub-android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/samgum/aegisub-android/actions/workflows/android-ci.yml)
[![Version](https://img.shields.io/badge/version-2.0.0--sgmy-2563EB)]()
[![License](https://img.shields.io/badge/license-BSD%203--Clause-16A34A)](LICENCE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)]()
[![Adaptive](https://img.shields.io/badge/adaptive-手机%20%7C%20平板%20%7C%20折叠屏%20%7C%20DeX-8B5CF6)]()

面向 Android 平台深度重构的全功能专业字幕工作站——同时 **1:1 完整复刻上游桌面版 Aegisub 全部核心功能** 与 **[samgum/Aegisub (Aegisub SGMY)](https://github.com/samgum/Aegisub) 全部定制功能**，并全面适配 **手机（竖屏单手 / 横屏分栏）、平板 / Samsung DeX 桌面模式、折叠屏（悬停 Tabletop / 书本双页 Book）以及外接物理键盘 + 鼠标/触控板**。

**作者 / 维护**：伤感咩吖 (`samgum`)

---

## ✨ 核心特性

### 1. 完整桌面版 Aegisub 全套能力
- **单屏全联动工作台**：视频预览舞台、可缩放波形/频谱打轴台、零偏移语法高亮 ASS 编辑框与 9 列专业字幕网格同屏实时同步，支持多级写时复制（CoW）无限撤销/重做。
- **Automation 4 自动化与卡拉OK模板引擎（`自动化` 菜单）**：
  - **Karaoke Templater (`kara-templater`)**：完整支持 `Comment: template line / pre-line / syl / char` 模板行、修饰符（`all`、`noblank`、`notext`、`keeptags`、`fxgroup`、`repeat N`）、`$start / $end / $dur / $mid / $kdur / $x / $y / $si / $ci` 内置变量与 `!数学表达式!` 求值，一键生成 `fx` 动态卡拉OK特效行。
  - 完整内置全部 7 款官方自动化脚本：**应用卡拉OK模板**、**清理整理标签 (`Clean Tags`)**、**剥离全部标签 (`Strip Tags`)**、**添加边缘柔化 (`\be1`)**、**半角字符转全角**、**卡拉OK自动前导衔接**、**选中所有时间重叠行**。
- **ASS 内嵌字体与图片附件（`[Fonts]` & `[Graphics]`）**：
  - 1:1 移植桌面 `libaegisub/ass/uuencode.cpp` 的 6-bit `+33` 80 列换行 UUEncode 编解码器（`AssUuCodec`），无损保留并支持直接内嵌或提取导出 `.ttf/.otf` 字体与图片附件。
- **定比视频舞台、可播放虚拟视频（Dummy Video）与 8 大可视化排版工具**：
  - 严格按视频真实宽高比或 `PlayResX:PlayResY` 定比渲染（`50%–300%` 缩放平移、安全框参考线、视频画面鼠标右键菜单 `video_context`）。
  - **可播放虚拟视频（Dummy Video）**：内置高精度单调虚拟帧率时钟，无需加载媒体文件即可自定义分辨率、帧率、时长、背景色与棋盘格纹理，直接播放/暂停、试听单句与逐帧步进调试特效。
  - **跳转到时间或帧号（`Ctrl+G`）** 与 **VFR 可变帧率时间码（`# timecode format v1 / v2`）** 导入导出。
  - 完整 8 种视觉工具：**坐标标尺**、**`\pos` 定位**、**`\move` 路径**、**`\frz` 平面旋转**、**3D `\frx/\fry` + `\org` 原点**、**`\fscx/\fscy` 缩放**、**矩形 `\clip/\iclip`**、**矢量多边形 `\clip/\iclip`**。
- **可缩放音频波形与频谱打轴盒（`AegisubAudioBox`）**：
  - 流式防 OOM 波形峰值提取 + 有界 STFT 频谱热图 + 视频关键帧提取（`# keyframe format v1` 导入导出与关键帧吸附）。
  - `2s–60s` 视窗自由缩放、红起/橙止手柄拖拽、前后导延长、首/末/前/后 0.5 秒切点试听，以及卡拉OK音节线交互切分。
- **全套桌面级专业工程对话框**：
  - **四合一时间后处理器（Timing Post-Processor）**：样式多选过滤 + 防碰撞 `safe_time` 前后导延长 + 相邻连轴与去重叠（含 `0%–100%` 边界偏置滑杆）+ 四阈值关键帧吸附。
  - **11 字段粘贴覆盖（Paste Over）**：`注释 / 图层 / 起点 / 终点 / 样式 / 说话人 / 左中右边距 / 特效备注 / 文本` 11 个字段独立勾选，同时支持纯文本与完整 `Dialogue:` 行。
  - **导出转换流水线（Export Filters & Charset）**：支持在导出时自动执行**卡拉OK模板展开 / 清理标签 / 转换帧率 / 剥离标签**，并支持 `UTF-8`、`UTF-8 BOM`、`UTF-16LE`、`GB18030`、`Big5`、`Shift_JIS` 6 种字符集编码输出。
  - **双栏样式管理器**（`全局样式库 Storage` ↔ `当前脚本样式`）+ **HSV / RGB / 不透明度 / `#RRGGBB` / `&HBBGGRR&` 全功能调色板**、样式助手、翻译助手、汉字计时复制器（Kanji Timer）、按条件选行、步进查找下一个/替换并下一个、原位仅排序选中行、纯文本说话人分隔符解析、拼写与错字检查、字体使用检查、分辨率重采样、历史版本快照与时间轴书签。

### 2. 完整复刻 `samgum/Aegisub` (SGMY) 全部独家功能
- **修复常见错误**：一键批量修复重叠时间、过短间隔、过短/过长时长、行尾空白（含全角 `U+3000`）与 ASS 不可见空白空行（自动保护 `template*` 模板与 `\p1` 绘图行），并分阶段独立提交撤销栈。
- **滚动歌词生成器**：支持单语与双语自动配对、7 档横竖屏画幅预设（含 `4K` 与竖屏 `1080×1920`），自动生成 `ScrollCurrent` / `ScrollDim` 样式及三次缓出 `\move` + `\t` 动态过渡。
- **中日韩文本工具箱**：Android ICU `Transliterator` + 词组表**简繁中文转换**、**字幕文本清理**、**栈式配对标点检查**、**幂等可重编日语假名注音（`{*sgmy-furigana}`）**。
- **时间缝合器 & 同样式重叠检查**、**增强型 LRC / TTML 逐词卡拉OK与 Apple Music 背景和声拆行**、**字幕超框溢出与高语速高亮**、**HDR10 / HLG / 杜比视界识别与 1080p 预览优化**、**`0.25×–4.0×` 保调变速**，以及 **OpenAI 兼容 AI 字幕语法分析/翻译/润色**（支持 DeepSeek、OpenRouter、Gemini、阿里云百炼与自定义端点，API Key 仅驻留运行内存）。

### 3. 清爽直观界面 + 完整键鼠操作与多形态适配
- **清爽无干扰 UI**：主界面与工具栏简洁直观，无冗余宣传注释或助记符噪音；未关联视频时手机端自动收起空画面框，将全屏空间让给字幕编辑。
- **原生级物理键盘与鼠标/触控板适配**：
  - 智能输入焦点隔离：在输入框打字时绝不误触单键打轴/播放热键；支持 `Enter` 提交跳下一句、`Shift+Enter` 插入 `\N`、`Ctrl+B/I/U` 加粗斜体下划线、`Ctrl+G` 跳帧定位、`Ctrl+X/C/V/A` 剪切复制粘贴全选。
  - 完整鼠标交互：字幕行、工程卡片与视频画布均支持**鼠标右键菜单**；网格支持 `Ctrl+左键` 多选、`Shift+左键` 连选与悬停高亮；音频打轴区支持左键设起点、右键设终点、滚轮平移时间轴与 `Ctrl/Shift+滚轮` 缩放。
- **5 种硬件形态自动响应**（`DESKTOP_THREE_ZONE` 桌面三区 / `BOOK_DUAL_PANE` 书本双页 / `TABLETOP` 悬停笔记本模式 / `LANDSCAPE_SPLIT` 横屏分栏 / `PHONE_COMPACT` 手机竖屏工作台）。

---

## 📂 支持的字幕与歌词格式（11 种双向读写格式）

| 格式 | 扩展名 | 导入 | 导出 | 说明 |
|---|---|---|---|---|
| **ASS** | `.ass` | ✅ | ✅ | 完整保留样式、`[Script Info]`、覆盖标签、矢量绘图及 `[Fonts]`/`[Graphics]` 内嵌附件 |
| **SSA** | `.ssa` | ✅ | ✅ | SubStation Alpha v4.00 样式与对齐码自动互转 |
| **SRT** | `.srt` | ✅ | ✅ | SubRip 通用多行时间轴字幕 |
| **WebVTT** | `.vtt` | ✅ | ✅ | HTML5 网页视频字幕 |
| **TTML / DFXP** | `.ttml`, `.dfxp`, `.xml` | ✅ | ✅ | `<span>` 逐词卡拉OK与 Apple Music `ttm:role="x-bg"` 和声拆行 |
| **增强型 LRC** | `.lrc` | ✅ | ✅ | `[mm:ss.xx]`、`[offset:]`、`<mm:ss.xx>` 逐字卡拉OK与行尾结束时间标记 |
| **MPEG-4 TTXT** | `.ttxt` | ✅ | ✅ | GPAC / MPEG-4 Timed Text v1.0 & v1.1 XML 字幕 |
| **MicroDVD** | `.sub` | ✅ | ✅ | `{起始帧}{结束帧}` 帧号字幕 |
| **TranStation** | `.transtation.txt` | ✅ | ✅ | TranStation 时间码字幕脚本 |
| **Adobe Encore** | `.encore.txt` | ✅ | ✅ | Encore 时间码字幕稿 |
| **纯文本** | `.txt` | ✅ | ✅ | 纯文本导入导出 + 说话人分隔符与注释前缀自动分角解析 |

---

## 🏗️ 模块架构

```text
aegisub-android/
├── core/domain/      # 纯 Kotlin/JVM 引擎：ASS/UUCodec 内嵌附件、11 种格式、Automation 4 与卡拉OK模板、SGMY 与桌面全量算法
├── core/data/        # Room（工程/快照/书签）、DataStore 偏好与热键持久化、AI 分析服务、ICU 简繁转换
├── feature/preview/  # Media3 HDR 播放器 + Dummy Video 虚拟时钟、流式波形/STFT/关键帧、8 大视觉排版层、AegisubAudioBox
├── feature/editor/   # 双栏全局样式库 + 脚本样式管理器、样式助手、翻译助手
└── app/              # 5 姿态自适应 WorkspaceScreen、完整键鼠事件系统、34 类桌面+SGMY 对话框、清爽工程主页与设置
```

与桌面版 Aegisub 及 SGMY 分支的功能对照表见 [docs/COMPARISON.md](docs/COMPARISON.md)，里程碑记录见 [ROADMAP.md](ROADMAP.md)。

---

## 🛠️ 本地构建与测试

### 环境要求
- **JDK 17**
- **Android SDK 35** (`platforms;android-35`, `build-tools;35.0.0`)

### 常用命令
```bash
# 运行全部单元测试、Android Lint 静态检查并构建 Debug APK
./gradlew test lintDebug assembleDebug
```

---

## 📜 开源许可

本项目基于 **BSD 3-Clause (Aegisub Compatible)** 许可证发布，详见 [LICENCE](LICENCE)。
功能与算法移植自 [samgum/Aegisub](https://github.com/samgum/Aegisub) 与上游 [Aegisub](http://aegisub.org)；Android 端架构与自适应工程由 **伤感咩吖 (`samgum`)** 维护。
