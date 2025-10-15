package com.example.fcmd

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.Build
import android.util.Log

data class AudioHardwareInfo(
    val maxSampleRate: Int,
    val minSampleRate: Int,
    val supportedSampleRates: List<Int>,
    val maxInputChannels: Int,
    val maxOutputChannels: Int,
    val supportsLowLatency: Boolean,
    val supportsProAudio: Boolean,
    val inputLatencyMs: Double,
    val outputLatencyMs: Double,
    val nativeBufferSize: Int,
    val supportedEncodings: List<String>,
    val supportedBitDepths: List<String>,
    val deviceInfo: String
)

class AudioCapabilities(private val context: Context) {

    companion object {
        private const val TAG = "AudioCapabilities"

        private val SAMPLE_RATES = intArrayOf(
            8000, 11025, 16000, 22050, 32000,
            44100, 48000, 88200, 96000, 176400, 192000
        )

        private val ENCODINGS = mapOf(
            AudioFormat.ENCODING_PCM_16BIT to "PCM 16-bit",
            AudioFormat.ENCODING_PCM_FLOAT to "PCM Float",
            AudioFormat.ENCODING_PCM_8BIT to "PCM 8-bit",
            AudioFormat.ENCODING_AC3 to "AC3",
            AudioFormat.ENCODING_E_AC3 to "E-AC3",
            AudioFormat.ENCODING_DTS to "DTS",
            AudioFormat.ENCODING_DTS_HD to "DTS-HD"
        )

        private fun getBitDepthFormats(): Map<Int, String> {
            val formats = mutableMapOf(
                AudioFormat.ENCODING_PCM_8BIT to "8-bit",
                AudioFormat.ENCODING_PCM_16BIT to "16-bit",
                AudioFormat.ENCODING_PCM_FLOAT to "32-bit Float"
            )

            // Add 24-bit support for Android S (API 31) and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                formats[AudioFormat.ENCODING_PCM_24BIT_PACKED] = "24-bit"
                formats[AudioFormat.ENCODING_PCM_32BIT] = "32-bit"
            }

            return formats
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getAudioHardwareInfo(): AudioHardwareInfo {
        val supportedSampleRates = findSupportedSampleRates()
        val maxSampleRate = supportedSampleRates.maxOrNull() ?: 48000
        val minSampleRate = supportedSampleRates.minOrNull() ?: 8000

        val maxInputChannels = getMaxInputChannels()
        val maxOutputChannels = getMaxOutputChannels()

        val supportsLowLatency = checkLowLatencySupport()
        val supportsProAudio = checkProAudioSupport()

        val (inputLatency, outputLatency) = estimateLatency()
        val nativeBufferSize = getNativeBufferSize()
        val supportedEncodings = getSupportedEncodings()
        val supportedBitDepths = getSupportedBitDepths()
        val deviceInfo = getAudioDeviceInfo()

        return AudioHardwareInfo(
            maxSampleRate = maxSampleRate,
            minSampleRate = minSampleRate,
            supportedSampleRates = supportedSampleRates,
            maxInputChannels = maxInputChannels,
            maxOutputChannels = maxOutputChannels,
            supportsLowLatency = supportsLowLatency,
            supportsProAudio = supportsProAudio,
            inputLatencyMs = inputLatency,
            outputLatencyMs = outputLatency,
            nativeBufferSize = nativeBufferSize,
            supportedEncodings = supportedEncodings,
            supportedBitDepths = supportedBitDepths,
            deviceInfo = deviceInfo
        )
    }

    private fun findSupportedSampleRates(): List<Int> {
        val supported = mutableListOf<Int>()

        for (rate in SAMPLE_RATES) {
            val bufferSize = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize > 0) {
                supported.add(rate)
            }
        }

        Log.d(TAG, "Supported sample rates: $supported")
        return supported
    }

    private fun getMaxInputChannels(): Int {
        var maxChannels = 0

        val channelConfigs = listOf(
            AudioFormat.CHANNEL_IN_MONO to 1,
            AudioFormat.CHANNEL_IN_STEREO to 2
        )

        for ((config, count) in channelConfigs) {
            val bufferSize = AudioRecord.getMinBufferSize(
                48000,
                config,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize > 0) {
                maxChannels = count
            }
        }

        return maxChannels
    }

    private fun getMaxOutputChannels(): Int {
        var maxChannels = 0

        val channelConfigs = listOf(
            AudioFormat.CHANNEL_OUT_MONO to 1,
            AudioFormat.CHANNEL_OUT_STEREO to 2,
            AudioFormat.CHANNEL_OUT_QUAD to 4,
            AudioFormat.CHANNEL_OUT_5POINT1 to 6,
            AudioFormat.CHANNEL_OUT_7POINT1 to 8
        )

        for ((config, count) in channelConfigs) {
            val bufferSize = AudioTrack.getMinBufferSize(
                48000,
                config,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize > 0) {
                maxChannels = count
            }
        }

        return maxChannels
    }

    private fun checkLowLatencySupport(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_AUDIO_LOW_LATENCY
            )
        } else {
            false
        }
    }

    private fun checkProAudioSupport(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_AUDIO_PRO
            )
        } else {
            false
        }
    }

    private fun estimateLatency(): Pair<Double, Double> {
        val nativeBufferSize = getNativeBufferSize()
        val nativeSampleRate = getNativeSampleRate()

        // Estimate based on buffer size
        val bufferLatency = (nativeBufferSize.toDouble() / nativeSampleRate) * 1000.0

        // Add typical processing overhead
        val inputLatency = bufferLatency + 10.0  // ~10ms typical input overhead
        val outputLatency = bufferLatency + 5.0  // ~5ms typical output overhead

        return Pair(inputLatency, outputLatency)
    }

    private fun getNativeBufferSize(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256
        } else {
            256
        }
    }

    private fun getNativeSampleRate(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        } else {
            48000
        }
    }

    private fun getSupportedEncodings(): List<String> {
        val supported = mutableListOf<String>()

        for ((encoding, name) in ENCODINGS) {
            val bufferSize = AudioTrack.getMinBufferSize(
                48000,
                AudioFormat.CHANNEL_OUT_STEREO,
                encoding
            )

            if (bufferSize > 0) {
                supported.add(name)
            }
        }

        return supported.ifEmpty { listOf("PCM 16-bit") }
    }

    private fun getSupportedBitDepths(): List<String> {
        val supported = mutableListOf<String>()
        val bitDepthFormats = getBitDepthFormats()

        // Test each bit depth for both input and output
        for ((encoding, name) in bitDepthFormats) {
            var inputSupported = false
            var outputSupported = false

            // Test input (AudioRecord)
            val inputBufferSize = AudioRecord.getMinBufferSize(
                48000,
                AudioFormat.CHANNEL_IN_STEREO,
                encoding
            )
            if (inputBufferSize > 0) {
                inputSupported = true
            }

            // Test output (AudioTrack)
            val outputBufferSize = AudioTrack.getMinBufferSize(
                48000,
                AudioFormat.CHANNEL_OUT_STEREO,
                encoding
            )
            if (outputBufferSize > 0) {
                outputSupported = true
            }

            // Add if supported on either input or output
            if (inputSupported || outputSupported) {
                val suffix = when {
                    inputSupported && outputSupported -> " (I/O)"
                    inputSupported -> " (Input)"
                    outputSupported -> " (Output)"
                    else -> ""
                }
                supported.add(name + suffix)
                Log.d(TAG, "Bit depth $name: Input=$inputSupported, Output=$outputSupported")
            }
        }

        return supported.ifEmpty { listOf("16-bit (I/O)") }
    }

    private fun getAudioDeviceInfo(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
            val deviceNames = devices.mapNotNull { device ->
                val type = getDeviceTypeName(device)
                val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    device.productName.toString().ifEmpty { type }
                } else {
                    type
                }
                if (device.isSource || device.isSink) name else null
            }

            return deviceNames.distinct().joinToString(", ").ifEmpty { "Default" }
        }
        return "Default"
    }

    private fun getDeviceTypeName(device: AudioDeviceInfo): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Headphones"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
                else -> "Audio Device"
            }
        } else {
            "Audio Device"
        }
    }

    fun formatAudioInfo(info: AudioHardwareInfo): String {
        return buildString {
            appendLine("━━━━━ AUDIO HARDWARE CAPABILITIES ━━━━━")
            appendLine()
            appendLine("Sample Rates:")
            appendLine("  • Range: ${info.minSampleRate} - ${info.maxSampleRate} Hz")
            appendLine("  • Supported: ${info.supportedSampleRates.joinToString(", ")} Hz")
            appendLine()
            appendLine("Bit Depths:")
            info.supportedBitDepths.forEach { bitDepth ->
                appendLine("  • $bitDepth")
            }
            appendLine()
            appendLine("Channels:")
            appendLine("  • Max Input: ${info.maxInputChannels}")
            appendLine("  • Max Output: ${info.maxOutputChannels}")
            appendLine()
            appendLine("Latency:")
            appendLine("  • Input: ~${String.format("%.1f", info.inputLatencyMs)} ms")
            appendLine("  • Output: ~${String.format("%.1f", info.outputLatencyMs)} ms")
            appendLine("  • Buffer Size: ${info.nativeBufferSize} frames")
            appendLine()
            appendLine("Features:")
            appendLine("  • Low Latency: ${if (info.supportsLowLatency) "Yes" else "No"}")
            appendLine("  • Pro Audio: ${if (info.supportsProAudio) "Yes" else "No"}")
            appendLine()
            appendLine("Encodings:")
            info.supportedEncodings.forEach { encoding ->
                appendLine("  • $encoding")
            }
            appendLine()
            appendLine("Devices: ${info.deviceInfo}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }
}
