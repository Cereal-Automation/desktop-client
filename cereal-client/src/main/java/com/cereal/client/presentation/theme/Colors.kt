package com.cereal.client.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Design-token defaults are raw ARGB color literals; naming each as a constant adds no clarity.
@Suppress("MagicNumber")
class CerealColors(
    val border: Color = Color(0xFF2A2A2A),
    val contentSecondary: Color = Color(0xFFB4AFB6),
    val contentTertiary: Color = Color(0xFF8A8A8A),
    val contentSubtle: Color = Color(0xFF6A6A6A),
    val backgroundLight: Color = Color(0xFFF6F6F6),
    val backgroundDark: Color = Color(0xFF141414),
    val cardDark: Color = Color(0xFF161616),
    val borderDark: Color = Color(0xFF3E3E3E),
    val success: Color = Color(0xFF2ECC71),
    val warning: Color = Color(0xFFF5B73A),
    val info: Color = Color(0xFF4F8CFF),
    val link: Color = Color(0xFF64B5F6),
    val highlight: Color = Color(0xFF0075FF),
    val progressTrack: Color = Color(0xFF232323),
    val securityWeak: Color = Color(0xFFE54848),
    val securityFair: Color = Color(0xFFF2C94C),
    val securityGood: Color = Color(0xFF3B82F6),
)

val cerealColors = CerealColors()

val darkColorScheme =
    darkColorScheme(
        primary = Color(0xFFE54848),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF3E1212),
        onPrimaryContainer = Color(0xFFFFDADA),
        secondary = Color(0xFFE54848),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF3E1212),
        onSecondaryContainer = Color(0xFFFFDADA),
        tertiary = Color(0xFFE54848),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF3E1212),
        onTertiaryContainer = Color(0xFFFFDADA),
        background = Color(0xFF1C1C1C),
        onBackground = Color.White,
        surface = Color(0xFF161616),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF222222),
        onSurfaceVariant = Color(0xFFB4AFB6),
        surfaceTint = Color(0xFFE54848),
        inverseSurface = Color(0xFFE6E1E5),
        inverseOnSurface = Color(0xFF313033),
        error = Color(0xFFE54848),
        onError = Color(0xFF1C1C1E),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF6A6A6A),
        outlineVariant = Color(0xFF3E3E3E),
        scrim = Color.Black,
    )
