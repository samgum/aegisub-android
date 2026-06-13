package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * ScriptInfoOps 测试：[Script Info] 键值对的 set / get / remove。
 *
 * @author 伤感咩吖
 */
class ScriptInfoOpsTest {

    private val base = listOf(
        AssInfo("ScriptType", "v4.00+"),
        AssInfo("PlayResX", "384"),
        AssInfo("PlayResY", "288"),
    )

    @Test fun set_updates_existing_key_in_place() {
        val r = ScriptInfoOps.set(base, "PlayResX", "1920")
        assertEquals("1920", ScriptInfoOps.get(r, "PlayResX"))
        // 其余键不受影响，且不重复插入
        assertEquals(3, r.size)
        assertEquals("v4.00+", ScriptInfoOps.get(r, "ScriptType"))
    }

    @Test fun set_inserts_new_key_at_end_when_absent() {
        val r = ScriptInfoOps.set(base, "Title", "我的字幕")
        assertEquals(4, r.size)
        assertEquals("我的字幕", ScriptInfoOps.get(r, "Title"))
        // 新键追加在末尾
        assertEquals("Title", r.last().key)
    }

    @Test fun set_preserves_order_of_other_keys() {
        val r = ScriptInfoOps.set(base, "PlayResY", "1080")
        assertEquals(listOf("ScriptType", "PlayResX", "PlayResY"), r.map { it.key })
    }

    @Test fun get_returns_null_when_absent() {
        assertNull(ScriptInfoOps.get(base, "Title"))
    }

    @Test fun get_returns_value_when_present() {
        assertEquals("384", ScriptInfoOps.get(base, "PlayResX"))
    }

    @Test fun remove_drops_matching_key() {
        val r = ScriptInfoOps.remove(base, "PlayResY")
        assertEquals(2, r.size)
        assertNull(ScriptInfoOps.get(r, "PlayResY"))
    }

    @Test fun remove_absent_key_is_noop() {
        assertEquals(base, ScriptInfoOps.remove(base, "Nonexistent"))
    }

    @Test fun set_empty_value_is_allowed() {
        val r = ScriptInfoOps.set(base, "Title", "")
        assertEquals("", ScriptInfoOps.get(r, "Title"))
    }

    @Test fun set_multiple_then_remove_round_trip() {
        var info = base
        info = ScriptInfoOps.set(info, "WrapStyle", "2")
        info = ScriptInfoOps.set(info, "Collisions", "Reverse")
        info = ScriptInfoOps.set(info, "Title", "测试")
        assertEquals(6, info.size)
        assertEquals("2", ScriptInfoOps.get(info, "WrapStyle"))
        assertEquals("测试", ScriptInfoOps.get(info, "Title"))
        info = ScriptInfoOps.remove(info, "Collisions")
        assertEquals(5, info.size)
        assertNull(ScriptInfoOps.get(info, "Collisions"))
    }
}
