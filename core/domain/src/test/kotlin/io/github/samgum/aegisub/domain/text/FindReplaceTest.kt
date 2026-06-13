package io.github.samgum.aegisub.domain.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * FindReplace 测试：普通/大小写/正则/计数/容错。
 *
 * @author 伤感咩吖
 */
class FindReplaceTest {

    @Test fun plain_replace_all_occurrences() {
        assertEquals("XbcXbc", FindReplace.replaceAll("abcabc", "a", "X", useRegex = false))
    }

    @Test fun plain_replace_multichar() {
        assertEquals("XXworld", FindReplace.replaceAll("hello world", "hello ", "XX", useRegex = false))
    }

    @Test fun ignore_case_plain() {
        assertEquals("XBXBX", FindReplace.replaceAll("aBaBa", "a", "X", useRegex = false, ignoreCase = true))
    }

    @Test fun case_sensitive_only_matches_exact_case() {
        // query "A"（大写）在 "aBaBa"（小写 a）→ 不匹配
        assertEquals("aBaBa", FindReplace.replaceAll("aBaBa", "A", "X", useRegex = false, ignoreCase = false))
    }

    @Test fun empty_query_returns_unchanged() {
        assertEquals("abc", FindReplace.replaceAll("abc", "", "X", useRegex = false))
    }

    @Test fun regex_replace_simple() {
        assertEquals("X, X, X", FindReplace.replaceAll("a, b, c", "[a-z]", "X", useRegex = true))
    }

    @Test fun regex_with_capture_group() {
        assertEquals("Mr. Smith", FindReplace.replaceAll("Smith", "(.+)", "Mr. $1", useRegex = true))
    }

    @Test fun invalid_regex_falls_back_to_original() {
        assertEquals("abc", FindReplace.replaceAll("abc", "[", "X", useRegex = true))
    }

    @Test fun count_plain() {
        assertEquals(3, FindReplace.count("abcabca", "a", useRegex = false))
    }

    @Test fun count_regex() {
        assertEquals(3, FindReplace.count("a1b2c3", "\\d", useRegex = true))
    }

    @Test fun count_empty_query_zero() {
        assertEquals(0, FindReplace.count("abc", "", useRegex = false))
    }
}
