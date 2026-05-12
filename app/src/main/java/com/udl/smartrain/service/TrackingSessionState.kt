package com.udl.smartrain.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackingMetrics(
    val isTracking: Boolean = false,
    val startedAtMillis: Long? = null,
    val distanceMeters: Double = 0.0
)

object TrackingSessionState {
    private val _metrics = MutableStateFlow(TrackingMetrics())
    val metrics: StateFlow<TrackingMetrics> = _metrics.asStateFlow()

    fun start() {
        _metrics.value = TrackingMetrics(
            isTracking = true,
            startedAtMillis = System.currentTimeMillis(),
            distanceMeters = 0.0
        )
    }

    fun updateDistance(distanceMeters: Double) {
        _metrics.value = _metrics.value.copy(distanceMeters = distanceMeters)
    }

    fun stop() {
        _metrics.value = _metrics.value.copy(isTracking = false)
    }

    fun reset() {
        _metrics.value = TrackingMetrics()
    }
}
