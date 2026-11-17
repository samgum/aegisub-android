# Aegisub Android

**English** | [简体中文](README_CN.md)

[![CI](https://github.com/samgum/aegisub-android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/samgum/aegisub-android/actions/workflows/android-ci.yml)
[![Version](https://img.shields.io/badge/version-2.0.0--sgmy-2563EB)]()
[![License](https://img.shields.io/badge/license-BSD%203--Clause-16A34A)](LICENCE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)]()
[![Adaptive](https://img.shields.io/badge/adaptive-Phone%20%7C%20Tablet%20%7C%20Foldable%20%7C%20DeX-8B5CF6)]()

A full-featured Android subtitle & karaoke studio replicating **both complete upstream desktop Aegisub and all custom features of [samgum/Aegisub (Aegisub SGMY)](https://github.com/samgum/Aegisub)** — engineered with a clean Material 3 UI across **phones, tablets, desktop mode (Samsung DeX), foldables (Tabletop & Book postures), and physical keyboard + mouse/trackpad**.

**Author / Maintainer**: 伤感咩吖 (`samgum`)

---

## ✨ Highlights

### 1. Complete Upstream Desktop Aegisub Feature Set
- **Unified Studio Workspace**: video stage, zoomable audio waveform/spectrogram deck, syntax-highlighted ASS edit box, and 9-column subtitle grid stay synchronized in real time with multi-level copy-on-write Undo/Redo.
- **Automation 4 & Karaoke Templater (`Automation` Menu)**:
  - **Karaoke Templater (`kara-templater`)**: full support for `Comment: template line / pre-line / syl / char`, modifiers (`all`, `noblank`, `notext`, `keeptags`, `fxgroup`, `repeat N`), inline `$start / $end / $dur / $mid / $kdur / $x / $y / $si / $ci` variables, and `!math expressions!` generating `fx` output lines.
  - All built-in desktop macros: **Clean Tags**, **Strip Tags**, **Add Edge Blur (`\be1`)**, **Make Fullwidth**, **Karaoke Auto Lead-In**, and **Select Overlaps**.
- **ASS Embedded Attachments (`[Fonts]` & `[Graphics]`)**:
  - Byte-accurate 6-bit `+33` ASS UUEncoding (`AssUuCodec`, matching `libaegisub/ass/uuencode.cpp`) with lossless round-trip preservation, font/graphic embedding, and file extraction.
- **Aspect-Locked Video Stage, Playable Dummy Video & 8 Visual Typesetting Tools**:
  - Locks `SubtitleOverlay` and `VisualTypesettingOverlay` strictly to the video / `PlayResX:PlayResY` frame (`50%–300%` zoom, pan, safe-area guides, and right-click `video_context` menu).
  - **Playable Dummy Video**: virtual monotonic frame clock supporting Play/Pause, Play Line, Frame Step, and `0.25×–4.0×` speed on customizable solid or checkerboard canvases without loading a media file.
  - **Jump To Frame / Time (`Ctrl+G`)** & **VFR Timecodes (`# timecode format v1 / v2`)** import/export.
  - All 8 visual tools: **Crosshair**, **`\pos`**, **`\move`**, **`\frz`**, **3D `\frx/\fry` + `\org`**, **`\fscx/\fscy` Scale**, **Rectangular `\clip/\iclip`**, and **Vector Polygon `\clip/\iclip`**.
- **Zoomable Audio Timing Deck (`AegisubAudioBox`)**:
  - Streaming OOM-safe waveform peaks + STFT spectrogram + keyframe extraction (`# keyframe format v1` I/O & snap).
  - `2s–60s` zoomable viewport, red Start / orange End drag handles, lead-in/out, 500ms before/after/first/last auditions, and interactive karaoke syllable splitting.
- **Full Desktop Batch & Engineering Dialogs**:
  - **4-in-1 Timing Post-Processor**: style multi-select, collision-safe `safe_time` lead-in/out, adjacent gap/overlap snapping with `0–100%` bias slider, and 4-threshold keyframe snapping.
  - **11-Field Paste Over**: granular field checkboxes (`Comment`, `Layer`, `Start`, `End`, `Style`, `Actor`, `MarginL/R/V`, `Effect`, `Text`) for both plain lines and copied ASS `Dialogue:` lines.
  - **Export Pipeline**: export filters (**Karaoke Templater**, **Clean Tags**, **Transform Framerate**, **Strip Tags**) + 6 charsets (`UTF-8`, `UTF-8 BOM`, `UTF-16LE`, `GB18030`, `Big5`, `Shift_JIS`).
  - **Dual-Pane Style Manager** (`Global Storage` ↔ `Current Script`) + full **HSV / RGB / Opacity / `#RRGGBB` / `&HBBGGRR&` Color Picker**, Styling Assistant, Translation Assistant, Kanji Timer, Select Lines, Step-by-Step Find/Replace Next, In-Place Sort Selected Lines, Text Actor-Separator Parser, Spell & Typo Checker, Fonts Collector, Resolution Resampler, History Snapshots, and Bookmarks.

### 2. 1:1 Parity with `samgum/Aegisub` (SGMY Exclusive Features)
- **Fix Common Errors**: batch pass fixing overlapping display times, short gaps, short/long durations, trailing whitespace (incl. `U+3000`), and ASS whitespace-only empty lines (preserving `template*` and `\p1` drawings) as independent undo steps.
- **Music Lyrics Scroll Generator**: multi-line scrolling lyrics with cubic ease-out `\move` + `\t(\alpha\1c\fscx\fscy)`, `ScrollCurrent` / `ScrollDim` styles, single or auto-paired bilingual mode, and 7 resolution presets (incl. `4K` and vertical `1080×1920`).
- **CJK Text Suite**:
  - **Chinese Simplified ↔ Traditional Conversion** (Android ICU `Transliterator` + phrase protection dictionary, skipping `{...}` tags).
  - **Subtitle Text Cleanup**, **Stack-Based Paired Punctuation Checker**, and **Idempotent Japanese Furigana Annotator (`{*sgmy-furigana}`)**.
- **Timing Stitcher & Same-Style Overlap Checker**: millisecond or video-frame gap stitching + per-style overlap diagnostics.
- **Enhanced LRC & TTML Karaoke**: `<mm:ss.xx>` word/syllable LRC karaoke ↔ `{\kf}`, Apple Music trailing end markers, TTML `<span begin/end>` karaoke, and `ttm:role="x-bg"` background vocal splitting.
- **Subtitle Overflow & High-CPS Highlighting**, **HDR10 / HLG / Dolby Vision / BT.2020 Detection & 1080p Preview Optimization**, **`0.25×–4.0×` Pitch-Preserving Speed**, and **OpenAI-Compatible AI Subtitle Assistant** (DeepSeek, OpenRouter, Gemini, DashScope, Custom; volatile in-memory API key).

### 3. First-Class Keyboard, Mouse/Trackpad & Multi-Form-Factor UX
- **Keyboard & Mouse Ready**:
  - Input-focus guard ensures typing in text fields never triggers single-key playback/timing shortcuts; `Enter` commits & advances, `Shift+Enter` inserts `\N`, `Ctrl+B/I/U` toggles inline formatting, `Ctrl+G` opens Jump To, `Ctrl+X/C/V/A` manages rows.
  - Mouse right-click context menus on subtitle rows, project cards, video stage (`\pos` copy/move, zoom, safe area), and audio timeline (right-click sets End, `Shift+Click` sets Start, wheel pans, `Ctrl/Shift+Wheel` zooms, `Ctrl+Click` / `Shift+Click` multi-selects).
- **5 Adaptive Hardware Postures** via Jetpack `WindowInfoTracker` & `FoldingFeature`:
  - **`DESKTOP_THREE_ZONE`** (Tablets `≥ 840dp`, Unfolded Foldables, DeX): full menu bar + icon toolbar + draggable splitters.
  - **`BOOK_DUAL_PANE`** (Vertical hinge foldables): Video + Audio on left page · Hinge gutter · Toolbar + Edit Box + Grid on right page.
  - **`TABLETOP`** (Half-opened horizontal foldables): unobstructed Video Stage on upper screen · Touch Audio/Edit/Grid console on lower deck.
  - **`LANDSCAPE_SPLIT`** (Phones in landscape): draggable two-column studio.
  - **`PHONE_COMPACT`** (Phones in portrait): clean clutter-free studio with bottom navigation (**Subtitles** / **Timing** / **Typesetting** / **Styles** / **Export**) and categorized toolbox sheet.

---

## 📂 Supported Formats (11 Bidirectional Formats)

| Format | Extensions | Read | Write | Notes |
|---|---|---|---|---|
| **ASS** | `.ass` | ✅ | ✅ | Lossless `[Script Info]`, `[V4+ Styles]`, `[Events]`, `[Fonts]` & `[Graphics]` UUEncoded attachments |
| **SSA** | `.ssa` | ✅ | ✅ | SubStation Alpha v4.00 style & alignment conversion |
| **SRT** | `.srt` | ✅ | ✅ | SubRip timestamps & multi-line events |
| **WebVTT** | `.vtt` | ✅ | ✅ | HTML5 Web Video Text Tracks |
| **TTML / DFXP** | `.ttml`, `.dfxp`, `.xml` | ✅ | ✅ | Word-level `<span>` karaoke & Apple Music `ttm:role="x-bg"` harmony splitting |
| **Enhanced LRC** | `.lrc` | ✅ | ✅ | `[mm:ss.xx]`, `[offset:]`, `<mm:ss.xx>` syllable karaoke, Apple Music end timestamps |
| **MPEG-4 TTXT** | `.ttxt` | ✅ | ✅ | GPAC / MPEG-4 Timed Text v1.0 & v1.1 XML |
| **MicroDVD** | `.sub` | ✅ | ✅ | `{startFrame}{endFrame}` frame-based subtitles |
| **TranStation** | `.transtation.txt` | ✅ | ✅ | SMPTE-style TranStation subtitle scripts |
| **Adobe Encore** | `.encore.txt` | ✅ | ✅ | Encore DVD / NLE timecode scripts |
| **Plain Text** | `.txt` | ✅ | ✅ | Clean dialogue import/export + Actor separator & comment prefix parser |

---

## 🏗️ Project Architecture

```text
aegisub-android/
├── core/domain/      # Pure Kotlin/JVM engine: ASS/UUCodec attachments, 11 formats, Automation 4 & Karaoke Templater, SGMY & desktop ops
├── core/data/        # Room (projects, snapshots, bookmarks), DataStore preferences/hotkeys, AI analysis client, ICU Chinese converter
├── feature/preview/  # Media3 HDR player + playable Dummy Video clock, streaming waveform/STFT/keyframes, 8 visual tools, AudioBox
├── feature/editor/   # Dual-pane Global Storage + Script Style Manager, Styling & Translation Assistants
└── app/              # Unified 5-posture WorkspaceScreen, full keyboard/mouse handling, 34 Desktop + SGMY dialogs, Home, Settings
```

Detailed parity matrix: [docs/COMPARISON.md](docs/COMPARISON.md) · Engineering roadmap: [ROADMAP.md](ROADMAP.md).

---

## 🛠️ Building & Testing

### Prerequisites
- **JDK 17**
- **Android SDK 35** (`platforms;android-35`, `build-tools;35.0.0`)

### Commands
```bash
# Run all unit tests, Android Lint checks, and assemble Debug APK
./gradlew test lintDebug assembleDebug
```

---

## 📜 License

Released under the **BSD 3-Clause (Aegisub Compatible)** license — see [LICENCE](LICENCE).
Ported and adapted from [samgum/Aegisub](https://github.com/samgum/Aegisub) and upstream [Aegisub](http://aegisub.org). Maintained by **伤感咩吖 (`samgum`)**.
