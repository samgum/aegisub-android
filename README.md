# Aegisub SGMY Android

**English** | [简体中文](README_CN.md)

[![CI](https://github.com/samgum/aegisub-android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/samgum/aegisub-android/actions/workflows/android-ci.yml)
[![Version](https://img.shields.io/badge/version-2.0.0--sgmy-2563EB)]()
[![License](https://img.shields.io/badge/license-BSD%203--Clause-16A34A)](LICENCE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)]()
[![Adaptive](https://img.shields.io/badge/adaptive-Phone%20%7C%20Tablet%20%7C%20Foldable%20%7C%20DeX-8B5CF6)]()

A ground-up Android subtitle studio faithfully replicating **[samgum/Aegisub (Aegisub SGMY)](https://github.com/samgum/Aegisub)** — bringing every custom SGMY workflow, classical desktop Aegisub typesetting/timing power, and **first-class adaptive UI/UX across phones, tablets, desktop mode (Samsung DeX), and foldable postures (Tabletop & Book)** into a unified single-screen workspace.

**Author / Maintainer**: 伤感咩吖 (`samgum`)

---

## ✨ Highlights

### 1. 1:1 Parity with `samgum/Aegisub` (SGMY Exclusive Features)
- **Fix Common Errors (`Tools → Fix Common Errors`)**: Subtitle Edit-style batch pass fixing overlapping display times, short gaps, short/long durations, trailing whitespace (including full-width `U+3000`), and ASS whitespace-only empty lines (while preserving `template*` automation and `\p1` vector drawings). Commits timing, text, and line removals as independent undo steps.
- **Music Lyrics Scroll Generator (`Tools → Lyric Scroll`)**: Generates Apple Music / player-style multi-line scrolling lyrics with cubic ease-out `\move` + `\t(\alpha\1c\fscx\fscy)` transitions, automatic `ScrollCurrent` & `ScrollDim` styles, single or auto-paired bilingual lines, and 7 resolution presets (`PlayRes`, `4K 2160p`, `1080p`, `720p`, vertical `1080×1920`, vertical `720×1280`, custom).
- **CJK Text Suite**:
  - **Chinese Simplified ↔ Traditional Conversion** powered by Android ICU `Transliterator` + curated phrase dictionary, skipping `{...}` ASS override blocks with per-style & selection scoping.
  - **Subtitle Text Cleanup**: replaces full-width commas, smart-cleans Chinese periods, normalizes smart quotes, and collapses double spaces without touching ASS tags.
  - **Paired Punctuation Checker**: stack-based validation for `()`, `[]`, `{}`, `（）`, `【】`, `《》`, `〈〉`, `「」`, `『』`, and quotes, with 1-tap row jump.
  - **Japanese Furigana Annotator**: extracts kanji occurrences, generates `@row#occ:kanji=reading` & global templates, supports Hiragana/Katakana, above/below ruby placement, and idempotent `{*sgmy-furigana}` re-editing.
- **Timing Stitcher & Same-Style Overlap Checker**: snaps micro-gaps by milliseconds or video frame boundaries (`Previous End` or `Next Start` anchor) and audits exact/start/end/crossing overlaps per style.
- **Enhanced LRC & TTML Formats**:
  - **Enhanced LRC**: `[offset:+/-ms]`, `<mm:ss.xx>` inline word/syllable karaoke → `{\kf}`, Apple Music trailing line-end timestamps, untimed LRC fallback, and bidirectional karaoke LRC export.
  - **TTML / iTunes Timed Text (`.ttml` / `.dfxp`)**: clock & offset timestamps, `<span begin/end>` word-level karaoke → `{\kf}`, and Apple Music `ttm:role="x-bg"` background vocal splitting.
- **Subtitle Overflow & High-CPS Highlighting**: real-time PlayRes width estimation honoring inline `\fs`, `\fscx`, `\fsp`, `\pos`, `\move`, and `WrapStyle`, highlighting overflowing lines directly in the grid and edit box.
- **HDR / Dolby Vision / BT.2020 Video Support & Speed Control**: detects HDR10 (PQ), HLG, and Dolby Vision color metadata with a live HUD badge, optional 1080p preview downscaling for 4K HDR sources, and **0.25×–4.0× pitch-preserving playback speed**.
- **AI Subtitle Grammar Analysis, Translation & Polish**: built-in OpenAI-compatible client with presets for **DeepSeek**, **OpenRouter**, **Gemini**, **Alibaba Bailian (DashScope)**, and custom endpoints; API keys stay strictly in volatile session memory.

### 2. Complete Desktop Aegisub Studio Workspace
- **Unified Studio Architecture**: replaces the legacy disconnected two-screen design with a single reactive `WorkspaceScreen` where video stage, audio box, edit box, and subtitle grid stay synchronized in real time.
- **Aspect-Locked Video Stage & 8 Visual Typesetting Tools**:
  - Fixes letterbox coordinate distortion by locking `SubtitleOverlay` and `VisualTypesettingOverlay` strictly to the true video / `PlayResX:PlayResY` frame, with pinch-to-zoom (`50%–300%`), overscan safe-area guides, and a checkerboard dummy video canvas when no media is loaded.
  - All 8 desktop visual tools: **Crosshair**, **`\pos` Drag**, **`\move` Dual-Handle Path**, **`\frz` Rotation**, **3D `\frx/\fry` + `\org` Origin**, **`\fscx/\fscy` Scale**, **Rectangular `\clip/\iclip`**, and **Vector Polygon `\clip/\iclip`**.
  - Live interpolation of `\move`, `\fad`/`\fade`, `\t(t1,t2,accel,\tags)`, multi-color spans, and `\kf` karaoke sweeps.
- **Zoomable Audio Timing Deck (`AegisubAudioBox`)**:
  - Streaming OOM-safe waveform peak extraction + bounded STFT spectrogram heatmap + hardware sync-frame keyframe detection (`# keyframe format v1` import/export & snap).
  - Zoomable `2s–60s` sliding viewport with time ruler, ghost neighbor lines, red Start / orange End drag handles, lead-in/out buttons, 500ms before/after/first/last segment audition, and interactive karaoke syllable boundary dragging.
- **Syntax-Highlighted ASS Edit Box & Full Grid**:
  - Zero-offset `VisualTransformation` highlighting `{...}` override blocks, `\tag` commands, and `\N`/`\h` escapes without IME cursor drift.
  - Dual-pane **Global Style Storage + Script Styles Manager**, Styling Assistant, Translation Assistant, Kanji Timer, Select Lines, Paste Over, Fonts Collector, Resolution Resampler, Framerate Converter, Shift Times, Timing Post-Processor, History Snapshots, Bookmarks, and 34 customizable hardware keyboard hotkeys.

---

## 📱 Adaptive Layouts: Phone, Tablet, Foldable & DeX

Powered by Jetpack `WindowInfoTracker` and `FoldingFeature`, the workspace automatically adapts across **5 hardware postures** (and allows manual override in Preferences):

| Posture | Target Form Factors | Studio Layout |
|---|---|---|
| **`DESKTOP_THREE_ZONE`** | Tablets (`≥ 840dp`), Unfolded Flat Foldables, Samsung DeX / Desktop Windowing | Classic desktop Aegisub menu + icon toolbar; resizable upper split (**Video Stage** left \| **Audio Box + ASS Edit Box** right) over a resizable full-width **9-column Subtitle Grid**. |
| **`BOOK_DUAL_PANE`** | Foldables with vertical separating hinge (`HALF_OPENED` / `isSeparating`) | **Left page**: Video Stage + Audio Timing Deck · **Hinge-safe gutter** · **Right page**: Toolbar + ASS Edit Box + Subtitle Grid. |
| **`TABLETOP`** | Foldables half-opened horizontally on a desk (`HALF_OPENED + HORIZONTAL`) | **Upper upright screen**: unobstructed Video Stage · **Hinge-safe gutter** · **Lower flat console**: touch Audio Timing Deck, Edit Box, and Grid tabs. |
| **`LANDSCAPE_SPLIT`** | Phones in landscape & compact tablets (`600–839dp`) | Compact studio bar + draggable two-column split (**Video + Audio** left \| **Edit Box + Grid** right). |
| **`PHONE_COMPACT`** | Phones in portrait (`< 600dp`) | Single-screen studio with instant stage switcher (**Video+Audio** / **Audio Timing** / **Visual Typesetting** / **Text Focus**), inline ASS Edit Box, high-density 2-line cards, and thumb navigation bar. |

---

## 📂 Supported Formats

| Format | Extensions | Read | Write | Notes |
|---|---|---|---|---|
| **ASS** | `.ass` | ✅ | ✅ | Lossless styles, `[Script Info]`, override tags, vector drawings, microsecond internal precision |
| **SSA** | `.ssa` | ✅ | ✅ | SubStation Alpha v4.00 style & alignment conversion |
| **SRT** | `.srt` | ✅ | ✅ | SubRip timestamps & multi-line events |
| **WebVTT** | `.vtt` | ✅ | ✅ | HTML5 Web Video Text Tracks |
| **TTML / DFXP** | `.ttml`, `.dfxp`, `.xml` | ✅ | ✅ | SGMY word-level `<span>` karaoke & Apple Music `ttm:role="x-bg"` harmony splitting |
| **Enhanced LRC** | `.lrc` | ✅ | ✅ | Standard `[mm:ss.xx]`, `[offset:]`, SGMY `<mm:ss.xx>` syllable karaoke, Apple Music end markers |
| **MicroDVD** | `.sub` | ✅ | ✅ | `{startFrame}{endFrame}` frame-based subtitles |
| **Plain Text** | `.txt` | ✅ | ✅ | Clean dialogue script import/export |

---

## 🏗️ Project Architecture

```text
aegisub-android/
├── core/domain/      # Pure Kotlin/JVM subtitle engine: models, parsers, 8 formats, SGMY tools, undo stack
├── core/data/        # Room (projects, snapshots, bookmarks), DataStore settings/hotkeys, SGMY AI client
├── feature/preview/  # Media3 HDR-aware player, streaming waveform/STFT/keyframes, aspect-locked overlays, AudioBox
├── feature/editor/   # Dual-pane Global + Script Style Manager, Styling & Translation Assistants
└── app/              # Unified Adaptive WorkspaceScreen (5 postures), 27 SGMY/Desktop dialogs, Home, Settings
```

Full feature comparison with desktop Aegisub & SGMY: [docs/COMPARISON.md](docs/COMPARISON.md) · Milestone history: [ROADMAP.md](ROADMAP.md).

---

## 🛠️ Building & Testing

### Prerequisites
- **JDK 17**
- **Android SDK 35** (`platforms;android-35`, `build-tools;35.0.0`)

### Commands
```bash
# Run all unit tests across :core:domain (JUnit 5), :core:data, :feature:*, and :app
./gradlew test

# Build installable Debug APK (output: app/build/outputs/apk/debug/app-debug.apk)
./gradlew assembleDebug
```

---

## 📜 License

Released under the **BSD 3-Clause (Aegisub Compatible)** license — see [LICENCE](LICENCE).
Ported and adapted from [samgum/Aegisub](https://github.com/samgum/Aegisub) and upstream [Aegisub](http://aegisub.org). Original Android & SGMY engineering by **伤感咩吖 (`samgum`)**.
