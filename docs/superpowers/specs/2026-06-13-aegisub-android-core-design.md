# Aegisub Android — 核心设计与架构 Spec

- **日期**：2026-06-13
- **项目**：Aegisub Android（专业级 Android 字幕编辑器）
- **参考来源**：`samgum/Aegisub`（fork 自 `TypesettingTools/Aegisub`，BSD 许可）
- **状态**：设计已确认，进入实现计划阶段

---

## 1. 背景与目标

### 1.1 背景
桌面 Aegisub 是 20 年沉淀的 C++ 项目（wxWidgets + libass + FFmpeg + LuaJIT + ICU + Boost），约 325 个源文件。**Android 版不是移植 C++ 代码**（桌面 GUI 技术栈在 Android 无意义），而是用 **Kotlin / Jetpack Compose 重新实现 Aegisub 的数据模型、格式编解码与交互体验**。C++ 源码作为算法与信息架构的参考蓝图。

### 1.2 核心目标
- 保留原版 Aegisub 核心体验：字幕/样式/标签/时间轴编辑、Karaoke、打轴辅助。
- 充分利用 Android 平台能力：响应式布局（手机/平板/横竖屏/折叠屏/DeX/Desktop Mode）、外接键鼠、Media3。
- 工业级工程：纯逻辑层可单测、撤销重做、自动保存、历史版本、超大文件（10 万行）可编辑。

### 1.3 非目标（本阶段）
- 不复刻 wxWidgets 任何 UI 代码。
- 不在本阶段引入 libass JNI 渲染、Lua Automation、AI 字幕（均为远期预留）。
- 不联网（纯离线工具）；因此 CLAUDE.md 中的 API 签名/防注入等后端安全规则**对本项目不触发**。

---

## 2. 模块架构（多模块 Gradle，黑盒下沉）

```
aegisub-android/
├── :core:domain      ★ 纯 Kotlin/JVM，零 Android 依赖。【Milestone 1 交付】
│   ├─ time/    SubTime（微秒）+ ASS/SRT/LRC/SMPTE 格式化与解析
│   ├─ model/   AssScript / AssEvent / AssStyle / AssInfo / AssColor（不可变 data class）
│   ├─ parse/   对话体 tokenizer（端口自 Aegisub dialogue_parser）+ 块模型
│   ├─ format/  SubtitleFormat 接口 + Ass/Ssa/Srt/Txt/Lrc 编解码 + 自动检测注册表
│   └─ undo/    UndoStack 接口 + 不可变 CoW 快照实现
├── :core:data        Room + DataStore：项目管理 / 自动保存 / 历史版本（Phase 1）
├── :core:media       Media3 / ExoPlayer 封装 + 音频波形提取（Phase 3-4）
├── :core:ass-render  libass JNI 桥（远期预留）
├── :feature:editor   Compose 字幕列表 + 事件编辑（Phase 2）
├── :feature:timeline 时间轴 + 音频波形 UI（Phase 3-4）
├── :feature:video    视频预览（Phase 3）
├── :feature:styles   样式编辑器 + 标签编辑 + Karaoke（Phase 5）
└── :app              应用入口 + 导航 + Hilt DI + 响应式布局
```

**设计原则**：
- `:core:domain` 零 Android 依赖 → 可在纯 JVM 跑单元测试，不受模拟器拖累。
- 业务逻辑全部下沉到 `:core:*`，UI 层（`:feature:*` / `:app`）只做展示与输入转发（黑盒架构）。
- 模块间通过接口（Repository / UseCase）通信，便于替换与测试。

---

## 3. 关键架构决策：撤销引擎

Aegisub 原版采用"整文件快照推栈 + CommitType 位掩码粒度"。**在 10 万行场景下每次编辑拷贝整个文件会 OOM**，必须重构。

| 方案 | 内存代价 | 实现复杂度 | 结论 |
|---|---|---|---|
| A. 整文件快照（原版） | O(n)/次 | 低 | ✗ 10 万行崩溃 |
| B. Command 模式（do/undo） | O(编辑数) | 高 | 复杂操作难保一致性 |
| **C. 不可变模型 + 结构共享快照** | **O(差分)** | 中 | ✓ 采用 |

**采用方案 C**：
- `AssScript` 及其子结构为不可变（`data class` + `kotlin.collections.immutable` 持久集合）。
- 每次编辑产生新版本，但**只复制变化路径**（结构共享），旧版本作为 undo 栈引用保留。
- Undo 栈为有界环形缓冲（默认上限可配），超出则丢弃最旧；触发自动保存后可清栈。
- **性能演进留接口**：MVP 先用 `ImmutableList` 快照（小文件足够）；Phase 7 引入 bit-partitioned trie 持久向量支持 10 万行级，通过同一 `UndoStack` 接口替换实现，不破坏调用方。

---

## 4. 时间精度模型（硬要求：精度不得丢失）

Aegisub 内部存 `int 毫秒`，ASS 规范取厘秒（centisecond = 10ms，`+5` 向上取整），SRT 用完整毫秒。**本设计升级内部精度**：

- `SubTime`：值类（value class），内部 = `Long 微秒`（10⁻⁶s），高于 ASS 厘秒(10⁻²)与 LRC 毫秒(10⁻³)。
- **所有格式仅在导出时按规则取整；解析时保留全精度** → 往返零损失。
- 有效范围：`[0, 10 小时]`（对齐 Aegisub 的 `10*60*60*1000` 上限）。
- 容错解析：接受 `H:MM:SS.cs` / `H:MM:SS.mmm` / `MM:SS.xx` 等，`.` 与 `,` 均作小数分隔符。

### LRC 四种时间格式建模
```
LrcTimeFormat { precision: CENTI | MILLI, separator: DOT | COLON }
  [mm:ss.xx]   → CENTI, DOT
  [mm:ss.xxx]  → MILLI, DOT
  [mm:ss:xx]   → CENTI, COLON
  [mm:ss:xxx]  → MILLI, COLON
```
注：上游 Aegisub **原生无 LRC**，本格式为全新实现。

---

## 5. 格式与自动检测

### 5.1 支持格式
ASS、SSA、SRT、TXT、**LRC（全新）**。导入导出双向。

### 5.2 SubtitleFormat 接口
```kotlin
interface SubtitleFormat {
    val name: String
    val extensions: List<String>          // [".ass"], [".srt"] ...
    fun canRead(fileName: String, content: String): Boolean
    fun canWrite(fileName: String): Boolean
    fun read(text: String, options: ReadOptions): AssScript   // 抛 SubtitleFormatException
    fun write(script: AssScript, options: WriteOptions): String
}
```

### 5.3 自动检测（FormatRegistry）
= 扩展名优先 + 内容嗅探兜底：
- ASS/SSA：含 `[Script Info]` / `[V4+ Styles]` / `[V4 Styles]` / `[Events]` 段头。
- SRT：行首序号 + `-->` 时间箭头。
- LRC：多行行首匹配 `[mm:ss.xx]` / `[mm:ss.xxx]` / `[mm:ss:xx]` / `[mm:ss:xxx]`。
- TXT：无时间标记的纯文本（按行生成无时间事件，或按设定间隔分配）。

### 5.4 导出精度选项
```
TimePrecision { TWO_MS(厘秒, ASS默认) | THREE_MS(毫秒, SRT/LRC) | AUTO }
```
混合格式文件：先统一为内部 `AssScript` 表示，再按所选精度与格式导出。

### 5.5 转换工具（对齐 Aegisub 静态方法）
- `stripTags` / `stripComments` / `convertNewlines` / `recombineOverlaps` / `mergeIdentical`。

---

## 6. 数据模型（不可变，对齐 C++）

```kotlin
@Immutable data class AssScript(
    val info: ImmutableList<AssInfo>,
    val styles: ImmutableList<AssStyle>,
    val events: ImmutableList<AssEvent>,
    val extradata: ImmutableList<ExtradataEntry> = persistentListOf(),
    val properties: ProjectProperties = ProjectProperties(),
)

@Immutable data class AssEvent(
    val id: Long, val row: Int, val comment: Boolean = false,
    val layer: Int = 0, val margins: Margins = Margins.ZERO,
    val start: SubTime, val end: SubTime,
    val style: String = "Default", val actor: String = "", val effect: String = "",
    val text: String = "",
)

@Immutable data class AssStyle(
    val name: String = "Default", val font: String = "Arial", val fontSize: Double = 48.0,
    val primary: AssColor = AssColor.WHITE, val secondary: AssColor = AssColor.RED,
    val outline: AssColor = AssColor.BLACK, val shadow: AssColor = AssColor.BLACK,
    val bold: Boolean = false, val italic: Boolean = false,
    val underline: Boolean = false, val strikeout: Boolean = false,
    val scaleX: Double = 100.0, val scaleY: Double = 100.0,
    val spacing: Double = 0.0, val angle: Double = 0.0,
    val borderStyle: Int = 1, val outlineWidth: Double = 2.0, val shadowWidth: Double = 2.0,
    val alignment: Int = 2,           // \an 风格（1-9）
    val margins: Margins = Margins.ZERO, val encoding: Int = 1,
)

@Immutable data class AssInfo(val key: String, val value: String)
@Immutable data class AssColor(val r: Int, val g: Int, val b: Int, val a: Int = 0)

// 值类型，承载 ASS 三段式边距（左 / 右 / 纵向），对齐 C++ 的 std::array<int,3> Margin
@Immutable inline class Margins(val left: Int, val right: Int, val vertical: Int) {
    companion object { val ZERO = Margins(0, 0, 0) }
}

sealed interface DialogueBlock {
    data class Plain(val text: String) : DialogueBlock
    data class Comment(val text: String) : DialogueBlock
    data class Override(val tags: List<AssOverrideTag>) : DialogueBlock
    data class Drawing(val text: String, val scale: Int) : DialogueBlock
}
```
> 注：`flyweight` 优化（C++ 用于 Style/Actor 等重复字符串）在 Kotlin 中用字符串常量池 + 不可变集合自然达成，无需额外机制。

---

## 7. 对话体 Tokenizer（ASS 编辑核心）

端口自 `libaegisub/ass/dialogue_parser.cpp`，将对话 `text` 切分为 token 流：
`TEXT / WORD / LINE_BREAK / OVR_BEGIN({) / OVR_END(}) / TAG_START(\) / TAG_NAME / OPEN_PAREN / CLOSE_PAREN / ARG_SEP(,) / ARG / WHITESPACE / DRAWING_* / KARAOKE_* / COMMENT / ERROR`。

- 纯函数：`tokenize(text): List<DialogueToken>`、`markDrawings(...)`、`splitWords(...)`。
- 同时驱动：语法高亮、标签结构化解析、查找替换、Karaoke。
- 词分割：C++ 用 ICU BreakIterator；Android 侧用 `java.text.BreakIterator`（Phase 5 启用拼写检查时）。
- 完全可单测，无 Android 依赖。

---

## 8. 错误处理

- 类型化异常体系：`SubtitleFormatException`（基类）→ `UnknownFormatError` / `ParseError` / `WriteError`。
- **单行容错**：畸形单行不中断整体解析，降级为 `ParseDiagnostic(level, lineNo, message)` 收集到结果；致命错误（编码无法识别等）才抛异常。
- 编码：统一 UTF-8；导入侧探测 BOM 并剥离（ASS 历史可能含 BOM）。

---

## 9. 测试策略（TDD）

`:core:domain` 采用**测试驱动开发**，目标覆盖率 ≥ 90%，全部纯 JVM 可跑：

- **往返等价**：`parse(write(parse(x))) == parse(x)`，针对 ASS/SSA/SRT/LRC/TXT 各做属性测试。
- **LRC 四格式**：分别构造与解析 `[mm:ss.xx]/[mm:ss.xxx]/[mm:ss:xx]/[mm:ss:xxx]`，含混合格式文件。
- **自动检测**：内容嗅探各格式样本，断言命中正确格式。
- **边界**：空文件、10 小时上限、缺字段、负值、超长行。
- **ASS↔SSA 对齐换算**：`AssToSsa` / `SsaToAss`（numpad 1-9 ↔ 1-9 网格）。
- **覆盖标签 round-trip**：`{\i1}am{\i0}` 解析→块→重组文本一致。
- **撤销**：do/undo 序列恢复原状、栈上限行为、CoW 不污染历史版本。

---

## 10. 许可证与署名策略

- 项目许可证：**Aegisub BSD**（与上游一致，GPL 兼容），根目录保留 `LICENCE`。
- **凡参考/对齐 Aegisub 算法移植的文件**：文件头保留 BSD 版权声明 `Copyright (c) 2004-2012, Aegisub Project`，并标注 `ported from libaegisub`（**法定要求，非作者署名**）。
- **原创代码**：文件头署名 **伤感咩吖**（遵循 CLAUDE.md）。
- 二者并存：BSD 版权保留 = 法律合规；伤感咩吖 = 作者标识。

---

## 11. 安全考量（对齐 CLAUDE.md）

- 纯离线工具，**不联网** → API 签名/指纹/Prompt Injection 等后端安全规则不触发。
- `.gitignore` 强制屏蔽：`*.keystore` / `*.jks` / `.env` / `local.properties` / `*.pem` / `*.key` 等。
- 提交前自检：扫描 diff 中的密钥模式（`gh_`、`AKIA`、`-----BEGIN` 等），命中即阻断。
- 日志：本阶段为离线开发，无服务端日志；后续若引入崩溃上报，敏感字段脱敏。

---

## 12. 分阶段 Roadmap

| Phase | 内容 | 状态 |
|---|---|---|
| **0（本会话）** | `:core:domain` 完整 + TDD：时间/模型/tokenizer/5 格式/撤销引擎 | 🔨 进行中 |
| 1 | 项目管理(Room) + DataStore + 自动保存 + App 外壳 + Hilt DI | 待办 |
| 2 | Compose 字幕列表(虚拟列表 LazyColumn) + 事件编辑器 + 响应式布局 | 待办 |
| 3 | 视频预览(Media3/ExoPlayer) + 基础时间轴 | 待办 |
| 4 | 音频波形/频谱 + 逐帧 + 关键帧 | 待办 |
| 5 | 样式编辑器 + ASS 标签编辑 UI + Karaoke | 待办 |
| 6 | 打轴辅助 + 批量操作 + 查找替换(正则) | 待办 |
| 7 | 10 万行性能硬化（持久向量 + 分页）+ 历史版本 | 待办 |
| 预留 | Lua Automation / 插件系统 / libass JNI 渲染 / AI 辅助字幕 | 远期 |

### 平板布局目标（Phase 2 起遵循）
```
┌──────────┬───────────────────────┬──────────────┐
│ 字幕列表  │      视频预览          │  属性编辑器   │
│          ├───────────────────────┤              │
│          │      时间轴 / 波形      │              │
└──────────┴───────────────────────┴──────────────┘
```

---

## 13. Milestone 1（本会话）交付验收标准

`:core:domain` 模块，纯 Kotlin/JVM，**全部单测通过**：

1. `SubTime`：微秒存储、ASS/SRT/LRC(4 格式)/SMPTE stub 格式化与解析、范围限制、容错。
2. 数据模型：`AssScript / AssEvent / AssStyle / AssInfo / AssColor` 不可变定义。
3. 对话体 tokenizer：token 化、块模型、绘图标记。
4. 格式编解码：ASS / SSA / SRT / TXT / **LRC** 读写 + 自动检测注册表 + 导出精度选项。
5. 撤销引擎：`UndoStack` 接口 + CoW 快照实现 + 单测。
6. `LICENCE`、`README.md`、本 spec、ROADMAP.md 入库。
