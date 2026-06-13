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

    /** 序列化为 `&HAABBGGRR`（ASS alpha 反转：00=不透明）。 */
    fun toAssString(): String {
        val aa = (255 - a) and 0xFF
        return "&H%02X%02X%02X%02X".format(aa, b, g, r)
    }

    companion object {
        val WHITE = AssColor(255, 255, 255)
        val BLACK = AssColor(0, 0, 0)
        val RED = AssColor(255, 0, 0)
        val TRANSPARENT = AssColor(0, 0, 0, 0)

        /** 解析 `&HAABBGGRR` 或 `&HBBGGRR`（无 alpha 视为不透明）。容错：去掉 &H 与结尾 &。 */
        fun parseAss(raw: String): AssColor {
            val hex = raw.trim()
                .removePrefix("&H").removePrefix("&h")
                .removeSuffix("&").removeSuffix("H").removeSuffix("h")
                .trim()
            val padded = hex.padStart(8, '0').take(8)
            val aa = padded.substring(0, 2).toInt(16)
            val bb = padded.substring(2, 4).toInt(16)
            val gg = padded.substring(4, 6).toInt(16)
            val rr = padded.substring(6, 8).toInt(16)
            val hasAlpha = hex.length > 6
            // 短形式（<=6 位）视为完全不透明
            val opaqueA = if (hasAlpha) (255 - aa) and 0xFF else 255
            return AssColor(rr, gg, bb, opaqueA)
        }
    }
}
