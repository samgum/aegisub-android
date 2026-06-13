package io.github.samgum.aegisub.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FormatOptionsTest {
    @Test fun write_options_defaults() {
        val o = WriteOptions()
        assertEquals(TimePrecision.AUTO, o.timePrecision)
        assertEquals(false, o.stripTags)
    }
    @Test fun read_options_defaults() {
        assertEquals(true, ReadOptions().detectEncoding)
    }
    @Test fun time_precision_has_three_variants() {
        assertEquals(3, TimePrecision.entries.size)
        assertNotEquals(TimePrecision.TWO_MS, TimePrecision.THREE_MS)
    }
}
