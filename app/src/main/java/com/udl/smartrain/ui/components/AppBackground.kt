package com.udl.smartrain.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun AppBackground(darkMode: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val colors = if (darkMode) {
        listOf(Color(0xFF24212B), Color(0xFF101218))
    } else {
        listOf(Color(0xFF6A1B9A), Color(0xFF1A237E))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = colors
                )
            ),
        content = content
    )
}
