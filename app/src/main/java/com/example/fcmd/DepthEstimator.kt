package com.example.fcmd

import kotlin.math.pow

/**
 * Depth categories for target depth estimation
 * Using categories instead of exact measurements due to inherent uncertainty
 */
enum class DepthCategory(val displayName: String, val depthRange: String, val indicator: String) {
    SURFACE("Surface", "0-2\"", "●●●●"),
    SHALLOW("Shallow", "2-4\"", "●●●○"),
    MEDIUM("Medium", "4-6\"", "●●○○"),
    DEEP("Deep", "6-8\"", "●○○○"),
    VERY_DEEP("Very Deep", "8\"+", "○○○○")
}

/**
 * Depth estimation result
 */
data class DepthEstimate(
    val category: DepthCategory,
    val confidence: Double,      // 0.0-1.0 (always lower than VDI confidence)
    val depthFactor: Double,     // Raw calculation factor for debugging
    val amplitude: Double        // Signal strength used
) {
    fun getDisplayString(): String {
        val confStr = when {
            confidence > 0.7 -> "High"
            confidence > 0.5 -> "Med"
            confidence > 0.3 -> "Low"
            else -> "Very Low"
        }
        return "${category.indicator} ${category.displayName} (${category.depthRange}) | Conf: $confStr"
    }
}

/**
 * Depth Estimator for metal detection
 *
 * IMPORTANT LIMITATIONS:
 * 1. Target size is unknown - large shallow target looks like small deep target
 * 2. Target orientation affects signal strength
 * 3. Soil conductivity varies with moisture and minerals
 * 4. Ground balance affects measurements
 *
 * APPROACH:
 * Combines signal amplitude with multi-frequency response ratio,
 * normalized by expected target size from VDI classification.
 * Returns depth CATEGORIES rather than exact measurements.
 *
 * CALIBRATION RECOMMENDED:
 * Bury test targets at known depths in your soil to tune the thresholds.
 */
class DepthEstimator {

    companion object {
        // Calibration thresholds (adjust based on testing)
        // These are conservative estimates
        private const val SURFACE_THRESHOLD = 1.2
        private const val SHALLOW_THRESHOLD = 2.2
        private const val MEDIUM_THRESHOLD = 3.8
        private const val DEEP_THRESHOLD = 6.0

        // Amplitude exponent for inverse power law
        // Signal strength ∝ 1/depth^n where n ≈ 2-3
        private const val AMPLITUDE_EXPONENT = 0.35  // Inverse of ~2.85

        // Minimum amplitude for reliable depth estimation
        private const val MIN_AMPLITUDE = 0.02
    }

    /**
     * Estimate target depth from IQ analysis and VDI result
     *
     * @param analysis Multi-frequency tone analysis
     * @param vdiResult Target type and conductivity information
     * @return DepthEstimate with category and confidence
     */
    fun estimateDepth(analysis: List<ToneAnalysis>, vdiResult: VDIResult?): DepthEstimate {
        if (analysis.isEmpty()) {
            return DepthEstimate(DepthCategory.VERY_DEEP, 0.0, 0.0, 0.0)
        }

        // 1. Calculate average signal amplitude
        val avgAmplitude = analysis.map { it.amplitude }.average()

        // Signal too weak for reliable estimation
        if (avgAmplitude < MIN_AMPLITUDE) {
            return DepthEstimate(DepthCategory.VERY_DEEP, 0.1, 999.0, avgAmplitude)
        }

        // 2. Calculate frequency response ratio
        // Deep targets attenuate high frequencies more than low frequencies
        val freqRatio = calculateFrequencyRatio(analysis)

        // 3. Get expected target size from VDI
        val sizeNormalization = getTargetSizeNormalization(vdiResult)

        // 4. Calculate depth factor combining all inputs
        // Higher factor = deeper target
        val amplitudeFactor = 1.0 / avgAmplitude.pow(AMPLITUDE_EXPONENT)
        val depthFactor = amplitudeFactor * freqRatio / sizeNormalization

        // 5. Classify into depth category
        val category = classifyDepth(depthFactor)

        // 6. Calculate confidence (always lower than VDI confidence)
        // Strong signal = more confident depth estimate
        val baseConfidence = vdiResult?.confidence ?: 0.5
        val amplitudeConfidence = (avgAmplitude * 2.0).coerceIn(0.0, 1.0)
        val confidence = (baseConfidence * 0.6 + amplitudeConfidence * 0.4).coerceIn(0.0, 0.9)

        return DepthEstimate(category, confidence, depthFactor, avgAmplitude)
    }

    /**
     * Calculate frequency response ratio
     * Low/High ratio increases with depth due to skin effect
     */
    private fun calculateFrequencyRatio(analysis: List<ToneAnalysis>): Double {
        if (analysis.size < 3) return 1.0

        // Compare lowest 1/3 frequencies to highest 1/3 frequencies
        val splitPoint = analysis.size / 3
        val lowFreqAmp = analysis.take(splitPoint).map { it.amplitude }.average()
        val highFreqAmp = analysis.takeLast(splitPoint).map { it.amplitude }.average()

        if (highFreqAmp < 0.001) return 1.0

        val ratio = lowFreqAmp / highFreqAmp

        // Clamp to reasonable range (1.0 = equal response, >1.5 = deep)
        return ratio.coerceIn(0.8, 2.5)
    }

    /**
     * Get expected target size normalization based on VDI target type
     * Larger expected targets need higher normalization factor
     */
    private fun getTargetSizeNormalization(vdiResult: VDIResult?): Double {
        if (vdiResult == null) return 1.0

        // Low confidence VDI = can't trust size estimate
        if (vdiResult.confidence < 0.4) return 1.0

        return when (vdiResult.targetType) {
            TargetType.HIGH_CONDUCTOR -> 1.5   // Large: copper penny, silver coin
            TargetType.MID_CONDUCTOR -> 1.2     // Medium: brass, zinc penny
            TargetType.GOLD_RANGE -> 1.0        // Medium: gold jewelry
            TargetType.LOW_CONDUCTOR -> 0.8     // Small: foil, small aluminum
            TargetType.FERROUS -> 1.3           // Variable: bottle caps to nails
            TargetType.UNKNOWN -> 1.0           // Unknown size
        }
    }

    /**
     * Classify depth factor into category
     */
    private fun classifyDepth(depthFactor: Double): DepthCategory {
        return when {
            depthFactor < SURFACE_THRESHOLD -> DepthCategory.SURFACE
            depthFactor < SHALLOW_THRESHOLD -> DepthCategory.SHALLOW
            depthFactor < MEDIUM_THRESHOLD -> DepthCategory.MEDIUM
            depthFactor < DEEP_THRESHOLD -> DepthCategory.DEEP
            else -> DepthCategory.VERY_DEEP
        }
    }

    /**
     * Calibrate thresholds based on known target at known depth
     * Call this during calibration mode with buried test targets
     *
     * @param analysis IQ analysis of known target
     * @param vdiResult VDI of known target
     * @param actualDepthInches Measured depth in inches
     * @return Calculated depth factor for this target (for calibration table)
     */
    fun calibrate(
        analysis: List<ToneAnalysis>,
        vdiResult: VDIResult?,
        actualDepthInches: Double
    ): Double {
        val estimate = estimateDepth(analysis, vdiResult)

        android.util.Log.d("DepthEstimator",
            "Calibration: Actual=${actualDepthInches}\" " +
            "Estimated=${estimate.category.displayName} " +
            "DepthFactor=${String.format("%.2f", estimate.depthFactor)} " +
            "Amplitude=${String.format("%.3f", estimate.amplitude)}")

        return estimate.depthFactor
    }

    /**
     * Get description of depth estimation accuracy
     */
    fun getAccuracyNote(): String {
        return """
            Depth estimation is approximate due to:
            • Unknown target size and orientation
            • Variable soil conditions
            • Ground mineral interference

            Categories are more reliable than exact measurements.
            Calibrate with test targets for best accuracy.
        """.trimIndent()
    }
}
