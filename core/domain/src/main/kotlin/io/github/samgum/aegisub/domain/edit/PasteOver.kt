package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent

/**
 * 粘贴覆盖（复刻桌面 Aegisub Paste Over）：
 * 按 [orderedIds] 顺序把 [texts] 逐行覆盖到对应事件文本（保留时间/样式/其它字段）。
 *
 * - id 与 text 按 zip 配对（取较短），多余的 id 无对应文本则不变，多余的 text 忽略。
 * - 不存在的 id 自然跳过。
 *
 * 纯函数；通过 `session.editEvents { PasteOver.apply(it, ids, texts) }` 接入，单撤销点。
 *
 * @author 伤感咩吖
 */
object PasteOver {
    fun apply(events: List<AssEvent>, orderedIds: List<Long>, texts: List<String>): List<AssEvent> {
        val idToText: Map<Long, String> = orderedIds.zip(texts).toMap()
        if (idToText.isEmpty()) return events
        return events.map { e -> idToText[e.id]?.let { t -> e.copy(text = t) } ?: e }
    }
}
