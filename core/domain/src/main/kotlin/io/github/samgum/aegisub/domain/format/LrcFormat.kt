package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.edit.KaraokeSyllables
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssInfo
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.LrcTimeFormat
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.toPersistentList

/**
 * LRC 歌词编解码（完整复刻 samgum/Aegisub SGMY `subtitle_format_lrc.cpp`）：
 * - 支持 `[mm:ss]`、`[mm:ss.x]`、`[mm:ss.xx]`、`[mm:ss.xxx]`、`[mm:ss:xx]`、`[mm:ss:xxx]` 全部时间变体
 * - 支持同一行前置多个时间标签展开
 * - 支持 `[offset:+/-ms]` 全局时间偏移
 * - 支持增强型 LRC（Enhanced / A2 word-level `<mm:ss.xx>` 逐字时间标签）自动映射为 ASS `{\kf<cs>}` 扫词卡拉OK，
 *   并识别 Apple Music 风格行尾 `<mm:ss.xx>` 结束标记
 * - 支持纯文本无时间轴 `.lrc` 回退导入与导出
 *
 * @author 伤感咩吖
 */
object LrcFormat : SubtitleFormat {
    override val name = "lrc"
    override val extensions = listOf(".lrc")

    private val bracketTagRe = Regex("""\[([^\[\]]+)]""")
    private val inlineWordTagRe = Regex("""<([^<>]+)>""")
    private val metadataTagRe = Regex("""^([a-z_]+):(.*)$""", RegexOption.IGNORE_CASE)

    private data class ParsedLrcLine(
        var startMs: Long,
        var endMarkerMs: Long = -1L,
        val plainText: String = "",
        val syllables: MutableList<Pair<Long, String>> = mutableListOf(),
        val hasSyllables: Boolean = false,
    )

    override fun canRead(content: String): Boolean =
        content.lineSequence().any { line ->
            bracketTagRe.findAll(line).any { parseLrcTimestampMs(it.groupValues[1]) != null }
        }

    /** 解析 `mm:ss` / `mm:ss.xx` / `mm:ss.xxx` / `mm:ss:xx` / `mm:ss:xxx` 返回毫秒，非法返回 null。 */
    internal fun parseLrcTimestampMs(rawBody: String): Long? {
        val body = rawBody.trim()
        val firstColon = body.indexOf(':')
        if (firstColon <= 0) return null
        val minStr = body.substring(0, firstColon)
        val minutes = minStr.toLongOrNull() ?: return null
        if (minutes !in 0L..599L || minStr.any { !it.isDigit() }) return null

        val rest = body.substring(firstColon + 1)
        val sepIdx = rest.indexOfFirst { it == '.' || it == ':' }
        val secStr = if (sepIdx < 0) rest else rest.substring(0, sepIdx)
        val seconds = secStr.toLongOrNull() ?: return null
        if (seconds !in 0..59 || secStr.isEmpty() || secStr.any { !it.isDigit() }) return null

        var fracMs = 0L
        if (sepIdx >= 0) {
            val fracStr = rest.substring(sepIdx + 1)
            if (fracStr.isEmpty() || fracStr.length > 3 || fracStr.any { !it.isDigit() }) return null
            val frac = fracStr.toLongOrNull() ?: return null
            fracMs = when (fracStr.length) {
                1 -> frac * 100L
                2 -> frac * 10L
                else -> frac
            }
        }
        return (minutes * 60L + seconds) * 1000L + fracMs
    }

    override fun read(text: String, options: ReadOptions): AssScript {
        val info = mutableListOf<AssInfo>()
        val timedLines = mutableListOf<ParsedLrcLine>()
        val untimedLines = mutableListOf<String>()
        var offsetMs = 0L

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!line.startsWith("[")) {
                untimedLines += line
                continue
            }

            val timestamps = mutableListOf<Long>()
            var pos = 0
            while (pos < line.length && line[pos] == '[') {
                val close = line.indexOf(']', pos + 1)
                if (close < 0) break
                val tagBody = line.substring(pos + 1, close).trim()
                val ts = parseLrcTimestampMs(tagBody)
                if (ts != null) {
                    timestamps += ts
                } else {
                    val meta = metadataTagRe.matchEntire(tagBody)
                    if (meta != null) {
                        val key = meta.groupValues[1].trim()
                        val value = meta.groupValues[2].trim()
                        if (key.equals("offset", ignoreCase = true)) {
                            value.removePrefix("+").trim().toLongOrNull()?.let { offsetMs = it }
                        } else {
                            info += AssInfo(key, value)
                        }
                    }
                }
                pos = close + 1
            }

            if (timestamps.isEmpty()) continue
            val remainder = line.substring(pos).trim()

            // 解析增强型 LRC 行内 <mm:ss.xx> 音节标记
            val syllables = mutableListOf<Pair<Long, String>>()
            var hasSyllables = false
            var endMarkerMs = -1L
            var scan = 0
            var lastSylMs = timestamps.first()
            val pending = StringBuilder()

            while (scan < remainder.length) {
                val lt = remainder.indexOf('<', scan)
                val gt = if (lt >= 0) remainder.indexOf('>', lt + 1) else -1
                if (lt < 0 || gt < 0) {
                    pending.append(remainder.substring(scan))
                    break
                }
                val wordTs = parseLrcTimestampMs(remainder.substring(lt + 1, gt))
                if (wordTs == null) {
                    pending.append(remainder.substring(scan, gt + 1))
                    scan = gt + 1
                    continue
                }
                pending.append(remainder.substring(scan, lt))
                if (pending.isNotEmpty()) {
                    syllables += lastSylMs to pending.toString()
                    pending.setLength(0)
                }
                lastSylMs = wordTs
                hasSyllables = true
                scan = gt + 1
            }

            val tail = pending.toString().trimEnd('\r')
            if (hasSyllables && tail.isNotBlank()) {
                syllables += lastSylMs to tail
            } else if (hasSyllables && tail.isBlank()) {
                endMarkerMs = lastSylMs
            }

            for (ts in timestamps) {
                val delta = ts - timestamps.first()
                if (hasSyllables) {
                    if (syllables.any { it.second.isNotBlank() }) {
                        timedLines += ParsedLrcLine(
                            startMs = ts,
                            endMarkerMs = if (endMarkerMs < 0) -1L else endMarkerMs + delta,
                            syllables = syllables.map { (t, s) -> (t + delta) to s }.toMutableList(),
                            hasSyllables = true,
                        )
                    }
                } else {
                    // 保留显式带时间戳的非空行；若同一文件无其它逐字标签，像 [00:01.00]x 这类正常文本保留，纯空白间隔跳过
                    if (remainder.isNotBlank()) {
                        timedLines += ParsedLrcLine(
                            startMs = ts,
                            plainText = remainder.replace("\r", ""),
                        )
                    }
                }
            }
        }

        if (timedLines.isEmpty() && untimedLines.isNotEmpty()) {
            val events = untimedLines.mapIndexed { idx, lyric ->
                AssEvent(
                    id = idx.toLong(),
                    row = idx,
                    start = SubTime.ZERO,
                    end = SubTime.ZERO,
                    text = lyric,
                )
            }
            return AssScript(info = info.toPersistentList(), events = events.toPersistentList())
        }

        fun adjust(ms: Long): Long = (ms - offsetMs).coerceIn(0L, 35_999_990L)
        for (item in timedLines) {
            item.startMs = adjust(item.startMs)
            if (item.endMarkerMs >= 0) item.endMarkerMs = adjust(item.endMarkerMs)
            for (i in item.syllables.indices) {
                item.syllables[i] = adjust(item.syllables[i].first) to item.syllables[i].second
            }
        }

        timedLines.sortBy { it.startMs }

        fun karaokeCs(ms: Long): Long = (ms + 5L) / 10L

        val events = timedLines.mapIndexed { idx, cur ->
            var endMs = timedLines.getOrNull(idx + 1)?.startMs ?: (cur.startMs + 5_000L)
            if (cur.endMarkerMs >= 0L) endMs = cur.endMarkerMs
            val nextStart = timedLines.getOrNull(idx + 1)?.startMs
            if (nextStart != null && endMs > nextStart && nextStart > cur.startMs) {
                endMs = nextStart
            }
            if (endMs <= cur.startMs) endMs = cur.startMs + 500L

            val assText = if (cur.hasSyllables && cur.syllables.isNotEmpty()) {
                buildString {
                    var elapsedCs = 0L
                    for (s in cur.syllables.indices) {
                        val (sylStart, sylWord) = cur.syllables[s]
                        val segBegin = sylStart.coerceIn(cur.startMs, endMs)
                        val beginCs = karaokeCs(segBegin - cur.startMs)
                        if (beginCs > elapsedCs) {
                            append("{\\k").append(beginCs - elapsedCs).append("}")
                            elapsedCs = beginCs
                        }
                        val rawSegEnd = if (s + 1 < cur.syllables.size) cur.syllables[s + 1].first else endMs
                        val endCs = karaokeCs(rawSegEnd.coerceIn(segBegin, endMs) - cur.startMs)
                        val durCs = (endCs - elapsedCs).coerceAtLeast(0L)
                        append("{\\kf").append(durCs).append("}").append(sylWord)
                        elapsedCs += durCs
                    }
                }
            } else {
                cur.plainText
            }

            AssEvent(
                id = idx.toLong(),
                row = idx,
                start = SubTime.ofMillis(cur.startMs),
                end = SubTime.ofMillis(endMs),
                text = assText,
            )
        }

        return AssScript(info = info.toPersistentList(), events = events.toPersistentList())
    }

    override fun write(script: AssScript, options: WriteOptions): String {
        val fmt = when (options.timePrecision) {
            TimePrecision.THREE_MS -> LrcTimeFormat.XXX
            TimePrecision.TWO_MS -> LrcTimeFormat.XX
            TimePrecision.AUTO -> LrcTimeFormat.XX
        }
        val lrcMetaKeys = setOf("ti", "ar", "al", "by", "re", "ve", "au", "length")
        return buildString {
            script.info.forEach { info ->
                if (info.key.lowercase() in lrcMetaKeys || script.info.none { it.key == "ScriptType" }) {
                    appendLine("[${info.key}:${info.value}]")
                }
            }
            val dialogueEvents = script.events.filter { !it.comment }
            val allUntimed = dialogueEvents.isNotEmpty() &&
                dialogueEvents.all { it.start.micros == 0L && it.end.micros == 0L }

            for (event in dialogueEvents) {
                if (allUntimed) {
                    appendLine(event.strippedText)
                    continue
                }
                val syllables = KaraokeSyllables.parse(event.text)
                if (syllables.isNotEmpty()) {
                    append(event.start.toLrcString(fmt))
                    var cursorMs = event.start.millis
                    for (syl in syllables) {
                        if (syl.text.isNotEmpty()) {
                            val wordTag = SubTime.ofMillis(cursorMs).toLrcString(fmt)
                                .removePrefix("[").removeSuffix("]")
                            append("<").append(wordTag).append(">").append(syl.text)
                        }
                        cursorMs += syl.centiseconds * 10L
                    }
                    val endTag = SubTime.ofMillis(cursorMs).toLrcString(fmt)
                        .removePrefix("[").removeSuffix("]")
                    append("<").append(endTag).append(">")
                    appendLine()
                } else {
                    appendLine("${event.start.toLrcString(fmt)}${event.strippedText}")
                }
            }
        }
    }
}
