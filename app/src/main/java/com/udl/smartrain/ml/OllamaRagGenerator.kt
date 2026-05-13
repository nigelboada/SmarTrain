package com.udl.smartrain.ml

import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class OllamaRagGenerator(
    private val baseUrl: String,
    private val model: String
) : RagAnswerGenerator {

    override suspend fun generate(
        session: Session,
        retrievedDocuments: List<RagDocument>
    ): SessionRagInsight = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(session, retrievedDocuments)
        val payload = JSONObject()
            .put("model", model)
            .put("prompt", prompt)
            .put("stream", false)
            .put(
                "options",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("num_predict", 220)
            )

        val connection = (URL("${baseUrl.trimEnd('/')}/api/generate").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
        }

        val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val answer = JSONObject(responseText).optString("response").trim()
        if (answer.isBlank()) {
            error("Ollama ha retornat una resposta buida.")
        }

        SessionRagInsight(
            title = "Interpretacio post sessio",
            answer = answer,
            sourceTitles = retrievedDocuments.map { it.title }
        )
    }

    private fun buildPrompt(session: Session, retrievedDocuments: List<RagDocument>): String {
        val confidencePercent = (session.avgMlConfidence * 100).toInt()
        val context = retrievedDocuments.joinToString(separator = "\n") { document ->
            "[${document.id}] ${document.title} (${document.category}): ${document.text}"
        }
        val sessionSummary = buildString {
            append("Activitat dominant: ${session.dominantActivity}. ")
            append("Confianca mitjana: $confidencePercent%. ")
            append("Blocs d'alta intensitat: ${session.highIntensityCount}/${session.mlPredictionCount}.")
        }

        return """
            Ets l'assistent de SmarTrain. Respon en catala, de forma breu i prudent.
            Basa la resposta nomes en el context recuperat i en les dades de la sessio.
            No inventis metriques ni diagnositcs medics. Inclou una recomanacio accionable.

            Context RAG:
            $context

            Resum de sessio:
            $sessionSummary

            Resposta:
        """.trimIndent()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}

object RagGenerationConfig {
    const val USE_OLLAMA = false
    const val OLLAMA_BASE_URL = "http://10.0.2.2:11434"
    const val OLLAMA_MODEL = "gemma3:1b"
}
