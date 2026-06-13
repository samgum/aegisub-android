package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent

/**
 * 样式批量替换：把所有 style == [fromStyle] 的事件改为 [toStyle]。
 *
 * - 样式名大小写敏感（ASS 规范如此）。
 * - [fromStyle] 为空时 no-op，避免误伤空样式名事件。
 *
 * @author 伤感咩吖
 */
object StyleReplace {
    fun apply(events: List<AssEvent>, fromStyle: String, toStyle: String): List<AssEvent> {
        if (fromStyle.isEmpty()) return events
        return events.map { if (it.style == fromStyle) it.copy(style = toStyle) else it }
    }
}
