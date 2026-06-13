package io.github.samgum.aegisub.domain.text

/**
 * 查找替换纯函数：支持普通字符串与正则，可选忽略大小写。
 * 无效正则容错（返回原文 / 计 0），不抛异常。
 *
 * @author 伤感咩吖
 */
object FindReplace {

    /** 把 text 中所有匹配 query 的片段替换为 replacement。 */
    fun replaceAll(
        text: String,
        query: String,
        replacement: String,
        useRegex: Boolean,
        ignoreCase: Boolean = false,
    ): String {
        if (query.isEmpty()) return text
        return if (useRegex) {
            val opts = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            try {
                Regex(query, opts).replace(text, replacement)
            } catch (e: Exception) {
                text
            }
        } else {
            text.replace(query, replacement, ignoreCase)
        }
    }

    /** 统计 text 中匹配 query 的次数。 */
    fun count(text: String, query: String, useRegex: Boolean, ignoreCase: Boolean = false): Int {
        if (query.isEmpty()) return 0
        return if (useRegex) {
            val opts = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            try {
                Regex(query, opts).findAll(text).count()
            } catch (e: Exception) {
                0
            }
        } else {
            text.split(query, ignoreCase = ignoreCase).size - 1
        }
    }
}
