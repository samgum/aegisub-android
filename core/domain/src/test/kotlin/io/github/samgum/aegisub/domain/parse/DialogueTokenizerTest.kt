package io.github.samgum.aegisub.domain.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DialogueTokenizerTest {
    @Test fun tokenizes_plain_text() {
        val toks = DialogueTokenizer.tokenize("hello")
        assertEquals(1, toks.size)
        assertEquals(DialogueTokenType.TEXT, toks[0].type)
        assertEquals(5, toks[0].length)
    }
    @Test fun tokenizes_override_block() {
        val toks = DialogueTokenizer.tokenize("{\\i1}x{\\i0}")
        val types = toks.map { it.type }
        assertTrue(DialogueTokenType.OVR_BEGIN in types)
        assertTrue(DialogueTokenType.OVR_END in types)
        assertTrue(DialogueTokenType.TAG_NAME in types)
        assertTrue(DialogueTokenType.ARG in types)
    }
    @Test fun tokenizes_paren_args() {
        val toks = DialogueTokenizer.tokenize("{\\pos(100,200)}")
        val types = toks.map { it.type }
        assertTrue(DialogueTokenType.OPEN_PAREN in types)
        assertTrue(DialogueTokenType.CLOSE_PAREN in types)
        assertTrue(DialogueTokenType.ARG_SEP in types)
    }
    @Test fun marks_drawings_after_p_tag() {
        val s = "{\\p1}m 0 0 l 100 0{\\p0}text"
        val marked = DialogueTokenizer.markDrawings(s, DialogueTokenizer.tokenize(s))
        assertTrue(marked.any { it.type == DialogueTokenType.DRAWING_FULL })
    }
    @Test fun total_length_equals_string_length() {
        val s = "Yes, I {\\i1}am{\\i0} here."
        val toks = DialogueTokenizer.tokenize(s)
        assertEquals(s.length, toks.sumOf { it.length })
    }
}
