package com.udl.smartrain.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.udl.smartrain.data.local.AppLanguage
import com.udl.smartrain.domain.model.Session
import com.udl.smartrain.ml.SessionRagRecommender
import com.udl.smartrain.ml.SessionRagInsight
import com.udl.smartrain.ml.RagSourceDetail
import com.udl.smartrain.ui.components.GlassCard
import com.udl.smartrain.ui.i18n.TextKey
import com.udl.smartrain.ui.i18n.activityLabel
import com.udl.smartrain.ui.i18n.text
import com.udl.smartrain.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SessionDetailScreen(
    viewModel: MainViewModel,
    sessionId: String,
    navController: NavController
) {
    val sessions by viewModel.sessionsHistory.collectAsState(initial = emptyList())
    val language by viewModel.appLanguage.collectAsState()
    val session = sessions.firstOrNull { it.id == sessionId }

    if (session == null) {
        SessionNotFound(
            modifier = Modifier.fillMaxSize(),
            language = language,
            onBack = { navController.popBackStack() }
        )
    } else {
        SessionDetailContent(
            viewModel = viewModel,
            session = session,
            language = language,
            onBack = { navController.popBackStack() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun SessionDetailContent(
    viewModel: MainViewModel,
    session: Session,
    language: AppLanguage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val insight = remember(session, language) { session.persistedOrGeneratedRagInsight(language).forDisplay(session, language) }
    val timeline = remember(session.activityTimeline) { parseActivityTimeline(session.activityTimeline) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = language.text(TextKey.BACK), tint = Color.White)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = session.sessionName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = dateFormat.format(session.startTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = language.text(TextKey.SESSIONS),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailMetric(language.text(TextKey.TIME), formatDurationShort(session.durationSeconds), Modifier.weight(1f))
                        DetailMetric(language.text(TextKey.DISTANCE), formatDistance(session.distanceMetres), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailMetric(
                            language.text(TextKey.ACTIVITY),
                            session.dominantActivity
                                .ifBlank { language.text(TextKey.NO_ML_SUMMARY) }
                                .let { language.activityLabel(it) },
                            Modifier.weight(1f)
                        )
                        DetailMetric(language.text(TextKey.CONFIDENCE), "${(session.avgMlConfidence * 100).toInt()}%", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailMetric(language.text(TextKey.PREDICTIONS), session.mlPredictionCount.toString(), Modifier.weight(1f))
                        DetailMetric(language.text(TextKey.HIGH_INTENSITY), session.highIntensityCount.toString(), Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = language.text(TextKey.CATEGORIES_OVER_TIME),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    ActivityTimelineChart(
                        points = timeline,
                        language = language,
                        sessionDurationSeconds = session.durationSeconds,
                        totalPredictions = session.mlPredictionCount,
                        averageConfidence = session.avgMlConfidence
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RagInsightCard(
                        viewModel = viewModel,
                        session = session,
                        insight = insight,
                        language = language
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityTimelineChart(
    points: List<ActivityTimelinePoint>,
    language: AppLanguage,
    sessionDurationSeconds: Long,
    totalPredictions: Int,
    averageConfidence: Double
) {
    if (points.isEmpty()) {
        Text(
            text = language.text(TextKey.NO_ML_DATA),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f)
        )
        return
    }

    val segments = remember(points) { buildTimelineSegments(points) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        val totalMillis = totalTimelineMillis(segments).coerceAtLeast(1L)
        var left = 0f
        clipRect {
            segments.forEachIndexed { index, segment ->
                val width = if (index == segments.lastIndex) {
                    size.width - left
                } else {
                    (size.width * segment.durationMillis / totalMillis).coerceAtLeast(2f)
                }
                drawRect(
                    color = colorForActivity(segment.label),
                    topLeft = Offset(left, 0f),
                    size = Size(width = width, height = size.height)
                )
                if (index < segments.lastIndex) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.82f),
                        start = Offset(left + width, 0f),
                        end = Offset(left + width, size.height),
                        strokeWidth = 1f
                    )
                }
                left += width
            }
        }
    }

    Text(
        text = timelineSummaryText(
            language = language,
            duration = formatDurationCompact(totalTimelineMillis(segments)),
            totalPredictions = totalPredictions,
            averageConfidence = averageConfidence
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.72f)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            TimelineSegmentRow(segment = segment, language = language)
        }
    }

    Text(
        text = buildTimelineFootnote(
            sessionDurationMillis = sessionDurationSeconds * 1000,
            mlTimelineMillis = totalTimelineMillis(segments),
            language = language
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.56f)
    )
}

@Composable
private fun TimelineSegmentRow(segment: ActivityTimelineSegment, language: AppLanguage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawRect(color = colorForActivity(segment.label), size = size)
        }
        Text(
            text = language.activityLabel(segment.label),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RagInsightCard(
    viewModel: MainViewModel,
    session: Session,
    insight: SessionRagInsight,
    language: AppLanguage
) {
    var selectedSource by remember { mutableStateOf<RagSourceDetail?>(null) }
    var selectedGuidedAnswer by remember { mutableStateOf<GuidedRagAnswer?>(null) }
    var guidedError by remember { mutableStateOf<String?>(null) }
    var loadingQuestionId by remember { mutableStateOf<String?>(null) }
    var showFaqDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val guidedQuestionsEnabled = session.mlPredictionCount > 0

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SummarySourceChip(session = session, language = language)
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val generationTimeText = buildGenerationTimeText(session)
            if (generationTimeText.isNotBlank()) {
                Text(
                    text = generationTimeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.68f)
                )
            }
        }

        Surface(
            color = Color.White.copy(alpha = 0.08f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = insight.answer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f)
            )
        }

        if (session.ragUsedFallback) {
            Text(
                text = fallbackText(session, language),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFC857)
            )
        }

        if (insight.sourceTitles.isNotEmpty()) {
            Text(
                text = "${language.text(TextKey.RAG_CONTEXT_ITEMS)}: ${insight.sourceTitles.size}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f)
            )
        }
        if (insight.sourceDetails.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                insight.sourceDetails.take(4).forEach { source ->
                    SourceChunkRow(source = source, onClick = { selectedSource = source })
                }
            }
        }

        if (guidedQuestionsEnabled) {
            Button(
                onClick = { showFaqDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = faqRagButtonColors()
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = guidedQuestionsTitle(language),
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 4.dp)
                )
                Text(guidedQuestionsTitle(language))
            }
        }

        if (showFaqDialog) {
            GuidedRagQuestionDialog(
                language = language,
                errorMessage = guidedError,
                loadingQuestionId = loadingQuestionId,
                onDismiss = { showFaqDialog = false },
                onQuestionClick = { question ->
                    loadingQuestionId = question.id
                    guidedError = null
                    coroutineScope.launch {
                        val result = viewModel.generateGuidedRagAnswer(session, question.id)
                        loadingQuestionId = null
                        result
                            .onSuccess { answer ->
                                selectedGuidedAnswer = GuidedRagAnswer(question = question, insight = answer)
                            }
                            .onFailure { error ->
                                guidedError = error.localizedMessage ?: guidedQuestionError(language)
                            }
                    }
                }
            )
        }

        guidedError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFC857)
            )
        }
    }

    selectedSource?.let { source ->
        SourceChunkDialog(
            source = source,
            language = language,
            onDismiss = { selectedSource = null }
        )
    }

    selectedGuidedAnswer?.let { answer ->
        GuidedRagAnswerDialog(
            answer = answer,
            language = language,
            onDismiss = { selectedGuidedAnswer = null }
        )
    }
}

@Composable
private fun SummarySourceChip(session: Session, language: AppLanguage) {
    Surface(
        color = summarySourceColor(session),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = summarySourceLabel(session, language),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun summarySourceColor(session: Session): Color {
    return when {
        session.ragUsedFallback -> Color(0xFF8A6D2F)
        session.ragProvider == "remote-rag" -> Color(0xFF2F6F6A)
        session.ragProvider == "ollama" -> Color(0xFF5145A6)
        else -> Color(0xFF5B6472)
    }
}

@Composable
private fun GuidedRagQuestionDialog(
    language: AppLanguage,
    errorMessage: String?,
    loadingQuestionId: String?,
    onDismiss: () -> Unit,
    onQuestionClick: (GuidedRagQuestion) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val questions = remember(language) { guidedRagQuestions(language) }
    val filteredQuestions = remember(searchQuery, questions) {
        val query = searchQuery.normalizedSearchText()
        if (query.isBlank()) {
            questions
        } else {
            questions.filter { question ->
                question.label.normalizedSearchText().contains(query) ||
                    question.searchText.normalizedSearchText().contains(query)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(guidedQuestionsTitle(language)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFFFFC857).copy(alpha = 0.18f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = connectionWarningText(language),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Text(
                    text = guidedQuestionsIntro(language),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(searchPlaceholderText(language)) }
                )
                filteredQuestions.forEach { question ->
                    Button(
                        onClick = { onQuestionClick(question) },
                        enabled = loadingQuestionId == null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = faqRagButtonColors()
                    ) {
                        if (loadingQuestionId == question.id) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Text(consultingSourcesText(language))
                            }
                        } else {
                            Text(question.label)
                        }
                    }
                }
                if (filteredQuestions.isEmpty()) {
                    Text(
                        text = emptyQuestionSearchText(language),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(language.text(TextKey.CANCEL))
            }
        }
    )
}

@Composable
private fun faqRagButtonColors() = if (isDarkSmarTrainTheme()) {
    ButtonDefaults.buttonColors(
        containerColor = Color(0xFF5145A6),
        contentColor = Color.White,
        disabledContainerColor = Color(0xFF2F2A49),
        disabledContentColor = Color.White.copy(alpha = 0.42f)
    )
} else {
    ButtonDefaults.buttonColors()
}

@Composable
private fun isDarkSmarTrainTheme(): Boolean {
    return MaterialTheme.colorScheme.background == Color(0xFF101218)
}

@Composable
private fun GuidedRagAnswerDialog(answer: GuidedRagAnswer, language: AppLanguage, onDismiss: () -> Unit) {
    var selectedSource by remember { mutableStateOf<RagSourceDetail?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(answer.question.label) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = answer.insight.answer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (answer.insight.sourceDetails.isNotEmpty()) {
                    Text(
                        text = "${language.text(TextKey.RAG_CONTEXT_ITEMS)}: ${answer.insight.sourceDetails.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    answer.insight.sourceDetails.take(3).forEach { source ->
                        GuidedSourceMiniCard(source = source, onClick = { selectedSource = source })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(language.text(TextKey.CONFIRM))
            }
        }
    )
    selectedSource?.let { source ->
        SourceChunkDialog(
            source = source,
            language = language,
            onDismiss = { selectedSource = null }
        )
    }
}

@Composable
private fun GuidedSourceMiniCard(source: RagSourceDetail, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "${source.source.ifBlank { source.id }} - score ${"%.2f".format(source.score)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = source.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SourceChunkRow(source: RagSourceDetail, onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.06f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "${source.source.ifBlank { source.id }} - score ${"%.2f".format(source.score)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = source.text,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SourceChunkDialog(source: RagSourceDetail, language: AppLanguage, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(source.source.ifBlank { source.id }) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${source.category} - chunk ${source.chunkId} - score ${"%.2f".format(source.score)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = source.text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(language.text(TextKey.CONFIRM))
            }
        }
    )
}

private data class GuidedRagQuestion(
    val id: String,
    val label: String,
    val searchText: String = label
)

private data class GuidedRagAnswer(
    val question: GuidedRagQuestion,
    val insight: SessionRagInsight
)

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.68f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SessionNotFound(modifier: Modifier = Modifier, language: AppLanguage, onBack: () -> Unit) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = language.text(TextKey.BACK), tint = Color.White)
        }
        Text(
            text = language.text(TextKey.SESSION_NOT_FOUND),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
    }
}

private fun formatDistance(distanceMetres: Double): String {
    return if (distanceMetres >= 1000.0) {
        "${"%.2f".format(distanceMetres / 1000.0)} km"
    } else {
        "${distanceMetres.toInt()} m"
    }
}

private fun guidedQuestionsTitle(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "FAQ RAG"
    AppLanguage.ENGLISH -> "RAG FAQ"
    AppLanguage.SPANISH -> "FAQ RAG"
    AppLanguage.CHINESE -> "RAG \u5f15\u5bfc\u95ee\u9898"
}

private fun guidedQuestionsIntro(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Tria una pregunta. El backend RAG recuperarà fonts i respondrà segons la sessió."
    AppLanguage.ENGLISH -> "Choose a question. The RAG backend will retrieve sources and answer from this session."
    AppLanguage.SPANISH -> "Elige una pregunta. El backend RAG recuperara fuentes y respondera segun la sesion."
    AppLanguage.CHINESE -> "\u9009\u62e9\u4e00\u4e2a\u95ee\u9898\u3002RAG \u540e\u7aef\u4f1a\u68c0\u7d22\u6765\u6e90\u5e76\u6839\u636e\u8bad\u7ec3\u56de\u7b54\u3002"
}

private fun guidedRagQuestions(language: AppLanguage): List<GuidedRagQuestion> {
    return when (language) {
        AppLanguage.CATALAN -> listOf(
            GuidedRagQuestion("improve_next", "Com puc millorar la propera sessió?"),
            GuidedRagQuestion("why_recommendation", "Per què recomanes això?"),
            GuidedRagQuestion("prediction_limits", "Limitacions de la predicció"),
            GuidedRagQuestion("recovery", "Quina recuperació em convé?"),
            GuidedRagQuestion("confidence_meaning", "Què significa la confiança?"),
            GuidedRagQuestion("high_intensity", "Com interpreto l'alta intensitat?"),
            GuidedRagQuestion("sensor_quality", "Com milloro la qualitat dels sensors?"),
            GuidedRagQuestion("phone_position", "On hauria de portar el mòbil?"),
            GuidedRagQuestion("warmup_first_seconds", "Els primers segons són fiables?"),
            GuidedRagQuestion("repeat_session", "Hauria de repetir aquesta sessió?"),
            GuidedRagQuestion("load_control", "Com puc controlar la càrrega?"),
            GuidedRagQuestion("next_training_focus", "Quin focus hauria de tenir el proper entrenament?"),
            GuidedRagQuestion("low_confidence_causes", "Què pot causar baixa confiança?"),
            GuidedRagQuestion("football_limits", "Què limita l'ús en futbol?"),
            GuidedRagQuestion("data_needed", "Quines dades caldrien per millorar el model?"),
            GuidedRagQuestion("dominant_activity", "Què vol dir l'activitat dominant?"),
            GuidedRagQuestion("rest_interpretation", "Com interpreto una sessió suau o de repòs?"),
            GuidedRagQuestion("intensity_ratio", "Hi ha massa alta intensitat?"),
            GuidedRagQuestion("model_metrics", "Quines mètriques té el model final?"),
            GuidedRagQuestion("rag_sources", "D'on surten les fonts del RAG?"),
            GuidedRagQuestion("demo_best_mode", "Quin mode convé per a una demo?"),
            GuidedRagQuestion("practical_tip", "Dona'm un consell pràctic per avui")
        )
        AppLanguage.ENGLISH -> listOf(
            GuidedRagQuestion("improve_next", "How can I improve the next session?"),
            GuidedRagQuestion("why_recommendation", "Why do you recommend this?"),
            GuidedRagQuestion("prediction_limits", "Prediction limitations"),
            GuidedRagQuestion("recovery", "What recovery is appropriate?"),
            GuidedRagQuestion("confidence_meaning", "What does confidence mean?"),
            GuidedRagQuestion("high_intensity", "How should I read high intensity?"),
            GuidedRagQuestion("sensor_quality", "How can I improve sensor quality?"),
            GuidedRagQuestion("phone_position", "Where should I carry the phone?"),
            GuidedRagQuestion("warmup_first_seconds", "Are the first seconds reliable?"),
            GuidedRagQuestion("repeat_session", "Should I repeat this session?"),
            GuidedRagQuestion("load_control", "How can I control training load?"),
            GuidedRagQuestion("next_training_focus", "What should the next training focus be?"),
            GuidedRagQuestion("low_confidence_causes", "What can cause low confidence?"),
            GuidedRagQuestion("football_limits", "What limits football use?"),
            GuidedRagQuestion("data_needed", "What data would improve the model?"),
            GuidedRagQuestion("dominant_activity", "What does dominant activity mean?"),
            GuidedRagQuestion("rest_interpretation", "How do I read a light or rest session?"),
            GuidedRagQuestion("intensity_ratio", "Is there too much high intensity?"),
            GuidedRagQuestion("model_metrics", "What metrics does the final model have?"),
            GuidedRagQuestion("rag_sources", "Where do RAG sources come from?"),
            GuidedRagQuestion("demo_best_mode", "Which mode is best for a demo?"),
            GuidedRagQuestion("practical_tip", "Give me one practical tip for today")
        )
        AppLanguage.SPANISH -> listOf(
            GuidedRagQuestion("improve_next", "Como puedo mejorar la proxima sesion?"),
            GuidedRagQuestion("why_recommendation", "Por que recomiendas esto?"),
            GuidedRagQuestion("prediction_limits", "Limitaciones de la prediccion"),
            GuidedRagQuestion("recovery", "Que recuperacion me conviene?"),
            GuidedRagQuestion("confidence_meaning", "Que significa la confianza?"),
            GuidedRagQuestion("high_intensity", "Como interpreto la alta intensidad?"),
            GuidedRagQuestion("sensor_quality", "Como mejoro la calidad de los sensores?"),
            GuidedRagQuestion("phone_position", "Donde deberia llevar el movil?"),
            GuidedRagQuestion("warmup_first_seconds", "Son fiables los primeros segundos?"),
            GuidedRagQuestion("repeat_session", "Deberia repetir esta sesion?"),
            GuidedRagQuestion("load_control", "Como puedo controlar la carga?"),
            GuidedRagQuestion("next_training_focus", "Que enfoque deberia tener el proximo entreno?"),
            GuidedRagQuestion("low_confidence_causes", "Que puede causar baja confianza?"),
            GuidedRagQuestion("football_limits", "Que limita el uso en futbol?"),
            GuidedRagQuestion("data_needed", "Que datos mejorarian el modelo?"),
            GuidedRagQuestion("dominant_activity", "Que significa la actividad dominante?"),
            GuidedRagQuestion("rest_interpretation", "Como interpreto una sesion suave o de reposo?"),
            GuidedRagQuestion("intensity_ratio", "Hay demasiada alta intensidad?"),
            GuidedRagQuestion("model_metrics", "Que metricas tiene el modelo final?"),
            GuidedRagQuestion("rag_sources", "De donde salen las fuentes del RAG?"),
            GuidedRagQuestion("demo_best_mode", "Que modo conviene para una demo?"),
            GuidedRagQuestion("practical_tip", "Dame un consejo practico para hoy")
        )
        AppLanguage.CHINESE -> listOf(
            GuidedRagQuestion("improve_next", "\u5982\u4f55\u6539\u8fdb\u4e0b\u4e00\u6b21\u8bad\u7ec3\uff1f"),
            GuidedRagQuestion("why_recommendation", "\u4e3a\u4ec0\u4e48\u8fd9\u6837\u5efa\u8bae\uff1f"),
            GuidedRagQuestion("prediction_limits", "\u9884\u6d4b\u5c40\u9650"),
            GuidedRagQuestion("recovery", "\u6211\u5e94\u8be5\u5982\u4f55\u6062\u590d\uff1f"),
            GuidedRagQuestion("confidence_meaning", "\u7f6e\u4fe1\u5ea6\u662f\u4ec0\u4e48\uff1f"),
            GuidedRagQuestion("high_intensity", "\u5982\u4f55\u7406\u89e3\u9ad8\u5f3a\u5ea6\uff1f"),
            GuidedRagQuestion("sensor_quality", "\u5982\u4f55\u63d0\u9ad8\u4f20\u611f\u5668\u8d28\u91cf\uff1f"),
            GuidedRagQuestion("phone_position", "\u624b\u673a\u5e94\u8be5\u653e\u5728\u54ea\u91cc\uff1f"),
            GuidedRagQuestion("warmup_first_seconds", "\u524d\u51e0\u79d2\u53ef\u9760\u5417\uff1f"),
            GuidedRagQuestion("repeat_session", "\u6211\u5e94\u8be5\u91cd\u590d\u8fd9\u6b21\u8bad\u7ec3\u5417\uff1f"),
            GuidedRagQuestion("load_control", "\u5982\u4f55\u63a7\u5236\u8bad\u7ec3\u8d1f\u8377\uff1f"),
            GuidedRagQuestion("next_training_focus", "\u4e0b\u6b21\u8bad\u7ec3\u5e94\u5173\u6ce8\u4ec0\u4e48\uff1f"),
            GuidedRagQuestion("low_confidence_causes", "\u4ec0\u4e48\u4f1a\u5bfc\u81f4\u4f4e\u7f6e\u4fe1\u5ea6\uff1f"),
            GuidedRagQuestion("football_limits", "\u8db3\u7403\u573a\u666f\u6709\u4ec0\u4e48\u9650\u5236\uff1f"),
            GuidedRagQuestion("data_needed", "\u54ea\u4e9b\u6570\u636e\u53ef\u4ee5\u6539\u8fdb\u6a21\u578b\uff1f"),
            GuidedRagQuestion("dominant_activity", "\u4e3b\u8981\u6d3b\u52a8\u662f\u4ec0\u4e48\u610f\u601d\uff1f"),
            GuidedRagQuestion("rest_interpretation", "\u5982\u4f55\u89e3\u8bfb\u8f7b\u5ea6\u6216\u4f11\u606f\u8bad\u7ec3\uff1f"),
            GuidedRagQuestion("intensity_ratio", "\u9ad8\u5f3a\u5ea6\u662f\u5426\u8fc7\u591a\uff1f"),
            GuidedRagQuestion("model_metrics", "\u6700\u7ec8\u6a21\u578b\u7684\u6307\u6807\u662f\u4ec0\u4e48\uff1f"),
            GuidedRagQuestion("rag_sources", "RAG \u6765\u6e90\u4ece\u54ea\u91cc\u6765\uff1f"),
            GuidedRagQuestion("demo_best_mode", "\u6f14\u793a\u65f6\u6700\u9002\u5408\u7528\u54ea\u79cd\u6a21\u5f0f\uff1f"),
            GuidedRagQuestion("practical_tip", "\u7ed9\u6211\u4e00\u4e2a\u4eca\u5929\u7684\u5b9e\u7528\u5efa\u8bae")
        )
    }
}

private fun guidedQuestionError(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "No s'ha pogut generar la resposta guiada amb el backend RAG."
    AppLanguage.ENGLISH -> "The RAG backend could not generate the guided answer."
    AppLanguage.SPANISH -> "No se ha podido generar la respuesta guiada con el backend RAG."
    AppLanguage.CHINESE -> "\u65e0\u6cd5\u901a\u8fc7 RAG \u540e\u7aef\u751f\u6210\u5f15\u5bfc\u56de\u7b54\u3002"
}

private fun consultingSourcesText(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Consultant fonts..."
    AppLanguage.ENGLISH -> "Checking sources..."
    AppLanguage.SPANISH -> "Consultando fuentes..."
    AppLanguage.CHINESE -> "\u6b63\u5728\u67e5\u8be2\u6765\u6e90..."
}

private fun connectionWarningText(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "No hi ha connexio amb el backend RAG."
    AppLanguage.ENGLISH -> "No connection to the RAG backend."
    AppLanguage.SPANISH -> "No hay conexion con el backend RAG."
    AppLanguage.CHINESE -> "\u65e0\u6cd5\u8fde\u63a5 RAG \u540e\u7aef\u3002"
}

private fun searchPlaceholderText(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Cerca preguntes"
    AppLanguage.ENGLISH -> "Search questions"
    AppLanguage.SPANISH -> "Buscar preguntas"
    AppLanguage.CHINESE -> "\u641c\u7d22\u95ee\u9898"
}

private fun emptyQuestionSearchText(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "No hi ha preguntes amb aquest filtre."
    AppLanguage.ENGLISH -> "No questions match this filter."
    AppLanguage.SPANISH -> "No hay preguntas con este filtro."
    AppLanguage.CHINESE -> "\u6ca1\u6709\u5339\u914d\u7684\u95ee\u9898\u3002"
}

private fun String.normalizedSearchText(): String {
    return lowercase()
        .replace("à", "a")
        .replace("á", "a")
        .replace("è", "e")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ï", "i")
        .replace("ò", "o")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ü", "u")
        .replace("ç", "c")
        .trim()
}

private fun formatDurationShort(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

private fun buildGenerationTimeText(session: Session): String {
    return if (session.ragLatencyMillis > 0) {
        "${session.ragLatencyMillis} ms"
    } else {
        ""
    }
}

private fun summarySourceLabel(session: Session, language: AppLanguage): String {
    return when {
        session.ragUsedFallback -> localFallbackChipLabel(language)
        session.ragProvider == "remote-rag" -> backendRagChipLabel(language)
        session.ragProvider == "ollama" -> cloudAiChipLabel(language)
        else -> localChipLabel(language)
    }
}

private fun localFallbackChipLabel(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Resum local"
    AppLanguage.ENGLISH -> "Local summary"
    AppLanguage.SPANISH -> "Resumen local"
    AppLanguage.CHINESE -> "\u672c\u5730\u603b\u7ed3"
}

private fun localChipLabel(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Resum local"
    AppLanguage.ENGLISH -> "Local summary"
    AppLanguage.SPANISH -> "Resumen local"
    AppLanguage.CHINESE -> "\u672c\u5730\u603b\u7ed3"
}

private fun cloudAiChipLabel(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "IA cloud"
    AppLanguage.ENGLISH -> "Cloud AI"
    AppLanguage.SPANISH -> "IA cloud"
    AppLanguage.CHINESE -> "\u4e91\u7aef AI"
}

private fun backendRagChipLabel(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Backend RAG"
    AppLanguage.ENGLISH -> "RAG backend"
    AppLanguage.SPANISH -> "Backend RAG"
    AppLanguage.CHINESE -> "RAG \u540e\u7aef"
}

private fun automaticLabel(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "automatic"
    AppLanguage.ENGLISH -> "automatic"
    AppLanguage.SPANISH -> "automatico"
    AppLanguage.CHINESE -> "\u81ea\u52a8"
}

private fun localLabel(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "local"
    AppLanguage.ENGLISH -> "local"
    AppLanguage.SPANISH -> "local"
    AppLanguage.CHINESE -> "\u672c\u5730"
}

private fun remoteRagLabel(language: AppLanguage, model: String): String {
    val suffix = displayModelName(model, language).takeIf { it.isNotBlank() }?.let { ":$it" }.orEmpty()
    return when (language) {
        AppLanguage.CATALAN -> "backend RAG$suffix"
        AppLanguage.ENGLISH -> "RAG backend$suffix"
        AppLanguage.SPANISH -> "backend RAG$suffix"
        AppLanguage.CHINESE -> "RAG \u540e\u7aef$suffix"
    }
}

private fun displayModelName(model: String, language: AppLanguage): String {
    return if (model == "qwen3-coder-next") {
        when (language) {
            AppLanguage.CATALAN -> "IA cloud"
            AppLanguage.ENGLISH -> "Cloud AI"
            AppLanguage.SPANISH -> "IA cloud"
            AppLanguage.CHINESE -> "\u4e91\u7aef AI"
        }
    } else {
        model
    }
}

private fun fallbackText(session: Session, language: AppLanguage): String {
    val reason = session.ragFallbackReason.ifBlank { "IA" }
    return when (language) {
        AppLanguage.CATALAN -> "S'ha utilitzat el resum local per seguretat: $reason"
        AppLanguage.ENGLISH -> "The local summary was used for safety: $reason"
        AppLanguage.SPANISH -> "Se ha utilizado el resumen local por seguridad: $reason"
        AppLanguage.CHINESE -> "\u5df2\u4f7f\u7528\u672c\u5730\u603b\u7ed3\u4ee5\u786e\u4fdd\u7a33\u5b9a\uff1a$reason"
    }
}

private fun Session.persistedOrGeneratedRagInsight(language: AppLanguage): SessionRagInsight {
    if (ragAnswer.isNotBlank() && !ragUsedFallback) {
        return SessionRagInsight(
            title = ragTitle.ifBlank { SessionRagRecommender.localizedInsightTitle(language) },
            answer = ragAnswer,
            sourceTitles = ragSourceTitles
                .split("|")
                .map { it.trim() }
                .filter { it.isNotBlank() },
            sourceDetails = parseRagSourceDetails(ragSourceDetails)
        )
    }

    return SessionRagRecommender.buildInsight(this, language)
}

private fun SessionRagInsight.forDisplay(session: Session, language: AppLanguage): SessionRagInsight {
    val localizedTitle = SessionRagRecommender.localizedInsightTitle(language)
    if (session.mlPredictionCount == 0) {
        return SessionRagRecommender.buildInsight(session, language)
    }
    if (shouldUseLocalizedSummary(session = session, answer = answer, language = language)) {
        val localized = SessionRagRecommender.buildInsight(session, language)
        return localized.copy(title = localizedTitle)
    }
    return copy(title = localizedTitle)
}

private fun shouldUseLocalizedSummary(session: Session, answer: String, language: AppLanguage): Boolean {
    if (session.ragProvider == "rules" || session.ragUsedFallback) {
        return true
    }
    return when (language) {
        AppLanguage.CATALAN -> answer.containsAny(
            "sesion",
            "sesión",
            "confianza",
            "recomendacion",
            "recomendación",
            "actividad dominante"
        )
        AppLanguage.SPANISH -> answer.containsAny(
            "sessio",
            "sessió",
            "confianca",
            "confiança",
            "recomanacio",
            "recomanació",
            "activitat dominant"
        )
        AppLanguage.ENGLISH -> answer.containsAny(
            "sessio",
            "sessió",
            "sesion",
            "sesión",
            "confianca",
            "confianza"
        )
        AppLanguage.CHINESE -> !answer.any { it in '\u4e00'..'\u9fff' }
    }
}

private fun String.containsAny(vararg needles: String): Boolean {
    return needles.any { needle -> contains(needle, ignoreCase = true) }
}

private fun parseRagSourceDetails(raw: String): List<RagSourceDetail> {
    if (raw.isBlank()) return emptyList()
    return raw.split("|").mapNotNull { item ->
        val parts = item.split("~")
        if (parts.size < 6) {
            return@mapNotNull null
        }
        RagSourceDetail(
            id = parts[0].decodeSourcePart(),
            source = parts[1].decodeSourcePart(),
            category = parts[2].decodeSourcePart(),
            chunkId = parts[3].toIntOrNull() ?: 0,
            score = parts[4].replace(",", ".").toDoubleOrNull() ?: 0.0,
            text = parts.drop(5).joinToString("~").decodeSourcePart()
        )
    }
}

private fun String.decodeSourcePart(): String {
    return replace("%7E", "~")
        .replace("%7C", "|")
        .replace("%25", "%")
}

private data class ActivityTimelinePoint(
    val timestampMillis: Long,
    val label: String,
    val confidence: Float
)

private data class ActivityTimelineSegment(
    val label: String,
    val startMillis: Long,
    val durationMillis: Long
)

private fun parseActivityTimeline(timeline: String): List<ActivityTimelinePoint> {
    if (timeline.isBlank()) {
        return emptyList()
    }

    return timeline.split("|").mapNotNull { rawPoint ->
        val parts = rawPoint.split(",")
        if (parts.size < 5) {
            return@mapNotNull null
        }

        ActivityTimelinePoint(
            timestampMillis = parts[0].toLongOrNull() ?: return@mapNotNull null,
            label = parts[2].trim(),
            confidence = parts[4].toFloatOrNull() ?: 0f
        )
    }
}

private fun buildTimelineSegments(points: List<ActivityTimelinePoint>): List<ActivityTimelineSegment> {
    if (points.isEmpty()) {
        return emptyList()
    }

    val sorted = points.sortedBy { it.timestampMillis }
    val intervals = sorted
        .zipWithNext { current, next -> (next.timestampMillis - current.timestampMillis).coerceAtLeast(0L) }
        .filter { it > 0L }
    val fallbackInterval = intervals.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 1000L
    val segments = mutableListOf<ActivityTimelineSegment>()

    sorted.forEachIndexed { index, point ->
        val duration = sorted.getOrNull(index + 1)
            ?.let { (it.timestampMillis - point.timestampMillis).coerceAtLeast(0L) }
            ?: fallbackInterval
        val previous = segments.lastOrNull()
        if (previous != null && previous.label == point.label) {
            segments[segments.lastIndex] = previous.copy(
                durationMillis = previous.durationMillis + duration
            )
        } else {
            segments += ActivityTimelineSegment(
                label = point.label,
                startMillis = point.timestampMillis,
                durationMillis = duration
            )
        }
    }

    return segments
}

private fun totalTimelineMillis(segments: List<ActivityTimelineSegment>): Long {
    return segments.sumOf { it.durationMillis }
}

private fun formatDurationCompact(durationMillis: Long): String {
    val totalSeconds = if (durationMillis in 1..999) {
        1L
    } else {
        (durationMillis / 1000).coerceAtLeast(0L)
    }
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

private fun buildTimelineFootnote(sessionDurationMillis: Long, mlTimelineMillis: Long, language: AppLanguage): String {
    return if (sessionDurationMillis > mlTimelineMillis + 1500) {
        language.text(TextKey.ML_TIMELINE_NOTE)
    } else {
        language.text(TextKey.ML_TIMELINE_NOTE_SHORT)
    }
}

private fun timelineSummaryText(
    language: AppLanguage,
    duration: String,
    totalPredictions: Int,
    averageConfidence: Double
): String {
    val confidence = (averageConfidence * 100).toInt()
    return when (language) {
        AppLanguage.CATALAN -> "$duration registrats - $totalPredictions prediccions - confianca mitjana $confidence%"
        AppLanguage.ENGLISH -> "$duration registered - $totalPredictions predictions - average confidence $confidence%"
        AppLanguage.SPANISH -> "$duration registrados - $totalPredictions predicciones - confianza media $confidence%"
        AppLanguage.CHINESE -> "$duration \u5df2\u8bb0\u5f55 - $totalPredictions \u6b21\u9884\u6d4b - \u5e73\u5747\u7f6e\u4fe1\u5ea6 $confidence%"
    }
}

private fun colorForActivity(label: String): Color {
    return when (label) {
        "Alta intensitat" -> Color(0xFFFFC857)
        "Desplacament suau" -> Color(0xFF46D9A8)
        "Repos" -> Color(0xFF8BB7FF)
        else -> Color(0xFFD8B4FE)
    }
}

