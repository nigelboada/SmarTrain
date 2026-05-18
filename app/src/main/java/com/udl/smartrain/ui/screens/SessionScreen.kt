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
import com.udl.smartrain.data.local.AppLanguage
import com.udl.smartrain.ml.ActivityPrediction
import com.udl.smartrain.ml.ActivityRecognitionState
import com.udl.smartrain.service.TrackingMetrics
import com.udl.smartrain.service.TrackingService
import com.udl.smartrain.service.TrackingSessionState
import com.udl.smartrain.ui.components.GlassCard
import com.udl.smartrain.ui.i18n.TextKey
import com.udl.smartrain.ui.i18n.activityLabel
import com.udl.smartrain.ui.i18n.text
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
    val language by viewModel.appLanguage.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (hasLocationPermission(context, permissions)) {
            permissionMessage = null
            startTrackingService(
                context = context,
                language = language,
                onError = { message -> permissionMessage = message }
            )
        } else {
            permissionMessage = locationPermissionText(language)
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
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SessionHeader(metrics = trackingMetrics, nowMillis = nowMillis, language = language)
            }

            item {
                MetricsGrid(
                    metrics = trackingMetrics,
                    predictionCount = predictionHistory.size,
                    nowMillis = nowMillis,
                    language = language
                )
            }

            item {
                PredictionCard(prediction = currentPrediction, language = language)
            }

            item {
                ActionPanel(
                    isTracking = trackingMetrics.isTracking,
                    isGeneratingRag = ragGenerationState.isGenerating,
                    permissionMessage = permissionMessage,
                    generationMessage = ragGenerationState.message,
                    language = language,
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
                    text = language.text(TextKey.ML_HISTORY),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            if (predictionHistory.isEmpty()) {
                item {
                    EmptyHistoryCard(language = language)
                }
            } else {
                items(predictionHistory.asReversed()) { prediction ->
                    PredictionHistoryRow(prediction = prediction, language = language)
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

private fun startTrackingService(context: Context, language: AppLanguage, onError: (String) -> Unit) {
    try {
        val intent = Intent(context, TrackingService::class.java)
        ContextCompat.startForegroundService(context, intent)
    } catch (exception: SecurityException) {
        onError(servicePermissionText(language))
    } catch (exception: IllegalStateException) {
        onError(backgroundServiceText(language))
    }
}

private fun locationPermissionText(language: AppLanguage): String = when (language) {
    else -> language.text(TextKey.LOCATION_PERMISSION_REQUIRED)
}

private fun servicePermissionText(language: AppLanguage): String = language.text(TextKey.SERVICE_PERMISSION_ERROR)

private fun backgroundServiceText(language: AppLanguage): String = language.text(TextKey.SERVICE_BACKGROUND_ERROR)

@Composable
private fun SessionHeader(metrics: TrackingMetrics, nowMillis: Long, language: AppLanguage) {
    val elapsedSeconds = metrics.startedAtMillis?.let { (nowMillis - it) / 1000 } ?: 0L

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (metrics.isTracking) language.text(TextKey.SESSION_ACTIVE) else language.text(TextKey.SESSION_READY),
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
private fun MetricsGrid(metrics: TrackingMetrics, predictionCount: Int, nowMillis: Long, language: AppLanguage) {
    val elapsedSeconds = metrics.startedAtMillis?.let { (nowMillis - it) / 1000 } ?: 0L

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                label = language.text(TextKey.TIME),
                value = formatDuration(elapsedSeconds),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = language.text(TextKey.DISTANCE),
                value = "${"%.2f".format(metrics.distanceMeters / 1000.0)} km",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                label = language.text(TextKey.PREDICTIONS),
                value = predictionCount.toString(),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = language.text(TextKey.STATUS),
                value = if (metrics.isTracking) language.text(TextKey.SESSION_ACTIVE) else language.text(TextKey.STOPPED),
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
private fun PredictionCard(prediction: ActivityPrediction?, language: AppLanguage) {
    val confidence = prediction?.confidence ?: 0f

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = language.text(TextKey.ACTIVITY_DETECTED), style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                text = prediction?.let { language.activityLabel(it.label) } ?: language.text(TextKey.NO_ML_DATA),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            LinearProgressIndicator(
                progress = { confidence.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${language.text(TextKey.CONFIDENCE)}: ${(confidence * 100).toInt()}%",
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
    language: AppLanguage,
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
            Text(text = if (isTracking) language.text(TextKey.SESSION_ACTIVE) else language.text(TextKey.START_SENSORING))
        }
        Button(
            onClick = onFinish,
            enabled = !isGeneratingRag,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text(if (isGeneratingRag) language.text(TextKey.GENERATING_SUMMARY) else language.text(TextKey.FINISH_SAVE))
        }
    }
}

@Composable
private fun EmptyHistoryCard(language: AppLanguage) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = language.text(TextKey.NO_HISTORY_PREDICTIONS),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun PredictionHistoryRow(prediction: ActivityPrediction, language: AppLanguage) {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = language.activityLabel(prediction.label), style = MaterialTheme.typography.titleSmall, color = Color.White)
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
