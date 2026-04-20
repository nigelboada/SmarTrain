package com.udl.smartrain.ui.screens

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.udl.smartrain.domain.model.Session
import com.udl.smartrain.ui.Screen
import com.udl.smartrain.ui.viewmodel.MainViewModel

@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    // Recol·lectem el Flow com a estat de Compose
    val sessions by viewModel.sessionsHistory.collectAsState(initial = emptyList())

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.Session.route) }) {
                Icon(Icons.Default.Add, contentDescription = "Començar Entrenament")
            }
        }
    ) { paddingValues ->
        // La LazyColumn va dins del contingut del Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Molt important! Apliquem el padding del Scaffold
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sessions) { session ->
                SessionItem(session)
            }
        }
    }
}

@Composable
fun SessionItem(session: Session) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Sessió: ${session.id.take(8)}...") // Mostrem part de l'ID
            Text(text = "Usuari: ${session.userId}")
        }
    }
}