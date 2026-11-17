package io.github.samgum.aegisub.domain.format

/**
 * 字幕与歌词自动检测注册表（完整覆盖桌面 Aegisub + SGMY 全部 11 种格式）：
 * ASS / SSA / SRT / WebVTT / TTML(DFXP) / LRC(含增强型逐字LRC) / TTXT(MPEG-4) / MicroDVD(.sub) / TranStation / Adobe Encore / TXT。
 *
 * @author 伤感咩吖
 */
object FormatRegistry {
    val formats: List<SubtitleFormat> = listOf(
        AssFormat,
        SsaFormat,
        SrtFormat,
        VttFormat,
        TtmlFormat,
        TtxtFormat,
        LrcFormat,
        MicroDvdFormat,
        TranStationFormat,
        EncoreFormat,
        TxtFormat,
    )

    fun byName(name: String): SubtitleFormat? = formats.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun detect(content: String): SubtitleFormat? =
        formats.firstOrNull { it.canRead(content) }

    fun detectByExtension(fileName: String): SubtitleFormat? =
        formats.firstOrNull { it.canWrite(fileName) }
}
