package com.udl.smartrain.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.navigation.NavController
import com.udl.smartrain.ui.components.AppHeader
import com.udl.smartrain.ui.components.ProfileField
import com.udl.smartrain.ui.theme.DarkBlueSecondary
import com.udl.smartrain.ui.theme.PurplePrimary
import com.udl.smartrain.ui.viewmodel.MainViewModel

@Composable
fun ProfileScreen(viewModel: MainViewModel, navController: NavController) {
    // Estats locals per als camps
    var userName by remember { mutableStateOf(viewModel.currentUserName) }
    var userPassword by remember { mutableStateOf("********") } // Exemple

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Perfil",
                onLanguageSelected = { /* ... */ },
                onProfileClick = { },
                onLogoutClick = { showLogoutDialog = true }
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(PurplePrimary, DarkBlueSecondary)))
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp)
        ) {
            ProfileField("Nom d'usuari", userName) { userName = it }
            ProfileField("Contrasenya", userPassword) { userPassword = it }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { showSaveConfirmation = true }, // Obrim diàleg de confirmació
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar canvis")
            }
        }
    }

    // Diàleg de Confirmació de Guardat
    if (showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmation = false },
            title = { Text("Confirmar canvis") },
            text = { Text("Estàs segur que vols aplicar els canvis al teu perfil?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateUserName(userName)
                    showSaveConfirmation = false
                    navController.popBackStack() // Tornem enrere un cop confirmat
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmation = false }) { Text("Cancel·lar") }
            }
        )
    }

    // Diàleg de sortida (manteníem la consistència)
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Tancar sessió") },
            text = { Text("Estàs segur que vols tancar la sessió?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    // Lògica logout...
                }) { Text("Sí, sortir") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel·lar") }
            }
        )
    }
}