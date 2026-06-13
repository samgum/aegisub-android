package io.github.samgum.aegisub.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * ASS 文件容器：不可变。编辑通过 with* 方法产生新实例（结构共享）。
 *
 * @author 伤感咩吖
 */
data class AssScript(
    val info: ImmutableList<AssInfo> = persistentListOf(),
    val styles: ImmutableList<AssStyle> = persistentListOf(),
    val events: ImmutableList<AssEvent> = persistentListOf(),
    val properties: Map<String, String> = emptyMap(),
) {
    fun getScriptInfo(key: String): String? = info.firstOrNull { it.key == key }?.value

    fun withEvent(event: AssEvent): AssScript = copy(events = (events + event).toPersistentList())
    fun withEvents(newEvents: List<AssEvent>): AssScript = copy(events = newEvents.toPersistentList())
    fun withStyle(style: AssStyle): AssScript = copy(styles = (styles + style).toPersistentList())

    companion object {
        fun default(): AssScript = AssScript(
            info = persistentListOf(
                AssInfo("ScriptType", "v4.00+"),
                AssInfo("PlayResX", "384"),
                AssInfo("PlayResY", "288"),
            ),
            styles = persistentListOf(AssStyle()),
            events = persistentListOf(),
        )
    }
}
