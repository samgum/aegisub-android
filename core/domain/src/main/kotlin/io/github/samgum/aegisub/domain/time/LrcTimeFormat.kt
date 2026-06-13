package io.github.samgum.aegisub.domain.time

/**
 * LRC 时间标签格式枚举：精度（厘秒/毫秒）× 分隔符（./:）共四种。
 *
 * @author 伤感咩吖
 */
enum class LrcSeparator { DOT, COLON }
enum class LrcPrecision { CENTI, MILLI }

/** LRC 时间标签格式：精度（厘秒/毫秒）× 分隔符（./:）共四种。 */
data class LrcTimeFormat(val precision: LrcPrecision, val separator: LrcSeparator) {
    companion object {
        /** [mm:ss.xx] 厘秒 + 点号 */
        val XX = LrcTimeFormat(LrcPrecision.CENTI, LrcSeparator.DOT)
        /** [mm:ss.xxx] 毫秒 + 点号 */
        val XXX = LrcTimeFormat(LrcPrecision.MILLI, LrcSeparator.DOT)
        /** [mm:ss:xx] 厘秒 + 冒号 */
        val XX_COLON = LrcTimeFormat(LrcPrecision.CENTI, LrcSeparator.COLON)
        /** [mm:ss:xxx] 毫秒 + 冒号 */
        val XXX_COLON = LrcTimeFormat(LrcPrecision.MILLI, LrcSeparator.COLON)
    }
}
