# Milestone 1 — `:core:domain` 核心域模块 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 TDD 建立纯 Kotlin/JVM、零 Android 依赖的字幕工程核心模块 `:core:domain`（时间模型、ASS 数据模型、对话体 tokenizer、ASS/SSA/SRT/TXT/LRC 编解码、撤销引擎），全部单测通过。

**Architecture:** 多模块 Gradle（本计划仅落地 `:core:domain`，纯 JVM）。所有类型不可变（`kotlinx.collections.immutable` 持久集合）；时间用微秒值类存储保证往返零精度损失；撤销引擎采用不可变 + 结构共享快照（替代会 OOM 的原版整文件快照）。参考并部分移植自 Aegisub（BSD）。

**Tech Stack:** Kotlin 2.1.0 · Gradle 8.11.1 · JDK 21 · `kotlinx-collections-immutable` 0.3.7 · JUnit Jupiter 5.11.4 · kotlin-test

**包根：** `io.github.samgum.aegisub.domain`

**Scope note（spec 对齐）：** spec §13 提到的 "SMPTE stub" 显式**延后到 Phase 3**——SMPTE 依赖帧率（VFR/Framerate），在视频模块落地前无意义，强行加 stub 只会产出无用代码。本 Milestone 的 SubTime 已预留 SMPTE 扩展点（companion 内后续追加 `toSmpte(fps)` 即可，不破坏既有签名）。

---

## 文件结构总览

```
aegisub-android/
├── settings.gradle.kts                       # 多模块设置 + foojay toolchain
├── build.gradle.kts                          # 根构建（Kotlin 插件声明）
├── gradle.properties                         # JVM/Gradle 调优
├── gradle/wrapper/gradle-wrapper.properties  # wrapper 指向 8.11.1
├── gradlew / gradlew.bat                     # wrapper 脚本
└── core/domain/
    ├── build.gradle.kts                      # Kotlin/JVM + 依赖
    └── src/
        ├── main/kotlin/io/github/samgum/aegisub/domain/
        │   ├── time/
        │   │   ├── SubTime.kt                # 微秒值类 + ASS/SRT/LRC 格式化与解析
        │   │   └── LrcTimeFormat.kt          # LRC 4 种时间格式
        │   ├── model/
        │   │   ├── AssColor.kt               # ARGB 值类 + ASS &H 解析
        │   │   ├── Margins.kt                # 左/右/纵向 边距
        │   │   ├── AssInfo.kt                # [Script Info] 键值
        │   │   ├── AssStyle.kt               # 样式 + 解析/序列化 + 对齐换算
        │   │   ├── AssEvent.kt               # 事件行 + 块解析 + 序列化
        │   │   ├── DialogueBlock.kt          # sealed 块模型 + 覆盖标签
        │   │   └── AssScript.kt              # 文件容器
        │   ├── parse/
        │   │   ├── DialogueToken.kt          # token 类型 + 数据
        │   │   └── DialogueTokenizer.kt      # tokenize() + markDrawings()
        │   ├── format/
        │   │   ├── SubtitleFormat.kt         # 接口 + ReadOptions/WriteOptions/TimePrecision
        │   │   ├── FormatRegistry.kt         # 自动检测
        │   │   ├── AssFormat.kt              # ASS 编解码（SSA 复用）
        │   │   ├── SrtFormat.kt              # SRT 编解码
        │   │   ├── TxtFormat.kt              # TXT 编解码
        │   │   └── LrcFormat.kt              # LRC 编解码（全新）
        │   └── undo/
        │       └── UndoStack.kt             # 接口 + SnapshotUndoStack 实现
        └── test/kotlin/io/github/samgum/aegisub/domain/...  # 镜像结构，各 *Test.kt
```

---

## Task 1: Gradle 多模块骨架 + `:core:domain` 空模块冒烟测试

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Create: `core/domain/build.gradle.kts`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/SmokeTest.kt`

- [ ] **Step 1: 引导 Gradle wrapper（机器未装 gradle）**

```bash
cd "d:/VS Code Project/aegisub-android"
mkdir -p gradle/wrapper
# 从 Gradle 官方仓库拉取 wrapper 组件（固定 v8.11.1）
curl -fsSL -o gradle/wrapper/gradle-wrapper.jar   https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar
curl -fsSL -o gradlew                              https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradlew
curl -fsSL -o gradlew.bat                          https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradlew.bat
chmod +x gradlew
```
> 备选：若机器已装 gradle，可执行 `gradle wrapper --gradle-version 8.11.1` 替代上面的下载。

- [ ] **Step 2: 写 wrapper properties（指向 8.11.1 发行版）**

文件 `gradle/wrapper/gradle-wrapper.properties`：

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 3: 写 `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.parallel=true
kotlin.code.style=official
```

- [ ] **Step 4: 写 `settings.gradle.kts`**

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
```

- [ ] **Step 5: 写根 `build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.1.0" apply false
}
```

- [ ] **Step 6: 写 `core/domain/build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
}

tasks.test { useJUnitPlatform() }
```

- [ ] **Step 7: 写冒烟测试（先红）**

文件 `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/SmokeTest.kt`：

```kotlin
package io.github.samgum.aegisub.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test fun build_wiring_works() {
        assertEquals(42, 40 + 2)
    }
}
```

- [ ] **Step 8: 运行测试，确认通过（验证 wrapper 与依赖可用）**

```bash
./gradlew :core:domain:test
```
Expected: `BUILD SUCCESSFUL`，1 test passed。

- [ ] **Step 9: 提交**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat core/domain/
git commit -m "build: 引入 Gradle 多模块骨架与 :core:domain 空模块"
```

---

## Task 2: AssColor（ARGB 值类 + ASS `&H` 解析）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/AssColor.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/AssColorTest.kt`

**约定**：`AssColor(r,g,b,a)` 为标准 RGBA，`a∈[0,255]`，**255=完全不透明**。ASS 序列化为 `&HAABBGGRR`，其中 ASS 的 AA 字节为「透明度」（00=不透明），故 `ASS_AA = 255 - a`。

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AssColorTest {
    @Test fun packs_argb_components() {
        val c = AssColor(0x12, 0x34, 0x56, 0xFF)
        assertEquals(0x12, c.r)
        assertEquals(0x34, c.g)
        assertEquals(0x56, c.b)
        assertEquals(0xFF, c.a)
    }
    @Test fun parses_ass_full_alpha_inverted() {
        // &H00FFFFFF  -> AA=00(完全不透明) => a=255 ; BB=FF GG=FF RR=FF => 白
        val c = AssColor.parseAss("&H00FFFFFF")
        assertEquals(255, c.r); assertEquals(255, c.g); assertEquals(255, c.b)
        assertEquals(255, c.a)
    }
    @Test fun parses_ass_short_without_alpha() {
        // &H0000FF -> BB=00 GG=00 RR=FF => 红色，alpha 默认不透明
        val c = AssColor.parseAss("&H0000FF")
        assertEquals(255, c.r); assertEquals(0, c.g); assertEquals(0, c.b)
        assertEquals(255, c.a)
    }
    @Test fun round_trips_through_ass_string() {
        val original = AssColor(10, 20, 30, 200)
        val parsed = AssColor.parseAss(original.toAssString())
        assertEquals(original.r, parsed.r)
        assertEquals(original.g, parsed.g)
        assertEquals(original.b, parsed.b)
        assertEquals(original.a, parsed.a)
    }
}
```

- [ ] **Step 2: 运行，确认编译失败（AssColor 未定义）**

```bash
./gradlew :core:domain:test --tests "*AssColorTest"
```
Expected: 编译错误 / FAIL。

- [ ] **Step 3: 实现 AssColor**

```kotlin
package io.github.samgum.aegisub.domain.model

/** 标准 RGBA 颜色，a∈[0,255]，255=完全不透明。ASS 边界会反转 alpha（00=不透明）。 */
@JvmInline
value class AssColor(val argb: Int) {
    constructor(r: Int, g: Int, b: Int, a: Int = 255) : this(
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    )
    val a: Int get() = (argb ushr 24) and 0xFF
    val r: Int get() = (argb ushr 16) and 0xFF
    val g: Int get() = (argb ushr 8) and 0xFF
    val b: Int get() = argb and 0xFF

    /** 序列化为 `&HAABBGGRR`（ASS alpha 反转：00=不透明）。 */
    fun toAssString(): String {
        val aa = (255 - a) and 0xFF
        return "&H%02X%02X%02X%02X".format(aa, b, g, r)
    }

    companion object {
        val WHITE = AssColor(255, 255, 255)
        val BLACK = AssColor(0, 0, 0)
        val RED = AssColor(255, 0, 0)
        val TRANSPARENT = AssColor(0, 0, 0, 0)

        /** 解析 `&HAABBGGRR` 或 `&HBBGGRR`（无 alpha 视为不透明）。容错：去掉 &H 与结尾 &。 */
        fun parseAss(raw: String): AssColor {
            val hex = raw.trim()
                .removePrefix("&H").removePrefix("&h")
                .removeSuffix("&").removeSuffix("H").removeSuffix("h")
                .trim()
            val padded = hex.padStart(8, '0').take(8)
            val aa = padded.substring(0, 2).toInt(16)
            val bb = padded.substring(2, 4).toInt(16)
            val gg = padded.substring(4, 6).toInt(16)
            val rr = padded.substring(6, 8).toInt(16)
            val hasAlpha = hex.length > 6
            // 短形式（<=6 位）视为完全不透明
            val opaqueA = if (hasAlpha) (255 - aa) and 0xFF else 255
            return AssColor(rr, gg, bb, opaqueA)
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
./gradlew :core:domain:test --tests "*AssColorTest"
```
Expected: 4 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/AssColor.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/AssColorTest.kt
git commit -m "feat(domain): AssColor 值类与 ASS &H 颜色编解码"
```

---

## Task 3: Margins（左/右/纵向边距值类型）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/Margins.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/MarginsTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarginsTest {
    @Test fun default_is_zero() {
        assertEquals(0, Margins.ZERO.left)
        assertEquals(0, Margins.ZERO.right)
        assertEquals(0, Margins.ZERO.vertical)
    }
    @Test fun equality_is_structural() {
        assertEquals(Margins(1, 2, 3), Margins(1, 2, 3))
        assertTrue(Margins(1, 2, 3) != Margins(0, 2, 3))
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*MarginsTest"
```

- [ ] **Step 3: 实现**

```kotlin
package io.github.samgum.aegisub.domain.model

/** ASS 三段式边距：左 / 右 / 纵向（对齐 C++ std::array<int,3> Margin）。 */
data class Margins(val left: Int = 0, val right: Int = 0, val vertical: Int = 0) {
    companion object { val ZERO = Margins() }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*MarginsTest"
```

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/Margins.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/MarginsTest.kt
git commit -m "feat(domain): Margins 边距值类型"
```

---

## Task 4: SubTime 核心（微秒存储、范围钳制、算术、比较）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/SubTime.kt`（仅核心，格式化留到 Task 5-7）
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/time/SubTimeCoreTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubTimeCoreTest {
    @Test fun stores_micros_and_clamps_to_range() {
        assertEquals(0, SubTime.ZERO.micros)
        assertEquals(1_000_000, SubTime.ofMicros(1_000_000).micros)
        // 负值钳为 0
        assertEquals(0, SubTime.ofMicros(-5).micros)
        // 超过 10 小时钳为上限
        val tenHours = 10L * 60 * 60 * 1_000_000
        assertEquals(tenHours, SubTime.ofMicros(tenHours + 999).micros)
    }
    @Test fun factory_conversions() {
        assertEquals(5_000_000, SubTime.ofMillis(5_000).micros)
        assertEquals(50_000, SubTime.ofCentiseconds(5).micros) // 5cs = 50ms
    }
    @Test fun arithmetic_clamps() {
        val a = SubTime.ofMillis(3_000)
        val b = SubTime.ofMillis(1_500)
        assertEquals(4_500_000, (a + b).micros)
        assertEquals(1_500_000, (a - b).micros)
        // 不允许为负
        assertEquals(0, (b - a).micros)
    }
    @Test fun comparison() {
        assertTrue(SubTime.ofMillis(1) < SubTime.ofMillis(2))
        assertTrue(SubTime.ofMillis(2) > SubTime.ofMillis(1))
        assertEquals(0, SubTime.ofMillis(5).compareTo(SubTime.ofMillis(5)))
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*SubTimeCoreTest"
```

- [ ] **Step 3: 实现 SubTime 核心**

文件 `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/SubTime.kt`：

```kotlin
package io.github.samgum.aegisub.domain.time

/**
 * 时间值类，内部以微秒（10^-6s）存储，高于 ASS 厘秒与 LRC/SRT 毫秒，保证往返零精度损失。
 * 范围 [0, 10 小时]（对齐 Aegisub）。格式化/解析方法见后续扩展。
 */
@JvmInline
value class SubTime private constructor(val micros: Long) : Comparable<SubTime> {

    val millis: Long get() = micros / 1_000

    operator fun plus(other: SubTime): SubTime = ofMicros(micros + other.micros)
    operator fun minus(other: SubTime): SubTime = ofMicros(micros - other.micros)
    override fun compareTo(other: SubTime): Int = micros.compareTo(other.micros)
    override fun toString(): String = "SubTime(${micros}µs)"

    companion object {
        const val MAX_MICROS: Long = 10L * 60 * 60 * 1_000_000 // 10h
        val ZERO: SubTime = SubTime(0)

        fun ofMicros(v: Long): SubTime = SubTime(v.coerceIn(0, MAX_MICROS))
        fun ofMillis(v: Long): SubTime = ofMicros(v * 1_000)
        fun ofCentiseconds(v: Long): SubTime = ofMicros(v * 10_000)
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*SubTimeCoreTest"
```
Expected: 4 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/SubTime.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/time/SubTimeCoreTest.kt
git commit -m "feat(domain): SubTime 微秒值类核心（存储/钳制/算术/比较）"
```

---

## Task 5: SubTime ASS 格式化与容错解析

**Files:**
- Modify: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/SubTime.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/time/SubTimeAssFormatTest.kt`

ASS 格式：`H:MM:SS.cc`（厘秒，2 位）或 `H:MM:SS.mmm`（毫秒，3 位，msPrecision）。解析容错：接受 `.`/`,` 分隔，多余冒号按 Aegisub 算法折叠。

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals

class SubTimeAssFormatTest {
    @Test fun formats_ass_centisecond() {
        // 1m23.45s = 83.45s = 83450ms = 83_450_000µs
        val t = SubTime.ofMillis(83_450)
        assertEquals("0:01:23.45", t.toAssString(msPrecision = false))
    }
    @Test fun formats_ass_millisecond_precision() {
        val t = SubTime.ofMillis(83_456)
        assertEquals("0:01:23.456", t.toAssString(msPrecision = true))
    }
    @Test fun formats_hours_single_digit() {
        val t = SubTime.ofMillis(3 * 3_600_000L + 0)
        assertEquals("3:00:00.00", t.toAssString(msPrecision = false))
    }
    @Test fun parses_ass_centisecond() {
        assertEquals(83_450_000L, SubTime.parseAss("0:01:23.45").micros)
    }
    @Test fun parses_ass_tolerant_comma_and_extra_colons() {
        // SRT 风格 HH:MM:SS,mmm 也能被通用解析吃下
        assertEquals(83_456_000L, SubTime.parseAss("0:01:23,456").micros)
    }
}
```

- [ ] **Step 2: 运行确认失败（方法未实现）**

```bash
./gradlew :core:domain:test --tests "*SubTimeAssFormatTest"
```

- [ ] **Step 3: 在 SubTime.kt 追加方法（值类成员 + companion 解析）**

在 `SubTime` 类体内（`toString()` 之后）追加：

```kotlin
    /** ASS 文本：H:MM:SS.cc（默认厘秒）或 H:MM:SS.mmm（msPrecision）。 */
    fun toAssString(msPrecision: Boolean): String {
        if (msPrecision) {
            val total = millis
            val h = total / 3_600_000
            val m = (total % 3_600_000) / 60_000
            val s = (total % 60_000) / 1_000
            val mm = total % 1_000
            return "%d:%02d:%02d.%03d".format(h, m, s, mm)
        }
        val cs = (micros + 5_000) / 10_000 // 厘秒，5ms 半向上取整（对齐 Aegisub）
        val h = cs / 360_000
        val m = (cs % 360_000) / 6_000
        val s = (cs % 6_000) / 100
        val cc = cs % 100
        return "%d:%02d:%02d.%02d".format(h, m, s, cc)
    }
```

在 companion object 内追加通用容错解析器与 `parseAss`：

```kotlin
        /** Aegisub 风格容错解析：吃 H:MM:SS.cc / H:MM:SS.mmm / MM:SS.cc，`.`/`,` 皆可。 */
        fun parseAss(text: String): SubTime = ofMicros(parseFlexibleMs(text) * 1_000)

        /** 移植自 libaegisub Time::Time(string_view)：返回毫秒。 */
        private fun parseFlexibleMs(text: String): Long {
            var time = 0L
            var current = 0
            var afterDecimal = -1
            for (c in text) {
                when {
                    c == ':' -> { time = time * 60 + current; current = 0 }
                    c == '.' || c == ',' -> { time = (time * 60 + current) * 1000; current = 0; afterDecimal = 100 }
                    c !in '0'..'9' -> { /* 跳过非数字 */ }
                    afterDecimal < 0 -> { current = current * 10 + (c - '0') }
                    else -> { time += (c - '0').toLong() * afterDecimal; afterDecimal /= 10 }
                }
            }
            if (afterDecimal < 0) time = (time * 60 + current) * 1000
            return time
        }
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*SubTimeAssFormatTest"
```
Expected: 5 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/SubTime.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/time/SubTimeAssFormatTest.kt
git commit -m "feat(domain): SubTime ASS 格式化与容错解析（移植 libaegisub 算法）"
```

---

## Task 6: SubTime SRT 格式化与解析

**Files:**
- Modify: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/SubTime.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/time/SubTimeSrtFormatTest.kt`

SRT 格式：`HH:MM:SS,mmm`（2 位小时、逗号、3 位毫秒，不取整）。

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals

class SubTimeSrtFormatTest {
    @Test fun formats_srt() {
        assertEquals("00:01:23,456", SubTime.ofMillis(83_456).toSrtString())
        assertEquals("03:00:00,000", SubTime.ofMillis(3 * 3_600_000L).toSrtString())
    }
    @Test fun parses_srt() {
        assertEquals(83_456_000L, SubTime.parseSrt("00:01:23,456").micros)
    }
    @Test fun round_trip_srt() {
        val t = SubTime.ofMillis(7_123)
        assertEquals(t.micros, SubTime.parseSrt(t.toSrtString()).micros)
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*SubTimeSrtFormatTest"
```

- [ ] **Step 3: 在 SubTime 追加 SRT 方法**

类体内追加：

```kotlin
    /** SRT 文本：HH:MM:SS,mmm（不取整，截断到毫秒）。 */
    fun toSrtString(): String {
        val total = millis
        val h = total / 3_600_000
        val m = (total % 3_600_000) / 60_000
        val s = (total % 60_000) / 1_000
        val mm = total % 1_000
        return "%02d:%02d:%02d,%03d".format(h, m, s, mm)
    }
```

companion 内追加：

```kotlin
        fun parseSrt(text: String): SubTime = ofMicros(parseFlexibleMs(text) * 1_000)
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*SubTimeSrtFormatTest"
```
Expected: 3 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/SubTime.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/time/SubTimeSrtFormatTest.kt
git commit -m "feat(domain): SubTime SRT 格式化与解析"
```

---

## Task 7: SubTime LRC 四种格式（LrcTimeFormat）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/LrcTimeFormat.kt`
- Modify: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/SubTime.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/time/SubTimeLrcFormatTest.kt`

四种：`[mm:ss.xx]` `[mm:ss.xxx]` `[mm:ss:xx]` `[mm:ss:xxx]` = 精度(厘/毫) × 分隔符(`.`/`:`)。

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals

class SubTimeLrcFormatTest {
    // 1m23.45s = 83450ms；1m23.456s = 83456ms
    @Test fun formats_four_variants() {
        val tCenti = SubTime.ofMillis(83_450)
        val tMilli = SubTime.ofMillis(83_456)
        assertEquals("[01:23.45]", tCenti.toLrcString(LrcTimeFormat.XX))
        assertEquals("[01:23.456]", tMilli.toLrcString(LrcTimeFormat.XXX))
        assertEquals("[01:23.45]", tCenti.toLrcString(LrcTimeFormat.XX_COLON))
        assertEquals("[01:23.456]", tMilli.toLrcString(LrcTimeFormat.XXX_COLON))
    }
    @Test fun parses_four_variants() {
        assertEquals(83_450_000L, SubTime.parseLrc("[01:23.45]").micros)
        assertEquals(83_456_000L, SubTime.parseLrc("[01:23.456]").micros)
        assertEquals(83_450_000L, SubTime.parseLrc("[01:23:45]").micros)
        assertEquals(83_456_000L, SubTime.parseLrc("[01:23:456]").micros)
    }
    @Test fun round_trip_all_variants() {
        val t = SubTime.ofMillis(83_456)
        LrcTimeFormat.entries.forEach {
            assertEquals(t.micros, SubTime.parseLrc(t.toLrcString(it)).micros, "failed for $it")
        }
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*SubTimeLrcFormatTest"
```

- [ ] **Step 3: 实现 LrcTimeFormat**

文件 `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/LrcTimeFormat.kt`：

```kotlin
package io.github.samgum.aegisub.domain.time

enum class LrcSeparator { DOT, COLON }
enum class LrcPrecision { CENTI, MILLI }

/** LRC 时间标签格式：精度（厘秒/毫秒）× 分隔符（./:）共四种。 */
data class LrcTimeFormat(val precision: LrcPrecision, val separator: LrcSeparator) {
    companion object {
        val XX = LrcTimeFormat(LrcPrecision.CENTI, LrcSeparator.DOT)        // [mm:ss.xx]
        val XXX = LrcTimeFormat(LrcPrecision.MILLI, LrcSeparator.DOT)       // [mm:ss.xxx]
        val XX_COLON = LrcTimeFormat(LrcPrecision.CENTI, LrcSeparator.COLON) // [mm:ss:xx]
        val XXX_COLON = LrcTimeFormat(LrcPrecision.MILLI, LrcSeparator.COLON) // [mm:ss:xxx]
    }
}
```

- [ ] **Step 4: 在 SubTime 追加 LRC 方法**

类体内追加：

```kotlin
    /** LRC 标签：[mm:ss.xx] / [mm:ss.xxx] / [mm:ss:xx] / [mm:ss:xxx]。 */
    fun toLrcString(format: LrcTimeFormat): String {
        val totalSec = micros / 1_000_000
        val mm = totalSec / 60
        val ss = totalSec % 60
        val fracMicros = micros % 1_000_000
        val sep = if (format.separator == LrcSeparator.DOT) '.' else ':'
        return when (format.precision) {
            LrcPrecision.CENTI -> {
                val cc = fracMicros / 10_000 // 厘秒
                "[%02d:%02d%s%02d]".format(mm, ss, sep, cc)
            }
            LrcPrecision.MILLI -> {
                val mmm = fracMicros / 1_000 // 毫秒
                "[%02d:%02d%s%03d]".format(mm, ss, sep, mmm)
            }
        }
    }
```

companion 内追加：

```kotlin
        /** 解析 LRC 标签 `[mm:ss.xx|xxx|:xx|:xxx]`，自动判定分隔符与精度。 */
        fun parseLrc(tag: String): SubTime {
            val inner = tag.trim().removeSurrounding("[", "]")
            val firstColon = inner.indexOf(':')
            require(firstColon > 0) { "Invalid LRC tag: $tag" }
            val mm = inner.substring(0, firstColon).toLong()
            val rest = inner.substring(firstColon + 1) // ss<sep>ff
            val sep = if ('.' in rest) '.' else ':'
            val (ssStr, fracStr) = rest.split(sep, limit = 2)
                .let { it[0] to it.getOrElse(1) { "" } }
            val ss = ssStr.toLong()
            val frac = fracStr.ifEmpty { "0" }
            val micros = (mm * 60 + ss) * 1_000_000 + when (frac.length) {
                2 -> frac.toLong() * 10_000      // 厘秒
                3 -> frac.toLong() * 1_000       // 毫秒
                else -> (frac.toDouble() * 1_000_000).toLong()
            }
            return ofMicros(micros)
        }
```

- [ ] **Step 5: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*SubTimeLrcFormatTest"
```
Expected: 3 tests passed。

- [ ] **Step 6: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/LrcTimeFormat.kt core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/time/SubTime.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/time/SubTimeLrcFormatTest.kt
git commit -m "feat(domain): SubTime LRC 四种时间格式编解码"
```

---

## Task 8: AssInfo（[Script Info] 键值）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/AssInfo.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/AssInfoTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssInfoTest {
    @Test fun serializes_as_key_colon_value() {
        assertEquals("ScriptType: v4.00+", AssInfo("ScriptType", "v4.00+").toLine())
    }
    @Test fun parses_line() {
        val info = AssInfo.parse("PlayResX: 1920")
        assertEquals("PlayResX", info.key)
        assertEquals("1920", info.value)
    }
    @Test fun parse_returns_null_when_not_a_kv_line() {
        assertNull(AssInfo.parse("[Script Info]"))
        assertNull(AssInfo.parse("no colon here"))
    }
    @Test fun parse_trims_value() {
        assertEquals("Arial", AssInfo.parse("Font:   Arial   ").value)
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*AssInfoTest"
```

- [ ] **Step 3: 实现**

```kotlin
package io.github.samgum.aegisub.domain.model

/** [Script Info] 段的一条键值对。 */
data class AssInfo(val key: String, val value: String) {
    fun toLine(): String = "$key: $value"

    companion object {
        /** 解析 `Key: Value`；非键值行（段头、无冒号）返回 null。 */
        fun parse(line: String): AssInfo? {
            val idx = line.indexOf(':')
            if (idx <= 0) return null
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim()
            if (k.isEmpty() || k.startsWith("[")) return null
            return AssInfo(k, v)
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*AssInfoTest"
```
Expected: 4 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/AssInfo.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/AssInfoTest.kt
git commit -m "feat(domain): AssInfo 键值模型与解析"
```

---

## Task 9: AssStyle（模型 + 解析/序列化 + ASS↔SSA 对齐换算）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/AssStyle.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/AssStyleTest.kt`

V4+ Style 行格式（逗号分隔，23 字段）：
`Style: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AssStyleTest {
    private val sampleLine =
        "Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000," +
        "-1,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1"

    @Test fun parses_all_fields() {
        val s = AssStyle.parse(sampleLine)
        assertEquals("Default", s.name)
        assertEquals("Arial", s.font)
        assertEquals(48.0, s.fontSize)
        assertEquals(AssColor.WHITE, s.primary)
        assertEquals(AssColor.RED, s.secondary)
        assertEquals(AssColor.BLACK, s.outline)
        assertEquals(true, s.bold)
        assertEquals(false, s.italic)
        assertEquals(100.0, s.scaleX)
        assertEquals(1, s.borderStyle)
        assertEquals(2.0, s.outlineWidth)
        assertEquals(2, s.alignment)
        assertEquals(Margins(10, 10, 10), s.margins)
        assertEquals(1, s.encoding)
    }
    @Test fun round_trips_through_style_line() {
        val s = AssStyle.parse(sampleLine)
        assertEquals(sampleLine, s.toStyleLine())
    }
    @Test fun ass_to_ssa_matches_aegisub_table() {
        // 对齐 Aegisub 源码 AssStyle::AssToSsa
        assertEquals(1, AssStyle.assToSsa(1))
        assertEquals(2, AssStyle.assToSsa(2))
        assertEquals(3, AssStyle.assToSsa(3))
        assertEquals(9, AssStyle.assToSsa(4))
        assertEquals(10, AssStyle.assToSsa(5))
        assertEquals(11, AssStyle.assToSsa(6))
        assertEquals(5, AssStyle.assToSsa(7))
        assertEquals(6, AssStyle.assToSsa(8))
        assertEquals(7, AssStyle.assToSsa(9))
    }
    @Test fun ssa_ass_round_trip_is_identity_for_all_numpad() {
        // ssaToAss(assToSsa(x)) == x 对所有有效 ASS 对齐 1..9 成立
        for (x in 1..9) assertEquals(x, AssStyle.ssaToAss(AssStyle.assToSsa(x)))
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*AssStyleTest"
```

- [ ] **Step 3: 实现 AssStyle**

```kotlin
package io.github.samgum.aegisub.domain.model

/** ASS/SSA 样式（V4+ 字段）。alignment 以 \\an 1-9 记。 */
data class AssStyle(
    val name: String = "Default",
    val font: String = "Arial",
    val fontSize: Double = 48.0,
    val primary: AssColor = AssColor.WHITE,
    val secondary: AssColor = AssColor.RED,
    val outline: AssColor = AssColor.BLACK,
    val shadow: AssColor = AssColor.BLACK,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeout: Boolean = false,
    val scaleX: Double = 100.0,
    val scaleY: Double = 100.0,
    val spacing: Double = 0.0,
    val angle: Double = 0.0,
    val borderStyle: Int = 1,
    val outlineWidth: Double = 2.0,
    val shadowWidth: Double = 2.0,
    val alignment: Int = 2,
    val margins: Margins = Margins.ZERO,
    val encoding: Int = 1,
) {
    fun toStyleLine(): String = listOf(
        name, font, fontSize.formatNum(),
        primary.toAssString(), secondary.toAssString(), outline.toAssString(), shadow.toAssString(),
        bold.toInt(), italic.toInt(), underline.toInt(), strikeout.toInt(),
        scaleX.formatNum(), scaleY.formatNum(), spacing.formatNum(), angle.formatNum(),
        borderStyle, outlineWidth.formatNum(), shadowWidth.formatNum(), alignment,
        margins.left, margins.right, margins.vertical, encoding,
    ).joinToString(",")

    companion object {
        private fun Boolean.toInt() = if (this) -1 else 0
        private fun Double.formatNum() =
            if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()

        /** 解析 `Style: ...` 行（参数可含或不带 "Style:" 前缀）。 */
        fun parse(line: String): AssStyle {
            val body = line.trim().let {
                if (it.startsWith("Style:", ignoreCase = true)) it.substringAfter(":").trim() else it
            }
            val f = body.split(",").map { it.trim() }
            require(f.size >= 23) { "Invalid Style line, expected >=23 fields, got ${f.size}" }
            fun b(i: Int) = f[i] == "-1" || f[i].equals("true", true)
            fun d(i: Int) = f[i].toDoubleOrNull() ?: 0.0
            fun n(i: Int) = f[i].toIntOrNull() ?: 0
            return AssStyle(
                name = f[0], font = f[1], fontSize = d(2),
                primary = AssColor.parseAss(f[3]), secondary = AssColor.parseAss(f[4]),
                outline = AssColor.parseAss(f[5]), shadow = AssColor.parseAss(f[6]),
                bold = b(7), italic = b(8), underline = b(9), strikeout = b(10),
                scaleX = d(11), scaleY = d(12), spacing = d(13), angle = d(14),
                borderStyle = n(15), outlineWidth = d(16), shadowWidth = d(17), alignment = n(18),
                margins = Margins(n(19), n(20), n(21)), encoding = n(22),
            )
        }

        /** \\an(1-9) → SSA。对齐 Aegisub 源码 AssStyle::AssToSsa（含 default→2）。 */
        fun assToSsa(assAlign: Int): Int = when (assAlign) {
            1 -> 1; 2 -> 2; 3 -> 3; 4 -> 9; 5 -> 10; 6 -> 11; 7 -> 5; 8 -> 6; 9 -> 7
            else -> 2
        }
        /** SSA → \\an(1-9)。对齐 Aegisub 源码 AssStyle::SsaToAss。SSA 合法值为 1,2,3,5,6,7,9,10,11。 */
        fun ssaToAss(ssaAlign: Int): Int = when (ssaAlign) {
            1 -> 1; 2 -> 2; 3 -> 3; 5 -> 7; 6 -> 8; 7 -> 9; 9 -> 4; 10 -> 5; 11 -> 6
            else -> 2
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*AssStyleTest"
```
Expected: 4 tests passed（`parses_all_fields` / `round_trips_through_style_line` / `ass_to_ssa_matches_aegisub_table` / `ssa_ass_round_trip_is_identity_for_all_numpad`）。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/AssStyle.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/AssStyleTest.kt
git commit -m "feat(domain): AssStyle 模型、解析/序列化与 ASS↔SSA 对齐换算"
```

---

## Task 10: DialogueBlock 块模型 + AssOverrideTag

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/DialogueBlock.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/DialogueBlockTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DialogueBlockTest {
    @Test fun splits_plain_and_overrides() {
        val blocks = DialogueBlock.parse("Yes, I {\\i1}am{\\i0} here.")
        assertEquals(5, blocks.size)
        assertIs<DialogueBlock.Plain>("Yes, I ", (blocks[0] as DialogueBlock.Plain).text.let { it })
        assertEquals("Yes, I ", (blocks[0] as DialogueBlock.Plain).text)
        assertIs<DialogueBlock.Override>(blocks[1])
        assertEquals("am", (blocks[2] as DialogueBlock.Plain).text)
        assertIs<DialogueBlock.Override>(blocks[3])
        assertEquals(" here.", (blocks[4] as DialogueBlock.Plain).text)
    }
    @Test fun override_parses_tags() {
        val blocks = DialogueBlock.parse("{\\i1\\b1}text")
        val ov = blocks[0] as DialogueBlock.Override
        assertEquals(2, ov.tags.size)
        assertEquals("i", ov.tags[0].name)
        assertEquals("1", ov.tags[0].rawValue)
        assertEquals("b", ov.tags[1].name)
    }
    @Test fun comment_block() {
        val blocks = DialogueBlock.parse("{*this is a comment}x")
        assertIs<DialogueBlock.Comment>(blocks[0])
    }
    @Test fun round_trips_text() {
        val original = "Yes, I {\\i1}am{\\i0} here."
        assertEquals(original, DialogueBlock.toText(DialogueBlock.parse(original)))
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*DialogueBlockTest"
```

- [ ] **Step 3: 实现**

```kotlin
package io.github.samgum.aegisub.domain.model

/** 单个覆盖标签：\\name 后跟原样值（带括号参数也保留为 rawValue）。 */
data class AssOverrideTag(val name: String, val rawValue: String = "") {
    override fun toString(): String = if (rawValue.isEmpty()) "\\$name" else "\\$name$rawValue"
}

/** 对话文本块。Plain/Override/Comment/Drawing。 */
sealed interface DialogueBlock {
    val text: String

    data class Plain(override val text: String) : DialogueBlock
    data class Comment(override val text: String) : DialogueBlock
    data class Override(val tags: List<AssOverrideTag>) : DialogueBlock {
        override val text: String get() = "{" + tags.joinToString("") { it.toString() } + "}"
    }
    data class Drawing(override val text: String, val scale: Int) : DialogueBlock

    companion object {
        /** 把事件文本切成块。空覆盖块 `{}` 视为 Comment。`{*...}` 视为 Comment。 */
        fun parse(text: String): List<DialogueBlock> {
            val blocks = mutableListOf<DialogueBlock>()
            var i = 0
            var plainStart = 0
            while (i < text.length) {
                if (text[i] == '{') {
                    if (i > plainStart) blocks += Plain(text.substring(plainStart, i))
                    val end = text.indexOf('}', i + 1)
                    val close = if (end < 0) text.length else end + 1
                    val inner = text.substring(i + 1, if (end < 0) text.length else end)
                    blocks += parseOverrideBlock(inner)
                    i = close
                    plainStart = i
                } else {
                    i++
                }
            }
            if (plainStart < text.length) blocks += Plain(text.substring(plainStart))
            return blocks
        }

        private fun parseOverrideBlock(inner: String): DialogueBlock {
            if (inner.isEmpty() || inner.startsWith("*")) return Comment(inner)
            val tags = mutableListOf<AssOverrideTag>()
            for (seg in inner.split('\\').drop(1)) { // 前导 '\' 产生首个空段
                if (seg.isEmpty()) continue
                val nameEnd = seg.indexOfFirst { !it.isLetter() }.let { if (it < 0) seg.length else it }
                val name = seg.substring(0, nameEnd)
                val raw = seg.substring(nameEnd)
                tags += AssOverrideTag(name, raw)
            }
            return Override(tags)
        }

        /** 由块列表重建文本（与原文本在合法输入上一致）。 */
        fun toText(blocks: List<DialogueBlock>): String = buildString {
            blocks.forEach { b ->
                when (b) {
                    is Plain -> append(b.text)
                    is Comment -> append("{").append(b.text).append("}")
                    is Override -> append(b.text)
                    is Drawing -> append(b.text)
                }
            }
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*DialogueBlockTest"
```
Expected: 4 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/DialogueBlock.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/DialogueBlockTest.kt
git commit -m "feat(domain): DialogueBlock 块模型与覆盖标签解析/重建"
```

---

## Task 11: DialogueTokenizer（token 化 + markDrawings）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/parse/DialogueToken.kt`
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/parse/DialogueTokenizer.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/parse/DialogueTokenizerTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DialogueTokenizerTest {
    @Test fun tokenizes_plain_text() {
        val toks = DialogueTokenizer.tokenize("hello")
        assertEquals(1, toks.size)
        assertEquals(DialogueTokenType.TEXT, toks[0].type)
        assertEquals(5, toks[0].length)
    }
    @Test fun tokenizes_override_block() {
        // {\i1}x{\i0}
        val toks = DialogueTokenizer.tokenize("{\\i1}x{\\i0}")
        val types = toks.map { it.type }
        assertTrue(DialogueTokenType.OVR_BEGIN in types)
        assertTrue(DialogueTokenType.OVR_END in types)
        assertTrue(DialogueTokenType.TAG_NAME in types)
        assertTrue(DialogueTokenType.ARG in types)
    }
    @Test fun tokenizes_paren_args() {
        // {\pos(100,200)}
        val toks = DialogueTokenizer.tokenize("{\\pos(100,200)}")
        val types = toks.map { it.type }
        assertTrue(DialogueTokenType.OPEN_PAREN in types)
        assertTrue(DialogueTokenType.CLOSE_PAREN in types)
        assertTrue(DialogueTokenType.ARG_SEP in types)
    }
    @Test fun marks_drawings_after_p_tag() {
        // {\p1}m 0 0 l 100 0 100 100{\p0}text
        val s = "{\\p1}m 0 0 l 100 0{\p0}text"
        val marked = DialogueTokenizer.markDrawings(s, DialogueTokenizer.tokenize(s))
        assertTrue(marked.any { it.type == DialogueTokenType.DRAWING_FULL })
    }
    @Test fun total_length_equals_string_length() {
        val s = "Yes, I {\\i1}am{\\i0} here."
        val toks = DialogueTokenizer.tokenize(s)
        assertEquals(s.length, toks.sumOf { it.length })
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*DialogueTokenizerTest"
```

- [ ] **Step 3: 实现 DialogueToken**

```kotlin
package io.github.samgum.aegisub.domain.parse

object DialogueTokenType {
    const val TEXT = 1000
    const val WORD = 1001
    const val LINE_BREAK = 1002
    const val OVR_BEGIN = 1003
    const val OVR_END = 1004
    const val TAG_START = 1005
    const val TAG_NAME = 1006
    const val OPEN_PAREN = 1007
    const val CLOSE_PAREN = 1008
    const val ARG_SEP = 1009
    const val ARG = 1010
    const val ERROR = 1011
    const val COMMENT = 1012
    const val WHITESPACE = 1013
    const val DRAWING_FULL = 1014
    const val DRAWING_CMD = 1015
    const val DRAWING_X = 1016
    const val DRAWING_Y = 1017
}

data class DialogueToken(val type: Int, val length: Int)
```

- [ ] **Step 4: 实现 DialogueTokenizer**

```kotlin
package io.github.samgum.aegisub.domain.parse

import io.github.samgum.aegisub.domain.parse.DialogueTokenType as TT

/** 对话体 tokenizer：产出 token 流，驱动语法高亮与结构分析。参考自 libaegisub dialogue_parser。 */
object DialogueTokenizer {

    fun tokenize(s: String): List<DialogueToken> {
        if (s.isEmpty()) return emptyList()
        val out = mutableListOf<DialogueToken>()
        var i = 0
        var textStart = -1
        fun flushText(end: Int) {
            if (textStart in 0 until end) out += DialogueToken(TT.TEXT, end - textStart)
            textStart = -1
        }
        while (i < s.length) {
            if (s[i] == '{') {
                flushText(i)
                out += DialogueToken(TT.OVR_BEGIN, 1)
                i = parseOverride(s, i + 1, out)
            } else {
                if (textStart < 0) textStart = i
                i++
            }
        }
        flushText(s.length)
        return out
    }

    private fun parseOverride(s: String, start: Int, out: MutableList<DialogueToken>): Int {
        var i = start
        while (i < s.length) {
            val c = s[i]
            when {
                c == '}' -> { out += DialogueToken(TT.OVR_END, 1); return i + 1 }
                c == '\\' -> {
                    out += DialogueToken(TT.TAG_START, 1); i++
                    val nameStart = i
                    while (i < s.length && s[i].isLetter()) i++
                    if (i > nameStart) out += DialogueToken(TT.TAG_NAME, i - nameStart)
                    if (i < s.length && s[i] == '(') {
                        out += DialogueToken(TT.OPEN_PAREN, 1); i++
                        while (i < s.length && s[i] != '}' && s[i] != '\\') i = consumeArg(s, i, out)
                        if (i < s.length && s[i] == ')') { out += DialogueToken(TT.CLOSE_PAREN, 1); i++ }
                    } else {
                        // 附加值（如 \fnArial、\i1、\c&H..&）直到下一个 \ 或 }
                        val a0 = i
                        while (i < s.length && s[i] != '\\' && s[i] != '}') i++
                        if (i > a0) out += DialogueToken(TT.ARG, i - a0)
                    }
                }
                c.isWhitespace() -> {
                    val w0 = i
                    while (i < s.length && s[i].isWhitespace()) i++
                    out += DialogueToken(TT.WHITESPACE, i - w0)
                }
                else -> { out += DialogueToken(TT.ARG, 1); i++ }
            }
        }
        return i
    }

    private fun consumeArg(s: String, start: Int, out: MutableList<DialogueToken>): Int {
        var i = start
        val a0 = i
        while (i < s.length) {
            val c = s[i]
            if (c == ',' || c == ')' || c == '\\') break
            i++
        }
        if (i > a0) out += DialogueToken(TT.ARG, i - a0)
        if (i < s.length && s[i] == ',') { out += DialogueToken(TT.ARG_SEP, 1); i++ }
        return i
    }

    /** 标记 \\p（scale>0）之后的 TEXT 为 DRAWING_FULL，直到 \\p0。 */
    fun markDrawings(s: String, tokens: List<DialogueToken>): List<DialogueToken> {
        val result = tokens.toMutableList()
        var inDrawing = false
        var pos = 0
        var i = 0
        while (i < result.size) {
            val t = result[i]
            // 检测 \p 标签：TAG_NAME('p') 后跟 ARG，且不在括号内
            if (t.type == TT.TAG_NAME && t.length == 1 && s.regionMatches(pos, "p", 0, 1)) {
                if (i + 1 < result.size && result[i + 1].type == TT.ARG) {
                    val argStart = pos + t.length
                    val argText = s.substring(argStart, argStart + result[i + 1].length)
                    val scale = argText.trimStart('0').firstOrNull()?.digitToIntOrNull() ?: 0
                    inDrawing = scale > 0
                }
            }
            if (t.type == TT.TEXT && inDrawing) result[i] = DialogueToken(TT.DRAWING_FULL, t.length)
            pos += t.length
            i++
        }
        return result
    }
}
```

- [ ] **Step 5: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*DialogueTokenizerTest"
```
Expected: 5 tests passed。

- [ ] **Step 6: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/parse/ core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/parse/
git commit -m "feat(domain): 对话体 tokenizer 与绘图标记"
```

---

## Task 12: AssEvent（事件行 + 块解析 + 序列化）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/AssEvent.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/AssEventTest.kt`

Dialogue 行格式：
`Dialogue: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text`
（Comment 行同结构，前缀 `Comment:`）

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.model

import io.github.samgum.aegisub.domain.time.SubTime
import kotlin.test.Test
import kotlin.test.assertEquals

class AssEventTest {
    private val line =
        "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello {\\i1}world{\\i0}"

    @Test fun parses_dialogue_line() {
        val e = AssEvent.parse(line)
        assertEquals(0, e.layer)
        assertEquals(1_000_000L, e.start.micros)
        assertEquals(3_000_000L, e.end.micros)
        assertEquals("Default", e.style)
        assertEquals("Hello {\\i1}world{\\i0}", e.text)
        assertEquals(false, e.comment)
    }
    @Test fun parses_comment_line() {
        val e = AssEvent.parse("Comment: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,note")
        assertEquals(true, e.comment)
    }
    @Test fun round_trips_dialogue_line() {
        assertEquals(line, AssEvent.parse(line).toLine())
    }
    @Test fun stripped_text_removes_tags() {
        val e = AssEvent.parse(line)
        assertEquals("Hello world", e.strippedText)
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*AssEventTest"
```

- [ ] **Step 3: 实现 AssEvent**

```kotlin
package io.github.samgum.aegisub.domain.model

import io.github.samgum.aegisub.domain.time.SubTime

/** ASS 事件行（Dialogue/Comment）。 */
data class AssEvent(
    val id: Long = 0L,
    val row: Int = -1,
    val comment: Boolean = false,
    val layer: Int = 0,
    val start: SubTime = SubTime.ZERO,
    val end: SubTime = SubTime.ofMillis(5_000),
    val style: String = "Default",
    val actor: String = "",
    val margins: Margins = Margins.ZERO,
    val effect: String = "",
    val text: String = "",
) {
    /** 去除所有覆盖标签后的纯文本（绘图块视作空）。 */
    val strippedText: String
        get() = DialogueBlock.parse(text).joinToString("") {
            when (it) { is DialogueBlock.Plain -> it.text; else -> "" }
        }

    fun toLine(): String {
        val kind = if (comment) "Comment" else "Dialogue"
        val parts = listOf(
            layer.toString(), start.toAssString(false), end.toAssString(false), style, actor,
            margins.left.toString(), margins.right.toString(), margins.vertical.toString(), effect, text,
        )
        return "$kind: " + parts.joinToString(",")
    }

    companion object {
        fun parse(line: String): AssEvent {
            val trimmed = line.trim()
            val comment = trimmed.startsWith("Comment:", ignoreCase = true)
            val kind = if (comment) "Comment" else "Dialogue"
            require(trimmed.startsWith("$kind:", ignoreCase = true)) { "Not a Dialogue/Comment line: $line" }
            val body = trimmed.substringAfter(":").trim()
            // 前 9 字段按逗号切，第 10 个（文本）保留全部（含逗号）
            val f = body.split(",", limit = 10).map { it.trim() }
            require(f.size == 10) { "Invalid Dialogue line: expected 10 fields, got ${f.size}" }
            return AssEvent(
                comment = comment,
                layer = f[0].toIntOrNull() ?: 0,
                start = SubTime.parseAss(f[1]),
                end = SubTime.parseAss(f[2]),
                style = f[3],
                actor = f[4],
                margins = Margins(f[5].toIntOrNull() ?: 0, f[6].toIntOrNull() ?: 0, f[7].toIntOrNull() ?: 0),
                effect = f[8],
                text = f[9],
            )
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*AssEventTest"
```
Expected: 4 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/AssEvent.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/AssEventTest.kt
git commit -m "feat(domain): AssEvent 事件行解析/序列化与去标签文本"
```

---

## Task 13: AssScript（文件容器）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/AssScript.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/AssScriptTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.model

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class AssScriptTest {
    @Test fun default_script_has_script_info_and_default_style() {
        val s = AssScript.default()
        assertEquals("v4.00+", s.info.first { it.key == "ScriptType" }.value)
        assertEquals("Default", s.styles.first().name)
    }
    @Test fun script_info_helpers() {
        val s = AssScript.default()
        assertEquals("v4.00+", s.getScriptInfo("ScriptType"))
        assertEquals(null, s.getScriptInfo("Missing"))
    }
    @Test fun with_events_returns_new_immutable_instance() {
        val s = AssScript.default()
        val e = AssEvent(text = "hi")
        val s2 = s.withEvent(e)
        assertEquals(1, s2.events.size)
        assertEquals(0, s.events.size) // 原实例不变（不可变）
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*AssScriptTest"
```

- [ ] **Step 3: 实现 AssScript**

```kotlin
package io.github.samgum.aegisub.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/** ASS 文件容器：不可变。编辑通过 with* 方法产生新实例（结构共享）。 */
data class AssScript(
    val info: ImmutableList<AssInfo> = persistentListOf(),
    val styles: ImmutableList<AssStyle> = persistentListOf(),
    val events: ImmutableList<AssEvent> = persistentListOf(),
    val properties: Map<String, String> = emptyMap(),
) {
    fun getScriptInfo(key: String): String? = info.firstOrNull { it.key == key }?.value

    fun withEvent(event: AssEvent): AssScript = copy(events = (events + event).toPersistentList())
    fun withEvents(newEvents: List<AssEvent>): AssScript = copy(events = newEvents.toPersistentList())
    fun withStyle(style: AssStyle): AssScript = copy(styles = (styles + style).toPersistentList())

    companion object {
        fun default(): AssScript = AssScript(
            info = persistentListOf(
                AssInfo("ScriptType", "v4.00+"),
                AssInfo("PlayResX", "384"),
                AssInfo("PlayResY", "288"),
            ),
            styles = persistentListOf(AssStyle()),
            events = persistentListOf(),
        )
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*AssScriptTest"
```
Expected: 3 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/model/AssScript.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/model/AssScriptTest.kt
git commit -m "feat(domain): AssScript 不可变容器与结构共享编辑方法"
```

---

## Task 14: SubtitleFormat 接口 + 选项

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/SubtitleFormat.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/FormatOptionsTest.kt`

> 本任务只定义自包含的接口与选项（仅依赖 Task 13 的 `AssScript`），可独立编译提交。`FormatRegistry` 自动检测在 Task 18（所有格式就绪后）实现，避免中间编译失败。

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FormatOptionsTest {
    @Test fun write_options_defaults() {
        val o = WriteOptions()
        assertEquals(TimePrecision.AUTO, o.timePrecision)
        assertEquals(false, o.stripTags)
    }
    @Test fun read_options_defaults() {
        assertEquals(true, ReadOptions().detectEncoding)
    }
    @Test fun time_precision_has_three_variants() {
        assertEquals(3, TimePrecision.entries.size)
        assertNotEquals(TimePrecision.TWO_MS, TimePrecision.THREE_MS)
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*FormatOptionsTest"
```

- [ ] **Step 3: 实现 SubtitleFormat 接口与选项**

```kotlin
package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssScript

/** 导出时间精度。 */
enum class TimePrecision { TWO_MS, THREE_MS, AUTO }

data class ReadOptions(val detectEncoding: Boolean = true)

data class WriteOptions(
    val timePrecision: TimePrecision = TimePrecision.AUTO,
    val stripTags: Boolean = false,
)

/** 字幕格式编解码器接口。 */
interface SubtitleFormat {
    val name: String
    val extensions: List<String>

    /** 内容嗅探：能否解析该文本。 */
    fun canRead(content: String): Boolean
    fun canWrite(fileName: String): Boolean = extensions.any { fileName.endsWith(it, ignoreCase = true) }

    fun read(text: String, options: ReadOptions = ReadOptions()): AssScript
    fun write(script: AssScript, options: WriteOptions = WriteOptions()): String
}
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*FormatOptionsTest"
```
Expected: 3 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/SubtitleFormat.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/FormatOptionsTest.kt
git commit -m "feat(domain): SubtitleFormat 接口与读写选项"
```

---

## Task 15: ASS + SSA 编解码

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/AssFormat.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/AssFormatTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssFormatTest {
    private val sample = """
        [Script Info]
        ScriptType: v4.00+
        PlayResX: 1920
        PlayResY: 1080

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello
    """.trimIndent()

    @Test fun reads_ass_sections() {
        val s = AssFormat.read(sample)
        assertEquals("v4.00+", s.getScriptInfo("ScriptType"))
        assertEquals("1920", s.getScriptInfo("PlayResX"))
        assertEquals(1, s.styles.size)
        assertEquals("Default", s.styles[0].name)
        assertEquals(1, s.events.size)
        assertEquals(1_000_000L, s.events[0].start.micros)
        assertEquals("Hello", s.events[0].text)
    }
    @Test fun writes_ass_round_trip() {
        val original = AssFormat.read(sample)
        val rewritten = AssFormat.write(original)
        val reparsed = AssFormat.read(rewritten)
        assertEquals(original.events.size, reparsed.events.size)
        assertEquals(original.events[0].text, reparsed.events[0].text)
        assertEquals(original.events[0].start, reparsed.events[0].start)
    }
    @Test fun can_read_detects_ass() {
        assertTrue(AssFormat.canRead("[Script Info]"))
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*AssFormatTest"
```

- [ ] **Step 3: 实现 AssFormat + SsaFormat**

```kotlin
package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssInfo
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import kotlinx.collections.immutable.toPersistentList

/** ASS (V4+) 编解码。SSA 通过 [ssaMode] 复用（仅段头与 ScriptType 不同）。 */
class AssFormatBase(private val ssaMode: Boolean = false) : SubtitleFormat {
    override val name: String = if (ssaMode) "ssa" else "ass"
    override val extensions: List<String> = if (ssaMode) listOf(".ssa") else listOf(".ass")

    override fun canRead(content: String): Boolean =
        content.contains("[Script Info]", ignoreCase = true) &&
            (content.contains("[V4+ Styles]", ignoreCase = true) ||
                content.contains("[V4 Styles]", ignoreCase = true))

    override fun read(text: String, options: ReadOptions): AssScript {
        val info = mutableListOf<AssInfo>()
        val styles = mutableListOf<AssStyle>()
        val events = mutableListOf<AssEvent>()
        var eventIdx = 0L
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("[") && line.endsWith("]") -> Unit // 段头，无需记录
                // 任意段的 Format: 描述行都跳过（Styles 段与 Events 段都有）
                line.startsWith("Format:", ignoreCase = true) -> Unit
                line.startsWith("Style:", ignoreCase = true) -> styles += AssStyle.parse(line)
                line.startsWith("Dialogue:", ignoreCase = true) ||
                    line.startsWith("Comment:", ignoreCase = true) ->
                    events += AssEvent.parse(line).copy(id = eventIdx++, row = events.size)
                else -> AssInfo.parse(line)?.let { info += it }
            }
        }
        return AssScript(
            info = info.toPersistentList(),
            styles = styles.toPersistentList(),
            events = events.toPersistentList(),
        )
    }

    override fun write(script: AssScript, options: WriteOptions): String = buildString {
        val scriptType = if (ssaMode) "v4.00" else "v4.00+"
        val stylesHeader = if (ssaMode) "[V4 Styles]" else "[V4+ Styles]"
        appendLine("[Script Info]")
        val infoLines = script.info.toMutableList()
        if (infoLines.none { it.key.equals("ScriptType", true) })
            infoLines.add(0, AssInfo("ScriptType", scriptType))
        infoLines.forEach { appendLine(it.toLine()) }
        appendLine()
        appendLine(stylesHeader)
        appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        (if (script.styles.isEmpty()) listOf(AssStyle()) else script.styles).forEach { appendLine("Style: " + it.toStyleLine()) }
        appendLine()
        appendLine("[Events]")
        appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        script.events.forEach { appendLine(it.toLine()) }
    }
}

object AssFormat : SubtitleFormat by AssFormatBase(ssaMode = false)
object SsaFormat : SubtitleFormat by AssFormatBase(ssaMode = true)
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*AssFormatTest"
```
Expected: 3 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/AssFormat.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/AssFormatTest.kt
git commit -m "feat(domain): ASS/SSA 编解码（段解析、Style/Dialogue 读写、往返）"
```

---

## Task 16: SRT 编解码

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/SrtFormat.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/SrtFormatTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SrtFormatTest {
    private val sample = "1\n00:00:01,000 --> 00:00:03,000\nHello world\n\n2\n00:00:04,000 --> 00:00:06,500\nSecond\n"

    @Test fun reads_srt_blocks() {
        val s = SrtFormat.read(sample)
        assertEquals(2, s.events.size)
        assertEquals(1_000_000L, s.events[0].start.micros)
        assertEquals(3_000_000L, s.events[0].end.micros)
        assertEquals("Hello world", s.events[0].text)
        assertEquals(6_500_000L, s.events[1].end.micros)
    }
    @Test fun writes_srt_round_trip() {
        val reparsed = SrtFormat.read(SrtFormat.write(SrtFormat.read(sample)))
        assertEquals(2, reparsed.events.size)
        assertEquals("Second", reparsed.events[1].text)
        assertEquals(6_500_000L, reparsed.events[1].end.micros)
    }
    @Test fun can_read_detects_srt() {
        assertTrue(SrtFormat.canRead("1\n00:00:01,000 --> 00:00:02,000\n"))
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*SrtFormatTest"
```

- [ ] **Step 3: 实现 SrtFormat**

```kotlin
package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime

object SrtFormat : SubtitleFormat {
    override val name = "srt"
    override val extensions = listOf(".srt")

    private val timeLine = Regex("""\d{2}:\d{2}:\d{2}[,.]\d{3}\s*-->\s*\d{2}:\d{2}:\d{2}[,.]\d{3}""")

    override fun canRead(content: String): Boolean = timeLine.containsMatchIn(content)

    override fun read(text: String, options: ReadOptions): AssScript {
        val events = mutableListOf<AssEvent>()
        val blocks = text.replace("\r\n", "\n").split("\n\n")
        var idx = 0L
        for (block in blocks) {
            val lines = block.trim().lines().filter { it.isNotBlank() }
            if (lines.size < 2) continue
            val timeIdx = lines.indexOfFirst { timeLine.containsMatchIn(it) }
            if (timeIdx < 0) continue
            val (a, b) = lines[timeIdx].split("-->")
            val start = SubTime.parseSrt(a.trim())
            val end = SubTime.parseSrt(b.trim())
            val body = lines.drop(timeIdx + 1).joinToString("\n")
            events += AssEvent(id = idx++, row = events.size, start = start, end = end, text = body)
        }
        return AssScript(events = kotlinx.collections.immutable.toPersistentList(events))
    }

    override fun write(script: AssScript, options: WriteOptions): String = buildString {
        script.events.forEachIndexed { i, e ->
            appendLine(i + 1)
            appendLine("${e.start.toSrtString()} --> ${e.end.toSrtString()}")
            appendLine(e.strippedText)
            if (i < script.events.size - 1) appendLine()
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*SrtFormatTest"
```
Expected: 3 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/SrtFormat.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/SrtFormatTest.kt
git commit -m "feat(domain): SRT 编解码（块解析、往返）"
```

---

## Task 17: TXT 编解码

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/TxtFormat.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/TxtFormatTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals

class TxtFormatTest {
    @Test fun reads_lines_as_sequential_events() {
        val s = TxtFormat.read("line one\nline two\n")
        assertEquals(2, s.events.size)
        assertEquals("line one", s.events[0].text)
        // 每行默认 2s，第二行 start 应为 2s（2000ms）
        assertEquals(2_000_000L, s.events[1].start.micros)
        assertEquals(4_000_000L, s.events[1].end.micros)
    }
    @Test fun writes_text_only() {
        val s = TxtFormat.read("a\nb\n")
        assertEquals("a\nb", TxtFormat.write(s).trim())
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*TxtFormatTest"
```

- [ ] **Step 3: 实现 TxtFormat**

```kotlin
package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime

object TxtFormat : SubtitleFormat {
    override val name = "txt"
    override val extensions = listOf(".txt")
    private val lineDurationMs = 2_000L

    override fun canRead(content: String): Boolean {
        // 纯文本兜底：不含任何已知格式标记
        return content.isNotBlank() &&
            !content.contains("[Script Info]", true) &&
            !content.contains("-->", true) &&
            !content.contains(Regex("""^\[\d{2}:\d{2}[.:]""", RegexOption.MULTILINE))
    }

    override fun read(text: String, options: ReadOptions): AssScript {
        val events = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { i, line ->
                val start = SubTime.ofMillis(i * lineDurationMs)
                AssEvent(id = i.toLong(), row = i, start = start, end = start + SubTime.ofMillis(lineDurationMs), text = line)
            }.toList()
        return AssScript(events = kotlinx.collections.immutable.toPersistentList(events))
    }

    override fun write(script: AssScript, options: WriteOptions): String =
        script.events.joinToString("\n") { it.strippedText }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*TxtFormatTest"
```
Expected: 2 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/TxtFormat.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/TxtFormatTest.kt
git commit -m "feat(domain): TXT 编解码（逐行顺序事件）"
```

---

## Task 18: LRC 编解码 + 注册表回归

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/LrcFormat.kt`
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/FormatRegistry.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/LrcFormatTest.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/FormatRegistryTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.time.LrcTimeFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LrcFormatTest {
    private val sample = "[ti:Title]\n[ar:Artist]\n[00:01.00]first line\n[00:03.50]second line\n"

    @Test fun reads_metadata_and_events() {
        val s = LrcFormat.read(sample)
        assertEquals("Title", s.getScriptInfo("ti"))
        assertEquals("Artist", s.getScriptInfo("ar"))
        assertEquals(2, s.events.size)
        assertEquals(1_000_000L, s.events[0].start.micros)
        assertEquals("first line", s.events[0].text)
        assertEquals(3_500_000L, s.events[1].start.micros)
    }
    @Test fun writes_with_default_format() {
        val out = LrcFormat.write(LrcFormat.read(sample))
        assertTrue(out.contains("[ti:Title]"))
        assertTrue(out.contains("[00:01.00]first line"))
    }
    @Test fun reads_colon_variant() {
        val s = LrcFormat.read("[00:01:00]x\n")
        assertEquals(1_000_000L, s.events[0].start.micros)
    }
    @Test fun writes_chosen_format() {
        val s = LrcFormat.read("[00:01.00]x\n")
        val out = LrcFormat.write(s, WriteOptions(timePrecision = TimePrecision.THREE_MS))
        assertTrue(out.contains("[00:01.000]"))
    }
    @Test fun can_read_detects_lrc() {
        assertTrue(LrcFormat.canRead("[00:01.00]hi\n"))
    }
    @Test fun can_read_detects_lrc() {
        assertTrue(LrcFormat.canRead("[00:01.00]hi\n"))
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*LrcFormatTest"
```

- [ ] **Step 3: 实现 LrcFormat**

```kotlin
package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssInfo
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.LrcTimeFormat
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.toPersistentList

object LrcFormat : SubtitleFormat {
    override val name = "lrc"
    override val extensions = listOf(".lrc")
    private val timeTag = Regex("""\[(\d{1,2}:\d{2}[.:]\d{1,3})\]""")
    private val idTag = Regex("""^\[([a-z]+):(.*)]$""", RegexOption.IGNORE_CASE)

    override fun canRead(content: String): Boolean =
        content.lineSequence().any { timeTag.containsMatchIn(it) }

    override fun read(text: String, options: ReadOptions): AssScript {
        val info = mutableListOf<AssInfo>()
        val rawEvents = mutableListOf<Pair<SubTime, String>>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            val idm = idTag.matchEntire(line.trim())
            if (idm != null && !timeTag.containsMatchIn(idm.value)) {
                info += AssInfo(idm.groupValues[1], idm.groupValues[2].trim())
                continue
            }
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) continue
            val lyric = line.substring(tags.last().range.last + 1).trim()
            tags.forEach { m -> rawEvents += SubTime.parseLrc("[${m.groupValues[1]}]") to lyric }
        }
        rawEvents.sortBy { it.first.micros }
        val events = rawEvents.mapIndexed { i, (start, lyric) ->
            val end = rawEvents.getOrNull(i + 1)?.first ?: (start + SubTime.ofMillis(5_000))
            AssEvent(id = i.toLong(), row = i, start = start, end = end, text = lyric)
        }
        return AssScript(info = info.toPersistentList(), events = events.toPersistentList())
    }

    override fun write(script: AssScript, options: WriteOptions): String {
        val fmt = when (options.timePrecision) {
            TimePrecision.THREE_MS -> LrcTimeFormat.XXX
            TimePrecision.TWO_MS -> LrcTimeFormat.XX
            TimePrecision.AUTO -> LrcTimeFormat.XX
        }
        return buildString {
            script.info.forEach { appendLine("[${it.key}:${it.value}]") }
            script.events.forEach { appendLine("${it.start.toLrcString(fmt)}${it.strippedText}") }
        }
    }
}
```

- [ ] **Step 4: 运行 LrcFormatTest 确认通过**

```bash
./gradlew :core:domain:test --tests "*LrcFormatTest"
```
Expected: 5 tests passed。

- [ ] **Step 5: 实现 FormatRegistry（此时 5 种格式已全部就绪）**

文件 `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/FormatRegistry.kt`：

```kotlin
package io.github.samgum.aegisub.domain.format

/** 自动检测注册表：扩展名 + 内容嗅探。 */
object FormatRegistry {
    val formats: List<SubtitleFormat> = listOf(
        AssFormat, SsaFormat, SrtFormat, LrcFormat, TxtFormat,
    )

    fun byName(name: String): SubtitleFormat? = formats.firstOrNull { it.name == name }

    fun detect(content: String): SubtitleFormat? =
        formats.firstOrNull { it.canRead(content) }

    fun detectByExtension(fileName: String): SubtitleFormat? =
        formats.firstOrNull { it.canWrite(fileName) }
}
```

- [ ] **Step 6: 写 FormatRegistryTest 并运行通过**

文件 `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/FormatRegistryTest.kt`：

```kotlin
package io.github.samgum.aegisub.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatRegistryTest {
    @Test fun detects_ass_by_content() {
        assertEquals("ass", FormatRegistry.detect("[Script Info]\nScriptType: v4.00+\n[V4+ Styles]\nFormat: Name, Fontname")?.name)
    }
    @Test fun detects_srt_by_content() {
        assertEquals("srt", FormatRegistry.detect("1\n00:00:01,000 --> 00:00:02,000\nHi\n")?.name)
    }
    @Test fun detects_lrc_by_content() {
        assertEquals("lrc", FormatRegistry.detect("[ti:Song]\n[00:01.00]la la la\n")?.name)
    }
    @Test fun returns_null_for_unknown() {
        assertNull(FormatRegistry.detect(""))
    }
    @Test fun by_name_and_extension() {
        assertEquals("ass", FormatRegistry.byName("ass")?.name)
        assertEquals("lrc", FormatRegistry.detectByExtension("song.lrc")?.name)
    }
}
```

```bash
./gradlew :core:domain:test --tests "*FormatRegistryTest"
```
Expected: 5 tests passed。

- [ ] **Step 7: 提交 LRC + 注册表**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/LrcFormat.kt core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/format/FormatRegistry.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/LrcFormatTest.kt core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/format/FormatRegistryTest.kt
git commit -m "feat(domain): LRC 编解码（4 种时间格式/元数据/混合）与 FormatRegistry 自动检测"
```

---

## Task 19: 撤销引擎（UndoStack 接口 + SnapshotUndoStack）

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/undo/UndoStack.kt`
- Test: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/undo/SnapshotUndoStackTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package io.github.samgum.aegisub.domain.undo

import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotUndoStackTest {
    @Test fun commit_advances_current() {
        val stack = SnapshotUndoStack(AssScript.default())
        stack.commit(stack.current.withEvent(AssEvent(text = "a")), "add a")
        assertEquals(1, stack.current.events.size)
        assertTrue(stack.canUndo)
        assertFalse(stack.canRedo)
    }
    @Test fun undo_redo_restores_versions() {
        val stack = SnapshotUndoStack(AssScript.default())
        stack.commit(stack.current.withEvent(AssEvent(text = "a")), "a")
        stack.commit(stack.current.withEvent(AssEvent(text = "b")), "b")
        assertEquals(2, stack.current.events.size)
        stack.undo()
        assertEquals(1, stack.current.events.size)
        assertEquals("a", stack.current.events[0].text)
        assertTrue(stack.canRedo)
        stack.undo()
        assertEquals(0, stack.current.events.size)
        assertFalse(stack.canUndo)
        stack.redo()
        assertEquals(1, stack.current.events.size)
    }
    @Test fun new_commit_after_undo_clears_redo_branch() {
        val stack = SnapshotUndoStack(AssScript.default())
        stack.commit(stack.current.withEvent(AssEvent(text = "a")), "a")
        stack.undo()
        stack.commit(stack.current.withEvent(AssEvent(text = "c")), "c")
        assertFalse(stack.canRedo)
        assertEquals("c", stack.current.events[0].text)
    }
    @Test fun history_limit_evicts_oldest() {
        val stack = SnapshotUndoStack(AssScript.default(), limit = 3)
        repeat(5) { stack.commit(stack.current.withEvent(AssEvent(text = "$it")), "$it") }
        // 最多回退 limit-1 步
        var undos = 0; while (stack.canUndo) { stack.undo(); undos++ }
        assertEquals(3, undos)
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
./gradlew :core:domain:test --tests "*SnapshotUndoStackTest"
```

- [ ] **Step 3: 实现 UndoStack**

```kotlin
package io.github.samgum.aegisub.domain.undo

/** 撤销/重做栈接口。T 须为不可变类型（结构共享由调用方/不可变集合保证）。 */
interface UndoStack<T> {
    val current: T
    val canUndo: Boolean
    val canRedo: Boolean
    fun commit(next: T, description: String): Int
    fun undo(): T?
    fun redo(): T?
}

/** 基于快照的 CoW 实现：保存不可变版本引用，有界环形缓冲。 */
class SnapshotUndoStack<T>(
    initial: T,
    private val limit: Int = 100,
) : UndoStack<T> {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()
    override var current: T = initial
        private set

    override val canUndo: Boolean get() = past.isNotEmpty()
    override val canRedo: Boolean get() = future.isNotEmpty()

    override fun commit(next: T, description: String): Int {
        past.addLast(current)
        while (past.size > limit - 1) past.removeFirst()
        future.clear()
        current = next
        return past.size
    }

    override fun undo(): T? {
        if (past.isEmpty()) return null
        future.addLast(current)
        current = past.removeLast()
        return current
    }

    override fun redo(): T? {
        if (future.isEmpty()) return null
        past.addLast(current)
        current = future.removeLast()
        return current
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
./gradlew :core:domain:test --tests "*SnapshotUndoStackTest"
```
Expected: 4 tests passed。

- [ ] **Step 5: 提交**

```bash
git add core/domain/src/main/kotlin/io/github/samgum/aegisub/domain/undo/ core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/undo/
git commit -m "feat(domain): 撤销引擎 UndoStack 接口与 CoW 快照实现"
```

---

## Task 20: 往返集成测试 + 全量覆盖率门禁

**Files:**
- Create: `core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/FormatRoundTripTest.kt`
- Modify: `core/domain/build.gradle.kts`（加 kover 覆盖率插件，可选）

- [ ] **Step 1: 写跨格式往返属性测试**

```kotlin
package io.github.samgum.aegisub.domain

import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.FormatRegistry
import io.github.samgum.aegisub.domain.format.LrcFormat
import io.github.samgum.aegisub.domain.format.SrtFormat
import io.github.samgum.aegisub.domain.format.TimePrecision
import io.github.samgum.aegisub.domain.format.WriteOptions
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FormatRoundTripTest {
    private fun sampleScript() = AssScript.default().withEvents(
        listOf(
            AssEvent(start = SubTime.ofMillis(1_000), end = SubTime.ofMillis(3_000), text = "Hello"),
            AssEvent(start = SubTime.ofMillis(3_500), end = SubTime.ofMillis(6_500), text = "World"),
        )
    )

    @Test fun ass_round_trip_preserves_events() {
        val s = sampleScript()
        val reparsed = AssFormat.read(AssFormat.write(s))
        assertEquals(2, reparsed.events.size)
        assertEquals("Hello", reparsed.events[0].text)
        assertEquals(s.events[0].start, reparsed.events[0].start)
        assertEquals(s.events[1].end, reparsed.events[1].end)
    }

    @Test fun srt_round_trip_preserves_times() {
        val s = sampleScript()
        val reparsed = SrtFormat.read(SrtFormat.write(s))
        assertEquals(6_500_000L, reparsed.events[1].end.micros)
    }

    @Test fun lrc_round_trip_three_ms_precision() {
        val s = sampleScript()
        val out = LrcFormat.write(s, WriteOptions(timePrecision = TimePrecision.THREE_MS))
        val reparsed = LrcFormat.read(out)
        assertEquals(s.events[0].start, reparsed.events[0].start)
    }

    @Test fun registry_round_trips_each_format_by_name() {
        val names = listOf("ass", "srt", "lrc")
        names.forEach { n ->
            val fmt = assertNotNull(FormatRegistry.byName(n))
            val out = fmt.write(sampleScript())
            val reparsed = fmt.read(out)
            assertEquals(2, reparsed.events.size, "round-trip failed for $n")
        }
    }

    @Test fun mixed_lrc_format_detection() {
        // 混合 4 种时间格式的 LRC 仍能被识别并解析
        val mixed = "[00:01.00]a\n[00:02.000]b\n[00:03:00]c\n[00:04:000]d\n"
        val fmt = assertNotNull(FormatRegistry.detect(mixed))
        assertEquals("lrc", fmt.name)
        val s = fmt.read(mixed)
        assertEquals(4, s.events.size)
    }
}
```

- [ ] **Step 2: 运行全量测试**

```bash
./gradlew :core:domain:test
```
Expected: 全部测试通过（约 50+ 用例），`BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add core/domain/src/test/kotlin/io/github/samgum/aegisub/domain/FormatRoundTripTest.kt
git commit -m "test(domain): 跨格式往返与混合 LRC 检测集成测试"
```

- [ ] **Step 4: 推送到远端**

```bash
git push origin main
```

- [ ] **Step 5: 更新 ROADMAP，勾选 Phase 0 完成项**

把 `ROADMAP.md` 中 Phase 0 的 `- [ ]` 改为 `- [x]`，并在「当前状态」标注 Phase 0 完成、进入 Phase 1。提交：

```bash
git add ROADMAP.md README.md
git commit -m "docs: Phase 0 核心域模块完成，更新 Roadmap"
git push origin main
```

---

## 完成定义（Definition of Done）

- [ ] `:core:domain` 全部单测通过（`./gradlew :core:domain:test` 绿）
- [ ] 覆盖 ASS/SSA/SRT/TXT/LRC 五格式读写 + 自动检测
- [ ] LRC 四种时间格式全覆盖、混合格式可识别
- [ ] 时间微秒存储，ASS/SRT/LRC 往返零精度损失
- [ ] 撤销引擎 CoW 实现可用，有界栈正确淘汰
- [ ] 所有提交已推送 `origin/main`
- [ ] ROADMAP Phase 0 标记完成
