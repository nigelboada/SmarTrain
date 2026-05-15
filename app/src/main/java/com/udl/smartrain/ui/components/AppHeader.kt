package com.udl.smartrain.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.udl.smartrain.data.local.AppLanguage
import com.udl.smartrain.ui.i18n.TextKey
import com.udl.smartrain.ui.i18n.text

@Composable
fun AppHeader(
    title: String,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onProfileClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White)

        Row {
            Box {
                IconButton(onClick = { showLanguageMenu = true }) {
                    Icon(Icons.Default.Translate, contentDescription = currentLanguage.text(TextKey.CHANGE_LANGUAGE), tint = Color.White)
                }
                DropdownMenu(expanded = showLanguageMenu, onDismissRequest = { showLanguageMenu = false }) {
                    AppLanguage.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (lang == currentLanguage) "${lang.label} ✓" else lang.label
                                )
                            },
                            onClick = {
                                onLanguageSelected(lang)
                                showLanguageMenu = false
                            }
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showSettingsMenu = true }) {
                    Icon(Icons.Default.Settings, contentDescription = currentLanguage.text(TextKey.PROFILE), tint = Color.White)
                }
                DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(currentLanguage.text(TextKey.PROFILE)) },
                        onClick = {
                            onProfileClick()
                            showSettingsMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(currentLanguage.text(TextKey.SIGN_OUT)) },
                        onClick = {
                            onLogoutClick()
                            showSettingsMenu = false
                        }
                    )
                }
            }
        }
    }
}
