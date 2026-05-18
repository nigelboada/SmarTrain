package com.udl.smartrain.ml

import com.udl.smartrain.data.local.AppLanguage
import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class OllamaRagGenerator(
    private val baseUrl: String,
    override val model: String,
    private val apiKey: String = ""
) : RagAnswerGenerator {
    override val provider: String = "ollama"

    override suspend fun generate(
        session: Session,
        retrievedDocuments: List<RagDocument>,
        language: AppLanguage
    ): SessionRagInsight = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(session, retrievedDocuments, language)
        val payload = JSONObject()
            .put("model", model)
            .put(
                "messages",
                org.json.JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", prompt)
                    )
            )
            .put("stream", false)
            .put("think", false)
            .put(
                "options",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("num_predict", 220)
            )

        val connection = (URL(generateUrl()).openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("ngrok-skip-browser-warning", "true")
        if (apiKey.isNotBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        connection.doOutput = true

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
        }

        val responseCode = connection.responseCode
        val responseText = readResponseText(connection, responseCode)
        if (responseCode !in HTTP_SUCCESS_RANGE) {
            error("Ollama HTTP $responseCode: ${responseText.ifBlank { "resposta buida" }}")
        }
        if (responseText.isBlank()) {
            error("Ollama ha retornat una resposta buida a ${generateUrl()}.")
        }

        val responseJson = JSONObject(responseText)
        val answer = responseJson
            .optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()
            .ifBlank { responseJson.optString("response").trim() }
        if (answer.isBlank()) {
            error("Ollama ha retornat una resposta buida.")
        }

        SessionRagInsight(
            title = SessionRagRecommender.localizedInsightTitle(language),
            answer = answer,
            sourceTitles = retrievedDocuments.map { it.title },
            sourceDetails = retrievedDocuments.mapIndexed { index, document ->
                RagSourceDetail(
                    id = document.id,
                    source = document.title,
                    category = document.category,
                    chunkId = index,
                    score = 1.0,
                    text = document.text
                )
            }
        )
    }

    private fun readResponseText(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in HTTP_SUCCESS_RANGE) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun generateUrl(): String {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        return if (normalizedBaseUrl.endsWith("/api")) {
            "$normalizedBaseUrl/chat"
        } else {
            "$normalizedBaseUrl/api/chat"
        }
    }

    private fun buildPrompt(session: Session, retrievedDocuments: List<RagDocument>, language: AppLanguage): String {
        val confidencePercent = (session.avgMlConfidence * 100).toInt()
        val context = retrievedDocuments.joinToString(separator = "\n") { document ->
            "[${document.id}] ${document.title} (${document.category}): ${document.text}"
        }
        val sessionSummary = SessionRagRecommender.localizedSessionSummary(session, language)
        val languageInstruction = when (language) {
            AppLanguage.CATALAN -> "Respon exclusivament en catala. No facis servir castella."
            AppLanguage.ENGLISH -> "Answer exclusively in English."
            AppLanguage.SPANISH -> "Responde exclusivamente en castellano."
            AppLanguage.CHINESE -> "\u8bf7\u53ea\u7528\u4e2d\u6587\u56de\u7b54\u3002"
        }

        return """
            You are the SmarTrain assistant. $languageInstruction Be brief and careful.
            Base the answer only on the retrieved context and the session data.
            Do not invent metrics or medical diagnoses. Include one actionable recommendation.

            Context RAG:
            $context

            Resum de sessio:
            $sessionSummary Confidence: $confidencePercent%.

            Resposta:
        """.trimIndent()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 30_000
        val HTTP_SUCCESS_RANGE = 200..299
    }
}
