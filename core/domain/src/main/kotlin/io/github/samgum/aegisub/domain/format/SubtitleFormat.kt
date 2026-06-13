package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssScript

/**
 * 字幕格式编解码抽象。
 *
 * @author 伤感咩吖
 */

/** 导出时间精度。 */
enum class TimePrecision { TWO_MS, THREE_MS, AUTO }

data class ReadOptions(val detectEncoding: Boolean = true)

data class WriteOptions(
    val timePrecision: TimePrecision = TimePrecision.AUTO,
    val stripTags: Boolean = false,
)

/** 字幕格式编解码器接口。 */
interface SubtitleFormat {
    val name: String
    val extensions: List<String>

    /** 内容嗅探：能否解析该文本。 */
    fun canRead(content: String): Boolean
    fun canWrite(fileName: String): Boolean = extensions.any { fileName.endsWith(it, ignoreCase = true) }

    fun read(text: String, options: ReadOptions = ReadOptions()): AssScript
    fun write(script: AssScript, options: WriteOptions = WriteOptions()): String
}
