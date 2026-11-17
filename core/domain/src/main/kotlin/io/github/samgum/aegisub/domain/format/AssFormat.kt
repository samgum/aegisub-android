package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssAttachment
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssInfo
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.AttachmentGroup
import kotlinx.collections.immutable.toPersistentList

/**
 * ASS (V4+) / SSA (V4) 编解码（支持 `[Script Info]`、`[V4+ Styles]`、`[Events]` 以及 `[Fonts]` / `[Graphics]` 内嵌附件无损往返）。
 *
 * @author 伤感咩吖
 */
class AssFormatBase(private val ssaMode: Boolean = false) : SubtitleFormat {
    override val name: String = if (ssaMode) "ssa" else "ass"
    override val extensions: List<String> = if (ssaMode) listOf(".ssa") else listOf(".ass")

    override fun canRead(content: String): Boolean =
        content.contains("[Script Info]", ignoreCase = true)

    private enum class Section { INFO, STYLES, EVENTS, FONTS, GRAPHICS, OTHER }

    override fun read(text: String, options: ReadOptions): AssScript {
        val info = mutableListOf<AssInfo>()
        val styles = mutableListOf<AssStyle>()
        val events = mutableListOf<AssEvent>()
        val attachments = mutableListOf<AssAttachment>()

        var section = Section.INFO
        var eventIdx = 0L
        var pendingAttName: String? = null
        var pendingAttGroup: AttachmentGroup? = null
        val pendingAttData = StringBuilder()

        fun flushAttachment() {
            val name = pendingAttName
            val grp = pendingAttGroup
            if (name != null && grp != null && pendingAttData.isNotEmpty()) {
                attachments += AssAttachment(name, grp, pendingAttData.toString().trimEnd('\r', '\n'))
            }
            pendingAttName = null
            pendingAttGroup = null
            pendingAttData.setLength(0)
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                flushAttachment()
                section = when (line.lowercase()) {
                    "[script info]" -> Section.INFO
                    "[v4+ styles]", "[v4 styles]", "[v4 styles+]" -> Section.STYLES
                    "[events]" -> Section.EVENTS
                    "[fonts]" -> Section.FONTS
                    "[graphics]" -> Section.GRAPHICS
                    else -> Section.OTHER
                }
                continue
            }

            when (section) {
                Section.FONTS, Section.GRAPHICS -> {
                    val grp = if (section == Section.FONTS) AttachmentGroup.FONT else AttachmentGroup.GRAPHIC
                    if (line.startsWith("fontname:", ignoreCase = true) || line.startsWith("filename:", ignoreCase = true)) {
                        flushAttachment()
                        pendingAttName = line.substringAfter(':').trim()
                        pendingAttGroup = grp
                    } else if (pendingAttName != null) {
                        if (pendingAttData.isNotEmpty()) pendingAttData.append('\n')
                        pendingAttData.append(line)
                    }
                }
                else -> {
                    when {
                        line.startsWith("Format:", ignoreCase = true) -> Unit
                        line.startsWith("Style:", ignoreCase = true) -> styles += AssStyle.parse(line)
                        line.startsWith("Dialogue:", ignoreCase = true) ||
                            line.startsWith("Comment:", ignoreCase = true) ->
                            events += AssEvent.parse(line).copy(id = eventIdx++, row = events.size)
                        section == Section.INFO -> AssInfo.parse(line)?.let { info += it }
                    }
                }
            }
        }
        flushAttachment()

        return AssScript(
            info = info.toPersistentList(),
            styles = styles.toPersistentList(),
            events = events.toPersistentList(),
            attachments = attachments.toPersistentList(),
        )
    }

    override fun write(script: AssScript, options: WriteOptions): String = buildString {
        val scriptType = if (ssaMode) "v4.00" else "v4.00+"
        val stylesHeader = if (ssaMode) "[V4 Styles]" else "[V4+ Styles]"
        val msPrecision = options.timePrecision == TimePrecision.THREE_MS

        appendLine("[Script Info]")
        val infoLines = script.info.toMutableList()
        if (infoLines.none { it.key.equals("ScriptType", true) }) {
            infoLines.add(0, AssInfo("ScriptType", scriptType))
        }
        infoLines.forEach { appendLine(it.toLine()) }
        appendLine()

        appendLine(stylesHeader)
        appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        (if (script.styles.isEmpty()) listOf(AssStyle()) else script.styles).forEach { appendLine(it.toStyleLine()) }
        appendLine()

        appendLine("[Events]")
        appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        script.events.forEach { appendLine(it.toLine(msPrecision)) }

        val fonts = script.attachments.filter { it.group == AttachmentGroup.FONT }
        if (fonts.isNotEmpty()) {
            appendLine()
            appendLine("[Fonts]")
            fonts.forEach { appendLine(it.toAssLines()) }
        }

        val graphics = script.attachments.filter { it.group == AttachmentGroup.GRAPHIC }
        if (graphics.isNotEmpty()) {
            appendLine()
            appendLine("[Graphics]")
            graphics.forEach { appendLine(it.toAssLines()) }
        }
    }
}

object AssFormat : SubtitleFormat by AssFormatBase(ssaMode = false)
object SsaFormat : SubtitleFormat by AssFormatBase(ssaMode = true)
