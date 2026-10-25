package com.cereal.client.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme

/** A full-width 1dp line: the horizontal counterpart of [VerticalDivider]. */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    color: Color = CerealTheme.colorScheme.border,
) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}
