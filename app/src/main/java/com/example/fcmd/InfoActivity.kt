package com.example.fcmd

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.fcmd.databinding.ActivityInfoBinding

class InfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInfoBinding
    private lateinit var audioCapabilities: AudioCapabilities

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
            runOnUiThread {
                if (hardwareInfo != null) {
                    binding.hardwareInfoText.text = audioCapabilities.formatAudioInfo(hardwareInfo)
                } else {
                    binding.hardwareInfoText.text = "Failed to load hardware information."
                }
            }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
