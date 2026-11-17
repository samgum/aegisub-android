package io.github.samgum.aegisub.domain.model

import io.github.samgum.aegisub.domain.time.SubTime
import kotlin.math.roundToInt

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
            when (it) {
                is DialogueBlock.Plain -> it.text
                else -> ""
            }
        }

    /** 仿桌面 Aegisub「简化标签」显示模式：把每个 `{...}` 覆盖块替换为 `☼`。 */
    val simplifiedText: String
        get() = DialogueBlock.parse(text).joinToString("") {
            when (it) {
                is DialogueBlock.Plain -> it.text
                is DialogueBlock.Override, is DialogueBlock.Comment -> "☼"
                is DialogueBlock.Drawing -> "[绘图]"
            }
        }

    /** 持续时间（毫秒，最小为 0）。 */
    val durationMs: Long
        get() = (end.millis - start.millis).coerceAtLeast(0L)

    /** 可见有效字符数（仿桌面 Aegisub CPS 统计：忽略标签、换行 `\N`/`\n`、硬空格 `\h`、空白与标点）。 */
    val visibleCharCount: Int
        get() {
            val clean = strippedText
                .replace("\\N", "")
                .replace("\\n", "")
                .replace("\\h", "")
            var count = 0
            for (ch in clean) {
                if (!ch.isWhitespace() && Character.getType(ch) !in PUNCTUATION_TYPES) {
                    count++
                }
            }
            return count
        }

    /** 每秒字符数（CPS，Characters Per Second），对齐桌面 Aegisub 网格 CPS 列。 */
    val cps: Int
        get() {
            val dur = durationMs
            if (dur <= 0L) return 0
            return ((visibleCharCount * 1000.0) / dur).roundToInt()
        }

    /**
     * 序列化为 Dialogue/Comment 行。
     * @param msPrecision true=毫秒（H:MM:SS.mmm，3 位），false=厘秒（H:MM:SS.cc，2 位，ASS 标准）。
     */
    fun toLine(msPrecision: Boolean = false): String {
        val kind = if (comment) "Comment" else "Dialogue"
        val parts = listOf(
            layer.toString(), start.toAssString(msPrecision), end.toAssString(msPrecision), style, actor,
            margins.left.toString(), margins.right.toString(), margins.vertical.toString(), effect, text,
        )
        return "$kind: " + parts.joinToString(",")
    }

    companion object {
        private val PUNCTUATION_TYPES = setOf(
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
        )

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
