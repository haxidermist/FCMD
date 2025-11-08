package com.example.fcmd

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Custom view for displaying VDI (Visual Discrimination Indicator)
 * Shows large central VDI number with confidence bars and conductivity indication
 */
class VDIDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var vdiValue: Int = 0
    private var confidence: Double = 0.0
    private var targetType: TargetType = TargetType.UNKNOWN
    private var conductivityIndex: Double = 0.0

    private val vdiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF00")
        textSize = 300f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFAA00")
        textSize = 60f
        textAlign = Paint.Align.CENTER
    }

    private val confidenceBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFFF")  // Bright cyan for better contrast
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val barBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")  // Dark background for empty bars
        style = Paint.Style.FILL
    }

    private val conductivityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }

    private val confidenceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 120f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val iconOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }

    fun updateVDI(vdiResult: VDIResult?) {
        if (vdiResult == null) {
            vdiValue = 0
            confidence = 0.0
            targetType = TargetType.UNKNOWN
            conductivityIndex = 0.0
        } else {
            vdiValue = vdiResult.vdi
            confidence = vdiResult.confidence
            targetType = vdiResult.targetType
            conductivityIndex = vdiResult.conductivityIndex
        }

        // Update VDI color based on target type
        vdiPaint.color = when (targetType) {
            TargetType.FERROUS -> Color.parseColor("#FF0000")  // Red
            TargetType.LOW_CONDUCTOR -> Color.parseColor("#FFA500")  // Orange
            TargetType.MID_CONDUCTOR -> Color.parseColor("#FFFF00")  // Yellow
            TargetType.GOLD_RANGE -> Color.parseColor("#FFD700")  // Gold
            TargetType.HIGH_CONDUCTOR -> Color.parseColor("#00FF00")  // Green
            TargetType.UNKNOWN -> Color.parseColor("#808080")  // Gray
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f

        // Draw conductivity indicator at top, closer to status bar
        drawConductivityIndicator(canvas, centerX, 40f)

        // Draw target type icon to the left of VDI
        drawTargetIcon(canvas, centerX, centerY + 80f)

        // Draw large VDI number lower in center for more spacing from conductivity bar
        val vdiText = String.format("%02d", vdiValue)
        canvas.drawText(vdiText, centerX, centerY + 80f, vdiPaint)

        // Draw confidence percentage to the right of VDI with color-coded text
        drawConfidenceText(canvas, centerX, centerY + 80f)

        // Draw target type below VDI (adjusted for new VDI position)
        val targetText = getTargetTypeText()
        canvas.drawText(targetText, centerX, centerY + 180f, targetPaint)
    }

    private fun getTargetTypeText(): String {
        return when (targetType) {
            TargetType.FERROUS -> "FERROUS"
            TargetType.LOW_CONDUCTOR -> "LOW COND"
            TargetType.MID_CONDUCTOR -> "MID COND"
            TargetType.HIGH_CONDUCTOR -> "HIGH COND"
            TargetType.GOLD_RANGE -> "GOLD RANGE"
            TargetType.UNKNOWN -> "UNKNOWN"
        }
    }

    private fun drawTargetIcon(canvas: Canvas, centerX: Float, vdiY: Float) {
        // Position icon to the left of VDI, centered vertically with VDI number
        val vdiText = String.format("%02d", vdiValue)
        val vdiTextWidth = vdiPaint.measureText(vdiText)
        val iconCenterX = centerX - vdiTextWidth / 2f - 200f
        val iconCenterY = vdiY - 100f  // Align with center of VDI text
        val iconSize = 150f  // Increased from 120f

        // Set icon color based on target type (same as VDI color)
        iconPaint.color = vdiPaint.color

        when (targetType) {
            TargetType.FERROUS -> {
                // Draw magnet icon (horseshoe shape)
                val path = Path().apply {
                    moveTo(iconCenterX - iconSize / 2f, iconCenterY - iconSize / 2f)
                    lineTo(iconCenterX - iconSize / 2f, iconCenterY + iconSize / 2f)
                    lineTo(iconCenterX - iconSize / 4f, iconCenterY + iconSize / 2f)
                    lineTo(iconCenterX - iconSize / 4f, iconCenterY)
                    arcTo(
                        iconCenterX - iconSize / 4f, iconCenterY - iconSize / 2f,
                        iconCenterX + iconSize / 4f, iconCenterY + iconSize / 2f,
                        180f, 180f, false
                    )
                    lineTo(iconCenterX + iconSize / 4f, iconCenterY + iconSize / 2f)
                    lineTo(iconCenterX + iconSize / 2f, iconCenterY + iconSize / 2f)
                    lineTo(iconCenterX + iconSize / 2f, iconCenterY - iconSize / 2f)
                }
                canvas.drawPath(path, iconPaint)
            }
            TargetType.LOW_CONDUCTOR, TargetType.MID_CONDUCTOR -> {
                // Draw coin icon (circle)
                canvas.drawCircle(iconCenterX, iconCenterY, iconSize / 2f, iconPaint)
                canvas.drawCircle(iconCenterX, iconCenterY, iconSize / 2f, iconOutlinePaint)
                // Draw value symbol inside
                val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = iconSize * 0.6f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("¢", iconCenterX, iconCenterY + iconSize * 0.2f, symbolPaint)
            }
            TargetType.GOLD_RANGE -> {
                // Draw diamond/gem icon
                val path = Path().apply {
                    moveTo(iconCenterX, iconCenterY - iconSize / 2f)
                    lineTo(iconCenterX + iconSize / 2f, iconCenterY)
                    lineTo(iconCenterX, iconCenterY + iconSize / 2f)
                    lineTo(iconCenterX - iconSize / 2f, iconCenterY)
                    close()
                }
                canvas.drawPath(path, iconPaint)
                canvas.drawPath(path, iconOutlinePaint)
                // Draw inner lines
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                }
                canvas.drawLine(iconCenterX - iconSize / 2f, iconCenterY, iconCenterX + iconSize / 2f, iconCenterY, linePaint)
                canvas.drawLine(iconCenterX, iconCenterY - iconSize / 2f, iconCenterX, iconCenterY + iconSize / 2f, linePaint)
            }
            TargetType.HIGH_CONDUCTOR -> {
                // Draw larger coin with dollar sign
                canvas.drawCircle(iconCenterX, iconCenterY, iconSize / 2f, iconPaint)
                canvas.drawCircle(iconCenterX, iconCenterY, iconSize / 2f, iconOutlinePaint)
                val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = iconSize * 0.7f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("$", iconCenterX, iconCenterY + iconSize * 0.25f, symbolPaint)
            }
            TargetType.UNKNOWN -> {
                // Draw question mark
                val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = vdiPaint.color
                    textSize = iconSize * 1.2f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("?", iconCenterX, iconCenterY + iconSize * 0.3f, symbolPaint)
            }
        }
    }

    private fun drawConfidenceText(canvas: Canvas, centerX: Float, vdiY: Float) {
        // Calculate confidence color from red (low) to green (high)
        val color = when {
            confidence < 0.33 -> Color.parseColor("#FF0000")  // Red
            confidence < 0.67 -> Color.parseColor("#FFAA00")  // Orange
            else -> Color.parseColor("#00FF00")  // Green
        }

        confidenceTextPaint.color = color

        // Position to the right of the VDI number
        // VDI text is centered, so we need to calculate the right edge
        val vdiText = String.format("%02d", vdiValue)
        val vdiTextWidth = vdiPaint.measureText(vdiText)
        // Use fixed-width format with 3 digits (right-aligned) so % sign doesn't move
        val confidenceText = String.format("%3.0f%%", confidence * 100)

        // Draw confidence text starting from right edge of VDI with some spacing
        val confidenceX = centerX + vdiTextWidth / 2f + 20f
        canvas.drawText(confidenceText, confidenceX, vdiY, confidenceTextPaint)
    }

    private fun drawConfidenceBars(canvas: Canvas, centerX: Float, startY: Float) {
        // Draw confidence label with brighter color
        labelPaint.textSize = 40f
        labelPaint.color = Color.parseColor("#00FFFF")  // Cyan
        canvas.drawText("CONFIDENCE", centerX, startY, labelPaint)
        labelPaint.color = Color.parseColor("#AAAAAA")  // Reset

        // Draw horizontal bar same width as conductivity bar
        val barWidth = width * 0.8f
        val barHeight = 50f  // Same height as conductivity bar
        val barLeft = centerX - barWidth / 2f
        val barTop = startY + 20f
        val barRight = barLeft + barWidth
        val barBottom = barTop + barHeight

        // Draw dark background
        canvas.drawRect(barLeft, barTop, barRight, barBottom, barBackgroundPaint)

        // Calculate filled portion
        val filledWidth = barWidth * confidence.toFloat()
        val filledRight = barLeft + filledWidth

        // Create gradient for confidence bar
        if (confidence > 0.0) {
            val confidenceGradient = LinearGradient(
                barLeft, barTop, filledRight, barTop,
                intArrayOf(
                    Color.parseColor("#FF0000"),  // Red (low)
                    Color.parseColor("#FFA500"),  // Orange
                    Color.parseColor("#FFFF00"),  // Yellow
                    Color.parseColor("#00FF00")   // Green (high)
                ),
                null,
                Shader.TileMode.CLAMP
            )
            confidenceBarPaint.shader = confidenceGradient
            canvas.drawRect(barLeft, barTop, filledRight, barBottom, confidenceBarPaint)
            confidenceBarPaint.shader = null
        }

        // Draw outline with bright cyan
        canvas.drawRect(barLeft, barTop, barRight, barBottom, barOutlinePaint)

        // Draw percentage markers on bar
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#666666")
            strokeWidth = 2f
        }
        for (percent in 20..80 step 20) {
            val x = barLeft + barWidth * (percent / 100f)
            canvas.drawLine(x, barTop, x, barBottom, markerPaint)
        }

        // Draw scale labels aligned with bar
        labelPaint.textSize = 18f
        labelPaint.color = Color.WHITE
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("0%", barLeft, startY + barHeight + 20f, labelPaint)

        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("100%", barRight, startY + barHeight + 20f, labelPaint)

        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.color = Color.parseColor("#AAAAAA")  // Reset
    }

    private fun drawConductivityIndicator(canvas: Canvas, centerX: Float, startY: Float) {
        // Draw conductivity label with brighter color
        labelPaint.textSize = 40f
        labelPaint.color = Color.parseColor("#00FFFF")  // Cyan
        canvas.drawText("CONDUCTIVITY", centerX, startY, labelPaint)
        labelPaint.color = Color.parseColor("#AAAAAA")  // Reset

        // LED VU meter style bar
        val barWidth = width * 0.8f
        val barHeight = 50f
        val barLeft = centerX - barWidth / 2f
        val barTop = startY + 20f
        val barRight = barLeft + barWidth
        val barBottom = barTop + barHeight

        // Number of LED segments
        val numSegments = 40
        val segmentWidth = (barWidth - (numSegments - 1) * 4f) / numSegments  // 4px gap between segments
        val gap = 4f

        // Calculate how many segments should be lit based on conductivity
        val litSegments = (conductivityIndex * numSegments).toInt()

        // Define color zones (like classic VU meter)
        val colors = listOf(
            Color.parseColor("#FF0000"),  // Red - segments 0-7 (ferrous)
            Color.parseColor("#FF0000"),
            Color.parseColor("#FFA500"),  // Orange - segments 8-15
            Color.parseColor("#FFA500"),
            Color.parseColor("#FFFF00"),  // Yellow - segments 16-23
            Color.parseColor("#FFFF00"),
            Color.parseColor("#FFD700"),  // Gold - segments 24-31
            Color.parseColor("#FFD700"),
            Color.parseColor("#00FF00"),  // Green - segments 32-39 (high conductor)
            Color.parseColor("#00FF00")
        )

        val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val segmentOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#333333")
        }

        // Draw each LED segment
        for (i in 0 until numSegments) {
            val segmentLeft = barLeft + i * (segmentWidth + gap)
            val segmentRight = segmentLeft + segmentWidth

            // Determine segment color based on position
            val colorIndex = (i * 10 / numSegments).coerceIn(0, colors.size - 1)
            val segmentColor = colors[colorIndex]

            if (i < litSegments) {
                // Lit segment - full brightness
                segmentPaint.color = segmentColor
            } else {
                // Unlit segment - very dark version
                val r = Color.red(segmentColor) / 8
                val g = Color.green(segmentColor) / 8
                val b = Color.blue(segmentColor) / 8
                segmentPaint.color = Color.rgb(r, g, b)
            }

            // Draw segment with rounded corners
            val segmentRect = RectF(segmentLeft, barTop, segmentRight, barBottom)
            canvas.drawRoundRect(segmentRect, 4f, 4f, segmentPaint)

            // Draw subtle outline
            canvas.drawRoundRect(segmentRect, 4f, 4f, segmentOutlinePaint)

            // Add glow effect for lit segments
            if (i < litSegments) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = segmentColor
                    alpha = 100
                    style = Paint.Style.FILL
                    maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawRoundRect(segmentRect, 4f, 4f, glowPaint)
            }
        }

        // Draw labels with better contrast, aligned with bar
        labelPaint.textSize = 18f
        labelPaint.color = Color.WHITE
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("LOW", barLeft, startY + barHeight + 20f, labelPaint)

        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("HIGH", barRight, startY + barHeight + 20f, labelPaint)

        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.color = Color.parseColor("#AAAAAA")  // Reset
    }

    fun clear() {
        vdiValue = 0
        confidence = 0.0
        targetType = TargetType.UNKNOWN
        conductivityIndex = 0.0
        invalidate()
    }
}
