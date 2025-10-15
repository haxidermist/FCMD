package com.example.fcmd

import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates multiple simultaneous tones for audio analysis
 */
class MultiToneGenerator(
    private val sampleRate: Int,
    frequencies: List<Double>
) {
    private val toneGenerators = frequencies.map { freq ->
        ToneOscillator(freq, sampleRate)
    }

    val frequencies: List<Double> = frequencies.toList()

    /**
     * Generate a buffer of samples containing all tones summed together
     */
    fun generateSamples(numSamples: Int): FloatArray {
        val output = FloatArray(numSamples)
        val numTones = toneGenerators.size

        if (numTones == 0) return output

        // Generate and sum all tones
        for (generator in toneGenerators) {
            val toneSamples = generator.generate(numSamples)
            for (i in output.indices) {
                output[i] += toneSamples[i]
            }
        }

        // Normalize to prevent clipping
        val scaleFactor = 1.0f / numTones
        for (i in output.indices) {
            output[i] *= scaleFactor
        }

        return output
    }

    /**
     * Reset all oscillator phases
     */
    fun reset() {
        toneGenerators.forEach { it.reset() }
    }

    companion object {
        /**
         * Create a multi-tone generator with evenly spaced frequencies
         */
        fun createLinearSpaced(
            startFreq: Double,
            endFreq: Double,
            numTones: Int,
            sampleRate: Int
        ): MultiToneGenerator {
            val step = (endFreq - startFreq) / (numTones - 1)
            val frequencies = (0 until numTones).map { i ->
                startFreq + (i * step)
            }
            return MultiToneGenerator(sampleRate, frequencies)
        }

        /**
         * Create a multi-tone generator with logarithmically spaced frequencies
         */
        fun createLogSpaced(
            startFreq: Double,
            endFreq: Double,
            numTones: Int,
            sampleRate: Int
        ): MultiToneGenerator {
            val logStart = kotlin.math.ln(startFreq)
            val logEnd = kotlin.math.ln(endFreq)
            val step = (logEnd - logStart) / (numTones - 1)

            val frequencies = (0 until numTones).map { i ->
                kotlin.math.exp(logStart + (i * step))
            }
            return MultiToneGenerator(sampleRate, frequencies)
        }
    }
}

/**
 * Single tone oscillator
 */
private class ToneOscillator(
    private val frequency: Double,
    private val sampleRate: Int
) {
    private var phase = 0.0
    private val phaseIncrement = 2.0 * PI * frequency / sampleRate

    fun generate(numSamples: Int): FloatArray {
        val output = FloatArray(numSamples)

        for (i in output.indices) {
            output[i] = sin(phase).toFloat()
            phase += phaseIncrement

            // Keep phase in range to prevent overflow
            if (phase >= 2.0 * PI) {
                phase -= 2.0 * PI
            }
        }

        return output
    }

    fun reset() {
        phase = 0.0
    }
}
