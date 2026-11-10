package com.omstudy.ai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E7FF),
    onPrimaryContainer = Color(0xFF111A5E),
    secondary = Color(0xFFE65100),
    onSecondary = Color.White,
    background = Color(0xFFFAFAFE),
    surface = Color(0xFFFAFAFE)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FA8DA),
    onPrimary = Color(0xFF10186B),
    primaryContainer = Color(0xFF263078),
    onPrimaryContainer = Color(0xFFE3E7FF),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF3E2000),
    background = Color(0xFF12131A),
    surface = Color(0xFF12131A)
)

@Composable
fun OmTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
