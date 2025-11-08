# Theme System Integration Guide

## Overview

The FCMD theme system allows users to switch between different visual styles (Modern, Steampunk, Aviation, Retro Audio) while maintaining full functionality. Themes are selected in the **Info/Settings** screen and persist across app restarts.

## Architecture

```
UITheme (interface)
    ├── DefaultTheme (Modern/Current)
    ├── SteampunkTheme (Victorian brass & copper)
    ├── AviationTheme (Cockpit instruments)
    └── RetroAudioTheme (Classic audio equipment)

ThemeManager (singleton)
    ├── Stores current theme
    ├── Persists to SharedPreferences
    └── Notifies listeners of changes

Activities/Views
    └── Listen to theme changes
    └── Update UI when theme changes
```

## Quick Start

### 1. Get Theme Manager

```kotlin
val themeManager = ThemeManager.getInstance(context)
val currentTheme = themeManager.getCurrentTheme()
```

### 2. Apply Theme to View

```kotlin
// In your Activity or Custom View
class MainActivity : AppCompatActivity(), ThemeManager.ThemeChangeListener {

    private lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themeManager = ThemeManager.getInstance(this)
        themeManager.addThemeChangeListener(this)

        // Apply initial theme
        applyTheme(themeManager.getCurrentTheme())
    }

    override fun onThemeChanged(newTheme: UITheme) {
        // Theme was changed (e.g., in Settings)
        applyTheme(newTheme)
    }

    private fun applyTheme(theme: UITheme) {
        // Update UI colors
        binding.root.setBackgroundColor(theme.backgroundColor)
        binding.statusText.setTextColor(theme.primaryTextColor)
        binding.infoText.setTextColor(theme.secondaryTextColor)

        // Update custom views
        binding.waveformView.applyTheme(theme)
        binding.vdiDisplayView.applyTheme(theme)
    }

    override fun onDestroy() {
        super.onDestroy()
        themeManager.removeThemeChangeListener(this)
    }
}
```

### 3. Update Custom Views

```kotlin
class WaveformView : View {

    private var theme: UITheme = DefaultTheme()

    fun applyTheme(newTheme: UITheme) {
        theme = newTheme

        // Update paint colors
        waveformPaint.color = theme.waveformColor
        gridPaint.color = theme.gridColor
        centerLinePaint.color = theme.centerLineColor

        // Apply glow effect if theme supports it
        if (theme.enableGlow) {
            waveformPaint.maskFilter = BlurMaskFilter(
                theme.glowRadius,
                BlurMaskFilter.Blur.NORMAL
            )
        } else {
            waveformPaint.maskFilter = null
        }

        // Redraw with new theme
        invalidate()
    }
}
```

## Theme Properties Reference

### Colors

| Property | Purpose | Example (Default) | Example (Steampunk) |
|----------|---------|-------------------|---------------------|
| `backgroundColor` | Main background | `#1E1E1E` (dark gray) | `#2C1810` (dark wood) |
| `surfaceColor` | Card/panel backgrounds | `#2D2D2D` | `#3D2415` |
| `primaryTextColor` | Main text | `#FFFFFF` (white) | `#F4E8C1` (aged paper) |
| `secondaryTextColor` | Secondary text | `#B0B0B0` (gray) | `#C9A961` (faded brass) |
| `primaryAccentColor` | Buttons, highlights | `#4CAF50` (green) | `#B8860B` (brass) |
| `waveformColor` | Signal waveform | `#00FF00` (green) | `#B8860B` (brass) |
| `vdiFerrous` | VDI: ferrous metals | `#8B0000` | `#8B0000` (rust) |
| `vdiHighConductor` | VDI: copper/silver | `#32CD32` | `#B8860B` (brass) |

### Typography

| Property | Purpose |
|----------|---------|
| `titleFont` | Headings, titles |
| `bodyFont` | Normal text |
| `monoFont` | Technical data, numbers |

**Example:**
```kotlin
textView.typeface = theme.titleFont
textView.textSize = 18f
textView.setTextColor(theme.primaryTextColor)
```

### Effects

| Property | Type | Purpose |
|----------|------|---------|
| `enableGlow` | Boolean | Enable glow/bloom effects |
| `glowRadius` | Float | Glow radius in pixels |
| `enableTextures` | Boolean | Enable background textures |

## Integration Examples

### Example 1: VDI Display View

```kotlin
class VDIDisplayView : View {

    private lateinit var theme: UITheme
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun applyTheme(newTheme: UITheme) {
        theme = newTheme
        textPaint.typeface = theme.titleFont
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        canvas.drawColor(theme.surfaceColor)

        // VDI bar segments
        val vdiColors = listOf(
            theme.vdiFerrous,
            theme.vdiLowConductor,
            theme.vdiMidConductor,
            theme.vdiGoldRange,
            theme.vdiHighConductor
        )

        vdiColors.forEachIndexed { index, color ->
            paint.color = color

            // Add glow if enabled
            if (theme.enableGlow) {
                paint.maskFilter = BlurMaskFilter(
                    theme.glowRadius,
                    BlurMaskFilter.Blur.NORMAL
                )
            }

            // Draw segment
            canvas.drawRect(/* segment bounds */, paint)
        }

        // VDI number
        textPaint.color = theme.primaryTextColor
        textPaint.textSize = 48f
        canvas.drawText(vdiValue.toString(), centerX, centerY, textPaint)
    }
}
```

### Example 2: MainActivity Background & Text

```kotlin
private fun applyTheme(theme: UITheme) {
    // Main background
    binding.root.setBackgroundColor(theme.backgroundColor)

    // Card backgrounds
    binding.controlCard.setCardBackgroundColor(theme.cardBackgroundColor)
    binding.displayCard.setCardBackgroundColor(theme.cardBackgroundColor)

    // Text colors
    binding.titleText.setTextColor(theme.primaryTextColor)
    binding.statusText.setTextColor(theme.secondaryTextColor)
    binding.vdiLabel.setTextColor(theme.accentTextColor)

    // Apply to custom views
    binding.waveformView.applyTheme(theme)
    binding.vdiDisplayView.applyTheme(theme)
    binding.spectrumView.applyTheme(theme)

    // Update tone analysis text
    updateToneAnalysisColors(theme)
}
```

### Example 3: Dynamic Button Styling

```kotlin
private fun styleButton(button: Button, theme: UITheme) {
    // Button background
    val drawable = GradientDrawable().apply {
        setColor(theme.primaryAccentColor)
        cornerRadius = theme.buttonCornerRadius
        setStroke(theme.strokeWidth.toInt(), theme.secondaryAccentColor)
    }
    button.background = drawable

    // Button text
    button.setTextColor(theme.primaryTextColor)
    button.typeface = theme.bodyFont
}
```

## Steampunk Theme Enhancements

For the Steampunk theme, you can add special effects:

### Brass Texture Background

```kotlin
if (theme.enableTextures && theme is SteampunkTheme) {
    // Draw brass texture
    val brassTexture = BitmapFactory.decodeResource(resources, R.drawable.brass_texture)
    canvas.drawBitmap(brassTexture, 0f, 0f, texturePaint)
}
```

### Rivets & Ornamental Details

```kotlin
private fun drawSteampunkBorder(canvas: Canvas, theme: UITheme) {
    if (theme !is SteampunkTheme) return

    paint.color = theme.secondaryAccentColor  // Copper
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = theme.strokeWidth * 2

    // Draw ornate border
    canvas.drawRect(bounds, paint)

    // Draw corner rivets
    val rivetRadius = 8f
    paint.style = Paint.Style.FILL
    paint.color = theme.primaryAccentColor  // Brass

    canvas.drawCircle(left + 10f, top + 10f, rivetRadius, paint)
    canvas.drawCircle(right - 10f, top + 10f, rivetRadius, paint)
    canvas.drawCircle(left + 10f, bottom - 10f, rivetRadius, paint)
    canvas.drawCircle(right - 10f, bottom - 10f, rivetRadius, paint)
}
```

## Testing Themes

### Test All Themes in InfoActivity

1. Navigate to Info/Settings screen
2. Select each theme from dropdown
3. Return to main screen
4. Verify:
   - ✅ Colors updated correctly
   - ✅ Fonts applied
   - ✅ No layout breaks
   - ✅ Custom views redrawn
   - ✅ Text remains readable

### Checklist for Each View

- [ ] Background color
- [ ] Text colors (primary, secondary, accent)
- [ ] Waveform/graph colors
- [ ] VDI segment colors
- [ ] Button styles
- [ ] Card backgrounds
- [ ] Fonts applied
- [ ] Glow effects (if enabled)
- [ ] No performance issues

## Adding a New Theme

### 1. Create Theme Class

```kotlin
class NixieTubeTheme : UITheme {
    override val name = "Nixie Tube"
    override val description = "Vintage electronic displays"

    override val backgroundColor = Color.parseColor("#0D0D0D")
    override val primaryTextColor = Color.parseColor("#FF6600")
    // ... define all properties
    override val enableGlow = true
    override val glowRadius = 15f
}
```

### 2. Add to ThemeType Enum

```kotlin
enum class ThemeType {
    DEFAULT,
    STEAMPUNK,
    AVIATION,
    RETRO_AUDIO,
    NIXIE_TUBE;  // Add new theme

    fun createTheme(): UITheme {
        return when (this) {
            DEFAULT -> DefaultTheme()
            STEAMPUNK -> SteampunkTheme()
            AVIATION -> AviationTheme()
            RETRO_AUDIO -> RetroAudioTheme()
            NIXIE_TUBE -> NixieTubeTheme()  // Create instance
        }
    }
}
```

### 3. Theme Auto-Appears

The theme will automatically appear in the Settings dropdown!

## Performance Considerations

### Good Practices

✅ **Apply theme once on start**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    applyTheme(themeManager.getCurrentTheme())
}
```

✅ **Update only when theme changes**
```kotlin
override fun onThemeChanged(newTheme: UITheme) {
    applyTheme(newTheme)
}
```

✅ **Reuse Paint objects**
```kotlin
private val paint = Paint().apply {
    isAntiAlias = true
}

fun applyTheme(theme: UITheme) {
    paint.color = theme.waveformColor  // Update existing paint
}
```

### Avoid

❌ **Creating new Paint objects every frame**
```kotlin
override fun onDraw(canvas: Canvas) {
    val paint = Paint()  // BAD: allocates every frame
    paint.color = theme.waveformColor
}
```

❌ **Applying theme on every draw**
```kotlin
override fun onDraw(canvas: Canvas) {
    applyTheme(currentTheme)  // BAD: unnecessary
}
```

## Current Integration Status

### ✅ Implemented
- [x] Theme system architecture (UITheme, ThemeManager)
- [x] Four themes (Default, Steampunk, Aviation, Retro Audio)
- [x] Theme selector in InfoActivity
- [x] Theme persistence (SharedPreferences)
- [x] Theme change notifications

### 🔄 Needs Integration
- [ ] MainActivity background/text colors
- [ ] WaveformView theme support
- [ ] VDIDisplayView theme support
- [ ] SpectrumView theme support
- [ ] Buttons and controls
- [ ] Tone analysis text colors

### 📝 To Integrate

Add to each view:
```kotlin
fun applyTheme(theme: UITheme) {
    // Update colors, fonts, effects
    invalidate()  // Redraw
}
```

Add to MainActivity:
```kotlin
// In onCreate
themeManager.addThemeChangeListener(this)
applyTheme(themeManager.getCurrentTheme())

// Implement listener
override fun onThemeChanged(newTheme: UITheme) {
    applyTheme(newTheme)
}

// Apply theme to all views
private fun applyTheme(theme: UITheme) {
    binding.waveformView.applyTheme(theme)
    binding.vdiDisplayView.applyTheme(theme)
    // ... all other views
}
```

## Troubleshooting

### Theme doesn't persist
- Check SharedPreferences permissions
- Verify ThemeManager is singleton

### Colors not updating
- Ensure `applyTheme()` calls `invalidate()`
- Check that listener is registered

### Performance issues
- Remove theme application from draw loop
- Reuse Paint objects
- Disable glow effects if slow

## Future Enhancements

- [ ] Theme preview in Settings
- [ ] Custom color picker
- [ ] Export/import themes
- [ ] Night mode support
- [ ] Animated theme transitions
- [ ] Per-view theme overrides

---

**Theme system is ready!** Users can now swap between visual styles while maintaining full functionality.
