package com.timestampcam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFFFC857)
private val Ink = Color(0xFF12110E)
private val Cyan = Color(0xFF7AD7F0)

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241B08),
    secondary = Cyan,
    background = Color.Black,
    surface = Ink,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun TimestampCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
