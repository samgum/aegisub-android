package io.github.samgum.aegisub.domain.edit

/**
 * Karaoke 音节（复刻桌面 Aegisub Karaoke 模式的逐音节计时）。
 *
 * 一个音节 = 一个 `{\k<cs>}` / `{\kf<cs>}` 标签 + 其后到下一个标签前的可视文本。
 * [centiseconds] 为该音节持续时长（厘秒）。解析/重建可往返；[adjust] 在相邻音节间
 * 挪动时长（拖拽边界），总和守恒、每音节至少 1cs。
 *
 * @author 伤感咩吖
 */
object KaraokeSyllables {

    /** 匹配 {\k<cs>} / {\kf<cs>} / {\ko<cs>} 等卡拉OK标签（捕获 tag 与 cs）。 */
    private val K_TAG_RE = Regex("""\{\\(ko?|kf?)(\d+)\}""")

    /** 单个音节。 */
    data class Syllable(val tag: String, val centiseconds: Long, val text: String)

    /** 解析文本为音节列表；无 karaoke 标签返回空列表。 */
    fun parse(text: String): List<Syllable> {
        val matches = K_TAG_RE.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexed { idx, m ->
            val tag = m.groupValues[1]
            val cs = m.groupValues[2].toLong()
            val textStart = m.range.last + 1
            val textEnd = if (idx + 1 < matches.size) matches[idx + 1].range.first else text.length
            Syllable(tag, cs, text.substring(textStart, textEnd))
        }
    }

    /** 重建 karaoke 文本。 */
    fun build(syllables: List<Syllable>): String =
        syllables.joinToString("") { "{\\${it.tag}${it.centiseconds}}${it.text}" }

    /** 全部音节时长总和（厘秒）。 */
    fun total(syllables: List<Syllable>): Long = syllables.sumOf { it.centiseconds }

    /**
     * 在 [boundary]（音节 i 与 i+1 之间，0 基）处挪动 [deltaCs] 厘秒：
     * 正数把时长从 i+1 移给 i，负数反之。每音节钳到 ≥1cs，总和守恒。
     * [boundary] 越界则原样返回。
     */
    fun adjust(syllables: List<Syllable>, boundary: Int, deltaCs: Long): List<Syllable> {
        if (boundary !in 0 until syllables.size - 1) return syllables
        val a = syllables[boundary]
        val b = syllables[boundary + 1]
        val maxAdd = b.centiseconds - 1
        val maxRemove = a.centiseconds - 1
        val clamped = deltaCs.coerceIn(-maxRemove, maxAdd)
        val result = syllables.toMutableList()
        result[boundary] = a.copy(centiseconds = a.centiseconds + clamped)
        result[boundary + 1] = b.copy(centiseconds = b.centiseconds - clamped)
        return result
    }
}
