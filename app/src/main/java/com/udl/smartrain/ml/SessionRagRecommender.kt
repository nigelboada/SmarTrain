package com.udl.smartrain.ml

import android.content.Context
import com.udl.smartrain.data.local.AppLanguage
import com.udl.smartrain.domain.model.Session
import java.io.File

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
    val sourceTitles: List<String>,
    val sourceDetails: List<RagSourceDetail> = emptyList()
)

data class RagSourceDetail(
    val id: String,
    val source: String,
    val category: String,
    val chunkId: Int,
    val score: Double,
    val text: String
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

    private var documents: List<RagDocument> = loadLocalKnowledgeBase()

    fun initialize(context: Context) {
        val loaded = runCatching {
            context.assets.open("knowledge_base.jsonl").bufferedReader(Charsets.UTF_8).use { reader ->
                parseKnowledgeBase(reader.readText())
            }
        }.getOrDefault(emptyList())
        if (loaded.isNotEmpty()) {
            documents = loaded
        }
    }

    fun loadDocumentsForTest(jsonl: String) {
        documents = parseKnowledgeBase(jsonl)
    }

    private fun loadLocalKnowledgeBase(): List<RagDocument> {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File("ml/rag/data/knowledge_base.jsonl"),
            File("../ml/rag/data/knowledge_base.jsonl"),
            File(userDir, "ml/rag/data/knowledge_base.jsonl"),
            File(userDir, "../ml/rag/data/knowledge_base.jsonl"),
            File(userDir, "../../ml/rag/data/knowledge_base.jsonl")
        )
        return candidates.firstOrNull { it.exists() }
            ?.let { file -> parseKnowledgeBase(file.readText(Charsets.UTF_8)) }
            .orEmpty()
    }

    private fun parseKnowledgeBase(jsonl: String): List<RagDocument> {
        return jsonl.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                runCatching {
                    val title = line.jsonStringValue("title")
                    val category = line.jsonStringValue("category")
                    val text = line.jsonStringValue("text")
                    RagDocument(
                        id = line.jsonStringValue("id"),
                        title = title,
                        category = category,
                        text = text,
                        keywords = tokenize("$title $category $text")
                    )
                }.getOrNull()
            }
            .toList()
    }

    private fun String.jsonStringValue(key: String): String {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        return pattern.find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?.replace("\\t", "\t")
            ?.replace("\\\\", "\\")
            .orEmpty()
    }

    fun buildInsight(session: Session, language: AppLanguage = AppLanguage.CATALAN): SessionRagInsight {
        if (session.mlPredictionCount == 0) {
            return unavailableInsight(language)
        }

        return buildRuleBasedInsight(session, retrieveDocuments(session), language)
    }

    suspend fun buildInsightWithGenerator(
        session: Session,
        settings: RagGenerationSettings = DebugRagGenerationSettings.settings.value,
        language: AppLanguage = AppLanguage.CATALAN
    ): SessionRagGenerationResult {
        if (session.mlPredictionCount == 0) {
            return SessionRagGenerationResult(
                insight = buildInsight(session, language),
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
            generator.generate(session, retrieved, language)
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
                    insight = buildRuleBasedInsight(session, retrieved, language),
                    provider = RuleBasedRagAnswerGenerator.provider,
                    model = RuleBasedRagAnswerGenerator.model,
                    latencyMillis = System.currentTimeMillis() - startedAt,
                    usedFallback = settings.useOllama,
                    fallbackReason = fallbackReasonFor(settings, error, language)
                )
            }
        )
    }

    fun buildRuleBasedGenerationResult(session: Session, language: AppLanguage = AppLanguage.CATALAN): SessionRagGenerationResult {
        val startedAt = System.currentTimeMillis()
        return SessionRagGenerationResult(
            insight = buildInsight(session, language),
            provider = RuleBasedRagAnswerGenerator.provider,
            model = RuleBasedRagAnswerGenerator.model,
            latencyMillis = System.currentTimeMillis() - startedAt,
            usedFallback = false
        )
    }

    private fun generatorFor(settings: RagGenerationSettings): RagAnswerGenerator {
        return when {
            settings.useRemoteRag -> RemoteRagGenerator(settings.remoteRagBaseUrl)
            settings.useOllama -> {
                OllamaRagGenerator(
                    baseUrl = settings.ollamaBaseUrl,
                    model = settings.ollamaModel,
                    apiKey = settings.ollamaApiKey
                )
            }
            else -> RuleBasedRagAnswerGenerator
        }
    }

    private fun fallbackReasonFor(settings: RagGenerationSettings, error: Throwable, language: AppLanguage): String {
        val message = error.localizedMessage.orEmpty()
        return when {
            message.contains("failed to connect", ignoreCase = true) ||
                message.contains("Connection refused", ignoreCase = true) -> {
                when (language) {
                    AppLanguage.CATALAN -> "No s'ha pogut connectar amb Ollama a ${settings.ollamaBaseUrl}. En mobil fisic usa ngrok o la IP LAN del PC amb Ollama escoltant a 0.0.0.0."
                    AppLanguage.ENGLISH -> "Could not connect to Ollama at ${settings.ollamaBaseUrl}. On a physical phone, use ngrok or the PC LAN IP with Ollama listening on 0.0.0.0."
                    AppLanguage.SPANISH -> "No se ha podido conectar con Ollama en ${settings.ollamaBaseUrl}. En movil fisico usa ngrok o la IP LAN del PC con Ollama escuchando en 0.0.0.0."
                    AppLanguage.CHINESE -> "\u65e0\u6cd5\u8fde\u63a5\u5230 ${settings.ollamaBaseUrl} \u7684 Ollama\u3002\u5728\u5b9e\u4f53\u624b\u673a\u4e0a\uff0c\u8bf7\u4f7f\u7528 ngrok \u6216 PC \u7684 LAN IP\uff0c\u5e76\u8ba9 Ollama \u76d1\u542c 0.0.0.0\u3002"
                }
            }
            message.contains("timeout", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) -> {
                when (language) {
                    AppLanguage.CATALAN -> "Ollama ha trigat massa a respondre amb el model ${settings.ollamaModel}."
                    AppLanguage.ENGLISH -> "Ollama took too long to respond with model ${settings.ollamaModel}."
                    AppLanguage.SPANISH -> "Ollama ha tardado demasiado en responder con el modelo ${settings.ollamaModel}."
                    AppLanguage.CHINESE -> "Ollama \u4f7f\u7528\u6a21\u578b ${settings.ollamaModel} \u54cd\u5e94\u8d85\u65f6\u3002"
                }
            }
            message.contains("404", ignoreCase = true) -> {
                if (settings.ollamaBaseUrl.contains("ollama.com", ignoreCase = true)) {
                    when (language) {
                        AppLanguage.CATALAN -> "Ollama Cloud no ha trobat el model ${settings.ollamaModel}. Usa un nom retornat per https://ollama.com/api/tags i no un model local com gemma3:1b."
                        AppLanguage.ENGLISH -> "Ollama Cloud could not find model ${settings.ollamaModel}. Use a name returned by https://ollama.com/api/tags, not a local model such as gemma3:1b."
                        AppLanguage.SPANISH -> "Ollama Cloud no ha encontrado el modelo ${settings.ollamaModel}. Usa un nombre devuelto por https://ollama.com/api/tags y no un modelo local como gemma3:1b."
                        AppLanguage.CHINESE -> "Ollama Cloud \u627e\u4e0d\u5230\u6a21\u578b ${settings.ollamaModel}\u3002\u8bf7\u4f7f\u7528 https://ollama.com/api/tags \u8fd4\u56de\u7684\u540d\u79f0\uff0c\u800c\u4e0d\u662f gemma3:1b \u8fd9\u6837\u7684\u672c\u5730\u6a21\u578b\u3002"
                    }
                } else {
                    when (language) {
                        AppLanguage.CATALAN -> "Ollama local no ha trobat el model ${settings.ollamaModel}. Comprova el nom exacte amb 'ollama list' al PC."
                        AppLanguage.ENGLISH -> "Local Ollama could not find model ${settings.ollamaModel}. Check the exact name with 'ollama list' on the PC."
                        AppLanguage.SPANISH -> "Ollama local no ha encontrado el modelo ${settings.ollamaModel}. Comprueba el nombre exacto con 'ollama list' en el PC."
                        AppLanguage.CHINESE -> "\u672c\u5730 Ollama \u627e\u4e0d\u5230\u6a21\u578b ${settings.ollamaModel}\u3002\u8bf7\u5728 PC \u4e0a\u7528 'ollama list' \u68c0\u67e5\u51c6\u786e\u540d\u79f0\u3002"
                    }
                }
            }
            message.contains("401", ignoreCase = true) ||
                message.contains("403", ignoreCase = true) -> {
                when (language) {
                    AppLanguage.CATALAN -> "Ollama ha rebutjat la peticio. Si uses cloud, revisa que la API key sigui valida i que el model ${settings.ollamaModel} estigui disponible per al teu compte."
                    AppLanguage.ENGLISH -> "Ollama rejected the request. If you use cloud, check that the API key is valid and model ${settings.ollamaModel} is available for your account."
                    AppLanguage.SPANISH -> "Ollama ha rechazado la peticion. Si usas cloud, revisa que la API key sea valida y que el modelo ${settings.ollamaModel} este disponible para tu cuenta."
                    AppLanguage.CHINESE -> "Ollama \u62d2\u7edd\u4e86\u8bf7\u6c42\u3002\u5982\u679c\u4f7f\u7528\u4e91\u7aef\uff0c\u8bf7\u68c0\u67e5 API key \u662f\u5426\u6709\u6548\uff0c\u4ee5\u53ca\u6a21\u578b ${settings.ollamaModel} \u662f\u5426\u5bf9\u4f60\u7684\u8d26\u6237\u53ef\u7528\u3002"
                }
            }
            message.contains("resposta buida", ignoreCase = true) ||
                message.contains("End of input", ignoreCase = true) -> {
                when (language) {
                    AppLanguage.CATALAN -> "Ollama ha retornat una resposta buida. Revisa la Base URL, el model i la API key."
                    AppLanguage.ENGLISH -> "Ollama returned an empty response. Check the base URL, model and API key."
                    AppLanguage.SPANISH -> "Ollama ha devuelto una respuesta vacia. Revisa la URL base, el modelo y la API key."
                    AppLanguage.CHINESE -> "Ollama \u8fd4\u56de\u4e86\u7a7a\u54cd\u5e94\u3002\u8bf7\u68c0\u67e5\u57fa\u7840 URL\u3001\u6a21\u578b\u548c API key\u3002"
                }
            }
            message.isNotBlank() -> message
            else -> when (language) {
                AppLanguage.CATALAN -> "Error generant el resum amb IA."
                AppLanguage.ENGLISH -> "Error generating the AI summary."
                AppLanguage.SPANISH -> "Error generando el resumen con IA."
                AppLanguage.CHINESE -> "\u751f\u6210 AI \u603b\u7ed3\u65f6\u51fa\u9519\u3002"
            }
        }
    }

    fun buildRuleBasedInsight(
        session: Session,
        retrieved: List<RagDocument>,
        language: AppLanguage = AppLanguage.CATALAN
    ): SessionRagInsight {
        if (session.mlPredictionCount == 0) {
            return unavailableInsight(language)
        }

        val intensityRatio = session.highIntensityCount.toDouble() / session.mlPredictionCount
        val confidencePercent = (session.avgMlConfidence * 100).toInt()
        val title = localizedInsightTitle(language)
        val answer = localizedRuleBasedAnswer(
            session = session,
            retrieved = retrieved,
            confidencePercent = confidencePercent,
            addHighIntensityAdvice = intensityRatio > HIGH_INTENSITY_RATIO,
            language = language
        )

        return SessionRagInsight(
            title = title,
            answer = answer,
            sourceTitles = retrieved.map { localizedDocument(it, language).title },
            sourceDetails = retrieved.mapIndexed { index, document ->
                val localized = localizedDocument(document, language)
                RagSourceDetail(
                    id = localized.id,
                    source = localized.title,
                    category = localized.category,
                    chunkId = index,
                    score = 1.0,
                    text = localized.text
                )
            }
        )
    }

    fun localizedInsightTitle(language: AppLanguage): String = when (language) {
        AppLanguage.CATALAN -> "Interpretacio post sessio"
        AppLanguage.ENGLISH -> "Post-session interpretation"
        AppLanguage.SPANISH -> "Interpretacion post sesion"
        AppLanguage.CHINESE -> "\u8bad\u7ec3\u540e\u89e3\u8bfb"
    }

    fun localizedSessionSummary(session: Session, language: AppLanguage): String {
        val confidencePercent = (session.avgMlConfidence * 100).toInt()
        val activity = localizedActivity(session.dominantActivity, language)
        return when (language) {
            AppLanguage.CATALAN -> "Activitat dominant: $activity. Confianca mitjana: $confidencePercent%. Blocs d'alta intensitat: ${session.highIntensityCount}/${session.mlPredictionCount}."
            AppLanguage.ENGLISH -> "Dominant activity: $activity. Average confidence: $confidencePercent%. High-intensity blocks: ${session.highIntensityCount}/${session.mlPredictionCount}."
            AppLanguage.SPANISH -> "Actividad dominante: $activity. Confianza media: $confidencePercent%. Bloques de alta intensidad: ${session.highIntensityCount}/${session.mlPredictionCount}."
            AppLanguage.CHINESE -> "\u4e3b\u8981\u6d3b\u52a8\uff1a$activity\u3002\u5e73\u5747\u7f6e\u4fe1\u5ea6\uff1a$confidencePercent%\u3002\u9ad8\u5f3a\u5ea6\u7247\u6bb5\uff1a${session.highIntensityCount}/${session.mlPredictionCount}\u3002"
        }
    }

    private fun unavailableInsight(language: AppLanguage): SessionRagInsight {
        return when (language) {
            AppLanguage.CATALAN -> SessionRagInsight(
                title = "Resum ML no disponible",
                answer = "Aquesta sessio no conte prediccions ML suficients per generar una recomanacio.",
                sourceTitles = emptyList()
            )
            AppLanguage.ENGLISH -> SessionRagInsight(
                title = "ML summary unavailable",
                answer = "This session does not contain enough ML predictions to generate a recommendation.",
                sourceTitles = emptyList()
            )
            AppLanguage.SPANISH -> SessionRagInsight(
                title = "Resumen ML no disponible",
                answer = "Esta sesion no contiene suficientes predicciones ML para generar una recomendacion.",
                sourceTitles = emptyList()
            )
            AppLanguage.CHINESE -> SessionRagInsight(
                title = "\u65e0\u53ef\u7528 ML \u603b\u7ed3",
                answer = "\u6b64\u8bad\u7ec3\u6ca1\u6709\u8db3\u591f\u7684 ML \u9884\u6d4b\u6765\u751f\u6210\u5efa\u8bae\u3002",
                sourceTitles = emptyList()
            )
        }
    }

    private fun localizedRuleBasedAnswer(
        session: Session,
        retrieved: List<RagDocument>,
        confidencePercent: Int,
        addHighIntensityAdvice: Boolean,
        language: AppLanguage
    ): String {
        val context = retrieved.take(2).joinToString(separator = " ") { localizedDocument(it, language).text }
        val activity = localizedActivity(session.dominantActivity, language)
        val highIntensityBlocks = "${session.highIntensityCount}/${session.mlPredictionCount}"
        val advice = if (addHighIntensityAdvice) {
            when (language) {
                AppLanguage.CATALAN -> " Per a la propera sessio, prioritza recuperacio i control de carrega."
                AppLanguage.ENGLISH -> " For the next session, prioritize recovery and load control."
                AppLanguage.SPANISH -> " Para la proxima sesion, prioriza recuperacion y control de carga."
                AppLanguage.CHINESE -> " \u4e0b\u4e00\u6b21\u8bad\u7ec3\u8bf7\u4f18\u5148\u8003\u8651\u6062\u590d\u548c\u8d1f\u8377\u63a7\u5236\u3002"
            }
        } else {
            ""
        }

        return when (language) {
            AppLanguage.CATALAN -> "Activitat dominant: $activity. Confianca mitjana: $confidencePercent%. Blocs d'alta intensitat: $highIntensityBlocks. $context$advice"
            AppLanguage.ENGLISH -> "Dominant activity: $activity. Average confidence: $confidencePercent%. High-intensity blocks: $highIntensityBlocks. $context$advice"
            AppLanguage.SPANISH -> "Actividad dominante: $activity. Confianza media: $confidencePercent%. Bloques de alta intensidad: $highIntensityBlocks. $context$advice"
            AppLanguage.CHINESE -> "\u4e3b\u8981\u6d3b\u52a8\uff1a$activity\u3002\u5e73\u5747\u7f6e\u4fe1\u5ea6\uff1a$confidencePercent%\u3002\u9ad8\u5f3a\u5ea6\u7247\u6bb5\uff1a$highIntensityBlocks\u3002$context$advice"
        }
    }

    private fun localizedActivity(activity: String, language: AppLanguage): String {
        return when (activity) {
            "Alta intensitat" -> when (language) {
                AppLanguage.CATALAN -> "Alta intensitat"
                AppLanguage.ENGLISH -> "High intensity"
                AppLanguage.SPANISH -> "Alta intensidad"
                AppLanguage.CHINESE -> "\u9ad8\u5f3a\u5ea6"
            }
            "Desplacament suau" -> when (language) {
                AppLanguage.CATALAN -> "Desplacament suau"
                AppLanguage.ENGLISH -> "Light movement"
                AppLanguage.SPANISH -> "Desplazamiento suave"
                AppLanguage.CHINESE -> "\u8f7b\u5ea6\u79fb\u52a8"
            }
            "Repos" -> when (language) {
                AppLanguage.CATALAN -> "Repos"
                AppLanguage.ENGLISH -> "Rest"
                AppLanguage.SPANISH -> "Reposo"
                AppLanguage.CHINESE -> "\u4f11\u606f"
            }
            else -> if (activity.isBlank()) {
                when (language) {
                    AppLanguage.CATALAN -> "Desconeguda"
                    AppLanguage.ENGLISH -> "Unknown"
                    AppLanguage.SPANISH -> "Desconocida"
                    AppLanguage.CHINESE -> "\u672a\u77e5"
                }
            } else {
                activity
            }
        }
    }

    private fun localizedDocument(document: RagDocument, language: AppLanguage): RagDocument {
        if (language == AppLanguage.CATALAN) return document

        val localized = when (document.id) {
            "kb_001" -> when (language) {
                AppLanguage.ENGLISH -> "Rest and recovery" to "When a session shows a lot of rest or low-intensity activity, SmarTrain recommends checking whether it was a recovery session, warm-up or cool-down. If the goal was intense training, progressively add light running blocks and technical ball drills."
                AppLanguage.SPANISH -> "Reposo y recuperacion" to "Cuando una sesion muestra mucha actividad de reposo o baja intensidad, SmarTrain recomienda revisar si era una sesion de recuperacion, calentamiento o vuelta a la calma. Si el objetivo era entrenamiento intenso, aumenta progresivamente bloques de carrera suave y ejercicios tecnicos con balon."
                AppLanguage.CHINESE -> "\u4f11\u606f\u4e0e\u6062\u590d" to "\u5f53\u8bad\u7ec3\u663e\u793a\u5927\u91cf\u4f11\u606f\u6216\u4f4e\u5f3a\u5ea6\u6d3b\u52a8\u65f6\uff0cSmarTrain \u5efa\u8bae\u68c0\u67e5\u5b83\u662f\u5426\u662f\u6062\u590d\u3001\u70ed\u8eab\u6216\u653e\u677e\u8bad\u7ec3\u3002\u5982\u679c\u76ee\u6807\u662f\u9ad8\u5f3a\u5ea6\u8bad\u7ec3\uff0c\u53ef\u9010\u6b65\u589e\u52a0\u6162\u8dd1\u7247\u6bb5\u548c\u5e26\u7403\u6280\u672f\u7ec3\u4e60\u3002"
                AppLanguage.CATALAN -> document.title to document.text
            }
            "kb_002" -> when (language) {
                AppLanguage.ENGLISH -> "Light movement" to "A session dominated by walking or light movement indicates a low aerobic load. It can be interpreted as regenerative work, activation or a technical phase. To progress, add short moderate running intervals and monitor fatigue."
                AppLanguage.SPANISH -> "Desplazamiento suave" to "Una sesion dominada por caminar o desplazamiento suave indica carga aerobica baja. Puede interpretarse como trabajo regenerativo, activacion o fase tecnica. Para progresar, anade intervalos cortos de carrera moderada y controla la fatiga."
                AppLanguage.CHINESE -> "\u8f7b\u5ea6\u79fb\u52a8" to "\u4ee5\u6b65\u884c\u6216\u8f7b\u5ea6\u79fb\u52a8\u4e3a\u4e3b\u7684\u8bad\u7ec3\u8868\u793a\u6709\u6c27\u8d1f\u8377\u8f83\u4f4e\u3002\u5b83\u53ef\u80fd\u662f\u6062\u590d\u3001\u6fc0\u6d3b\u6216\u6280\u672f\u9636\u6bb5\u3002\u82e5\u8981\u63d0\u5347\uff0c\u53ef\u52a0\u5165\u77ed\u65f6\u4e2d\u7b49\u8dd1\u6b65\u95f4\u6b47\u5e76\u76d1\u63a7\u75b2\u52b3\u3002"
                AppLanguage.CATALAN -> document.title to document.text
            }
            "kb_003" -> when (language) {
                AppLanguage.ENGLISH -> "Approximate high intensity" to "The walking-upstairs and walking-downstairs classes are used as a technical approximation of high-intensity actions. If they appear often, the session may contain changes of pace, accelerations or demanding movements. Alternate these blocks with enough recovery."
                AppLanguage.SPANISH -> "Alta intensidad aproximada" to "Las clases subir escaleras y bajar escaleras se usan como aproximacion tecnica a acciones de alta intensidad. Si aparecen a menudo, la sesion puede contener cambios de ritmo, aceleraciones o movimientos exigentes. Alterna estos bloques con recuperacion suficiente."
                AppLanguage.CHINESE -> "\u8fd1\u4f3c\u9ad8\u5f3a\u5ea6" to "\u4e0a\u697c\u548c\u4e0b\u697c\u7c7b\u522b\u88ab\u7528\u4f5c\u9ad8\u5f3a\u5ea6\u52a8\u4f5c\u7684\u6280\u672f\u8fd1\u4f3c\u3002\u5982\u679c\u5b83\u4eec\u9891\u7e41\u51fa\u73b0\uff0c\u8bad\u7ec3\u53ef\u80fd\u5305\u542b\u53d8\u901f\u3001\u52a0\u901f\u6216\u9ad8\u8981\u6c42\u52a8\u4f5c\u3002\u5efa\u8bae\u5728\u8fd9\u4e9b\u7247\u6bb5\u4e4b\u95f4\u5b89\u6392\u8db3\u591f\u6062\u590d\u3002"
                AppLanguage.CATALAN -> document.title to document.text
            }
            "kb_004" -> when (language) {
                AppLanguage.ENGLISH -> "Low model confidence" to "When average model confidence is low, the prediction should be interpreted carefully. It may indicate sensor noise, poor phone placement, activity not represented in the dataset or transitions between movements. The app should show the result as guidance."
                AppLanguage.SPANISH -> "Confianza baja del modelo" to "Cuando la confianza media del modelo es baja, la prediccion debe interpretarse con prudencia. Puede indicar ruido del sensor, movil mal colocado, actividad no representada en el dataset o transiciones entre movimientos. La app debe mostrar el resultado como orientativo."
                AppLanguage.CHINESE -> "\u6a21\u578b\u7f6e\u4fe1\u5ea6\u4f4e" to "\u5f53\u6a21\u578b\u5e73\u5747\u7f6e\u4fe1\u5ea6\u8f83\u4f4e\u65f6\uff0c\u5e94\u8c28\u614e\u89e3\u8bfb\u9884\u6d4b\u3002\u5b83\u53ef\u80fd\u8868\u793a\u4f20\u611f\u5668\u566a\u58f0\u3001\u624b\u673a\u4f4d\u7f6e\u4e0d\u4f73\u3001\u6570\u636e\u96c6\u4e2d\u672a\u8986\u76d6\u7684\u6d3b\u52a8\u6216\u52a8\u4f5c\u8fc7\u6e21\u3002\u5e94\u5c06\u7ed3\u679c\u4f5c\u4e3a\u53c2\u8003\u3002"
                AppLanguage.CATALAN -> document.title to document.text
            }
            "kb_005" -> when (language) {
                AppLanguage.ENGLISH -> "High model confidence" to "High model confidence indicates that the sensor window clearly resembles a class learned during training. Even so, SmarTrain uses UCI HAR rather than football-specific data, so the reading remains an approximation."
                AppLanguage.SPANISH -> "Confianza alta del modelo" to "Una confianza alta indica que la ventana del sensor se parece claramente a una clase aprendida durante el entrenamiento. Aun asi, SmarTrain usa UCI HAR y no datos especificos de futbol, asi que la lectura sigue siendo aproximada."
                AppLanguage.CHINESE -> "\u6a21\u578b\u7f6e\u4fe1\u5ea6\u9ad8" to "\u9ad8\u7f6e\u4fe1\u5ea6\u8868\u793a\u4f20\u611f\u5668\u7a97\u53e3\u4e0e\u8bad\u7ec3\u4e2d\u5b66\u5230\u7684\u67d0\u4e00\u7c7b\u660e\u663e\u76f8\u4f3c\u3002\u5373\u4fbf\u5982\u6b64\uff0cSmarTrain \u4f7f\u7528 UCI HAR \u800c\u975e\u8db3\u7403\u4e13\u7528\u6570\u636e\uff0c\u56e0\u6b64\u89e3\u8bfb\u4ecd\u662f\u8fd1\u4f3c\u503c\u3002"
                AppLanguage.CATALAN -> document.title to document.text
            }
            else -> when (language) {
                AppLanguage.ENGLISH -> "Prediction interpretation" to "SmarTrain predictions should be read as short movement windows. The session summary uses dominant activity, average confidence, prediction count and high-intensity count to give the user context."
                AppLanguage.SPANISH -> "Interpretacion de predicciones" to "Las predicciones de SmarTrain deben interpretarse como ventanas cortas de movimiento. El resumen de sesion usa actividad dominante, confianza media, numero de predicciones y recuento de alta intensidad para dar contexto al usuario."
                AppLanguage.CHINESE -> "\u9884\u6d4b\u89e3\u8bfb" to "SmarTrain \u9884\u6d4b\u5e94\u89c6\u4e3a\u77ed\u65f6\u95f4\u52a8\u4f5c\u7a97\u53e3\u3002\u8bad\u7ec3\u603b\u7ed3\u4f7f\u7528\u4e3b\u8981\u6d3b\u52a8\u3001\u5e73\u5747\u7f6e\u4fe1\u5ea6\u3001\u9884\u6d4b\u6570\u91cf\u548c\u9ad8\u5f3a\u5ea6\u8ba1\u6570\u6765\u4e3a\u7528\u6237\u63d0\u4f9b\u4e0a\u4e0b\u6587\u3002"
                AppLanguage.CATALAN -> document.title to document.text
            }
        }

        return document.copy(title = localized.first, text = localized.second)
    }

    fun retrieveDocuments(session: Session): List<RagDocument> {
        val queryTokens = buildQueryTokens(session)
        val retrieved = documents
            .map { document -> document to score(document, queryTokens) }
            .sortedByDescending { (_, score) -> score }
            .take(TOP_K)
            .map { (document, _) -> document }
            .toMutableList()

        if (session.highIntensityCount > 0 && retrieved.none { it.id == "kb_003" }) {
            documents.firstOrNull { it.id == "kb_003" }?.let { document ->
                if (retrieved.size >= TOP_K) {
                    retrieved[retrieved.lastIndex] = document
                } else {
                    retrieved += document
                }
            }
        }
        return retrieved
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
