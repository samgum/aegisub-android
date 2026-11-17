# Roadmap — Aegisub SGMY Android

> Author / Maintainer: 伤感咩吖 (`samgum`)

## Current Status: ✅ v2.0.0-sgmy Complete Studio Overhaul

`aegisub-android` has completed its full architectural & UI overhaul to **1:1 replicate [samgum/Aegisub (Aegisub SGMY)](https://github.com/samgum/Aegisub)** while delivering native adaptive postures for **Phones, Tablets / DeX, and Foldables (Tabletop & Book)**.

---

## Completed Milestones

### Milestone 1 · Core Domain & Format Engine (`:core:domain`) ✅
- [x] Microsecond-precision `SubTime` + immutable `AssScript / AssEvent / AssStyle / AssInfo / AssColor`
- [x] Bidirectional **8-format engine**: ASS, SSA, SRT, WebVTT, **TTML/DFXP** (`<span>` word karaoke + `ttm:role="x-bg"` harmony), **Enhanced LRC** (`[offset:]`, `<mm:ss.xx>` syllable karaoke, Apple Music trailing end timestamps), **MicroDVD (`.sub`)**, and TXT
- [x] **SGMY Exclusive Domain Suite**:
  - [x] `FixCommonErrors` (short/long durations, overlap trimming, short gaps, trailing `U+3000` whitespace, ASS whitespace-only empty line removal with independent staged snapshots)
  - [x] `LyricScrollGenerator` (Single/Bilingual pairing, 7 resolution presets incl. vertical `1080×1920`, cubic ease-out `\move` + `\t` animation, `ScrollCurrent`/`ScrollDim`)
  - [x] `ChineseConverter` (ICU + phrase dictionary), `SubtitleTextCleaner`, `PairedPunctuationChecker`, `FuriganaAnnotator` (`{*sgmy-furigana}` idempotent ruby annotations)
  - [x] `StitchTimings` & `StyleOverlapChecker`
  - [x] `SubtitleOverflowDetector` (PlayRes width & CPS bounds check)
- [x] **Desktop Aegisub Engineering Tools**: `VisualTags` (pos, move, frx/fry/frz, org, fscx/fscy, fad/fade, rect & vector clip/iclip), `SelectLines`, `KanjiTimer`, `KeyframesIo` (`# keyframe format v1`), `FontsCollectorAnalyzer`, `ResolutionResampler`, `FramerateConverter`, `TimePostProcess`, `ActiveSubtitleResolver` (real-time `\move` / `\fad` / `\t` / `\kf` interpolation)

### Milestone 2 · Data, HDR Media, Zoomable Audio & Styles (`:core:data`, `:feature:*`) ✅
- [x] Room persistence (projects, manual snapshots, timeline bookmarks) + DataStore preferences & 34 customizable hotkeys
- [x] `AiAnalysisService` (DeepSeek, OpenRouter, Gemini, DashScope, Custom OpenAI-compatible presets; volatile in-memory API key)
- [x] Media3 `VideoPlayer` with `0.25×–4.0×` pitch-preserving speed, segment playback (`playRange`), HDR10/HLG/Dolby Vision/BT.2020 color detection, and 1080p HDR preview optimization
- [x] Streaming OOM-safe `WaveformExtractor` + STFT spectrogram + sync-frame keyframe scanner
- [x] Aspect-locked `SubtitleOverlay` & 8-mode `VisualTypesettingOverlay`
- [x] Zoomable `AegisubAudioBox` (`2s–60s` window, keyframe snap, ghost neighbor lines, karaoke syllable drag)
- [x] Dual-pane Global Style Storage + Script Styles Manager

### Milestone 3 · Unified Adaptive Studio Workspace (`:app`) ✅
- [x] Single-screen `WorkspaceScreen` replacing legacy split `EditorScreen` / `PreviewScreen`
- [x] Jetpack `WindowInfoTracker` + `FoldingFeature` 5-posture engine:
  - [x] `DESKTOP_THREE_ZONE` (Tablet / Unfolded / DeX resizable 3-zone desktop replica)
  - [x] `BOOK_DUAL_PANE` (Vertical foldable hinge dual-page studio)
  - [x] `TABLETOP` (Horizontal half-opened foldable laptop posture)
  - [x] `LANDSCAPE_SPLIT` (Phone landscape draggable 2-column studio)
  - [x] `PHONE_COMPACT` (Phone portrait 4-mode stage switcher + thumb navigation bar)
- [x] All 27 SGMY & desktop Aegisub dialogs/sheets integrated
- [x] Bilingual documentation ([README.md](README.md) & [README_CN.md](README_CN.md)) + GitHub Actions CI

---

## Future Horizons
- [ ] Optional native `libass` JNI pixel-exact renderer backend alongside the Compose real-time vector overlay
- [ ] Embedded Matroska (`.mkv`) subtitle track demuxing & font attachment extraction
- [ ] Lua / MoonScript Automation 4 sandboxed runtime
