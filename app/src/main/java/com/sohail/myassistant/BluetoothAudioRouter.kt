package com.sohail.myassistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

object BluetoothAudioRouter {

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

    @SuppressLint("MissingPermission")
    fun routeToBluetoothMic(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasBluetoothPermission(context)) {
                return false
            }

            val bluetoothDevice = audioManager.availableCommunicationDevices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }

            if (bluetoothDevice != null) {
                audioManager.setCommunicationDevice(bluetoothDevice)
            } else {
                false
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()

            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = true

            true
        }
    }

    fun resetAudioRoute(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()

            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
        }

        audioManager.mode = AudioManager.MODE_NORMAL
    }
}