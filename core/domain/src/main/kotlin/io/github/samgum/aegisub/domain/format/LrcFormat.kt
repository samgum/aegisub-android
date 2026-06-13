package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssInfo
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.LrcTimeFormat
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.toPersistentList

/**
 * LRC 编解码：全新实现（上游 Aegisub 无 LRC）。支持 4 种时间格式与元数据标签。
 *
 * @author 伤感咩吖
 */
object LrcFormat : SubtitleFormat {
    override val name = "lrc"
    override val extensions = listOf(".lrc")
    private val timeTag = Regex("""\[(\d{1,2}:\d{2}[.:]\d{1,3})\]""")
    private val idTag = Regex("""^\[([a-z]+):(.*)]$""", RegexOption.IGNORE_CASE)

    override fun canRead(content: String): Boolean =
        content.lineSequence().any { timeTag.containsMatchIn(it) }

    override fun read(text: String, options: ReadOptions): AssScript {
        val info = mutableListOf<AssInfo>()
        val rawEvents = mutableListOf<Pair<SubTime, String>>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            val idm = idTag.matchEntire(line.trim())
            if (idm != null && !timeTag.containsMatchIn(idm.value)) {
                info += AssInfo(idm.groupValues[1], idm.groupValues[2].trim())
                continue
            }
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) continue
            val lyric = line.substring(tags.last().range.last + 1).trim()
            tags.forEach { m -> rawEvents += SubTime.parseLrc("[${m.groupValues[1]}]") to lyric }
        }
        rawEvents.sortBy { it.first.micros }
        val events = rawEvents.mapIndexed { i, (start, lyric) ->
            val end = rawEvents.getOrNull(i + 1)?.first ?: (start + SubTime.ofMillis(5_000))
            AssEvent(id = i.toLong(), row = i, start = start, end = end, text = lyric)
        }
        return AssScript(info = info.toPersistentList(), events = events.toPersistentList())
    }

    override fun write(script: AssScript, options: WriteOptions): String {
        val fmt = when (options.timePrecision) {
            TimePrecision.THREE_MS -> LrcTimeFormat.XXX
            TimePrecision.TWO_MS -> LrcTimeFormat.XX
            TimePrecision.AUTO -> LrcTimeFormat.XX
        }
        return buildString {
            script.info.forEach { appendLine("[${it.key}:${it.value}]") }
            script.events.forEach { appendLine("${it.start.toLrcString(fmt)}${it.strippedText}") }
        }
    }
}
