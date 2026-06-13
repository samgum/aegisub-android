package io.github.samgum.aegisub.domain.audio

import kotlin.math.cos
import kotlin.math.sin

/**
 * 基-2 库利-图基 FFT（就地，长度须为 2 的幂）。
 * 输入：实部 [real] / 虚部 [imag] 同长数组；原地变换为频域。
 * 复刻标准 Cooley-Tukey 迭代实现，纯函数无副作用（仅改传入数组）。
 *
 * 用于音频频谱（STFT）分析：取窗口 PCM 作实部、虚部置零后变换，
 * 每个 bin 的幅度 √(re²+im²) 即该频率分量的能量。
 *
 * @author 伤感咩吖
 */
object Fft {

    /** 就地 FFT。[real] 与 [imag] 必须等长且长度为 2 的幂。 */
    fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n == imag.size && n > 0 && n and (n - 1) == 0) { "FFT 长度须为 2 的幂，当前 $n" }

        // 1) 位反转重排
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                swap(real, i, j)
                swap(imag, i, j)
            }
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
        }

        // 2) 蝶形运算
        var len = 2
        while (len <= n) {
            val ang = (-2.0 * Math.PI / len).toFloat()
            val wReal = cos(ang)
            val wImag = sin(ang)
            var i = 0
            while (i < n) {
                var curReal = 1f
                var curImag = 0f
                val half = len shr 1
                var k = 0
                while (k < half) {
                    val a = i + k
                    val b = a + half
                    val tReal = curReal * real[b] - curImag * imag[b]
                    val tImag = curReal * imag[b] + curImag * real[b]
                    real[b] = real[a] - tReal
                    imag[b] = imag[a] - tImag
                    real[a] = real[a] + tReal
                    imag[a] = imag[a] + tImag
                    val nextReal = curReal * wReal - curImag * wImag
                    curImag = curReal * wImag + curImag * wReal
                    curReal = nextReal
                    k++
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun swap(a: FloatArray, x: Int, y: Int) {
        val t = a[x]; a[x] = a[y]; a[y] = t
    }
}
