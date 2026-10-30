package com.chatku.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.chatku.app.data.Appearance
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Warna khusus tampilan chat (mirip WhatsApp). */
data class WaColors(
    val topBar: Color,
    val onTopBar: Color,
    val accent: Color,
    val bubbleOut: Color,
    val bubbleIn: Color,
    val wallpaper: Color,
    val tickRead: Color,
    val tickGrey: Color,
    val secondary: Color,
    val inputBar: Color,
)

private val LightWa = WaColors(
    topBar = Color(0xFF075E54), onTopBar = Color.White, accent = Color(0xFF25D366),
    bubbleOut = Color(0xFFD9FDD3), bubbleIn = Color.White, wallpaper = Color(0xFFEFEAE2),
    tickRead = Color(0xFF53BDEB), tickGrey = Color(0xFF8696A0), secondary = Color(0xFF667781),
    inputBar = Color.White,
)

private val DarkWa = WaColors(
    topBar = Color(0xFF1F2C34), onTopBar = Color(0xFFE9EDEF), accent = Color(0xFF00A884),
    bubbleOut = Color(0xFF005C4B), bubbleIn = Color(0xFF202C33), wallpaper = Color(0xFF0B141A),
    tickRead = Color(0xFF53BDEB), tickGrey = Color(0xFF8696A0), secondary = Color(0xFF8696A0),
    inputBar = Color(0xFF1F2C34),
)

val LocalWa = staticCompositionLocalOf { LightWa }

@Composable
fun ChatKuTheme(content: @Composable () -> Unit) {
    val customDark by Appearance.dark
    val dark = customDark
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(Appearance.accent.value), onPrimary = Color.White,
            background = Color(0xFF111B21), onBackground = Color(0xFFE9EDEF),
            surface = Color(0xFF111B21), onSurface = Color(0xFFE9EDEF),
            surfaceVariant = Color(0xFF202C33), onSurfaceVariant = Color(0xFF8696A0),
            error = Color(0xFFF15C6D),
        )
    } else {
        lightColorScheme(
            primary = Color(Appearance.accent.value), onPrimary = Color.White,
            background = Color.White, onBackground = Color(0xFF111B21),
            surface = Color.White, onSurface = Color(0xFF111B21),
            surfaceVariant = Color(0xFFF0F2F5), onSurfaceVariant = Color(0xFF667781),
            error = Color(0xFFD93025),
        )
    }
    CompositionLocalProvider(LocalWa provides if (dark) DarkWa else LightWa) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
