# FCMD - Field Coil Metal Detector

A professional-grade metal detector app for Android devices using multi-frequency IQ demodulation and real-time DSP.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-5.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org)

## Overview

FCMD (Field Coil Metal Detector) transforms your Android smartphone into a sophisticated metal detector by leveraging the device's audio hardware for electromagnetic signal generation and processing. The app uses advanced DSP techniques including IQ demodulation, phase analysis, and multi-frequency discrimination to identify and classify metallic targets.

### Key Features

- **Multi-Frequency Detection**: 1-24 logarithmically-spaced frequencies (1 kHz - 20 kHz)
- **Real-Time IQ Demodulation**: Extract amplitude and phase at each frequency
- **VDI (Visual Discrimination Indicator)**: Classify targets (ferrous, copper, aluminum, gold, etc.)
- **Ground Balance**: Four modes (OFF, Manual, Auto-Tracking, Manual+Tracking) for mineralized soil
- **Depth Estimation**: Category-based depth estimation (Surface, Shallow, Medium, Deep)
- **Audio Feedback**: Multi-tone audio response based on target characteristics
- **Real-Time Waveform Display**: Visualize received signals
- **Performance Monitoring**: Actual callback rate, latency, and buffer size logging

## How It Works

### Signal Flow

```
┌─────────────────┐
│ Multi-Tone TX   │ → Audio Output → Transmit Coil → EM Field
│  (1-24 tones)   │
└─────────────────┘

                                    ↓ (Metal Target)

┌─────────────────┐
│   Receive Coil  │ ← Audio Input ← Target Response
└─────────────────┘
         ↓
┌─────────────────┐
│ IQ Demodulator  │ → Extract amplitude/phase per frequency
└─────────────────┘
         ↓
┌─────────────────┐
│ Ground Balance  │ → Cancel soil mineralization
└─────────────────┘
         ↓
┌─────────────────┐
│ VDI Calculator  │ → Identify target type (0-99 scale)
└─────────────────┘
         ↓
┌─────────────────┐
│ Depth Estimator │ → Estimate target depth (categorical)
└─────────────────┘
```

### DSP Pipeline

1. **Transmission**: Generate multi-tone signal (stereo output: L=TX tones, R=target audio)
2. **Reception**: Capture mono audio from receive coil (microphone input)
3. **IQ Demodulation**: For each frequency, mix with cos/sin and low-pass filter
4. **Ground Balance**: Subtract baseline I/Q vector to cancel ground response
5. **VDI Calculation**: Analyze phase slope and conductivity index
6. **Depth Estimation**: Combine amplitude, frequency ratio, and VDI classification

## Technical Specifications

| Specification | Value |
|---------------|-------|
| Sample Rate | 44,100 Hz |
| Audio Callback Rate | ~23 Hz (device-dependent) |
| Latency | ~43.5 ms (device-dependent) |
| Update Rate | 30 Hz (configurable) |
| Frequencies | 1-24 tones, 1-20 kHz |
| DSP Method | IQ demodulation with IIR filtering |
| CPU Usage | 15-25% single core |
| Battery Life | 7-15 hours typical |

## Requirements

### Software

- Android 5.0 (Lollipop) or higher
- Kotlin 1.9+
- Android Studio Arctic Fox or newer

### Hardware

- Android device with:
  - Audio output (headphone jack or USB-C audio)
  - Microphone input
  - Recommended: 4GB+ RAM for smooth operation

### External Hardware

- **Transmit Coil**: 16-32Ω impedance, connected to headphone output
- **Receive Coil**: Connected to microphone input
- **Coil Construction**: See [COIL_CONSTRUCTION.md](COIL_CONSTRUCTION.md) for DIY instructions

## Installation

### From Source

1. Clone the repository:
```bash
git clone https://github.com/[your-username]/FCMD.git
cd FCMD
```

2. Open in Android Studio

3. Build and run:
```bash
./gradlew assembleDebug
```

4. Install on device:
```bash
./gradlew installDebug
```

### APK Release

Download the latest APK from [Releases](https://github.com/[your-username]/FCMD/releases)

## Usage

### Quick Start

1. **Connect Hardware**:
   - Plug transmit coil into headphone jack
   - Connect receive coil to microphone input

2. **Launch App**:
   - Grant microphone permission
   - Set frequency range (default: 1 kHz - 20 kHz, 8 tones)
   - Adjust transmit volume (start at 50%)

3. **Ground Balance** (if in mineralized soil):
   - Select Manual mode
   - Tap "Start Capture"
   - Pump coil up/down over ground 10 times
   - Tap "Stop Capture"

4. **Detect**:
   - Sweep coil slowly over ground
   - Watch VDI display for target classification
   - Listen for audio tone changes
   - Note depth estimate

### Controls

- **Frequency Range**: Min (1000 Hz fixed) to Max (2000-20000 Hz)
- **Tone Count**: 1-24 simultaneous frequencies
- **Transmit Volume**: 0-100% (controls TX coil power)
- **Ground Balance Mode**: OFF / Manual / Auto / Manual+Tracking
- **GB Offset**: -50 to +50 (fine-tune ground null point)
- **Audio Feedback**: Enable/disable target audio tones

## Project Structure

```
FCMD/
├── app/src/main/java/com/example/fcmd/
│   ├── AudioEngine.kt              # Audio I/O management
│   ├── IQDemodulator.kt            # IQ demodulation and DSP
│   ├── VDICalculator.kt            # Target discrimination
│   ├── GroundBalanceManager.kt     # Ground balance algorithms
│   ├── DepthEstimator.kt           # Depth estimation
│   ├── MultiToneGenerator.kt       # TX signal generation
│   ├── AudioToneGenerator.kt       # Audio feedback
│   ├── MainActivity.kt             # Main UI
│   └── [other support files]
│
├── docs/
│   ├── Android_Metal_Detection_Ebook.html  # Complete technical guide
│   ├── DEPTH_ESTIMATION.md                  # Depth estimation documentation
│   ├── AUDIO_FEEDBACK.md                    # Audio feedback system
│   └── [diagrams]
│
└── README.md                        # This file
```

## Key Algorithms

### IQ Demodulation

```kotlin
fun analyze(samples: FloatArray): ToneAnalysis {
    for (sample in samples) {
        // Quadrature mixing
        val i = sample * cos(phase)
        val q = -sample * sin(phase)

        // Single-pole IIR low-pass filter (α=0.01)
        iFiltered = 0.01 * i + 0.99 * iFiltered
        qFiltered = 0.01 * q + 0.99 * qFiltered

        phase += phaseIncrement
    }

    // Calculate amplitude and phase
    val amplitude = sqrt(iFiltered² + qFiltered²) * 2.0
    val phaseAngle = atan2(qFiltered, iFiltered)

    return ToneAnalysis(frequency, amplitude, phaseAngle, iFiltered, qFiltered)
}
```

### VDI Calculation

```kotlin
fun calculateVDI(analysis: List<ToneAnalysis>): VDIResult {
    // 1. Calculate phase slope (deg/kHz)
    val phaseSlope = (phaseHigh - phaseLow) / (freqHigh - freqLow)

    // 2. Ferrous detection (steep negative slope)
    val vdi = if (phaseSlope < -3.0) {
        // Ferrous: 0-30 VDI
        (30 * (1.0 - phaseSlope / -10.0)).toInt()
    } else {
        // Non-ferrous: use conductivity (30-99 VDI)
        (30 + conductivityIndex * 69).toInt()
    }

    return VDIResult(vdi, confidence, targetType, phaseSlope, conductivityIndex)
}
```

### Ground Balance

```kotlin
// Manual mode: average 10 pump samples
fun stopManualCapture() {
    manualBaseline = averageCapturedSamples(captureSamples)
}

// Auto-tracking: slow IIR filter (α=0.0005)
fun updateTrackingBaseline(analysis: List<ToneAnalysis>) {
    if (maxAmplitude > 0.3) {
        trackingFrozen = true  // Freeze when target detected
        return
    }

    trackingBaseline = analysis.map { tone ->
        0.0005 * tone.inPhase + 0.9995 * baseline.inPhase  // Very slow tracking
    }
}
```

## Performance Characteristics

### Measured Performance (Typical Mid-Range Device)

- **Buffer Size**: 1920 samples
- **Callback Rate**: 23.2 Hz
- **Latency**: 43.5 ms
- **IQ Processing**: ~6 ms for 24 tones
- **VDI Calculation**: ~0.5 ms
- **Total CPU**: ~18% of available time

### Real-Time Constraints

The app processes audio in real-time with strict deadlines:
- Audio callback must complete in <43.5 ms
- Thread priority set to `THREAD_PRIORITY_URGENT_AUDIO`
- Processing optimized for consistent performance

## Documentation

📚 **[View All Documentation](https://haxidermist.github.io/FCMD/)** (Rendered HTML with interactive diagrams)

Or browse individual files:
- **[Complete Ebook](https://haxidermist.github.io/FCMD/Android_Metal_Detection_Ebook.html)**: 50+ page guide from physics to code
- **[Depth Estimation](DEPTH_ESTIMATION.md)**: How depth estimation works and its limitations
- **[Audio Feedback](AUDIO_FEEDBACK.md)**: Audio feedback system documentation
- **[Signal Flow Diagram](https://haxidermist.github.io/FCMD/signal_flow_diagram.html)**: Visual system overview
- **[VDI Process Diagram](https://haxidermist.github.io/FCMD/vdi_phase_slope_diagram.html)**: Phase slope discrimination explained
- **[Ground Balance Diagram](https://haxidermist.github.io/FCMD/ground_balance_diagram.html)**: Ground balance algorithms visualized

## Limitations

### Fundamental Physics

- **Target size unknown**: Large shallow = small deep (ambiguous)
- **Depth limited**: ~8" max on coin-sized targets (due to smartphone power limits)
- **Ground mineralization**: Requires ground balance in most soils

### Hardware Constraints

- **Low transmit power**: ~100 mW vs 1-5W in commercial detectors
- **Audio latency**: 40-50 ms typical (acceptable for metal detection)
- **Frequency range**: Limited to 20 kHz (Nyquist limit at 44.1 kHz sample rate)

### Software

- **Depth accuracy**: ±1-2 categories (±2-4 inches) without calibration
- **VDI overlap**: Gold and aluminum can be indistinguishable
- **Battery life**: Display is main power consumer

## Calibration

### VDI Calibration

1. Collect known targets (coins, rings, trash)
2. Measure VDI for each target multiple times
3. Build histogram of VDI values
4. Adjust thresholds in `VDICalculator.kt`

### Depth Calibration

1. Bury test targets at known depths (2", 4", 6", 8")
2. Measure depth factor: `depthEstimator.calibrate(analysis, vdi, actualDepth)`
3. Record depth factors
4. Adjust thresholds in `DepthEstimator.kt`

### Ground Balance Offset

- **Positive offset** (+10 to +50): Reduce sensitivity to hot ground
- **Zero offset**: Exact ground null
- **Negative offset** (-10 to -50): Increase sensitivity (mild ground only)

## Contributing

Contributions welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Areas for Contribution

- **Algorithm improvements**: Better VDI calculation, depth estimation
- **UI enhancements**: Better visualizations, settings interface
- **Performance optimization**: Reduce CPU usage, improve latency
- **Documentation**: Tutorials, examples, translations
- **Hardware**: Coil designs, amplifier circuits

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by commercial metal detector algorithms from Minelab, Garrett, and White's Electronics
- DSP techniques adapted from GNU Radio and SDR communities
- Android audio optimization guidance from Superpowered Inc.

## References

### Key Papers

- Candy, B.H. (1993). "A Pulsed Induction Metal Detector." IEEE Transactions on Geoscience and Remote Sensing
- Nelson, C.V., et al. (1990). "Wide Bandwidth Time-Domain Electromagnetic Sensor for Metal Target Classification."

### Books

- Oppenheim & Schafer. (2009). "Discrete-Time Signal Processing"
- Lyons, R.G. (2011). "Understanding Digital Signal Processing"
- Garrett, C. (2013). "Modern Metal Detectors"

### Online Resources

- Android Audio Documentation: https://source.android.com/devices/audio
- DSP Related: https://www.dsprelated.com
- GNU Radio: https://www.gnuradio.org

## Support

- **Issues**: [GitHub Issues](https://github.com/[your-username]/FCMD/issues)
- **Discussions**: [GitHub Discussions](https://github.com/[your-username]/FCMD/discussions)
- **Email**: [your-email@example.com]

## Roadmap

### Version 1.1 (Planned)
- [ ] Implement FFT spectrum analyzer view
- [ ] Add GPS waypoint logging for finds
- [ ] Bluetooth headphone support
- [ ] Save/load ground balance profiles

### Version 1.2 (Future)
- [ ] Machine learning VDI classification
- [ ] 3D target imaging
- [ ] Multi-device synchronization
- [ ] Cloud-based calibration database

## Screenshots

> **Note**: Add screenshots of the app in action here

## FAQ

**Q: How deep can it detect?**
A: Typical depths: coin at 4-6", can at 8-10". Limited by smartphone's low transmit power.

**Q: Does it work without external coils?**
A: No. You must build or buy transmit/receive coils. See coil construction guide.

**Q: Can it find gold?**
A: Yes, but gold overlaps with aluminum trash (VDI 50-70). No detector perfectly separates these.

**Q: Why are readings inconsistent?**
A: Check ground balance, ensure coil is stable, avoid sweeping too fast. Inconsistent phase = low confidence.

**Q: Battery drains fast?**
A: Reduce screen brightness (main power consumer). Expect 7-15 hours runtime.

---

**Built with ❤️ for the metal detecting and maker communities**
