package com.udl.smartrain.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppHeader(title: String, onLanguageSelected: (String) -> Unit, onSettingsClick: () -> Unit) {
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Títol a l'esquerra
        Text(text = title, style = MaterialTheme.typography.titleLarge)

        // Icons i Menús a la dreta
        Row {
            // Menú Idioma
            Box {
                IconButton(onClick = { showLanguageMenu = true }) {
                    Icon(Icons.Default.Translate, "Canviar idioma")
                }
                DropdownMenu(expanded = showLanguageMenu, onDismissRequest = { showLanguageMenu = false }) {
                    listOf("Anglès", "Català", "Castellà", "Xinès").forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = { onLanguageSelected(lang); showLanguageMenu = false }
                        )
                    }
                }
            }
            // Menú Settings
            Box {
                IconButton(onClick = { showSettingsMenu = true }) {
                    Icon(Icons.Default.Settings, "Configuració")
                }
                DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                    DropdownMenuItem(text = { Text("Perfil") }, onClick = { onSettingsClick(); showSettingsMenu = false })
                    DropdownMenuItem(text = { Text("Tancar sessió") }, onClick = { /* Lògica logout */ showSettingsMenu = false })
                }
            }
        }
    }
}