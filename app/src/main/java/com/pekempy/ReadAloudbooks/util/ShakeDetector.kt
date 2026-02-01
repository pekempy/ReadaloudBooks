package com.pekempy.ReadAloudbooks.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Shake Detector for sleep timer "shake to extend" feature
 */
class ShakeDetector(
    private val context: Context,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private var lastShakeTime: Long = 0
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f

    companion object {
        private const val SHAKE_THRESHOLD = 15f // Acceleration threshold for shake
        private const val SHAKE_COOLDOWN_MS = 2000L // Minimum time between shakes
    }

    fun start() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelerometer?.let {
            sensorManager?.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val currentTime = System.currentTimeMillis()

            if (currentTime - lastShakeTime > SHAKE_COOLDOWN_MS) {
                val deltaX = x - lastX
                val deltaY = y - lastY
                val deltaZ = z - lastZ

                val acceleration = sqrt(
                    deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
                )

                if (acceleration > SHAKE_THRESHOLD) {
                    lastShakeTime = currentTime
                    onShakeDetected()
                }
            }

            lastX = x
            lastY = y
            lastZ = z
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for shake detection
    }
}
