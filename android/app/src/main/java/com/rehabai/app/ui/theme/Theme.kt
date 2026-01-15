package com.rehabai.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF1A1A2E),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFF1F5F9),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF1A1A2E),
    secondaryContainer = Color(0xFF14532D),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF1A1A2E),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFF1F5F9),
    error = Color(0xFFEF4444),
    onError = Color(0xFF1A1A2E),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF16162A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF2D2D44),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF3D3D5C)
)

@Composable
fun RehabAITheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
