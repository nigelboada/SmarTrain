package com.udl.smartrain.ml

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityRecognitionStateTest {

    @After
    fun tearDown() {
        ActivityRecognitionState.reset()
    }

    @Test
    fun `session summary uses full session stats while live history stays short`() {
        ActivityRecognitionState.reset()

        repeat(25) { index ->
            ActivityRecognitionState.publish(
                ActivityPrediction(
                    classIndex = 3,
                    label = "Repos",
                    confidence = 0.80f,
                    modelLabel = "Repos estable",
                    timestampMillis = index.toLong()
                )
            )
        }
        repeat(25) { index ->
            ActivityRecognitionState.publish(
                ActivityPrediction(
                    classIndex = 1,
                    label = "Alta intensitat",
                    confidence = 0.90f,
                    modelLabel = "Pujar escales",
                    timestampMillis = 25_000L + index
                )
            )
        }

        val summary = ActivityRecognitionState.buildSessionSummary()

        assertEquals(20, ActivityRecognitionState.predictionHistory.value.size)
        assertEquals(50, summary.mlPredictionCount)
        assertEquals(25, summary.highIntensityCount)
        assertTrue(summary.activityTimeline.contains("Repos"))
        assertTrue(summary.activityTimeline.contains("Alta intensitat"))
    }
}
