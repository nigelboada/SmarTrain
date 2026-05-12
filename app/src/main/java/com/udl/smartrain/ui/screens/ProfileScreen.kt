package com.udl.smartrain.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.udl.smartrain.ui.components.AppHeader
import com.udl.smartrain.ui.components.ProfileField
import com.udl.smartrain.ui.navigation.Screen
import com.udl.smartrain.ui.theme.DarkBlueSecondary
import com.udl.smartrain.ui.theme.PurplePrimary
import com.udl.smartrain.ui.viewmodel.MainViewModel

@Composable
fun ProfileScreen(viewModel: MainViewModel, navController: NavController) {
    var userName by remember { mutableStateOf(viewModel.currentUserName) }
    var userPassword by remember { mutableStateOf("********") }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Perfil",
                onLanguageSelected = { },
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
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            ProfileField("Usuari", userName) { userName = it }
            ProfileField("Contrasenya", userPassword) { userPassword = it }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { showSaveConfirmation = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar canvis")
            }
        }
    }

    if (showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmation = false },
            title = { Text("Confirmar canvis") },
            text = { Text("Vols aplicar els canvis al perfil?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateUserName(userName)
                    showSaveConfirmation = false
                    navController.popBackStack()
                }) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Tancar sessio") },
            text = { Text("Segur que vols tancar la sessio?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.signOut()
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
