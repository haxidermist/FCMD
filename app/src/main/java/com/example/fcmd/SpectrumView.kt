package com.example.fcmd

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.log10
import kotlin.math.max

/**
 * Custom view for displaying frequency spectrum
 */
class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val spectrumPaint = Paint().apply {
        color = Color.parseColor("#00FF00") // Green
        strokeWidth = 2f
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 180  // Semi-transparent
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#303030")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 20f
        isAntiAlias = true
    }

    private val peakPaint = Paint().apply {
        color = Color.parseColor("#FFFF00") // Yellow for peaks
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var magnitudeData = FloatArray(0)
    private var frequencyBins = FloatArray(0)
    private var sampleRate = 48000

    private val fftSize = 2048
    private val fft = FFT(fftSize)

    // Display parameters
    private val minFreqHz = 100f
    private val maxFreqHz = 24000f

    // Log amplitude display (normalized 0 to 1, displayed as log)
    private val minLogAmplitude = -4f  // log10(0.0001) = -4
    private val maxLogAmplitude = 0f   // log10(1.0) = 0

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * Update spectrum with new audio data
     */
    fun updateSpectrum(audioData: FloatArray, sampleRate: Int) {
        this.sampleRate = sampleRate

        // Compute FFT
        val rawMagnitude = fft.computeMagnitude(audioData)

        // Normalize magnitude to 0-1 range
        val maxMag = rawMagnitude.maxOrNull() ?: 1f
        magnitudeData = FloatArray(rawMagnitude.size) { i ->
            if (maxMag > 0) rawMagnitude[i] / maxMag else 0f
        }

        frequencyBins = fft.getFrequencyBins(sampleRate)

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (magnitudeData.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()

        // Draw frequency grid
        drawGrid(canvas, width, height)

        // Draw spectrum
        drawSpectrum(canvas, width, height)

        // Draw frequency labels
        drawLabels(canvas, width, height)
    }

    private fun drawGrid(canvas: Canvas, width: Float, height: Float) {
        // Horizontal grid lines (log amplitude: 1.0, 0.1, 0.01, 0.001, 0.0001)
        val amplitudeSteps = floatArrayOf(1.0f, 0.1f, 0.01f, 0.001f, 0.0001f)
        for (amp in amplitudeSteps) {
            val y = amplitudeToY(amp, height)
            canvas.drawLine(0f, y, width, y, gridPaint)
        }

        // Vertical grid lines (frequency - log scale)
        val freqSteps = floatArrayOf(100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f, 20000f)
        for (freq in freqSteps) {
            if (freq in minFreqHz..maxFreqHz) {
                val x = freqToX(freq, width)
                canvas.drawLine(x, 0f, x, height, gridPaint)
            }
        }
    }

    private fun drawSpectrum(canvas: Canvas, width: Float, height: Float) {
        val path = Path()
        var started = false

        for (i in magnitudeData.indices) {
            val freq = frequencyBins[i]
            if (freq < minFreqHz || freq > maxFreqHz) continue

            val normalizedMag = magnitudeData[i].coerceIn(0.0001f, 1.0f)

            val x = freqToX(freq, width)
            val y = amplitudeToY(normalizedMag, height)

            if (!started) {
                path.moveTo(x, height)
                path.lineTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }

        // Close path at bottom
        if (started) {
            path.lineTo(freqToX(maxFreqHz, width), height)
            path.close()
            canvas.drawPath(path, spectrumPaint)
        }

        // Draw peak indicators for multi-tone
        drawPeaks(canvas, width, height)
    }

    private fun drawPeaks(canvas: Canvas, width: Float, height: Float) {
        if (magnitudeData.size < 10) return

        // Find local peaks
        val threshold = magnitudeData.maxOrNull()?.let { it * 0.3f } ?: return

        for (i in 5 until magnitudeData.size - 5) {
            val mag = magnitudeData[i]
            if (mag > threshold &&
                mag > magnitudeData[i - 1] &&
                mag > magnitudeData[i + 1] &&
                mag > magnitudeData[i - 2] &&
                mag > magnitudeData[i + 2]) {

                val freq = frequencyBins[i]
                if (freq in minFreqHz..maxFreqHz) {
                    val x = freqToX(freq, width)
                    val y = amplitudeToY(mag.coerceIn(0.0001f, 1.0f), height)

                    // Draw vertical line at peak
                    canvas.drawLine(x, y, x, height, peakPaint)
                }
            }
        }
    }

    private fun drawLabels(canvas: Canvas, width: Float, height: Float) {
        // Frequency labels (log scale)
        canvas.drawText("100", freqToX(100f, width) - 10f, height - 5f, textPaint)
        canvas.drawText("1k", freqToX(1000f, width) - 5f, height - 5f, textPaint)
        canvas.drawText("10k", freqToX(10000f, width) - 10f, height - 5f, textPaint)

        // Amplitude labels (log scale, normalized)
        canvas.drawText("1.0", 5f, amplitudeToY(1.0f, height) + 15f, textPaint)
        canvas.drawText("0.1", 5f, amplitudeToY(0.1f, height) + 15f, textPaint)
        canvas.drawText("0.01", 5f, amplitudeToY(0.01f, height) + 15f, textPaint)
    }

    /**
     * Convert frequency to X coordinate (logarithmic scale)
     */
    private fun freqToX(freq: Float, width: Float): Float {
        val logMin = log10(minFreqHz)
        val logMax = log10(maxFreqHz)
        val logFreq = log10(freq.coerceIn(minFreqHz, maxFreqHz))
        return width * (logFreq - logMin) / (logMax - logMin)
    }

    /**
     * Convert normalized amplitude to Y coordinate (logarithmic scale, inverted)
     */
    private fun amplitudeToY(amplitude: Float, height: Float): Float {
        val logAmp = log10(amplitude.coerceIn(0.0001f, 1.0f))
        val normalized = (logAmp - minLogAmplitude) / (maxLogAmplitude - minLogAmplitude)
        return height * (1f - normalized.coerceIn(0f, 1f))
    }

    fun clear() {
        magnitudeData = FloatArray(0)
        invalidate()
    }
}
