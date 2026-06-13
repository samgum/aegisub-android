package io.github.samgum.aegisub.domain.edit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * KaraokeGenerator 测试：{\k}/{\kf} 音节生成、时间均匀分配、余数前置。
 *
 * @author 伤感咩吖
 */
class KaraokeGeneratorTest {

    // 1000ms = 100 厘秒

    @Test fun generate_by_word_splits_on_spaces() {
        val r = KaraokeGenerator.generate("Hello World", 1_000_000L, KaraokeMode.BY_WORD)
        assertEquals("{\\k50}Hello {\\k50}World", r)
    }

    @Test fun generate_by_char_each_character() {
        val r = KaraokeGenerator.generate("你好", 1_000_000L, KaraokeMode.BY_CHAR)
        assertEquals("{\\k50}你{\\k50}好", r)
    }

    @Test fun generate_kf_uses_kf_tag() {
        // 单音节独占全部 100cs
        val r = KaraokeGenerator.generate("Hi", 1_000_000L, KaraokeMode.BY_WORD, useKf = true)
        assertEquals("{\\kf100}Hi", r)
    }

    @Test fun remainder_distributed_to_first_syllables() {
        // 3 音节，1000ms = 100cs → 33+33+34（余数 1 给首个）
        val r = KaraokeGenerator.generate("a b c", 1_000_000L, KaraokeMode.BY_WORD)
        assertEquals("{\\k34}a {\\k33}b {\\k33}c", r)
    }

    @Test fun single_syllable_gets_all_time() {
        val r = KaraokeGenerator.generate("Solo", 1_000_000L, KaraokeMode.BY_WORD)
        assertEquals("{\\k100}Solo", r)
    }

    @Test fun empty_text_returns_empty() {
        assertEquals("", KaraokeGenerator.generate("", 1_000_000L, KaraokeMode.BY_WORD))
        assertEquals("", KaraokeGenerator.generate("   ", 1_000_000L, KaraokeMode.BY_WORD))
    }

    @Test fun strips_override_tags_before_splitting() {
        // 标签先剥离，再按词切分
        val r = KaraokeGenerator.generate("{\\b1}Hello{\\b0} World", 1_000_000L, KaraokeMode.BY_WORD)
        assertEquals("{\\k50}Hello {\\k50}World", r)
    }

    @Test fun zero_duration_floor_to_one_cs_per_syllable() {
        // 时长 0：每个音节至少 1cs（钳制）
        val r = KaraokeGenerator.generate("a b", 0L, KaraokeMode.BY_WORD)
        assertEquals("{\\k1}a {\\k1}b", r)
    }

    @Test fun sum_of_k_tags_equals_total_centiseconds() {
        // 任意文本，所有 {\k<cs>} 之和应等于总厘秒
        val duration = 2_350_000L // 235cs
        val r = KaraokeGenerator.generate("one two three four", duration, KaraokeMode.BY_WORD)
        val sum = Regex("\\{\\\\k\\d+}").findAll(r)
            .map { it.value.removePrefix("{\\k").removeSuffix("}").toInt() }
            .sum()
        assertEquals(235, sum)
    }

    @Test fun by_char_skips_whitespace() {
        val r = KaraokeGenerator.generate("a b", 1_000_000L, KaraokeMode.BY_CHAR)
        // 空格被跳过，两个字符各 50cs
        assertEquals("{\\k50}a{\\k50}b", r)
    }

    @Test fun generates_from_event_uses_duration() {
        val e = io.github.samgum.aegisub.domain.model.AssEvent(
            start = io.github.samgum.aegisub.domain.time.SubTime.ofMillis(1_000),
            end = io.github.samgum.aegisub.domain.time.SubTime.ofMillis(3_000),
            text = "Hi Yo",
        )
        val r = KaraokeGenerator.generateFromEvent(e, KaraokeMode.BY_WORD)
        // 2000ms = 200cs → 100 + 100
        assertEquals("{\\k100}Hi {\\k100}Yo", r)
    }

    @Test fun output_is_non_empty_when_input_non_empty() {
        assertTrue(KaraokeGenerator.generate("test", 500_000L, KaraokeMode.BY_WORD).isNotEmpty())
    }
}
