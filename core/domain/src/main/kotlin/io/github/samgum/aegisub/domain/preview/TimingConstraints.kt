package io.github.samgum.aegisub.domain.preview

import io.github.samgum.aegisub.domain.time.SubTime

/**
 * 字幕行起止时间的合法化约束（spec §7）：
 * - start 不小于 0
 * - end 不小于 start + 1ms（最小持续，防零宽）
 * - end 不大于 durationMs（仅在有媒体/已知时长，即 durationMs > 0 时强制）
 *
 * 纯函数，无副作用，可纯 JVM 单测。
 *
 * @author 伤感咩吖
 */
object TimingConstraints {

    private val MIN_DURATION = SubTime.ofMillis(1)

    /** 把期望的 start/end 钳制为合法对，返回 (start, end)。 */
    fun constrain(start: SubTime, end: SubTime, durationMs: Long): Pair<SubTime, SubTime> {
        val s = if (start.millis < 0) SubTime.ZERO else start
        var e = end
        if (durationMs > 0 && e.millis > durationMs) {
            e = SubTime.ofMillis(durationMs)
        }
        if (e <= s) {
            e = s + MIN_DURATION
        }
        return s to e
    }
}
