# Roadmap — Aegisub Android

> Author / Maintainer: 伤感咩吖 (`samgum`)

## Current Status: ✅ v1.0.0 Full Desktop Aegisub + SGMY Parity

`aegisub-android` replicates **both the complete upstream desktop Aegisub feature set and all custom features of [samgum/Aegisub (Aegisub SGMY)](https://github.com/samgum/Aegisub)**, paired with native adaptive layouts for **Phones, Tablets / DeX, Foldables (Tabletop & Book)**, and full **Keyboard + Mouse/Trackpad** support.

---

## Completed Milestones

### Milestone 1 · Core Domain, 11 Formats, Attachments & SGMY Suite (`:core:domain`) ✅
- [x] Microsecond-precision `SubTime` + immutable `AssScript / AssEvent / AssStyle / AssInfo / AssColor / AssAttachment`
- [x] Byte-accurate `[Fonts]` & `[Graphics]` UUEncode codec (`AssUuCodec`, ported from `libaegisub/ass/uuencode.cpp`)
- [x] Bidirectional **11-format engine**: ASS, SSA, SRT, WebVTT, TTML/DFXP, Enhanced LRC, MPEG-4 TTXT (`.ttxt`), MicroDVD (`.sub`), TranStation, Adobe Encore, and Plain Text (with 17 ported upstream `lyric_readers.cpp` edge-case tests)
- [x] **Automation 4 & Karaoke Templater**: `AutomationEngine` porting `kara-templater`, `cleantags`, `strip-tags`, `macro-1-edgeblur`, `macro-2-mkfullwitdh`, `karaoke-auto-leadin`, and `select-overlaps`
- [x] **Full Desktop Batch Engines**: 11-field `PasteOverFields`, 4-in-1 `FullTimingProcessor`, `SortSelectedLines`, `TextImportProcessor`, `TimecodesIo` (`v1/v2`), `FindReplaceStepper`, and multi-charset `ExportPipeline`
- [x] **SGMY Exclusive Domain Suite**: `FixCommonErrors`, `LyricScrollGenerator`, `ChineseConverter`, `SubtitleTextCleaner`, `PairedPunctuationChecker`, `FuriganaAnnotator`, `StitchTimings`, `StyleOverlapChecker`, and `SubtitleOverflowDetector`

### Milestone 2 · Data, HDR Media, Playable Dummy Video & Zoomable Audio (`:core:data`, `:feature:*`) ✅
- [x] Room persistence (projects, snapshots, bookmarks) + DataStore preferences & customizable hotkeys
- [x] `AiAnalysisService` (DeepSeek, OpenRouter, Gemini, DashScope, Custom OpenAI-compatible presets; volatile in-memory API key)
- [x] Media3 `VideoPlayer` with `0.25×–4.0×` pitch-preserving speed, `playRange`, HDR10/HLG/Dolby Vision/BT.2020 detection, **plus playable monotonic Dummy Video clock**
- [x] Streaming OOM-safe `WaveformExtractor` + STFT spectrogram + keyframe scanner
- [x] Aspect-locked `SubtitleOverlay` & 8-mode `VisualTypesettingOverlay` + zoomable `AegisubAudioBox`
- [x] Dual-pane Global Style Storage + Script Styles Manager

### Milestone 3 · Clean Adaptive Studio UI & Full Keyboard/Mouse Support (`:app`) ✅
- [x] Clean, intuitive Material 3 Studio UI across 5 hardware postures (`DESKTOP_THREE_ZONE`, `BOOK_DUAL_PANE`, `TABLETOP`, `LANDSCAPE_SPLIT`, `PHONE_COMPACT`)
- [x] Complete physical keyboard & mouse/trackpad support (focus-isolated hotkeys, right-click menus across grid/projects/video/audio, `Ctrl+Click` / `Shift+Click`, wheel pan & zoom, hover cursors)
- [x] All 34 Desktop Aegisub & SGMY engineering dialogs wired end-to-end
- [x] Bilingual documentation ([README.md](README.md) ↔ [README_CN.md](README_CN.md)) + GitHub Actions CI

---

## Future Horizons
- [ ] Optional native `libass` JNI pixel-exact renderer backend alongside the Compose real-time vector overlay
- [ ] Embedded Matroska (`.mkv`) subtitle track demuxing & font attachment extraction
- [ ] User-supplied external `.lua` / `.moon` script interpreter sandbox
