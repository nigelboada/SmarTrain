package com.udl.smartrain.service

import android.app.*
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.udl.smartrain.R
import com.udl.smartrain.data.local.LocationProvider
import com.udl.smartrain.data.local.SensorProvider
import com.udl.smartrain.ml.ActivityClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TrackingService : Service() {

    private val classifier = ActivityClassifier(this)
    private val buffer = mutableListOf<FloatArray>()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var sensorProvider: SensorProvider
    private lateinit var locationProvider: LocationProvider

    private val CHANNEL_ID = "tracking_channel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        sensorProvider = SensorProvider(this)
        locationProvider = LocationProvider(this)
        createNotificationChannel()
    }

    // Dins de TrackingService.kt, en el mètode onStartCommand:

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        sensorProvider.startListening()
        locationProvider.startTracking()

        // --- NOU: Escoltem el flux de dades (Flow) ---
        serviceScope.launch {
            sensorProvider.accelerometerData.collect { values ->
                val x = values[0]
                val y = values[1]
                val z = values[2]

                // Afegim al buffer
                buffer.add(floatArrayOf(x, y, z))

                // Quan tenim 128 dades, fem inferència
                if (buffer.size >= 128) {
                    // Transformem la llista a l'array 3D: [1][128][3]
                    val inputArray = arrayOf(buffer.toTypedArray())

                    // Classifiquem
                    val activityIndex = classifier.classify(inputArray)

                    Log.d("ML_TRACKING", "Activitat detectada: $activityIndex")

                    // Lliscament de la finestra: eliminem la primera dada
                    buffer.removeAt(0)
                }
            }
        }
        // ----------------------------------------------

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SERVICE_DEBUG", "Servei destruint-se...")

        sensorProvider.stopListening()
        locationProvider.stopTracking()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmarTrain en curs")
            .setContentText("Recollint dades de l'entrenament...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Assegura't que la icona existeix
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Canal de Tracking SmarTrain",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}