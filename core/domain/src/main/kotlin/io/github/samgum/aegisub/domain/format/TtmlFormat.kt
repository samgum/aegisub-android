package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.edit.KaraokeSyllables
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
 * TTML (Timed Text Markup Language) / DFXP 字幕与逐词歌词编解码
 * （完整复刻 samgum/Aegisub SGMY `subtitle_format_ttml.cpp` 并支持双向导出）：
 * - 支持时钟时间（`HH:MM:SS.mmm`、`MM:SS.mmm`、`SS.mmm`）与偏移时间（`ms`、`s`、`m`、`h`）
 * - 支持 `<p begin="..." end="..." dur="...">`、`<br/>` → `\N`
 * - 支持逐词 `<span begin="..." end="...">` 自动转换为 ASS `{\kf<cs>}` 扫词卡拉OK
 * - 支持 Apple Music `ttm:role="x-bg"` 和声背景人声自动拆分为并行事件行
 * - 支持无时间轴 `<p>`（`itunes:timing="None"`）纯歌词导入
 *
 * @author 伤感咩吖
 */
object TtmlFormat : SubtitleFormat {
    override val name = "ttml"
    override val extensions = listOf(".ttml", ".dfxp")

    private data class TtmlSegment(
        val beginMs: Long,
        val endMs: Long,
        val text: String,
        val bg: Boolean = false,
    )

    private data class TtmlParagraph(
        val beginMs: Long,
        val endMs: Long,
        val text: String,
    )

    override fun canRead(content: String): Boolean {
        val trimmed = content.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (!trimmed.startsWith("<")) return false
        return Regex("""<(?:[a-zA-Z0-9_]+:)?tt(?:\s|>)""").containsMatchIn(trimmed.take(1024))
    }

    /** 解析 TTML 时钟或偏移时间为毫秒；非法返回 -1L。 */
    internal fun parseTtmlTimeMs(raw: String): Long {
        val value = raw.trim()
        if (value.isEmpty() || value.length >= 64) return -1L

        val lastChar = value.last()
        if (!lastChar.isDigit()) {
            val (numStr, scale) = when {
                value.endsWith("ms") -> value.dropLast(2) to 1.0
                value.endsWith("s") -> value.dropLast(1) to 1_000.0
                value.endsWith("m") -> value.dropLast(1) to 60_000.0
                value.endsWith("h") -> value.dropLast(1) to 3_600_000.0
                else -> return -1L
            }
            val amount = numStr.toDoubleOrNull() ?: return -1L
            if (!amount.isFinite() || amount < 0.0) return -1L
            val msDouble = amount * scale
            if (!msDouble.isFinite() || msDouble < 0.0 || msDouble > 35_999_990.0) return -1L
            return msDouble.roundToLong()
        }

        val normalized = value.replace(',', '.')
        val parts = normalized.split(':')
        val totalMsDouble = when (parts.size) {
            1 -> {
                val sec = parts[0].toDoubleOrNull() ?: return -1L
                if (!sec.isFinite() || sec < 0.0) return -1L
                sec * 1_000.0
            }
            2 -> {
                val min = parts[0].toLongOrNull() ?: return -1L
                val sec = parts[1].toDoubleOrNull() ?: return -1L
                if (min < 0 || !sec.isFinite() || sec < 0.0 || sec >= 60.0) return -1L
                (min * 60.0 + sec) * 1_000.0
            }
            3 -> {
                val hr = parts[0].toLongOrNull() ?: return -1L
                val min = parts[1].toLongOrNull() ?: return -1L
                val sec = parts[2].toDoubleOrNull() ?: return -1L
                if (hr < 0 || min !in 0..59 || !sec.isFinite() || sec < 0.0 || sec >= 60.0) return -1L
                (hr * 3_600.0 + min * 60.0 + sec) * 1_000.0
            }
            else -> return -1L
        }
        if (!totalMsDouble.isFinite() || totalMsDouble < 0.0 || totalMsDouble > 35_999_990.0) return -1L
        return totalMsDouble.roundToLong()
    }

    private fun localName(node: Node): String {
        val raw = node.localName ?: node.nodeName ?: ""
        return raw.substringAfter(':')
    }

    private fun attrByLocalName(el: Element, target: String): String? {
        val map = el.attributes ?: return null
        for (i in 0 until map.length) {
            val item = map.item(i)
            val name = (item.localName ?: item.nodeName ?: "").substringAfter(':')
            if (name == target) return item.nodeValue
        }
        return null
    }

    private fun collectVisibleText(node: Node): String = buildString {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> append(child.nodeValue ?: "")
                Node.ELEMENT_NODE -> {
                    if (localName(child) == "br") append("\\N")
                    else append(collectVisibleText(child))
                }
            }
        }
    }

    private fun buildParagraphContent(
        parent: Node,
        plain: StringBuilder,
        segments: MutableList<TtmlSegment>,
        bgVoice: Boolean = false,
    ) {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    val content = node.nodeValue ?: ""
                    if (segments.isNotEmpty()) {
                        val last = segments.removeAt(segments.lastIndex)
                        segments += last.copy(text = last.text + content)
                    } else {
                        plain.append(content)
                    }
                }
                Node.ELEMENT_NODE -> {
                    val el = node as Element
                    when (localName(el)) {
                        "br" -> {
                            if (segments.isNotEmpty()) {
                                val last = segments.removeAt(segments.lastIndex)
                                segments += last.copy(text = last.text + "\\N")
                            } else {
                                plain.append("\\N")
                            }
                        }
                        "span" -> {
                            val beginAttr = attrByLocalName(el, "begin")
                            val endAttr = attrByLocalName(el, "end")
                            val roleAttr = attrByLocalName(el, "role") ?: ""
                            val isBg = bgVoice || roleAttr.startsWith("x-bg")
                            val spanText = collectVisibleText(el)
                            val spanBegin = if (beginAttr != null) parseTtmlTimeMs(beginAttr) else -1L
                            val spanEnd = if (endAttr != null) parseTtmlTimeMs(endAttr) else -1L
                            if (spanBegin >= 0L && spanText.isNotEmpty()) {
                                segments += TtmlSegment(spanBegin, spanEnd, spanText, isBg)
                            } else if (spanText.isNotEmpty()) {
                                buildParagraphContent(el, plain, segments, isBg)
                            }
                        }
                        else -> buildParagraphContent(el, plain, segments, bgVoice)
                    }
                }
            }
        }
    }

    override fun read(text: String, options: ReadOptions): AssScript {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(text.trimStart('\uFEFF'))))
        val root = doc.documentElement ?: error("Invalid TTML: missing root element")
        require(localName(root) == "tt") { "Invalid TTML: root element is not <tt>" }

        val paragraphs = mutableListOf<TtmlParagraph>()
        val stack = ArrayDeque<Node>()
        stack.addLast(root)

        fun karaokeCs(ms: Long): Long = (ms + 5L) / 10L

        fun buildKaraokeChain(segs: List<TtmlSegment>, lineBegin: Long, lineEnd: Long): String = buildString {
            var elapsedCs = 0L
            for (s in segs.indices) {
                val seg = segs[s]
                val segBegin = seg.beginMs.coerceIn(lineBegin, lineEnd)
                val beginCs = karaokeCs(segBegin - lineBegin)
                if (beginCs > elapsedCs) {
                    append("{\\k").append(beginCs - elapsedCs).append("}")
                    elapsedCs = beginCs
                }
                var segEnd = if (seg.endMs > seg.beginMs) seg.endMs else lineEnd
                if (s + 1 < segs.size) segEnd = minOf(segEnd, segs[s + 1].beginMs)
                val endCs = karaokeCs(segEnd.coerceIn(segBegin, lineEnd) - lineBegin)
                val durCs = (endCs - elapsedCs).coerceAtLeast(0L)
                append("{\\kf").append(durCs).append("}").append(seg.text)
                elapsedCs += durCs
            }
        }

        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur.nodeType != Node.ELEMENT_NODE) continue
            val el = cur as Element
            if (localName(el) == "p") {
                val beginAttr = attrByLocalName(el, "begin")
                val endAttr = attrByLocalName(el, "end")
                val durAttr = attrByLocalName(el, "dur")

                var beginMs = if (beginAttr != null) parseTtmlTimeMs(beginAttr) else -1L
                val paragraphTimed = beginMs >= 0L
                if (!paragraphTimed && beginAttr != null) continue
                if (!paragraphTimed) beginMs = 0L

                var endMs = when {
                    endAttr != null -> parseTtmlTimeMs(endAttr).takeIf { it >= 0L } ?: beginMs
                    durAttr != null -> {
                        val dur = parseTtmlTimeMs(durAttr)
                        if (dur >= 0L) beginMs + dur else beginMs
                    }
                    else -> beginMs
                }
                if (endMs < beginMs) endMs = beginMs

                val plain = StringBuilder()
                val segments = mutableListOf<TtmlSegment>()
                buildParagraphContent(el, plain, segments)
                val preamble = plain.toString().trimStart()

                if (segments.isNotEmpty()) {
                    val sortedSegs = segments.sortedBy { it.beginMs }
                    if (endMs <= beginMs) {
                        if (!paragraphTimed) beginMs = sortedSegs.first().beginMs
                        endMs = sortedSegs.maxOf { s ->
                            if (s.endMs > s.beginMs) s.endMs else s.beginMs + 500L
                        }.coerceAtLeast(beginMs)
                    }

                    val (bgSegs, leadSegs) = sortedSegs.partition { it.bg }
                    val leadLastEnd = leadSegs.maxOfOrNull { if (it.endMs > it.beginMs) it.endMs else it.beginMs } ?: 0L
                    if (bgSegs.isNotEmpty() && leadSegs.isNotEmpty() && bgSegs.first().beginMs < leadLastEnd) {
                        val leadText = (preamble + buildKaraokeChain(leadSegs, beginMs, endMs)).trim()
                        val harmonyStart = maxOf(beginMs, bgSegs.first().beginMs)
                        val harmonyEnd = maxOf(endMs, harmonyStart)
                        val harmonyText = buildKaraokeChain(bgSegs, harmonyStart, harmonyEnd).trim()
                        if (leadText.isNotEmpty()) paragraphs += TtmlParagraph(beginMs, endMs, leadText)
                        if (harmonyText.isNotEmpty()) paragraphs += TtmlParagraph(harmonyStart, harmonyEnd, harmonyText)
                        continue
                    }

                    val fullText = (preamble + buildKaraokeChain(sortedSegs, beginMs, endMs)).trim()
                    if (fullText.isNotEmpty()) paragraphs += TtmlParagraph(beginMs, endMs, fullText)
                } else {
                    val textBody = preamble.trim()
                    if (textBody.isNotEmpty()) paragraphs += TtmlParagraph(beginMs, endMs, textBody)
                }
                continue
            }

            val children = el.childNodes
            for (i in children.length - 1 downTo 0) {
                stack.addLast(children.item(i))
            }
        }

        val sorted = paragraphs.sortedBy { it.beginMs }
        val events = sorted.mapIndexed { idx, p ->
            AssEvent(
                id = idx.toLong(),
                row = idx,
                start = SubTime.ofMillis(p.beginMs.coerceAtLeast(0L)),
                end = SubTime.ofMillis(p.endMs.coerceAtLeast(p.beginMs.coerceAtLeast(0L))),
                text = p.text,
            )
        }
        return AssScript.default().withEvents(events.toPersistentList())
    }

    private fun formatTtmlClock(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val h = safe / 3_600_000L
        val m = (safe % 3_600_000L) / 60_000L
        val s = (safe % 60_000L) / 1_000L
        val millis = safe % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, millis)
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    override fun write(script: AssScript, options: WriteOptions): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">""")
        appendLine("  <body>")
        appendLine("    <div>")
        for (event in script.events) {
            if (event.comment) continue
            val startAttr = formatTtmlClock(event.start.millis)
            val endAttr = formatTtmlClock(event.end.millis)
            val syllables = KaraokeSyllables.parse(event.text)
            if (syllables.isNotEmpty()) {
                append("""      <p begin="$startAttr" end="$endAttr">""")
                var cursorMs = event.start.millis
                for (syl in syllables) {
                    val durMs = syl.centiseconds * 10L
                    val segEnd = (cursorMs + durMs).coerceAtMost(event.end.millis.coerceAtLeast(cursorMs))
                    if (syl.text.isNotEmpty()) {
                        val encoded = escapeXml(syl.text).replace("\\N", "<br/>").replace("\\n", "<br/>")
                        append("""<span begin="${formatTtmlClock(cursorMs)}" end="${formatTtmlClock(segEnd)}">$encoded</span>""")
                    }
                    cursorMs += durMs
                }
                appendLine("</p>")
            } else {
                val body = escapeXml(event.strippedText).replace("\\N", "<br/>").replace("\\n", "<br/>")
                appendLine("""      <p begin="$startAttr" end="$endAttr">$body</p>""")
            }
        }
        appendLine("    </div>")
        appendLine("  </body>")
        appendLine("</tt>")
    }
}
