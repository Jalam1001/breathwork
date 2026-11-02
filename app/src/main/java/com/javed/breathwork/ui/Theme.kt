package com.javed.breathwork.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF9FD8FF), onPrimary = Color(0xFF00344F), primaryContainer = Color(0xFF1D3B5C), onPrimaryContainer = Color(0xFFDDF1FF),
    secondary = Color(0xFFB8E6C9), tertiary = Color(0xFFFFC58A),
    background = Color(0xFF0B1320), surface = Color(0xFF0B1320), surfaceVariant = Color(0xFF1A2536), onSurfaceVariant = Color(0xFFC3CCD8),
)
private val Light = lightColorScheme(
    primary = Color(0xFF1F6391), onPrimary = Color.White, primaryContainer = Color(0xFFCDE5FF), onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF2E6B47), tertiary = Color(0xFF8A5100),
    background = Color(0xFFF5F8FC), surface = Color(0xFFF5F8FC), surfaceVariant = Color(0xFFE0E7F0), onSurfaceVariant = Color(0xFF424B56),
)

@Composable
fun BreathTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
