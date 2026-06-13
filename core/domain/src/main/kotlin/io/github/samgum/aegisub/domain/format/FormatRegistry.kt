package io.github.samgum.aegisub.domain.format

/**
 * 自动检测注册表：扩展名 + 内容嗅探。
 *
 * @author 伤感咩吖
 */
object FormatRegistry {
    val formats: List<SubtitleFormat> = listOf(
        AssFormat, SsaFormat, SrtFormat, VttFormat, LrcFormat, TxtFormat,
    )

    fun byName(name: String): SubtitleFormat? = formats.firstOrNull { it.name == name }

    fun detect(content: String): SubtitleFormat? =
        formats.firstOrNull { it.canRead(content) }

    fun detectByExtension(fileName: String): SubtitleFormat? =
        formats.firstOrNull { it.canWrite(fileName) }
}
