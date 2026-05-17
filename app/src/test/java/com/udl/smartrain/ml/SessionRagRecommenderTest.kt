package com.udl.smartrain.ml

import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRagRecommenderTest {

    @Test
    fun `returns unavailable insight when session has no ml predictions`() {
        val insight = SessionRagRecommender.buildInsight(Session(mlPredictionCount = 0))

        assertEquals("Resum ML no disponible", insight.title)
        assertTrue(insight.sourceTitles.isEmpty())
        assertTrue(insight.answer.contains("no conte prediccions ML"))
    }

    @Test
    fun `builds local rag insight with retrieved sources for high intensity session`() {
        val session = Session(
            dominantActivity = "Alta intensitat",
            avgMlConfidence = 0.82,
            mlPredictionCount = 10,
            highIntensityCount = 4
        )

        val insight = SessionRagRecommender.buildInsight(session)

        assertEquals("Interpretacio post sessio", insight.title)
        assertTrue(insight.answer.contains("Activitat dominant: Alta intensitat"))
        assertTrue(insight.answer.contains("prioritza recuperacio"))
        assertTrue(insight.sourceTitles.contains("Alta intensitat aproximada"))
        assertTrue(insight.sourceTitles.isNotEmpty())
    }

    @Test
    fun `remote rag connection failure is marked as fallback`() = runBlocking {
        val session = Session(
            dominantActivity = "Alta intensitat",
            avgMlConfidence = 0.82,
            mlPredictionCount = 10,
            highIntensityCount = 4
        )

        val result = SessionRagRecommender.buildInsightWithGenerator(
            session = session,
            settings = RagGenerationSettings(
                useRemoteRag = true,
                remoteRagBaseUrl = "http://127.0.0.1:1"
            )
        )

        assertTrue(result.usedFallback)
        assertEquals("rules", result.provider)
        assertTrue(result.fallbackReason.isNotBlank())
    }
}
