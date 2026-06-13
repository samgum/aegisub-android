package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.toPersistentList

/**
 * SRT 编解码。
 *
 * @author 伤感咩吖
 */
object SrtFormat : SubtitleFormat {
    override val name = "srt"
    override val extensions = listOf(".srt")

    private val timeLine = Regex("""\d{2}:\d{2}:\d{2}[,.]\d{3}\s*-->\s*\d{2}:\d{2}:\d{2}[,.]\d{3}""")

    override fun canRead(content: String): Boolean = timeLine.containsMatchIn(content)

    override fun read(text: String, options: ReadOptions): AssScript {
        val events = mutableListOf<AssEvent>()
        val blocks = text.replace("\r\n", "\n").split("\n\n")
        var idx = 0L
        for (block in blocks) {
            val lines = block.trim().lines().filter { it.isNotBlank() }
            if (lines.size < 2) continue
            val timeIdx = lines.indexOfFirst { timeLine.containsMatchIn(it) }
            if (timeIdx < 0) continue
            val (a, b) = lines[timeIdx].split("-->")
            val start = SubTime.parseSrt(a.trim())
            val end = SubTime.parseSrt(b.trim())
            val body = lines.drop(timeIdx + 1).joinToString("\n")
            events += AssEvent(id = idx++, row = events.size, start = start, end = end, text = body)
        }
        return AssScript(events = events.toPersistentList())
    }

    override fun write(script: AssScript, options: WriteOptions): String = buildString {
        script.events.forEachIndexed { i, e ->
            appendLine(i + 1)
            appendLine("${e.start.toSrtString()} --> ${e.end.toSrtString()}")
            appendLine(e.strippedText)
            if (i < script.events.size - 1) appendLine()
        }
    }
}
