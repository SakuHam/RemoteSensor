package com.example.sensor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sensor.ui.model.AccelerometerData
import com.example.sensor.ui.model.CalibrationData
import kotlin.math.abs
import kotlin.math.sqrt

class SensorForegroundService : Service(), SensorEventListener {

    private val TAG = "SensorForegroundService"

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val MAX_SAMPLES = 100
    private val recentReadings = Array<AccelerometerData?>(MAX_SAMPLES) { null }
    private var writeIndex = 0  // Points to where the next sample goes
    private var filledSamples = 0  // How many slots are actually filled (up to MAX_SAMPLES)

    // calibration = (avgX, avgY, avgZ, threshold)
    @Volatile
    private var calibration = Quadruple(0.02f, 0.02f, 0.20f, 0.1f)

    // Keep track of the number of triggers since last reset
    @Volatile
    private var triggerCountDuringCalibration = 0

    // A separate handler thread for iterative calibration logic
    private lateinit var calibrationThread: HandlerThread
    private lateinit var calibrationHandler: Handler

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()

        // Acquire the sensor manager and accelerometer
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Register listener
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Promote to Foreground Service with a notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Set up a separate thread for calibration tasks
        calibrationThread = HandlerThread("CalibrationThread")
        calibrationThread.start()
        calibrationHandler = Handler(calibrationThread.looper)

        // Register receivers
        registerReceiver(
            calibrationReceiver,
            IntentFilter(BroadcastActions.ACTION_CALIBRATION_REQUEST),
            RECEIVER_EXPORTED
        )

        registerReceiver(
            setterReceiver,
            IntentFilter(BroadcastActions.ACTION_SET_VALUE_REQUEST),
            RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(calibrationReceiver)
        unregisterReceiver(setterReceiver)
        sensorManager.unregisterListener(this)
        calibrationThread.quitSafely()
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
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                val deltaX = abs(x - calibration.first)
                val deltaY = abs(y - calibration.second)
                val deltaZ = abs(z - calibration.third)
                val d = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)

                // If we consider 'calibration.fourth' to be the threshold
                if (d >= calibration.fourth) {
                    // This reading "triggers" an event
                    triggerCountDuringCalibration++
                    val data = AccelerometerData(x, y, z)
                    addSample(data)
                    SensorRepository.updateSensorData(data)
                } else {
                    // If below threshold, ignore or do something else
                }
            }
        }
    }

    private fun addSample(sample: AccelerometerData) {
        synchronized(recentReadings) {
            recentReadings[writeIndex] = sample

            // Move the write index forward
            writeIndex = (writeIndex + 1) % MAX_SAMPLES

            // If we haven't filled up yet, increment
            if (filledSamples < MAX_SAMPLES) {
                filledSamples++
            }
        }
    }

    private val calibrationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BroadcastActions.ACTION_CALIBRATION_REQUEST) {
                Log.d(TAG, "Received calibration request broadcast")

                // Do the normal single calibration pass
                val (avgX, avgY, avgZ, maxDiff) = computeCalibration()
                calibration = Quadruple(avgX, avgY, avgZ, maxDiff)
                SensorRepository.updateSensorCalibrationData(
                    CalibrationData(avgX, avgY, avgZ, maxDiff)
                )

                // Also optionally start an adaptive calibration approach in background
                // If you only want the single pass, comment this out.
                startAdaptiveCalibration()

                val response = floatArrayOf(avgX, avgY, avgZ, maxDiff)
                val responseIntent = Intent(BroadcastActions.ACTION_CALIBRATION_RESPONSE)
                responseIntent.putExtra(BroadcastActions.EXTRA_CALIB_RESULT, response)
                sendBroadcast(responseIntent)
                Log.d(TAG, "Sent calibration response: $response")
            }
        }
    }

    private val setterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BroadcastActions.ACTION_SET_VALUE_REQUEST) {
                Log.d(TAG, "Received set value request broadcast")

                val delta = intent.getFloatExtra("SENSITIVITY", 0.0f)
                calibration = Quadruple(
                    calibration.first,
                    calibration.second,
                    calibration.third,
                    calibration.fourth + delta
                )
                SensorRepository.updateSensorCalibrationData(
                    CalibrationData(
                        calibration.first,
                        calibration.second,
                        calibration.third,
                        calibration.fourth
                    )
                )
            }
        }
    }

    /**
     * Computes the average (x,y,z) of the recentReadings and the maximum distance
     * from that average. Returns a 4-tuple: (avgX, avgY, avgZ, maxDistance).
     */
    private fun computeCalibration(): Quadruple<Float, Float, Float, Float> {
        var sumX = 0f
        var sumY = 0f
        var sumZ = 0f
        val sampleCount: Int

        synchronized(recentReadings) {
            sampleCount = filledSamples
            if (sampleCount == 0) {
                // No data
                return Quadruple(0f, 0f, 0f, 0f)
            }
            for (i in 0 until sampleCount) {
                val sampleIndex = (writeIndex - 1 - i + MAX_SAMPLES) % MAX_SAMPLES
                val reading = recentReadings[sampleIndex]
                if (reading != null) {
                    sumX += reading.x
                    sumY += reading.y
                    sumZ += reading.z
                }
            }
        }

        // Compute average
        val avgX = sumX / sampleCount
        val avgY = sumY / sampleCount
        val avgZ = sumZ / sampleCount

        // Find max distance
        var maxDistance = 0f
        synchronized(recentReadings) {
            for (i in 0 until sampleCount) {
                val sampleIndex = (writeIndex - 1 - i + MAX_SAMPLES) % MAX_SAMPLES
                val reading = recentReadings[sampleIndex]
                if (reading != null) {
                    val dx = reading.x - avgX
                    val dy = reading.y - avgY
                    val dz = reading.z - avgZ
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                    if (distance > maxDistance) {
                        maxDistance = distance
                    }
                }
            }
        }

        return Quadruple(avgX, avgY, avgZ, maxDistance)
    }

    private fun startAdaptiveCalibration() {
        calibrationHandler.post {
            val desiredTriggersPerSecond = 0
            val calibrationDurationMs = 3000L
            val maxIterations = 10
            var iteration = 0

            var minThreshold = 0.01f
            var maxThreshold = 5.0f

            while (iteration < maxIterations) {
                iteration++

                triggerCountDuringCalibration = 0

                // Run for a few seconds with the current threshold
                try {
                    Thread.sleep(calibrationDurationMs)
                } catch (e: InterruptedException) {
                    // handle interruption
                }

                val triggersPerSecond = triggerCountDuringCalibration / (calibrationDurationMs / 1000f)

                val error = triggersPerSecond - desiredTriggersPerSecond
                if (abs(error) < 1.0f) {
                    Log.d(TAG, "Adaptive calibration converged in $iteration iterations. " +
                            "Threshold=${calibration.fourth}, triggers/s=$triggersPerSecond")
                    break
                }

                // Simple “binary search” or naive step:
                // If we have too many triggers, threshold is too low -> raise threshold
                // If we have too few triggers, threshold is too high -> lower threshold
                if (error > 0) {
                    // We are getting more triggers than desired; increase threshold
                    minThreshold = calibration.fourth
                    calibration = calibration.copy(fourth = (calibration.fourth + maxThreshold) / 2f)
                } else {
                    // We are getting fewer triggers than desired; decrease threshold
                    maxThreshold = calibration.fourth
                    calibration = calibration.copy(fourth = (calibration.fourth + minThreshold) / 2f)
                }

                Log.d(TAG, "Iteration=$iteration, triggers/s=$triggersPerSecond, " +
                        "new threshold=${calibration.fourth}")
            }

            SensorRepository.updateSensorCalibrationData(
                CalibrationData(calibration.first, calibration.second,
                    calibration.third, calibration.fourth)
            )

            Log.d(TAG, "Adaptive calibration finished. Final threshold=${calibration.fourth}")
        }
    }

    /** A simple class that holds 4 typed values. */
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
