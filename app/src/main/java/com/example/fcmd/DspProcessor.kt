package com.example.fcmd

/**
 * Interface for real-time DSP processing of audio data
 */
interface DspProcessor {
    /**
     * Process stereo audio data in real-time
     * @param leftChannel Left channel samples (normalized -1.0 to 1.0)
     * @param rightChannel Right channel samples (normalized -1.0 to 1.0)
     * @param sampleRate Sample rate in Hz
     * @return Processed data or null if no processing needed
     */
    fun processStereo(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        sampleRate: Int
    ): Pair<FloatArray, FloatArray>?

    /**
     * Process mono audio data in real-time
     * @param data Audio samples (normalized -1.0 to 1.0)
     * @param sampleRate Sample rate in Hz
     * @return Processed data or null if no processing needed
     */
    fun processMono(data: FloatArray, sampleRate: Int): FloatArray?

    /**
     * Called when DSP processing should be reset
     */
    fun reset()
}

/**
 * Example DSP processor that demonstrates the interface
 */
class PassThroughDspProcessor : DspProcessor {
    override fun processStereo(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        sampleRate: Int
    ): Pair<FloatArray, FloatArray>? {
        // Pass through without modification
        return Pair(leftChannel, rightChannel)
    }

    override fun processMono(data: FloatArray, sampleRate: Int): FloatArray? {
        // Pass through without modification
        return data
    }

    override fun reset() {
        // Nothing to reset
    }
}

/**
 * Example: Simple gain control DSP processor
 */
class GainDspProcessor(private var gain: Float = 1.0f) : DspProcessor {

    fun setGain(newGain: Float) {
        gain = newGain.coerceIn(0f, 2f)
    }

    override fun processStereo(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        sampleRate: Int
    ): Pair<FloatArray, FloatArray> {
        val processedLeft = FloatArray(leftChannel.size) { i ->
            (leftChannel[i] * gain).coerceIn(-1f, 1f)
        }
        val processedRight = FloatArray(rightChannel.size) { i ->
            (rightChannel[i] * gain).coerceIn(-1f, 1f)
        }
        return Pair(processedLeft, processedRight)
    }

    override fun processMono(data: FloatArray, sampleRate: Int): FloatArray {
        return FloatArray(data.size) { i ->
            (data[i] * gain).coerceIn(-1f, 1f)
        }
    }

    override fun reset() {
        gain = 1.0f
    }
}
