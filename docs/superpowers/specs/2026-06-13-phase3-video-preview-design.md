# Phase 3 · 视频预览与时间轴 — 设计规格

> 状态：已通过 brainstorming，待实施计划
> 作者：伤感咩吖
> 日期：2026-06-13
> 关联 ROADMAP：Phase 3（视频预览与时间轴）

---

## 1. 背景与目标

Phase 0/1/2 已交付字幕编辑闭环（加载 → 列表 → 编辑文本/时间/样式/层 → 撤销重做 → 防抖回写 Room；compact 列表+底栏 / expanded 双栏按屏宽自适应）。但字幕编辑器缺少最关键的一环：**在真实视频上看到字幕、并能跟随播放定位时间**。

Phase 3 目标：在 `:feature:editor` 之外新增 `:feature:preview`，让用户挂载一段视频，在播放画面上叠加字幕（简化样式），并提供播放控制与"点字幕行跳到该行起点 / 播放头高亮当前行"的时间联动。本阶段为**只读预览**——不修改字幕内容，与既有撤销/自动保存链路完全解耦。

### 成功标准（验收）
1. 用户可经系统文件选择器挂载视频，URI 跨会话持久化（杀进程后重开仍可播放）。
2. 视频可播放/暂停/0.5×–2× 倍速/拖动进度条 seek。
3. 播放画面叠加当前时间点的活动字幕（去标签纯文本 + 样式：字体名/字号/主色/加粗/斜体/真描边/对齐 1-9/边距）。
4. 点击字幕列表任意行 → 播放头跳到该行起点；播放头进入某事件区间时，列表高亮该行。
5. compact（手机）竖排、expanded（平板）双栏均自适应可用。
6. 域层"给定脚本+时间→当前活动事件+样式"抽成纯函数，纯 JVM 单测覆盖；`:feature:preview:testDebugUnitTest` 与 `:core:domain:test` 全绿。

---

## 2. 范围

### In Scope（本阶段交付）
- 新增 `:feature:preview` 模块 + `preview/{projectId}` 路由
- Room 迁移 1→2：`projects` 表加 `mediaUri` 列
- SAF 持久化 URI 选片 + `takePersistableUriPermission`
- Media3（ExoPlayer）封装为 `VideoPlayer` 接口，可注入测试替身
- `PreviewViewModel`：加载脚本（只读）+ 派生 `currentEventId` + `seekToEvent`
- `ActiveSubtitleResolver`：域层纯函数（当前活动事件 + 样式解析）
- `SubtitleOverlay`：Compose 自绘叠加（真描边 + 对齐/边距/加粗斜体）
- 预览屏 UI（compact/expanded）+ 倍速/播放/seek 控件 + 只读字幕列表
- 编辑器 TopAppBar 加"预览"动作导航

### Out of Scope（明确推迟）
- 拖拽起止手柄改时间、逐帧步进、打轴辅助 → **Phase 4**
- 样式编辑器、override 标签内联富文本（`\b \i \c \fs`）、`\pos/\move` 定位、矢量绘图 → **Phase 5**
- PlayResX/Y → 视频分辨率缩放（本阶段直接用脚本边距像素值）→ **Phase 5**
- libass JNI 精确渲染 → **远期预留**
- 音频波形/频谱 → **Phase 4**

---

## 3. 关键决策（已与用户确认）

| 维度 | 选定 | 理由 |
|---|---|---|
| 叠加保真度 | 简化纯文本 + 基础样式 | libass 属远期预留；MVP 用 Compose 自绘足以验证排版与打轴，成本可控 |
| 视频来源持久化 | SAF 持久化 URI + Room 存 `mediaUri` | 不复制文件、零额外空间、符合 Android 沙盒规范；`takePersistableUriPermission` 保证跨会话可读 |
| 时间轴交互边界 | 播放+seek+行联动（只读） | 与编辑链路解耦，降低风险；拖拽改时间留 Phase 4 |

---

## 4. 架构总览（方案 A）

新增独立 `:feature:preview` 模块，编辑器通过 TopAppBar 动作导航进入。预览只读消费脚本，不写数据。

```
:app  ──依赖──▶ :feature:editor  (Phase 2，文本编辑)
   │
   ├──依赖──▶ :feature:preview  (Phase 3，视频预览，新增)
   │                │
   │                ├──依赖──▶ :core:data  (Room repo)
   │                └──依赖──▶ :core:domain (AssScript 解析 + ActiveSubtitleResolver)
   │
   └── :core:domain ◀── :core:data
```

导航图新增目的地：
- `editor/{projectId}` —— TopAppBar actions 增加"预览" IconButton → `navController.navigate("preview/$projectId")`
- `preview/{projectId}` —— 新增，参数 `projectId: String`

### 为何独立模块而非塞进 editor
- 与既有 per-feature 模块范式（`:feature:editor`）一致。
- `EditorViewModel` 保持聚焦文本编辑+撤销+保存，职责不膨胀。
- 预览为只读消费，与写链路物理隔离，回归风险最低。
- Media3 依赖隔离在 `:feature:preview`，不污染 `:app`/`:core`。

---

## 5. 数据层变更（Room 迁移 1→2）

### 5.1 ProjectEntity
新增列：
```
val mediaUri: String? = null   // SAF 持久化 content URI，null 表示未挂载视频
```

### 5.2 Project（UI 模型）镜像
```
data class Project(..., val mediaUri: String?)
```
`ProjectEntity.toModel()` 带上 `mediaUri`。

### 5.3 ProjectRepository 接口扩展
```
suspend fun getMediaUri(id: Long): String?
suspend fun setMediaUri(id: Long, mediaUri: String)
```
实现走 DAO：`updateMediaUri(id, uri)`。

### 5.4 ProjectDao
- `observeAll`/`observeById`/`getById` 的 `SELECT` 与实体带 `mediaUri` 列
- 新增 `@Query("UPDATE projects SET mediaUri = :uri WHERE id = :id") suspend fun updateMediaUri(id: Long, uri: String)`

### 5.5 SubtitleDatabase
- `version = 2`
- `MIGRATION_1_2`：`ALTER TABLE projects ADD COLUMN mediaUri TEXT`
- 注册迁移，避免 `fallbackToDestructiveMigration` 丢数据

### 5.6 SAF 持久化流程（UI 侧）
1. `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())`，`arrayOf("video/*")`
2. 取得 `uri` 后：`context.contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`
3. `viewModel.setMediaUri(uri.toString())` → 回写 Room
4. 进程重启后 ExoPlayer 用同一 URI 字符串 `setMedia(MediaItem.fromUri(uri))` 仍可读（因已持久化授权）

---

## 6. VideoPlayer 抽象（可测关键）

把 ExoPlayer 藏在接口后，单测用假实现，零 Android 播放器依赖：

```
data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val isReady: Boolean = false,
)

interface VideoPlayer {
    val state: StateFlow<PlaybackState>
    fun setMedia(uri: String)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(rate: Float)
    fun release()
}
```

### Media3VideoPlayer 实现（Hilt @Inject）
- 内部持 `ExoPlayer.Builder(context).build()`
- `setMedia` → `setMediaItem(MediaItem.fromUri(uri))` + `prepare()`
- `Player.Listener`：`onIsPlayingChanged`/`onPlaybackStateChanged`/`onPositionDiscontinuity` 更新 `state`
- 位置采样：在 `Media3VideoPlayer` 内部以协程周期(≈50ms)轮询 `player.currentPosition` 更新 `state`；VM 经 `state.map{ positionMs }` 派生 `currentEventId`；UI 仅订阅 VM，不反向写状态
- `release()` 在 `PreviewViewModel.onCleared()` 调用

### 为什么不用 Media3 自带 PlayerView 的状态
自带状态耦合 UI，难以在 ViewModel/单测中驱动；抽象为 `StateFlow<PlaybackState>` 后，`currentEventId` 派生、`seekToEvent` 都可在无播放器环境下单测。

---

## 7. PreviewViewModel

```
sealed interface PreviewUiState {
    object Loading : PreviewUiState
    data class Loaded(
        val script: AssScript,
        val hasMedia: Boolean,          // mediaUri 是否非空
        val playback: PlaybackState,
        val currentEventId: Long?,      // 派生：当前时间点活动事件
    ) : PreviewUiState
    data class Error(val message: String) : PreviewUiState
}
```

### 职责
- `init`/加载：`projectId = savedStateHandle["projectId"]!!.toLong()`；`repo.getContent` → `AssFormat.detectAndParse`；分配稳定 id（与 EditorViewModel 同 `mapIndexed` 套路，保证与列表/`ActiveSubtitleResolver` 的 id 一致）；读 `mediaUri`。
- 若 `mediaUri` 非空 → `player.setMedia(uri)`；否则 `hasMedia=false`，UI 提示选片。
- `currentEventId` 派生：`player.state.map { it.positionMs }` → `ActiveSubtitleResolver.activeEventId(script, positionMs)`。
- `seekToEvent(eventId)`：查事件 → `player.seekTo(event.start.millis)`。
- `attachMedia(uri)`：SAF 回调路径，`takePersistableUriPermission` 后 `repo.setMediaUri` + `player.setMedia`。
- `onCleared()`：`player.release()`。

### 不写脚本
预览全程只读，不触发 `updateContent`、不动 `SnapshotUndoStack`。编辑在编辑器侧进行；预览每次加载读最新 Room 内容。

---

## 8. ActiveSubtitleResolver（域层纯函数）

放在 `:core:domain`（`io.github.samgum.aegisub.domain.preview`），纯 JVM、可单测、不依赖 Compose：

```
object ActiveSubtitleResolver {
    /** 给定脚本与播放位置(ms)，返回当前应显示的事件（非注释、start<=t<end），无则 null。 */
    fun activeEvent(script: AssScript, positionMs: Long): AssEvent?

    /** 给定事件与其脚本样式表，解析叠加所需渲染信息（去标签文本+样式）。 */
    fun resolveStyle(script: AssScript, event: AssEvent): SubtitleRenderInfo?
}

data class SubtitleRenderInfo(
    val text: String,            // event.strippedText
    val style: AssStyle,         // script.styles 匹配 event.style，未匹配回退 Default
)
```

### 设计要点
- "算"与"画"分离：`SubtitleOverlay` 只消费 `SubtitleRenderInfo`，逻辑全在纯函数里，单测覆盖边界（无活动事件/注释行不显示/样式缺失回退/区间边界 t==start 显示、t==end 不显示）。
- 样式查找：`script.styles.firstOrNull { it.name == event.style } ?: script.styles.firstOrNull { it.name == "Default" } ?: script.styles.firstOrNull() ?: AssStyle()`。

---

## 9. SubtitleOverlay 渲染（简化 ASS，纯 Compose）

```
@Composable
fun SubtitleOverlay(
    script: AssScript,
    positionMs: Long,
    modifier: Modifier = Modifier,
)
```

- 覆在 `PlayerView`（`AndroidView`）之上的 `Box`，`matchParentSize`。
- 调 `ActiveSubtitleResolver.activeEvent(script, positionMs)` → 无则 `return`（透明，不绘制）。
- `resolveStyle` 取 `SubtitleRenderInfo`。
- 用 `rememberTextMeasurer()` 测量 `text`（字体粗细/斜体按 style），在 `Canvas` 中**描边两次绘制**：
  1. `drawText(layout, color = style.outline.toComposeColor(), style = Stroke(width = style.outlineWidth.dp.toPx()*2))`
  2. `drawText(layout, color = style.primary.toComposeColor(), style = Fill)`
  → 真描边（非伪 shadow blur）。
- 对齐：`style.alignment`（\an 1-9）→ `Alignment`：
  - 7/8/9 → TopStart/TopCenter/TopEnd
  - 4/5/6 → CenterStart/Center/CenterEnd
  - 1/2/3 → BottomStart/BottomCenter/BottomEnd
  边距 `style.margins` + `event.margins` 叠加作 `Box` 的 `padding`。
- 字号：`style.fontSize.sp`（PlayRes 缩放留 Phase 5，本阶段直接用脚本 px/sp 值）。
- 加粗 `FontWeight.Bold`、斜体 `FontStyle.Italic`。
- 字体名 `style.font`：本阶段尽力映射（系统默认 sans/serif），自定义字体加载留 Phase 5。

### AssColor → Compose Color 扩展
在 `:feature:preview` 加 `AssColor.toColor(): Color = Color(argb)`（不污染 `:core:domain`，避免域层引入 Compose 依赖）。

---

## 10. 预览屏 UI（compact/expanded 自适应）

```
PreviewScreen(onBack: () -> Unit, viewModel: PreviewViewModel = hiltViewModel())
```
- `val isCompact = LocalConfiguration.current.screenWidthDp < 600`（与 EditorScreen 一致断点）。
- Loaded 分派：

#### compact（竖排 Column）
1. 顶部 `Box`：`AndroidView { PlayerView }`（16:9 纵横比 `aspectRatio(16f/9f)`）+ `SubtitleOverlay` 叠加
2. 控件行：播放/暂停 IconButton + 倍速下拉(0.5/0.75/1.0/1.25/1.5/2.0) + 当前时间/总时长 Text
3. `Slider` 进度条（绑定 `playback.positionMs` / `durationMs`，`onValueChangeFinished` → `player.seekTo`）
4. `LazyColumn` 只读字幕列表（`items(events, key={it.id}) { EventRow(...) }`），`currentEventId` 行高亮背景；点击 → `viewModel.seekToEvent(it.id)`

#### expanded（双栏 Row，复用 `EventRow`）
- 左 `Column` weight(0.6)：视频+叠加 + 控件 + 进度条
- 右 `LazyColumn` weight(0.4)：字幕列表（当前行高亮 + 点行 seek）

#### 无媒体态
`hasMedia=false` 时，视频区显示占位 Card + "选择视频"按钮 → 触发 SAF launcher。

#### TopAppBar
标题"预览"，返回 IconButton（`popBackStack`）。

---

## 11. 导航接线

### AppNavigation.kt
```
NavHost(startDestination = HOME_ROUTE) {
    composable(HOME_ROUTE) { HomeScreen(onOpenProject = { nav.navigate(editorRoute(it)) }) }
    editorScreen(onBack = { nav.popBackStack() })
    previewScreen(onBack = { nav.popBackStack() })
}
```

### 新增 feature/editor/.../navigation：编辑器"预览"动作
`EditorScreen` 增加 `onOpenPreview: () -> Unit` 回调，TopAppBar `actions` 加 `IconButton(Icons.Filled.PlayArrow)`；`AppNavigation` 中 `editorScreen(onBack=..., onOpenPreview = { id -> nav.navigate(previewRoute(id)) })`。

### feature/preview/.../navigation/PreviewNavigation.kt
```
const val PREVIEW_ROUTE_BASE = "preview"
fun previewRoute(projectId: Long) = "$PREVIEW_ROUTE_BASE/$projectId"
fun NavGraphBuilder.previewScreen(onBack: () -> Unit) {
    composable("$PREVIEW_ROUTE_BASE/{projectId}",
        arguments = listOf(navArgument("projectId"){type=NavType.StringType})) {
        PreviewScreen(onBack = onBack)
    }
}
```

---

## 12. 依赖变更

### `:feature:preview` 新模块 build.gradle.kts
- plugins: `com.android.library` + `kotlin("android")` + `org.jetbrains.kotlin.plugin.compose` + `com.google.devtools.ksp` + `com.google.dagger.hilt.android`
- android: namespace `io.github.samgum.aegisub.feature.preview`，与 editor 同 compileSdk 35 / minSdk 26 / jvmTarget 17
- dependencies:
  - `implementation(project(":core:domain"))`
  - `implementation(project(":core:data"))`
  - `implementation("androidx.media3:media3-exoplayer:1.5.1")`
  - `implementation("androidx.media3:media3-ui:1.5.1")`
  - Compose BOM + material3 + lifecycle-viewmodel-compose + hilt-navigation-compose + hilt-android（参照 editor 模块）
  - test: junit + coroutines-test + kotlinx（参照 editor test）

### `:app` build.gradle.kts
- `implementation(project(":feature:preview"))`

### settings.gradle.kts
- `include(":feature:preview")`

### 根目录 dependency notes
Media3 用最新稳定版（≥1.5.x，与 compileSdk 35 兼容）；上面示例写 1.5.1，实施时以 Maven Central 最新稳定版为准 pin。

---

## 13. 测试策略（延续 TDD）

### `:core:domain` — ActiveSubtitleResolverTest（纯 JVM）
- `activeEvent` 在区间内返回、`t==start` 返回、`t==end` 返回 null、跨多事件取正确行
- 注释行（`comment=true`）永不返回
- 无活动事件返回 null
- `resolveStyle`：样式名匹配、未匹配回退 Default、全空回退默认 AssStyle

### `:feature:preview` — PreviewViewModelTest（FakeVideoPlayer）
- 加载脚本成功进入 Loaded
- 空 `mediaUri` → `hasMedia=false`
- `currentEventId` 随 `positionMs` 推进而切换（推 positionMs → 断言 id）
- `seekToEvent(id)` → 假播放器记录 `seekedTo == event.start.ms`
- `attachMedia(uri)` → repo 记录 `setMediaUri` 调用 + `hasMedia=true`
- repo 抛错 → Error 态
- 假 `VideoPlayer`：内部 `MutableStateFlow<PlaybackState>`，提供 `emitPosition(ms)` 供测试驱动

### `:core:data` — 迁移测试
- `Migration1To2Test`：建 v1 库插入无 mediaUri 行 → 跑 `MIGRATION_1_2` → 校验列存在、旧数据 `mediaUri` 为 null、可写入。

### 手动验收
- `:app:assembleDebug` 出 APK
- 真机挂载一段视频，验证叠加/seek/高亮/倍速/跨会话持久化

---

## 14. 风险与缓解

| 风险 | 缓解 |
|---|---|
| SAF 持久化 URI 在某些厂商 ROM 上重定向失效 | 文档注明用标准 `OpenDocument` + `takePersistableUriPermission`；失败时回退提示重选（不崩溃） |
| Media3 体积增加 APK | 仅 `media3-exoplayer` + `media3-ui`，不加完整 media3；可接受 |
| 高频位置采样致卡顿 | 采样 ~50ms；叠加只重绘当前活动事件文本，LazyColumn 走稳定 key |
| 描边两次绘制性能 | 文本量小（单行字幕），`rememberTextMeasurer` 缓存；活动事件未变时不重测 |
| 预览与编辑器 id 不一致导致高亮错位 | 两处加载都用 `mapIndexed{ i,e -> e.copy(id=i.toLong()) }` 同套分配，保证行序→id 一致 |
| Room 迁移破坏既有数据 | 提供 `MIGRATION_1_2` 而非 destructive；补迁移单测 |

---

## 15. 交付里程碑（建议拆分，供 writing-plans 参考）

1. **3A 域层**：`ActiveSubtitleResolver` + `SubtitleRenderInfo` + 单测
2. **3B 数据层**：Room 迁移（`mediaUri` 列）+ repo 扩展 + 迁移测试
3. **3C 骨架**：`:feature:preview` 模块 + `VideoPlayer` 接口 + `Media3VideoPlayer` + `PreviewViewModel` + 单测（FakeVideoPlayer）
4. **3D 叠加与屏**：`SubtitleOverlay` 渲染 + `PreviewScreen`（compact/expanded）+ SAF 选片流程
5. **3E 接线收尾**：编辑器"预览"动作 + 导航注册 + assembleDebug 出 APK + ROADMAP/README 更新

---

## 16. 后续衔接

Phase 3 完成后，Phase 4（音频工程 + 打轴辅助）可在此基础上加：拖拽起止手柄（复用 `seekTo` 与 `setEventTimes` 编辑回写链路）、音频波形提取、逐帧步进。
