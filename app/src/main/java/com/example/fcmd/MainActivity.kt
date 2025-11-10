package com.example.fcmd

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
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
    private var iqDemodulatorDSP: IQDemodulatorDSP? = null
    private var currentToneCount = 24  // Start with 24 tones for VDI
    private var currentMaxFrequency = 10000.0  // Reduced for phase stability testing
    private var debugPanelVisible = false

    // Audio feedback components
    private var audioToneGenerator: AudioToneGenerator? = null
    private var bluetoothAudioManager: BluetoothAudioManager? = null
    private val audioUpdateHandler = Handler(Looper.getMainLooper())

    // Haptic feedback components
    private var vibrator: Vibrator? = null
    private var hapticEnabled = false
    private var lastVibrationTime = 0L
    private val vibrationCooldown = 500L // Minimum 500ms between vibrations

    // Dynamic UI control
    private var isDetecting = false
    private var controlPanelVisible = true
    private val controlPanelHideHandler = Handler(Looper.getMainLooper())
    private val controlPanelHideDelay = 3000L // 3 seconds

    private fun checkAndHideControlPanel() {
        // Check if ground balance is actively pumping
        val gbManager = iqDemodulatorDSP?.getGroundBalanceManager()
        if (gbManager?.isCapturing() == true) {
            // Don't hide while pumping - reschedule the timeout
            controlPanelHideHandler.postDelayed(hideControlPanelRunnable, controlPanelHideDelay)
        } else if (isDetecting && controlPanelVisible) {
            hideControlPanel()
        }
    }

    private val hideControlPanelRunnable = Runnable {
        checkAndHideControlPanel()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 1002
        private const val FIXED_MIN_FREQUENCY = 1000.0 // Fixed lowest frequency
        private const val MIN_MAX_FREQUENCY = 2000 // Minimum selectable max frequency
        private const val MAX_MAX_FREQUENCY = 10000 // Maximum selectable max frequency (reduced for testing)
        private const val MIN_TONE_COUNT = 2
        private const val MAX_TONE_COUNT = 24
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen on while app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize audio engine with AudioManager
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioEngine = AudioEngine(audioManager)

        // Initialize audio feedback components
        audioToneGenerator = AudioToneGenerator()
        bluetoothAudioManager = BluetoothAudioManager(this, audioManager)

        // Link target audio generator to audio engine for RIGHT channel stereo output
        audioEngine.setTargetAudioGenerator(audioToneGenerator)

        // Initialize vibrator for haptic feedback
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Set waveform callback - only update when debug panel visible
        audioEngine.setWaveformCallback { stereoData ->
            if (debugPanelVisible) {
                runOnUiThread {
                    binding.waveformView.updateWaveform(stereoData)
                }
            }
        }

        // Set spectrum callback - only update when debug panel visible
        audioEngine.setSpectrumCallback { audioData, sampleRate ->
            if (debugPanelVisible) {
                runOnUiThread {
                    binding.spectrumView.updateSpectrum(audioData, sampleRate)
                }
            }
        }

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        // Debug toggle button
        binding.debugToggleButton.setOnClickListener {
            debugPanelVisible = !debugPanelVisible
            binding.debugPanel.visibility = if (debugPanelVisible) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            // Adjust VDI display constraints
            if (debugPanelVisible) {
                val params = binding.vdiDisplayView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                params.topToBottom = binding.debugPanel.id
                binding.vdiDisplayView.layoutParams = params
            } else {
                val params = binding.vdiDisplayView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                params.topToBottom = binding.topBar.id
                binding.vdiDisplayView.layoutParams = params
            }
        }

        // Max Frequency control (logarithmic scale: 2000-10000 Hz for testing)
        binding.frequencySeekBar.max = 1000
        val defaultMaxFreq = 10000.0  // Reduced to 10 kHz for phase stability testing
        currentMaxFrequency = defaultMaxFreq
        binding.frequencySeekBar.progress = frequencyToProgress(defaultMaxFreq)

        binding.frequencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) resetControlPanelTimeout()
                val maxFreq = progressToFrequency(progress)
                currentMaxFrequency = maxFreq
                // Round to nearest 100 Hz for display to avoid 19999 issue
                val displayFreq = kotlin.math.round(maxFreq / 100.0).toInt() * 100
                binding.frequencyText.text = "$displayFreq"
                audioEngine.setFrequencyRange(FIXED_MIN_FREQUENCY, maxFreq, currentToneCount)
                updateIQDemodulator()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                resetControlPanelTimeout()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Set initial frequency display
        binding.frequencyText.text = "${defaultMaxFreq.toInt()}"

        // Volume control
        binding.volumeSeekBar.max = 100
        binding.volumeSeekBar.progress = 100

        binding.volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) resetControlPanelTimeout()
                val volumePercent = progress
                binding.volumeText.text = "$volumePercent%"
                audioEngine.setTransmitVolume(volumePercent / 100.0)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                resetControlPanelTimeout()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Tone count control
        binding.toneCountSeekBar.max = MAX_TONE_COUNT - MIN_TONE_COUNT
        currentToneCount = 8  // Start with 8 tones for phase stability testing
        binding.toneCountSeekBar.progress = 8 - MIN_TONE_COUNT  // Set slider to 8 tones
        binding.toneCountText.text = "$currentToneCount"

        binding.toneCountSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) resetControlPanelTimeout()
                val toneCount = progress + MIN_TONE_COUNT
                binding.toneCountText.text = "$toneCount"
                currentToneCount = toneCount
                audioEngine.setFrequencyRange(FIXED_MIN_FREQUENCY, currentMaxFrequency, toneCount)
                updateIQDemodulator()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                resetControlPanelTimeout()
            }
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

            // Pass current tone configuration
            val frequencies = audioEngine.getFrequencies()
            intent.putExtra(InfoActivity.EXTRA_TONE_FREQUENCIES, frequencies.toDoubleArray())
            intent.putExtra(InfoActivity.EXTRA_TONE_COUNT, currentToneCount)
            intent.putExtra(InfoActivity.EXTRA_MIN_FREQ, FIXED_MIN_FREQUENCY)
            intent.putExtra(InfoActivity.EXTRA_MAX_FREQ, currentMaxFrequency)

            startActivity(intent)
        }

        // Ground Balance Mode Spinner with custom high-contrast layout
        val gbModes = arrayOf("OFF", "Manual", "Auto Track", "Manual+Track")
        val adapter = android.widget.ArrayAdapter(this, R.layout.spinner_item, gbModes)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.gbModeSpinner.adapter = adapter

        binding.gbModeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                resetControlPanelTimeout()
                val gbManager = iqDemodulatorDSP?.getGroundBalanceManager()
                val mode = when (position) {
                    0 -> GroundBalanceMode.OFF
                    1 -> GroundBalanceMode.MANUAL
                    2 -> GroundBalanceMode.AUTO_TRACKING
                    3 -> GroundBalanceMode.MANUAL_TRACKING
                    else -> GroundBalanceMode.OFF
                }
                gbManager?.setMode(mode)

                // Enable/disable pump button based on mode
                binding.gbPumpButton.isEnabled = (mode == GroundBalanceMode.MANUAL || mode == GroundBalanceMode.MANUAL_TRACKING) && audioEngine.isRunning()

                // Enable/disable offset control
                binding.gbOffsetSeekBar.isEnabled = mode != GroundBalanceMode.OFF

                updateStatus(gbManager?.getStatusString() ?: "Ready")
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Ground Balance Pump Button
        binding.gbPumpButton.setOnClickListener {
            resetControlPanelTimeout()
            val gbManager = iqDemodulatorDSP?.getGroundBalanceManager()
            if (gbManager?.isCapturing() == true) {
                // Stop pumping
                gbManager.stopManualCapture()
                binding.gbPumpButton.text = "PUMP"
                Toast.makeText(this, "Ground balance set", Toast.LENGTH_SHORT).show()
                updateStatus(gbManager.getStatusString())
            } else {
                // Start pumping
                gbManager?.startManualCapture()
                binding.gbPumpButton.text = "SET"
                Toast.makeText(this, "Pump coil over ground...", Toast.LENGTH_SHORT).show()
                updateStatus("Pumping... Move coil up/down")
            }
        }

        // Ground Balance Offset
        binding.gbOffsetSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) resetControlPanelTimeout()
                // Map 0-100 to -50 to +50
                val offset = progress - 50
                binding.gbOffsetText.text = offset.toString()
                iqDemodulatorDSP?.getGroundBalanceManager()?.setOffset(offset)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                resetControlPanelTimeout()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Audio feedback switch
        binding.audioFeedbackSwitch.setOnCheckedChangeListener { _, isChecked ->
            resetControlPanelTimeout()
            audioToneGenerator?.setEnabled(isChecked)
            if (isChecked) {
                checkBluetoothPermissions()
                startAudioFeedback()
                Toast.makeText(this, "Audio feedback enabled", Toast.LENGTH_SHORT).show()
            } else {
                stopAudioFeedback()
                Toast.makeText(this, "Audio feedback disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Haptic feedback switch
        binding.hapticFeedbackSwitch.setOnCheckedChangeListener { _, isChecked ->
            resetControlPanelTimeout()
            hapticEnabled = isChecked
            if (isChecked) {
                Toast.makeText(this, "Haptic feedback enabled", Toast.LENGTH_SHORT).show()
                // Test vibration
                triggerHapticFeedback(100)
            } else {
                Toast.makeText(this, "Haptic feedback disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Audio feedback volume control
        binding.audioFeedbackVolumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) resetControlPanelTimeout()
                val volume = progress / 100.0
                binding.audioFeedbackVolumeText.text = "$progress%"
                audioToneGenerator?.setVolume(volume)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                resetControlPanelTimeout()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Setup Bluetooth audio manager callbacks
        bluetoothAudioManager?.setConnectionCallback { connected, deviceName ->
            runOnUiThread {
                updateAudioRoutingStatus()
                if (connected) {
                    Toast.makeText(this, "Connected: $deviceName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Disconnected: $deviceName", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Initialize frequency range in audio engine
        audioEngine.setFrequencyRange(FIXED_MIN_FREQUENCY, currentMaxFrequency, currentToneCount)

        // Setup touch detection for dynamic UI
        setupTouchDetection()

        updateStatus("Ready. Press Start to begin.")
        updateIQDemodulator()  // Initialize IQ demodulator
        updateAudioRoutingStatus()
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

            // Enable pump button if in manual GB mode
            val gbManager = iqDemodulatorDSP?.getGroundBalanceManager()
            val mode = gbManager?.getMode()
            binding.gbPumpButton.isEnabled = (mode == GroundBalanceMode.MANUAL || mode == GroundBalanceMode.MANUAL_TRACKING)

            val toneText = "TX: $currentToneCount tones (${FIXED_MIN_FREQUENCY.toInt()}-${currentMaxFrequency.toInt()} Hz)"
            val gbStatus = gbManager?.getStatusString() ?: ""
            updateStatus("Running - $toneText, RX: Mono IQ | $gbStatus")

            // Set detecting state and hide control panel after delay
            isDetecting = true
            controlPanelHideHandler.postDelayed(hideControlPanelRunnable, controlPanelHideDelay)
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
        binding.gbPumpButton.isEnabled = false
        binding.waveformView.clear()
        binding.spectrumView.clear()
        binding.vdiDisplayView.clear()

        val gbManager = iqDemodulatorDSP?.getGroundBalanceManager()
        val gbStatus = gbManager?.getStatusString() ?: ""
        updateStatus("Stopped | $gbStatus")

        // Stop detecting and show control panel
        isDetecting = false
        controlPanelHideHandler.removeCallbacks(hideControlPanelRunnable)
        showControlPanel()
    }

    private fun updateStatus(message: String) {
        binding.statusText.text = message
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
        // Target audio is integrated into AudioEngine - stops automatically
        bluetoothAudioManager?.cleanup()
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

        // Set up IQ demodulator DSP with 40 Hz update rate and VDI calculation (MONO)
        iqDemodulatorDSP = IQDemodulatorDSP(
            sampleRate,
            frequencies,
            updateRateHz = 40.0,
            enableVDI = true,  // Always enable VDI (minimum is now 2 tones)
            callback = { analysis, vdiResult ->
                runOnUiThread {
                    updateToneAnalysis(analysis, sampleRate, vdiResult)
                }
            }
        )
        dspProcessor = iqDemodulatorDSP
        audioEngine.setDspProcessor(dspProcessor)

        // Show spectrum view in debug panel
        binding.spectrumView.visibility = android.view.View.VISIBLE

        // Show initial message with update rate info
        val minFreq = frequencies.minOrNull() ?: 0.0
        val maxFreq = frequencies.maxOrNull() ?: 0.0

        binding.toneAnalysisText.text = buildString {
            appendLine("━━━ IQ DEMODULATOR ━━━")
            appendLine()
            appendLine("Tones: ${frequencies.size}")
            appendLine("Range: ${minFreq.toInt()}-${maxFreq.toInt()} Hz")
            appendLine("Update: ~5 Hz | Filter: IIR")
            appendLine()
            appendLine("Press START for IQ analysis...")
            appendLine("Green spectrum overlay shows RX.")
        }

        val statusText = "$currentToneCount tones (${FIXED_MIN_FREQUENCY.toInt()}-${currentMaxFrequency.toInt()} Hz) - IQ Mode"
        updateStatus(statusText)
    }

    private fun updateToneAnalysis(
        analysis: List<ToneAnalysis>,
        sampleRate: Int,
        vdiResult: VDIResult?
    ) {
        val gbManager = iqDemodulatorDSP?.getGroundBalanceManager()
        val gbStatus = gbManager?.getStatusString() ?: "GB: OFF"

        // Update VDI display view
        binding.vdiDisplayView.updateVDI(vdiResult)

        // Update audio feedback with VDI
        if (vdiResult != null && binding.audioFeedbackSwitch.isChecked) {
            audioToneGenerator?.updateVDI(vdiResult.vdi, vdiResult.confidence)
        }

        // Trigger haptic feedback for high confidence targets
        if (vdiResult != null && hapticEnabled) {
            if (vdiResult.confidence > 0.8) {
                triggerHapticFeedback(50) // Short pulse for high confidence
            }
        }

        // Update status text with ground balance info
        updateStatus(gbStatus)

        // Update debug panel if visible
        if (debugPanelVisible) {
            val text = buildString {
                appendLine("━━━ IQ ANALYSIS (MONO) ━━━")
                appendLine("Sample Rate: ${sampleRate/1000}kHz | Update: 5Hz")
                appendLine(gbStatus)

                // Show VDI prominently if available
                if (vdiResult != null) {
                    appendLine()
                    appendLine("╔══════════════════════════════╗")
                    appendLine("║       VDI: ${String.format("%2d", vdiResult.vdi)} (${getVDIBar(vdiResult.vdi)})      ║")
                    appendLine("║ ${VDICalculator().getTargetDescription(vdiResult).padEnd(28)} ║")

                    // Show depth estimate if available
                    if (vdiResult.depthEstimate != null) {
                        val depth = vdiResult.depthEstimate!!
                        val depthLine = " ${depth.category.indicator} ${depth.category.displayName} (${depth.category.depthRange})"
                        appendLine("║${depthLine.padEnd(30)}║")
                    }

                    appendLine("╚══════════════════════════════╝")
                    appendLine()
                }

                appendLine("Hz      Amp    Phase°")
                appendLine("━━━━━━━━━━━━━━━━━━━━")

                for (tone in analysis) {
                    val freqStr = String.format("%5.0f", tone.frequency)
                    val ampStr = String.format("%.3f", tone.amplitude)
                    val phaseStr = String.format("%4.0f", tone.phaseDegrees())

                    appendLine("$freqStr  $ampStr  $phaseStr°")
                }

                appendLine("━━━━━━━━━━━━━━━━━━━━")
                appendLine("Amp:0-1 Φ:-180→+180")

                // Show VDI technical details at bottom if available
                if (vdiResult != null) {
                    appendLine()
                    appendLine("VDI Details:")
                    appendLine("Phase Slope: ${String.format("%.2f", vdiResult.phaseSlope)}°/kHz")
                    appendLine("Conductivity: ${String.format("%.2f", vdiResult.conductivityIndex)}")
                    appendLine("Confidence: ${String.format("%.0f", vdiResult.confidence * 100)}%")

                    // Show depth estimation details
                    if (vdiResult.depthEstimate != null) {
                        val depth = vdiResult.depthEstimate!!
                        appendLine()
                        appendLine("Depth Estimate:")
                        appendLine("Category: ${depth.category.displayName} (${depth.category.depthRange})")
                        appendLine("Amplitude: ${String.format("%.3f", depth.amplitude)}")
                        appendLine("Factor: ${String.format("%.2f", depth.depthFactor)}")
                        appendLine("Confidence: ${String.format("%.0f", depth.confidence * 100)}%")
                    }
                }
            }

            binding.toneAnalysisText.text = text
        }
    }

    /**
     * Generate a visual VDI bar indicator
     */
    private fun getVDIBar(vdi: Int): String {
        val barLength = 10
        val filled = (vdi * barLength / 99).coerceIn(0, barLength)
        return "█".repeat(filled) + "░".repeat(barLength - filled)
    }

    /**
     * Start audio feedback tone generator
     * NOTE: AudioToneGenerator is now integrated into AudioEngine's RIGHT channel
     * No need to start/stop separately - it's mixed into stereo output
     */
    private fun startAudioFeedback() {
        // Target audio is already integrated into AudioEngine stereo output
        // Just enable the generator and update routing status
        updateAudioRoutingStatus()
    }

    /**
     * Stop audio feedback tone generator
     * NOTE: AudioToneGenerator is now integrated into AudioEngine's RIGHT channel
     * No need to start/stop separately - it's mixed into stereo output
     */
    private fun stopAudioFeedback() {
        // Target audio is integrated into AudioEngine - no separate stop needed
    }

    /**
     * Update audio routing status display
     */
    private fun updateAudioRoutingStatus() {
        // Show stereo output mode for USB codec
        val status = if (binding.audioFeedbackSwitch.isChecked) {
            "Stereo: L=TX R=Target"
        } else {
            "Stereo: L=TX R=Silent"
        }
        binding.audioRoutingText.text = status
    }

    /**
     * Check Bluetooth permissions for audio routing
     */
    private fun checkBluetoothPermissions(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT
            )
            val missingPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toTypedArray(),
                    BLUETOOTH_PERMISSION_REQUEST_CODE
                )
                return false
            }
        }
        return true
    }

    /**
     * Trigger haptic feedback vibration
     */
    private fun triggerHapticFeedback(durationMs: Long) {
        val currentTime = System.currentTimeMillis()

        // Cooldown to prevent excessive vibrations
        if (currentTime - lastVibrationTime < vibrationCooldown) {
            return
        }

        lastVibrationTime = currentTime

        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Use VibrationEffect for Android O and above
                val effect = VibrationEffect.createOneShot(
                    durationMs,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vib.vibrate(effect)
            } else {
                // Fallback for older Android versions
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }

    /**
     * Hide control panel (VDI display remains full-screen)
     */
    private fun hideControlPanel() {
        controlPanelVisible = false
        // Fade out animation
        binding.controlPanel.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.controlPanel.visibility = android.view.View.GONE
            }
            .start()
    }

    /**
     * Show control panel overlaying VDI display
     */
    private fun showControlPanel() {
        controlPanelVisible = true
        binding.controlPanel.visibility = android.view.View.VISIBLE
        binding.controlPanel.alpha = 0f

        // Fade in animation
        binding.controlPanel.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        // Reset timeout if detecting
        resetControlPanelTimeout()
    }

    /**
     * Reset the control panel auto-hide timeout
     * Call this whenever user interacts with any control
     */
    private fun resetControlPanelTimeout() {
        if (isDetecting && controlPanelVisible) {
            controlPanelHideHandler.removeCallbacks(hideControlPanelRunnable)
            controlPanelHideHandler.postDelayed(hideControlPanelRunnable, controlPanelHideDelay)
        }
    }

    /**
     * Setup touch detection to show control panel temporarily during detection
     */
    private fun setupTouchDetection() {
        // Touch anywhere on screen to show controls
        binding.root.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN && isDetecting && !controlPanelVisible) {
                showControlPanel()
                true
            } else {
                false
            }
        }

        // Reset timeout when user touches or scrolls the control panel
        binding.controlPanel.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE -> {
                    resetControlPanelTimeout()
                }
            }
            false  // Let the event propagate to child views
        }
    }
}