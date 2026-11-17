# Desktop `samgum/Aegisub` (SGMY) ↔ `aegisub-android` Feature Parity Matrix

> Updated for **v2.0.0-sgmy** · Author / Maintainer: 伤感咩吖 (`samgum`)

This matrix compares **[samgum/Aegisub (Aegisub SGMY)](https://github.com/samgum/Aegisub)** on desktop with **Aegisub SGMY Android**.

---

## 1. `samgum/Aegisub` (SGMY) Exclusive Features

| SGMY Desktop Feature | Android v2.0 | Implementation Details |
|---|---|---|
| **Fix Common Errors** (`dialog_timing_tools.cpp`) | ✅ | Overlaps, short gaps, short/long durations, trailing `U+3000` whitespace, ASS whitespace-only empty lines (preserving `template*` & `\p1`), independent undo commits |
| **Music Lyrics Scroll Generator** (`apply_lyric_scroll`) | ✅ | Single & Bilingual auto-pairing, 7 resolution presets (incl. vertical `1080×1920`), `ScrollCurrent`/`ScrollDim` styles, cubic ease-out `\move` + `\t` animation |
| **Chinese Simplified ↔ Traditional (ICU)** | ✅ | Android ICU `Transliterator` + phrase dictionary fallback, skips `{...}` tags, style & selection filters |
| **Subtitle Text Cleanup** | ✅ | Full-width commas, Chinese period smart spacing/removal, smart quotes, double-space collapsing |
| **Paired Punctuation Checker** | ✅ | Stack-based CJK & Western bracket/quote pairing diagnostics with 1-tap row jump |
| **Japanese Furigana Annotator** | ✅ | Kanji extraction, `@row#occ:term=reading` & global mapping, Hiragana/Katakana, above/below ruby, idempotent `{*sgmy-furigana}` update |
| **Stitch Timings** | ✅ | `Previous Line End` / `Next Line Start` anchors, millisecond or video-frame boundary snapping |
| **Same-Style Overlap Checker** | ✅ | Per-style exact, same-start, same-end, and crossing overlap audit with clickable examples |
| **Enhanced LRC Import/Export** (`subtitle_format_lrc.cpp`) | ✅ | `[offset:]`, `<mm:ss.xx>` inline karaoke → `{\kf}`, Apple Music trailing line-end markers, bidirectional export |
| **TTML Import/Export** (`subtitle_format_ttml.cpp`) | ✅ | Clock/offset times, `<span begin/end>` word karaoke → `{\kf}`, `ttm:role="x-bg"` harmony row splitting |
| **Subtitle Overflow Highlighting** (`subtitle_overflow.cpp`) | ✅ | PlayRes width estimation + CPS warning badge in both grid and edit box |
| **HDR / Dolby Vision / BT.2020 Compatibility** | ✅ | Media3 HDR10/HLG/Dolby Vision metadata detection + HUD badge + 1080p HDR preview downscale toggle |
| **0.25×–4.0× Pitch-Preserving Speed** | ✅ | ExoPlayer pitch-preserving `PlaybackParameters` (`0.25×` to `4.0×`) + keyboard hotkeys |
| **AI Subtitle Analysis** (`dialog_ai_analysis.cpp`) | ✅ | DeepSeek, OpenRouter, Gemini, DashScope & Custom OpenAI-compatible presets; Grammar / Translate / Polish modes; session-only API key |

---

## 2. Classical Desktop Aegisub Features

| Category | Feature | Android v2.0 | Notes |
|---|---|---|---|
| **Formats** | ASS / SSA / SRT / WebVTT / TTML / LRC / MicroDVD / TXT | ✅ | Auto-detection + configurable export precision + SAF & `ACTION_VIEW` intent |
| **Video Stage** | Aspect-locked video + Dummy video canvas + Safe-area guides + Pinch zoom | ✅ | Eliminates letterbox `\pos` coordinate drift |
| **Visual Typesetting** | Crosshair, `\pos`, `\move`, `\frz`, `\frx/\fry+\org`, `\fscx/\fscy`, Rect `\clip`, Vector `\clip` | ✅ | All 8 desktop visual typesetting tools on left strip |
| **Dynamic Overlay** | Layer order, `\an1..9`, `\move`, `\fad/\fade`, `\t(...)`, `\kf` sweep, multi-span colors | ✅ | Real-time Compose canvas evaluation |
| **Audio Box** | Zoomable `2s–60s` waveform & STFT spectrogram, keyframe lines & snap, lead-in/out, 500ms auditions, karaoke drag | ✅ | Streaming OOM-safe decoder + `# keyframe format v1` I/O |
| **Edit Box** | Comment, Style, Actor, Effect, Layer, Start/End/Duration, L/R/V margins, `B/I/U/S`, `1c..4c`, ASS syntax highlighting | ✅ | `OffsetMapping.Identity` zero-cursor-drift highlighter |
| **Grid & Selection** | 9-column wide table / 2-line mobile card, `{tags}` / `☼` / hidden toggle, multi-select, full context menu | ✅ | Join/Split/Recombine/Make Continuous/Swap/Paste Over/Select Lines |
| **Styles & Tools** | Global Style Storage + Script Styles, Styling Assistant, Translation Assistant, Kanji Timer, Fonts Collector, Resample Resolution, Change Framerate | ✅ | Single-step undoable commits |
| **Adaptive UX** | Phone Portrait, Phone Landscape, Foldable Tabletop, Foldable Book, Tablet / DeX 3-Zone | ✅ | Jetpack `WindowInfoTracker` + `FoldingFeature` |
