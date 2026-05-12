package com.udl.smartrain.data.local

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AccelerometerSample(
    val values: FloatArray,
    val timestampNanos: Long
)

class SensorProvider(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _accelerometerData = MutableStateFlow(
        AccelerometerSample(
            values = floatArrayOf(0f, 0f, 0f),
            timestampNanos = 0L
        )
    )
    val accelerometerData: StateFlow<AccelerometerSample> = _accelerometerData

    fun startListening() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, TARGET_SAMPLING_PERIOD_US)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            _accelerometerData.value = AccelerometerSample(
                values = event.values.clone(),
                timestampNanos = event.timestamp
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val TARGET_SAMPLING_PERIOD_US = 20_000
    }
}
