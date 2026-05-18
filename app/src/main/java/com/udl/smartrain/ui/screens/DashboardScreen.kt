package com.udl.smartrain.ui.screens

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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.udl.smartrain.domain.model.Session
import com.udl.smartrain.ui.i18n.TextKey
import com.udl.smartrain.ui.i18n.activityLabel
import com.udl.smartrain.ui.i18n.text
import com.udl.smartrain.ui.components.GlassCard
import com.udl.smartrain.ui.navigation.Screen
import com.udl.smartrain.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val sessions by viewModel.sessionsHistory.collectAsState(initial = emptyList())
    val language by viewModel.appLanguage.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<Session?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var sessionToEdit by remember { mutableStateOf<Session?>(null) }
    var editUserName by remember { mutableStateOf("") }
    var editSessionName by remember { mutableStateOf("") }

    if (showEditDialog && sessionToEdit != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(language.text(TextKey.EDIT_SESSION)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = editUserName,
                        onValueChange = { editUserName = it },
                        label = { Text(language.text(TextKey.USER)) }
                    )
                    TextField(
                        value = editSessionName,
                        onValueChange = { editSessionName = it },
                        label = { Text(language.text(TextKey.SESSION_NAME)) }
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
                    Text(language.text(TextKey.SAVE_CHANGES))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(language.text(TextKey.CANCEL))
                }
            }
        )
    }

    if (showDeleteDialog && sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(language.text(TextKey.DELETE_SESSION)) },
            text = { Text(language.text(TextKey.DELETE_SESSION_QUESTION)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSession(sessionToDelete!!)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(language.text(TextKey.DELETE), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(language.text(TextKey.CANCEL))
                }
            }
        )
    }

    if (sessions.isEmpty()) {
        EmptyDashboard(language)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DashboardSummary(sessions = sessions, language = language)
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
                    },
                    onOpen = {
                        navController.navigate(Screen.SessionDetail.createRoute(session.id))
                    },
                    language = language
                )
            }
        }
    }
}

@Composable
private fun EmptyDashboard(language: com.udl.smartrain.data.local.AppLanguage) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = language.text(TextKey.NO_SESSIONS),
            color = Color.White.copy(alpha = 0.74f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun DashboardSummary(sessions: List<Session>, language: com.udl.smartrain.data.local.AppLanguage) {
    val sessionsWithMl = sessions.count { it.mlPredictionCount > 0 }
    val averageConfidence = sessions
        .filter { it.mlPredictionCount > 0 }
        .map { it.avgMlConfidence }
        .takeIf { it.isNotEmpty() }
        ?.average() ?: 0.0
    val totalDistance = sessions.sumOf { it.distanceMetres }
    val totalHighIntensity = sessions.sumOf { it.highIntensityCount }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = language.text(TextKey.SUMMARY),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryMetric(
                    label = language.text(TextKey.SESSIONS),
                    value = sessions.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = language.text(TextKey.DISTANCE),
                    value = formatDashboardDistance(totalDistance),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryMetric(
                    label = language.text(TextKey.ML_SESSIONS),
                    value = sessionsWithMl.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = language.text(TextKey.CONFIDENCE),
                    value = "${(averageConfidence * 100).toInt()}%",
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = language.text(TextKey.HIGH_INTENSITY),
                    value = totalHighIntensity.toString(),
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
fun SessionItem(
    session: Session,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onOpen: () -> Unit,
    language: com.udl.smartrain.data.local.AppLanguage
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(session.startTime)
    val syncLabel = if (session.isSynced) language.text(TextKey.SYNCED) else language.text(TextKey.PENDING)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = session.mlPredictionCount > 0, onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = session.sessionName,
                        style = MaterialTheme.typography.titleSmall,
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
                        text = "${language.activityLabel(session.dominantActivity)} · ${formatDashboardDuration(session.durationSeconds)} · ${formatDashboardDistance(session.distanceMetres)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                    Text(
                        text = "${session.mlPredictionCount} ${language.text(TextKey.PREDICTIONS).lowercase()} · ${(session.avgMlConfidence * 100).toInt()}% · $syncLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.66f)
                    )
                } else {
                    Text(
                        text = language.text(TextKey.NO_ML_SUMMARY),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.58f)
                    )
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = language.text(TextKey.EDIT), tint = Color.White)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = language.text(TextKey.DELETE), tint = Color.White)
                }
            }
        }
    }
}

private fun formatDashboardDistance(distanceMetres: Double): String {
    return if (distanceMetres >= 1000.0) {
        "${"%.2f".format(distanceMetres / 1000.0)} km"
    } else {
        "${distanceMetres.toInt()} m"
    }
}

private fun formatDashboardDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}
