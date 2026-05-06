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
}
