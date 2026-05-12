package com.udl.smartrain.ml

import com.udl.smartrain.domain.model.Session

data class RagDocument(
    val id: String,
    val title: String,
    val category: String,
    val text: String,
    val keywords: Set<String>
)

data class SessionRagInsight(
    val title: String,
    val answer: String,
    val sourceTitles: List<String>
)

object SessionRagRecommender {

    private val documents = listOf(
        RagDocument(
            id = "kb_001",
            title = "Repos i recuperacio",
            category = "training_recommendation",
            text = "Sessio dominada per repos o baixa intensitat. Es pot interpretar com recuperacio, escalfament o tornada a la calma. Si l'objectiu era intensitat, convindria afegir blocs progressius de carrera suau.",
            keywords = setOf("repos", "baixa", "recuperacio", "escalfament")
        ),
        RagDocument(
            id = "kb_002",
            title = "Desplacament suau",
            category = "training_recommendation",
            text = "Una sessio dominada per desplacament suau indica carrega aerobia baixa o treball tecnic lleuger. Es pot progressar afegint intervals moderats i controlant la fatiga.",
            keywords = setOf("desplacament", "suau", "caminar", "aerobia", "tecnica")
        ),
        RagDocument(
            id = "kb_003",
            title = "Alta intensitat aproximada",
            category = "training_recommendation",
            text = "Quan apareixen blocs d'alta intensitat, la sessio pot contenir acceleracions, canvis de ritme o moviments exigents. Conve alternar aquests blocs amb recuperacions suficients.",
            keywords = setOf("alta", "intensitat", "acceleracions", "ritme", "exigent")
        ),
        RagDocument(
            id = "kb_004",
            title = "Confianca baixa del model",
            category = "model_interpretation",
            text = "Si la confianca mitjana es baixa, la prediccio s'ha d'interpretar amb prudencia. Pot haver-hi soroll, mobil mal posicionat o activitat poc representada al dataset.",
            keywords = setOf("confianca", "baixa", "prudencia", "soroll", "posicio")
        ),
        RagDocument(
            id = "kb_005",
            title = "Confianca alta del model",
            category = "model_interpretation",
            text = "Una confianca alta indica que les finestres de sensor s'assemblen a patrons apresos pel model. Tot i aixi, el model es UCI HAR i continua sent una aproximacio.",
            keywords = setOf("confianca", "alta", "model", "aproximacio")
        ),
        RagDocument(
            id = "kb_006",
            title = "Limitacio UCI HAR futbol",
            category = "ml_experiment",
            text = "UCI HAR no conte etiquetes especifiques de futbol com sprint, canvi de direccio o conduccio de pilota. El model valida la integracio tecnica, no un classificador final futbolistic.",
            keywords = setOf("uci", "har", "futbol", "limitacio", "sprint")
        )
    )

    fun buildInsight(session: Session): SessionRagInsight {
        if (session.mlPredictionCount == 0) {
            return SessionRagInsight(
                title = "Resum ML no disponible",
                answer = "Aquesta sessio no conte prediccions ML suficients per generar una recomanacio.",
                sourceTitles = emptyList()
            )
        }

        val queryTokens = buildQueryTokens(session)
        val retrieved = documents
            .map { document -> document to score(document, queryTokens) }
            .sortedByDescending { (_, score) -> score }
            .take(TOP_K)
            .map { (document, _) -> document }

        val intensityRatio = session.highIntensityCount.toDouble() / session.mlPredictionCount
        val confidencePercent = (session.avgMlConfidence * 100).toInt()
        val title = "Interpretacio post sessio"
        val answer = buildString {
            append("Activitat dominant: ${session.dominantActivity}. ")
            append("Confianca mitjana: $confidencePercent%. ")
            append("Blocs d'alta intensitat: ${session.highIntensityCount}/${session.mlPredictionCount}. ")
            append(retrieved.take(2).joinToString(separator = " ") { it.text })
            if (intensityRatio > HIGH_INTENSITY_RATIO) {
                append(" Per a la propera sessio, prioritza recuperacio i control de carrega.")
            }
        }

        return SessionRagInsight(
            title = title,
            answer = answer,
            sourceTitles = retrieved.map { it.title }
        )
    }

    private fun buildQueryTokens(session: Session): Set<String> {
        val tokens = mutableSetOf<String>()
        tokens += tokenize(session.dominantActivity)
        tokens += "uci"
        tokens += "har"

        if (session.highIntensityCount > 0) {
            tokens += setOf("alta", "intensitat", "acceleracions")
        }

        if (session.avgMlConfidence < LOW_CONFIDENCE_THRESHOLD) {
            tokens += setOf("confianca", "baixa", "prudencia")
        } else {
            tokens += setOf("confianca", "alta", "model")
        }

        return tokens
    }

    private fun score(document: RagDocument, queryTokens: Set<String>): Int {
        return document.keywords.count { keyword -> keyword in queryTokens }
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { token -> token.length > 1 }
            .toSet()
    }

    private const val TOP_K = 3
    private const val LOW_CONFIDENCE_THRESHOLD = 0.60
    private const val HIGH_INTENSITY_RATIO = 0.30
}
