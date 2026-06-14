package com.example.rawaudiorecorder

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Fft {

    /** In-place radix-2 FFT. real.size must be a power of 2. imag starts all zeros. */
    fun transform(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n <= 1) return

        // Bit-reversal reordering
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = real[i]; real[i] = real[j]; real[j] = t
                t = imag[i]; imag[i] = imag[j]; imag[j] = t
            }
        }

        // Butterfly combine
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wReal = cos(ang).toFloat()
            val wImag = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curReal = 1f
                var curImag = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = i + k + len / 2
                    val oddReal = real[b] * curReal - imag[b] * curImag
                    val oddImag = real[b] * curImag + imag[b] * curReal
                    real[b] = real[a] - oddReal
                    imag[b] = imag[a] - oddImag
                    real[a] += oddReal
                    imag[a] += oddImag
                    val nextReal = curReal * wReal - curImag * wImag
                    curImag = curReal * wImag + curImag * wReal
                    curReal = nextReal
                }
                i += len
            }
            len = len shl 1
        }
    }
}