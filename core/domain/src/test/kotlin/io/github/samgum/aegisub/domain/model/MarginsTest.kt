package io.github.samgum.aegisub.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarginsTest {
    @Test fun default_is_zero() {
        assertEquals(0, Margins.ZERO.left)
        assertEquals(0, Margins.ZERO.right)
        assertEquals(0, Margins.ZERO.vertical)
    }
    @Test fun equality_is_structural() {
        assertEquals(Margins(1, 2, 3), Margins(1, 2, 3))
        assertTrue(Margins(1, 2, 3) != Margins(0, 2, 3))
    }
}
