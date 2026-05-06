package com.udl.smartrain.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.udl.smartrain.ml.ActivityPrediction
import com.udl.smartrain.ml.ActivityRecognitionState
import com.udl.smartrain.service.TrackingMetrics
import com.udl.smartrain.service.TrackingService
import com.udl.smartrain.service.TrackingSessionState
import com.udl.smartrain.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SessionScreen(viewModel: MainViewModel, onStopSession: () -> Unit) {
    val context = LocalContext.current
    val currentPrediction by ActivityRecognitionState.currentPrediction.collectAsState()
    val predictionHistory by ActivityRecognitionState.predictionHistory.collectAsState()
    val trackingMetrics by TrackingSessionState.metrics.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            val intent = Intent(context, TrackingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startNewSession("usuari_proves_123")
    }

    LaunchedEffect(trackingMetrics.isTracking) {
        while (trackingMetrics.isTracking) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SessionHeader(metrics = trackingMetrics, nowMillis = nowMillis)
            }

            item {
                MetricsGrid(
                    metrics = trackingMetrics,
                    predictionCount = predictionHistory.size,
                    nowMillis = nowMillis
                )
            }

            item {
                PredictionCard(prediction = currentPrediction)
            }

            item {
                ActionPanel(
                    isTracking = trackingMetrics.isTracking,
                    onStart = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onFinish = {
                        viewModel.finishAndSaveSession(context)
                        onStopSession()
                    }
                )
            }

            item {
                Text(
                    text = "Historic ML recent",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (predictionHistory.isEmpty()) {
                item {
                    EmptyHistoryCard()
                }
            } else {
                items(predictionHistory.asReversed()) { prediction ->
                    PredictionHistoryRow(prediction)
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(metrics: TrackingMetrics, nowMillis: Long) {
    val elapsedSeconds = metrics.startedAtMillis?.let { (nowMillis - it) / 1000 } ?: 0L

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (metrics.isTracking) "Sessio activa" else "Sessio preparada",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = formatDuration(elapsedSeconds),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MetricsGrid(metrics: TrackingMetrics, predictionCount: Int, nowMillis: Long) {
    val elapsedSeconds = metrics.startedAtMillis?.let { (nowMillis - it) / 1000 } ?: 0L

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                label = "Temps",
                value = formatDuration(elapsedSeconds),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Distancia",
                value = "${"%.2f".format(metrics.distanceMeters / 1000.0)} km",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                label = "Prediccions",
                value = predictionCount.toString(),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Estat",
                value = if (metrics.isTracking) "Actiu" else "Aturat",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun PredictionCard(prediction: ActivityPrediction?) {
    val confidence = prediction?.confidence ?: 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Activitat detectada", style = MaterialTheme.typography.titleMedium)
            Text(
                text = prediction?.label ?: "Esperant dades del sensor",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            LinearProgressIndicator(
                progress = { confidence.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Confianca: ${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ActionPanel(isTracking: Boolean, onStart: () -> Unit, onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onStart,
            enabled = !isTracking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (isTracking) "Sensoritzacio en curs" else "Comencar sensoritzacio")
        }
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Finalitzar i guardar")
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = "Encara no hi ha prediccions. Comenca la sensoritzacio per veure el model en temps real.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun PredictionHistoryRow(prediction: ActivityPrediction) {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = prediction.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = formatter.format(Date(prediction.timestampMillis)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "${(prediction.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
