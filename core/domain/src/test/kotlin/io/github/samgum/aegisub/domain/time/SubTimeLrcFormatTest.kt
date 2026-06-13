package io.github.samgum.aegisub.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SubTime LRC 四种时间格式测试：精度（厘秒/毫秒）× 分隔符（./:）。
 *
 * @author 伤感咩吖
 */
class SubTimeLrcFormatTest {

    // 1m23.45s = 83450ms；1m23.456s = 83456ms
    @Test
    fun formats_four_variants() {
        val tCenti = SubTime.ofMillis(83_450)
        val tMilli = SubTime.ofMillis(83_456)
        assertEquals("[01:23.45]", tCenti.toLrcString(LrcTimeFormat.XX))
        assertEquals("[01:23.456]", tMilli.toLrcString(LrcTimeFormat.XXX))
        assertEquals("[01:23:45]", tCenti.toLrcString(LrcTimeFormat.XX_COLON))
        assertEquals("[01:23:456]", tMilli.toLrcString(LrcTimeFormat.XXX_COLON))
    }

    @Test
    fun parses_four_variants() {
        assertEquals(83_450_000L, SubTime.parseLrc("[01:23.45]").micros)
        assertEquals(83_456_000L, SubTime.parseLrc("[01:23.456]").micros)
        assertEquals(83_450_000L, SubTime.parseLrc("[01:23:45]").micros)
        assertEquals(83_456_000L, SubTime.parseLrc("[01:23:456]").micros)
    }

    @Test
    fun round_trip_all_variants() {
        // CENTI 格式截断到厘秒（10ms），使用能被厘秒整除的值保证四种格式都零损失
        val t = SubTime.ofMillis(83_450)
        listOf(LrcTimeFormat.XX, LrcTimeFormat.XXX, LrcTimeFormat.XX_COLON, LrcTimeFormat.XXX_COLON).forEach {
            assertEquals(t.micros, SubTime.parseLrc(t.toLrcString(it)).micros, "failed for $it")
        }
    }
}
