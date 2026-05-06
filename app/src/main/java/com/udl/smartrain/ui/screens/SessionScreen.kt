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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.udl.smartrain.ml.ActivityPrediction
import com.udl.smartrain.ml.ActivityRecognitionState
import com.udl.smartrain.service.TrackingService
import com.udl.smartrain.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionScreen(viewModel: MainViewModel, onStopSession: () -> Unit) {
    val context = LocalContext.current
    val currentPrediction by ActivityRecognitionState.currentPrediction.collectAsState()
    val predictionHistory by ActivityRecognitionState.predictionHistory.collectAsState()

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Sessio en curs",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "00:00:00",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            item {
                PredictionCard(prediction = currentPrediction)
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Comencar sensoritzacio")
                    }

                    Button(
                        onClick = {
                            viewModel.finishAndSaveSession(context)
                            onStopSession()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Finalitzar entrenament")
                    }
                }
            }

            item {
                Text(
                    text = "Historic ML recent",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (predictionHistory.isEmpty()) {
                item {
                    Text(
                        text = "Encara no hi ha prediccions. Comenca la sensoritzacio per veure el model en temps real.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth()
                    )
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
private fun PredictionCard(prediction: ActivityPrediction?) {
    val confidence = prediction?.confidence ?: 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Activitat detectada",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = prediction?.label ?: "Esperant dades del sensor",
                style = MaterialTheme.typography.headlineMedium
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
                Text(
                    text = prediction.label,
                    style = MaterialTheme.typography.titleSmall
                )
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
