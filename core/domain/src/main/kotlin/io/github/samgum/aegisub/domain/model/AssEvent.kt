package io.github.samgum.aegisub.domain.model

import io.github.samgum.aegisub.domain.time.SubTime

/**
 * ASS 事件行（Dialogue/Comment）。
 *
 * @author 伤感咩吖
 */
data class AssEvent(
    val id: Long = 0L,
    val row: Int = -1,
    val comment: Boolean = false,
    val layer: Int = 0,
    val start: SubTime = SubTime.ZERO,
    val end: SubTime = SubTime.ofMillis(5_000),
    val style: String = "Default",
    val actor: String = "",
    val margins: Margins = Margins.ZERO,
    val effect: String = "",
    val text: String = "",
) {
    /** 去除所有覆盖标签后的纯文本（绘图块视作空）。 */
    val strippedText: String
        get() = DialogueBlock.parse(text).joinToString("") {
            when (it) { is DialogueBlock.Plain -> it.text; else -> "" }
        }

    fun toLine(): String {
        val kind = if (comment) "Comment" else "Dialogue"
        val parts = listOf(
            layer.toString(), start.toAssString(false), end.toAssString(false), style, actor,
            margins.left.toString(), margins.right.toString(), margins.vertical.toString(), effect, text,
        )
        return "$kind: " + parts.joinToString(",")
    }

    companion object {
        fun parse(line: String): AssEvent {
            val trimmed = line.trim()
            val comment = trimmed.startsWith("Comment:", ignoreCase = true)
            val kind = if (comment) "Comment" else "Dialogue"
            require(trimmed.startsWith("$kind:", ignoreCase = true)) { "Not a Dialogue/Comment line: $line" }
            val body = trimmed.substringAfter(":").trim()
            // 前 9 字段按逗号切，第 10 个（文本）保留全部（含逗号）
            val f = body.split(",", limit = 10).map { it.trim() }
            require(f.size == 10) { "Invalid Dialogue line: expected 10 fields, got ${f.size}" }
            return AssEvent(
                comment = comment,
                layer = f[0].toIntOrNull() ?: 0,
                start = SubTime.parseAss(f[1]),
                end = SubTime.parseAss(f[2]),
                style = f[3],
                actor = f[4],
                margins = Margins(f[5].toIntOrNull() ?: 0, f[6].toIntOrNull() ?: 0, f[7].toIntOrNull() ?: 0),
                effect = f[8],
                text = f[9],
            )
        }
    }
}
