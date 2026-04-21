package com.udl.smartrain.ui.screens

import android.util.Log

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.udl.smartrain.service.TrackingService
import com.udl.smartrain.ui.viewmodel.MainViewModel

@Composable
fun SessionScreen(viewModel: MainViewModel, onStopSession: () -> Unit) {
    val context = LocalContext.current

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
        viewModel.startNewSession("usuari_proves_123") // Inicialitzem amb un ID dummy
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFAFAFA) // Això agafa el color del teu tema!
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Sessió en curs...", color = MaterialTheme.colorScheme.onBackground)
            Text(text = "00:00:00", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)

            Spacer(modifier = Modifier.height(50.dp))

            Button(onClick = {
                permissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }) {
                Text(text = "COMENÇAR SENSORITZACIÓ")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = {
                viewModel.finishAndSaveSession(context)
                onStopSession()
            }) {
                Text("Finalitzar Entrenament")
            }
        }
    }

}