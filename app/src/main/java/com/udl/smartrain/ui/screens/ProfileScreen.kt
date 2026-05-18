package com.udl.smartrain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.udl.smartrain.data.local.AppLanguage
import com.udl.smartrain.ml.DEFAULT_REMOTE_RAG_BASE_URL
import com.udl.smartrain.ml.RagGenerationMode
import com.udl.smartrain.ui.i18n.TextKey
import com.udl.smartrain.ui.i18n.text
import com.udl.smartrain.ui.components.ProfileField
import com.udl.smartrain.ui.viewmodel.MainViewModel
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(viewModel: MainViewModel, navController: NavController) {
    var userName by remember { mutableStateOf(viewModel.currentUserName) }
    var userPassword by remember { mutableStateOf("********") }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    val ragSettings by viewModel.ragGenerationSettings.collectAsState()
    val language by viewModel.appLanguage.collectAsState()
    var selectedMode by remember(ragSettings.mode) { mutableStateOf(ragSettings.mode) }
    var showModeMenu by remember { mutableStateOf(false) }
    var remoteRagBaseUrl by remember(ragSettings.remoteRagBaseUrl) { mutableStateOf(ragSettings.remoteRagBaseUrl) }
    var ollamaBaseUrl by remember(ragSettings.ollamaBaseUrl) { mutableStateOf(ragSettings.ollamaBaseUrl) }
    var ollamaModel by remember(ragSettings.ollamaModel) { mutableStateOf(ragSettings.ollamaModel) }
    var ollamaApiKey by remember(ragSettings.ollamaApiKey) { mutableStateOf(ragSettings.ollamaApiKey) }
    var backendStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedMode, remoteRagBaseUrl, language) {
        backendStatus = if (selectedMode == RagGenerationMode.REMOTE_BACKEND) {
            backendStatusText(language, checkBackendHealth(remoteRagBaseUrl))
        } else {
            null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ProfileField(language.text(TextKey.USER), userName) { userName = it }
            ProfileField(language.text(TextKey.PASSWORD), userPassword) { userPassword = it }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = language.text(TextKey.AI_SETTINGS),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = language.text(TextKey.IA_SETTINGS_HELP),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f)
                )

                Column {
                    Button(
                        onClick = { showModeMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(ragModeTitle(selectedMode, language))
                    }
                    DropdownMenu(
                        expanded = showModeMenu,
                        onDismissRequest = { showModeMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            RagGenerationMode.LOCAL_FALLBACK,
                            RagGenerationMode.CLOUD_QWEN,
                            RagGenerationMode.REMOTE_BACKEND
                        ).forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(ragModeTitle(mode, language)) },
                                onClick = {
                                    selectedMode = mode
                                    if (
                                        mode == RagGenerationMode.REMOTE_BACKEND &&
                                        (remoteRagBaseUrl == "http://10.0.2.2:8000" || remoteRagBaseUrl == "http://192.168.1.75:8000")
                                    ) {
                                        remoteRagBaseUrl = DEFAULT_REMOTE_RAG_BASE_URL
                                    }
                                    showModeMenu = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = ragModeDescription(selectedMode, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f)
                )

                if (selectedMode == RagGenerationMode.CLOUD_QWEN) {
                    OutlinedTextField(
                        value = ollamaApiKey,
                        onValueChange = { ollamaApiKey = it },
                        label = { Text(language.text(TextKey.API_KEY)) },
                        singleLine = true,
                        colors = darkOutlinedTextFieldColors(),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = language.text(TextKey.IA_SETTINGS_HELP_CLOUD),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.64f)
                    )
                }

                if (selectedMode == RagGenerationMode.REMOTE_BACKEND) {
                    OutlinedTextField(
                        value = remoteRagBaseUrl,
                        onValueChange = { remoteRagBaseUrl = it },
                        label = { Text(language.text(TextKey.REMOTE_RAG_URL)) },
                        singleLine = true,
                        colors = darkOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = backendStatus ?: backendCheckingText(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (backendStatus?.contains("actiu", ignoreCase = true) == true ||
                            backendStatus?.contains("active", ignoreCase = true) == true ||
                            backendStatus?.contains("activo", ignoreCase = true) == true
                        ) Color(0xFF46D9A8) else Color(0xFFFFC857)
                    )
                }
            }
        }
        item {
            Button(
                onClick = { showSaveConfirmation = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(language.text(TextKey.SAVE_CHANGES))
            }
        }
    }

    if (showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmation = false },
            title = { Text(language.text(TextKey.CONFIRM_CHANGES)) },
            text = { Text(language.text(TextKey.SAVE_PROFILE_QUESTION)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateUserName(userName)
                    viewModel.updateRagGenerationSettings(
                        mode = selectedMode,
                        useRemoteRag = selectedMode == RagGenerationMode.REMOTE_BACKEND,
                        remoteRagBaseUrl = remoteRagBaseUrl,
                        useOllama = selectedMode == RagGenerationMode.CLOUD_QWEN,
                        ollamaBaseUrl = ollamaBaseUrl,
                        ollamaModel = ollamaModel,
                        ollamaApiKey = ollamaApiKey
                    )
                    showSaveConfirmation = false
                    navController.popBackStack()
                }) {
                    Text(language.text(TextKey.CONFIRM))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmation = false }) {
                    Text(language.text(TextKey.CANCEL))
                }
            }
        )
    }
}

private fun ragModeTitle(mode: RagGenerationMode, language: AppLanguage): String {
    return when (mode) {
        RagGenerationMode.LOCAL_FALLBACK -> when (language) {
            AppLanguage.CATALAN -> "Resum local"
            AppLanguage.ENGLISH -> "Local summary"
            AppLanguage.SPANISH -> "Resumen local"
            AppLanguage.CHINESE -> "\u672c\u5730\u603b\u7ed3"
        }
        RagGenerationMode.CLOUD_QWEN -> when (language) {
            AppLanguage.CATALAN -> "IA cloud"
            AppLanguage.ENGLISH -> "Cloud AI"
            AppLanguage.SPANISH -> "IA cloud"
            AppLanguage.CHINESE -> "\u4e91\u7aef AI"
        }
        RagGenerationMode.REMOTE_BACKEND -> when (language) {
            AppLanguage.CATALAN -> "Backend RAG"
            AppLanguage.ENGLISH -> "RAG backend"
            AppLanguage.SPANISH -> "Backend RAG"
            AppLanguage.CHINESE -> "RAG \u540e\u7aef"
        }
        RagGenerationMode.CUSTOM -> when (language) {
            AppLanguage.CATALAN -> "Avancat"
            AppLanguage.ENGLISH -> "Advanced"
            AppLanguage.SPANISH -> "Avanzado"
            AppLanguage.CHINESE -> "\u9ad8\u7ea7"
        }
    }
}

private fun ragModeDescription(mode: RagGenerationMode, language: AppLanguage): String {
    return when (mode) {
        RagGenerationMode.LOCAL_FALLBACK -> when (language) {
            AppLanguage.CATALAN -> "Funciona sense internet. Genera una recomanacio prudent amb regles locals."
            AppLanguage.ENGLISH -> "Works offline. Generates a careful recommendation with local rules."
            AppLanguage.SPANISH -> "Funciona sin internet. Genera una recomendacion prudente con reglas locales."
            AppLanguage.CHINESE -> "\u53ef\u79bb\u7ebf\u4f7f\u7528\uff0c\u7528\u672c\u5730\u89c4\u5219\u751f\u6210\u8c28\u614e\u5efa\u8bae\u3002"
        }
        RagGenerationMode.CLOUD_QWEN -> when (language) {
            AppLanguage.CATALAN -> "Usa IA cloud per generar un resum mes natural."
            AppLanguage.ENGLISH -> "Uses the selected cloud model to generate a more natural summary."
            AppLanguage.SPANISH -> "Usa IA cloud para generar un resumen mas natural."
            AppLanguage.CHINESE -> "\u4f7f\u7528\u5df2\u9009\u4e91\u7aef\u6a21\u578b\u751f\u6210\u66f4\u81ea\u7136\u7684\u603b\u7ed3\u3002"
        }
        RagGenerationMode.REMOTE_BACKEND -> when (language) {
            AppLanguage.CATALAN -> "Usa el backend RAG amb ChromaDB i retorna fonts recuperades."
            AppLanguage.ENGLISH -> "Uses the RAG backend with ChromaDB and returns retrieved sources."
            AppLanguage.SPANISH -> "Usa el backend RAG con ChromaDB y devuelve fuentes recuperadas."
            AppLanguage.CHINESE -> "\u4f7f\u7528 ChromaDB RAG \u540e\u7aef\u5e76\u8fd4\u56de\u68c0\u7d22\u6765\u6e90\u3002"
        }
        RagGenerationMode.CUSTOM -> ""
    }
}

private fun backendStatusText(language: AppLanguage, active: Boolean): String = when (language) {
    AppLanguage.CATALAN -> if (active) "Backend RAG actiu." else "Backend RAG no accessible."
    AppLanguage.ENGLISH -> if (active) "RAG backend active." else "RAG backend not reachable."
    AppLanguage.SPANISH -> if (active) "Backend RAG activo." else "Backend RAG no accesible."
    AppLanguage.CHINESE -> if (active) "RAG \u540e\u7aef\u5df2\u6fc0\u6d3b\u3002" else "RAG \u540e\u7aef\u4e0d\u53ef\u8bbf\u95ee\u3002"
}

private fun backendCheckingText(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Comprovant backend RAG..."
    AppLanguage.ENGLISH -> "Checking RAG backend..."
    AppLanguage.SPANISH -> "Comprobando backend RAG..."
    AppLanguage.CHINESE -> "\u6b63\u5728\u68c0\u67e5 RAG \u540e\u7aef..."
}

private suspend fun checkBackendHealth(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val endpoint = "${baseUrl.trimEnd('/')}/health"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        connection.responseCode in 200..299
    }.getOrDefault(false)
}

@Composable
private fun darkOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Color.White.copy(alpha = 0.72f),
    cursorColor = Color.White,
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Color.White.copy(alpha = 0.54f),
    focusedContainerColor = Color.White.copy(alpha = 0.08f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.06f)
)
