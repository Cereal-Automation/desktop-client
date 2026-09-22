package com.cereal.client.presentation.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A reusable two-column layout commonly used across screens.
 * Left column typically contains a list/navigation, right column contains details.
 */
@Composable
fun TwoColumnLayout(
    leftColumnWidth: Dp = 400.dp,
    modifier: Modifier = Modifier,
    leftColumn: @Composable () -> Unit,
    rightColumn: @Composable () -> Unit,
) {
    Row(modifier.fillMaxSize()) {
        Column(Modifier.width(leftColumnWidth)) {
            leftColumn()
        }

        VerticalDivider()

        Column(Modifier.weight(1f)) {
            rightColumn()
        }
    }
}
