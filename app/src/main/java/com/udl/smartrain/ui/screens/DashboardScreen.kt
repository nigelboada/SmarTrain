package com.udl.smartrain.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.udl.smartrain.domain.model.Session
import com.udl.smartrain.ui.components.AppHeader
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
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var sessionToEdit by remember { mutableStateOf<Session?>(null) }
    var editUserName by remember { mutableStateOf("") }
    var editSessionName by remember { mutableStateOf("") }

    if (showEditDialog && sessionToEdit != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Editar sessio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = editUserName,
                        onValueChange = { editUserName = it },
                        label = { Text("Usuari") }
                    )
                    TextField(
                        value = editSessionName,
                        onValueChange = { editSessionName = it },
                        label = { Text("Nom de la sessio") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateSession(
                        sessionToEdit!!.copy(
                            userId = editUserName,
                            sessionName = editSessionName
                        )
                    )
                    showEditDialog = false
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeleteDialog && sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Esborrar sessio") },
            text = { Text("Segur que vols esborrar '${sessionToDelete?.sessionName}'?") },
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
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Dashboard",
                onLanguageSelected = { },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onLogoutClick = { showLogoutDialog = true }
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier.background(
            Brush.verticalGradient(colors = listOf(PurplePrimary, DarkBlueSecondary))
        ),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.Session.route) },
                containerColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova sessio", tint = PurplePrimary)
            }
        }
    ) { paddingValues ->
        if (sessions.isEmpty()) {
            EmptyDashboard(paddingValues)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    DashboardSummary(sessions = sessions)
                }
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
                            showEditDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Tancar sessio") },
            text = { Text("Segur que vols tancar la sessio?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }) {
                    Text("Sortir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun EmptyDashboard(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No hi ha sessions enregistrades.\nClica el boto + per comencar.",
            color = Color.White.copy(alpha = 0.74f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun DashboardSummary(sessions: List<Session>) {
    val sessionsWithMl = sessions.count { it.mlPredictionCount > 0 }
    val averageConfidence = sessions
        .filter { it.mlPredictionCount > 0 }
        .map { it.avgMlConfidence }
        .takeIf { it.isNotEmpty() }
        ?.average() ?: 0.0

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Resum",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryMetric(
                    label = "Sessions",
                    value = sessions.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = "Amb ML",
                    value = sessionsWithMl.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = "Conf.",
                    value = "${(averageConfidence * 100).toInt()}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.68f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SessionItem(session: Session, onDelete: () -> Unit, onEdit: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(session.startTime)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = session.sessionName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.64f)
                )
                if (session.mlPredictionCount > 0) {
                    Text(
                        text = "ML: ${session.dominantActivity} - ${(session.avgMlConfidence * 100).toInt()}% - ${session.mlPredictionCount} prediccions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                } else {
                    Text(
                        text = "Sense resum ML",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.58f)
                    )
                }
            }

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
