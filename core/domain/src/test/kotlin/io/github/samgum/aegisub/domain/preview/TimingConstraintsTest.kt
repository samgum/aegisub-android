package io.github.samgum.aegisub.domain.preview

import io.github.samgum.aegisub.domain.time.SubTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * TimingConstraints 约束规则测试。
 *
 * @author 伤感咩吖
 */
class TimingConstraintsTest {

    @Test fun normal_pair_unchanged() {
        val (s, e) = TimingConstraints.constrain(SubTime.ofMillis(1_000), SubTime.ofMillis(3_000), 10_000)
        assertEquals(1_000L, s.millis)
        assertEquals(3_000L, e.millis)
    }

    @Test fun start_clamped_to_zero() {
        val (s, _) = TimingConstraints.constrain(SubTime.ofMillis(-500), SubTime.ofMillis(3_000), 10_000)
        assertEquals(0L, s.millis)
    }

    @Test fun end_clamped_to_duration_when_media_present() {
        val (_, e) = TimingConstraints.constrain(SubTime.ofMillis(9_000), SubTime.ofMillis(12_000), 10_000)
        assertEquals(10_000L, e.millis)
    }

    @Test fun end_not_clamped_when_no_media() {
        // durationMs <= 0 表示无媒体/未知时长，end 不设上界
        val (_, e) = TimingConstraints.constrain(SubTime.ofMillis(9_000), SubTime.ofMillis(120_000), 0)
        assertEquals(120_000L, e.millis)
    }

    @Test fun end_raised_to_start_plus_1ms_when_too_close() {
        val (s, e) = TimingConstraints.constrain(SubTime.ofMillis(5_000), SubTime.ofMillis(5_000), 10_000)
        assertEquals(5_000L, s.millis)
        assertEquals(5_001L, e.millis, "最小持续 1ms")
    }

    @Test fun end_raised_when_before_start() {
        val (s, e) = TimingConstraints.constrain(SubTime.ofMillis(8_000), SubTime.ofMillis(2_000), 10_000)
        assertEquals(8_000L, s.millis)
        assertEquals(8_001L, e.millis)
    }

    @Test fun both_clamped_together_start_near_duration() {
        // start=9500, end=9600, duration=10000：合法，不动
        val (s, e) = TimingConstraints.constrain(SubTime.ofMillis(9_500), SubTime.ofMillis(9_600), 10_000)
        assertEquals(9_500L, s.millis)
        assertEquals(9_600L, e.millis)
    }
}
