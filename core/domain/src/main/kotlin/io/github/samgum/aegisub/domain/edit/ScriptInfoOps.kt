package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssInfo

/**
 * [Script Info] 段键值操作（复刻桌面 Aegisub Properties 对 [Script Info] 的编辑）。
 *
 * 纯函数：不改原列表。键大小写敏感（ASS 规范区分大小写）。
 * 通过 `session.editInfo { ScriptInfoOps.xxx(it, ...) }` 接入。
 *
 * @author 伤感咩吖
 */
object ScriptInfoOps {

    /** 取指定键的值；不存在返回 null。 */
    fun get(info: List<AssInfo>, key: String): String? = info.firstOrNull { it.key == key }?.value

    /**
     * 设置键值：键已存在则原地更新（保留位置），不存在则追加到末尾。
     */
    fun set(info: List<AssInfo>, key: String, value: String): List<AssInfo> {
        val updated = info.map { if (it.key == key) AssInfo(key, value) else it }
        return if (updated.any { it.key == key }) updated else info + AssInfo(key, value)
    }

    /** 删除指定键；不存在则原样返回。 */
    fun remove(info: List<AssInfo>, key: String): List<AssInfo> = info.filterNot { it.key == key }
}
