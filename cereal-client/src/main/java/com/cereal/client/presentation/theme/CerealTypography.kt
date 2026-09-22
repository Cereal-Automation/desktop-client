package com.cereal.client.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp

object CerealTypography {
    val sectionLabel: TextStyle
        @Composable
        @ReadOnlyComposable
        get() =
            MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = TextUnit(0.14f, TextUnitType.Em),
            )

    val controlLabel: TextStyle
        @Composable
        @ReadOnlyComposable
        get() =
            MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )

    val paneTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() =
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                letterSpacing = TextUnit(-0.01f, TextUnitType.Em),
            )

    val statusChipLabel: TextStyle
        @Composable
        @ReadOnlyComposable
        get() =
            MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = TextUnit(0.02f, TextUnitType.Em),
            )

    val rowTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() =
            MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
            )
}
