package io.github.samgum.aegisub.domain.model

/** 单个覆盖标签：\name 后跟原样值（带括号参数也保留为 rawValue）。
 *  @author 伤感咩吖
 */
data class AssOverrideTag(val name: String, val rawValue: String = "") {
    override fun toString(): String = if (rawValue.isEmpty()) "\\$name" else "\\$name$rawValue"
}

/** 对话文本块。Plain/Override/Comment/Drawing。
 *  将 ASS 事件文本切分为结构化的块序列，并支持重建原始文本。
 *  @author 伤感咩吖
 */
sealed interface DialogueBlock {
    val text: String

    /** 纯文本段。 */
    data class Plain(override val text: String) : DialogueBlock

    /** 注释块，以 {*} 或空 {} 标记。 */
    data class Comment(override val text: String) : DialogueBlock

    /** 覆盖标签块，包含零个或多个 [AssOverrideTag]。 */
    data class Override(val tags: List<AssOverrideTag>) : DialogueBlock {
        override val text: String get() = "{" + tags.joinToString("") { it.toString() } + "}"
    }

    /** 绘图指令块。 */
    data class Drawing(override val text: String, val scale: Int) : DialogueBlock

    companion object {
        /** 把事件文本切成块。空覆盖块 `{}` 视为 Comment。`{*...}` 视为 Comment。 */
        fun parse(text: String): List<DialogueBlock> {
            val blocks = mutableListOf<DialogueBlock>()
            var i = 0
            var plainStart = 0
            while (i < text.length) {
                if (text[i] == '{') {
                    // 先收集前面的纯文本
                    if (i > plainStart) blocks += Plain(text.substring(plainStart, i))
                    // 找到对应的闭合括号
                    val end = text.indexOf('}', i + 1)
                    val close = if (end < 0) text.length else end + 1
                    val inner = text.substring(i + 1, if (end < 0) text.length else end)
                    blocks += parseOverrideBlock(inner)
                    i = close
                    plainStart = i
                } else {
                    i++
                }
            }
            // 收集末尾的纯文本
            if (plainStart < text.length) blocks += Plain(text.substring(plainStart))
            return blocks
        }

        /** 解析花括号内部内容，决定是 Comment 还是 Override。 */
        private fun parseOverrideBlock(inner: String): DialogueBlock {
            // 空块或以 * 开头的块视为注释
            if (inner.isEmpty() || inner.startsWith("*")) return Comment(inner)

            val tags = mutableListOf<AssOverrideTag>()
            // 以 \ 分割，第一个元素是 \ 前的空段，跳过
            for (seg in inner.split('\\').drop(1)) {
                if (seg.isEmpty()) continue
                // 标签名由连续字母组成，其后为 rawValue
                val nameEnd = seg.indexOfFirst { !it.isLetter() }.let { if (it < 0) seg.length else it }
                val name = seg.substring(0, nameEnd)
                val raw = seg.substring(nameEnd)
                tags += AssOverrideTag(name, raw)
            }
            return Override(tags)
        }

        /** 由块列表重建文本（与原文本在合法输入上一致）。 */
        fun toText(blocks: List<DialogueBlock>): String = buildString {
            blocks.forEach { b ->
                when (b) {
                    is Plain -> append(b.text)
                    is Comment -> append("{").append(b.text).append("}")
                    is Override -> append(b.text)
                    is Drawing -> append(b.text)
                }
            }
        }
    }
}
