package com.sacco.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF0B6E4F)
val SuccessColor = Color(0xFF2E7D32)
val WarningColor = Color(0xFFB26A00)
val ErrorColor = Color(0xFFC62828)

private val Light = lightColorScheme(
    primary = Green, onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0D8), onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF8A5A00), tertiary = Color(0xFF1F4E79)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF7CD9B4), onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF00513A), onPrimaryContainer = Color(0xFFB7F0D8)
)

@Composable
fun SaccoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
