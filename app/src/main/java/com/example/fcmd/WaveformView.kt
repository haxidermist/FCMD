package com.example.fcmd

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val leftChannelPaint = Paint().apply {
        color = Color.parseColor("#00FF00") // Green for left
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val rightChannelPaint = Paint().apply {
        color = Color.parseColor("#FF6600") // Orange for right
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val centerLinePaint = Paint().apply {
        color = Color.parseColor("#404040")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#202020")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
    }

    private val leftChannelPath = Path()
    private val rightChannelPath = Path()

    private var leftChannelData = FloatArray(0)
    private var rightChannelData = FloatArray(0)
    private var displayBuffer = FloatArray(0)

    init {
        setBackgroundColor(Color.BLACK)
    }

    fun updateWaveform(stereoData: FloatArray) {
        if (stereoData.isEmpty()) return

        // Separate stereo channels
        val numSamples = stereoData.size / 2
        leftChannelData = FloatArray(numSamples)
        rightChannelData = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            leftChannelData[i] = stereoData[i * 2]
            rightChannelData[i] = stereoData[i * 2 + 1]
        }

        invalidate()
    }

    fun updateMonoWaveform(monoData: FloatArray) {
        leftChannelData = monoData.clone()
        rightChannelData = FloatArray(0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2f

        // Draw grid
        val gridLines = 8
        for (i in 1 until gridLines) {
            val y = (height / gridLines) * i
            canvas.drawLine(0f, y, width, y, gridPaint)
        }

        // Draw center line
        canvas.drawLine(0f, centerY, width, centerY, centerLinePaint)

        // Draw left channel
        if (leftChannelData.isNotEmpty()) {
            drawChannel(canvas, leftChannelData, leftChannelPaint, centerY, height / 4f)
            canvas.drawText("L", 10f, 30f, textPaint)
        }

        // Draw right channel
        if (rightChannelData.isNotEmpty()) {
            drawChannel(canvas, rightChannelData, rightChannelPaint, centerY, height / 4f)
            canvas.drawText("R", 10f, 60f, textPaint.apply {
                color = Color.parseColor("#FF6600")
            })
            textPaint.color = Color.WHITE
        }
    }

    private fun drawChannel(
        canvas: Canvas,
        data: FloatArray,
        paint: Paint,
        centerY: Float,
        amplitude: Float
    ) {
        if (data.isEmpty()) return

        val width = width.toFloat()
        val path = Path()

        val samplesPerPixel = max(1, data.size / width.toInt())
        val pixelStep = width / min(data.size, width.toInt())

        var started = false
        var pixelIndex = 0f

        for (i in data.indices step samplesPerPixel) {
            val endIndex = min(i + samplesPerPixel, data.size)
            var sum = 0f
            for (j in i until endIndex) {
                sum += data[j]
            }
            val average = sum / (endIndex - i)

            val x = pixelIndex
            val y = centerY - (average * amplitude)

            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }

            pixelIndex += pixelStep
        }

        canvas.drawPath(path, paint)
    }

    fun clear() {
        leftChannelData = FloatArray(0)
        rightChannelData = FloatArray(0)
        invalidate()
    }
}
