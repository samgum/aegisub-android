package io.github.samgum.aegisub.domain.model

/** 标准 RGBA 颜色，a∈[0,255]，255=完全不透明。ASS 边界会反转 alpha（00=不透明）。 */
@JvmInline
value class AssColor(val argb: Int) {
    constructor(r: Int, g: Int, b: Int, a: Int = 255) : this(
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    )
    val a: Int get() = (argb ushr 24) and 0xFF
    val r: Int get() = (argb ushr 16) and 0xFF
    val g: Int get() = (argb ushr 8) and 0xFF
    val b: Int get() = argb and 0xFF

    fun withAlpha(newAlpha: Int): AssColor = AssColor(r, g, b, newAlpha.coerceIn(0, 255))

    /** 序列化为 `&HAABBGGRR`（ASS Style 格式，alpha 反转：00=不透明）。 */
    fun toAssString(): String {
        val aa = (255 - a) and 0xFF
        return "&H%02X%02X%02X%02X".format(aa, b, g, r)
    }

    /** 序列化为 `&HBBGGRR&`（ASS 行内 \c / \1c..\4c 覆盖标签格式）。 */
    fun toBgrTagString(): String = "&H%02X%02X%02X&".format(b, g, r)

    /** 序列化为 `&HAA&`（ASS 行内 \alpha / \1a..\4a 覆盖标签格式，00=不透明，FF=全透）。 */
    fun toAlphaTagString(): String = "&H%02X&".format((255 - a) and 0xFF)

    companion object {
        val WHITE = AssColor(255, 255, 255)
        val BLACK = AssColor(0, 0, 0)
        val RED = AssColor(255, 0, 0)
        val YELLOW = AssColor(255, 255, 0)
        val TRANSPARENT = AssColor(0, 0, 0, 0)

        /** 解析 `&HAABBGGRR` 或 `&HBBGGRR`（无 alpha 视为不透明）。容错：去掉 &H 与结尾 &。 */
        fun parseAss(raw: String): AssColor = parseAssOrNull(raw) ?: WHITE

        /** 安全解析 ASS 颜色；遇到非法十六进制返回 null。 */
        fun parseAssOrNull(raw: String): AssColor? {
            val hex = raw.trim()
                .removePrefix("&H").removePrefix("&h")
                .removePrefix("H").removePrefix("h")
                .removeSuffix("&").removeSuffix("H").removeSuffix("h")
                .trim()
            if (hex.isEmpty() || hex.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) {
                return null
            }
            val padded = hex.padStart(8, '0').takeLast(8)
            val aa = padded.substring(0, 2).toIntOrNull(16) ?: return null
            val bb = padded.substring(2, 4).toIntOrNull(16) ?: return null
            val gg = padded.substring(4, 6).toIntOrNull(16) ?: return null
            val rr = padded.substring(6, 8).toIntOrNull(16) ?: return null
            val hasAlpha = hex.length > 6
            val opaqueA = if (hasAlpha) (255 - aa) and 0xFF else 255
            return AssColor(rr, gg, bb, opaqueA)
        }

        /** 解析 ASS alpha 标签值（如 `&H80&` 或 `80`），返回不透明度 [0..255]（255=完全不透明）。 */
        fun parseAssAlphaToOpacity(raw: String): Int? {
            val hex = raw.trim()
                .removePrefix("&H").removePrefix("&h")
                .removePrefix("H").removePrefix("h")
                .removeSuffix("&").removeSuffix("H").removeSuffix("h")
                .trim()
            if (hex.isEmpty()) return null
            val aa = hex.takeLast(2).toIntOrNull(16) ?: return null
            return (255 - aa).coerceIn(0, 255)
        }
    }
}
