package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.Margins
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * ResolutionResampler 测试：缩放 PlayRes + {\pos}/{\move} + 样式字号/描边/边距。
 *
 * @author 伤感咩吖
 */
class ResolutionResamplerTest {

    private fun style(
        fontSize: Double = 48.0,
        outline: Double = 2.0,
        shadow: Double = 2.0,
        margins: Margins = Margins(10, 20, 30),
    ) = AssStyle(fontSize = fontSize, outlineWidth = outline, shadowWidth = shadow, margins = margins)

    private fun script(text: String, s: AssStyle = style()) = AssScript(
        info = persistentListOf(
            io.github.samgum.aegisub.domain.model.AssInfo("PlayResX", "384"),
            io.github.samgum.aegisub.domain.model.AssInfo("PlayResY", "288"),
        ),
        styles = persistentListOf(s),
        events = persistentListOf(
            io.github.samgum.aegisub.domain.model.AssEvent(
                start = SubTime.ZERO, end = SubTime.ofMillis(1000), text = text,
            ),
        ),
    )

    @Test fun rescale_updates_playres_in_info() {
        val r = ResolutionResampler.rescale(script("Hi"), 384, 288, 1920, 1080)
        assertEquals("1920", r.getScriptInfo("PlayResX"))
        assertEquals("1080", r.getScriptInfo("PlayResY"))
    }

    @Test fun rescale_scales_pos_coordinates() {
        // 384x288 → 1920x1080：sx=5, sy=3.75；pos(100,100) → (500, 375)
        val r = ResolutionResampler.rescale(script("{\\pos(100,100)}Hi"), 384, 288, 1920, 1080)
        assertEquals(500 to 375, VisualTags.getPos(r.events.first().text))
    }

    @Test fun rescale_scales_move_coordinates() {
        val r = ResolutionResampler.rescale(script("{\\move(10,10,20,20)}Hi"), 384, 288, 768, 576)
        // sx=2, sy=2 → (20,20,40,40)
        assertEquals(VisualTags.MoveParams(20, 20, 40, 40), VisualTags.getMove(r.events.first().text))
    }

    @Test fun rescale_no_scale_positions_keeps_pos() {
        val r = ResolutionResampler.rescale(
            script("{\\pos(100,100)}Hi"), 384, 288, 1920, 1080,
            options = ResolutionResampler.Options(scalePositions = false),
        )
        assertEquals(100 to 100, VisualTags.getPos(r.events.first().text))
    }

    @Test fun rescale_scales_font_and_borders() {
        // 288 → 576：sy=2；字号 48→96，描边 2→4，阴影 2→4
        val r = ResolutionResampler.rescale(script("Hi", style()), 384, 288, 384, 576)
        val s = r.styles.first()
        assertEquals(96.0, s.fontSize)
        assertEquals(4.0, s.outlineWidth)
        assertEquals(4.0, s.shadowWidth)
    }

    @Test fun rescale_scales_margins_by_axis() {
        // sx=2 (384→768), sy=2 (288→576)：L10→20, R20→40, V30→60
        val r = ResolutionResampler.rescale(script("Hi", style()), 384, 288, 768, 576)
        val m = r.styles.first().margins
        assertEquals(20, m.left)
        assertEquals(40, m.right)
        assertEquals(60, m.vertical)
    }

    @Test fun rescale_no_scale_borders_keeps_outline_shadow() {
        val r = ResolutionResampler.rescale(
            script("Hi", style(outline = 2.0, shadow = 3.0)), 384, 288, 384, 576,
            options = ResolutionResampler.Options(scaleBorders = false),
        )
        val s = r.styles.first()
        assertEquals(2.0, s.outlineWidth) // 不缩放
        assertEquals(3.0, s.shadowWidth)
        // 字号仍按 sy 缩放（字号缩放不受 scaleBorders 控制）
        assertEquals(96.0, s.fontSize)
    }

    @Test fun rescale_identity_keeps_values() {
        val src = script("{\\pos(100,200)}Hi", style())
        val r = ResolutionResampler.rescale(src, 384, 288, 384, 288)
        assertEquals(100 to 200, VisualTags.getPos(r.events.first().text))
        assertEquals(48.0, r.styles.first().fontSize)
    }

    @Test fun rescale_preserves_event_timing_and_text() {
        val r = ResolutionResampler.rescale(script("Hi"), 384, 288, 1920, 1080)
        val e = r.events.first()
        assertEquals(0L, e.start.millis)
        assertEquals(1000L, e.end.millis)
        assertEquals("Hi", e.text)
    }

    @Test fun rescale_invalid_resolution_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            ResolutionResampler.rescale(script("Hi"), 0, 288, 1920, 1080)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResolutionResampler.rescale(script("Hi"), 384, 288, 1920, 0)
        }
    }
}
