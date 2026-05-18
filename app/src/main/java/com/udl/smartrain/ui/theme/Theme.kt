package com.udl.smartrain.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB7A6FF),
    secondary = Color(0xFF9DA3B5),
    tertiary = Color(0xFFE1BEE7),
    background = Color(0xFF101218),
    surface = Color(0xFF1D1B24),
    onPrimary = Color.White,
    onSurface = Color(0xFFECEAF2)
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
    darkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkMode) DarkColorScheme else LightColorScheme,
        typography = Typography, // La teva tipografia existent
        content = content
    )
}
