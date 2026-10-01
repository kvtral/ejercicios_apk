package com.kvtral.ejercicios.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFFF5722)
private val OrangeDark = Color(0xFFFF8A65)
private val Teal = Color(0xFF00897B)
private val TealLight = Color(0xFF4DB6AC)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF37474F),
    secondaryContainer = Color(0xFFE3E8EB),
    tertiary = Teal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2DFDB),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFFFFBFA),
    surface = Color(0xFFFFFBFA),
)

private val DarkColors = darkColorScheme(
    primary = OrangeDark,
    onPrimary = Color(0xFF3A0B00),
    primaryContainer = Color(0xFF8A2A00),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFB0BEC5),
    tertiary = TealLight,
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFFB2DFDB),
)

@Composable
fun EjerciciosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
