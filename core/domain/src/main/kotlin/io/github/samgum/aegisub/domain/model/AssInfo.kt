package io.github.samgum.aegisub.domain.model

/** [Script Info] 段的一条键值对。 */
data class AssInfo(val key: String, val value: String) {
    fun toLine(): String = "$key: $value"

    companion object {
        /** 解析 `Key: Value`；非键值行（段头、无冒号）返回 null。 */
        fun parse(line: String): AssInfo? {
            val idx = line.indexOf(':')
            if (idx <= 0) return null
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim()
            if (k.isEmpty() || k.startsWith("[")) return null
            return AssInfo(k, v)
        }
    }
}
