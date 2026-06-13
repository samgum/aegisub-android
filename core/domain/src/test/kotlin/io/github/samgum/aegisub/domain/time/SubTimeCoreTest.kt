package io.github.samgum.aegisub.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubTimeCoreTest {
    @Test fun stores_micros_and_clamps_to_range() {
        assertEquals(0, SubTime.ZERO.micros)
        assertEquals(1_000_000, SubTime.ofMicros(1_000_000).micros)
        // 负值钳为 0
        assertEquals(0, SubTime.ofMicros(-5).micros)
        // 超过 10 小时钳为上限
        val tenHours = 10L * 60 * 60 * 1_000_000
        assertEquals(tenHours, SubTime.ofMicros(tenHours + 999).micros)
    }
    @Test fun factory_conversions() {
        assertEquals(5_000_000, SubTime.ofMillis(5_000).micros)
        assertEquals(50_000, SubTime.ofCentiseconds(5).micros) // 5cs = 50ms
    }
    @Test fun arithmetic_clamps() {
        val a = SubTime.ofMillis(3_000)
        val b = SubTime.ofMillis(1_500)
        assertEquals(4_500_000, (a + b).micros)
        assertEquals(1_500_000, (a - b).micros)
        // 不允许为负
        assertEquals(0, (b - a).micros)
    }
    @Test fun comparison() {
        assertTrue(SubTime.ofMillis(1) < SubTime.ofMillis(2))
        assertTrue(SubTime.ofMillis(2) > SubTime.ofMillis(1))
        assertEquals(0, SubTime.ofMillis(5).compareTo(SubTime.ofMillis(5)))
    }
}
