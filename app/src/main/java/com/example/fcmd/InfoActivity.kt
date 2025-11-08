package com.example.fcmd

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.fcmd.databinding.ActivityInfoBinding

class InfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInfoBinding
    private lateinit var audioCapabilities: AudioCapabilities

    companion object {
        const val EXTRA_TONE_FREQUENCIES = "tone_frequencies"
        const val EXTRA_TONE_COUNT = "tone_count"
        const val EXTRA_MIN_FREQ = "min_freq"
        const val EXTRA_MAX_FREQ = "max_freq"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Hardware Information"

        // Initialize audio capabilities
        audioCapabilities = AudioCapabilities(this)

        // Show loading message
        binding.hardwareInfoText.text = "Loading hardware information..."

        // Load hardware info in background
        loadHardwareInfo()
    }

    private fun loadHardwareInfo() {
        Thread {
            val hardwareInfo = audioCapabilities.getAudioHardwareInfo()

            // Get tone frequency data from intent
            val toneFrequencies = intent.getDoubleArrayExtra(EXTRA_TONE_FREQUENCIES)
            val toneCount = intent.getIntExtra(EXTRA_TONE_COUNT, 0)
            val minFreq = intent.getDoubleExtra(EXTRA_MIN_FREQ, 0.0)
            val maxFreq = intent.getDoubleExtra(EXTRA_MAX_FREQ, 0.0)

            runOnUiThread {
                val infoText = buildString {
                    // Show tone configuration first
                    if (toneFrequencies != null && toneFrequencies.isNotEmpty()) {
                        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        appendLine("       TRANSMIT FREQUENCIES")
                        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        appendLine()
                        appendLine("Tone Count: $toneCount")
                        appendLine("Range: ${minFreq.toInt()} - ${maxFreq.toInt()} Hz")
                        appendLine()
                        appendLine("Individual Frequencies:")
                        appendLine("------------------------")

                        toneFrequencies.forEachIndexed { index, freq ->
                            val freqStr = String.format("%7.1f Hz", freq)
                            val noteNum = index + 1
                            appendLine("  Tone ${"%-2d".format(noteNum)}: $freqStr")
                        }

                        appendLine()
                        appendLine()
                    }

                    // Then show hardware info
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine("       HARDWARE INFORMATION")
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine()

                    if (hardwareInfo != null) {
                        append(audioCapabilities.formatAudioInfo(hardwareInfo))
                    } else {
                        append("Failed to load hardware information.")
                    }
                }

                binding.hardwareInfoText.text = infoText
            }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
