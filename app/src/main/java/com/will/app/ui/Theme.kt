package com.will.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object WillColors {
    val Background = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1A1A1A)
    val Muted = Color(0xFF787878)
    val Row = Color(0xFFF8F8F8)
    val Deeper = Color(0xFFEFEFEF)
    val Accent = Color(0xFF3B82F6)
    val Divider = Color(0xFFECECEC)
    val Outline = Color(0xFFC8C8C8)
    val Composer = Color(0xFFFAFAFA)
    val Supplication = Color(0xFF9E1B32)
    val SupplicationRow = Color(0xFFFBECEE)
}

@Composable
fun WillTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = WillColors.Accent,
            onPrimary = Color.White,
            background = WillColors.Background,
            onBackground = WillColors.Ink,
            surface = WillColors.Background,
            onSurface = WillColors.Ink,
            surfaceVariant = WillColors.Row,
            onSurfaceVariant = WillColors.Muted,
            outlineVariant = WillColors.Divider,
        ),
        content = content,
    )
}
