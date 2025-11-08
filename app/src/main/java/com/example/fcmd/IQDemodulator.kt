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
     * Analyze mono signal and return tone analysis for each frequency
     */
    fun analyze(samples: FloatArray): List<ToneAnalysis> {
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
 * DSP processor that performs IQ demodulation in real-time (MONO channel)
 */
class IQDemodulatorDSP(
    private val sampleRate: Int,
    private val frequencies: List<Double>,
    private val callback: (List<ToneAnalysis>, VDIResult?) -> Unit,
    updateRateHz: Double = 30.0,  // Default 30 Hz update rate
    private val enableVDI: Boolean = true,  // Enable VDI calculation
    private val enableDepth: Boolean = true  // Enable depth estimation
) : DspProcessor {

    private val demodulator = IQDemodulator(sampleRate, frequencies)
    private val vdiCalculator = if (enableVDI) VDICalculator() else null
    private val depthEstimator = if (enableDepth) DepthEstimator() else null
    private val groundBalanceManager = GroundBalanceManager(frequencies)
    private var frameCount = 0
    private var updateInterval = calculateUpdateInterval(updateRateHz)

    // Performance monitoring
    private var callbackCount = 0
    private var lastRateCheckTime = System.currentTimeMillis()
    private var actualCallbackRate = 0.0
    private var targetUpdateRateHz = updateRateHz

    /**
     * Calculate frame interval based on desired update rate
     * Update rate limited by audio callback rate
     */
    private fun calculateUpdateInterval(targetHz: Double): Int {
        // Use actual measured callback rate if available, otherwise use conservative estimate
        // Actual rate depends on device buffer size: sampleRate / bufferSize
        // Example: 44100 / 1920 = 23.2 Hz (measured on some devices)
        val baseRate = if (actualCallbackRate > 0.0) actualCallbackRate else 20.0
        val interval = (baseRate / targetHz).toInt().coerceAtLeast(1)
        return interval
    }

    /**
     * Set the update rate for IQ analysis results
     * @param hz Update rate in Hertz (max depends on device audio callback rate, typically 20-43 Hz)
     */
    fun setUpdateRate(hz: Double) {
        targetUpdateRateHz = hz
        updateInterval = calculateUpdateInterval(hz)
    }

    override fun processStereo(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        sampleRate: Int
    ): Pair<FloatArray, FloatArray> {
        // Process only left channel (mono RX)
        return Pair(processMono(leftChannel, sampleRate), leftChannel)
    }

    override fun processMono(data: FloatArray, sampleRate: Int): FloatArray {
        // Measure actual callback rate every second
        callbackCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRateCheckTime >= 1000) {
            actualCallbackRate = callbackCount / ((currentTime - lastRateCheckTime) / 1000.0)
            android.util.Log.d("IQDemodulatorDSP",
                "Actual callback rate: ${String.format("%.1f", actualCallbackRate)} Hz, " +
                "Buffer size: ${data.size} samples, Latency: ${String.format("%.1f", data.size / sampleRate.toDouble() * 1000)} ms")
            callbackCount = 0
            lastRateCheckTime = currentTime

            // Recalculate update interval based on actual measured rate
            val newInterval = calculateUpdateInterval(targetUpdateRateHz)
            if (newInterval != updateInterval) {
                updateInterval = newInterval
                val actualUpdateRate = actualCallbackRate / updateInterval
                android.util.Log.d("IQDemodulatorDSP",
                    "Update interval adjusted: every $updateInterval frames = ${String.format("%.1f", actualUpdateRate)} Hz (target: ${String.format("%.1f", targetUpdateRateHz)} Hz)")
            }
        }

        // Perform IQ demodulation on mono channel
        val analysis = demodulator.analyze(data)

        // Apply ground balance
        val balanced = groundBalanceManager.applyGroundBalance(analysis)

        // Send results to callback periodically
        frameCount++
        if (frameCount >= updateInterval) {
            // Calculate VDI if enabled and we have multiple frequencies
            var vdiResult = if (vdiCalculator != null && balanced.size > 1) {
                vdiCalculator.calculateVDI(balanced)
            } else {
                null
            }

            // Add depth estimation if enabled
            if (depthEstimator != null && vdiResult != null) {
                val depthEstimate = depthEstimator.estimateDepth(balanced, vdiResult)
                // Create new VDI result with depth estimate included
                vdiResult = vdiResult.copy(depthEstimate = depthEstimate)
            }

            callback(balanced, vdiResult)
            frameCount = 0
        }

        return data
    }

    override fun reset() {
        demodulator.reset()
        frameCount = 0
        callbackCount = 0
        lastRateCheckTime = System.currentTimeMillis()
        actualCallbackRate = 0.0
    }

    /**
     * Get the actual measured callback rate
     */
    fun getActualCallbackRate(): Double = actualCallbackRate

    /**
     * Get ground balance manager for UI control
     */
    fun getGroundBalanceManager(): GroundBalanceManager = groundBalanceManager
}
