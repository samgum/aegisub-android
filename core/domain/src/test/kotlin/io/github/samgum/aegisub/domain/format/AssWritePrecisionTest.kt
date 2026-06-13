package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ASS 写入精度：默认厘秒（2 位），THREE_MS 毫秒（3 位），AUTO 走厘秒标准。
 * 验证 WriteOptions.timePrecision 真正穿透到 Dialogue 行。
 *
 * @author 伤感咩吖
 */
class AssWritePrecisionTest {

    private fun script(): AssScript = AssScript.default().withEvents(
        listOf(
            AssEvent(
                id = 0L,
                row = 0,
                start = SubTime.ofMillis(1_000),
                end = SubTime.ofMillis(2_500),
                text = "精度测试",
            ),
        ),
    )

    private fun dialogueLine(out: String): String =
        out.lineSequence().first { it.startsWith("Dialogue:", ignoreCase = true) }

    @Test fun default_is_centisecond() {
        val line = dialogueLine(AssFormat.write(script()))
        // 1.000s → 厘秒 0:00:01.00；2.500s → 0:00:02.50
        assertTrue(line.contains("0:00:01.00,"), "默认应厘秒：$line")
        assertTrue(line.contains(",0:00:02.50,"), "默认应厘秒：$line")
    }

    @Test fun three_ms_writes_millisecond() {
        val out = AssFormat.write(script(), WriteOptions(timePrecision = TimePrecision.THREE_MS))
        val line = dialogueLine(out)
        assertTrue(line.contains("0:00:01.000,"), "THREE_MS 应毫秒：$line")
        assertTrue(line.contains(",0:00:02.500,"), "THREE_MS 应毫秒：$line")
    }

    @Test fun two_ms_is_centisecond() {
        val out = AssFormat.write(script(), WriteOptions(timePrecision = TimePrecision.TWO_MS))
        val line = dialogueLine(out)
        assertTrue(line.contains("0:00:01.00,"), "TWO_MS 应厘秒：$line")
    }

    @Test fun auto_is_centisecond() {
        val out = AssFormat.write(script(), WriteOptions(timePrecision = TimePrecision.AUTO))
        val line = dialogueLine(out)
        assertTrue(line.contains("0:00:01.00,"), "AUTO 应厘秒（ASS 标准）：$line")
    }

    @Test fun precision_round_trips_lossless() {
        // 毫秒写入后重新解析，时间零损失（厘秒会丢 5ms 以内）
        val s = script()
        val reparsed = AssFormat.read(AssFormat.write(s, WriteOptions(timePrecision = TimePrecision.THREE_MS)))
        assertEquals(s.events[0].start, reparsed.events[0].start)
        assertEquals(s.events[0].end, reparsed.events[0].end)
    }
}
