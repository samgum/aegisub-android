package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.toPersistentList

/**
 * WebVTT（.vtt）编解码。时间用 `.` 分隔（HH:MM:SS.mmm），导出剥离覆盖标签。
 *
 * @author 伤感咩吖
 */
object VttFormat : SubtitleFormat {
    override val name = "vtt"
    override val extensions = listOf(".vtt")

    override fun canRead(content: String): Boolean =
        content.trimStart().startsWith("WEBVTT", ignoreCase = true)

    override fun read(text: String, options: ReadOptions): AssScript {
        val norm = text.replace("\r\n", "\n")
        val events = mutableListOf<AssEvent>()
        var idx = 0L
        for (block in norm.split("\n\n")) {
            val lines = block.trim().lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val tIdx = lines.indexOfFirst { it.contains("-->") }
            if (tIdx < 0) continue
            val (a, b) = lines[tIdx].split("-->")
            val start = SubTime.parseSrt(a.trim())
            val end = SubTime.parseSrt(b.trim())
            val body = lines.drop(tIdx + 1).joinToString("\n")
            events += AssEvent(id = idx++, row = events.size, start = start, end = end, text = body)
        }
        return AssScript(events = events.toPersistentList())
    }

    override fun write(script: AssScript, options: WriteOptions): String = buildString {
        appendLine("WEBVTT")
        script.events.forEach { e ->
            appendLine()
            appendLine("${e.start.toSrtString().replace(',', '.')} --> ${e.end.toSrtString().replace(',', '.')}")
            appendLine(e.strippedText)
        }
    }
}
