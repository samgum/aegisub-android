package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.Margins
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.roundToInt

/**
 * 分辨率重采样器（复刻桌面 Aegisub Resolution Resampler）。
 *
 * 改变脚本分辨率（PlayResX/Y）并按比例缩放：
 * - **{\pos}/{\move}** 坐标（[Options.scalePositions]，按各自轴比例）。
 * - **样式字号**（按 Y 轴）、**描边/阴影**（[Options.scaleBorders]，按 Y 轴）、
 *   **边距**（L/R 按 X 轴，V 按 Y 轴）。
 * - **[Script Info] 的 PlayResX/PlayResY** 更新为目标值。
 *
 * 不改时间/文本/其它字段。纯函数；通过 `session.editScript` 接入，单撤销点。
 *
 * @author 伤感咩吖
 */
object ResolutionResampler {

    /** 重采样选项。 */
    data class Options(
        val scalePositions: Boolean = true,
        val scaleBorders: Boolean = true,
    )

    fun rescale(
        script: AssScript,
        fromW: Int,
        fromH: Int,
        toW: Int,
        toH: Int,
        options: Options = Options(),
    ): AssScript {
        require(fromW > 0 && fromH > 0 && toW > 0 && toH > 0) {
            "分辨率必须为正：from=${fromW}x$fromH to=${toW}x$toH"
        }
        val sx = toW.toDouble() / fromW
        val sy = toH.toDouble() / fromH

        val newEvents = if (options.scalePositions) {
            script.events.map { e -> e.copy(text = scalePositionTags(e.text, sx, sy)) }.toPersistentList()
        } else {
            script.events
        }
        val newStyles = script.styles.map { scaleStyle(it, sx, sy, options.scaleBorders) }.toPersistentList()
        var info = ScriptInfoOps.set(script.info, "PlayResX", toW.toString())
        info = ScriptInfoOps.set(info, "PlayResY", toH.toString())

        return script.copy(events = newEvents, styles = newStyles, info = info.toPersistentList())
    }

    /** 缩放文本中的 {\pos}/{\move} 坐标（无则原样返回）。 */
    private fun scalePositionTags(text: String, sx: Double, sy: Double): String {
        var t = text
        VisualTags.getPos(t)?.let { (x, y) ->
            t = VisualTags.setPos(t, (x * sx).roundToInt(), (y * sy).roundToInt())
        }
        VisualTags.getMove(t)?.let { m ->
            t = VisualTags.setMove(
                t,
                (m.x1 * sx).roundToInt(), (m.y1 * sy).roundToInt(),
                (m.x2 * sx).roundToInt(), (m.y2 * sy).roundToInt(),
                m.t1, m.t2,
            )
        }
        return t
    }

    /** 缩放单个样式的字号/描边/阴影/边距。 */
    private fun scaleStyle(s: AssStyle, sx: Double, sy: Double, scaleBorders: Boolean): AssStyle {
        val newMargins = Margins(
            left = (s.margins.left * sx).roundToInt(),
            right = (s.margins.right * sx).roundToInt(),
            vertical = (s.margins.vertical * sy).roundToInt(),
        )
        return s.copy(
            fontSize = s.fontSize * sy,
            outlineWidth = if (scaleBorders) s.outlineWidth * sy else s.outlineWidth,
            shadowWidth = if (scaleBorders) s.shadowWidth * sy else s.shadowWidth,
            margins = newMargins,
        )
    }
}
