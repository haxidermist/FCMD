# Contributing to FCMD

First off, thank you for considering contributing to FCMD! It's people like you that make this project better for the metal detecting and maker communities.

## Code of Conduct

This project adheres to a simple code of conduct: **Be respectful, be constructive, and help make metal detection technology accessible to everyone.**

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the existing issues to avoid duplicates. When creating a bug report, include:

- **Clear title and description**
- **Steps to reproduce** the issue
- **Expected vs actual behavior**
- **Device information**: Make, model, Android version
- **App version** and build number
- **Log output** if relevant (use `adb logcat`)

**Example bug report:**

```markdown
**Title**: VDI calculation crashes on single-frequency mode

**Description**: App crashes when VDI calculation is called with only 1 frequency

**Steps to reproduce**:
1. Set tone count to 1
2. Detect a target
3. App crashes

**Expected**: VDI should return UNKNOWN for single frequency
**Actual**: NullPointerException in VDICalculator.kt:84

**Device**: Samsung Galaxy S21, Android 13
**App version**: 1.0.0-debug

**Logcat**:
```
[paste logcat here]
```
```

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, include:

- **Clear use case**: Why is this enhancement needed?
- **Describe the solution**: How should it work?
- **Alternatives considered**: Other approaches you've thought about
- **Additional context**: Screenshots, mockups, or examples

### Pull Requests

1. **Fork the repo** and create your branch from `master`
2. **Make your changes**:
   - Add tests if applicable
   - Update documentation
   - Follow the coding style
3. **Test thoroughly** on multiple devices if possible
4. **Commit with clear messages**
5. **Push to your fork** and submit a pull request

**Good commit messages:**
```
Add depth calibration mode for user-specific soil

- Implement calibration UI in MainActivity
- Add calibration data storage in SharedPreferences
- Update DepthEstimator to use calibration factors
- Add calibration guide to documentation

Fixes #42
```

**Bad commit messages:**
```
fixed stuff
update code
```

## Development Setup

### Prerequisites

- **Android Studio**: Arctic Fox or newer
- **JDK**: 11 or newer
- **Android SDK**: API 21+ (Android 5.0+)
- **Git**: For version control
- **Device or Emulator**: For testing (physical device recommended)

### Getting Started

1. Clone your fork:
```bash
git clone https://github.com/[your-username]/FCMD.git
cd FCMD
```

2. Open in Android Studio

3. Sync Gradle files

4. Run on device:
```bash
./gradlew installDebug
```

### Project Structure

```
FCMD/
├── app/src/main/java/com/example/fcmd/
│   ├── AudioEngine.kt              # Audio I/O (modify for new audio features)
│   ├── IQDemodulator.kt            # DSP core (modify for new demod algorithms)
│   ├── VDICalculator.kt            # Discrimination (modify for better VDI)
│   ├── GroundBalanceManager.kt     # Ground balance (add new GB modes)
│   ├── DepthEstimator.kt           # Depth estimation (improve accuracy)
│   └── MainActivity.kt             # UI (add new controls)
└── app/src/main/res/layout/        # UI layouts
```

## Coding Style

### Kotlin Style Guide

Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// Good
class IQDemodulator(
    private val sampleRate: Int,
    private val frequency: Double
) {
    fun analyze(samples: FloatArray): ToneAnalysis {
        // Clear, descriptive variable names
        val amplitude = calculateAmplitude(samples)
        val phase = calculatePhase(samples)

        return ToneAnalysis(frequency, amplitude, phase)
    }

    private fun calculateAmplitude(samples: FloatArray): Double {
        // Implementation with comments explaining WHY, not WHAT
        return sqrt(i * i + q * q) * 2.0  // *2 due to mixing loss
    }
}

// Bad
class IQD(s:Int,f:Double){
fun a(x:FloatArray)=ToneAnalysis(f,sqrt(x[0]*x[0]+x[1]*x[1]),0.0)
}
```

### Documentation

- **Public APIs**: Always document with KDoc
- **Complex algorithms**: Explain the theory and cite sources
- **Performance-critical code**: Document computational complexity

```kotlin
/**
 * Calculate VDI (Visual Discrimination Indicator) from multi-frequency IQ analysis.
 *
 * Uses phase slope across frequencies as primary discriminator:
 * - Ferrous metals: steep negative slope (< -3 deg/kHz)
 * - Non-ferrous metals: flat slope, classified by conductivity
 *
 * @param analysis List of ToneAnalysis from IQ demodulator
 * @return VDIResult with classification and confidence
 *
 * @see calculatePhaseSlope for phase slope calculation
 * @see calculateConductivityIndex for conductivity measurement
 */
fun calculateVDI(analysis: List<ToneAnalysis>): VDIResult {
    // Implementation
}
```

### Performance Guidelines

- **Avoid allocations in audio callback**: Pre-allocate buffers
- **Prefer primitive types**: Use `FloatArray` not `List<Float>`
- **Profile before optimizing**: Use Android Profiler to find actual bottlenecks
- **Test on low-end devices**: Not just flagship phones

```kotlin
// Good: Pre-allocated buffer, no allocations in loop
class AudioEngine {
    private val buffer = ShortArray(bufferSize)

    private fun playbackLoop() {
        while (isPlaying.get()) {
            generateSamples(buffer)  // Fills pre-allocated buffer
            audioTrack?.write(buffer, 0, buffer.size)
        }
    }
}

// Bad: Allocates new array every iteration
private fun playbackLoop() {
    while (isPlaying.get()) {
        val buffer = ShortArray(bufferSize)  // GC pressure!
        audioTrack?.write(buffer, 0, buffer.size)
    }
}
```

## Testing

### Manual Testing Checklist

Before submitting a PR, test:

- [ ] App starts without crashes
- [ ] Audio engine starts/stops cleanly
- [ ] No buffer underruns (check logcat)
- [ ] VDI calculation works for all tone counts (1-24)
- [ ] Ground balance modes work correctly
- [ ] Settings persist across app restarts
- [ ] No memory leaks (use Android Profiler)
- [ ] Works on Android 5.0+ (test on old device if possible)

### Test Targets

If modifying VDI or depth estimation, test with known targets:

- Copper penny (VDI ~78)
- Zinc penny (VDI ~60)
- Aluminum can (VDI ~45)
- Iron nail (VDI ~10-20)
- Gold ring if available (VDI ~55-65)

### Automated Tests

Add unit tests for:
- DSP algorithms (IQ demodulation, filtering)
- VDI calculation edge cases
- Ground balance math

```kotlin
@Test
fun testIQDemodulation_singleTone_correctAmplitude() {
    val demod = SingleToneDemodulator(1000.0, 44100)
    val testSignal = generateSinWave(1000.0, 44100, amplitude = 1.0)

    val result = demod.analyze(testSignal)

    assertEquals(1.0, result.amplitude, 0.01)
}
```

## Areas for Contribution

### High Priority

- **Performance optimization**: Reduce CPU usage, improve battery life
- **VDI accuracy**: Better discrimination algorithms
- **Depth estimation**: Improve calibration and accuracy
- **UI/UX**: Make app more intuitive for beginners

### Medium Priority

- **Documentation**: Tutorials, videos, coil construction guides
- **Testing**: Add unit tests, integration tests
- **Localization**: Translate UI to other languages
- **Accessibility**: Screen reader support, larger fonts

### Advanced Projects

- **Machine learning**: Train ML model on labeled target data
- **3D imaging**: Implement advanced target sizing
- **Multi-coil support**: Simultaneous RX from multiple coils
- **Cloud sync**: Share calibration data across devices

## Hardware Contributions

If you design improved coils or amplifiers:

1. Document thoroughly (schematic, BOM, construction guide)
2. Test and provide measurements (impedance, frequency response)
3. Add to `/hardware` directory
4. Submit PR with documentation

## Documentation Contributions

- **Fix typos/errors**: Small fixes are welcome!
- **Add examples**: Code snippets, use cases
- **Write tutorials**: "How to build a coil", "Calibrating VDI", etc.
- **Create diagrams**: Visual explanations of DSP concepts

## Questions?

- **Open an issue** for discussion
- **Join GitHub Discussions** for general questions
- **Email**: [maintainer-email]

## Recognition

Contributors will be acknowledged in:
- README.md contributors section
- Release notes for their contributions
- About screen in the app (for major contributors)

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

**Thank you for making FCMD better!** 🎉
