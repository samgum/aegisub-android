package io.github.samgum.aegisub.domain.edit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * VisualTags 测试：{\pos}/{\fr} 覆盖标签的注入、替换、解析、移除。
 *
 * @author 伤感咩吖
 */
class VisualTagsTest {

    // ---------------- \pos ----------------

    @Test fun setPos_inserts_block_when_absent() {
        assertEquals("{\\pos(100,200)}Hello", VisualTags.setPos("Hello", 100, 200))
    }

    @Test fun setPos_inserts_into_empty_text() {
        assertEquals("{\\pos(0,0)}", VisualTags.setPos("", 0, 0))
    }

    @Test fun setPos_replaces_existing_args() {
        assertEquals("{\\pos(50,60)}Hello", VisualTags.setPos("{\\pos(10,20)}Hello", 50, 60))
    }

    @Test fun setPos_replaces_inside_grouped_block() {
        // 已有标签组里含 \pos：仅替换 \pos 参数，其余标签保留
        val r = VisualTags.setPos("{\\b1\\pos(1,2)\\i1}Hi", 9, 8)
        assertEquals("{\\b1\\pos(9,8)\\i1}Hi", r)
    }

    @Test fun getPos_returns_coords() {
        assertEquals(100 to 200, VisualTags.getPos("{\\pos(100,200)}Hi"))
    }

    @Test fun getPos_finds_inside_group() {
        assertEquals(1 to 2, VisualTags.getPos("{\\b1\\pos(1,2)}Hi"))
    }

    @Test fun getPos_returns_null_when_absent() {
        assertNull(VisualTags.getPos("Hello"))
        assertNull(VisualTags.getPos("{\\b1}Hi"))
    }

    @Test fun removePos_drops_tag_keeps_others() {
        assertEquals("{\\b1\\i1}Hi", VisualTags.removePos("{\\b1\\pos(1,2)\\i1}Hi"))
        assertEquals("Hello", VisualTags.removePos("Hello"))
    }

    // ---------------- \fr ----------------

    @Test fun setRotation_inserts_when_absent() {
        assertEquals("{\\fr30}Hello", VisualTags.setRotation("Hello", 30))
    }

    @Test fun setRotation_replaces_existing() {
        assertEquals("{\\fr45}Hi", VisualTags.setRotation("{\\fr10}Hi", 45))
    }

    @Test fun setRotation_does_not_touch_frx_fry() {
        // \frx/\fry 不被 \fr 替换误伤
        val r = VisualTags.setRotation("{\\frx20\\fry10}Hi", 90)
        assertEquals("{\\fr90\\frx20\\fry10}Hi", r)
    }

    @Test fun getRotation_returns_value() {
        assertEquals(30, VisualTags.getRotation("{\\fr30}Hi"))
    }

    @Test fun getRotation_returns_zero_when_absent() {
        assertEquals(0, VisualTags.getRotation("Hi"))
    }

    @Test fun removeRotation_drops_only_fr() {
        assertEquals("{\\frx20}Hi", VisualTags.removeRotation("{\\fr30\\frx20}Hi"))
    }

    // ---------------- 往返 ----------------

    @Test fun setPos_then_get_round_trips() {
        val text = VisualTags.setPos("Hello", 320, 240)
        assertEquals(320 to 240, VisualTags.getPos(text))
    }

    @Test fun pos_and_rotation_coexist() {
        var t = VisualTags.setPos("Hi", 10, 20)
        t = VisualTags.setRotation(t, 45)
        assertEquals(10 to 20, VisualTags.getPos(t))
        assertEquals(45, VisualTags.getRotation(t))
    }

    // ---------------- \move ----------------

    @Test fun setMove_inserts_four_arg_when_no_times() {
        assertEquals("{\\move(1,2,3,4)}Hi", VisualTags.setMove("Hi", 1, 2, 3, 4))
    }

    @Test fun setMove_inserts_six_arg_with_times() {
        assertEquals("{\\move(1,2,3,4,100,500)}Hi", VisualTags.setMove("Hi", 1, 2, 3, 4, 100, 500))
    }

    @Test fun setMove_replaces_existing() {
        assertEquals("{\\move(9,8,7,6)}Hi", VisualTags.setMove("{\\move(1,2,3,4)}Hi", 9, 8, 7, 6))
    }

    @Test fun getMove_parses_four_arg() {
        assertEquals(VisualTags.MoveParams(1, 2, 3, 4), VisualTags.getMove("{\\move(1,2,3,4)}Hi"))
    }

    @Test fun getMove_parses_six_arg() {
        assertEquals(
            VisualTags.MoveParams(1, 2, 3, 4, 100, 500),
            VisualTags.getMove("{\\move(1,2,3,4,100,500)}Hi"),
        )
    }

    @Test fun getMove_returns_null_when_absent() {
        assertNull(VisualTags.getMove("Hi"))
    }

    @Test fun removeMove_drops_only_move() {
        assertEquals("{\\b1}Hi", VisualTags.removeMove("{\\move(1,2,3,4)\\b1}Hi"))
    }

    // ---------------- \fad ----------------

    @Test fun setFade_inserts() {
        assertEquals("{\\fad(200,300)}Hi", VisualTags.setFade("Hi", 200, 300))
    }

    @Test fun setFade_replaces() {
        assertEquals("{\\fad(500,600)}Hi", VisualTags.setFade("{\\fad(1,2)}Hi", 500, 600))
    }

    @Test fun getFade_parses() {
        assertEquals(VisualTags.FadeParams(200, 300), VisualTags.getFade("{\\fad(200,300)}Hi"))
    }

    @Test fun getFade_returns_null_when_absent() {
        assertNull(VisualTags.getFade("Hi"))
    }

    @Test fun removeFade_drops_only_fad() {
        assertEquals("{\\b1}Hi", VisualTags.removeFade("{\\fad(1,2)\\b1}Hi"))
    }

    @Test fun move_and_fad_and_pos_coexist() {
        var t = VisualTags.setPos("Hi", 10, 20)
        t = VisualTags.setMove(t, 1, 2, 3, 4)
        t = VisualTags.setFade(t, 100, 200)
        // 注意：\pos 与 \move 互斥（libass 中 \move 覆盖 \pos），此处仅校验三者都能存取
        assertEquals(10 to 20, VisualTags.getPos(t))
        assertEquals(VisualTags.MoveParams(1, 2, 3, 4), VisualTags.getMove(t))
        assertEquals(VisualTags.FadeParams(100, 200), VisualTags.getFade(t))
    }

    // ---------------- \clip / \iclip ----------------

    @Test fun setClip_inserts_rectangle() {
        assertEquals("{\\clip(0,0,100,100)}Hi", VisualTags.setClip("Hi", 0, 0, 100, 100))
    }

    @Test fun setClip_iclip_uses_iclip_tag() {
        assertEquals("{\\iclip(1,2,3,4)}Hi", VisualTags.setClip("Hi", 1, 2, 3, 4, inverse = true))
    }

    @Test fun setClip_replaces_existing_rectangle() {
        assertEquals("{\\clip(9,8,7,6)}Hi", VisualTags.setClip("{\\clip(1,2,3,4)}Hi", 9, 8, 7, 6))
    }

    @Test fun getClip_parses_rectangle() {
        assertEquals(VisualTags.ClipRect(0, 0, 100, 100), VisualTags.getClip("{\\clip(0,0,100,100)}Hi"))
        assertEquals(VisualTags.ClipRect(1, 2, 3, 4, inverse = true), VisualTags.getClip("{\\iclip(1,2,3,4)}Hi"))
    }

    @Test fun getClip_returns_null_for_vector_or_absent() {
        assertNull(VisualTags.getClip("Hi"))
        assertNull(VisualTags.getClip("{\\clip(m 0 0 l 10 10)}Hi"))
    }

    @Test fun removeClip_drops_only_rectangle_clip() {
        assertEquals("{\\b1}Hi", VisualTags.removeClip("{\\clip(1,2,3,4)\\b1}Hi"))
    }

    @Test fun setClip_round_trips() {
        val t = VisualTags.setClip("Hi", 5, 6, 7, 8)
        assertEquals(VisualTags.ClipRect(5, 6, 7, 8), VisualTags.getClip(t))
    }
}
