package com.example.fcmd

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Bluetooth Audio Manager
 * Handles Bluetooth device discovery, connection, and audio routing
 */
class BluetoothAudioManager(
    private val context: Context,
    private val audioManager: AudioManager
) {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectionCallback: ((Boolean, String) -> Unit)? = null
    private var deviceListCallback: ((List<BluetoothDeviceInfo>) -> Unit)? = null

    data class BluetoothDeviceInfo(
        val name: String,
        val address: String,
        val isConnected: Boolean,
        val type: DeviceType
    )

    enum class DeviceType {
        HEADSET,
        SPEAKER,
        UNKNOWN
    }

    // Broadcast receiver for Bluetooth state changes
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    handleBluetoothStateChange(state)
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        connectionCallback?.invoke(true, it.name ?: "Unknown Device")
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        connectionCallback?.invoke(false, it.name ?: "Unknown Device")
                    }
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_ERROR
                    )
                    handleScoStateChange(state)
                }
            }
        }
    }

    init {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Register broadcast receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }

    /**
     * Set callback for connection status changes
     */
    fun setConnectionCallback(callback: (Boolean, String) -> Unit) {
        connectionCallback = callback
    }

    /**
     * Set callback for device list updates
     */
    fun setDeviceListCallback(callback: (List<BluetoothDeviceInfo>) -> Unit) {
        deviceListCallback = callback
    }

    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Get list of paired Bluetooth audio devices
     */
    fun getPairedAudioDevices(): List<BluetoothDeviceInfo> {
        val devices = mutableListOf<BluetoothDeviceInfo>()

        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                // Check if device is an audio device (using Bluetooth class)
                val deviceClass = device.bluetoothClass?.majorDeviceClass
                val isAudioDevice = deviceClass == 0x0400 // Audio/Video major class

                if (isAudioDevice || device.name?.contains("headset", ignoreCase = true) == true ||
                    device.name?.contains("speaker", ignoreCase = true) == true ||
                    device.name?.contains("audio", ignoreCase = true) == true) {

                    val type = when {
                        device.name?.contains("headset", ignoreCase = true) == true -> DeviceType.HEADSET
                        device.name?.contains("speaker", ignoreCase = true) == true -> DeviceType.SPEAKER
                        else -> DeviceType.UNKNOWN
                    }

                    devices.add(
                        BluetoothDeviceInfo(
                            name = device.name ?: "Unknown",
                            address = device.address,
                            isConnected = isDeviceConnected(device),
                            type = type
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Handle missing Bluetooth permissions
            e.printStackTrace()
        }

        return devices
    }

    /**
     * Check if a specific device is connected
     */
    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        // This is a simplified check - proper implementation would use
        // BluetoothProfile.ServiceListener to check A2DP/Headset profile connection
        return false  // Default to false, would need profile listeners for accurate status
    }

    /**
     * Route audio to Bluetooth
     */
    fun routeAudioToBluetooth(): Boolean {
        return try {
            // Check if Bluetooth audio device is connected
            if (isBluetoothAudioConnected()) {
                // AudioTrack with proper AudioAttributes will automatically route to BT
                // if BT audio is connected and active
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false
                audioManager.isBluetoothScoOn = false  // Use A2DP, not SCO

                // For A2DP (music/media audio), no special routing needed
                // AudioTrack with USAGE_MEDIA will go to BT automatically
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Route audio to phone speaker
     */
    fun routeAudioToSpeaker(): Boolean {
        return try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Check if Bluetooth audio is connected
     */
    fun isBluetoothAudioConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        } else {
            // Fallback for older API levels
            return audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
        }
    }

    /**
     * Get currently connected Bluetooth audio device name
     */
    fun getConnectedDeviceName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val btDevice = devices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            return btDevice?.productName?.toString()
        }
        return null
    }

    /**
     * Handle Bluetooth state changes
     */
    private fun handleBluetoothStateChange(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_ON -> {
                // Bluetooth turned on
                deviceListCallback?.invoke(getPairedAudioDevices())
            }
            BluetoothAdapter.STATE_OFF -> {
                // Bluetooth turned off
                connectionCallback?.invoke(false, "Bluetooth Disabled")
            }
        }
    }

    /**
     * Handle SCO audio state changes
     */
    private fun handleScoStateChange(state: Int) {
        when (state) {
            AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                // SCO connected (for headset profile)
            }
            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                // SCO disconnected
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get audio routing status string for display
     */
    fun getAudioRoutingStatus(): String {
        return when {
            isBluetoothAudioConnected() -> {
                val deviceName = getConnectedDeviceName()
                if (deviceName != null) {
                    "BT: $deviceName"
                } else {
                    "BT: Connected"
                }
            }
            audioManager.isSpeakerphoneOn -> "Speaker"
            else -> "Phone Audio"
        }
    }
}
