package com.example.fcmd

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
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
    private var depthEstimate: DepthEstimate? = null

    // Helper function to convert dp to pixels
    private fun Float.dp(): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this,
            context.resources.displayMetrics
        )
    }

    // Helper function to convert sp to pixels for text
    private fun Float.sp(): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            this,
            context.resources.displayMetrics
        )
    }

    private val vdiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF00")
        textSize = 120f.sp()  // Reduced from 300px to 120sp
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 16f.sp()  // Reduced from 40px to 16sp
        textAlign = Paint.Align.CENTER
    }

    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFAA00")
        textSize = 24f.sp()  // Reduced from 60px to 24sp
        textAlign = Paint.Align.CENTER
    }

    private val confidenceBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFFF")  // Bright cyan for better contrast
        style = Paint.Style.STROKE
        strokeWidth = 2f.dp()  // Converted to dp
    }

    private val barBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")  // Dark background for empty bars
        style = Paint.Style.FILL
    }

    private val conductivityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f.sp()  // Reduced from 36px to 14sp
        textAlign = Paint.Align.CENTER
    }

    private val confidenceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f.sp()  // Reduced from 120px to 48sp
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val iconOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f.dp()  // Converted to dp
        color = Color.WHITE
    }

    fun updateVDI(vdiResult: VDIResult?) {
        if (vdiResult == null) {
            vdiValue = 0
            confidence = 0.0
            targetType = TargetType.UNKNOWN
            conductivityIndex = 0.0
            depthEstimate = null
        } else {
            vdiValue = vdiResult.vdi
            confidence = vdiResult.confidence
            targetType = vdiResult.targetType
            conductivityIndex = vdiResult.conductivityIndex
            depthEstimate = vdiResult.depthEstimate
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

        if (width == 0 || height == 0) return

        val centerX = width / 2f
        val centerY = height / 2f

        // Calculate responsive sizes based on view height
        val viewHeight = height.toFloat()

        // VDI text size: 15-20% of view height
        val vdiTextSize = (viewHeight * 0.18f).coerceIn(60f.sp(), 150f.sp())
        vdiPaint.textSize = vdiTextSize

        // Target text: 4% of view height
        val targetTextSize = (viewHeight * 0.04f).coerceIn(14f.sp(), 28f.sp())
        targetPaint.textSize = targetTextSize

        // Confidence text: 7% of view height
        val confidenceTextSize = (viewHeight * 0.07f).coerceIn(24f.sp(), 60f.sp())
        confidenceTextPaint.textSize = confidenceTextSize

        // Label text: 3% of view height
        val labelTextSize = (viewHeight * 0.03f).coerceIn(12f.sp(), 20f.sp())
        labelPaint.textSize = labelTextSize

        // Calculate vertical spacing (divide view into sections)
        val topMargin = viewHeight * 0.03f // 3% from top
        val conductivityBarHeight = viewHeight * 0.15f // 15% for conductivity section
        val vdiY = topMargin + conductivityBarHeight + (viewHeight - topMargin - conductivityBarHeight) * 0.25f
        val confidenceY = vdiY + viewHeight * 0.16f // Confidence below VDI
        val targetIconY = vdiY + viewHeight * 0.32f // Target icon below confidence
        val depthY = vdiY + viewHeight * 0.48f // Depth below target icon

        // Draw conductivity indicator at top
        drawConductivityIndicator(canvas, centerX, topMargin)

        // Draw large VDI number
        val vdiText = String.format("%02d", vdiValue)
        canvas.drawText(vdiText, centerX, vdiY, vdiPaint)

        // Draw confidence percentage below VDI (centered) with same text size as VDI
        drawConfidenceBelow(canvas, centerX, confidenceY, vdiTextSize)

        // Draw target icon centered below confidence
        drawTargetIconCentered(canvas, centerX, targetIconY)

        // Draw depth estimate below target icon
        drawDepthEstimate(canvas, centerX, depthY)
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

        // Icon size: 8% of view height
        val iconSize = (height * 0.08f).coerceIn(40f.dp(), 80f.dp())

        // Position icon to left of VDI with responsive spacing
        val iconSpacing = height * 0.08f
        val iconCenterX = centerX - vdiTextWidth / 2f - iconSpacing
        val iconCenterY = vdiY - vdiPaint.textSize * 0.35f  // Align with VDI baseline

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
                    strokeWidth = 1.5f.dp()
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

    private fun drawTargetIconCentered(canvas: Canvas, centerX: Float, iconY: Float) {
        // Icon size: 10% of view height for better visibility when centered
        val iconSize = (height * 0.10f).coerceIn(50f.dp(), 100f.dp())

        val iconCenterX = centerX
        val iconCenterY = iconY

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
                    strokeWidth = 1.5f.dp()
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
        val vdiText = String.format("%02d", vdiValue)
        val vdiTextWidth = vdiPaint.measureText(vdiText)
        // Use fixed-width format with 3 digits (right-aligned) so % sign doesn't move
        val confidenceText = String.format("%3.0f%%", confidence * 100)

        // Draw confidence text with responsive spacing (3% of width or 8dp minimum)
        val confidenceSpacing = (width * 0.03f).coerceAtLeast(8f.dp())
        val confidenceX = centerX + vdiTextWidth / 2f + confidenceSpacing
        canvas.drawText(confidenceText, confidenceX, vdiY, confidenceTextPaint)
    }

    private fun drawConfidenceBelow(canvas: Canvas, centerX: Float, confidenceY: Float, vdiTextSize: Float) {
        // Calculate confidence color from red (low) to green (high)
        val color = when {
            confidence < 0.33 -> Color.parseColor("#FF0000")  // Red
            confidence < 0.67 -> Color.parseColor("#FFAA00")  // Orange
            else -> Color.parseColor("#00FF00")  // Green
        }

        // Create a paint for centered confidence text below VDI with same size as VDI
        val confidenceBelowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = vdiTextSize  // Use same text size as VDI number
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Format confidence as percentage
        val confidenceText = String.format("%.0f%%", confidence * 100)
        canvas.drawText(confidenceText, centerX, confidenceY, confidenceBelowPaint)
    }

    private fun drawDepthEstimate(canvas: Canvas, centerX: Float, depthY: Float) {
        val depth = depthEstimate
        if (depth == null) {
            // No depth estimate available
            return
        }

        // Depth text size: 5% of view height
        val depthTextSize = (height * 0.05f).coerceIn(18f.sp(), 40f.sp())

        // Create paint for depth display
        val depthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FFFF")  // Cyan
            textSize = depthTextSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Display format: "SHALLOW 2-4""
        val depthText = "${depth.category.displayName.uppercase()} ${depth.category.depthRange}"
        canvas.drawText(depthText, centerX, depthY, depthPaint)

        // Draw depth indicator bars below text
        val indicatorY = depthY + depthTextSize * 0.8f
        val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = depthTextSize * 0.9f
            textAlign = Paint.Align.CENTER
        }

        // Color indicator based on depth
        indicatorPaint.color = when (depth.category) {
            DepthCategory.SURFACE -> Color.parseColor("#00FF00")      // Green - surface
            DepthCategory.SHALLOW -> Color.parseColor("#7FFF00")      // Yellow-green
            DepthCategory.MEDIUM -> Color.parseColor("#FFFF00")       // Yellow
            DepthCategory.DEEP -> Color.parseColor("#FFA500")         // Orange
            DepthCategory.VERY_DEEP -> Color.parseColor("#FF0000")    // Red
        }

        canvas.drawText(depth.category.indicator, centerX, indicatorY, indicatorPaint)
    }

    private fun drawConfidenceBars(canvas: Canvas, centerX: Float, startY: Float) {
        // Draw confidence label with brighter color
        labelPaint.textSize = 16f.sp()
        labelPaint.color = Color.parseColor("#00FFFF")  // Cyan
        canvas.drawText("CONFIDENCE", centerX, startY, labelPaint)
        labelPaint.color = Color.parseColor("#AAAAAA")  // Reset

        // Draw horizontal bar same width as conductivity bar
        val barWidth = width * 0.8f
        val barHeight = 20f.dp()  // Converted to dp
        val barLeft = centerX - barWidth / 2f
        val barTop = startY + 8f.dp()
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
            strokeWidth = 1f.dp()
        }
        for (percent in 20..80 step 20) {
            val x = barLeft + barWidth * (percent / 100f)
            canvas.drawLine(x, barTop, x, barBottom, markerPaint)
        }

        // Draw scale labels aligned with bar
        labelPaint.textSize = 12f.sp()
        labelPaint.color = Color.WHITE
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("0%", barLeft, startY + barHeight + 8f.dp(), labelPaint)

        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("100%", barRight, startY + barHeight + 8f.dp(), labelPaint)

        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.color = Color.parseColor("#AAAAAA")  // Reset
    }

    private fun drawConductivityIndicator(canvas: Canvas, centerX: Float, startY: Float) {
        // Draw conductivity label with brighter color (use current labelPaint size set in onDraw)
        labelPaint.color = Color.parseColor("#00FFFF")  // Cyan
        canvas.drawText("CONDUCTIVITY", centerX, startY + labelPaint.textSize, labelPaint)
        labelPaint.color = Color.parseColor("#AAAAAA")  // Reset

        // LED VU meter style bar - responsive to view size
        val barWidth = width * 0.8f
        val barHeight = (height * 0.035f).coerceIn(16f.dp(), 32f.dp())  // 3.5% of height
        val barLeft = centerX - barWidth / 2f
        val labelHeight = labelPaint.textSize
        val barTop = startY + labelHeight + (height * 0.015f)  // 1.5% spacing
        val barRight = barLeft + barWidth
        val barBottom = barTop + barHeight

        // Number of LED segments - adaptive to screen width
        val numSegments = ((width / 20f).toInt().coerceIn(30, 50))
        val gap = (barHeight * 0.1f).coerceAtLeast(1f.dp())  // Gap proportional to bar height
        val segmentWidth = (barWidth - (numSegments - 1) * gap) / numSegments

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
            strokeWidth = 1f.dp()
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
            val cornerRadius = (barHeight * 0.15f).coerceAtLeast(2f.dp())  // Corner radius proportional to bar height
            canvas.drawRoundRect(segmentRect, cornerRadius, cornerRadius, segmentPaint)

            // Draw subtle outline
            canvas.drawRoundRect(segmentRect, cornerRadius, cornerRadius, segmentOutlinePaint)

            // Add glow effect for lit segments
            if (i < litSegments) {
                val glowRadius = (barHeight * 0.2f).coerceAtLeast(2f.dp())
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = segmentColor
                    alpha = 100
                    style = Paint.Style.FILL
                    maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawRoundRect(segmentRect, cornerRadius, cornerRadius, glowPaint)
            }
        }

        // Draw labels with better contrast, aligned with bar
        val smallLabelSize = (height * 0.02f).coerceIn(10f.sp(), 16f.sp())
        val savedLabelSize = labelPaint.textSize
        labelPaint.textSize = smallLabelSize
        labelPaint.color = Color.WHITE
        labelPaint.textAlign = Paint.Align.LEFT
        val labelY = barBottom + smallLabelSize + (height * 0.01f)
        canvas.drawText("LOW", barLeft, labelY, labelPaint)

        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("HIGH", barRight, labelY, labelPaint)

        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.color = Color.parseColor("#AAAAAA")  // Reset
        labelPaint.textSize = savedLabelSize
    }

    fun clear() {
        vdiValue = 0
        confidence = 0.0
        targetType = TargetType.UNKNOWN
        conductivityIndex = 0.0
        depthEstimate = null
        invalidate()
    }
}
