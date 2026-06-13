package io.github.samgum.aegisub.domain.preview

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * ActiveSubtitleResolver 纯函数测试。
 *
 * @author 伤感咩吖
 */
class ActiveSubtitleResolverTest {

    private val script = AssScript(
        styles = persistentListOf(
            AssStyle(name = "Default", alignment = 2),
            AssStyle(name = "Title", alignment = 8),
        ),
        events = persistentListOf(
            AssEvent(id = 0, start = SubTime.ofMillis(1_000), end = SubTime.ofMillis(3_000), style = "Default", text = "第一句"),
            AssEvent(id = 1, comment = true, start = SubTime.ofMillis(3_000), end = SubTime.ofMillis(4_000), text = "注释"),
            AssEvent(id = 2, start = SubTime.ofMillis(4_000), end = SubTime.ofMillis(6_000), style = "Title", text = "第二句"),
        ),
    )

    @Test fun activeEvent_returns_event_within_range() {
        val ev = ActiveSubtitleResolver.activeEvent(script, 2_000)
        assertEquals(0L, ev?.id)
    }

    @Test fun activeEvent_at_start_returns_event() {
        assertEquals(0L, ActiveSubtitleResolver.activeEvent(script, 1_000)?.id)
    }

    @Test fun activeEvent_at_end_returns_null() {
        // 半开区间 [start, end)：t == end 不属于本行
        assertNull(ActiveSubtitleResolver.activeEvent(script, 3_000))
    }

    @Test fun activeEvent_skips_comment_lines() {
        // 3_000~4_000 是注释行，应被跳过返回 null
        assertNull(ActiveSubtitleResolver.activeEvent(script, 3_500))
    }

    @Test fun activeEvent_null_when_no_active() {
        assertNull(ActiveSubtitleResolver.activeEvent(script, 999))
    }

    @Test fun renderInfo_resolves_text_and_named_style() {
        val info = ActiveSubtitleResolver.renderInfo(script, 5_000)
        assertEquals("第二句", info?.text)
        assertEquals("Title", info?.style?.name)
    }

    @Test fun renderInfo_falls_back_to_default_style_when_name_missing() {
        // event.style="Default" 命中 Default 样式
        val info = ActiveSubtitleResolver.renderInfo(script, 2_000)
        assertEquals("Default", info?.style?.name)
    }

    @Test fun renderInfo_falls_back_to_first_style_when_no_match_or_default() {
        val noDefault = AssScript(
            styles = persistentListOf(AssStyle(name = "Custom")),
            events = persistentListOf(
                AssEvent(id = 0, start = SubTime.ofMillis(0), end = SubTime.ofMillis(1_000), style = "Missing", text = "x"),
            ),
        )
        val info = ActiveSubtitleResolver.renderInfo(noDefault, 500)
        assertEquals("Custom", info?.style?.name)
    }

    @Test fun renderInfo_null_when_no_active_event() {
        assertNull(ActiveSubtitleResolver.renderInfo(script, 9_999))
    }
}
