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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasBluetoothPermission(context)) {
                return false
            }
            return audioManager.availableCommunicationDevices.any { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } else {
            @Suppress("DEPRECATION")
            return audioManager.isBluetoothScoAvailableOffCall
        }
    }

    /**
     * Attempts to route microphone input and voice audio to a connected Bluetooth headset.
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasBluetoothPermission(context)) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission missing, cannot route audio")
                return false
            }

            val bluetoothDevice = audioManager.availableCommunicationDevices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }

            if (bluetoothDevice == null) {
                Log.i(TAG, "No Bluetooth communication device found, using device microphone")
                return false
            }

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            val success = audioManager.setCommunicationDevice(bluetoothDevice)
            isBluetoothRouted = success
            Log.i(TAG, "setCommunicationDevice (${bluetoothDevice.productName}) result: $success")
            return success
        } else {
            @Suppress("DEPRECATION")
            if (!audioManager.isBluetoothScoAvailableOffCall) {
                Log.i(TAG, "Bluetooth SCO off-call not available on this device")
                return false
            }

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = true
            isBluetoothRouted = true
            Log.i(TAG, "Legacy Bluetooth SCO requested")
            return true
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
            Log.i(TAG, "Audio route reset to normal")
        }
    }
}
