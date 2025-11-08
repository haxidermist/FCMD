package com.example.fcmd

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

/**
 * Audio Tone Generator for VDI-based target indication
 * Maps VDI values (0-99) to musical notes
 * Implements confidence-based pulsing
 */
class AudioToneGenerator {

    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var generatorThread: Thread? = null

    // Audio parameters
    private val sampleRate = 48000
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2

    // VDI and confidence state
    private var currentVDI = 0
    private var currentConfidence = 0.0
    private var targetFrequency = 0.0
    private var isEnabled = false

    // Pulsing parameters
    private val pulseFrequency = 3.0  // 3 Hz pulse for low confidence
    private val confidenceThreshold = 0.7  // Above this = continuous, below = pulsed

    // Smoothing for frequency changes
    private var smoothedFrequency = 0.0
    private val frequencySmoothingFactor = 0.1  // Higher = faster response

    // Volume control
    private var outputVolume = 1.0  // 0.0 to 1.0

    /**
     * Musical note frequencies (pentatonic scale for pleasant sound)
     * Maps VDI 0-99 to notes spanning ~2 octaves
     * Pentatonic scale avoids dissonance
     */
    private val musicalScale = listOf(
        // Low VDI (ferrous) - Lower notes
        261.63,  // C4 (middle C)
        293.66,  // D4
        329.63,  // E4
        392.00,  // G4
        440.00,  // A4

        // Mid VDI - Mid notes
        523.25,  // C5
        587.33,  // D5
        659.25,  // E5
        783.99,  // G5
        880.00,  // A5

        // High VDI (high conductor) - Higher notes
        1046.50, // C6
        1174.66, // D6
        1318.51, // E6
        1567.98, // G6
        1760.00  // A6
    )

    companion object {
        private const val VDI_MIN = 0
        private const val VDI_MAX = 99
    }

    /**
     * Start the audio tone generator
     */
    fun start() {
        if (isRunning) return

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        isRunning = true
        smoothedFrequency = vdiToFrequency(currentVDI)

        audioTrack?.play()

        // Start generation thread
        generatorThread = Thread {
            generateAudio()
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Stop the audio tone generator
     */
    fun stop() {
        isRunning = false
        generatorThread?.interrupt()
        generatorThread?.join(1000)

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    /**
     * Update VDI and confidence for audio feedback
     */
    fun updateVDI(vdi: Int, confidence: Double) {
        currentVDI = vdi.coerceIn(VDI_MIN, VDI_MAX)
        currentConfidence = confidence.coerceIn(0.0, 1.0)
        targetFrequency = vdiToFrequency(currentVDI)
    }

    /**
     * Enable/disable audio feedback
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    /**
     * Set output volume (0.0 to 1.0)
     */
    fun setVolume(volume: Double) {
        outputVolume = volume.coerceIn(0.0, 1.0)
    }

    /**
     * Map VDI (0-99) to musical frequency
     * Uses pentatonic scale for pleasant sound
     */
    private fun vdiToFrequency(vdi: Int): Double {
        // Map VDI to scale index
        val normalizedVDI = vdi.toDouble() / VDI_MAX
        val scaleIndex = (normalizedVDI * (musicalScale.size - 1)).toInt()
            .coerceIn(0, musicalScale.size - 1)

        // Interpolate between notes for smooth transitions
        val exactIndex = normalizedVDI * (musicalScale.size - 1)
        val lowerIndex = exactIndex.toInt().coerceIn(0, musicalScale.size - 1)
        val upperIndex = (lowerIndex + 1).coerceIn(0, musicalScale.size - 1)
        val fraction = exactIndex - lowerIndex

        return musicalScale[lowerIndex] * (1.0 - fraction) +
               musicalScale[upperIndex] * fraction
    }

    /**
     * Generate audio samples
     */
    private fun generateAudio() {
        val chunkSize = 512  // Small chunks for responsive updates
        val buffer = ShortArray(chunkSize)
        var phase = 0.0
        var pulsePhase = 0.0

        while (isRunning && !Thread.currentThread().isInterrupted) {
            try {
                // Smooth frequency transitions
                smoothedFrequency += (targetFrequency - smoothedFrequency) * frequencySmoothingFactor

                // Calculate pulse envelope (for low confidence)
                val pulsing = currentConfidence < confidenceThreshold
                val pulsePhaseIncrement = 2.0 * PI * pulseFrequency / sampleRate

                // Generate audio samples
                for (i in 0 until chunkSize) {
                    // Calculate pulse amplitude (3 Hz sine wave envelope)
                    val pulseAmplitude = if (pulsing && isEnabled) {
                        // Pulse between 0 and 1 at 3 Hz
                        (sin(pulsePhase) * 0.5 + 0.5).coerceIn(0.0, 1.0)
                    } else if (isEnabled) {
                        1.0  // Continuous tone for high confidence
                    } else {
                        0.0  // Silent when disabled
                    }

                    // Generate sine wave
                    val phaseIncrement = 2.0 * PI * smoothedFrequency / sampleRate
                    val amplitude = if (isEnabled && currentConfidence > 0.1) {
                        // Scale amplitude by confidence (minimum 20% for audibility)
                        val baseAmplitude = 0.2 + 0.8 * currentConfidence
                        baseAmplitude * pulseAmplitude * outputVolume * 0.3  // 30% max volume to avoid clipping
                    } else {
                        0.0
                    }

                    // Generate sample with smooth envelope
                    val sample = sin(phase) * amplitude * Short.MAX_VALUE
                    buffer[i] = sample.toInt().toShort()

                    // Advance phase
                    phase += phaseIncrement
                    if (phase >= 2.0 * PI) {
                        phase -= 2.0 * PI
                    }

                    // Advance pulse phase
                    if (pulsing) {
                        pulsePhase += pulsePhaseIncrement
                        if (pulsePhase >= 2.0 * PI) {
                            pulsePhase -= 2.0 * PI
                        }
                    }
                }

                // Write to audio track
                audioTrack?.write(buffer, 0, chunkSize)

            } catch (e: Exception) {
                if (isRunning) {
                    e.printStackTrace()
                }
                break
            }
        }
    }

    /**
     * Check if generator is running
     */
    fun isRunning(): Boolean = isRunning

    /**
     * Get current frequency for display
     */
    fun getCurrentFrequency(): Double = smoothedFrequency

    /**
     * Get note name for current VDI
     */
    fun getNoteName(vdi: Int): String {
        val noteNames = listOf(
            "C4", "D4", "E4", "G4", "A4",
            "C5", "D5", "E5", "G5", "A5",
            "C6", "D6", "E6", "G6", "A6"
        )

        val normalizedVDI = vdi.coerceIn(VDI_MIN, VDI_MAX).toDouble() / VDI_MAX
        val scaleIndex = (normalizedVDI * (noteNames.size - 1)).toInt()
            .coerceIn(0, noteNames.size - 1)

        return noteNames[scaleIndex]
    }

    // State for sample generation (used when generating directly without AudioTrack)
    private var directPhase = 0.0
    private var directPulsePhase = 0.0
    private var directSmoothedFrequency = 0.0

    /**
     * Generate target audio samples for mixing into stereo output
     * Used by AudioEngine for right channel output
     */
    fun generateTargetAudioSamples(numSamples: Int): FloatArray {
        val samples = FloatArray(numSamples)

        // Initialize smoothed frequency on first call
        if (directSmoothedFrequency == 0.0) {
            directSmoothedFrequency = vdiToFrequency(currentVDI)
        }

        // Smooth frequency transitions
        directSmoothedFrequency += (targetFrequency - directSmoothedFrequency) * frequencySmoothingFactor

        // Calculate pulse envelope (for low confidence)
        val pulsing = currentConfidence < confidenceThreshold
        val pulsePhaseIncrement = 2.0 * PI * pulseFrequency / sampleRate

        // Generate audio samples
        for (i in 0 until numSamples) {
            // Calculate pulse amplitude (3 Hz sine wave envelope)
            val pulseAmplitude = if (pulsing && isEnabled) {
                // Pulse between 0 and 1 at 3 Hz
                (sin(directPulsePhase) * 0.5 + 0.5).coerceIn(0.0, 1.0)
            } else if (isEnabled) {
                1.0  // Continuous tone for high confidence
            } else {
                0.0  // Silent when disabled
            }

            // Generate sine wave
            val phaseIncrement = 2.0 * PI * directSmoothedFrequency / sampleRate
            val amplitude = if (isEnabled && currentConfidence > 0.1) {
                // Scale amplitude by confidence (minimum 20% for audibility)
                val baseAmplitude = 0.2 + 0.8 * currentConfidence
                baseAmplitude * pulseAmplitude * outputVolume * 0.3  // 30% max volume to avoid clipping
            } else {
                0.0
            }

            // Generate sample
            samples[i] = (sin(directPhase) * amplitude).toFloat()

            // Advance phase
            directPhase += phaseIncrement
            if (directPhase >= 2.0 * PI) {
                directPhase -= 2.0 * PI
            }

            // Advance pulse phase
            if (pulsing) {
                directPulsePhase += pulsePhaseIncrement
                if (directPulsePhase >= 2.0 * PI) {
                    directPulsePhase -= 2.0 * PI
                }
            }
        }

        return samples
    }
}
