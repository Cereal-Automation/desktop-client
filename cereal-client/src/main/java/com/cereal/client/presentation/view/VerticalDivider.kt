package com.cereal.client.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme

@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = CerealTheme.colorScheme.border,
    thickness: Dp = 1.dp,
    startIndent: Dp = 0.dp,
) {
    val indentMod =
        if (startIndent.value != 0f) {
            Modifier.padding(start = startIndent)
        } else {
            Modifier
        }
    Box(
        modifier
            .then(indentMod)
            .fillMaxHeight()
            .width(thickness)
            .background(color = color),
    )
}
