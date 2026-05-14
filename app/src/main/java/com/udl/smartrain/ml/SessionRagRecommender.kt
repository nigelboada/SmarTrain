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

data class SessionRagGenerationResult(
    val insight: SessionRagInsight,
    val provider: String,
    val model: String,
    val latencyMillis: Long,
    val usedFallback: Boolean,
    val fallbackReason: String = ""
)

object SessionRagRecommender {

    private val documents = listOf(
        RagDocument(
            id = "kb_001",
            title = "Repos i recuperacio",
            category = "training_recommendation",
            text = "Quan una sessio mostra molta activitat de repos o baixa intensitat, SmarTrain recomana revisar si era una sessio de recuperacio, escalfament o tornada a la calma. Si l'objectiu era entrenament intens, cal augmentar progressivament blocs de carrera suau i exercicis tecnics amb pilota.",
            keywords = setOf("repos", "baixa", "recuperacio", "escalfament", "calma")
        ),
        RagDocument(
            id = "kb_002",
            title = "Desplacament suau",
            category = "training_recommendation",
            text = "Una sessio dominada per caminar o desplacament suau indica carrega aerobia baixa. Es pot interpretar com treball regeneratiu, activacio o fase tecnica. Per progressar, es recomana afegir intervals curts de carrera moderada i controlar que la fatiga no augmenti massa.",
            keywords = setOf("desplacament", "suau", "caminar", "aerobia", "tecnica", "progressar")
        ),
        RagDocument(
            id = "kb_003",
            title = "Alta intensitat aproximada",
            category = "training_recommendation",
            text = "Les classes pujar escales i baixar escales s'utilitzen com aproximacio tecnica a accions d'alta intensitat. Si apareixen sovint, la sessio pot contenir canvis de ritme, acceleracions o moviments exigents. Es recomana alternar aquests blocs amb recuperacions suficients.",
            keywords = setOf("alta", "intensitat", "escales", "acceleracions", "ritme", "exigent")
        ),
        RagDocument(
            id = "kb_004",
            title = "Confianca baixa del model",
            category = "model_interpretation",
            text = "Quan la confianca mitjana del model es baixa, la prediccio s'ha d'interpretar amb prudencia. Pot indicar soroll del sensor, mobil mal posicionat, activitat no representada al dataset o transicions entre moviments. En aquest cas, l'app ha de mostrar el resultat com orientatiu.",
            keywords = setOf("confianca", "baixa", "prudencia", "soroll", "posicio", "orientatiu")
        ),
        RagDocument(
            id = "kb_005",
            title = "Confianca alta del model",
            category = "model_interpretation",
            text = "Una confianca alta del model indica que la finestra de sensor s'assembla clarament a una classe apresa durant l'entrenament. Tot i aixi, en SmarTrain el model esta entrenat amb UCI HAR i no amb dades especifiques de futbol, per tant la lectura continua sent una aproximacio.",
            keywords = setOf("confianca", "alta", "model", "aproximacio", "uci", "har", "futbol")
        ),
        RagDocument(
            id = "kb_006",
            title = "Confusio seure dret",
            category = "model_interpretation",
            text = "La matriu de confusio mostra que les classes seure i dret son les mes similars. Aquesta confusio es esperable perque totes dues son posturals i tenen patrons d'acceleracio amb poca variacio. Per futbol, totes dues es poden agrupar com baixa intensitat o repos.",
            keywords = setOf("confusio", "seure", "dret", "postural", "repos", "baixa")
        ),
        RagDocument(
            id = "kb_007",
            title = "Model final CNN deep",
            category = "ml_experiment",
            text = "El model seleccionat per a l'entrega final es cnn_deep. Obte 96,01 per cent d'accuracy, 96,02 per cent de F1 weighted, ocupa 55.208 bytes en format TFLite i te una inferencia mitjana de 0,078 ms en l'entorn d'avaluacio.",
            keywords = setOf("cnn", "deep", "accuracy", "f1", "tflite", "inferencia")
        ),
        RagDocument(
            id = "kb_008",
            title = "Limitacio UCI HAR futbol",
            category = "ml_experiment",
            text = "UCI HAR no conte etiquetes especifiques de futbol com sprint, canvi de direccio, pressio o conduccio de pilota. Per aquest motiu, el model de SmarTrain valida la integracio tecnica pero no s'ha de presentar com un classificador final de rendiment futbolistic.",
            keywords = setOf("uci", "har", "futbol", "limitacio", "sprint", "direccio", "pilota")
        ),
        RagDocument(
            id = "kb_009",
            title = "Recomanacio post sessio",
            category = "training_recommendation",
            text = "Despres d'una sessio amb alta intensitat, es recomana incloure recuperacio, hidratacio i analisi de carrega. Si hi ha massa blocs intensos seguits, la propera sessio pot prioritzar tecnica, mobilitat o treball aerobi suau.",
            keywords = setOf("alta", "intensitat", "recuperacio", "hidratacio", "carrega", "mobilitat")
        ),
        RagDocument(
            id = "kb_010",
            title = "Us del RAG a SmarTrain",
            category = "rag_design",
            text = "El RAG de SmarTrain recupera fragments documentals sobre interpretacio del model, limitacions del dataset i recomanacions d'entrenament. La resposta final ha de basar-se en els fragments recuperats i evitar conclusions no suportades per les dades de la sessio.",
            keywords = setOf("rag", "fragments", "interpretacio", "limitacions", "recomanacions")
        ),
        RagDocument(
            id = "kb_011",
            title = "Estructura de dades del RAG",
            category = "rag_design",
            text = "La base documental del RAG es guarda a ml/rag/data/knowledge_base.jsonl. Cada document conte id, titol, categoria i text. Aquesta estructura permet afegir noves recomanacions sense modificar el codi del recuperador.",
            keywords = setOf("rag", "knowledge", "jsonl", "documents", "estructura")
        ),
        RagDocument(
            id = "kb_012",
            title = "Interpretacio de prediccions",
            category = "model_interpretation",
            text = "Les prediccions de SmarTrain s'han d'interpretar com finestres curtes de moviment. El resum de sessio utilitza activitat dominant, confianca mitjana, nombre de prediccions i recompte d'alta intensitat per donar context a l'usuari.",
            keywords = setOf("prediccions", "finestres", "activitat", "dominant", "confianca", "recompte")
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

        return buildRuleBasedInsight(session, retrieveDocuments(session))
    }

    suspend fun buildInsightWithGenerator(
        session: Session,
        settings: RagGenerationSettings = DebugRagGenerationSettings.settings.value
    ): SessionRagGenerationResult {
        if (session.mlPredictionCount == 0) {
            return SessionRagGenerationResult(
                insight = buildInsight(session),
                provider = RuleBasedRagAnswerGenerator.provider,
                model = RuleBasedRagAnswerGenerator.model,
                latencyMillis = 0,
                usedFallback = false
            )
        }

        val generator = generatorFor(settings)
        val retrieved = retrieveDocuments(session)
        val startedAt = System.currentTimeMillis()
        return runCatching {
            generator.generate(session, retrieved)
        }.fold(
            onSuccess = { insight ->
                SessionRagGenerationResult(
                    insight = insight,
                    provider = generator.provider,
                    model = generator.model,
                    latencyMillis = System.currentTimeMillis() - startedAt,
                    usedFallback = false
                )
            },
            onFailure = { error ->
                SessionRagGenerationResult(
                    insight = buildRuleBasedInsight(session, retrieved),
                    provider = RuleBasedRagAnswerGenerator.provider,
                    model = RuleBasedRagAnswerGenerator.model,
                    latencyMillis = System.currentTimeMillis() - startedAt,
                    usedFallback = settings.useOllama,
                    fallbackReason = fallbackReasonFor(settings, error)
                )
            }
        )
    }

    fun buildRuleBasedGenerationResult(session: Session): SessionRagGenerationResult {
        val startedAt = System.currentTimeMillis()
        return SessionRagGenerationResult(
            insight = buildInsight(session),
            provider = RuleBasedRagAnswerGenerator.provider,
            model = RuleBasedRagAnswerGenerator.model,
            latencyMillis = System.currentTimeMillis() - startedAt,
            usedFallback = false
        )
    }

    private fun generatorFor(settings: RagGenerationSettings): RagAnswerGenerator {
        return if (settings.useOllama) {
            OllamaRagGenerator(
                baseUrl = settings.ollamaBaseUrl,
                model = settings.ollamaModel,
                apiKey = settings.ollamaApiKey
            )
        } else {
            RuleBasedRagAnswerGenerator
        }
    }

    private fun fallbackReasonFor(settings: RagGenerationSettings, error: Throwable): String {
        val message = error.localizedMessage.orEmpty()
        return when {
            message.contains("failed to connect", ignoreCase = true) ||
                message.contains("Connection refused", ignoreCase = true) -> {
                "No s'ha pogut connectar amb Ollama a ${settings.ollamaBaseUrl}. En mobil fisic usa ngrok o la IP LAN del PC amb Ollama escoltant a 0.0.0.0."
            }
            message.contains("timeout", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) -> {
                "Ollama ha trigat massa a respondre amb el model ${settings.ollamaModel}."
            }
            message.contains("404", ignoreCase = true) -> {
                "Ollama no ha trobat el model ${settings.ollamaModel}. Comprova el nom exacte amb 'ollama list'."
            }
            message.isNotBlank() -> message
            else -> "Error generant el resum amb IA."
        }
    }

    fun buildRuleBasedInsight(
        session: Session,
        retrieved: List<RagDocument>
    ): SessionRagInsight {
        if (session.mlPredictionCount == 0) {
            return SessionRagInsight(
                title = "Resum ML no disponible",
                answer = "Aquesta sessio no conte prediccions ML suficients per generar una recomanacio.",
                sourceTitles = emptyList()
            )
        }

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

    fun retrieveDocuments(session: Session): List<RagDocument> {
        val queryTokens = buildQueryTokens(session)
        return documents
            .map { document -> document to score(document, queryTokens) }
            .sortedByDescending { (_, score) -> score }
            .take(TOP_K)
            .map { (document, _) -> document }
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
