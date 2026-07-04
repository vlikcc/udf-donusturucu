package com.velikececi.udfdonusturucu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AccentNavyLight,
    onPrimary = SurfaceLight,
    secondary = AccentNavyLight,
    background = SurfaceLight,
    surface = SurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = AccentNavyDark,
    onPrimary = SurfaceDark,
    secondary = AccentNavyDark,
    background = SurfaceDark,
    surface = SurfaceDark,
)

@Composable
fun EvrakDonusturucuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
