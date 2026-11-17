package io.github.samgum.aegisub.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * ASS 文件容器：不可变。支持 `[Script Info]`、`[V4+ Styles]`、`[Events]` 以及 `[Fonts]` / `[Graphics]` 内嵌附件。
 *
 * @author 伤感咩吖
 */
data class AssScript(
    val info: ImmutableList<AssInfo> = persistentListOf(),
    val styles: ImmutableList<AssStyle> = persistentListOf(),
    val events: ImmutableList<AssEvent> = persistentListOf(),
    val attachments: ImmutableList<AssAttachment> = persistentListOf(),
    val properties: Map<String, String> = emptyMap(),
) {
    fun getScriptInfo(key: String): String? = info.firstOrNull { it.key == key }?.value

    fun withEvent(event: AssEvent): AssScript = copy(events = (events + event).toPersistentList())
    fun withEvents(newEvents: List<AssEvent>): AssScript = copy(events = newEvents.toPersistentList())
    fun withStyle(style: AssStyle): AssScript = copy(styles = (styles + style).toPersistentList())
    fun withStyles(newStyles: List<AssStyle>): AssScript = copy(styles = newStyles.toPersistentList())
    fun withInfo(newInfo: List<AssInfo>): AssScript = copy(info = newInfo.toPersistentList())
    fun withAttachments(newAttachments: List<AssAttachment>): AssScript = copy(attachments = newAttachments.toPersistentList())

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
