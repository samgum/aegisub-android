package io.github.samgum.aegisub.domain.format

/**
 * 自动检测注册表：扩展名 + 内容嗅探。
 * 支持 ASS / SSA / SRT / WebVTT / TTML(DFXP) / LRC(含增强型逐字LRC) / MicroDVD(.sub) / TXT。
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
        LrcFormat,
        MicroDvdFormat,
        TxtFormat,
    )

    fun byName(name: String): SubtitleFormat? = formats.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun detect(content: String): SubtitleFormat? =
        formats.firstOrNull { it.canRead(content) }

    fun detectByExtension(fileName: String): SubtitleFormat? =
        formats.firstOrNull { it.canWrite(fileName) }
}
