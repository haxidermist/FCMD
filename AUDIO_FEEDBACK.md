# Audio Feedback System

## Overview
The metal detector now includes a sophisticated audio feedback system that provides real-time target indication through musical tones. The system automatically routes audio to Bluetooth devices when available.

## Features

### 1. VDI-to-Tone Mapping
- **Musical Scale**: Uses a 15-note pentatonic scale for pleasant, non-dissonant audio
- **Frequency Range**: 261 Hz (C4) to 1760 Hz (A6) - spanning 2+ octaves
- **VDI Mapping**:
  - VDI 0-20 (Ferrous): Low notes (C4-E4) ~260-330 Hz
  - VDI 21-50 (Low-Mid Conductor): Mid notes (G4-C5) ~390-520 Hz
  - VDI 51-70 (Gold Range): Upper-mid notes (D5-A5) ~580-880 Hz
  - VDI 71-99 (High Conductor): High notes (C6-A6) ~1000-1760 Hz

### 2. Confidence-Based Pulsing
- **High Confidence (>70%)**: Continuous tone - solid, steady signal
- **Low Confidence (<70%)**: Pulsed at 3 Hz - intermittent beeping
- **Amplitude Scaling**: Volume increases with confidence (minimum 20% for audibility)

### 3. Bluetooth Audio Support
- **Automatic Routing**: Audio automatically routes to connected Bluetooth headsets/speakers
- **Device Detection**: Shows connected Bluetooth audio device name in UI
- **Fallback**: Uses phone speaker if no Bluetooth device connected
- **A2DP Support**: Uses high-quality stereo Bluetooth audio (A2DP profile)

### 4. Smooth Transitions
- **Frequency Smoothing**: Gradual frequency changes prevent jarring jumps
- **Envelope Shaping**: Smooth attack/release for pleasant audio
- **Real-time Updates**: 40 Hz update rate matches VDI calculation rate

## Musical Note Scale

The pentatonic scale used:

| VDI Range | Note | Frequency |
|-----------|------|-----------|
| 0-6       | C4   | 261.63 Hz |
| 7-13      | D4   | 293.66 Hz |
| 14-20     | E4   | 329.63 Hz |
| 21-27     | G4   | 392.00 Hz |
| 28-34     | A4   | 440.00 Hz |
| 35-41     | C5   | 523.25 Hz |
| 42-48     | D5   | 587.33 Hz |
| 49-55     | E5   | 659.25 Hz |
| 56-62     | G5   | 783.99 Hz |
| 63-69     | A5   | 880.00 Hz |
| 70-76     | C6   | 1046.50 Hz |
| 77-83     | D6   | 1174.66 Hz |
| 84-90     | E6   | 1318.51 Hz |
| 91-96     | G6   | 1567.98 Hz |
| 97-99     | A6   | 1760.00 Hz |

## Usage

### UI Controls
1. **Enable Switch**: Turn audio feedback on/off
2. **Volume Slider**: Control audio feedback volume (0-100%)
3. **Audio Routing Display**: Shows current output device (Phone/BT)

### Operation
1. Start the detector (press START button)
2. Enable audio feedback switch
3. If using Bluetooth:
   - Connect Bluetooth headset/speaker via phone settings
   - Audio will automatically route to BT device
   - Status shows "BT: [Device Name]"
4. Adjust volume as needed
5. Sweep detector over targets:
   - Low tones = ferrous metals (iron, steel)
   - Mid tones = mid conductors (brass, zinc, pull tabs)
   - High tones = good conductors (copper, silver)
   - Solid tone = high confidence target
   - Pulsing tone = uncertain/weak signal

## Technical Details

### AudioToneGenerator.kt
- Real-time sine wave synthesis
- Sample rate: 48 kHz
- Buffer size: 512 samples for low latency
- Thread priority: MAX_PRIORITY for consistent audio
- Volume control: 0-100% (internally limited to 30% to prevent clipping)

### BluetoothAudioManager.kt
- Detects paired Bluetooth audio devices
- Monitors connection state changes
- Provides device type classification (headset/speaker)
- Handles audio routing via AudioManager
- Supports Android 12+ Bluetooth permissions

### Integration
- VDI updates trigger tone updates in real-time
- Audio feedback independent of TX signal
- Separate volume controls for TX and audio feedback
- Clean shutdown on app exit or pause

## Permissions Required
- `BLUETOOTH_CONNECT` (Android 12+): Required for Bluetooth device access
- `MODIFY_AUDIO_SETTINGS`: Required for audio routing control
- Older Android versions use legacy Bluetooth permissions

## Performance
- Audio generation thread: High priority for consistent timing
- CPU usage: Minimal (~1-2% on modern devices)
- Latency: ~50ms from VDI calculation to audio update
- Battery impact: Low (audio generation is efficient)

## Future Enhancements
- Volume ducking (reduce volume when not detecting)
- Custom tone profiles (save preferred settings)
- Multi-tone alerts (chirps for specific target types)
- Audio recording of target signals
- Wireless latency compensation
