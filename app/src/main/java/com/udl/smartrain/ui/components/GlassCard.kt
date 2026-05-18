package com.udl.smartrain.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val isDarkMode = MaterialTheme.colorScheme.background == Color(0xFF101218)
    val containerAlpha = if (isDarkMode) 0.10f else 0.15f
    val borderAlpha = if (isDarkMode) 0.14f else 0.20f
    val shape = RoundedCornerShape(14.dp)

    Card(
        modifier = modifier
            .background(Color.Transparent)
            .border(1.dp, Color.White.copy(alpha = borderAlpha), shape),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = containerAlpha)),
        shape = shape
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
