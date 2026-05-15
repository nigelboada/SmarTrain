package com.udl.smartrain.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.udl.smartrain.ui.components.GlassCard
import com.udl.smartrain.ui.i18n.TextKey
import com.udl.smartrain.ui.i18n.activityLabel
import com.udl.smartrain.ui.i18n.text
import com.udl.smartrain.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SessionDetailScreen(
    viewModel: MainViewModel,
    sessionId: String,
    navController: NavController
) {
    val sessions by viewModel.sessionsHistory.collectAsState(initial = emptyList())
    val language by viewModel.appLanguage.collectAsState()
    val session = sessions.firstOrNull { it.id == sessionId }

    Scaffold(
        containerColor = Color.Transparent
    ) { paddingValues ->
        if (session == null) {
            SessionNotFound(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                language = language,
                onBack = { navController.popBackStack() }
            )
        } else {
            SessionDetailContent(
                session = session,
                language = language,
                onBack = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

@Composable
private fun SessionDetailContent(
    session: Session,
    language: AppLanguage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val insight = remember(session, language) { session.persistedOrGeneratedRagInsight(language) }
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
                    RagInsightCard(session = session, insight = insight, language = language)
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
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatDurationCompact(segment.durationMillis),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RagInsightCard(session: Session, insight: SessionRagInsight, language: AppLanguage) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildRagMetadataText(session, language),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.68f)
            )
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
    }
}

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

private fun formatDurationShort(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

private fun buildRagMetadataText(session: Session, language: AppLanguage): String {
    val source = when {
        session.ragUsedFallback -> automaticLabel(language)
        session.ragProvider == "ollama" -> session.ragModel.ifBlank { automaticLabel(language) }
        else -> localLabel(language)
    }
    val latency = if (session.ragLatencyMillis > 0) {
        " - ${session.ragLatencyMillis} ms"
    } else {
        ""
    }
    return "${language.text(TextKey.GENERATOR)}: $source$latency"
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
    if (ragAnswer.isNotBlank() && ragProvider == "ollama" && !ragUsedFallback) {
        return SessionRagInsight(
            title = ragTitle.ifBlank { SessionRagRecommender.localizedInsightTitle(language) },
            answer = ragAnswer,
            sourceTitles = ragSourceTitles
                .split("|")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        )
    }

    return SessionRagRecommender.buildInsight(this, language)
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
