# Depth Estimation Feature

## Overview

The FCMD metal detector now includes **experimental depth estimation** that provides approximate target depth in categories rather than exact measurements.

## How It Works

### Multi-Factor Analysis

The depth estimator combines three factors:

1. **Signal Amplitude**
   - Physics: Signal strength decreases with cube of distance
   - Formula: `amplitudeFactor = 1 / amplitude^0.35`
   - Strong signal = shallow target, weak signal = deep target

2. **Frequency Response Ratio**
   - Physics: High frequencies attenuate faster with depth (skin effect)
   - Formula: `freqRatio = lowFreqAmplitude / highFreqAmplitude`
   - Ratio ≈ 1.0 = shallow, ratio > 1.5 = deep

3. **Target Size Normalization**
   - Uses VDI classification to estimate expected target size
   - Large targets (copper penny) get higher normalization (1.5×)
   - Small targets (foil) get lower normalization (0.8×)
   - Accounts for "large shallow = small deep" ambiguity

### Depth Categories

Instead of claiming "6.2 inches" accuracy, the system returns honest categories:

| Indicator | Category   | Range | Visual |
|-----------|------------|-------|--------|
| ●●●●      | SURFACE    | 0-2"  | Very strong signal |
| ●●●○      | SHALLOW    | 2-4"  | Strong signal |
| ●●○○      | MEDIUM     | 4-6"  | Moderate signal |
| ●○○○      | DEEP       | 6-8"  | Weak signal |
| ○○○○      | VERY_DEEP  | 8"+   | Very weak signal |

## Limitations

### Critical Limitations

⚠️ **Target Size is Unknown**
- A large can at 8" gives the same amplitude as a coin at 2"
- Size normalization from VDI helps but isn't perfect
- **Expected Error: ±50-100%**

⚠️ **Target Orientation Matters**
- Coin flat vs edge-on gives different signal strength
- Can cause depth estimate to shift one category

⚠️ **Soil Conditions Vary**
- Wet soil: signals attenuate faster (appears deeper)
- Dry soil: signals penetrate better (appears shallower)
- Mineralization: can affect both amplitude and frequency response
- Temperature and salinity affect conductivity

⚠️ **Ground Balance Affects Readings**
- Aggressive ground balance can reduce apparent signal strength
- Offset settings change amplitude measurements

### Confidence Score

Depth confidence is **always lower** than VDI confidence because:
- Depth has more unknown variables (size, orientation, soil)
- Calculated as: `(VDI_confidence × 0.6) + (amplitude_score × 0.4)`
- Maximum confidence: 90%

## Calibration

### Why Calibrate?

The default thresholds are conservative estimates. Calibrating with known targets in **your specific soil** improves accuracy significantly.

### Calibration Procedure

1. **Prepare Test Targets**
   - Use known objects: penny, nickel, pull tab, bottle cap
   - Bury at known depths: 2", 4", 6", 8"
   - Use different soil conditions if possible

2. **Collect Data**
   ```kotlin
   val depthFactor = depthEstimator.calibrate(
       analysis = toneAnalysis,
       vdiResult = vdi,
       actualDepthInches = 4.0
   )
   ```

3. **Build Calibration Table**
   - Record `depthFactor` for each target at each depth
   - Look for patterns in your soil

4. **Adjust Thresholds**
   - Edit `DepthEstimator.kt` lines 30-33
   - Modify `SURFACE_THRESHOLD`, `SHALLOW_THRESHOLD`, etc.
   - Based on your calibration data

### Example Calibration Results

```
Target: Copper Penny (VDI 78)
Depth: 2" → Factor: 1.05 → SURFACE ✓
Depth: 4" → Factor: 2.15 → SHALLOW ✓
Depth: 6" → Factor: 3.92 → MEDIUM ✓
Depth: 8" → Factor: 6.45 → DEEP ✓

Target: Aluminum Foil (VDI 42)
Depth: 2" → Factor: 0.95 → SURFACE ✓
Depth: 4" → Factor: 2.35 → SHALLOW ✓
Depth: 6" → Factor: 4.22 → MEDIUM ✓
(Too weak beyond 6")
```

## Technical Details

### Algorithm

```
1. Check minimum amplitude (0.02 threshold)
   - Below this: return VERY_DEEP with low confidence

2. Calculate frequency ratio
   - Compare low 1/3 frequencies vs high 1/3 frequencies
   - Clamp to reasonable range [0.8, 2.5]

3. Get size normalization from VDI
   - HIGH_CONDUCTOR (copper/silver): 1.5×
   - MID_CONDUCTOR (brass/zinc): 1.2×
   - GOLD_RANGE (jewelry): 1.0×
   - LOW_CONDUCTOR (foil/aluminum): 0.8×
   - FERROUS (iron/steel): 1.3×

4. Calculate depth factor
   depthFactor = (1/amplitude^0.35) × freqRatio / sizeNormalization

5. Classify into category
   - < 1.2: SURFACE
   - < 2.2: SHALLOW
   - < 3.8: MEDIUM
   - < 6.0: DEEP
   - >= 6.0: VERY_DEEP

6. Calculate confidence
   confidence = (VDI_conf × 0.6) + (amplitude × 2.0 × 0.4)
   - Strong signal = higher confidence
   - Weak signal = lower confidence
```

### Physics Background

**Inverse Cube Law**
- EM field strength ∝ 1/r³ for small loop antennas
- This is why depth estimation is so challenging
- Small distance changes = large signal changes

**Skin Effect**
- AC current in conductor flows near surface
- Skin depth δ = √(2ρ/(ωμ)) where ω = frequency
- Higher frequencies → shallower penetration → more attenuation
- This is why we can estimate depth from frequency response

**Target Response**
- Eddy currents induced in target
- Amplitude ∝ target size, conductivity, depth
- Phase ∝ target conductivity, ferrous properties
- Multi-frequency separates these factors

## Display

### Main Display (VDI Box)
```
╔══════════════════════════════╗
║       VDI: 78 (█████████░)   ║
║ High Conductor (Cu/Ag) | High║
║ ●●○○ Medium (4-6")           ║
╚══════════════════════════════╝
```

### Technical Details
```
Depth Estimate:
Category: Medium (4-6")
Amplitude: 0.142
Factor: 3.67
Confidence: 72%
```

## Future Improvements

### Possible Enhancements

1. **Machine Learning**
   - Train on user feedback ("was this shallow/deep?")
   - Build soil-specific models
   - Improve size estimation from multi-frequency response

2. **Coil Height Compensation**
   - Use accelerometer to detect coil height
   - Adjust depth estimate accordingly

3. **Soil Type Database**
   - Store calibration per location
   - GPS-tagged soil profiles
   - Community-shared calibration data

4. **Real-time Calibration**
   - Detect when target is dug up
   - User confirms depth
   - Update model automatically

## Code References

- `DepthEstimator.kt` - Main depth estimation class
- `IQDemodulator.kt:236` - Depth calculation called
- `MainActivity.kt:527` - Depth display in UI
- `VDICalculator.kt:16` - Depth added to VDI result

## Recommendations

✅ **Do:**
- Treat depth as approximate category, not exact measurement
- Calibrate with test targets in your soil
- Use in combination with VDI and signal strength
- Trust high-confidence readings more than low

❌ **Don't:**
- Expect precise depth measurements without calibration
- Ignore confidence scores
- Use on highly mineralized ground without ground balance
- Trust readings on mixed targets (multiple objects)

## Accuracy Expectations

| Condition | Expected Accuracy |
|-----------|-------------------|
| Calibrated soil, known target type | ±1 category (±2") |
| Uncalibrated, VDI identified target | ±1-2 categories (±4") |
| Unknown target, poor ground | ±2-3 categories (unreliable) |

**Remember:** Even commercial detectors claiming "exact depth" are typically only accurate to ±30-50% without calibration.
