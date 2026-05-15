package com.udl.smartrain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.udl.smartrain.ui.viewmodel.MainViewModel
import com.udl.smartrain.ui.i18n.TextKey
import com.udl.smartrain.ui.i18n.text

@Composable
fun LoginScreen(viewModel: MainViewModel, onLoginSuccess: () -> Unit) {
    val rememberedUsers by viewModel.rememberedUsers.collectAsState()
    val language by viewModel.appLanguage.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberUser by remember { mutableStateOf(true) }
    var createAccountMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "SmarTrain", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text(
            text = if (createAccountMode) language.text(TextKey.CREATE_ACCOUNT) else language.text(TextKey.LOGIN),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.78f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!createAccountMode && rememberedUsers.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = language.text(TextKey.REMEMBERED_USERS),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.74f)
                )
                rememberedUsers.forEach { user ->
                    TextButton(
                        onClick = {
                            email = user.email
                            password = user.password
                            rememberUser = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = user.email, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(language.text(TextKey.EMAIL)) },
            singleLine = true,
            colors = loginTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(language.text(TextKey.PASSWORD)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            colors = loginTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rememberUser,
                onCheckedChange = { rememberUser = it }
            )
            Text(
                text = language.text(TextKey.REMEMBER_USER),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f)
            )
        }

        viewModel.authError?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (createAccountMode) {
                    viewModel.createAccount(email, password, rememberUser, onLoginSuccess)
                } else {
                    viewModel.signIn(email, password, rememberUser, onLoginSuccess)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (createAccountMode) language.text(TextKey.CREATE_ACCOUNT) else language.text(TextKey.ENTER))
        }

        TextButton(onClick = { createAccountMode = !createAccountMode }) {
            Text(
                text = if (createAccountMode) language.text(TextKey.HAVE_ACCOUNT) else language.text(TextKey.NEW_ACCOUNT),
                color = Color.White
            )
        }
    }
}

@Composable
private fun loginTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Color.White.copy(alpha = 0.74f),
    cursorColor = Color.White,
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Color.White.copy(alpha = 0.56f),
    focusedContainerColor = Color.White.copy(alpha = 0.08f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.06f)
)
