package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.roundToLong

/**
 * MicroDVD (`.sub`) 帧时间码字幕格式：`{startFrame}{endFrame}Line1|Line2`。
 * 默认按 23.976 fps 换算（若首行含 `{1}{1}25.000` 帧率声明则自动采用该帧率）。
 *
 * @author 伤感咩吖
 */
object MicroDvdFormat : SubtitleFormat {
    override val name = "sub"
    override val extensions = listOf(".sub")

    private val LINE_RE = Regex("""^\{(\d+)\}\{(\d+)\}(.*)$""")
    private const val DEFAULT_FPS = 23.976

    override fun canRead(content: String): Boolean =
        content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.let { LINE_RE.matches(it) } == true

    override fun read(text: String, options: ReadOptions): AssScript {
        var fps = DEFAULT_FPS
        val events = mutableListOf<AssEvent>()
        var nextId = 0L
        for (raw in text.lineSequence()) {
            val m = LINE_RE.matchEntire(raw.trim()) ?: continue
            val fStart = m.groupValues[1].toLongOrNull() ?: continue
            val fEnd = m.groupValues[2].toLongOrNull() ?: continue
            val body = m.groupValues[3]
            if (nextId == 0L && (fStart == 1L && fEnd == 1L || fStart == 0L && fEnd == 0L)) {
                val declaredFps = body.trim().toDoubleOrNull()
                if (declaredFps != null && declaredFps in 1.0..240.0) {
                    fps = declaredFps
                    continue
                }
            }
            val startMs = ((fStart * 1000.0) / fps).roundToLong()
            val endMs = ((fEnd * 1000.0) / fps).roundToLong()
            val assText = body.replace("|", "\\N")
            events += AssEvent(
                id = nextId,
                row = nextId.toInt(),
                start = SubTime.ofMillis(startMs),
                end = SubTime.ofMillis(endMs.coerceAtLeast(startMs)),
                text = assText,
            )
            nextId++
        }
        return AssScript.default().withEvents(events.toPersistentList())
    }

    override fun write(script: AssScript, options: WriteOptions): String = buildString {
        val fps = DEFAULT_FPS
        for (event in script.events) {
            if (event.comment) continue
            val fStart = ((event.start.millis * fps) / 1000.0).roundToLong()
            val fEnd = ((event.end.millis * fps) / 1000.0).roundToLong()
            val body = event.strippedText.replace("\\N", "|").replace("\\n", "|").replace("\n", "|")
            appendLine("{$fStart}{$fEnd}$body")
        }
    }
}
