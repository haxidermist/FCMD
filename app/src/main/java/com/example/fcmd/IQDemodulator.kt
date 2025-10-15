package com.example.fcmd

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Result of IQ demodulation for a single tone
 */
data class ToneAnalysis(
    val frequency: Double,
    val amplitude: Double,
    val phase: Double,  // in radians
    val inPhase: Double,  // I component
    val quadrature: Double  // Q component
) {
    /**
     * Get phase in degrees
     */
    fun phaseDegrees(): Double = phase * 180.0 / PI

    /**
     * Get amplitude in dB relative to full scale
     */
    fun amplitudeDB(): Double = 20.0 * kotlin.math.log10(amplitude.coerceAtLeast(1e-10))
}

/**
 * IQ Demodulator for extracting amplitude and phase of specific frequencies
 */
class IQDemodulator(
    private val sampleRate: Int,
    private val targetFrequencies: List<Double>
) {
    private val demodulators = targetFrequencies.map { freq ->
        SingleToneDemodulator(freq, sampleRate)
    }

    /**
     * Analyze stereo signal and return tone analysis for each frequency
     * @param leftChannel Left channel samples
     * @param rightChannel Right channel samples
     * @return Map of frequency to tone analysis for each channel
     */
    fun analyzeStereo(
        leftChannel: FloatArray,
        rightChannel: FloatArray
    ): Pair<List<ToneAnalysis>, List<ToneAnalysis>> {
        val leftResults = analyzeMono(leftChannel)
        val rightResults = analyzeMono(rightChannel)
        return Pair(leftResults, rightResults)
    }

    /**
     * Analyze mono signal and return tone analysis for each frequency
     */
    fun analyzeMono(samples: FloatArray): List<ToneAnalysis> {
        return demodulators.map { demod ->
            demod.analyze(samples)
        }
    }

    /**
     * Reset all demodulator states
     */
    fun reset() {
        demodulators.forEach { it.reset() }
    }

    fun getFrequencies(): List<Double> = targetFrequencies
}

/**
 * Single tone IQ demodulator using quadrature mixing
 * Optimized for maximum update rate using single-pole IIR filter
 */
private class SingleToneDemodulator(
    private val frequency: Double,
    private val sampleRate: Int
) {
    private var phase = 0.0
    private val phaseIncrement = 2.0 * PI * frequency / sampleRate

    // Single-pole IIR filter (much faster than moving average)
    // Alpha determines filter cutoff: smaller = more filtering, slower response
    private val filterAlpha = 0.01  // ~10 Hz cutoff for fast tracking
    private var iFiltered = 0.0
    private var qFiltered = 0.0

    fun analyze(samples: FloatArray): ToneAnalysis {
        // Process all samples with IIR low-pass filter
        for (sample in samples) {
            // Quadrature mixing
            val i = sample * cos(phase)
            val q = -sample * sin(phase)

            // Single-pole IIR low-pass filter: y[n] = alpha*x[n] + (1-alpha)*y[n-1]
            iFiltered = filterAlpha * i + (1.0 - filterAlpha) * iFiltered
            qFiltered = filterAlpha * q + (1.0 - filterAlpha) * qFiltered

            phase += phaseIncrement
            if (phase >= 2.0 * PI) {
                phase -= 2.0 * PI
            }
        }

        // Calculate amplitude and phase from filtered I/Q
        val amplitude = sqrt(iFiltered * iFiltered + qFiltered * qFiltered) * 2.0  // *2 due to mixing
        val phaseAngle = atan2(qFiltered, iFiltered)

        return ToneAnalysis(
            frequency = frequency,
            amplitude = amplitude,
            phase = phaseAngle,
            inPhase = iFiltered,
            quadrature = qFiltered
        )
    }

    fun reset() {
        phase = 0.0
        iFiltered = 0.0
        qFiltered = 0.0
    }
}

/**
 * Fixed-size circular buffer for filtering
 */
private class FixedSizeBuffer(private val size: Int) {
    private val buffer = DoubleArray(size)
    private var index = 0
    private var count = 0

    fun add(value: Double) {
        buffer[index] = value
        index = (index + 1) % size
        if (count < size) count++
    }

    fun average(): Double {
        if (count == 0) return 0.0
        return buffer.take(count).average()
    }

    fun isFull(): Boolean = count >= size

    fun clear() {
        count = 0
        index = 0
    }
}

/**
 * DSP processor that performs IQ demodulation in real-time
 */
class IQDemodulatorDSP(
    private val sampleRate: Int,
    private val frequencies: List<Double>,
    private val callback: (List<ToneAnalysis>, List<ToneAnalysis>) -> Unit,
    updateRateHz: Double = 30.0  // Default 30 Hz update rate
) : DspProcessor {

    private val demodulator = IQDemodulator(sampleRate, frequencies)
    private var frameCount = 0
    private var updateInterval = calculateUpdateInterval(updateRateHz)

    /**
     * Calculate frame interval based on desired update rate
     * For 48kHz with typical buffer of ~1024 samples: ~47 frames/sec
     * Update rate limited by audio callback rate
     */
    private fun calculateUpdateInterval(targetHz: Double): Int {
        // Estimate frames per second based on typical buffer size
        // At 48kHz with 2x multiplier, buffer is typically ~2048 bytes = 1024 samples
        // This gives ~47 callbacks per second
        val estimatedCallbackRate = 47.0
        val interval = (estimatedCallbackRate / targetHz).toInt().coerceAtLeast(1)
        return interval
    }

    /**
     * Set the update rate for IQ analysis results
     * @param hz Update rate in Hertz (max ~47 Hz for typical configuration)
     */
    fun setUpdateRate(hz: Double) {
        updateInterval = calculateUpdateInterval(hz)
    }

    override fun processStereo(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        sampleRate: Int
    ): Pair<FloatArray, FloatArray> {
        // Perform IQ demodulation
        val (leftAnalysis, rightAnalysis) = demodulator.analyzeStereo(leftChannel, rightChannel)

        // Send results to callback periodically
        frameCount++
        if (frameCount >= updateInterval) {
            callback(leftAnalysis, rightAnalysis)
            frameCount = 0
        }

        // Pass through the original signal
        return Pair(leftChannel, rightChannel)
    }

    override fun processMono(data: FloatArray, sampleRate: Int): FloatArray {
        val analysis = demodulator.analyzeMono(data)

        frameCount++
        if (frameCount >= updateInterval) {
            callback(analysis, emptyList())
            frameCount = 0
        }

        return data
    }

    override fun reset() {
        demodulator.reset()
        frameCount = 0
    }
}
