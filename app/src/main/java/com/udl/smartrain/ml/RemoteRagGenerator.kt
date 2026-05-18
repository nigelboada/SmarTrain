package com.udl.smartrain.ml

import com.udl.smartrain.data.local.AppLanguage
import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RemoteRagGenerator(
    private val baseUrl: String
) : RagAnswerGenerator {
    override val provider: String = "remote-rag"
    override val model: String = "backend"

    override suspend fun generate(
        session: Session,
        retrievedDocuments: List<RagDocument>,
        language: AppLanguage
    ): SessionRagInsight = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("language", language.apiCode())
            .put("session", session.toJson())

        postInsight(endpoint(), payload, language)
    }

    suspend fun generateGuidedQuestion(
        session: Session,
        language: AppLanguage,
        questionId: String
    ): SessionRagInsight = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("language", language.apiCode())
            .put("question_id", questionId)
            .put("session", session.toJson())

        postInsight(guidedEndpoint(), payload, language)
    }

    private fun postInsight(endpoint: String, payload: JSONObject, language: AppLanguage): SessionRagInsight {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
        }

        val responseCode = connection.responseCode
        val responseText = readResponseText(connection, responseCode)
        if (responseCode !in HTTP_SUCCESS_RANGE) {
            error("Remote RAG HTTP $responseCode: $responseText")
        }
        val json = JSONObject(responseText)
        val title = json.optString("title").ifBlank { SessionRagRecommender.localizedInsightTitle(language) }
        val answer = json.optString("answer").trim()
        if (answer.isBlank()) {
            error("Remote RAG ha retornat una resposta buida.")
        }
        val sources = json.optJSONArray("sources") ?: JSONArray()
        val details = buildList {
            for (index in 0 until sources.length()) {
                val item = sources.optJSONObject(index) ?: continue
                add(
                    RagSourceDetail(
                        id = item.optString("id"),
                        source = item.optString("source"),
                        category = item.optString("category"),
                        chunkId = item.optInt("chunk_id", index),
                        score = item.optDouble("score", 0.0),
                        text = item.optString("text")
                    )
                )
            }
        }

        return SessionRagInsight(
            title = title,
            answer = answer,
            sourceTitles = details.map { it.source.ifBlank { it.id } },
            sourceDetails = details
        )
    }

    private fun endpoint(): String = "${baseUrl.trimEnd('/')}/rag/session-summary"
    private fun guidedEndpoint(): String = "${baseUrl.trimEnd('/')}/rag/guided-question"

    private fun readResponseText(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in HTTP_SUCCESS_RANGE) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun Session.toJson(): JSONObject {
        return JSONObject()
            .put("dominantActivity", dominantActivity)
            .put("avgMlConfidence", avgMlConfidence)
            .put("mlPredictionCount", mlPredictionCount)
            .put("highIntensityCount", highIntensityCount)
            .put("durationSeconds", durationSeconds)
            .put("distanceMetres", distanceMetres)
    }

    private fun AppLanguage.apiCode(): String = when (this) {
        AppLanguage.CATALAN -> "ca"
        AppLanguage.ENGLISH -> "en"
        AppLanguage.SPANISH -> "es"
        AppLanguage.CHINESE -> "zh"
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 70_000
        val HTTP_SUCCESS_RANGE = 200..299
    }
}
