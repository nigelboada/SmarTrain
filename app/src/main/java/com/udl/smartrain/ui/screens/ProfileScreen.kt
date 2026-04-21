package com.udl.smartrain.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.udl.smartrain.ui.components.GlassCard
import com.udl.smartrain.ui.viewmodel.MainViewModel

@Composable
fun ProfileScreen(viewModel: MainViewModel, navController: NavController) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFAFAFA)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "El meu Perfil", style = MaterialTheme.typography.titleLarge)

            GlassCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Usuari: ${viewModel.currentUserEmail ?: "Desconegut"}")
                // Aquí pots afegir altres dades que vinguin del ViewModel
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = { /* Lògica per actualitzar dades */ }) {
                Text("Guardar canvis")
            }
        }
    }
}