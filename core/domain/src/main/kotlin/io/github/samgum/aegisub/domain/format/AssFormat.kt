package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssInfo
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import kotlinx.collections.immutable.toPersistentList

/**
 * ASS (V4+) 编解码。SSA 通过 [ssaMode] 复用（仅段头与 ScriptType 不同）。
 * 参考自 Aegisub subtitle_format_ass（BSD）。
 *
 * @author 伤感咩吖
 */
class AssFormatBase(private val ssaMode: Boolean = false) : SubtitleFormat {
    override val name: String = if (ssaMode) "ssa" else "ass"
    override val extensions: List<String> = if (ssaMode) listOf(".ssa") else listOf(".ass")

    override fun canRead(content: String): Boolean =
        content.contains("[Script Info]", ignoreCase = true) &&
            (content.contains("[V4+ Styles]", ignoreCase = true) ||
                content.contains("[V4 Styles]", ignoreCase = true))

    override fun read(text: String, options: ReadOptions): AssScript {
        val info = mutableListOf<AssInfo>()
        val styles = mutableListOf<AssStyle>()
        val events = mutableListOf<AssEvent>()
        var eventIdx = 0L
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("[") && line.endsWith("]") -> Unit // 段头，无需记录
                // 任意段的 Format: 描述行都跳过（Styles 段与 Events 段都有）
                line.startsWith("Format:", ignoreCase = true) -> Unit
                line.startsWith("Style:", ignoreCase = true) -> styles += AssStyle.parse(line)
                line.startsWith("Dialogue:", ignoreCase = true) ||
                    line.startsWith("Comment:", ignoreCase = true) ->
                    events += AssEvent.parse(line).copy(id = eventIdx++, row = events.size)
                else -> AssInfo.parse(line)?.let { info += it }
            }
        }
        return AssScript(
            info = info.toPersistentList(),
            styles = styles.toPersistentList(),
            events = events.toPersistentList(),
        )
    }

    override fun write(script: AssScript, options: WriteOptions): String = buildString {
        val scriptType = if (ssaMode) "v4.00" else "v4.00+"
        val stylesHeader = if (ssaMode) "[V4 Styles]" else "[V4+ Styles]"
        appendLine("[Script Info]")
        val infoLines = script.info.toMutableList()
        if (infoLines.none { it.key.equals("ScriptType", true) })
            infoLines.add(0, AssInfo("ScriptType", scriptType))
        infoLines.forEach { appendLine(it.toLine()) }
        appendLine()
        appendLine(stylesHeader)
        appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        // toStyleLine() 已含 "Style: " 前缀，这里直接用
        (if (script.styles.isEmpty()) listOf(AssStyle()) else script.styles).forEach { appendLine(it.toStyleLine()) }
        appendLine()
        appendLine("[Events]")
        appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        script.events.forEach { appendLine(it.toLine()) }
    }
}

object AssFormat : SubtitleFormat by AssFormatBase(ssaMode = false)
object SsaFormat : SubtitleFormat by AssFormatBase(ssaMode = true)
