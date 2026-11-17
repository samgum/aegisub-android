package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.toPersistentList
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/**
 * 桌面 Aegisub 其余内置工程字幕格式完整复刻：
 * 1. [TtxtFormat]：MPEG-4 Timed Text (`.ttxt`, v1.0 & v1.1 双向读写，移植自 `subtitle_format_ttxt.cpp`)
 * 2. [TranStationFormat]：TranStation SMPTE 时间码广播字幕 (`.transtation.txt`，移植自 `subtitle_format_transtation.cpp`)
 * 3. [EncoreFormat]：Adobe Encore DVD/Blu-ray 字幕 (`.encore.txt`，移植自 `subtitle_format_encore.cpp`)
 *
 * @author 伤感咩吖
 */
object TtxtFormat : SubtitleFormat {
    override val name = "ttxt"
    override val extensions = listOf(".ttxt")

    override fun canRead(content: String): Boolean {
        val head = content.trimStart('\uFEFF', ' ', '\t', '\r', '\n').take(512)
        return head.startsWith("<") && head.contains("<TextStream", ignoreCase = false)
    }

    override fun read(text: String, options: ReadOptions): AssScript {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(text.trimStart('\uFEFF'))))
        val root = doc.documentElement ?: error("Invalid TTXT")
        require(root.nodeName == "TextStream") { "Root must be TextStream" }
        val version = when (root.getAttribute("version")) {
            "1.0" -> 0
            "1.1", "" -> 1
            else -> 1
        }

        data class RawSample(val timeMs: Long, val text: String?)
        val samples = mutableListOf<RawSample>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == "TextSample") {
                val el = child as Element
                val sampleTimeStr = el.getAttribute("sampleTime").ifEmpty { "00:00:00.000" }
                val timeMs = TtmlFormat.parseTtmlTimeMs(sampleTimeStr).coerceAtLeast(0L)
                val body = if (version == 0) {
                    val attr = el.getAttribute("text")
                    if (attr.isEmpty()) null else parseV10Quoted(attr)
                } else {
                    collectSampleText(el).takeIf { it.isNotEmpty() }
                }
                samples += RawSample(timeMs, body)
            }
        }

        val events = mutableListOf<AssEvent>()
        var idx = 0L
        for (s in samples.indices) {
            val cur = samples[s]
            val body = cur.text ?: continue
            val endMs = samples.getOrNull(s + 1)?.timeMs ?: (cur.timeMs + 3_000L)
            events += AssEvent(
                id = idx,
                row = idx.toInt(),
                start = SubTime.ofMillis(cur.timeMs),
                end = SubTime.ofMillis(endMs.coerceAtLeast(cur.timeMs)),
                text = body,
            )
            idx++
        }
        return AssScript.default().withEvents(events.toPersistentList())
    }

    private fun parseV10Quoted(raw: String): String {
        val sb = StringBuilder()
        var inQuote = false
        var first = true
        for (ch in raw) {
            if (ch == '\'') {
                if (!inQuote && !first) sb.append("\\N")
                first = false
                inQuote = !inQuote
            } else if (inQuote) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun collectSampleText(el: Element): String {
        val lines = mutableListOf<String>()
        val children = el.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            when {
                n.nodeType == Node.TEXT_NODE || n.nodeType == Node.CDATA_SECTION_NODE -> {
                    val v = n.nodeValue?.trim('\r', '\n') ?: ""
                    if (v.isNotEmpty()) lines += v
                }
                n.nodeType == Node.ELEMENT_NODE && n.nodeName.equals("br", true) -> lines += "\\N"
            }
        }
        return lines.joinToString("").replace("\n", "\\N").trim()
    }

    override fun write(script: AssScript, options: WriteOptions): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<!-- GPAC 3GPP Text Stream -->""")
        appendLine("""<TextStream version="1.1">""")
        appendLine("""  <TextStreamHeader width="400" height="60" layer="0" translation_x="0" translation_y="0">""")
        appendLine("""    <TextSampleDescription horizontalJustification="center" verticalJustification="bottom" backColor="0 0 0 0" verticalText="no" fillTextRegion="no" continuousKaraoke="no" scroll="None"/>""")
        appendLine("""  </TextStreamHeader>""")

        val sorted = script.events.filter { !it.comment }.sortedBy { it.start.millis }
        var prevEndMs = 0L
        for (ev in sorted) {
            if (ev.start.millis > prevEndMs) {
                appendLine("""  <TextSample sampleTime="${fmtClock(prevEndMs)}" xml:space="preserve"></TextSample>""")
            }
            val escaped = ev.strippedText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\\N", "\n")
                .replace("\\n", "\n")
            appendLine("""  <TextSample sampleTime="${fmtClock(ev.start.millis)}" xml:space="preserve">$escaped</TextSample>""")
            prevEndMs = ev.end.millis
        }
        if (sorted.isNotEmpty()) {
            appendLine("""  <TextSample sampleTime="${fmtClock(prevEndMs)}" xml:space="preserve"></TextSample>""")
        }
        appendLine("""</TextStream>""")
    }

    private fun fmtClock(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val h = safe / 3_600_000L
        val m = (safe % 3_600_000L) / 60_000L
        val s = (safe % 60_000L) / 1_000L
        val mil = safe % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, mil)
    }
}

object TranStationFormat : SubtitleFormat {
    override val name = "transtation"
    override val extensions = listOf(".transtation.txt")

    private val headerRe = Regex("""^SUB\[\d[ LR][NI]\s+(\d{2}:\d{2}:\d{2}:\d{2})>(\d{2}:\d{2}:\d{2}:\d{2})]""")

    override fun canRead(content: String): Boolean =
        content.lineSequence().any { headerRe.containsMatchIn(it.trim()) }

    override fun read(text: String, options: ReadOptions): AssScript {
        val fps = 25.0
        val events = mutableListOf<AssEvent>()
        val lines = text.replace("\r\n", "\n").lines()
        var i = 0
        var id = 0L
        while (i < lines.size) {
            val m = headerRe.find(lines[i].trim())
            if (m != null) {
                val startMs = SmpteCodec.fromSmpte(m.groupValues[1], fps)
                val endMs = SmpteCodec.fromSmpte(m.groupValues[2], fps)
                i++
                val body = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank() && !lines[i].startsWith("SUB[")) {
                    body += lines[i]
                    i++
                }
                events += AssEvent(
                    id = id,
                    row = id.toInt(),
                    start = SubTime.ofMillis(startMs),
                    end = SubTime.ofMillis(endMs.coerceAtLeast(startMs)),
                    text = body.joinToString("\\N"),
                )
                id++
            } else {
                i++
            }
        }
        return AssScript.default().withEvents(events.toPersistentList())
    }

    override fun write(script: AssScript, options: WriteOptions): String = buildString {
        val fps = 25.0
        val styleMap = script.styles.associateBy { it.name }
        val sorted = script.events.filter { !it.comment }.sortedBy { it.start.millis }
        sorted.forEachIndexed { idx, ev ->
            val st = styleMap[ev.style]
            val align = st?.alignment ?: 2
            val valign = when {
                align >= 7 -> 9
                align >= 4 -> 4
                else -> 0
            }
            val halign = when (align) {
                1, 4, 7 -> "L"
                3, 6, 9 -> "R"
                else -> " "
            }
            val type = if (st?.italic == true || ev.text.contains("\\i1")) "I" else "N"
            val nextStart = sorted.getOrNull(idx + 1)?.start?.millis ?: -1L
            val endMs = if (nextStart > 0 && ev.end.millis == nextStart) (ev.end.millis - 40L).coerceAtLeast(ev.start.millis) else ev.end.millis
            append("SUB[").append(valign).append(halign).append(type).append(' ')
                .append(SmpteCodec.toSmpte(ev.start.millis, fps, ':')).append('>')
                .append(SmpteCodec.toSmpte(endMs, fps, ':')).append("]\r\n")
            append(ev.strippedText.replace("\\N", "\r\n").replace("\\n", "\r\n")).append("\r\n")
            if (idx < sorted.lastIndex) append("\r\n")
        }
        append("SUB[\r\n")
    }
}

object EncoreFormat : SubtitleFormat {
    override val name = "encore"
    override val extensions = listOf(".encore.txt")

    private val lineRe = Regex("""^(\d+)\s+(\d{2}[:;]\d{2}[:;]\d{2}[:;]\d{2})\s+(\d{2}[:;]\d{2}[:;]\d{2}[:;]\d{2})\s+(.*)$""")

    override fun canRead(content: String): Boolean =
        content.lineSequence().take(10).any { lineRe.matches(it.trim()) }

    override fun read(text: String, options: ReadOptions): AssScript {
        val events = mutableListOf<AssEvent>()
        var idx = 0L
        for (raw in text.replace("\r\n", "\n").lineSequence()) {
            val m = lineRe.matchEntire(raw.trim())
            if (m != null) {
                val drop = m.groupValues[2].contains(';')
                val fps = if (drop) 29.97 else 25.0
                val s = SmpteCodec.fromSmpte(m.groupValues[2], fps)
                val e = SmpteCodec.fromSmpte(m.groupValues[3], fps)
                events += AssEvent(
                    id = idx,
                    row = idx.toInt(),
                    start = SubTime.ofMillis(s),
                    end = SubTime.ofMillis(e.coerceAtLeast(s)),
                    text = m.groupValues[4],
                )
                idx++
            } else if (raw.isNotBlank() && events.isNotEmpty()) {
                val last = events.removeAt(events.lastIndex)
                events += last.copy(text = last.text + "\\N" + raw.trim())
            }
        }
        return AssScript.default().withEvents(events.toPersistentList())
    }

    override fun write(script: AssScript, options: WriteOptions): String = buildString {
        val fps = 29.97
        val sorted = script.events.filter { !it.comment }.sortedBy { it.start.millis }
        sorted.forEachIndexed { idx, ev ->
            val body = ev.strippedText.replace("\\N", "\r\n").replace("\\n", "\r\n")
            append(idx + 1).append(' ')
                .append(SmpteCodec.toSmpte(ev.start.millis, fps, ';')).append(' ')
                .append(SmpteCodec.toSmpte(ev.end.millis, fps, ';')).append(' ')
                .append(body).append("\r\n")
        }
    }
}

internal object SmpteCodec {
    fun toSmpte(ms: Long, fps: Double, frameSep: Char = ':'): String {
        val safe = ms.coerceAtLeast(0L)
        val totalSec = safe / 1_000L
        val h = totalSec / 3_600L
        val m = (totalSec % 3_600L) / 60L
        val s = totalSec % 60L
        val f = (((safe % 1_000L) / 1_000.0) * fps).toInt().coerceIn(0, fps.roundToLong().toInt() - 1)
        return String.format(Locale.US, "%02d:%02d:%02d%c%02d", h, m, s, frameSep, f)
    }

    fun fromSmpte(raw: String, fps: Double): Long {
        val p = raw.split(':', ';')
        if (p.size != 4) return 0L
        val h = p[0].toLongOrNull() ?: 0L
        val m = p[1].toLongOrNull() ?: 0L
        val s = p[2].toLongOrNull() ?: 0L
        val f = p[3].toLongOrNull() ?: 0L
        return (h * 3_600L + m * 60L + s) * 1_000L + ((f / fps) * 1_000.0).roundToLong()
    }
}
