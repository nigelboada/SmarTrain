package com.udl.smartrain.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.udl.smartrain.domain.model.Session
import com.udl.smartrain.ml.SessionRagRecommender
import com.udl.smartrain.ml.SessionRagInsight
import com.udl.smartrain.ui.components.GlassCard
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
    val session = sessions.firstOrNull { it.id == sessionId }

    Scaffold(
        containerColor = Color.Transparent
    ) { paddingValues ->
        if (session == null) {
            SessionNotFound(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onBack = { navController.popBackStack() }
            )
        } else {
            SessionDetailContent(
                session = session,
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val insight = remember(session) { session.persistedOrGeneratedRagInsight() }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tornar", tint = Color.White)
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
                        text = "Mètriques de sessió",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailMetric("Temps", formatDurationShort(session.durationSeconds), Modifier.weight(1f))
                        DetailMetric("Distancia", formatDistance(session.distanceMetres), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailMetric("Activitat", session.dominantActivity.ifBlank { "Sense ML" }, Modifier.weight(1f))
                        DetailMetric("Confianca", "${(session.avgMlConfidence * 100).toInt()}%", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailMetric("Prediccions", session.mlPredictionCount.toString(), Modifier.weight(1f))
                        DetailMetric("Alta intensitat", session.highIntensityCount.toString(), Modifier.weight(1f))
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
                        text = "Categories ML en el temps",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    ActivityTimelineChart(
                        points = timeline,
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
                    RagInsightCard(session = session, insight = insight)
                }
            }
        }
    }
}

@Composable
private fun ActivityTimelineChart(
    points: List<ActivityTimelinePoint>,
    totalPredictions: Int,
    averageConfidence: Double
) {
    if (points.isEmpty()) {
        Text(
            text = "Sense dades temporals ML per aquesta sessio.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f)
        )
        return
    }

    val categories = remember(points) { points.map { it.label }.distinct() }
    val segments = remember(points) { buildTimelineSegments(points) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val barWidth = (size.width / points.size).coerceAtLeast(3f)
        points.forEachIndexed { index, point ->
            drawRect(
                color = colorForActivity(point.label),
                topLeft = Offset(index * barWidth, 0f),
                size = Size(width = barWidth + 1f, height = size.height)
            )
        }
    }

    Text(
        text = "${formatDurationCompact(totalTimelineMillis(segments))} registrats - $totalPredictions prediccions - confianca mitjana ${(averageConfidence * 100).toInt()}%",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.72f)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            TimelineSegmentRow(segment = segment)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.forEach { category ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(10.dp)
                        .height(10.dp)
                ) {
                    drawRect(color = colorForActivity(category), size = size)
                }
                Text(
                    text = "$category: ${formatDurationCompact(segments.filter { it.label == category }.sumOf { it.durationMillis })}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun TimelineSegmentRow(segment: ActivityTimelineSegment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawRect(color = colorForActivity(segment.label), size = size)
        }
        Text(
            text = segment.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatDurationCompact(segment.durationMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RagInsightCard(session: Session, insight: SessionRagInsight) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.size(34.dp),
                color = Color.White.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.medium
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFFC857),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = buildRagMetadataText(session),
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
                text = "S'ha utilitzat el resum local per seguretat: ${session.ragFallbackReason.ifBlank { "el generador IA no estava disponible." }}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFC857)
            )
        }

        if (insight.sourceTitles.isNotEmpty()) {
            Text(
                text = "Resposta basada en ${insight.sourceTitles.size} criteris de context recuperats pel RAG.",
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
private fun SessionNotFound(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tornar", tint = Color.White)
        }
        Text(
            text = "Sessio no trobada",
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

private fun buildRagMetadataText(session: Session): String {
    val source = when {
        session.ragUsedFallback -> "automàtic (fallback local)"
        session.ragProvider == "ollama" -> "automàtic"
        else -> "local"
    }
    val latency = if (session.ragLatencyMillis > 0) {
        " - ${session.ragLatencyMillis} ms"
    } else {
        ""
    }
    return "Generador: $source$latency"
}

private fun Session.persistedOrGeneratedRagInsight(): SessionRagInsight {
    if (ragAnswer.isNotBlank()) {
        return SessionRagInsight(
            title = ragTitle.ifBlank { "Interpretacio post sessio" },
            answer = ragAnswer,
            sourceTitles = ragSourceTitles
                .split("|")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        )
    }

    return SessionRagRecommender.buildInsight(this)
}

private data class ActivityTimelinePoint(
    val timestampMillis: Long,
    val label: String,
    val confidence: Float
)

private data class ActivityTimelineSegment(
    val label: String,
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
    val segmentDurations = linkedMapOf<String, Long>()

    sorted.forEachIndexed { index, point ->
        val duration = sorted.getOrNull(index + 1)
            ?.let { (it.timestampMillis - point.timestampMillis).coerceAtLeast(0L) }
            ?: fallbackInterval
        segmentDurations[point.label] = (segmentDurations[point.label] ?: 0L) + duration
    }

    return segmentDurations.map { (label, duration) ->
        ActivityTimelineSegment(label = label, durationMillis = duration)
    }
}

private fun totalTimelineMillis(segments: List<ActivityTimelineSegment>): Long {
    return segments.sumOf { it.durationMillis }
}

private fun formatDurationCompact(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
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
