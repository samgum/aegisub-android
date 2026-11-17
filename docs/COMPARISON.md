# Upstream Desktop Aegisub & `samgum/Aegisub` (SGMY) ↔ `aegisub-android` Feature Parity Matrix

> Updated for **v1.0.0** · Author / Maintainer: 伤感咩吖 (`samgum`)

This matrix documents parity between **upstream desktop Aegisub**, **[samgum/Aegisub (Aegisub SGMY)](https://github.com/samgum/Aegisub)**, and **Aegisub Android**.

---

## 1. Upstream Desktop Aegisub Core Features

| Category | Desktop Aegisub Feature | Android v2.0 | Implementation Details |
|---|---|---|---|
| **Formats (11)** | ASS, SSA, SRT, WebVTT, TTML/DFXP, LRC, MicroDVD, MPEG-4 TTXT, TranStation, Adobe Encore, Plain Text | ✅ | Lossless microsecond `SubTime`, auto-detection, SAF import/export, `ACTION_VIEW` file open |
| **ASS Attachments** | `[Fonts]` & `[Graphics]` UUEncoded embedded files (`AssAttachment` / `uuencode.cpp`) | ✅ | Byte-accurate 6-bit `+33` 80-char codec (`AssUuCodec`), attach font/graphic, extract to file, lossless round-trip |
| **Automation 4** | `Karaoke Templater`, `Clean Tags`, `Strip Tags`, `Add Edge Blur`, `Make Fullwidth`, `Karaoke Auto-LeadIn`, `Select Overlaps` | ✅ | Dedicated `Automation` menu + `AutomationEngine` supporting `template line/pre-line/syl/char`, `$vars`, `!expr!`, `repeat N` |
| **Export Pipeline** | Export Filters (`Karaoke Templater`, `Clean Tags`, `Transform Framerate`, `Strip Tags`) + Charset encoding | ✅ | `ExportPipeline` outputting exact bytes in `UTF-8`, `UTF-8 BOM`, `UTF-16LE`, `GB18030`, `Big5`, `Shift_JIS` |
| **Video & Dummy Video** | Hardware Video + Playable Dummy Video + Jump To Frame/Time (`Ctrl+G`) + Video Details + `video_context` menu | ✅ | Monotonic virtual playback clock (`startDummyClock`), checkerboard/solid color, CFR & VFR `# timecode format v1/v2` I/O |
| **Visual Typesetting** | Crosshair, `\pos`, `\move`, `\frz`, `\frx/\fry+\org`, `\fscx/\fscy`, Rect `\clip/\iclip`, Vector `\clip/\iclip` | ✅ | Aspect-locked overlay, mouse live `(x, y)` HUD, pinch & `Ctrl+Wheel` zoom (`50%–300%`), safe-area guides |
| **Audio Timing Box** | Waveform + STFT Spectrogram + Keyframe lines & snap + Karaoke syllable drag + Segment auditions | ✅ | `2s–60s` zoomable viewport, left-click Start / right-click End, wheel pan, `Ctrl/Shift+Wheel` zoom, `# keyframe format v1` I/O |
| **Edit Box & Color** | Full event fields, `B/I/U/S`, `1c..4c` + `1a..4a`, zero-drift ASS syntax highlighting, HSV/RGB/Hex/ASS Color Picker | ✅ | Focus-guarded hotkeys, `Enter` commit & advance, `Shift+Enter` `\N`, full HSV + RGB + Alpha + `#RRGGBB` / `&HBBGGRR&` picker |
| **Paste Over** | 11-field `DialogPasteOver` (`Comment`, `Layer`, `Start`, `End`, `Style`, `Actor`, `MarginL/R/V`, `Effect`, `Text`) | ✅ | Supports both plain text lines and full copied ASS `Dialogue:` lines with quick field presets |
| **Timing Processor** | 4-in-1 `DialogTimingProcessor` (Style filter, safe Lead-In/Out, Make Adjacent + Bias, 4-threshold Keyframe Snap) | ✅ | Collision-safe `safe_time` lead-in/out + bias slider (`0..1`) + independent before/after keyframe snap distances |
| **Search, Sort & Check** | Find Next / Replace Next / Field selector, Sort All or Selected Only in-place, Text Actor Separator, Spell Checker | ✅ | `FindReplaceStepper` (incl. `STRIPPED_TEXT`), `SortSelectedLines`, `TextImportProcessor`, `SpellCheckerDialog` |
| **Styles & Assistants** | Dual-pane Style Manager (`Storage` ↔ `Script`), Styling Assistant, Translation Assistant, Kanji Timer, Fonts Collector, Resample Resolution | ✅ | Live checkerboard style preview, 9-grid `\an` selector, missing style extractor, full undo integration |

---

## 2. `samgum/Aegisub` (SGMY) Exclusive Features

| SGMY Desktop Feature | Android v2.0 | Implementation Details |
|---|---|---|
| **Fix Common Errors** (`dialog_timing_tools.cpp`) | ✅ | Overlaps, short gaps, short/long durations, trailing `U+3000` whitespace, ASS whitespace-only empty lines (preserving `template*` & `\p1`), staged undo commits |
| **Music Lyrics Scroll Generator** (`apply_lyric_scroll`) | ✅ | Single & Bilingual auto-pairing, 7 resolution presets (incl. vertical `1080×1920`), `ScrollCurrent`/`ScrollDim` styles, cubic ease-out `\move` + `\t` animation |
| **Chinese Simplified ↔ Traditional (ICU)** | ✅ | Android ICU `Transliterator` + phrase dictionary fallback, skips `{...}` tags, style & selection filters |
| **Subtitle Text Cleanup** | ✅ | Full-width commas, Chinese period smart spacing/removal, smart quotes, double-space collapsing |
| **Paired Punctuation Checker** | ✅ | Stack-based CJK & Western bracket/quote pairing diagnostics with 1-tap row jump |
| **Japanese Furigana Annotator** | ✅ | Kanji extraction, `@row#occ:term=reading` & global mapping, Hiragana/Katakana, above/below ruby, idempotent `{*sgmy-furigana}` update |
| **Stitch Timings & Style Overlap Checker** | ✅ | `Previous End` / `Next Start` anchors (ms or frame boundary) + per-style overlap audit |
| **Enhanced LRC & TTML Lyrics** (`lyric_readers.cpp` verified) | ✅ | `[offset:]`, `<mm:ss.xx>` word karaoke → `{\kf}`, Apple Music trailing end markers, TTML `<span>` karaoke & `ttm:role="x-bg"` harmony splitting (17 upstream C++ parity tests ported) |
| **Subtitle Overflow Highlighting** (`subtitle_overflow.cpp`) | ✅ | PlayRes width estimation + CPS warning badge in grid and edit box |
| **HDR / Dolby Vision / BT.2020 & 0.25×–4.0× Speed** | ✅ | Media3 HDR10/HLG/Dolby Vision metadata detection + 1080p HDR preview downscale toggle + pitch-preserving speed |
| **AI Subtitle Analysis** (`dialog_ai_analysis.cpp`) | ✅ | DeepSeek, OpenRouter, Gemini, DashScope & Custom OpenAI-compatible presets; session-only API key |

---

## 3. Multi-Device & Input Adaptation

| Capability | Android v2.0 | Details |
|---|---|---|
| **Phone Portrait & Landscape** | ✅ | Clean clutter-free layout, collapsible media bar when no video is attached, 2-column categorized toolbox sheet |
| **Tablet & Samsung DeX** | ✅ | Full desktop 8-menu bar (`File`, `Edit`, `Subtitle`, `Timing`, `Video/Audio`, `Automation`, `Tools`, `View`) + draggable 3-zone splitters |
| **Foldables (Book & Tabletop)** | ✅ | Jetpack `WindowInfoTracker` + `FoldingFeature` hinge-aware dual-page and laptop-style postures |
| **Keyboard & Mouse / Trackpad** | ✅ | Input-focus hotkey guard, right-click menus, `Ctrl+Click` / `Shift+Click`, wheel pan & `Ctrl/Shift+Wheel` zoom, hover cursors |
