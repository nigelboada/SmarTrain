package com.udl.smartrain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.udl.smartrain.R
import com.udl.smartrain.data.local.LocationProvider
import com.udl.smartrain.data.local.SensorProvider
import com.udl.smartrain.ml.ActivityClassifier
import com.udl.smartrain.ml.ActivityRecognitionState
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
        lastLocation = null
        distanceMeters = 0.0
        TrackingSessionState.start()

        serviceScope.launch {
            sensorProvider.accelerometerData.collect { values ->
                val x = values[0]
                val y = values[1]
                val z = values[2]

                buffer.add(floatArrayOf(x, y, z))

                if (buffer.size >= WINDOW_SIZE) {
                    val inputArray = arrayOf(buffer.toTypedArray())
                    val prediction = classifier.classify(inputArray)

                    ActivityRecognitionState.publish(prediction)

                    Log.d(
                        "ML_TRACKING",
                        "Activitat detectada: ${prediction.label} (${prediction.confidence})"
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

    private companion object {
        const val WINDOW_SIZE = 128
    }
}
