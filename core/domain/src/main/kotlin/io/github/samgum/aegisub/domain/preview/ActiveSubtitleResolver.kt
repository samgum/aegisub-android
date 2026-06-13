package io.github.samgum.aegisub.domain.preview

import io.github.samgum.aegisub.domain.edit.VisualTags
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.Margins

/**
 * 渲染一条叠加字幕所需的信息：去标签纯文本 + 解析后的样式 + 合并边距 + 脚本分辨率
 * +（可选）{\pos} 定位锚点。
 *
 * @author 伤感咩吖
 */
data class SubtitleRenderInfo(
    val text: String,
    val style: AssStyle,
    val margins: Margins,
    val playResX: Int = 384,
    val playResY: Int = 288,
    /** {\pos(x,y)} 锚点（脚本坐标系）；null 表示按对齐+边距定位。 */
    val pos: Pair<Int, Int>? = null,
)

/**
 * 给定脚本与播放位置，解析当前应叠加显示的字幕。
 * 纯函数，无 Android/Compose 依赖，可纯 JVM 单测。
 *
 * @author 伤感咩吖
 */
object ActiveSubtitleResolver {

    /** 返回当前时间点应显示的事件（非注释、start <= t < end），无则 null。 */
    fun activeEvent(script: AssScript, positionMs: Long): AssEvent? =
        script.events.firstOrNull { event ->
            !event.comment &&
                event.start.millis <= positionMs &&
                positionMs < event.end.millis
        }

    /** 返回当前应叠加的渲染信息（无活动事件返回 null）。 */
    fun renderInfo(script: AssScript, positionMs: Long): SubtitleRenderInfo? {
        val event = activeEvent(script, positionMs) ?: return null
        val style = resolveStyle(script, event)
        val margins = Margins(
            left = style.margins.left + event.margins.left,
            right = style.margins.right + event.margins.right,
            vertical = style.margins.vertical + event.margins.vertical,
        )
        return SubtitleRenderInfo(
            text = event.strippedText,
            style = style,
            margins = margins,
            playResX = script.getScriptInfo("PlayResX")?.toIntOrNull() ?: 384,
            playResY = script.getScriptInfo("PlayResY")?.toIntOrNull() ?: 288,
            pos = VisualTags.getPos(event.text),
        )
    }

    /** 样式解析：按名匹配 → Default → 首个 → 默认 AssStyle。 */
    fun resolveStyle(script: AssScript, event: AssEvent): AssStyle =
        script.styles.firstOrNull { it.name == event.style }
            ?: script.styles.firstOrNull { it.name == "Default" }
            ?: script.styles.firstOrNull()
            ?: AssStyle()
}
