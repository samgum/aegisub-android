package io.github.samgum.aegisub.domain.format

/**
 * 导入解析：从文件名 + 内容确定字幕格式与工程名。纯函数，可纯 JVM 单测。
 *
 * @author 伤感咩吖
 */
object SubtitleImport {

    /** 导入解析结果：工程名（去扩展名）+ 格式名（ass/ssa/srt/lrc/txt）。 */
    data class Resolved(val name: String, val format: String)

    /**
     * 优先按内容嗅探格式，其次按扩展名，最终回退 txt。
     *
     * @param fileName 原始文件名（含扩展名）
     * @param content 文件文本内容
     * @param fallbackName 文件名无主名时的回退工程名
     */
    fun resolve(fileName: String, content: String, fallbackName: String = "导入的字幕"): Resolved {
        val format = (FormatRegistry.detect(content) ?: FormatRegistry.detectByExtension(fileName))?.name ?: "txt"
        val base = fileName.substringBeforeLast('.', fileName).trim().ifBlank { fallbackName }
        return Resolved(name = base, format = format)
    }
}
