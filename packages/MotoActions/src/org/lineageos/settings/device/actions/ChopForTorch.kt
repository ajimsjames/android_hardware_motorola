/*
 * SPDX-FileCopyrightText: 2015-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import org.lineageos.settings.device.ActionsPreferenceFragment
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_CHOP_CHOP_KEY
import org.lineageos.settings.device.SensorHelper
import org.lineageos.settings.device.SensorHelper.Companion.SENSOR_TYPE_MMI_CHOP_CHOP

class ChopForTorch(
    context: Context,
    private val sharedPreferences: SharedPreferences,
    private val sensorHelper: SensorHelper,
) : UpdatedStateNotifier {

    private val cameraManager =
        context.getSystemService(CameraManager::class.java).apply {
            val callback =
                object : TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId != rearCameraId) return
                        torchEnabled = enabled
                    }

                    override fun onTorchModeUnavailable(cameraId: String) {
                        if (cameraId != rearCameraId) return
                        torchEnabled = false
                    }
                }
            registerTorchCallback(callback, null)
            runCatching {
                cameraIdList.forEach { cameraId ->
                    val characteristics = getCameraCharacteristics(cameraId)
                    val orientation = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (orientation == CameraCharacteristics.LENS_FACING_BACK) {
                        rearCameraId = cameraId
                        return@forEach
                    }
                }
            }
        }
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val chopChopSensor: Sensor? = sensorHelper.getSensor(SENSOR_TYPE_MMI_CHOP_CHOP)
    private val accelSensor: Sensor? = if (chopChopSensor == null) sensorHelper.getSensor(Sensor.TYPE_ACCELEROMETER) else null
    private val proximitySensor: Sensor? = sensorHelper.getSensor(Sensor.TYPE_PROXIMITY)

    private var isEnabled = false
    private var proxIsCovered = false
    private var torchEnabled = false
    private var rearCameraId: String? = null

    // Fallback Chop Gesture Detection Variables
    private var lastChopTimeMs: Long = 0
    private var chopCount = 0
    private var lastZAccel = 0f
    private var lastToggleTimeMs: Long = 0

    @Synchronized
    override fun updateState() {
        val enabled = sharedPreferences.getBoolean(GESTURE_CHOP_CHOP_KEY, true)
        if (enabled == isEnabled) {
            return
        }
        if (enabled) {
            Log.d(TAG, "Enabling chop chop (hw sensor = ${chopChopSensor != null})")
            if (chopChopSensor != null) {
                sensorHelper.registerListener(chopChopSensor, chopChopListener)
            } else if (accelSensor != null) {
                sensorHelper.registerListener(accelSensor, accelChopListener)
            }
            proximitySensor?.let { sensorHelper.registerListener(it, proximityListener) }
        } else {
            Log.d(TAG, "Disabling chop chop")
            sensorHelper.unregisterListener(chopChopListener)
            sensorHelper.unregisterListener(accelChopListener)
            sensorHelper.unregisterListener(proximityListener)
        }
        isEnabled = enabled
    }

    private fun toggleTorch() {
        if (proxIsCovered) {
            Log.d(TAG, "proximity sensor covered, ignoring chop-chop")
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastToggleTimeMs < 800) return
        lastToggleTimeMs = now

        vibrator.vibrate(
            VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE)
        )

        rearCameraId?.let { id ->
            runCatching {
                val nextState = !torchEnabled
                cameraManager.setTorchMode(id, nextState)
                torchEnabled = nextState

                // Apply custom LED brightness if turning ON
                if (nextState) {
                    val level = sharedPreferences.getString(ActionsPreferenceFragment.KEY_TORCH_BRIGHTNESS, "250") ?: "250"
                    ActionsPreferenceFragment.writeNode(
                        listOf(
                            "/sys/class/leds/led:torch_0/brightness",
                            "/sys/class/leds/led:torch_1/brightness"
                        ),
                        level
                    )
                }
            }
        }
    }

    private val chopChopListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                Log.d(TAG, "hw chop chop triggered")
                toggleTorch()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    private val accelChopListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val totalAccel = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                // Detect sharp acceleration spike characteristic of a chop motion (> 26 m/s^2)
                if (totalAccel > 26.0f) {
                    val now = System.currentTimeMillis()
                    if (now - lastChopTimeMs in 180..800) {
                        chopCount++
                        if (chopCount >= 2) {
                            Log.d(TAG, "software chop-chop gesture detected (peak: $totalAccel)")
                            chopCount = 0
                            lastChopTimeMs = 0
                            toggleTorch()
                        }
                    } else if (now - lastChopTimeMs > 800) {
                        chopCount = 1
                        lastChopTimeMs = now
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    private val proximityListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                val maxRange = kotlin.math.round((proximitySensor?.maximumRange ?: 0f) * 10f) / 10f
                proxIsCovered = event.values[0] < maxRange
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    companion object {
        private const val TAG = "ChopForTorch"
    }
}
