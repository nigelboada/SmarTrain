package com.udl.smartrain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.udl.smartrain.ui.components.AppHeader
import com.udl.smartrain.ui.i18n.TextKey
import com.udl.smartrain.ui.i18n.text
import com.udl.smartrain.ui.components.ProfileField
import com.udl.smartrain.ui.navigation.Screen
import com.udl.smartrain.ui.viewmodel.MainViewModel

@Composable
fun ProfileScreen(viewModel: MainViewModel, navController: NavController) {
    var userName by remember { mutableStateOf(viewModel.currentUserName) }
    var userPassword by remember { mutableStateOf("********") }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    val ragSettings by viewModel.ragGenerationSettings.collectAsState()
    val language by viewModel.appLanguage.collectAsState()
    var useOllama by remember(ragSettings.useOllama) { mutableStateOf(ragSettings.useOllama) }
    var ollamaBaseUrl by remember(ragSettings.ollamaBaseUrl) { mutableStateOf(ragSettings.ollamaBaseUrl) }
    var ollamaModel by remember(ragSettings.ollamaModel) { mutableStateOf(ragSettings.ollamaModel) }
    var ollamaApiKey by remember(ragSettings.ollamaApiKey) { mutableStateOf(ragSettings.ollamaApiKey) }

    Scaffold(
        topBar = {
            AppHeader(
                title = language.text(TextKey.PROFILE),
                currentLanguage = language,
                onLanguageSelected = viewModel::updateLanguage,
                onProfileClick = { },
                onLogoutClick = { showLogoutDialog = true }
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ProfileField(language.text(TextKey.USER), userName) { userName = it }
            ProfileField(language.text(TextKey.PASSWORD), userPassword) { userPassword = it }

            Spacer(modifier = Modifier.height(24.dp))

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
                Text(
                    text = language.text(TextKey.IA_SETTINGS_HELP_CLOUD),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = language.text(TextKey.USE_OLLAMA),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Switch(
                        checked = useOllama,
                        onCheckedChange = { useOllama = it }
                    )
                }
                OutlinedTextField(
                    value = ollamaBaseUrl,
                    onValueChange = { ollamaBaseUrl = it },
                    label = { Text(language.text(TextKey.BASE_URL)) },
                    singleLine = true,
                    colors = darkOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ollamaModel,
                    onValueChange = { ollamaModel = it },
                    label = { Text(language.text(TextKey.MODEL)) },
                    singleLine = true,
                    colors = darkOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ollamaApiKey,
                    onValueChange = { ollamaApiKey = it },
                    label = { Text(language.text(TextKey.API_KEY)) },
                    singleLine = true,
                    colors = darkOutlinedTextFieldColors(),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                        useOllama = useOllama,
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

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(language.text(TextKey.SIGN_OUT)) },
            text = { Text(language.text(TextKey.SIGN_OUT_QUESTION)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }) {
                    Text(language.text(TextKey.SIGN_OUT))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(language.text(TextKey.CANCEL))
                }
            }
        )
    }
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
