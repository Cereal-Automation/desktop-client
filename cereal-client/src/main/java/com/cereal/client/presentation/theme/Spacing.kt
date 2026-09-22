package com.cereal.client.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class CerealSpacing(
    /** 4.dp — hairline gaps, icon-label gaps */
    val xxs: Dp = 4.dp,
    /** 6.dp — tight component spacing */
    val xs: Dp = 6.dp,
    /** 8.dp — chip/badge padding, small gaps */
    val sm: Dp = 8.dp,
    /** 12.dp — medium gaps */
    val md: Dp = 12.dp,
    /** 16.dp — standard section spacing */
    val lg: Dp = 16.dp,
    /** 20.dp — card inner padding, chip horizontal padding */
    val xl: Dp = 20.dp,
    /** 24.dp — screen edge padding */
    val xxl: Dp = 24.dp,
)

internal val LocalCerealSpacing = staticCompositionLocalOf { CerealSpacing() }

val cerealSpacing = CerealSpacing()
