package com.udl.smartrain.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.udl.smartrain.domain.model.Session
import com.udl.smartrain.ml.SessionRagRecommender
import com.udl.smartrain.ml.SessionRagInsight
import com.udl.smartrain.ui.components.GlassCard
import com.udl.smartrain.ui.theme.DarkBlueSecondary
import com.udl.smartrain.ui.theme.PurplePrimary
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
        containerColor = Color.Transparent,
        modifier = Modifier.background(
            Brush.verticalGradient(colors = listOf(PurplePrimary, DarkBlueSecondary))
        )
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Text(
                        text = insight.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = insight.answer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                    Text(
                        text = buildRagMetadataText(session),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                    if (session.ragUsedFallback) {
                        Text(
                            text = "Fallback local utilitzat: ${session.ragFallbackReason.ifBlank { "el generador IA no estava disponible." }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFC857)
                        )
                    }
                    if (insight.sourceTitles.isNotEmpty()) {
                        Text(
                            text = "Fonts recuperades",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        insight.sourceTitles.forEach { source ->
                            Text(
                                text = source,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }
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
        text = "${points.size} punts del timeline - $totalPredictions prediccions totals - confianca mitjana ${(averageConfidence * 100).toInt()}%",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.72f)
    )

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
                    text = "$category: ${points.count { it.label == category }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
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
    val source = "${session.ragProvider}:${session.ragModel}"
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

private fun colorForActivity(label: String): Color {
    return when (label) {
        "Alta intensitat" -> Color(0xFFFFC857)
        "Desplacament suau" -> Color(0xFF46D9A8)
        "Repos" -> Color(0xFF8BB7FF)
        else -> Color(0xFFD8B4FE)
    }
}
