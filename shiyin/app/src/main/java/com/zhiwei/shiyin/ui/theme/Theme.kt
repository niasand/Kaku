package com.zhiwei.shiyin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0E1110)
val Paper = Color(0xFF171C1A)
val Panel = Color(0xFF242B28)
val Gold = Color(0xFFF2C14E)
val RecordRed = Color(0xFFE23D3D)
val Mist = Color(0xFF9AA39C)
val Cream = Color(0xFFF4F1EA)

private val Scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF241A00),
    secondary = Color(0xFF8FBFB2),
    background = Ink,
    surface = Paper,
    surfaceVariant = Panel,
    onBackground = Cream,
    onSurface = Cream,
    onSurfaceVariant = Mist,
    error = RecordRed,
)

@Composable
fun ShiyinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
