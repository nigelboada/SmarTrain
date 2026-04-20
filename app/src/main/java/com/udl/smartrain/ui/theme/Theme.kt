package com.udl.smartrain.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimary,          // Botons, accent principal
    secondary = DarkBlueSecondary,    // Elements secundaris, icones
    background = BackgroundWhite,     // Fons de la pantalla
    surface = White,                  // Fons de les Cards
    onPrimary = White,                // Text sobre el porpra (Blanc)
    onSurface = Color(0xFF1C1B1F)     // Text sobre el blanc (Negre suau)
)

@Composable
fun SmarTrainTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography, // La teva tipografia existent
        content = content
    )
}