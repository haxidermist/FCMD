package com.example.fcmd

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fast Fourier Transform implementation
 * Radix-2 Cooley-Tukey algorithm
 */
class FFT(private val fftSize: Int) {

    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) {
            "FFT size must be a power of 2"
        }
    }

    private val realPart = DoubleArray(fftSize)
    private val imagPart = DoubleArray(fftSize)

    /**
     * Compute FFT of input signal
     * @param input Input signal (will be zero-padded or truncated to fftSize)
     * @return Magnitude spectrum
     */
    fun computeMagnitude(input: FloatArray): FloatArray {
        // Copy input to real part, zero-pad if needed
        for (i in 0 until fftSize) {
            realPart[i] = if (i < input.size) input[i].toDouble() else 0.0
            imagPart[i] = 0.0
        }

        // Apply Hann window to reduce spectral leakage
        applyHannWindow()

        // Perform FFT
        fft(realPart, imagPart)

        // Compute magnitude spectrum (only first half, positive frequencies)
        val magnitude = FloatArray(fftSize / 2)
        for (i in 0 until fftSize / 2) {
            magnitude[i] = sqrt(realPart[i] * realPart[i] + imagPart[i] * imagPart[i]).toFloat()
        }

        return magnitude
    }

    /**
     * Apply Hann window function
     */
    private fun applyHannWindow() {
        for (i in 0 until fftSize) {
            val window = 0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
            realPart[i] *= window
        }
    }

    /**
     * Cooley-Tukey FFT algorithm (in-place)
     */
    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                // Swap
                var temp = real[i]
                real[i] = real[j]
                real[j] = temp

                temp = imag[i]
                imag[i] = imag[j]
                imag[j] = temp
            }

            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        // Cooley-Tukey decimation-in-time radix-2 FFT
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wlenReal = cos(angle)
            val wlenImag = sin(angle)

            var i = 0
            while (i < n) {
                var wReal = 1.0
                var wImag = 0.0

                for (k in 0 until len / 2) {
                    val evenIdx = i + k
                    val oddIdx = i + k + len / 2

                    val tReal = wReal * real[oddIdx] - wImag * imag[oddIdx]
                    val tImag = wReal * imag[oddIdx] + wImag * real[oddIdx]

                    real[oddIdx] = real[evenIdx] - tReal
                    imag[oddIdx] = imag[evenIdx] - tImag
                    real[evenIdx] += tReal
                    imag[evenIdx] += tImag

                    val tempW = wReal
                    wReal = tempW * wlenReal - wImag * wlenImag
                    wImag = tempW * wlenImag + wImag * wlenReal
                }

                i += len
            }

            len *= 2
        }
    }

    /**
     * Get frequency bins in Hz
     */
    fun getFrequencyBins(sampleRate: Int): FloatArray {
        val bins = FloatArray(fftSize / 2)
        val binWidth = sampleRate.toFloat() / fftSize
        for (i in bins.indices) {
            bins[i] = i * binWidth
        }
        return bins
    }
}
