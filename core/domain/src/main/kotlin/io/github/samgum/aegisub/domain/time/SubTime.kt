package io.github.samgum.aegisub.domain.time

/**
 * 时间值类，内部以微秒（10^-6s）存储，高于 ASS 厘秒与 LRC/SRT 毫秒，保证往返零精度损失。
 * 范围 [0, 10 小时]（对齐 Aegisub）。格式化/解析方法见后续扩展。
 *
 * @author 伤感咩吖
 */
@JvmInline
value class SubTime private constructor(val micros: Long) : Comparable<SubTime> {

    val millis: Long get() = micros / 1_000

    operator fun plus(other: SubTime): SubTime = ofMicros(micros + other.micros)
    operator fun minus(other: SubTime): SubTime = ofMicros(micros - other.micros)
    override fun compareTo(other: SubTime): Int = micros.compareTo(other.micros)
    override fun toString(): String = "SubTime(${micros}µs)"

    /** ASS 文本：H:MM:SS.cc（默认厘秒）或 H:MM:SS.mmm（msPrecision）。 */
    fun toAssString(msPrecision: Boolean): String {
        if (msPrecision) {
            val total = millis
            val h = total / 3_600_000
            val m = (total % 3_600_000) / 60_000
            val s = (total % 60_000) / 1_000
            val mm = total % 1_000
            return "%d:%02d:%02d.%03d".format(h, m, s, mm)
        }
        val cs = (micros + 5_000) / 10_000 // 厘秒，5ms 半向上取整（对齐 Aegisub）
        val h = cs / 360_000
        val m = (cs % 360_000) / 6_000
        val s = (cs % 6_000) / 100
        val cc = cs % 100
        return "%d:%02d:%02d.%02d".format(h, m, s, cc)
    }

    companion object {
        const val MAX_MICROS: Long = 10L * 60 * 60 * 1_000_000 // 10h
        val ZERO: SubTime = SubTime(0)

        fun ofMicros(v: Long): SubTime = SubTime(v.coerceIn(0, MAX_MICROS))
        fun ofMillis(v: Long): SubTime = ofMicros(v * 1_000)
        fun ofCentiseconds(v: Long): SubTime = ofMicros(v * 10_000)

        /** Aegisub 风格容错解析：吃 H:MM:SS.cc / H:MM:SS.mmm / MM:SS.cc，`.`/`,` 皆可。 */
        fun parseAss(text: String): SubTime = ofMicros(parseFlexibleMs(text) * 1_000)

        /** 移植自 libaegisub Time::Time(string_view)：返回毫秒。 */
        private fun parseFlexibleMs(text: String): Long {
            var time = 0L
            var current = 0
            var afterDecimal = -1
            for (c in text) {
                when {
                    c == ':' -> { time = time * 60 + current; current = 0 }
                    c == '.' || c == ',' -> { time = (time * 60 + current) * 1000; current = 0; afterDecimal = 100 }
                    c !in '0'..'9' -> { /* 跳过非数字 */ }
                    afterDecimal < 0 -> { current = current * 10 + (c - '0') }
                    else -> { time += (c - '0').toLong() * afterDecimal; afterDecimal /= 10 }
                }
            }
            if (afterDecimal < 0) time = (time * 60 + current) * 1000
            return time
        }
    }
}
