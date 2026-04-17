package com.udl.smartrain.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.udl.smartrain.R
import com.udl.smartrain.data.local.LocationProvider
import com.udl.smartrain.data.local.SensorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class TrackingService : Service() {

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        sensorProvider.startListening()
        locationProvider.startTracking()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorProvider.stopListening()
        locationProvider.stopTracking()
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