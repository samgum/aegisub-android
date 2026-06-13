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

    companion object {
        const val MAX_MICROS: Long = 10L * 60 * 60 * 1_000_000 // 10h
        val ZERO: SubTime = SubTime(0)

        fun ofMicros(v: Long): SubTime = SubTime(v.coerceIn(0, MAX_MICROS))
        fun ofMillis(v: Long): SubTime = ofMicros(v * 1_000)
        fun ofCentiseconds(v: Long): SubTime = ofMicros(v * 10_000)
    }
}
