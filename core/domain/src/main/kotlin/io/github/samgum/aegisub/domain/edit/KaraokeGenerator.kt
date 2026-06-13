package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.DialogueBlock

/** 音节切分模式：按词（空格）/ 按字符（每个字）。 */
enum class KaraokeMode { BY_WORD, BY_CHAR }

/**
 * Karaoke 音节生成（复刻桌面 Aegisub Karaoke / Split by syllable）。
 *
 * 把一行纯文本切成音节，均匀分配该行时长，输出 `{\k<cs>}`（逐字填充）或
 * `{\kf<cs>}`（平滑填充）标签序列。`<cs>` 为厘秒（1/100s），所有音节 cs 之和
 * 等于总时长厘秒；余数（整除不尽）从首个音节起依次 +1，保证总和精确。
 *
 * 时长 0 时每个音节钳为 1cs（避免 0cs 静音音节）。
 *
 * 通过 `session.editEvent` 接入，单撤销点。
 *
 * @author 伤感咩吖
 */
object KaraokeGenerator {

    /**
     * @param text 原文本（含可能覆盖标签，先剥离为纯文本再切分）
     * @param durationMicros 行时长（微秒）
     * @param mode 切分模式
     * @param useKf true={\kf} 平滑填充；false={\k} 逐字填充
     */
    fun generate(
        text: String,
        durationMicros: Long,
        mode: KaraokeMode,
        useKf: Boolean = false,
    ): String {
        val plain = stripPlain(text)
        val units = split(plain, mode)
        if (units.isEmpty()) return ""
        val tag = if (useKf) "kf" else "k"
        val totalCs = (durationMicros / 10_000L).coerceAtLeast(units.size.toLong())
        val per = totalCs / units.size
        val remainder = totalCs - per * units.size
        val sb = StringBuilder()
        for ((i, u) in units.withIndex()) {
            val cs = per + (if (i < remainder) 1L else 0L)
            sb.append("{\\").append(tag).append(cs).append("}").append(u)
            if (mode == KaraokeMode.BY_WORD && i < units.lastIndex) sb.append(' ')
        }
        return sb.toString()
    }

    /** 便捷：从事件取时长并生成。 */
    fun generateFromEvent(event: AssEvent, mode: KaraokeMode, useKf: Boolean = false): String =
        generate(event.text, event.end.micros - event.start.micros, mode, useKf)

    /** 剥离覆盖标签 / 注释块，保留纯可视文本。 */
    private fun stripPlain(text: String): String =
        DialogueBlock.parse(text).joinToString("") {
            when (it) { is DialogueBlock.Plain -> it.text; else -> "" }
        }

    /** 按模式切分；BY_WORD 按空白分词，BY_CHAR 每个非空白字符为一个单元。 */
    private fun split(plain: String, mode: KaraokeMode): List<String> = when (mode) {
        KaraokeMode.BY_WORD ->
            plain.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        KaraokeMode.BY_CHAR ->
            plain.asSequence().filter { !it.isWhitespace() }.map { it.toString() }.toList()
    }
}
