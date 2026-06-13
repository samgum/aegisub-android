package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * SortLines 排序测试：按起止/样式/演员/效果/文本/层，升序降序，相等项稳定。
 *
 * @author 伤感咩吖
 */
class SortLinesTest {

    private fun ev(
        id: Long,
        startMs: Long = 0L,
        endMs: Long = 5_000L,
        style: String = "Default",
        actor: String = "",
        effect: String = "",
        text: String = "t$id",
        layer: Int = 0,
    ) = AssEvent(
        id = id,
        start = SubTime.ofMillis(startMs),
        end = SubTime.ofMillis(endMs),
        style = style,
        actor = actor,
        effect = effect,
        text = text,
        layer = layer,
    )

    private fun ids(events: List<AssEvent>) = events.map { it.id }

    @Test fun sort_by_start_ascending() {
        val events = listOf(ev(0, startMs = 3_000), ev(1, startMs = 1_000), ev(2, startMs = 2_000))
        val r = SortLines.apply(events, SortKey.START, SortOrder.ASCENDING)
        assertEquals(listOf(1L, 2L, 0L), ids(r))
    }

    @Test fun sort_by_start_descending() {
        val events = listOf(ev(0, startMs = 3_000), ev(1, startMs = 1_000), ev(2, startMs = 2_000))
        val r = SortLines.apply(events, SortKey.START, SortOrder.DESCENDING)
        assertEquals(listOf(0L, 2L, 1L), ids(r))
    }

    @Test fun sort_by_end_time() {
        val events = listOf(ev(0, endMs = 9_000), ev(1, endMs = 2_000), ev(2, endMs = 5_000))
        val r = SortLines.apply(events, SortKey.END, SortOrder.ASCENDING)
        assertEquals(listOf(1L, 2L, 0L), ids(r))
    }

    @Test fun sort_by_style_name() {
        val events = listOf(ev(0, style = "Title"), ev(1, style = "Default"), ev(2, style = "Alt"))
        val r = SortLines.apply(events, SortKey.STYLE, SortOrder.ASCENDING)
        // Alt < Default < Title（字典序）
        assertEquals(listOf(2L, 1L, 0L), ids(r))
    }

    @Test fun sort_by_actor_name() {
        val events = listOf(ev(0, actor = "Bob"), ev(1, actor = "Alice"), ev(2, actor = "Carol"))
        val r = SortLines.apply(events, SortKey.ACTOR, SortOrder.ASCENDING)
        assertEquals(listOf(1L, 0L, 2L), ids(r))
    }

    @Test fun sort_by_effect() {
        val events = listOf(ev(0, effect = "z"), ev(1, effect = "a"), ev(2, effect = "m"))
        val r = SortLines.apply(events, SortKey.EFFECT, SortOrder.ASCENDING)
        assertEquals(listOf(1L, 2L, 0L), ids(r))
    }

    @Test fun sort_by_layer() {
        val events = listOf(ev(0, layer = 5), ev(1, layer = 1), ev(2, layer = 3))
        val r = SortLines.apply(events, SortKey.LAYER, SortOrder.ASCENDING)
        assertEquals(listOf(1L, 2L, 0L), ids(r))
    }

    @Test fun sort_by_text() {
        val events = listOf(ev(0, text = "c"), ev(1, text = "a"), ev(2, text = "b"))
        val r = SortLines.apply(events, SortKey.TEXT, SortOrder.ASCENDING)
        assertEquals(listOf(1L, 2L, 0L), ids(r))
    }

    @Test fun equal_keys_keep_original_order_stable() {
        // 三条 start 相同：稳定排序应保持原序
        val events = listOf(ev(0, startMs = 1_000), ev(1, startMs = 1_000), ev(2, startMs = 1_000))
        val r = SortLines.apply(events, SortKey.START, SortOrder.ASCENDING)
        assertEquals(listOf(0L, 1L, 2L), ids(r))
    }

    @Test fun descending_equal_keys_keep_original_order() {
        val events = listOf(ev(0, startMs = 1_000), ev(1, startMs = 1_000), ev(2, startMs = 1_000))
        val r = SortLines.apply(events, SortKey.START, SortOrder.DESCENDING)
        // 相等项不因反转而颠倒（Comparator.reversed 对相等元素仍判 0，稳定）
        assertEquals(listOf(0L, 1L, 2L), ids(r))
    }

    @Test fun empty_list_unchanged() {
        assertEquals(emptyList<AssEvent>(), SortLines.apply(emptyList(), SortKey.START))
    }

    @Test fun single_element_unchanged() {
        val events = listOf(ev(0))
        assertEquals(events, SortLines.apply(events, SortKey.START))
    }

    @Test fun sort_does_not_mutate_event_fields() {
        val e = ev(0, startMs = 1_000, endMs = 3_000, text = "Hello", style = "Title")
        val r = SortLines.apply(listOf(e), SortKey.START)
        assertEquals(1_000L, r[0].start.millis)
        assertEquals(3_000L, r[0].end.millis)
        assertEquals("Hello", r[0].text)
        assertEquals("Title", r[0].style)
    }
}
