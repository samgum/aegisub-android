package io.github.samgum.aegisub.domain.model

/** ASS/SSA 样式（V4+ 字段）。alignment 以 \an 1-9 记。
 *  — 伤感咩吖
 */
data class AssStyle(
    val name: String = "Default",
    val font: String = "Arial",
    val fontSize: Double = 48.0,
    val primary: AssColor = AssColor.WHITE,
    val secondary: AssColor = AssColor.RED,
    val outline: AssColor = AssColor.BLACK,
    val shadow: AssColor = AssColor.BLACK,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeout: Boolean = false,
    val scaleX: Double = 100.0,
    val scaleY: Double = 100.0,
    val spacing: Double = 0.0,
    val angle: Double = 0.0,
    val borderStyle: Int = 1,
    val outlineWidth: Double = 2.0,
    val shadowWidth: Double = 2.0,
    val alignment: Int = 2,
    val margins: Margins = Margins.ZERO,
    val encoding: Int = 1,
) {
    fun toStyleLine(): String = "Style: " + listOf(
        name, font, fontSize.formatNum(),
        primary.toAssString(), secondary.toAssString(), outline.toAssString(), shadow.toAssString(),
        bold.toInt(), italic.toInt(), underline.toInt(), strikeout.toInt(),
        scaleX.formatNum(), scaleY.formatNum(), spacing.formatNum(), angle.formatNum(),
        borderStyle, outlineWidth.formatNum(), shadowWidth.formatNum(), alignment,
        margins.left, margins.right, margins.vertical, encoding,
    ).joinToString(",")

    companion object {
        private fun Boolean.toInt() = if (this) -1 else 0
        private fun Double.formatNum() =
            if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()

        /** 解析 `Style: ...` 行（参数可含或不带 "Style:" 前缀）。 */
        fun parse(line: String): AssStyle {
            val body = line.trim().let {
                if (it.startsWith("Style:", ignoreCase = true)) it.substringAfter(":").trim() else it
            }
            val f = body.split(",").map { it.trim() }
            require(f.size >= 23) { "Invalid Style line, expected >=23 fields, got ${f.size}" }
            fun b(i: Int) = f[i] == "-1" || f[i].equals("true", true)
            fun d(i: Int) = f[i].toDoubleOrNull() ?: 0.0
            fun n(i: Int) = f[i].toIntOrNull() ?: 0
            return AssStyle(
                name = f[0], font = f[1], fontSize = d(2),
                primary = AssColor.parseAss(f[3]), secondary = AssColor.parseAss(f[4]),
                outline = AssColor.parseAss(f[5]), shadow = AssColor.parseAss(f[6]),
                bold = b(7), italic = b(8), underline = b(9), strikeout = b(10),
                scaleX = d(11), scaleY = d(12), spacing = d(13), angle = d(14),
                borderStyle = n(15), outlineWidth = d(16), shadowWidth = d(17), alignment = n(18),
                margins = Margins(n(19), n(20), n(21)), encoding = n(22),
            )
        }

        /** \an(1-9) → SSA。对齐 Aegisub 源码 AssStyle::AssToSsa（含 default→2）。 */
        fun assToSsa(assAlign: Int): Int = when (assAlign) {
            1 -> 1; 2 -> 2; 3 -> 3; 4 -> 9; 5 -> 10; 6 -> 11; 7 -> 5; 8 -> 6; 9 -> 7
            else -> 2
        }
        /** SSA → \an(1-9)。对齐 Aegisub 源码 AssStyle::SsaToAss。SSA 合法值为 1,2,3,5,6,7,9,10,11。 */
        fun ssaToAss(ssaAlign: Int): Int = when (ssaAlign) {
            1 -> 1; 2 -> 2; 3 -> 3; 5 -> 7; 6 -> 8; 7 -> 9; 9 -> 4; 10 -> 5; 11 -> 6
            else -> 2
        }
    }
}
