package com.qiell.doomscrollblocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF315A7D),
    onPrimary = Color.White,
    secondary = Color(0xFF5B7A56),
    background = Color(0xFFF6F8FA),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC3E5),
    secondary = Color(0xFFA8C69F),
    background = Color(0xFF10222E),
    surface = Color(0xFF1B303E),
)

@Composable
fun DoomscrollTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
