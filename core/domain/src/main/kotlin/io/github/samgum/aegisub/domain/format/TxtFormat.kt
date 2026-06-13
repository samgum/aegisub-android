package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.toPersistentList

/**
 * 纯文本（TXT）编解码：逐行生成顺序事件。
 *
 * @author 伤感咩吖
 */
object TxtFormat : SubtitleFormat {
    override val name = "txt"
    override val extensions = listOf(".txt")
    private const val lineDurationMs = 2_000L

    override fun canRead(content: String): Boolean {
        // 纯文本兜底：不含任何已知格式标记
        return content.isNotBlank() &&
            !content.contains("[Script Info]", true) &&
            !content.contains("-->", true) &&
            !content.contains(Regex("""^\[\d{2}:\d{2}[.:]""", RegexOption.MULTILINE))
    }

    override fun read(text: String, options: ReadOptions): AssScript {
        val events = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { i, line ->
                val start = SubTime.ofMillis(i * lineDurationMs)
                AssEvent(
                    id = i.toLong(), row = i,
                    start = start,
                    end = start + SubTime.ofMillis(lineDurationMs),
                    text = line,
                )
            }.toList()
        return AssScript(events = events.toPersistentList())
    }

    override fun write(script: AssScript, options: WriteOptions): String =
        script.events.joinToString("\n") { it.strippedText }
}
