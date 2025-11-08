package com.example.fcmd

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

class AudioEngine(
    private val audioManager: AudioManager,
    private val bufferSizeMultiplier: Int = 1  // Reduced from 2 for lower latency and higher update rate
) {
    // Target audio generator reference (for right channel mixing)
    private var targetAudioGenerator: AudioToneGenerator? = null
    companion object {
        private const val TAG = "AudioEngine"
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_STEREO  // Stereo: L=TX R=TargetAudio
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO     // Mono: Left channel only
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Try these sample rates in order of preference
        private val PREFERRED_SAMPLE_RATES = intArrayOf(192000, 96000, 48000, 44100)
    }

    private val sampleRate: Int

    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null

    private var playbackThread: Thread? = null
    private var recordThread: Thread? = null

    private val isPlaying = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)

    private var minFrequency = 1000.0 // Hz - fixed at 1000 Hz
    private var maxFrequency = 20000.0 // Hz - adjustable from 2000 to 20000 Hz
    private var toneCount = 1 // Number of tones
    private var transmitVolume = 1.0 // 0.0 to 1.0

    // Tone generator (always used now)
    private var multiToneGenerator: MultiToneGenerator? = null

    private var waveformCallback: ((FloatArray) -> Unit)? = null
    private var spectrumCallback: ((FloatArray, Int) -> Unit)? = null
    private var dspProcessor: DspProcessor? = null

    private val minBufferSizeOut: Int
    private val minBufferSizeIn: Int

    init {
        // Use fixed sample rate of 44100 Hz
        sampleRate = 44100
        Log.d(TAG, "Using sample rate: $sampleRate Hz")

        minBufferSizeOut = AudioTrack.getMinBufferSize(
            sampleRate,
            CHANNEL_CONFIG_OUT,
            AUDIO_FORMAT
        ) * bufferSizeMultiplier

        minBufferSizeIn = AudioRecord.getMinBufferSize(
            sampleRate,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT
        ) * bufferSizeMultiplier

        Log.d(TAG, "AudioEngine initialized: SR=$sampleRate, OutBuffer=$minBufferSizeOut, InBuffer=$minBufferSizeIn")
    }

    /**
     * Get the actual sample rate being used
     */
    fun getSampleRate(): Int = sampleRate

    /**
     * Set the frequency range and tone count
     * Tones are logarithmically spaced from minFreq to maxFreq
     */
    fun setFrequencyRange(minFreq: Double, maxFreq: Double, count: Int) {
        minFrequency = minFreq.coerceIn(20.0, 20000.0)
        maxFrequency = maxFreq.coerceIn(minFrequency, 20000.0)
        toneCount = count.coerceIn(1, 24)

        Log.d(TAG, "Frequency range set: $minFrequency - $maxFrequency Hz, $toneCount tones")
        updateToneGenerator()
    }

    /**
     * Set transmit volume (0.0 to 1.0)
     * This is the only volume control - system volume is bypassed
     */
    fun setTransmitVolume(volume: Double) {
        transmitVolume = volume.coerceIn(0.0, 1.0)
        Log.d(TAG, "Transmit volume set to $transmitVolume")
    }

    /**
     * Update the tone generator based on current frequency range and tone count
     */
    private fun updateToneGenerator() {
        if (toneCount == 1) {
            // Single tone at minimum frequency (1000 Hz)
            multiToneGenerator = MultiToneGenerator(sampleRate, listOf(minFrequency))
        } else {
            // Multiple tones logarithmically spaced from min to max frequency
            multiToneGenerator = MultiToneGenerator.createLogSpaced(minFrequency, maxFrequency, toneCount, sampleRate)
        }
        Log.d(TAG, "Tone generator updated: $toneCount tones, range=$minFrequency-$maxFrequency Hz")
    }

    /**
     * Get current list of frequencies being generated
     */
    fun getFrequencies(): List<Double> {
        return multiToneGenerator?.frequencies ?: emptyList()
    }

    /**
     * Set callback for waveform display updates
     */
    fun setWaveformCallback(callback: (FloatArray) -> Unit) {
        waveformCallback = callback
    }

    /**
     * Set callback for spectrum display updates (transmit signal)
     */
    fun setSpectrumCallback(callback: (FloatArray, Int) -> Unit) {
        spectrumCallback = callback
    }

    /**
     * Set DSP processor for real-time audio processing
     */
    fun setDspProcessor(processor: DspProcessor?) {
        dspProcessor = processor
        Log.d(TAG, "DSP processor set: ${processor?.javaClass?.simpleName ?: "none"}")
    }

    /**
     * Set target audio generator for right channel output
     */
    fun setTargetAudioGenerator(generator: AudioToneGenerator?) {
        targetAudioGenerator = generator
        Log.d(TAG, "Target audio generator set: ${if (generator != null) "enabled" else "disabled"}")
    }

    /**
     * Start audio generation and recording
     */
    fun start(): Boolean {
        if (isPlaying.get()) {
            Log.w(TAG, "Already running")
            return false
        }

        try {
            // Initialize AudioTrack for stereo playback
            // Left channel = TX tones, Right channel = Target audio
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSizeOut)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            // Set AudioTrack volume to maximum to bypass system volume control
            audioTrack?.setVolume(1.0f)

            // Initialize AudioRecord for recording
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(CHANNEL_CONFIG_IN)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSizeIn)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return false
            }

            isPlaying.set(true)
            isRecording.set(true)

            // Initialize tone generator if not already set
            if (multiToneGenerator == null) {
                updateToneGenerator()
            }

            // Start playback thread
            playbackThread = Thread {
                playbackLoop()
            }.apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            // Start recording thread
            recordThread = Thread {
                recordLoop()
            }.apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            audioTrack?.play()
            audioRecord?.startRecording()

            Log.d(TAG, "Audio engine started successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio engine", e)
            stop()
            return false
        }
    }

    /**
     * Stop audio generation and recording
     */
    fun stop() {
        isPlaying.set(false)
        isRecording.set(false)

        try {
            playbackThread?.join(1000)
            recordThread?.join(1000)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Thread join interrupted", e)
        }

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        playbackThread = null
        recordThread = null

        dspProcessor?.reset()

        Log.d(TAG, "Audio engine stopped")
    }

    /**
     * Playback loop - generates stereo output
     * LEFT channel = TX detector tones
     * RIGHT channel = Target audio feedback
     */
    private fun playbackLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

        val bufferSize = minBufferSizeOut / 2 // 16-bit samples (stereo interleaved)
        val buffer = ShortArray(bufferSize)
        val monoFrames = bufferSize / 2 // Number of frames per channel
        val floatBuffer = FloatArray(monoFrames)
        var frameCount = 0

        Log.d(TAG, "Playback loop started (Stereo: LEFT=TX tones, RIGHT=Target audio, Tones: $toneCount)")

        while (isPlaying.get()) {
            // Generate LEFT channel - TX tones
            val leftSamples = if (multiToneGenerator != null) {
                multiToneGenerator!!.generateSamples(monoFrames)
            } else {
                FloatArray(monoFrames) { 0f }
            }

            // Generate RIGHT channel - Target audio
            val rightSamples = targetAudioGenerator?.generateTargetAudioSamples(monoFrames)
                ?: FloatArray(monoFrames) { 0f }

            // Interleave stereo: L, R, L, R, L, R...
            for (i in 0 until monoFrames) {
                // LEFT channel (TX tones)
                val leftValue = (leftSamples[i] * Short.MAX_VALUE * 0.8 * transmitVolume).toInt().toShort()
                buffer[i * 2] = leftValue
                floatBuffer[i] = leftSamples[i]

                // RIGHT channel (Target audio)
                val rightValue = (rightSamples[i] * Short.MAX_VALUE * 0.8).toInt().toShort()
                buffer[i * 2 + 1] = rightValue
            }

            // Send to spectrum analyzer every 5 frames (~10 Hz update rate)
            frameCount++
            if (frameCount >= 5 && spectrumCallback != null) {
                spectrumCallback?.invoke(floatBuffer, sampleRate)
                frameCount = 0
            }

            // Write stereo output to audio
            audioTrack?.write(buffer, 0, buffer.size)
        }

        Log.d(TAG, "Playback loop ended")
    }

    /**
     * Record loop - captures mono audio (left channel) and processes it
     */
    private fun recordLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

        val bufferSize = minBufferSizeIn / 2 // 16-bit samples
        val buffer = ShortArray(bufferSize)
        val floatBuffer = FloatArray(bufferSize)

        Log.d(TAG, "Record loop started (MONO - Left channel only), buffer size: $bufferSize samples")

        while (isRecording.get()) {
            val readSamples = audioRecord?.read(buffer, 0, buffer.size) ?: 0

            if (readSamples > 0) {
                // Convert to float for processing and display
                for (i in 0 until readSamples) {
                    floatBuffer[i] = buffer[i].toFloat() / Short.MAX_VALUE
                }

                // Apply DSP processing to mono channel
                val processed = dspProcessor?.processMono(floatBuffer.copyOf(readSamples), sampleRate)
                    ?: floatBuffer.copyOf(readSamples)

                // Duplicate mono to stereo for waveform display (L=signal, R=signal)
                val displayData = FloatArray(readSamples * 2).apply {
                    for (i in 0 until readSamples) {
                        this[i * 2] = processed[i]      // Left
                        this[i * 2 + 1] = processed[i]  // Right (duplicate)
                    }
                }

                // Send to waveform display
                waveformCallback?.invoke(displayData)
            } else if (readSamples < 0) {
                Log.e(TAG, "AudioRecord read error: $readSamples")
            }
        }

        Log.d(TAG, "Record loop ended")
    }

    /**
     * Get current status
     */
    fun isRunning(): Boolean = isPlaying.get() && isRecording.get()

    /**
     * Get audio configuration info
     */
    fun getAudioInfo(): String {
        return "Sample Rate: $sampleRate Hz\n" +
               "Frequency Range: ${minFrequency.toInt()} - ${maxFrequency.toInt()} Hz\n" +
               "Tone Count: $toneCount\n" +
               "Output Buffer: $minBufferSizeOut bytes\n" +
               "Input Buffer: $minBufferSizeIn bytes\n" +
               "DSP: ${dspProcessor?.javaClass?.simpleName ?: "None"}"
    }
}
