package com.samhrncir.lightswitch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Warm "room with the lights on" palette. */
object LightRoom {
    val background = Color(0xFFFBF4E6)
    val glow = Color(0xFFFFE2A8)
    val onBackground = Color(0xFF3A3328)
    val muted = Color(0xFF8B7F6B)
    val accent = Color(0xFFE09A1F)
    val surface = Color(0xFFFFFBF3)
}

/** Cool "room with the lights off" palette. */
object DarkRoom {
    val background = Color(0xFF14161C)
    val glow = Color(0xFF1C2030)
    val onBackground = Color(0xFFE6E3DC)
    val muted = Color(0xFF8A8F9C)
    val accent = Color(0xFF7FA7FF)
    val surface = Color(0xFF20242D)
}

@Composable
fun LightSwitchTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = DarkRoom.accent,
            onPrimary = Color(0xFF0B1020),
            background = DarkRoom.background,
            onBackground = DarkRoom.onBackground,
            surface = DarkRoom.surface,
            onSurface = DarkRoom.onBackground,
            surfaceVariant = Color(0xFF2A2F3A),
            onSurfaceVariant = DarkRoom.muted,
            outline = Color(0xFF3C424E),
        )
    } else {
        lightColorScheme(
            primary = LightRoom.accent,
            onPrimary = Color.White,
            background = LightRoom.background,
            onBackground = LightRoom.onBackground,
            surface = LightRoom.surface,
            onSurface = LightRoom.onBackground,
            surfaceVariant = Color(0xFFF1E8D6),
            onSurfaceVariant = LightRoom.muted,
            outline = Color(0xFFD8CDB8),
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
