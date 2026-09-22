package com.cereal.client.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

val MontserratFontFamily =
    FontFamily(
        Font(resource = "fonts/Montserrat-Regular.ttf", weight = FontWeight.Normal),
        Font(resource = "fonts/Montserrat-Medium.ttf", weight = FontWeight.Medium),
        Font(resource = "fonts/Montserrat-SemiBold.ttf", weight = FontWeight.SemiBold),
        Font(resource = "fonts/Montserrat-Bold.ttf", weight = FontWeight.Bold),
    )

val typography =
    Typography(
        headlineLarge =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                fontFamily = MontserratFontFamily,
            ),
        headlineMedium =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                fontFamily = MontserratFontFamily,
            ),
        headlineSmall =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                fontFamily = MontserratFontFamily,
            ),
        titleLarge =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = MontserratFontFamily,
            ),
        titleMedium =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = MontserratFontFamily,
            ),
        titleSmall =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = MontserratFontFamily,
            ),
        bodyLarge =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                fontFamily = MontserratFontFamily,
                color = cerealColors.contentSecondary,
            ),
        bodyMedium =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                fontFamily = MontserratFontFamily,
            ),
        labelLarge =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                fontFamily = MontserratFontFamily,
            ),
        bodySmall =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                fontFamily = MontserratFontFamily,
            ),
        labelSmall =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                fontFamily = MontserratFontFamily,
            ),
    )
