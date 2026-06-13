package io.github.samgum.aegisub.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DialogueBlockTest {
    @Test fun splits_plain_and_overrides() {
        val blocks = DialogueBlock.parse("Yes, I {\\i1}am{\\i0} here.")
        assertEquals(5, blocks.size)
        assertEquals("Yes, I ", (blocks[0] as DialogueBlock.Plain).text)
        assertIs<DialogueBlock.Override>(blocks[1])
        assertEquals("am", (blocks[2] as DialogueBlock.Plain).text)
        assertIs<DialogueBlock.Override>(blocks[3])
        assertEquals(" here.", (blocks[4] as DialogueBlock.Plain).text)
    }
    @Test fun override_parses_tags() {
        val blocks = DialogueBlock.parse("{\\i1\\b1}text")
        val ov = blocks[0] as DialogueBlock.Override
        assertEquals(2, ov.tags.size)
        assertEquals("i", ov.tags[0].name)
        assertEquals("1", ov.tags[0].rawValue)
        assertEquals("b", ov.tags[1].name)
    }
    @Test fun comment_block() {
        val blocks = DialogueBlock.parse("{*this is a comment}x")
        assertIs<DialogueBlock.Comment>(blocks[0])
    }
    @Test fun round_trips_text() {
        val original = "Yes, I {\\i1}am{\\i0} here."
        assertEquals(original, DialogueBlock.toText(DialogueBlock.parse(original)))
    }
}
