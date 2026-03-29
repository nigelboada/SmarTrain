package com.udl.smartrain.ui.screens

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
import androidx.compose.ui.graphics.Color




@Composable
fun SessionScreen(onStopSession: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Sessió en curs...", color = Color.Red)
        Text(text = "00:00:00", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(50.dp))
        Button(onClick = onStopSession) {
            Text(text = "FINALITZAR")
        }
    }
}