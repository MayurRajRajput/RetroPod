package com.retropod.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RetroBlack = Color(0xFF000000)
val RetroSurface = Color(0xFF111111)
val RetroGlass = Color(0x661A1A1A)
val RetroWhite = Color(0xFFEEEEEE)
val RetroGray = Color(0xFF888888)
val RetroAccent = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    background = RetroBlack,
    surface = RetroSurface,
    primary = RetroAccent,
    onPrimary = RetroBlack,
    onBackground = RetroWhite,
    onSurface = RetroWhite
)

@Composable
fun RetroPodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
