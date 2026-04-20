package com.udl.smartrain.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.udl.smartrain.domain.model.Session
import com.udl.smartrain.ui.Screen
import com.udl.smartrain.ui.components.GlassCard
import com.udl.smartrain.ui.theme.DarkBlueSecondary
import com.udl.smartrain.ui.theme.PurplePrimary
import com.udl.smartrain.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val sessions by viewModel.sessionsHistory.collectAsState(initial = emptyList())

    var showDialog by remember { mutableStateOf(false) }
    var sessionToEdit by remember { mutableStateOf<Session?>(null) }

    // Nous estats per als camps d'edició
    var editUserName by remember { mutableStateOf("") }
    var editSessionName by remember { mutableStateOf("") }

    if (showDialog && sessionToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Editar Sessió") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = editUserName,
                        onValueChange = { editUserName = it },
                        label = { Text("Nom d'usuari") }
                    )
                    TextField(
                        value = editSessionName,
                        onValueChange = { editSessionName = it },
                        label = { Text("Nom de la sessió") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateSession(sessionToEdit!!.copy(
                        userId = editUserName,
                        sessionName = editSessionName
                    ))
                    showDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                Button(onClick = { showDialog = false }) { Text("Cancel·lar") }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent, // Fons transparent per veure el gradient
        modifier = Modifier.background(
            Brush.verticalGradient(colors = listOf(PurplePrimary, DarkBlueSecondary))
        )
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            items(sessions) { session ->
                SessionItem(
                    session = session,
                    onDelete = { viewModel.deleteSession(session) },
                    onEdit = {
                        sessionToEdit = session
                        editUserName = session.userId
                        editSessionName = session.sessionName
                        showDialog = true // Obrim el diàleg
                    }
                )
            }
        }
    }
}

@Composable
fun SessionItem(session: Session, onDelete: () -> Unit, onEdit: () -> Unit) {
    // Formatejador per a la data
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(session.startTime)

    GlassCard(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                // Títols amb Bebas Neue
                Text(text = session.sessionName, style = MaterialTheme.typography.titleMedium, color = Color.White)

                // Body amb Inter
                Text(text = "Usuari: ${session.userId}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            }

            // Botons amb tint blanc per contrastar amb el blau/porpra
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.White) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Esborrar", tint = Color.White) }
        }
    }


    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Títol de la sessió
                Text(text = session.sessionName, style = MaterialTheme.typography.titleMedium)

                // Salt de línia fet amb dos components Text
                Text(text = "Usuari: ${session.userId}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Una mica més gris
                )
            }

            // Botons d'acció
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Esborrar")
                }
            }
        }
    }
}
