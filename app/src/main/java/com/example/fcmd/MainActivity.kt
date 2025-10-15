package com.example.fcmd

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.fcmd.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var audioEngine: AudioEngine
    private var dspProcessor: DspProcessor? = null
    private var currentToneCount = 1
    private var currentMaxFrequency = 20000.0

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val FIXED_MIN_FREQUENCY = 1000.0 // Fixed lowest frequency
        private const val MIN_MAX_FREQUENCY = 2000 // Minimum selectable max frequency
        private const val MAX_MAX_FREQUENCY = 20000 // Maximum selectable max frequency
        private const val MIN_TONE_COUNT = 1
        private const val MAX_TONE_COUNT = 24
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize audio engine with AudioManager
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioEngine = AudioEngine(audioManager)

        // Set waveform callback
        audioEngine.setWaveformCallback { stereoData ->
            runOnUiThread {
                binding.waveformView.updateWaveform(stereoData)
            }
        }

        // Set spectrum callback
        audioEngine.setSpectrumCallback { audioData, sampleRate ->
            runOnUiThread {
                binding.spectrumView.updateSpectrum(audioData, sampleRate)
            }
        }

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        // Max Frequency control (logarithmic scale: 2000-20000 Hz)
        binding.frequencySeekBar.max = 1000
        val defaultMaxFreq = 20000.0
        currentMaxFrequency = defaultMaxFreq
        binding.frequencySeekBar.progress = frequencyToProgress(defaultMaxFreq)

        binding.frequencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val maxFreq = progressToFrequency(progress)
                currentMaxFrequency = maxFreq
                binding.frequencyText.text = "${maxFreq.toInt()} Hz"
                audioEngine.setFrequencyRange(FIXED_MIN_FREQUENCY, maxFreq, currentToneCount)
                updateIQDemodulator()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Set initial frequency display
        binding.frequencyText.text = "${defaultMaxFreq.toInt()} Hz"

        // Volume control
        binding.volumeSeekBar.max = 100
        binding.volumeSeekBar.progress = 100

        binding.volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val volumePercent = progress
                binding.volumeText.text = "$volumePercent%"
                audioEngine.setTransmitVolume(volumePercent / 100.0)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Tone count control
        binding.toneCountSeekBar.max = MAX_TONE_COUNT - MIN_TONE_COUNT
        binding.toneCountSeekBar.progress = 0 // Start with 1 tone

        binding.toneCountSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val toneCount = progress + MIN_TONE_COUNT
                binding.toneCountText.text = "$toneCount"
                currentToneCount = toneCount
                audioEngine.setFrequencyRange(FIXED_MIN_FREQUENCY, currentMaxFrequency, toneCount)
                updateIQDemodulator()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Start button
        binding.startButton.setOnClickListener {
            if (checkPermissions()) {
                startAudio()
            }
        }

        // Stop button
        binding.stopButton.setOnClickListener {
            stopAudio()
        }

        // Info button - launch InfoActivity
        binding.infoButton.setOnClickListener {
            val intent = Intent(this, InfoActivity::class.java)
            startActivity(intent)
        }

        // Initialize frequency range in audio engine
        audioEngine.setFrequencyRange(FIXED_MIN_FREQUENCY, currentMaxFrequency, currentToneCount)

        updateStatus("Ready. Press Start to begin.")
        updateIQDemodulator()  // Initialize IQ demodulator
    }

    private fun checkPermissions(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted. You can now start audio.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Audio recording permission is required", Toast.LENGTH_LONG).show()
                updateStatus("Error: Permission denied")
            }
        }
    }

    private fun startAudio() {
        if (audioEngine.start()) {
            binding.startButton.isEnabled = false
            binding.stopButton.isEnabled = true
            binding.frequencySeekBar.isEnabled = false
            binding.toneCountSeekBar.isEnabled = false

            val toneText = if (currentToneCount == 1) {
                "TX: ${FIXED_MIN_FREQUENCY.toInt()} Hz"
            } else {
                "TX: $currentToneCount tones (${FIXED_MIN_FREQUENCY.toInt()}-${currentMaxFrequency.toInt()} Hz)"
            }
            updateStatus("Running - $toneText, RX: Stereo IQ")
            Toast.makeText(this, "Audio started", Toast.LENGTH_SHORT).show()
        } else {
            updateStatus("Error: Failed to start audio")
            Toast.makeText(this, "Failed to start audio engine", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopAudio() {
        audioEngine.stop()
        binding.startButton.isEnabled = true
        binding.stopButton.isEnabled = false
        binding.frequencySeekBar.isEnabled = true
        binding.toneCountSeekBar.isEnabled = true
        binding.waveformView.clear()
        binding.spectrumView.clear()
        updateStatus("Stopped")
        Toast.makeText(this, "Audio stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus(message: String) {
        binding.statusText.text = "Status: $message"
    }

    /**
     * Convert logarithmic slider progress (0-1000) to max frequency (2000-20000 Hz)
     */
    private fun progressToFrequency(progress: Int): Double {
        val minLog = kotlin.math.ln(MIN_MAX_FREQUENCY.toDouble())
        val maxLog = kotlin.math.ln(MAX_MAX_FREQUENCY.toDouble())
        val scale = (maxLog - minLog) / 1000.0
        return kotlin.math.exp(minLog + scale * progress)
    }

    /**
     * Convert max frequency (2000-20000 Hz) to logarithmic slider progress (0-1000)
     */
    private fun frequencyToProgress(frequency: Double): Int {
        val minLog = kotlin.math.ln(MIN_MAX_FREQUENCY.toDouble())
        val maxLog = kotlin.math.ln(MAX_MAX_FREQUENCY.toDouble())
        val freqLog = kotlin.math.ln(frequency.coerceIn(MIN_MAX_FREQUENCY.toDouble(), MAX_MAX_FREQUENCY.toDouble()))
        return ((freqLog - minLog) / (maxLog - minLog) * 1000.0).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.stop()
    }

    override fun onPause() {
        super.onPause()
        // Optional: pause audio when app goes to background
        if (audioEngine.isRunning()) {
            stopAudio()
        }
    }

    private fun updateIQDemodulator() {
        // Get current frequencies from audio engine
        val frequencies = audioEngine.getFrequencies()

        if (frequencies.isEmpty()) {
            return
        }

        // Get actual sample rate from audio engine
        val sampleRate = audioEngine.getSampleRate()

        // Set up IQ demodulator DSP with 40 Hz update rate
        dspProcessor = IQDemodulatorDSP(
            sampleRate,
            frequencies,
            updateRateHz = 40.0,
            callback = { leftAnalysis, rightAnalysis ->
                runOnUiThread {
                    updateToneAnalysis(leftAnalysis, rightAnalysis, sampleRate)
                }
            }
        )
        audioEngine.setDspProcessor(dspProcessor)

        // Show IQ analysis view
        binding.toneAnalysisScrollView.visibility = android.view.View.VISIBLE
        binding.spectrumView.visibility = android.view.View.VISIBLE

        // Show initial message with update rate info
        val minFreq = frequencies.minOrNull() ?: 0.0
        val maxFreq = frequencies.maxOrNull() ?: 0.0

        binding.toneAnalysisText.text = buildString {
            appendLine("━━━ IQ DEMODULATOR ━━━")
            appendLine()
            appendLine("Tones: ${frequencies.size}")
            if (frequencies.size == 1) {
                appendLine("Frequency: ${frequencies[0].toInt()} Hz")
            } else {
                appendLine("Range: ${minFreq.toInt()}-${maxFreq.toInt()} Hz")
            }
            appendLine("Update: ~47 Hz | Filter: IIR")
            appendLine()
            appendLine("Press START for IQ analysis...")
            appendLine("Green spectrum overlay shows TX.")
        }

        val statusText = if (currentToneCount == 1) {
            "${FIXED_MIN_FREQUENCY.toInt()} Hz - IQ Mode"
        } else {
            "$currentToneCount tones (${FIXED_MIN_FREQUENCY.toInt()}-${currentMaxFrequency.toInt()} Hz) - IQ Mode"
        }
        updateStatus(statusText)
    }

    private fun updateToneAnalysis(leftAnalysis: List<ToneAnalysis>, rightAnalysis: List<ToneAnalysis>, sampleRate: Int) {
        val text = buildString {
            appendLine("━━━ IQ ANALYSIS ━━━")
            appendLine("Sample Rate: ${sampleRate/1000}kHz | Update: 40Hz")
            appendLine("Hz     L-Amp  L-Φ°   R-Amp  R-Φ°")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            for (i in leftAnalysis.indices) {
                val left = leftAnalysis[i]
                val right = if (i < rightAnalysis.size) rightAnalysis[i] else null

                val freqStr = String.format("%5.0f", left.frequency)
                val leftAmpStr = String.format("%.3f", left.amplitude)
                val leftPhaseStr = String.format("%4.0f", left.phaseDegrees())

                val rightAmpStr = if (right != null) String.format("%.3f", right.amplitude) else " --- "
                val rightPhaseStr = if (right != null) String.format("%4.0f", right.phaseDegrees()) else " -- "

                appendLine("$freqStr $leftAmpStr $leftPhaseStr° $rightAmpStr $rightPhaseStr°")
            }

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Amp:0-1 Φ:-180→+180")
        }

        binding.toneAnalysisText.text = text
    }
}