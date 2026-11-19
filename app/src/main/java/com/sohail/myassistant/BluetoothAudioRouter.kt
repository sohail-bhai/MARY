package com.sohail.myassistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Handles Bluetooth SCO and BLE Headset audio routing for hands-free voice input
 * and playback, tailored for driving with AirPods or car head units.
 */
object BluetoothAudioRouter {

    private const val TAG = "BluetoothAudioRouter"

    @Volatile
    var isBluetoothRouted: Boolean = false
        private set

    @Volatile
    var connectedDeviceName: String? = null
        private set

    /**
     * Checks whether the app has the required Bluetooth permission.
     * On Android 12+ (API 31+), BLUETOOTH_CONNECT is required.
     */
    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Determines whether an eligible Bluetooth headset (SCO or BLE Headset) is available.
     */
    @SuppressLint("MissingPermission")
    fun isBluetoothHeadsetAvailable(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!hasBluetoothPermission(context)) {
                    return false
                }
                audioManager.availableCommunicationDevices.any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoAvailableOffCall
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth headset availability: ${e.message}", e)
            false
        }
    }

    /**
     * Attempts to route microphone input and voice audio to a connected Bluetooth headset.
     * Guarantees that audioManager.mode is never left in MODE_IN_COMMUNICATION if routing fails.
     *
     * @return true if successfully routed to Bluetooth, false if falling back to device mic.
     */
    @SuppressLint("MissingPermission")
    fun routeToBluetoothMic(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            Log.e(TAG, "AudioManager not available")
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!hasBluetoothPermission(context)) {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission missing, falling back to device mic")
                    return false
                }

                val bluetoothDevice = audioManager.availableCommunicationDevices.firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }

                if (bluetoothDevice == null) {
                    Log.i(TAG, "No Bluetooth communication device found, using phone mic")
                    connectedDeviceName = null
                    return false
                }

                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                val success = audioManager.setCommunicationDevice(bluetoothDevice)

                if (success) {
                    isBluetoothRouted = true
                    connectedDeviceName = bluetoothDevice.productName?.toString() ?: "Bluetooth Headset"
                    Log.i(TAG, "Successfully routed audio to $connectedDeviceName")
                    true
                } else {
                    // Revert mode immediately if setCommunicationDevice failed
                    audioManager.mode = AudioManager.MODE_NORMAL
                    isBluetoothRouted = false
                    connectedDeviceName = null
                    Log.w(TAG, "setCommunicationDevice failed; reverted to MODE_NORMAL")
                    false
                }
            } else {
                @Suppress("DEPRECATION")
                if (!audioManager.isBluetoothScoAvailableOffCall) {
                    Log.i(TAG, "Bluetooth SCO off-call not available on this device")
                    connectedDeviceName = null
                    return false
                }

                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                isBluetoothRouted = true
                connectedDeviceName = "Bluetooth SCO Device"
                Log.i(TAG, "Legacy Bluetooth SCO requested")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Bluetooth mic routing: ${e.message}", e)
            audioManager.mode = AudioManager.MODE_NORMAL
            isBluetoothRouted = false
            connectedDeviceName = null
            false
        }
    }

    /**
     * Safely resets the audio mode and disconnects communication devices.
     */
    fun resetAudioRoute(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothScoOn) {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting Bluetooth route: ${e.message}", e)
        } finally {
            audioManager.mode = AudioManager.MODE_NORMAL
            isBluetoothRouted = false
            connectedDeviceName = null
            Log.i(TAG, "Audio route reset to normal")
        }
    }

    /**
     * Returns a human-readable description of the active audio route.
     */
    fun getAudioRouteStatus(): String {
        return if (isBluetoothRouted && connectedDeviceName != null) {
            "Connected to $connectedDeviceName"
        } else {
            "Using phone microphone"
        }
    }
}
