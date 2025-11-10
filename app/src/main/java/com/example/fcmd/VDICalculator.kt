package com.example.fcmd

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * VDI (Visual Discrimination Indicator) result
 */
data class VDIResult(
    val vdi: Int,                    // VDI value (0-99)
    val confidence: Double,          // Confidence score (0.0-1.0)
    val targetType: TargetType,      // Classified target type
    val phaseSlope: Double,          // Phase slope across frequencies
    val conductivityIndex: Double,   // Relative conductivity measure
    val depthEstimate: DepthEstimate? = null  // Optional depth estimation
)

/**
 * Target classification types
 */
enum class TargetType {
    FERROUS,        // Iron, steel (low VDI, steep negative phase slope)
    LOW_CONDUCTOR,  // Foil, small aluminum (low-mid VDI)
    MID_CONDUCTOR,  // Pull tabs, brass, zinc (mid VDI)
    HIGH_CONDUCTOR, // Copper, silver (high VDI)
    GOLD_RANGE,     // Gold jewelry range (special mid-high VDI)
    UNKNOWN         // Cannot classify
}

/**
 * VDI Calculator for metal detection discrimination
 * Uses multi-frequency IQ analysis to calculate target discrimination values
 */
class VDICalculator {

    companion object {
        // VDI scale ranges (0-99)
        private const val VDI_MIN = 0
        private const val VDI_MAX = 99

        // Target type VDI ranges
        private const val FERROUS_MAX = 30
        private const val LOW_CONDUCTOR_MAX = 45
        private const val MID_CONDUCTOR_MAX = 65
        private const val GOLD_RANGE_MIN = 50
        private const val GOLD_RANGE_MAX = 70
        private const val HIGH_CONDUCTOR_MIN = 70
    }

    /**
     * Calculate VDI from mono IQ analysis
     */
    fun calculateVDI(analysis: List<ToneAnalysis>): VDIResult {
        if (analysis.isEmpty()) {
            return VDIResult(50, 0.0, TargetType.UNKNOWN, 0.0, 0.0)
        }

        // Calculate various discrimination parameters
        val phaseSlope = calculatePhaseSlope(analysis)
        val avgAmplitude = analysis.map { it.amplitude }.average()
        val conductivityIndex = calculateConductivityIndex(analysis)
        val phaseConsistency = calculatePhaseConsistency(analysis)

        // Debug logging for troubleshooting
        android.util.Log.d("VDICalculator", String.format(
            "VDI Debug: avgAmp=%.4f, phaseSlope=%.2f deg/kHz, conductivity=%.2f, consistency=%.3f",
            avgAmplitude, phaseSlope, conductivityIndex, phaseConsistency
        ))

        // Log phase values for first, middle, and last frequency
        if (analysis.size >= 3) {
            val first = analysis.first()
            val mid = analysis[analysis.size / 2]
            val last = analysis.last()
            android.util.Log.d("VDICalculator", String.format(
                "Phase: %.0fHz=%.1f°, %.0fHz=%.1f°, %.0fHz=%.1f°",
                first.frequency, first.phaseDegrees(),
                mid.frequency, mid.phaseDegrees(),
                last.frequency, last.phaseDegrees()
            ))
        }

        // Calculate raw VDI based on multiple factors
        val rawVDI = calculateRawVDI(phaseSlope, conductivityIndex, avgAmplitude)

        // Clamp to valid range
        val vdi = rawVDI.coerceIn(VDI_MIN, VDI_MAX)

        // Classify target type
        val targetType = classifyTarget(vdi, phaseSlope, phaseConsistency)

        // Calculate confidence based on signal strength and consistency
        val confidence = calculateConfidence(avgAmplitude, phaseConsistency)

        android.util.Log.d("VDICalculator", String.format(
            "VDI Result: vdi=%d, confidence=%.1f%%, type=%s",
            vdi, confidence * 100, targetType
        ))

        return VDIResult(vdi, confidence, targetType, phaseSlope, conductivityIndex)
    }

    /**
     * Calculate phase slope across frequencies
     * Ferrous metals show steep negative slope
     * Non-ferrous show relatively flat slope
     */
    private fun calculatePhaseSlope(analysis: List<ToneAnalysis>): Double {
        if (analysis.size < 2) return 0.0

        val lowest = analysis.first()
        val highest = analysis.last()

        val phaseDiff = highest.phaseDegrees() - lowest.phaseDegrees()
        val freqDiff = highest.frequency - lowest.frequency

        // Normalize to degrees per kHz
        return phaseDiff / (freqDiff / 1000.0)
    }

    /**
     * Calculate conductivity index based on amplitude vs frequency relationship
     * High conductors maintain amplitude at high frequencies
     */
    private fun calculateConductivityIndex(analysis: List<ToneAnalysis>): Double {
        if (analysis.size < 2) return 0.5

        // Compare low frequency amplitude to high frequency amplitude
        val lowFreqAmp = analysis.take(analysis.size / 3).map { it.amplitude }.average()
        val highFreqAmp = analysis.takeLast(analysis.size / 3).map { it.amplitude }.average()

        if (lowFreqAmp < 0.001) return 0.5

        // Ratio > 1.0 means high frequencies are stronger (high conductivity)
        // Ratio < 1.0 means low frequencies dominate (lower conductivity)
        val ratio = highFreqAmp / lowFreqAmp

        // Normalize to 0-1 range
        return ratio.coerceIn(0.0, 2.0) / 2.0
    }

    /**
     * Calculate phase consistency across frequencies
     * More consistent = better confidence
     */
    private fun calculatePhaseConsistency(analysis: List<ToneAnalysis>): Double {
        if (analysis.size < 2) return 0.0

        val phases = analysis.map { it.phaseDegrees() }
        val avgPhase = phases.average()

        // Calculate standard deviation
        val variance = phases.map { (it - avgPhase) * (it - avgPhase) }.average()
        val stdDev = sqrt(variance)

        // Convert to consistency score (0-1, higher is more consistent)
        // Typical stdDev ranges from 0-90 degrees
        return (1.0 - (stdDev / 90.0).coerceIn(0.0, 1.0))
    }

    /**
     * Calculate raw VDI from multiple parameters
     */
    private fun calculateRawVDI(
        phaseSlope: Double,
        conductivityIndex: Double,
        avgAmplitude: Double
    ): Int {
        // Base VDI on phase slope (primary discriminator)
        // Steep negative slope = ferrous (low VDI)
        // Flat or positive slope = non-ferrous (higher VDI)

        val slopeComponent = if (phaseSlope < 0) {
            // Ferrous: map -∞ to 0 → 0 to 30 VDI
            val normalizedSlope = (phaseSlope / -10.0).coerceIn(0.0, 1.0)
            (30 * (1.0 - normalizedSlope)).toInt()
        } else {
            // Non-ferrous: use conductivity index
            // Map 0-1 conductivity → 30-99 VDI
            (30 + (conductivityIndex * 69)).toInt()
        }

        // Adjust for amplitude (larger targets can shift VDI)
        val amplitudeAdjust = if (avgAmplitude > 0.5) {
            5  // Strong signal, boost VDI slightly
        } else if (avgAmplitude < 0.1) {
            -5  // Weak signal, reduce VDI slightly
        } else {
            0
        }

        return slopeComponent + amplitudeAdjust
    }

    /**
     * Classify target type based on VDI and other parameters
     */
    private fun classifyTarget(
        vdi: Int,
        phaseSlope: Double,
        phaseConsistency: Double
    ): TargetType {
        // Low confidence = unknown
        if (phaseConsistency < 0.3) {
            return TargetType.UNKNOWN
        }

        return when {
            vdi <= FERROUS_MAX && phaseSlope < -3.0 -> TargetType.FERROUS
            vdi <= LOW_CONDUCTOR_MAX -> TargetType.LOW_CONDUCTOR
            vdi >= HIGH_CONDUCTOR_MIN -> TargetType.HIGH_CONDUCTOR
            vdi in GOLD_RANGE_MIN..GOLD_RANGE_MAX -> TargetType.GOLD_RANGE
            vdi in (LOW_CONDUCTOR_MAX + 1) until HIGH_CONDUCTOR_MIN -> TargetType.MID_CONDUCTOR
            else -> TargetType.UNKNOWN
        }
    }

    /**
     * Calculate confidence score
     */
    private fun calculateConfidence(avgAmplitude: Double, phaseConsistency: Double): Double {
        // Confidence based on signal strength and phase consistency
        val amplitudeScore = avgAmplitude.coerceIn(0.0, 1.0)

        // Weight phase consistency more heavily
        return (amplitudeScore * 0.3 + phaseConsistency * 0.7)
    }

    /**
     * Get human-readable target description
     */
    fun getTargetDescription(result: VDIResult): String {
        val confidence = when {
            result.confidence > 0.8 -> "High"
            result.confidence > 0.5 -> "Medium"
            result.confidence > 0.3 -> "Low"
            else -> "Very Low"
        }

        val typeDesc = when (result.targetType) {
            TargetType.FERROUS -> "Ferrous (Iron/Steel)"
            TargetType.LOW_CONDUCTOR -> "Low Conductor (Foil/Small Al)"
            TargetType.MID_CONDUCTOR -> "Mid Conductor (Brass/Zinc)"
            TargetType.HIGH_CONDUCTOR -> "High Conductor (Cu/Ag)"
            TargetType.GOLD_RANGE -> "Gold Range (Au jewelry)"
            TargetType.UNKNOWN -> "Unknown"
        }

        return "$typeDesc | Confidence: $confidence"
    }
}
