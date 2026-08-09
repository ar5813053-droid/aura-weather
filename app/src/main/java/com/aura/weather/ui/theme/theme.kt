package com.aura.weather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AuraDarkColorScheme = darkColorScheme(
    background = AuraColors.BackgroundBottom,
    surface = AuraColors.BackgroundBottom,
    primary = AuraColors.AccentViolet,
    secondary = AuraColors.AccentBlue,
    onBackground = AuraColors.TextPrimary,
    onSurface = AuraColors.TextPrimary
)

@Composable
fun AuraWeatherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Aura Weather is intentionally dark-first (the liquid-glass effect
    // depends on a dark backdrop), so we use the same scheme regardless
    // of system theme for now.
    MaterialTheme(
        colorScheme = AuraDarkColorScheme,
        typography = AuraTypography,
        content = content
    )
}

