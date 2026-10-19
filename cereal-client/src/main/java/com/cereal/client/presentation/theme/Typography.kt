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

// Slots not customised below keep Material 3's sizes but must still use the brand font,
// otherwise they silently fall back to the platform default typeface.
private val m3Defaults = Typography()

val typography =
    Typography(
        displayLarge = m3Defaults.displayLarge.copy(fontFamily = MontserratFontFamily),
        displayMedium = m3Defaults.displayMedium.copy(fontFamily = MontserratFontFamily),
        displaySmall = m3Defaults.displaySmall.copy(fontFamily = MontserratFontFamily),
        labelMedium = m3Defaults.labelMedium.copy(fontFamily = MontserratFontFamily),
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
                fontSize = 12.sp,
                fontFamily = MontserratFontFamily,
            ),
        labelSmall =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                fontFamily = MontserratFontFamily,
            ),
    )
