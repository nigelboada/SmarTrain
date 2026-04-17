package com.udl.smartrain.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onStartSession: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("SmarTrain Dashboard") }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Historial de sessions", modifier = Modifier.padding(16.dp))
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onStartSession,
                modifier = Modifier.padding(32.dp).fillMaxWidth()
            ) {
                Text(text = "COMENÇAR ENTRENAMENT")
            }
        }
    }
}