package com.ezansi.app.ui.theme

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

// High-contrast colours for WCAG AA compliance on low-end screens
private val EzansiLightColors = lightColorScheme(
    primary = Color(0xFF1A5D1A),          // Deep green — primary brand
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F0B8),
    onPrimaryContainer = Color(0xFF002200),
    secondary = Color(0xFF4A6741),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFCFCFC),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF1A1C19),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val EzansiDarkColors = darkColorScheme(
    primary = Color(0xFF9CD49C),
    onPrimary = Color(0xFF003A00),
    primaryContainer = Color(0xFF005300),
    onPrimaryContainer = Color(0xFFB8F0B8),
    secondary = Color(0xFFB8CCB0),
    onSecondary = Color(0xFF243424),
    background = Color(0xFF1A1C19),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF1A1C19),
    onSurface = Color(0xFFE2E3DD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun EzansiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic colour on Android 12+ (no GMS required)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(LocalContext.current)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme ->
            dynamicLightColorScheme(LocalContext.current)
        darkTheme -> EzansiDarkColors
        else -> EzansiLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
