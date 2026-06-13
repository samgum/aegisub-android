package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.DialogueBlock

/**
 * 删除「有效内容为空」的事件（复刻 Aegisub Delete Empty Lines）。
 *
 * 判定：解析为块后，若没有任何「含非空文本的 Plain 块」或「Drawing 块」→ 视为空。
 * 即纯空、纯空白、仅覆盖标签（{\k20}）、仅注释块（{*note}）都会被删；
 * 绘图行（{\p1}...{\p0}）即使不可视也保留。
 *
 * @author 伤感咩吖
 */
object DeleteEmpty {
    fun apply(events: List<AssEvent>): List<AssEvent> = events.filterNot { isEffectivelyEmpty(it.text) }

    private fun isEffectivelyEmpty(text: String): Boolean {
        val blocks = DialogueBlock.parse(text)
        return blocks.none { block ->
            block is DialogueBlock.Drawing ||
                (block is DialogueBlock.Plain && block.text.isNotBlank())
        }
    }
}
