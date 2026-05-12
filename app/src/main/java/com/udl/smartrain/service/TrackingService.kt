package com.udl.smartrain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.SensorManager
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.udl.smartrain.R
import com.udl.smartrain.data.local.LocationProvider
import com.udl.smartrain.data.local.SensorProvider
import com.udl.smartrain.ml.ActivityClassifier
import com.udl.smartrain.ml.ActivityPrediction
import com.udl.smartrain.ml.ActivityRecognitionState
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TrackingService : Service() {

    private lateinit var classifier: ActivityClassifier
    private val buffer = mutableListOf<FloatArray>()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var sensorProvider: SensorProvider
    private lateinit var locationProvider: LocationProvider
    private var lastAcceptedSensorTimestampNanos: Long = 0L
    private var lastLocation: Location? = null
    private var distanceMeters: Double = 0.0

    private val channelId = "tracking_channel"
    private val notificationId = 1

    override fun onCreate() {
        super.onCreate()
        classifier = ActivityClassifier(applicationContext)
        sensorProvider = SensorProvider(this)
        locationProvider = LocationProvider(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, createNotification())
        sensorProvider.startListening()
        locationProvider.startTracking()
        ActivityRecognitionState.reset()
        buffer.clear()
        lastAcceptedSensorTimestampNanos = 0L
        lastLocation = null
        distanceMeters = 0.0
        TrackingSessionState.start()

        serviceScope.launch {
            sensorProvider.accelerometerData.collect { sample ->
                if (!shouldAcceptSensorSample(sample.timestampNanos)) {
                    return@collect
                }

                val values = sample.values
                val x = values[0] / SensorManager.GRAVITY_EARTH
                val y = values[1] / SensorManager.GRAVITY_EARTH
                val z = values[2] / SensorManager.GRAVITY_EARTH

                buffer.add(floatArrayOf(x, y, z))

                if (buffer.size >= WINDOW_SIZE) {
                    val normalizedWindow = normalizeWindowForUciModel(buffer)
                    val inputArray = arrayOf(normalizedWindow.toTypedArray())
                    val modelPrediction = classifier.classify(inputArray)
                    val prediction = if (isStationary(buffer)) {
                        ActivityPrediction(
                            classIndex = REST_CLASS_INDEX,
                            label = "Repos",
                            confidence = stationaryConfidence(buffer),
                            modelLabel = "Repos estable"
                        )
                    } else {
                        modelPrediction
                    }

                    ActivityRecognitionState.publish(prediction)

                    Log.d(
                        "ML_TRACKING",
                        "Activitat detectada: ${prediction.label} (${prediction.confidence}) " +
                            "model=${modelPrediction.modelLabel} raw=${values.joinToString()}"
                    )

                    buffer.removeAt(0)
                }
            }
        }

        serviceScope.launch {
            locationProvider.currentLocation.collect { location ->
                if (location == null) return@collect

                lastLocation?.let { previousLocation ->
                    distanceMeters += previousLocation.distanceTo(location).toDouble()
                    TrackingSessionState.updateDistance(distanceMeters)
                }
                lastLocation = location
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SERVICE_DEBUG", "Servei destruint-se...")

        sensorProvider.stopListening()
        locationProvider.stopTracking()
        TrackingSessionState.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SmarTrain en curs")
            .setContentText("Recollint dades de l'entrenament...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Canal de Tracking SmarTrain",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun shouldAcceptSensorSample(timestampNanos: Long): Boolean {
        if (timestampNanos == 0L) {
            return false
        }

        if (lastAcceptedSensorTimestampNanos == 0L) {
            lastAcceptedSensorTimestampNanos = timestampNanos
            return true
        }

        val elapsedNanos = timestampNanos - lastAcceptedSensorTimestampNanos
        if (elapsedNanos < TARGET_SAMPLING_PERIOD_NANOS) {
            return false
        }

        lastAcceptedSensorTimestampNanos = timestampNanos
        return true
    }

    private fun normalizeWindowForUciModel(window: List<FloatArray>): List<FloatArray> {
        val axisMeans = FloatArray(AXIS_COUNT) { axis ->
            window.map { sample -> sample[axis] }.average().toFloat()
        }
        val gravityAxis = axisMeans.indices.maxByOrNull { axis -> abs(axisMeans[axis]) } ?: 0
        val gravitySign = if (axisMeans[gravityAxis] >= 0f) 1f else -1f
        val remainingAxes = (0 until AXIS_COUNT).filter { axis -> axis != gravityAxis }

        return window.map { sample ->
            floatArrayOf(
                sample[gravityAxis] * gravitySign,
                sample[remainingAxes[0]] * gravitySign,
                sample[remainingAxes[1]] * gravitySign
            )
        }
    }

    private fun isStationary(window: List<FloatArray>): Boolean {
        return magnitudeStd(window) < STATIONARY_MAGNITUDE_STD_THRESHOLD
    }

    private fun stationaryConfidence(window: List<FloatArray>): Float {
        val confidence = 1f - (magnitudeStd(window) / STATIONARY_MAGNITUDE_STD_THRESHOLD)
        return confidence.coerceIn(0.65f, 0.99f)
    }

    private fun magnitudeStd(window: List<FloatArray>): Float {
        val magnitudes = window.map { sample ->
            sqrt(
                sample[0] * sample[0] +
                    sample[1] * sample[1] +
                    sample[2] * sample[2]
            )
        }
        val mean = magnitudes.average().toFloat()
        val variance = magnitudes
            .map { magnitude -> (magnitude - mean) * (magnitude - mean) }
            .average()
            .toFloat()

        return sqrt(variance)
    }

    private companion object {
        const val WINDOW_SIZE = 128
        const val AXIS_COUNT = 3
        const val REST_CLASS_INDEX = 3
        const val TARGET_SAMPLING_PERIOD_NANOS = 20_000_000L
        const val STATIONARY_MAGNITUDE_STD_THRESHOLD = 0.035f
    }
}
