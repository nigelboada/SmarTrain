package com.udl.smartrain.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.udl.smartrain.ml.ActivityPrediction
import com.udl.smartrain.ml.ActivityRecognitionState
import com.udl.smartrain.service.TrackingMetrics
import com.udl.smartrain.service.TrackingService
import com.udl.smartrain.service.TrackingSessionState
import com.udl.smartrain.ui.components.GlassCard
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
    val ragGenerationState by viewModel.ragGenerationUiState.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (hasLocationPermission(context, permissions)) {
            permissionMessage = null
            startTrackingService(
                context = context,
                onError = { message -> permissionMessage = message }
            )
        } else {
            permissionMessage = "Cal concedir el permis de localitzacio per iniciar la sessio."
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startNewSession()
    }

    LaunchedEffect(trackingMetrics.isTracking) {
        while (trackingMetrics.isTracking) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
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
                    isGeneratingRag = ragGenerationState.isGenerating,
                    permissionMessage = permissionMessage,
                    generationMessage = ragGenerationState.message,
                    onStart = {
                        permissionLauncher.launch(trackingPermissions())
                    },
                    onFinish = {
                        viewModel.finishAndSaveSession(context, onSaved = onStopSession)
                    }
                )
            }

            item {
                Text(
                    text = "Historic ML recent",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
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

private fun trackingPermissions(): Array<String> {
    return buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}

private fun hasLocationPermission(context: Context, permissions: Map<String, Boolean>): Boolean {
    val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION]
        ?: (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED)

    val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION]
        ?: (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED)

    return fineGranted || coarseGranted
}

private fun startTrackingService(context: Context, onError: (String) -> Unit) {
    try {
        val intent = Intent(context, TrackingService::class.java)
        ContextCompat.startForegroundService(context, intent)
    } catch (exception: SecurityException) {
        onError("No s'ha pogut iniciar el servei: revisa els permisos de localitzacio.")
    } catch (exception: IllegalStateException) {
        onError("No s'ha pogut iniciar el servei en segon pla. Torna-ho a provar amb l'app oberta.")
    }
}

@Composable
private fun SessionHeader(metrics: TrackingMetrics, nowMillis: Long) {
    val elapsedSeconds = metrics.startedAtMillis?.let { (nowMillis - it) / 1000 } ?: 0L

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (metrics.isTracking) "Sessio activa" else "Sessio preparada",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        Text(
            text = formatDuration(elapsedSeconds),
            style = MaterialTheme.typography.displayMedium,
            color = Color.White,
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
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.68f))
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
    }
}

@Composable
private fun PredictionCard(prediction: ActivityPrediction?) {
    val confidence = prediction?.confidence ?: 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Activitat detectada", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                text = prediction?.label ?: "Esperant dades del sensor",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            LinearProgressIndicator(
                progress = { confidence.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Confianca: ${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.76f)
            )
        }
    }
}

@Composable
private fun ActionPanel(
    isTracking: Boolean,
    isGeneratingRag: Boolean,
    permissionMessage: String?,
    generationMessage: String?,
    onStart: () -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        permissionMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        generationMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (isGeneratingRag) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Button(
            onClick = onStart,
            enabled = !isTracking && !isGeneratingRag,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (isTracking) "Sensoritzacio en curs" else "Comencar sensoritzacio")
        }
        Button(
            onClick = onFinish,
            enabled = !isGeneratingRag,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text(if (isGeneratingRag) "Generant resum..." else "Finalitzar i guardar")
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Encara no hi ha prediccions. Comenca la sensoritzacio per veure el model en temps real.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun PredictionHistoryRow(prediction: ActivityPrediction) {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = prediction.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    text = formatter.format(Date(prediction.timestampMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.66f)
                )
            }
            Text(
                text = "${(prediction.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
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
