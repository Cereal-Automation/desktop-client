package com.cereal.client.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.cereal.client.application.ApplicationConfig
import org.koin.compose.koinInject

object CerealTheme {
    val colorScheme: CerealColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCerealColors.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val spacing: CerealSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalCerealSpacing.current
}

internal val LocalCerealColors =
    staticCompositionLocalOf {
        CerealColors()
    }

@Composable
fun CerealTheme(
    applicationConfig: ApplicationConfig = koinInject(),
    content: @Composable () -> Unit,
) {
    // A white-label Brand may override the accent color; stock Cereal uses the default palette.
    val colorScheme =
        remember(applicationConfig.brandPrimaryColorArgb) {
            applicationConfig.brandPrimaryColorArgb?.let { argb ->
                val brand = Color(argb)
                darkColorScheme.copy(
                    primary = brand,
                    secondary = brand,
                    tertiary = brand,
                    surfaceTint = brand,
                )
            } ?: darkColorScheme
        }

    CompositionLocalProvider(
        LocalCerealColors provides cerealColors,
        LocalCerealSpacing provides cerealSpacing,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            typography = typography,
            content = content,
        )
    }
}
