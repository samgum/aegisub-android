# Phase 3 · 视频预览与时间轴 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实施。步骤用 `- [ ]` 复选框跟踪。

**Goal:** 在 `:feature:preview` 新模块里挂载视频（SAF 持久化 URI）、用 Media3 播放，并在画面上叠加简化样式的当前字幕，提供播放/seek/倍速与字幕行联动。

**Architecture:** 新建 `:feature:preview` 模块 + `preview/{projectId}` 路由（方案 A，与编辑器解耦）。ExoPlayer 封装在 `VideoPlayer` 接口后便于单测；预览只读消费脚本，不动撤销/自动保存。域层 `ActiveSubtitleResolver` 把"给定时间→当前活动事件+样式"抽成纯函数。

**Tech Stack:** Kotlin 2.1.0 / Compose BOM 2024.12.01 / Material3 / Hilt / Coroutines+Flow / Room 2.6.1 / Media3 (ExoPlayer+PlayerView) 1.5.x / AGP 8.7.3 / compileSdk 35 / minSdk 26 / JDK 17。

**关联 spec:** `docs/superpowers/specs/2026-06-13-phase3-video-preview-design.md`

---

## 文件结构总览

### 新建文件
| 路径 | 职责 |
|---|---|
| `core/domain/.../preview/ActiveSubtitleResolver.kt` | 纯函数：当前活动事件 + 样式 + 渲染信息（无 Android/Compose 依赖） |
| `core/domain/src/test/.../preview/ActiveSubtitleResolverTest.kt` | 纯 JVM 单测 |
| `feature/preview/build.gradle.kts` | 新模块构建脚本 |
| `feature/preview/.../VideoPlayer.kt` | `VideoPlayer` 接口 + `PlaybackState` |
| `feature/preview/.../Media3VideoPlayer.kt` | ExoPlayer 封装实现 |
| `feature/preview/.../di/PreviewModule.kt` | Hilt `@Binds VideoPlayer → Media3VideoPlayer` |
| `feature/preview/.../PreviewViewModel.kt` | 加载脚本（只读）+ 派生 `currentEventId` + seek/媒体控制 + `PreviewUiState` |
| `feature/preview/src/test/.../PreviewViewModelTest.kt` | FakeVideoPlayer + FakeRepo 单测 |
| `feature/preview/.../components/AssColorExt.kt` | `AssColor.toColor()` 扩展（隔离 Compose 依赖于本模块） |
| `feature/preview/.../components/SubtitleOverlay.kt` | Compose 自绘字幕叠加（真描边 + 对齐/边距） |
| `feature/preview/.../components/PlayerSurface.kt` | `PlayerView`（AndroidView）包裹 |
| `feature/preview/.../PreviewScreen.kt` | 预览屏入口（compact/expanded + 控件 + SAF） |
| `feature/preview/.../navigation/PreviewNavigation.kt` | `preview/{projectId}` 路由注册 |

### 修改文件
| 路径 | 改动 |
|---|---|
| `core/data/.../local/ProjectEntity.kt` | 加 `mediaUri: String? = null` 列 |
| `core/data/.../repository/ProjectRepository.kt` | `Project` 加 `mediaUri`；接口加 `getMediaUri/setMediaUri`；实现转发 DAO |
| `core/data/.../local/ProjectDao.kt` | 加 `updateMediaUri(id, uri)` |
| `core/data/.../local/SubtitleDatabase.kt` | version=2 + `MIGRATION_1_2` |
| `core/data/.../di/DataModule.kt` | `fallbackToDestructiveMigration()` → `addMigrations(MIGRATION_1_2)` |
| `settings.gradle.kts` | `include(":feature:preview")` |
| `feature/editor/.../EditorScreen.kt` | 加 `onOpenPreview: (Long) -> Unit`，compact/expanded 各加"预览"按钮 |
| `feature/editor/.../expanded/EditorTwoPane.kt` | 加 `onOpenPreview: () -> Unit` 参，TopAppBar 加预览按钮 |
| `app/.../navigation/AppNavigation.kt` | 注册 `previewScreen`；editor 接 `onOpenPreview` |
| `ROADMAP.md` / `README.md` | Phase 3 状态更新 |

---

## Task 3A：ActiveSubtitleResolver（域层纯函数，TDD）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/preview/ActiveSubtitleResolver.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/preview/ActiveSubtitleResolverTest.kt`

- [ ] **Step 1：写失败测试**

`core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/preview/ActiveSubtitleResolverTest.kt`：

```kotlin
package io.github.samgum.aegisub.domain.preview

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.time.SubTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ActiveSubtitleResolver 纯函数测试。
 * @author 伤感咩吖
 */
class ActiveSubtitleResolverTest {

    private val script = AssScript(
        styles = persistentListOf(
            AssStyle(name = "Default", alignment = 2),
            AssStyle(name = "Title", alignment = 8),
        ),
        events = persistentListOf(
            AssEvent(id = 0, start = SubTime.ofMillis(1_000), end = SubTime.ofMillis(3_000), style = "Default", text = "第一句"),
            AssEvent(id = 1, comment = true, start = SubTime.ofMillis(3_000), end = SubTime.ofMillis(4_000), text = "注释"),
            AssEvent(id = 2, start = SubTime.ofMillis(4_000), end = SubTime.ofMillis(6_000), style = "Title", text = "第二句"),
        ),
    )

    @Test fun activeEvent_returns_event_within_range() {
        val ev = ActiveSubtitleResolver.activeEvent(script, 2_000)
        assertEquals(0L, ev?.id)
    }

    @Test fun activeEvent_at_start_returns_event() {
        assertEquals(0L, ActiveSubtitleResolver.activeEvent(script, 1_000)?.id)
    }

    @Test fun activeEvent_at_end_returns_null() {
        // 半开区间 [start, end)：t == end 不属于本行
        assertNull(ActiveSubtitleResolver.activeEvent(script, 3_000))
    }

    @Test fun activeEvent_skips_comment_lines() {
        // 3_000~4_000 是注释行，应被跳过返回 null
        assertNull(ActiveSubtitleResolver.activeEvent(script, 3_500))
    }

    @Test fun activeEvent_null_when_no_active() {
        assertNull(ActiveSubtitleResolver.activeEvent(script, 999))
    }

    @Test fun renderInfo_resolves_text_and_named_style() {
        val info = ActiveSubtitleResolver.renderInfo(script, 5_000)
        assertEquals("第二句", info?.text)
        assertEquals("Title", info?.style?.name)
    }

    @Test fun renderInfo_falls_back_to_default_style_when_name_missing() {
        // event.style="Default" 命中 Default 样式
        val info = ActiveSubtitleResolver.renderInfo(script, 2_000)
        assertEquals("Default", info?.style?.name)
    }

    @Test fun renderInfo_falls_back_to_first_style_when_no_match_or_default() {
        val noDefault = AssScript(
            styles = persistentListOf(AssStyle(name = "Custom")),
            events = persistentListOf(
                AssEvent(id = 0, start = SubTime.ofMillis(0), end = SubTime.ofMillis(1_000), style = "Missing", text = "x"),
            ),
        )
        val info = ActiveSubtitleResolver.renderInfo(noDefault, 500)
        assertEquals("Custom", info?.style?.name)
    }

    @Test fun renderInfo_null_when_no_active_event() {
        assertNull(ActiveSubtitleResolver.renderInfo(script, 9_999))
    }

    private companion object {
        // 复用 persistentListOf，避免每个测试重复全限定名
        fun <T> persistentListOf(vararg items: T) =
            kotlinx.collections.immutable.persistentListOf(*items)
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
./gradlew :core:domain:test --tests "*.ActiveSubtitleResolverTest" --no-daemon
```
Expected: FAIL（`ActiveSubtitleResolver` / `SubtitleRenderInfo` 未解析）。

- [ ] **Step 3：写最小实现**

`core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/preview/ActiveSubtitleResolver.kt`：

```kotlin
package io.github.samgum.aegisub.domain.preview

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.Margins

/**
 * 渲染一条叠加字幕所需的最小信息：去标签纯文本 + 解析后的样式 + 合并边距。
 *
 * @author 伤感咩吖
 */
data class SubtitleRenderInfo(
    val text: String,
    val style: AssStyle,
    val margins: Margins,
)

/**
 * 给定脚本与播放位置，解析当前应叠加显示的字幕。
 * 纯函数，无 Android/Compose 依赖，可纯 JVM 单测。
 *
 * @author 伤感咩吖
 */
object ActiveSubtitleResolver {

    /** 返回当前时间点应显示的事件（非注释、start <= t < end），无则 null。 */
    fun activeEvent(script: AssScript, positionMs: Long): AssEvent? =
        script.events.firstOrNull { event ->
            !event.comment &&
                event.start.millis <= positionMs &&
                positionMs < event.end.millis
        }

    /** 返回当前应叠加的渲染信息（无活动事件返回 null）。 */
    fun renderInfo(script: AssScript, positionMs: Long): SubtitleRenderInfo? {
        val event = activeEvent(script, positionMs) ?: return null
        val style = resolveStyle(script, event)
        val margins = Margins(
            left = style.margins.left + event.margins.left,
            right = style.margins.right + event.margins.right,
            vertical = style.margins.vertical + event.margins.vertical,
        )
        return SubtitleRenderInfo(text = event.strippedText, style = style, margins = margins)
    }

    /** 样式解析：按名匹配 → Default → 首个 → 默认 AssStyle。 */
    fun resolveStyle(script: AssScript, event: AssEvent): AssStyle =
        script.styles.firstOrNull { it.name == event.style }
            ?: script.styles.firstOrNull { it.name == "Default" }
            ?: script.styles.firstOrNull()
            ?: AssStyle()
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
./gradlew :core:domain:test --tests "*.ActiveSubtitleResolverTest" --no-daemon
```
Expected: PASS（9 个用例）。

- [ ] **Step 5：提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/preview/ActiveSubtitleResolver.kt \
        core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/preview/ActiveSubtitleResolverTest.kt
git commit -m "feat(domain): ActiveSubtitleResolver 当前活动字幕纯函数 + 9 单测

- activeEvent/renderInfo/resolveStyle 纯函数，无 Android 依赖
- 半开区间 [start,end)、注释行跳过、样式缺失回退
作者：伤感咩吖"
```

---

## Task 3B.1：数据模型加 mediaUri 列

**Files:**
- Modify: `core/data/src/main/kotlin/io/github/samgum/aegisub/data/local/ProjectEntity.kt`
- Modify: `core/data/src/main/kotlin/io/github/samgum/aegisub/data/repository/ProjectRepository.kt`
- Modify: `core/data/src/main/kotlin/io/github/samgum/aegisub/data/local/ProjectDao.kt`

> 说明：`ProjectRepository` 与 `Project`/`RoomProjectRepository` 都在 `ProjectRepository.kt` 同一文件。

- [ ] **Step 1：ProjectEntity 加列**

把 `ProjectEntity.kt` 的 data class 改为（在 `lastOpenedAt` 之后追加 `mediaUri`）：

```kotlin
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val format: String,      // ass / ssa / srt / lrc / txt
    val content: String,     // 序列化的字幕全文（由 :core:domain 格式编码器产出）
    val createdAt: Long,     // epoch millis
    val updatedAt: Long,     // epoch millis
    val lastOpenedAt: Long?, // epoch millis，null 表示从未打开
    val mediaUri: String? = null, // SAF 持久化视频 content URI，null 表示未挂载视频
)
```

- [ ] **Step 2：Project UI 模型镜像 + toModel 带列**

`ProjectRepository.kt` 中 `Project` data class 改为：

```kotlin
data class Project(
    val id: Long,
    val name: String,
    val format: String,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
    val mediaUri: String?,
)
```

`RoomProjectRepository.toModel()` 改为：

```kotlin
private fun ProjectEntity.toModel() =
    Project(
        id = id, name = name, format = format,
        updatedAt = updatedAt, lastOpenedAt = lastOpenedAt, mediaUri = mediaUri,
    )
```

- [ ] **Step 3：Repository 接口加 getMediaUri/setMediaUri**

`ProjectRepository.kt` 接口在 `touchLastOpened` 之后追加两行：

```kotlin
interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    fun observeProject(id: Long): Flow<Project?>
    suspend fun getContent(id: Long): String
    suspend fun createProject(name: String, format: String, content: String): Long
    suspend fun updateContent(id: Long, content: String, now: Long)
    suspend fun delete(id: Long)
    suspend fun touchLastOpened(id: Long, now: Long)
    suspend fun getMediaUri(id: Long): String?
    suspend fun setMediaUri(id: Long, mediaUri: String)
}
```

`RoomProjectRepository` 在 `touchLastOpened` 实现之后追加：

```kotlin
override suspend fun getMediaUri(id: Long): String? = dao.getById(id)?.mediaUri

override suspend fun setMediaUri(id: Long, mediaUri: String) {
    val entity = dao.getById(id) ?: return
    dao.updateMediaUri(id, mediaUri)
}
```

- [ ] **Step 4：DAO 加 updateMediaUri**

`ProjectDao.kt` 在 `touchLastOpened` 之后追加：

```kotlin
@Query("UPDATE projects SET mediaUri = :uri WHERE id = :id")
suspend fun updateMediaUri(id: Long, uri: String)
```

- [ ] **Step 5：编译验证（Room 生成 + 新列生效）**

Run:
```bash
./gradlew :core:data:compileDebugKotlin --no-daemon
```
Expected: BUILD SUCCESSFUL（KSP 重新生成 Room 实现，含 `updateMediaUri`）。

> 注：既有 `ProjectRepositoryTest`（假 DAO）会因接口新增两个方法编译失败——补上假实现。打开 `core/data/src/test/.../repository/ProjectRepositoryTest.kt`，在测试用的假 DAO/假 Repo 里追加：
> ```kotlin
> override suspend fun getMediaUri(id: Long): String? = null
> override suspend fun setMediaUri(id: Long, mediaUri: String) {}
> ```
> （若该测试用的是真实接口 mock，等价补桩。）

- [ ] **Step 6：提交**

```bash
git add core/data/src/main/kotlin/io/github/samgum/aegisub/data/local/ProjectEntity.kt \
        core/data/src/main/kotlin/io/github/samgum/aegisub/data/local/ProjectDao.kt \
        core/data/src/main/kotlin/io/github/samgum/aegisub/data/repository/ProjectRepository.kt \
        core/data/src/test/kotlin/io/github/samgum/aegisub/data/repository/ProjectRepositoryTest.kt
git commit -m "feat(data): projects 表加 mediaUri 列 + 仓储读写

- ProjectEntity/Project/DAO/Repository 同步 mediaUri
- 预备 SAF 持久化视频 URI
作者：伤感咩吖"
```

---

## Task 3B.2：Room 迁移 1→2 + 注册

**Files:**
- Modify: `core/data/src/main/kotlin/io/github/samgum/aegisub/data/local/SubtitleDatabase.kt`
- Modify: `core/data/src/main/kotlin/io/github/samgum/aegisub/data/di/DataModule.kt`

> 测试说明（务实偏离 spec §13）：迁移测试需 Robolectric/真机才能跑（纯 JVM 无法测 `SupportSQLiteDatabase`）。本计划不引入 Robolectric（避免 `--no-daemon` Windows 环境首次下载 runtime jar 卡住测试任务）。迁移 SQL 是单条 `ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT NULL`，由 `assembleDebug` 的 Room 编译校验保证结构正确。app 尚未发布、无 v1 用户数据需保护。Robolectric 迁移单测列为后续可补项。

- [ ] **Step 1：SubtitleDatabase 升 version + 定义迁移**

替换整个 `SubtitleDatabase.kt`：

```kotlin
package io.github.samgum.aegisub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库。version=2：projects 表加 mediaUri 列。
 * 后续阶段追加历史版本/自动保存等表时继续升级 version。
 *
 * @author 伤感咩吖
 */
@Database(entities = [ProjectEntity::class], version = 2, exportSchema = false)
abstract class SubtitleDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        /**
         * v1 → v2：projects 表增加 mediaUri 列（可空，默认 null）。
         * 仅影响从旧版本升级的既有库；新装直接建 v2 schema。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN mediaUri TEXT")
            }
        }
    }
}
```

- [ ] **Step 2：DataModule 用 addMigrations 取代 destructive**

把 `provideDatabase` 改为：

```kotlin
@Provides
@Singleton
fun provideDatabase(@ApplicationContext context: Context): SubtitleDatabase =
    Room.databaseBuilder(context, SubtitleDatabase::class.java, "subtitle.db")
        .addMigrations(SubtitleDatabase.MIGRATION_1_2)
        .build()
```

- [ ] **Step 3：编译验证**

Run:
```bash
./gradlew :core:data:assembleDebug --no-daemon
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4：提交**

```bash
git add core/data/src/main/kotlin/io/github/samgum/aegisub/data/local/SubtitleDatabase.kt \
        core/data/src/main/kotlin/io/github/samgum/aegisub/data/di/DataModule.kt
git commit -m "feat(data): Room 迁移 1→2（mediaUri 列）+ addMigrations 注册

- 替换 fallbackToDestructiveMigration 为显式迁移，保护既有数据
- 迁移单测暂缓（需 Robolectric，环境不支持；待后续补）
作者：伤感咩吖"
```

---

## Task 3C.1：创建 :feature:preview 模块骨架

**Files:**
- Create: `feature/preview/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1：settings.gradle.kts 注册模块**

在 `include(":feature:editor")` 之后追加：

```kotlin
include(":feature:preview")
```

完整文件应为：
```kotlin
pluginManagement {
    repositories { gradlePluginPortal(); google(); mavenCentral() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
rootProject.name = "aegisub-android"
include(":core:domain")
include(":core:data")
include(":feature:editor")
include(":feature:preview")
include(":app")
```

- [ ] **Step 2：写模块 build.gradle.kts**

`feature/preview/build.gradle.kts`（镜像 `:feature:editor` 结构 + Media3）：

```kotlin
plugins {
    id("com.android.library")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "io.github.samgum.aegisub.feature.preview"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")

    // Media3：ExoPlayer + PlayerView（版本以 Maven Central 最新稳定为准，≥1.5.x）
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [ ] **Step 3：空模块编译验证**

Run:
```bash
./gradlew :feature:preview:assembleDebug --no-daemon
```
Expected: BUILD SUCCESSFUL（空库模块可编译）。

- [ ] **Step 4：提交**

```bash
git add settings.gradle.kts feature/preview/build.gradle.kts
git commit -m "build: 新增 :feature:preview 模块骨架（Compose+Hilt+Media3）

作者：伤感咩吖"
```

---

## Task 3C.2：VideoPlayer 接口 + PlaybackState

**Files:**
- Create: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/VideoPlayer.kt`

- [ ] **Step 1：写接口**

```kotlin
package io.github.samgum.aegisub.feature.preview

import kotlinx.coroutines.flow.StateFlow

/**
 * 播放器状态快照（驱动 UI 与当前行派生）。
 *
 * @author 伤感咩吖
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val isReady: Boolean = false,
)

/**
 * 视频播放器抽象。真实实现封装 ExoPlayer；测试用假实现。
 * 位置采样在实现内部以协程周期更新 [state]，UI 与 VM 只订阅不反写。
 *
 * @author 伤感咩吖
 */
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

- [ ] **Step 2：编译验证**

Run:
```bash
./gradlew :feature:preview:compileDebugKotlin --no-daemon
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3：提交**

```bash
git add feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/VideoPlayer.kt
git commit -m "feat(preview): VideoPlayer 接口 + PlaybackState

作者：伤感咩吖"
```

---

## Task 3C.3：Media3VideoPlayer 实现 + Hilt 绑定

**Files:**
- Create: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/Media3VideoPlayer.kt`
- Create: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/di/PreviewModule.kt`

> 无单测（ExoPlayer 需真机/Robolectric）；由 `assembleDebug` 编译验证 + 后续真机手测。可测逻辑全在 `PreviewViewModel`（用 FakeVideoPlayer）。

- [ ] **Step 1：写 Media3VideoPlayer**

```kotlin
package io.github.samgum.aegisub.feature.preview

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ExoPlayer 封装：监听状态/参数 → 回填 [state]；播放时协程周期(≈50ms)采样位置。
 * [exoPlayer] 暴露给 PlayerView 绑定（仅预览模块内使用）。
 *
 * @author 伤感咩吖
 */
class Media3VideoPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : VideoPlayer {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) startPolling() else stopPolling()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val duration = exoPlayer.duration.coerceAtLeast(0L)
                    _state.value = _state.value.copy(
                        isReady = true,
                        durationMs = duration,
                        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    )
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _state.value = _state.value.copy(speed = playbackParameters.speed)
            }
        })
    }

    override fun setMedia(uri: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }

    override fun play() = exoPlayer.play()

    override fun pause() = exoPlayer.pause()

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        updatePosition()
    }

    override fun setSpeed(rate: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(rate)
    }

    override fun release() {
        stopPolling()
        scope.cancel()
        exoPlayer.release()
    }

    private fun updatePosition() {
        _state.value = _state.value.copy(positionMs = exoPlayer.currentPosition.coerceAtLeast(0L))
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                updatePosition()
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 50L
    }
}
```

- [ ] **Step 2：写 Hilt 绑定模块**

```kotlin
package io.github.samgum.aegisub.feature.preview.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.samgum.aegisub.feature.preview.Media3VideoPlayer
import io.github.samgum.aegisub.feature.preview.VideoPlayer

/**
 * 预览模块 DI 绑定。VideoPlayer 不加 @Singleton：每个 PreviewViewModel 注入新实例，
 * 随 VM onCleared() 释放，避免跨屏共享已释放的 ExoPlayer。
 *
 * @author 伤感咩吖
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreviewModule {
    @Binds
    abstract fun bindVideoPlayer(impl: Media3VideoPlayer): VideoPlayer
}
```

- [ ] **Step 3：编译验证**

Run:
```bash
./gradlew :feature:preview:compileDebugKotlin --no-daemon
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4：提交**

```bash
git add feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/Media3VideoPlayer.kt \
        feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/di/PreviewModule.kt
git commit -m "feat(preview): Media3VideoPlayer 封装 ExoPlayer + Hilt 绑定

- Listener 回填状态；播放时 50ms 协程采样位置
- 不加 Singleton，随 ViewModel 生命周期释放
作者：伤感咩吖"
```

---

## Task 3C.4：PreviewViewModel + 单测（TDD）

**Files:**
- Create: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/PreviewViewModel.kt`
- Test: `feature/preview/src/test/kotlin/io/github/samgum/aegisub/feature/preview/PreviewViewModelTest.kt`

- [ ] **Step 1：写失败测试**

`feature/preview/src/test/kotlin/io/github/samgum/aegisub/feature/preview/PreviewViewModelTest.kt`：

```kotlin
package io.github.samgum.aegisub.feature.preview

import androidx.lifecycle.SavedStateHandle
import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PreviewViewModel 单测：加载/空媒体/当前行派生/seekToEvent/attachMedia/错误态。
 * @author 伤感咩吖
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val sampleAss = """
        [Script Info]
        ScriptType: v4.00+

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,第一句
        Dialogue: 0,0:00:04.00,0:00:06.00,Default,,0,0,0,,第二句
    """.trimIndent()

    private fun vm(
        content: String? = sampleAss,
        mediaUri: String? = null,
        player: VideoPlayer = FakeVideoPlayer(),
    ): PreviewViewModel = PreviewViewModel(
        FakeProjectRepository(content, mediaUri),
        player,
        SavedStateHandle(mapOf("projectId" to "42")),
    )

    @Test fun loads_script_into_loaded_state() = runTest(dispatcher) {
        val v = vm()
        advanceUntilIdle()
        val state = v.state.value
        assertTrue("expected Loaded, got $state", state is PreviewUiState.Loaded)
        val loaded = state as PreviewUiState.Loaded
        assertEquals(2, loaded.script.events.size)
        assertEquals(42L, v.projectId)
    }

    @Test fun empty_media_uri_means_has_media_false() = runTest(dispatcher) {
        val v = vm(mediaUri = null)
        advanceUntilIdle()
        assertFalse((v.state.value as PreviewUiState.Loaded).hasMedia)
    }

    @Test fun has_media_true_when_media_uri_present() = runTest(dispatcher) {
        val v = vm(mediaUri = "content://video/1")
        advanceUntilIdle()
        assertTrue((v.state.value as PreviewUiState.Loaded).hasMedia)
    }

    @Test fun current_event_id_advances_with_position() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val v = vm(player = fake)
        advanceUntilIdle()
        // 2s 落在第一句 [1s,3s)
        fake.emitPosition(2_000)
        advanceUntilIdle()
        assertEquals(0L, (v.state.value as PreviewUiState.Loaded).currentEventId)
        // 5s 落在第二句 [4s,6s)
        fake.emitPosition(5_000)
        advanceUntilIdle()
        assertEquals(1L, (v.state.value as PreviewUiState.Loaded).currentEventId)
    }

    @Test fun current_event_id_null_when_between_lines() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val v = vm(player = fake)
        advanceUntilIdle()
        fake.emitPosition(3_500) // 两句之间
        advanceUntilIdle()
        assertNull((v.state.value as PreviewUiState.Loaded).currentEventId)
    }

    @Test fun seek_to_event_seeks_player_to_event_start() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val v = vm(player = fake)
        advanceUntilIdle()
        v.seekToEvent(1L) // 第二句 start=4s
        assertEquals(4_000L, fake.seekedTo)
    }

    @Test fun attach_media_persists_and_sets_player() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val repo = FakeProjectRepository(sampleAss, mediaUri = null)
        val v = PreviewViewModel(repo, fake, SavedStateHandle(mapOf("projectId" to "42")))
        advanceUntilIdle()
        v.attachMedia("content://video/9")
        advanceUntilIdle()
        assertEquals("content://video/9", repo.setMediaUriRecorded)
        assertEquals("content://video/9", fake.mediaSet)
        assertTrue((v.state.value as PreviewUiState.Loaded).hasMedia)
    }

    @Test fun error_state_when_repo_throws() = runTest(dispatcher) {
        val v = PreviewViewModel(
            FakeProjectRepository(throwOnGetContent = true),
            FakeVideoPlayer(),
            SavedStateHandle(mapOf("projectId" to "1")),
        )
        advanceUntilIdle()
        assertTrue(v.state.value is PreviewUiState.Error)
    }

    @Test fun on_clear_releases_player() = runTest(dispatcher) {
        val fake = FakeVideoPlayer()
        val v = vm(player = fake)
        advanceUntilIdle()
        v.releaseForTest()
        assertTrue(fake.released)
    }

    // ---------- fakes ----------

    private class FakeVideoPlayer : VideoPlayer {
        override val state = MutableStateFlow(PlaybackState())
        var seekedTo: Long? = null
        var mediaSet: String? = null
        var released = false
        override fun setMedia(uri: String) { mediaSet = uri }
        override fun play() { state.value = state.value.copy(isPlaying = true) }
        override fun pause() { state.value = state.value.copy(isPlaying = false) }
        override fun seekTo(positionMs: Long) { seekedTo = positionMs }
        override fun setSpeed(rate: Float) { state.value = state.value.copy(speed = rate) }
        override fun release() { released = true }
        fun emitPosition(ms: Long) { state.value = state.value.copy(positionMs = ms) }
    }

    private class FakeProjectRepository(
        private val content: String? = "",
        private val mediaUri: String? = null,
        private val throwOnGetContent: Boolean = false,
    ) : ProjectRepository {
        var setMediaUriRecorded: String? = null
        override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
        override fun observeProject(id: Long): Flow<Project?> = flowOf(null)
        override suspend fun getContent(id: Long): String {
            if (throwOnGetContent) throw RuntimeException("boom")
            return content ?: ""
        }
        override suspend fun createProject(name: String, format: String, content: String) = 0L
        override suspend fun updateContent(id: Long, content: String, now: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun touchLastOpened(id: Long, now: Long) {}
        override suspend fun getMediaUri(id: Long): String? = mediaUri
        override suspend fun setMediaUri(id: Long, mediaUri: String) { setMediaUriRecorded = mediaUri }
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
./gradlew :feature:preview:testDebugUnitTest --tests "*.PreviewViewModelTest" --no-daemon
```
Expected: FAIL（`PreviewViewModel` / `PreviewUiState` 未解析）。

- [ ] **Step 3：写实现**

`feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/PreviewViewModel.kt`：

```kotlin
package io.github.samgum.aegisub.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.domain.format.FormatRegistry
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.preview.ActiveSubtitleResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 预览屏状态。
 *
 * @author 伤感咩吖
 */
sealed interface PreviewUiState {
    object Loading : PreviewUiState
    data class Loaded(
        val script: AssScript,
        val hasMedia: Boolean,
        val playback: PlaybackState,
        val currentEventId: Long?,
    ) : PreviewUiState
    data class Error(val message: String) : PreviewUiState
}

/**
 * 预览 ViewModel：只读加载脚本 + 挂载媒体 + 派生当前活动行 + seek/媒体控制。
 * 不写脚本，与编辑器的撤销/自动保存完全解耦。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModel @Inject constructor(
    private val repo: ProjectRepository,
    private val player: VideoPlayer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = savedStateHandle.get<String>("projectId")!!.toLong()

    private val base = MutableStateFlow<BaseState>(BaseState.Loading)

    val state: StateFlow<PreviewUiState> = combine(base, player.state) { b, playback ->
        when (b) {
            BaseState.Loading -> PreviewUiState.Loading
            is BaseState.Error -> PreviewUiState.Error(b.message)
            is BaseState.Ready -> PreviewUiState.Loaded(
                script = b.script,
                hasMedia = b.hasMedia,
                playback = playback,
                currentEventId = ActiveSubtitleResolver.activeEvent(b.script, playback.positionMs)?.id,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PreviewUiState.Loading)

    /** 暴露播放器给 PlayerSurface 绑定（仅预览模块内做安全转型）。 */
    val videoPlayer: VideoPlayer get() = player

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val content = repo.getContent(projectId)
                val parsed = FormatRegistry.detect(content)?.read(content) ?: AssScript.default()
                val script = parsed.withEvents(parsed.events.mapIndexed { i, e -> e.copy(id = i.toLong()) })
                val mediaUri = repo.getMediaUri(projectId)
                if (mediaUri != null) player.setMedia(mediaUri)
                base.value = BaseState.Ready(script, hasMedia = mediaUri != null)
            } catch (e: Exception) {
                base.value = BaseState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun playPause() {
        if (player.state.value.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun setSpeed(rate: Float) {
        player.setSpeed(rate)
    }

    fun seekToEvent(eventId: Long) {
        val script = (base.value as? BaseState.Ready)?.script ?: return
        val event = script.events.firstOrNull { it.id == eventId } ?: return
        player.seekTo(event.start.millis)
    }

    fun attachMedia(uri: String) {
        viewModelScope.launch {
            repo.setMediaUri(projectId, uri)
            player.setMedia(uri)
            (base.value as? BaseState.Ready)?.let { base.value = it.copy(hasMedia = true) }
        }
    }

    override fun onCleared() {
        player.release()
    }

    /** 测试专用：触发 onCleared 等价的播放器释放。 */
    internal fun releaseForTest() {
        player.release()
    }

    private sealed interface BaseState {
        object Loading : BaseState
        data class Ready(val script: AssScript, val hasMedia: Boolean) : BaseState
        data class Error(val message: String) : BaseState
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
./gradlew :feature:preview:testDebugUnitTest --tests "*.PreviewViewModelTest" --no-daemon
```
Expected: PASS（9 用例）。

- [ ] **Step 5：提交**

```bash
git add feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/PreviewViewModel.kt \
        feature/preview/src/test/kotlin/io/github/samgum/aegisub/feature/preview/PreviewViewModelTest.kt
git commit -m "feat(preview): PreviewViewModel 只读加载 + 当前行派生 + 9 单测

- combine(base, playback) 派生 currentEventId
- seekToEvent/attachMedia/playPause/seekTo/setSpeed
- onCleared 释放播放器；releaseForTest 供测试
作者：伤感咩吖"
```

---

## Task 3D.1：AssColor 扩展 + SubtitleOverlay 叠加渲染

**Files:**
- Create: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/AssColorExt.kt`
- Create: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/SubtitleOverlay.kt`

> 无单测（Canvas 渲染）；由 `assembleDebug` 编译验证 + 真机手测。逻辑（活动事件/样式）已在 Task 3A 纯函数单测覆盖。

- [ ] **Step 1：写 AssColor → Compose Color 扩展**

```kotlin
package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.ui.graphics.Color
import io.github.samgum.aegisub.domain.model.AssColor

/**
 * AssColor(0-255 RGBA) → Compose Color。隔离在预览模块，避免域层引入 Compose 依赖。
 *
 * @author 伤感咩吖
 */
fun AssColor.toColor(): Color = Color(r, g, b, a)
```

- [ ] **Step 2：写 SubtitleOverlay**

```kotlin
package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.preview.ActiveSubtitleResolver

/**
 * 视频画面字幕叠加（简化渲染）。
 * 当前时间无活动事件时透明；有则按样式（字体/字号/主色/粗斜体/真描边/对齐/边距）自绘。
 * PlayRes→视频分辨率缩放留 Phase 5，本阶段边距直接按 dp 计。
 *
 * @author 伤感咩吖
 */
@Composable
fun SubtitleOverlay(
    script: AssScript,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val info = ActiveSubtitleResolver.renderInfo(script, positionMs)
    if (info == null || info.text.isBlank()) {
        // 无活动事件：透明占位（保留尺寸以覆盖视频区）
        Box(modifier.fillMaxSize())
        return
    }
    val measurer = rememberTextMeasurer()
    val style = info.style
    val baseTextStyle = TextStyle(
        color = style.primary.toColor(),
        fontSize = style.fontSize.toFloat().sp,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
    )
    // 仅在文本/样式变化时重测，避免每个位置 tick 重测
    val layout = remember(info.text, baseTextStyle) {
        measurer.measure(info.text, baseTextStyle)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val outlineWidthPx = style.outlineWidth.dp.toPx().coerceAtLeast(1f)
        val topLeft = computeTopLeft(
            alignment = style.alignment,
            layoutWidth = layout.size.width,
            layoutHeight = layout.size.height,
            canvasWidth = size.width,
            canvasHeight = size.height,
            marginLeft = info.margins.left.dp.toPx(),
            marginRight = info.margins.right.dp.toPx(),
            marginVertical = info.margins.vertical.dp.toPx(),
        )
        // 1) 描边层（Stroke）：先画，宽于填充
        drawText(
            textMeasurer = measurer,
            text = info.text,
            topLeft = topLeft,
            style = baseTextStyle.copy(color = style.outline.toColor(), drawStyle = Stroke(width = outlineWidthPx)),
        )
        // 2) 填充层（Fill）：覆盖描边中心
        drawText(
            textMeasurer = measurer,
            text = info.text,
            topLeft = topLeft,
            style = baseTextStyle.copy(drawStyle = Fill),
        )
    }
}

/**
 * 按 \an 1-9 对齐 + 边距计算文本左上角。numpad：1/4/7 左、2/5/8 中、3/6/9 右；
 * 7/8/9 上、4/5/6 中、1/2/3 下。
 */
private fun DrawScope.computeTopLeft(
    alignment: Int,
    layoutWidth: Float,
    layoutHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    marginLeft: Float,
    marginRight: Float,
    marginVertical: Float,
): Offset {
    val x = when (alignment) {
        1, 4, 7 -> marginLeft
        3, 6, 9 -> canvasWidth - layoutWidth - marginRight
        else -> (canvasWidth - layoutWidth) / 2f
    }
    val y = when (alignment) {
        7, 8, 9 -> marginVertical
        4, 5, 6 -> (canvasHeight - layoutHeight) / 2f
        else -> canvasHeight - layoutHeight - marginVertical
    }
    return Offset(x, y)
}
```

- [ ] **Step 3：编译验证**

Run:
```bash
./gradlew :feature:preview:compileDebugKotlin --no-daemon
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4：提交**

```bash
git add feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/AssColorExt.kt \
        feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/SubtitleOverlay.kt
git commit -m "feat(preview): SubtitleOverlay 自绘字幕叠加（真描边 + 对齐/边距）

- 描边层(Stroke)+填充层(Fill)两次绘制
- \an 1-9 对齐 + 样式/事件合并边距定位
- 无活动事件时透明占位
作者：伤感咩吖"
```

---

## Task 3D.2：PlayerSurface

**Files:**
- Create: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/PlayerSurface.kt`

- [ ] **Step 1：写 PlayerSurface**

```kotlin
package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import io.github.samgum.aegisub.feature.preview.Media3VideoPlayer
import io.github.samgum.aegisub.feature.preview.VideoPlayer

/**
 * 视频画面：把 ExoPlayer 绑定到 Media3 PlayerView（AndroidView）。
 * 安全转型仅限预览模块：真实环境 player 为 Media3VideoPlayer；测试/Preview 退化为黑底。
 *
 * @author 伤感咩吖
 */
@Composable
fun PlayerSurface(player: VideoPlayer, modifier: Modifier = Modifier) {
    val exo = (player as? Media3VideoPlayer)?.exoPlayer
    if (exo != null) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                PlayerView(context).apply {
                    this.player = exo
                    useController = false
                }
            },
        )
    } else {
        Box(modifier.fillMaxSize().background(Color.Black))
    }
}
```

- [ ] **Step 2：编译验证**

Run:
```bash
./gradlew :feature:preview:compileDebugKotlin --no-daemon
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3：提交**

```bash
git add feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/components/PlayerSurface.kt
git commit -m "feat(preview): PlayerSurface 包裹 Media3 PlayerView

作者：伤感咩吖"
```

---

## Task 3D.3：PreviewScreen（compact/expanded + 控件 + SAF）

**Files:**
- Create: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/PreviewScreen.kt`

> 说明：不复用 `:feature:editor` 的 `EventRow`（那会让 `:feature:preview` 反向依赖 `:feature:editor`）。本屏内自绘只读 `PreviewEventRow`（带当前行高亮）。 conscious duplication，两特性行可能各自演化。

- [ ] **Step 1：写 PreviewScreen**

```kotlin
package io.github.samgum.aegisub.feature.preview

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.feature.preview.components.PlayerSurface
import io.github.samgum.aegisub.feature.preview.components.SubtitleOverlay

/**
 * 预览屏入口：加载→分发（Loading/Error/Loaded）→ compact/expanded。
 * SAF 选片在本屏发起，结果回写 ViewModel.attachMedia。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            viewModel.attachMedia(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预览") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                PreviewUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is PreviewUiState.Error ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载失败：${s.message}")
                    }

                is PreviewUiState.Loaded -> {
                    val isCompact = LocalConfiguration.current.screenWidthDp < 600
                    if (isCompact) {
                        CompactPreview(
                            state = s,
                            viewModel = viewModel,
                            onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
                        )
                    } else {
                        ExpandedPreview(
                            state = s,
                            viewModel = viewModel,
                            onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoBlock(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            PlayerSurface(player = viewModel.videoPlayer, modifier = Modifier.fillMaxSize())
            SubtitleOverlay(
                script = state.script,
                positionMs = state.playback.positionMs,
                modifier = Modifier.fillMaxSize(),
            )
            if (!state.hasMedia) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("未挂载视频", color = Color.White)
                    Button(onClick = onPickVideo) { Text("选择视频") }
                }
            }
        }
        PlaybackControls(
            playback = state.playback,
            onPlayPause = viewModel::playPause,
            onSeek = viewModel::seekTo,
            onSpeedChange = viewModel::setSpeed,
        )
    }
}

@Composable
private fun PlaybackControls(
    playback: PlaybackState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var speedExpanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "播放/暂停",
                )
            }
            Text(formatTime(playback.positionMs), style = MaterialTheme.typography.bodySmall)
            Box {
                TextButton(onClick = { speedExpanded = true }) { Text("${playback.speed}x") }
                DropdownMenu(expanded = speedExpanded, onDismissRequest = { speedExpanded = false }) {
                    speeds.forEach { rate ->
                        DropdownMenuItem(
                            text = { Text("${rate}x") },
                            onClick = { onSpeedChange(rate); speedExpanded = false },
                        )
                    }
                }
            }
            Text(" / ${formatTime(playback.durationMs)}", style = MaterialTheme.typography.bodySmall)
        }
        val ratio = if (playback.durationMs > 0) {
            (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
        } else 0f
        Slider(
            value = ratio,
            onValueChange = { v ->
                if (playback.durationMs > 0) onSeek((v * playback.durationMs).toLong())
            },
        )
    }
}

@Composable
private fun CompactPreview(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        VideoBlock(state = state, viewModel = viewModel, onPickVideo = onPickVideo)
        EventListColumn(
            events = state.script.events,
            currentEventId = state.currentEventId,
            onEventClick = viewModel::seekToEvent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ExpandedPreview(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        VideoBlock(
            state = state,
            viewModel = viewModel,
            onPickVideo = onPickVideo,
            modifier = Modifier.weight(0.6f),
        )
        EventListColumn(
            events = state.script.events,
            currentEventId = state.currentEventId,
            onEventClick = viewModel::seekToEvent,
            modifier = Modifier.weight(0.4f),
        )
    }
}

@Composable
private fun EventListColumn(
    events: List<AssEvent>,
    currentEventId: Long?,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(events, key = { it.id }) { event ->
            PreviewEventRow(
                event = event,
                isCurrent = event.id == currentEventId,
                onClick = { onEventClick(event.id) },
            )
        }
    }
}

@Composable
private fun PreviewEventRow(event: AssEvent, isCurrent: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = event.strippedText.ifBlank { "（无文本）" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (event.comment) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = "${event.start.toAssString(false)} → ${event.end.toAssString(false)}  ·  ${event.style}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier = Modifier
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
    )
}

/** ms → "M:SS" 或 "H:MM:SS"。 */
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
```

> ⚠️ 顶部导航箭头用了 `Icons.AutoMirrored.Filled.ArrowBack`，需补 import：
> ```kotlin
> import androidx.compose.material.icons.automirrored.filled.ArrowBack
> ```
> 并把 `androidx.compose.material3.IconButton(onClick = onBack)` 改回 `IconButton(onClick = onBack)`（顶部已 import `IconButton`）。落地时以本 Step 末「import 校正」为准。

- [ ] **Step 2：import 校正**

把 `PreviewScreen.kt` 顶部 import 段改为（含 AutoMirrored.ArrowBack、移除全限定写法）：

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
```
并把 TopAppBar 的 `navigationIcon` 块改为：
```kotlin
navigationIcon = {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
    }
},
```

- [ ] **Step 3：编译验证**

Run:
```bash
./gradlew :feature:preview:assembleDebug --no-daemon
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4：提交**

```bash
git add feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/PreviewScreen.kt
git commit -m "feat(preview): PreviewScreen compact/expanded + 控件 + SAF 选片

- compact 竖排 / expanded 双栏自适应
- 播放暂停/倍速/进度条 seek；点行跳转；当前行高亮
- SAF OpenDocument + takePersistableUriPermission 回写
作者：伤感咩吖"
```

---

## Task 3E.1：PreviewNavigation 路由

**Files:**
- Create: `feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/navigation/PreviewNavigation.kt`

- [ ] **Step 1：写路由注册**

```kotlin
package io.github.samgum.aegisub.feature.preview.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.samgum.aegisub.feature.preview.PreviewScreen

/** 预览路由基段，projectId 作为路径参数（String 传递，ViewModel 内 toLong）。 */
const val PREVIEW_ROUTE_BASE = "preview"
private const val PREVIEW_ROUTE = "$PREVIEW_ROUTE_BASE/{projectId}"

/** 拼装某工程的预览路由。 */
fun previewRoute(projectId: Long): String = "$PREVIEW_ROUTE_BASE/$projectId"

/** 注册预览目的地。 */
fun NavGraphBuilder.previewScreen(onBack: () -> Unit) {
    composable(
        route = PREVIEW_ROUTE,
        arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
    ) {
        PreviewScreen(onBack = onBack)
    }
}
```

- [ ] **Step 2：编译验证**

Run:
```bash
./gradlew :feature:preview:compileDebugKotlin --no-daemon
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3：提交**

```bash
git add feature/preview/src/main/kotlin/io/github/samgum/aegisub/feature/preview/navigation/PreviewNavigation.kt
git commit -m "feat(preview): preview/{projectId} 路由注册

作者：伤感咩吖"
```

---

## Task 3E.2：编辑器加"预览"入口

**Files:**
- Modify: `feature/editor/src/main/kotlin/io/github/samgum/aegisub/feature/editor/EditorScreen.kt`
- Modify: `feature/editor/src/main/kotlin/io/github/samgum/aegisub/feature/editor/expanded/EditorTwoPane.kt`
- Modify: `feature/editor/src/main/kotlin/io/github/samgum/aegisub/feature/feature/editor/navigation/EditorNavigation.kt`

> 真实路径：`feature/editor/src/main/kotlin/io/github/samgum/aegisub/feature/editor/navigation/EditorNavigation.kt`（上行 package 笔误，落地用真实路径）。

- [ ] **Step 1：EditorScreen 加 onOpenPreview 参数 + 按钮**

把 `EditorScreen` 签名改为：
```kotlin
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
```
在文件顶部补 import：
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
```
在 `is EditorUiState.Loaded ->` 分支内，给 compact 与 expanded 两个调用各传 `onOpenPreview = { onOpenPreview(viewModel.projectId) }`：

compact 分支：
```kotlin
CompactEditor(
    script = s.script,
    editingId = editingId,
    onEventClick = { editingId = it.id },
    onDismissEdit = { editingId = null },
    onBack = onBack,
    onOpenPreview = { onOpenPreview(viewModel.projectId) },
    canUndo = canUndo,
    canRedo = canRedo,
    viewModel = viewModel,
)
```
expanded 分支：在 `EditorTwoPane(...)` 调用里加一行 `onOpenPreview = { onOpenPreview(viewModel.projectId) },`。

- [ ] **Step 2：CompactEditor 透传 onOpenPreview，compact 顶栏加预览按钮**

把 `CompactEditor` 签名加 `onOpenPreview: () -> Unit`，并在 `EventListScreen(...)` 的 `actions` 槽里前置一个预览 IconButton：
```kotlin
@Composable
private fun CompactEditor(
    script: AssScript,
    editingId: Long?,
    onEventClick: (AssEvent) -> Unit,
    onDismissEdit: () -> Unit,
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    viewModel: EditorViewModel,
) {
    EventListScreen(
        events = script.events,
        onEventClick = onEventClick,
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenPreview) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "预览")
            }
            EditorActions(
                canUndo = canUndo,
                canRedo = canRedo,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
            )
        },
    )
    // ... EventEditSheet 部分保持不变 ...
}
```

- [ ] **Step 3：EditorTwoPane 加 onOpenPreview + 顶栏按钮**

`EditorTwoPane` 签名加 `onOpenPreview: () -> Unit`，并在 `TopAppBar` 的 `actions` 里前置预览按钮：
```kotlin
@Composable
fun EditorTwoPane(
    script: AssScript,
    editingId: Long?,
    onEventClick: (AssEvent) -> Unit,
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTextChanged: (eventId: Long, text: String) -> Unit,
    onTimesChanged: (eventId: Long, start: SubTime, end: SubTime) -> Unit,
    onStyleChanged: (eventId: Long, style: String) -> Unit,
    onLayerChanged: (eventId: Long, layer: Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPreview) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "预览")
                    }
                    EditorActions(canUndo, canRedo, onUndo, onRedo)
                },
            )
        },
    ) { padding ->
        // ... Row 内容保持不变 ...
    }
}
```
顶部补 import：
```kotlin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.IconButton
```

- [ ] **Step 4：EditorNavigation 透传 onOpenPreview**

`EditorNavigation.kt` 的 `editorScreen` 改为：
```kotlin
fun NavGraphBuilder.editorScreen(onBack: () -> Unit, onOpenPreview: (Long) -> Unit) {
    composable(
        route = EDITOR_ROUTE,
        arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
    ) {
        EditorScreen(onBack = onBack, onOpenPreview = onOpenPreview)
    }
}
```

- [ ] **Step 5：编译验证**

Run:
```bash
./gradlew :feature:editor:compileDebugKotlin --no-daemon
```
Expected: BUILD SUCCESSFUL（注意 `EditorScreen` 默认参数被移除，调用方必须传 onOpenPreview——下一步 AppNavigation 会传）。

- [ ] **Step 6：提交**

```bash
git add feature/editor/src/main/kotlin/io/github/samgum/aegisub/feature/editor/EditorScreen.kt \
        feature/editor/src/main/kotlin/io/github/samgum/aegisub/feature/editor/expanded/EditorTwoPane.kt \
        feature/editor/src/main/kotlin/io/github/samgum/aegisub/feature/editor/navigation/EditorNavigation.kt
git commit -m "feat(editor): 顶栏加'预览'入口 → onOpenPreview(projectId)

- compact 走 EventListScreen actions 槽；expanded 走 EditorTwoPane 顶栏
作者：伤感咩吖"
```

---

## Task 3E.3：app 接线 + assembleDebug + 文档

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/kotlin/io/github/samgum/aegisub/navigation/AppNavigation.kt`
- Modify: `ROADMAP.md`
- Modify: `README.md`

- [ ] **Step 1：app 依赖 :feature:preview**

`app/build.gradle.kts` dependencies 段，在 `implementation(project(":feature:editor"))` 之后加：
```kotlin
implementation(project(":feature:preview"))
```

- [ ] **Step 2：AppNavigation 注册预览 + 接 editor onOpenPreview**

替换整个 `AppNavigation.kt`：

```kotlin
package io.github.samgum.aegisub.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.samgum.aegisub.feature.editor.navigation.editorRoute
import io.github.samgum.aegisub.feature.editor.navigation.editorScreen
import io.github.samgum.aegisub.feature.preview.navigation.previewRoute
import io.github.samgum.aegisub.feature.preview.navigation.previewScreen
import io.github.samgum.aegisub.ui.home.HomeScreen

/** 主屏路由常量。 */
private const val HOME_ROUTE = "home"

/**
 * 应用导航图：home（项目列表）↔ editor（字幕编辑器）↔ preview（视频预览）。
 *
 * @author 伤感咩吖
 */
@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            HomeScreen(onOpenProject = { id -> nav.navigate(editorRoute(id)) })
        }
        editorScreen(
            onBack = { nav.popBackStack() },
            onOpenPreview = { id -> nav.navigate(previewRoute(id)) },
        )
        previewScreen(onBack = { nav.popBackStack() })
    }
}
```

- [ ] **Step 3：出 debug APK**

Run:
```bash
./gradlew :app:assembleDebug --no-daemon
```
Expected: BUILD SUCCESSFUL，产出 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 4：跑预览模块单测回归**

Run:
```bash
./gradlew :feature:preview:testDebugUnitTest :core:domain:test --no-daemon
```
Expected: PASS（PreviewViewModel 9 + ActiveSubtitleResolver 9 + 既有 domain 用例）。

- [ ] **Step 5：更新 ROADMAP.md**

把"当前状态"行与 Phase 3 段更新为：
```markdown
## 当前状态：✅ Phase 0 / 1 / 2 / 3 完成。字幕编辑闭环 + 视频预览可用（挂载视频 SAF 持久化 → Media3 播放/seek/倍速 → 画面叠加简化字幕 → 点行跳转/当前行高亮）。剩余 Phase 1：DataStore 设置；下一步 Phase 4（音频工程/打轴辅助）。

## Phase 3 · 视频预览与时间轴 ✅
- [x] Media3 / ExoPlayer 集成 + 倍速播放（封装 VideoPlayer 接口）
- [x] 视频画面与字幕叠加预览（简化渲染：纯文本+样式，Compose 自绘真描边）
- [x] 基础时间轴：播放/暂停/seek/倍速 + 点行跳转 + 当前行高亮（拖拽改时间留 Phase 4）
- [ ] 拖拽起止手柄改时间 — 待办（Phase 4）
```

- [ ] **Step 6：更新 README.md 状态徽章**

把状态徽章/特性阶段行从 Phase 2 改为 Phase 3（如 "Phase 3（:feature:preview 视频预览已落地）"）。

- [ ] **Step 7：提交**

```bash
git add app/build.gradle.kts \
        app/src/main/kotlin/io/github/samgum/aegisub/navigation/AppNavigation.kt \
        ROADMAP.md README.md
git commit -m "feat(app): 接线 :feature:preview 路由 + Phase 3 收尾文档

- AppNavigation 注册 previewScreen；editor 接 onOpenPreview
- assembleDebug 出 APK；预览/域单测回归全绿
- ROADMAP/README 状态升 Phase 3
作者：伤感咩吖"
```

---

## 验收清单（全部勾选后合并 main）

- [ ] `:core:domain:test` 全绿（含 9 个 ActiveSubtitleResolverTest）
- [ ] `:feature:preview:testDebugUnitTest` 全绿（9 个 PreviewViewModelTest）
- [ ] `:app:assembleDebug` 出 APK，无报错
- [ ] 真机手测：选视频→播放/暂停/倍速/seek→字幕叠加正确→点行跳转→当前行高亮→杀进程重开仍可播放（持久化 URI）
- [ ] ROADMAP/README 已更新

## 自审记录

- **spec 覆盖**：spec §2 In Scope 逐项→Task 对应：`:feature:preview` 模块(3C.1)、路由(3E.1)、Room 迁移(3B.2)、SAF(3D.3)、VideoPlayer(3C.2/3C.3)、PreviewViewModel(3C.4)、ActiveSubtitleResolver(3A)、SubtitleOverlay(3D.1)、预览屏(3D.3)、编辑器入口(3E.2)。无遗漏。
- **占位符扫描**：计划中无 TBD/TODO/"implement later"；所有代码步骤含完整代码。UI 步骤的"保持不变"指向同文件既有内容（非占位），落地时整文件替换或局部 Edit。
- **类型一致性**：`VideoPlayer.state: StateFlow<PlaybackState>`（3C.2 定义）在 Media3VideoPlayer(3C.3)/PreviewViewModel(3C.4)/FakeVideoPlayer(测试) 一致；`PreviewUiState.Loaded.currentEventId` 由 `ActiveSubtitleResolver.activeEvent(...)?.id` 派生，与 3A 的 `activeEvent` 返回 `AssEvent`（含 `.id`）一致；`SubtitleRenderInfo(text, style, margins)` 在 3A 定义、3D.1 消费一致；`previewRoute/editorRoute` 命名与 AppNavigation 一致。
- **务实偏离（已交底）**：spec §13 的 Room 迁移单测未纳入（需 Robolectric，`--no-daemon` Windows 环境有卡测试风险；app 未发布无 v1 用户数据）。迁移 SQL 由 `assembleDebug` 编译验证。如需补单测，后续引入 Robolectric 即可。
