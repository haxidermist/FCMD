package com.example.fcmd

import android.graphics.Color
import android.graphics.Typeface

/**
 * UI Theme interface defining visual appearance
 * Allows swapping between different visual styles while maintaining functionality
 */
interface UITheme {
    val name: String
    val description: String

    // Background colors
    val backgroundColor: Int
    val surfaceColor: Int
    val cardBackgroundColor: Int

    // Text colors
    val primaryTextColor: Int
    val secondaryTextColor: Int
    val accentTextColor: Int

    // Accent colors
    val primaryAccentColor: Int
    val secondaryAccentColor: Int
    val warningColor: Int
    val successColor: Int
    val errorColor: Int

    // Waveform/Graph colors
    val waveformColor: Int
    val gridColor: Int
    val centerLineColor: Int

    // VDI Display colors
    val vdiFerrous: Int
    val vdiLowConductor: Int
    val vdiMidConductor: Int
    val vdiGoldRange: Int
    val vdiHighConductor: Int

    // UI Element styles
    val buttonCornerRadius: Float
    val cardCornerRadius: Float
    val strokeWidth: Float

    // Typography
    val titleFont: Typeface
    val bodyFont: Typeface
    val monoFont: Typeface

    // Special effects
    val enableGlow: Boolean
    val enableTextures: Boolean
    val glowRadius: Float
}

/**
 * Default/Modern theme - current clean UI
 */
class DefaultTheme : UITheme {
    override val name = "Modern"
    override val description = "Clean, modern interface with high contrast"

    override val backgroundColor = Color.parseColor("#1E1E1E")
    override val surfaceColor = Color.parseColor("#2D2D2D")
    override val cardBackgroundColor = Color.parseColor("#383838")

    override val primaryTextColor = Color.parseColor("#FFFFFF")
    override val secondaryTextColor = Color.parseColor("#B0B0B0")
    override val accentTextColor = Color.parseColor("#4CAF50")

    override val primaryAccentColor = Color.parseColor("#4CAF50")
    override val secondaryAccentColor = Color.parseColor("#2196F3")
    override val warningColor = Color.parseColor("#FF9800")
    override val successColor = Color.parseColor("#4CAF50")
    override val errorColor = Color.parseColor("#F44336")

    override val waveformColor = Color.parseColor("#00FF00")
    override val gridColor = Color.parseColor("#404040")
    override val centerLineColor = Color.parseColor("#808080")

    override val vdiFerrous = Color.parseColor("#8B0000")
    override val vdiLowConductor = Color.parseColor("#FF6347")
    override val vdiMidConductor = Color.parseColor("#FFD700")
    override val vdiGoldRange = Color.parseColor("#FFA500")
    override val vdiHighConductor = Color.parseColor("#32CD32")

    override val buttonCornerRadius = 8f
    override val cardCornerRadius = 12f
    override val strokeWidth = 2f

    override val titleFont: Typeface = Typeface.DEFAULT_BOLD
    override val bodyFont: Typeface = Typeface.DEFAULT
    override val monoFont: Typeface = Typeface.MONOSPACE

    override val enableGlow = false
    override val enableTextures = false
    override val glowRadius = 0f
}

/**
 * Steampunk theme - Victorian-era brass and copper aesthetic
 */
class SteampunkTheme : UITheme {
    override val name = "Steampunk"
    override val description = "Victorian-era brass and copper gauges"

    // Rich dark brown background (aged wood/leather)
    override val backgroundColor = Color.parseColor("#2C1810")
    override val surfaceColor = Color.parseColor("#3D2415")
    override val cardBackgroundColor = Color.parseColor("#4A2C1A")

    // Aged brass/copper text
    override val primaryTextColor = Color.parseColor("#F4E8C1")  // Aged paper
    override val secondaryTextColor = Color.parseColor("#C9A961")  // Faded brass
    override val accentTextColor = Color.parseColor("#B8860B")  // Dark gold

    // Brass and copper accents
    override val primaryAccentColor = Color.parseColor("#B8860B")  // Dark goldenrod (brass)
    override val secondaryAccentColor = Color.parseColor("#B87333")  // Copper
    override val warningColor = Color.parseColor("#CD853F")  // Peru (aged brass)
    override val successColor = Color.parseColor("#708238")  // Patina green
    override val errorColor = Color.parseColor("#8B0000")  // Dark red (rust)

    // Steampunk meter colors
    override val waveformColor = Color.parseColor("#B8860B")  // Brass
    override val gridColor = Color.parseColor("#5C4033")  // Dark wood
    override val centerLineColor = Color.parseColor("#8B7355")  // Lighter wood

    // VDI colors with vintage feel
    override val vdiFerrous = Color.parseColor("#8B0000")  // Rust red
    override val vdiLowConductor = Color.parseColor("#CD853F")  // Peru
    override val vdiMidConductor = Color.parseColor("#DAA520")  // Goldenrod
    override val vdiGoldRange = Color.parseColor("#FFD700")  // Gold
    override val vdiHighConductor = Color.parseColor("#B8860B")  // Dark goldenrod

    // Rounded corners for Victorian style
    override val buttonCornerRadius = 4f
    override val cardCornerRadius = 8f
    override val strokeWidth = 3f

    // Victorian-era serif fonts
    override val titleFont: Typeface = Typeface.create("serif", Typeface.BOLD)
    override val bodyFont: Typeface = Typeface.create("serif", Typeface.NORMAL)
    override val monoFont: Typeface = Typeface.MONOSPACE

    override val enableGlow = true
    override val enableTextures = true
    override val glowRadius = 10f
}

/**
 * Aviation/Cockpit theme - Flight instrument aesthetic
 */
class AviationTheme : UITheme {
    override val name = "Aviation"
    override val description = "Aircraft cockpit instrument panel"

    // Dark instrument panel
    override val backgroundColor = Color.parseColor("#0A0A0A")
    override val surfaceColor = Color.parseColor("#1A1A1A")
    override val cardBackgroundColor = Color.parseColor("#2A2A2A")

    override val primaryTextColor = Color.parseColor("#FFFFFF")
    override val secondaryTextColor = Color.parseColor("#00FF00")  // Radar green
    override val accentTextColor = Color.parseColor("#00BFFF")  // Deep sky blue

    // Aviation colors
    override val primaryAccentColor = Color.parseColor("#00BFFF")  // Sky blue
    override val secondaryAccentColor = Color.parseColor("#00FF00")  // Radar green
    override val warningColor = Color.parseColor("#FFA500")  // Orange
    override val successColor = Color.parseColor("#00FF00")  // Green
    override val errorColor = Color.parseColor("#FF0000")  // Red

    override val waveformColor = Color.parseColor("#00FF00")  // Radar green
    override val gridColor = Color.parseColor("#003300")  // Dark green grid
    override val centerLineColor = Color.parseColor("#00BFFF")  // Sky blue

    // VDI with aviation color coding
    override val vdiFerrous = Color.parseColor("#FF0000")  // Red (danger)
    override val vdiLowConductor = Color.parseColor("#FFA500")  // Orange (caution)
    override val vdiMidConductor = Color.parseColor("#FFFF00")  // Yellow (warning)
    override val vdiGoldRange = Color.parseColor("#00FF00")  // Green (good)
    override val vdiHighConductor = Color.parseColor("#00BFFF")  // Blue (excellent)

    override val buttonCornerRadius = 2f
    override val cardCornerRadius = 4f
    override val strokeWidth = 2f

    // Military stencil-style fonts
    override val titleFont: Typeface = Typeface.DEFAULT_BOLD
    override val bodyFont: Typeface = Typeface.SANS_SERIF
    override val monoFont: Typeface = Typeface.MONOSPACE

    override val enableGlow = true
    override val enableTextures = false
    override val glowRadius = 8f
}

/**
 * Retro/Classic Audio theme - Vintage audio equipment aesthetic
 */
class RetroAudioTheme : UITheme {
    override val name = "Retro Audio"
    override val description = "Classic audio equipment with VU meters"

    // Brushed aluminum look
    override val backgroundColor = Color.parseColor("#1C1C1C")
    override val surfaceColor = Color.parseColor("#2A2A2A")
    override val cardBackgroundColor = Color.parseColor("#383838")

    override val primaryTextColor = Color.parseColor("#E0E0E0")
    override val secondaryTextColor = Color.parseColor("#A0A0A0")
    override val accentTextColor = Color.parseColor("#FF6600")  // Warm orange

    // Warm vintage colors
    override val primaryAccentColor = Color.parseColor("#FF6600")  // Orange
    override val secondaryAccentColor = Color.parseColor("#FFD700")  // Gold
    override val warningColor = Color.parseColor("#FFFF00")  // Yellow
    override val successColor = Color.parseColor("#00FF00")  // Green
    override val errorColor = Color.parseColor("#FF0000")  // Red

    // VU meter style
    override val waveformColor = Color.parseColor("#00FF00")  // Green waveform
    override val gridColor = Color.parseColor("#404040")
    override val centerLineColor = Color.parseColor("#808080")

    // VDI with audio meter colors (green to red)
    override val vdiFerrous = Color.parseColor("#CC0000")
    override val vdiLowConductor = Color.parseColor("#FF6600")
    override val vdiMidConductor = Color.parseColor("#FFFF00")
    override val vdiGoldRange = Color.parseColor("#CCFF00")
    override val vdiHighConductor = Color.parseColor("#00FF00")

    override val buttonCornerRadius = 6f
    override val cardCornerRadius = 10f
    override val strokeWidth = 2f

    override val titleFont: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    override val bodyFont: Typeface = Typeface.SANS_SERIF
    override val monoFont: Typeface = Typeface.MONOSPACE

    override val enableGlow = true
    override val enableTextures = false
    override val glowRadius = 12f
}

/**
 * Available theme types
 */
enum class ThemeType {
    DEFAULT,
    STEAMPUNK,
    AVIATION,
    RETRO_AUDIO;

    fun createTheme(): UITheme {
        return when (this) {
            DEFAULT -> DefaultTheme()
            STEAMPUNK -> SteampunkTheme()
            AVIATION -> AviationTheme()
            RETRO_AUDIO -> RetroAudioTheme()
        }
    }
}
