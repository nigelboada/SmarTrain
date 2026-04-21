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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
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
import com.udl.smartrain.ui.components.GlassCard
import com.udl.smartrain.ui.navigation.Screen
import com.udl.smartrain.ui.theme.DarkBlueSecondary
import com.udl.smartrain.ui.theme.PurplePrimary
import com.udl.smartrain.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val sessions by viewModel.sessionsHistory.collectAsState(initial = emptyList())

    var showDeleteDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<Session?>(null) }

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

    if (showDeleteDialog && sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmar esborrat") },
            text = { Text("Segur que vols esborrar la sessió '${sessionToDelete?.sessionName}'? Aquesta acció no es pot desfer.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSession(sessionToDelete!!)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Esborrar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel·lar")
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(
            Brush.verticalGradient(colors = listOf(PurplePrimary, DarkBlueSecondary))
        ),
        // Afegim el botó flotant que havíem perdut
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.Session.route) },
                containerColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova Sessió", tint = PurplePrimary)
            }
        }
    ) { paddingValues ->
        // Si no hi ha sessions, mostrem un missatge perquè l'usuari sàpiga que funciona
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hi ha sessions enregistrades.\nClica el botó + per començar!",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            // Si hi ha sessions, mostrem la llista
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                items(sessions) { session ->
                    SessionItem(
                        session = session,
                        onDelete = {
                            sessionToDelete = session
                            showDeleteDialog = true
                        },
                        onEdit = {
                            sessionToEdit = session
                            editUserName = session.userId
                            editSessionName = session.sessionName
                            showDialog = true
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun SessionItem(session: Session, onDelete: () -> Unit, onEdit: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(session.startTime)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp), // Més padding intern = més elegància
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Columna principal amb espaiat entre línies
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp) // Aire entre les línies de text
            ) {
                Text(
                    text = session.sessionName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "Usuari: ${session.userId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f) // Lleugera transparència
                )
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f) // Encara més subtil
                )
            }

            // Botons d'acció amb mida optimitzada
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.White)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Esborrar", tint = Color.White)
                }
            }
        }
    }
}
