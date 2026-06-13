package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime

/**
 * 帧率转换（复刻桌面 Aegisub Change Framerate）。
 *
 * 按比例 `toFps / fromFps` 等比缩放每条事件的起止时间（微秒层精确计算，
 * 末步经 [SubTime.ofMicros] 钳制到 [0, 10h]）。文本与其它字段不变。
 *
 * 典型用途：字幕按 24fps 打轴，实际视频 25fps → 整体拉长 25/24。
 *
 * 通过 `session.editEvents { FramerateConverter.rescale(it, from, to) }` 接入，一次撤销点。
 *
 * @author 伤感咩吖
 */
object FramerateConverter {
    fun rescale(events: List<AssEvent>, fromFps: Double, toFps: Double): List<AssEvent> {
        require(fromFps > 0.0) { "fromFps 必须为正数，当前 $fromFps" }
        require(toFps > 0.0) { "toFps 必须为正数，当前 $toFps" }
        val factor = toFps / fromFps
        return events.map { e ->
            e.copy(
                start = SubTime.ofMicros((e.start.micros * factor).toLong()),
                end = SubTime.ofMicros((e.end.micros * factor).toLong()),
            )
        }
    }

    /** 常见帧率预设（含 NTSC 分数帧率），供 UI 选用。 */
    val PRESETS: List<Pair<String, Double>> = listOf(
        "23.976" to 23.976,
        "24" to 24.0,
        "25" to 25.0,
        "29.97" to 29.97,
        "30" to 30.0,
        "50" to 50.0,
        "59.94" to 59.94,
        "60" to 60.0,
    )
}
