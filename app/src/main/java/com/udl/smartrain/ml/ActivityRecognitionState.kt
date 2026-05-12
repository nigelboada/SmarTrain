package com.udl.smartrain.ml

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActivityRecognitionState {
    private const val MAX_HISTORY_SIZE = 20

    private val _currentPrediction = MutableStateFlow<ActivityPrediction?>(null)
    val currentPrediction: StateFlow<ActivityPrediction?> = _currentPrediction.asStateFlow()

    private val _predictionHistory = MutableStateFlow<List<ActivityPrediction>>(emptyList())
    val predictionHistory: StateFlow<List<ActivityPrediction>> = _predictionHistory.asStateFlow()

    fun publish(prediction: ActivityPrediction) {
        _currentPrediction.value = prediction
        _predictionHistory.value = (_predictionHistory.value + prediction).takeLast(MAX_HISTORY_SIZE)
    }

    fun reset() {
        _currentPrediction.value = null
        _predictionHistory.value = emptyList()
    }

    fun buildSessionSummary(): ActivitySessionSummary {
        val predictions = _predictionHistory.value
        if (predictions.isEmpty()) {
            return ActivitySessionSummary()
        }

        val dominantActivity = predictions
            .groupingBy { it.label }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()

        val avgConfidence = predictions
            .map { it.confidence.toDouble() }
            .average()

        val highIntensityCount = predictions.count { prediction ->
            prediction.label == "Alta intensitat"
        }

        val timeline = predictions.joinToString(separator = "|") { prediction ->
            "${prediction.timestampMillis},${prediction.classIndex},${prediction.label},${prediction.modelLabel},${prediction.confidence}"
        }

        return ActivitySessionSummary(
            dominantActivity = dominantActivity,
            avgMlConfidence = avgConfidence,
            mlPredictionCount = predictions.size,
            highIntensityCount = highIntensityCount,
            activityTimeline = timeline
        )
    }
}

data class ActivitySessionSummary(
    val dominantActivity: String = "",
    val avgMlConfidence: Double = 0.0,
    val mlPredictionCount: Int = 0,
    val highIntensityCount: Int = 0,
    val activityTimeline: String = ""
)
