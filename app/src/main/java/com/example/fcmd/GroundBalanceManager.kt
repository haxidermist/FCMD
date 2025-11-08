package com.example.fcmd

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ground balance modes
 */
enum class GroundBalanceMode {
    OFF,            // No ground balance
    MANUAL,         // Manual "pump and set" ground balance
    AUTO_TRACKING,  // Automatic ground tracking
    MANUAL_TRACKING // Manual preset with auto-tracking
}

/**
 * Ground balance data for a single frequency
 */
data class GroundBalancePoint(
    val frequency: Double,
    val inPhase: Double,
    val quadrature: Double,
    val amplitude: Double,
    val phase: Double
)

/**
 * Ground Balance Manager
 * Implements manual, auto-tracking, and offset-based ground balance
 */
class GroundBalanceManager(
    private val frequencies: List<Double>
) {
    private var mode = GroundBalanceMode.OFF

    // Manual ground balance: captured soil baseline
    private var manualBaseline: List<GroundBalancePoint>? = null
    private var isCapturing = false
    private var captureSamples = mutableListOf<List<ToneAnalysis>>()
    private val captureCount = 10  // Average 10 pump samples

    // Auto-tracking: slow-moving baseline that follows ground
    private var trackingBaseline: List<GroundBalancePoint>? = null
    private val trackingAlpha = 0.0005  // Very slow tracking (0.05%)

    // GB Offset: user adjustment (-50 to +50)
    private var gbOffset = 0  // 0 = neutral

    // Freeze tracking when strong target detected
    private var trackingFrozen = false
    private val freezeThreshold = 0.3  // Amplitude threshold to freeze tracking

    /**
     * Set ground balance mode
     */
    fun setMode(newMode: GroundBalanceMode) {
        mode = newMode

        // Reset tracking baseline when switching to auto modes
        if (newMode == GroundBalanceMode.AUTO_TRACKING) {
            trackingBaseline = null
        }
    }

    fun getMode(): GroundBalanceMode = mode

    /**
     * Set GB offset (-50 to +50)
     * Adjusts the phase angle of the ground balance point
     */
    fun setOffset(offset: Int) {
        gbOffset = offset.coerceIn(-50, 50)
    }

    fun getOffset(): Int = gbOffset

    /**
     * Start manual ground balance capture
     * User should pump coil over ground during capture
     */
    fun startManualCapture() {
        isCapturing = true
        captureSamples.clear()
    }

    /**
     * Stop manual capture and calculate baseline
     */
    fun stopManualCapture() {
        if (!isCapturing) return

        isCapturing = false

        if (captureSamples.isEmpty()) {
            return
        }

        // Average all captured samples
        manualBaseline = averageCapturedSamples(captureSamples)

        // If in manual+tracking mode, initialize tracking baseline from manual
        if (mode == GroundBalanceMode.MANUAL_TRACKING) {
            trackingBaseline = manualBaseline
        }

        captureSamples.clear()
    }

    fun isCapturing(): Boolean = isCapturing

    /**
     * Get ground balance status string
     */
    fun getStatusString(): String {
        val offsetStr = if (gbOffset != 0) {
            val sign = if (gbOffset > 0) "+" else ""
            " ($sign$gbOffset)"
        } else {
            ""
        }

        return when (mode) {
            GroundBalanceMode.OFF -> "GB: OFF"
            GroundBalanceMode.MANUAL -> {
                if (manualBaseline != null) {
                    "GB: MANUAL$offsetStr"
                } else {
                    "GB: MANUAL (Not Set)"
                }
            }
            GroundBalanceMode.AUTO_TRACKING -> {
                val frozen = if (trackingFrozen) " [FROZEN]" else ""
                if (trackingBaseline != null) {
                    "GB: AUTO$frozen$offsetStr"
                } else {
                    "GB: AUTO (Learning...)"
                }
            }
            GroundBalanceMode.MANUAL_TRACKING -> {
                val frozen = if (trackingFrozen) " [FROZEN]" else ""
                if (manualBaseline != null) {
                    "GB: MAN+TRK$frozen$offsetStr"
                } else {
                    "GB: MAN+TRK (Not Set)"
                }
            }
        }
    }

    /**
     * Apply ground balance to mono IQ analysis
     * Returns ground-balanced analysis
     */
    fun applyGroundBalance(analysis: List<ToneAnalysis>): List<ToneAnalysis> {

        // If capturing, store samples
        if (isCapturing) {
            captureSamples.add(analysis)
        }

        // No ground balance applied
        if (mode == GroundBalanceMode.OFF) {
            return analysis
        }

        // Update tracking baseline if in auto mode
        if (mode == GroundBalanceMode.AUTO_TRACKING || mode == GroundBalanceMode.MANUAL_TRACKING) {
            updateTrackingBaseline(analysis)
        }

        // Get active baseline
        val baseline = when (mode) {
            GroundBalanceMode.MANUAL -> manualBaseline
            GroundBalanceMode.AUTO_TRACKING -> trackingBaseline
            GroundBalanceMode.MANUAL_TRACKING -> trackingBaseline ?: manualBaseline
            else -> null
        } ?: return analysis

        // Apply baseline subtraction with offset
        return subtractBaseline(analysis, baseline, gbOffset)
    }

    /**
     * Update auto-tracking baseline
     * Uses slow IIR filter to follow ground changes
     * Freezes when strong target detected
     */
    private fun updateTrackingBaseline(analysis: List<ToneAnalysis>) {
        // Check if we should freeze tracking (strong target present)
        val maxAmplitude = analysis.maxOfOrNull { it.amplitude } ?: 0.0
        trackingFrozen = maxAmplitude > freezeThreshold

        // Don't update baseline when frozen
        if (trackingFrozen) {
            return
        }

        if (trackingBaseline == null) {
            // Initialize tracking baseline
            trackingBaseline = analysis.map { tone ->
                GroundBalancePoint(
                    frequency = tone.frequency,
                    inPhase = tone.inPhase,
                    quadrature = tone.quadrature,
                    amplitude = tone.amplitude,
                    phase = tone.phase
                )
            }
        } else {
            // Update with slow IIR filter: baseline = alpha*new + (1-alpha)*baseline
            trackingBaseline = trackingBaseline!!.mapIndexed { i, baseline ->
                val current = analysis.getOrNull(i) ?: return@mapIndexed baseline

                GroundBalancePoint(
                    frequency = baseline.frequency,
                    inPhase = trackingAlpha * current.inPhase + (1.0 - trackingAlpha) * baseline.inPhase,
                    quadrature = trackingAlpha * current.quadrature + (1.0 - trackingAlpha) * baseline.quadrature,
                    amplitude = trackingAlpha * current.amplitude + (1.0 - trackingAlpha) * baseline.amplitude,
                    phase = trackingAlpha * current.phase + (1.0 - trackingAlpha) * baseline.phase
                )
            }
        }
    }

    /**
     * Subtract baseline from analysis with offset adjustment
     */
    private fun subtractBaseline(
        analysis: List<ToneAnalysis>,
        baseline: List<GroundBalancePoint>,
        offset: Int
    ): List<ToneAnalysis> {
        return analysis.mapIndexed { i, tone ->
            val basePoint = baseline.getOrNull(i) ?: return@mapIndexed tone

            // Apply offset as phase rotation
            // Offset of +/- 50 = +/- 45 degrees phase shift
            val offsetRadians = (offset / 50.0) * (Math.PI / 4.0)
            val cosOffset = kotlin.math.cos(offsetRadians)
            val sinOffset = kotlin.math.sin(offsetRadians)

            // Rotate baseline vector by offset
            val rotatedI = basePoint.inPhase * cosOffset - basePoint.quadrature * sinOffset
            val rotatedQ = basePoint.inPhase * sinOffset + basePoint.quadrature * cosOffset

            // Subtract rotated baseline from current signal
            val newI = tone.inPhase - rotatedI
            val newQ = tone.quadrature - rotatedQ

            // Calculate new amplitude and phase
            val newAmplitude = sqrt(newI * newI + newQ * newQ)
            val newPhase = kotlin.math.atan2(newQ, newI)

            ToneAnalysis(
                frequency = tone.frequency,
                amplitude = newAmplitude,
                phase = newPhase,
                inPhase = newI,
                quadrature = newQ
            )
        }
    }

    /**
     * Average captured samples for manual ground balance
     */
    private fun averageCapturedSamples(
        samples: List<List<ToneAnalysis>>
    ): List<GroundBalancePoint> {
        if (samples.isEmpty()) return emptyList()

        val freqCount = samples.first().size

        return (0 until freqCount).map { freqIndex ->
            var sumI = 0.0
            var sumQ = 0.0
            var count = 0

            samples.forEach { analysis ->
                val tone = analysis.getOrNull(freqIndex)
                if (tone != null) {
                    sumI += tone.inPhase
                    sumQ += tone.quadrature
                    count++
                }
            }

            val avgI = sumI / count
            val avgQ = sumQ / count
            val avgAmp = sqrt(avgI * avgI + avgQ * avgQ)
            val avgPhase = kotlin.math.atan2(avgQ, avgI)

            GroundBalancePoint(
                frequency = frequencies[freqIndex],
                inPhase = avgI,
                quadrature = avgQ,
                amplitude = avgAmp,
                phase = avgPhase
            )
        }
    }

    /**
     * Reset ground balance
     */
    fun reset() {
        manualBaseline = null
        trackingBaseline = null
        captureSamples.clear()
        isCapturing = false
        trackingFrozen = false
    }
}
