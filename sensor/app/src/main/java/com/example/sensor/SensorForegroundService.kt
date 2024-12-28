package com.example.sensor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import com.example.sensor.ui.SensorViewModel
import com.example.sensor.ui.model.AccelerometerData
import kotlin.math.abs
import kotlin.math.sqrt

class SensorForegroundService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()

        // Acquire the sensor manager and accelerometer
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        // Register listener
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Promote to Foreground Service with a notification
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister sensor to avoid leaks
        sensorManager.unregisterListener(this)
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "sensor_service_channel"

        val channel = NotificationChannel(
            notificationChannelId,
            "Sensor Foreground Service Channel",
            NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Sensor Service")
            .setContentText("Running sensor updates...")
            .setSmallIcon(R.drawable.ic_notification) // your icon resource
            .build()
    }

    // Mandatory overrides for Service
    override fun onBind(intent: Intent?): IBinder? = null

    // SensorEventListener overrides
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Not used */ }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                // Get the accelerometer values
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                val deltaX = abs(x - 0.02)
                val deltaY = abs(y - 0.02)
                val deltaZ = abs(z - 0.20)
                val d = sqrt(deltaX*deltaX + deltaY*deltaY + deltaZ*deltaZ)

                if (d < 0.1) return

                // Update the ViewModel with the new accelerometer data
                val data = AccelerometerData(x, y, z)
                SensorRepository.updateSensorData(data)
            }
        }
    }
    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
