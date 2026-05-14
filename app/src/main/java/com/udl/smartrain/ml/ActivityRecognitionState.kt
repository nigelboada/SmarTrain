package com.udl.smartrain.ml

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActivityRecognitionState {
    private const val MAX_HISTORY_SIZE = 20
    private const val MAX_SESSION_TIMELINE_SIZE = 720
    private const val TIMELINE_SAMPLE_INTERVAL_MILLIS = 10_000L

    private val _currentPrediction = MutableStateFlow<ActivityPrediction?>(null)
    val currentPrediction: StateFlow<ActivityPrediction?> = _currentPrediction.asStateFlow()

    private val _predictionHistory = MutableStateFlow<List<ActivityPrediction>>(emptyList())
    val predictionHistory: StateFlow<List<ActivityPrediction>> = _predictionHistory.asStateFlow()

    private val sessionTimeline = mutableListOf<ActivityPrediction>()
    private val sessionLabelCounts = mutableMapOf<String, Int>()
    private var sessionConfidenceSum = 0.0
    private var sessionPredictionCount = 0
    private var sessionHighIntensityCount = 0

    fun publish(prediction: ActivityPrediction) {
        _currentPrediction.value = prediction
        _predictionHistory.value = (_predictionHistory.value + prediction).takeLast(MAX_HISTORY_SIZE)

        sessionPredictionCount += 1
        sessionConfidenceSum += prediction.confidence.toDouble()
        sessionLabelCounts[prediction.label] = (sessionLabelCounts[prediction.label] ?: 0) + 1
        if (prediction.label == "Alta intensitat") {
            sessionHighIntensityCount += 1
        }

        if (shouldKeepTimelinePoint(prediction)) {
            sessionTimeline += prediction
            compactTimelineIfNeeded()
        }
    }

    fun reset() {
        _currentPrediction.value = null
        _predictionHistory.value = emptyList()
        sessionTimeline.clear()
        sessionLabelCounts.clear()
        sessionConfidenceSum = 0.0
        sessionPredictionCount = 0
        sessionHighIntensityCount = 0
    }

    fun buildSessionSummary(): ActivitySessionSummary {
        if (sessionPredictionCount == 0) {
            return ActivitySessionSummary()
        }

        val dominantActivity = sessionLabelCounts
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()

        val avgConfidence = sessionConfidenceSum / sessionPredictionCount

        val timeline = sessionTimeline.joinToString(separator = "|") { prediction ->
            "${prediction.timestampMillis},${prediction.classIndex},${prediction.label},${prediction.modelLabel},${prediction.confidence}"
        }

        return ActivitySessionSummary(
            dominantActivity = dominantActivity,
            avgMlConfidence = avgConfidence,
            mlPredictionCount = sessionPredictionCount,
            highIntensityCount = sessionHighIntensityCount,
            activityTimeline = timeline
        )
    }

    private fun shouldKeepTimelinePoint(prediction: ActivityPrediction): Boolean {
        val lastTimelinePoint = sessionTimeline.lastOrNull() ?: return true
        if (prediction.label != lastTimelinePoint.label) {
            return true
        }

        return prediction.timestampMillis - lastTimelinePoint.timestampMillis >= TIMELINE_SAMPLE_INTERVAL_MILLIS
    }

    private fun compactTimelineIfNeeded() {
        if (sessionTimeline.size <= MAX_SESSION_TIMELINE_SIZE) {
            return
        }

        val compactedTimeline = sessionTimeline.filterIndexed { index, point ->
            val previous = sessionTimeline.getOrNull(index - 1)
            val next = sessionTimeline.getOrNull(index + 1)
            val isBoundary = previous?.label != point.label || next?.label != point.label

            index == 0 || index == sessionTimeline.lastIndex || isBoundary || index % 2 == 0
        }
        sessionTimeline.clear()
        sessionTimeline.addAll(compactedTimeline)
    }
}

data class ActivitySessionSummary(
    val dominantActivity: String = "",
    val avgMlConfidence: Double = 0.0,
    val mlPredictionCount: Int = 0,
    val highIntensityCount: Int = 0,
    val activityTimeline: String = ""
)
