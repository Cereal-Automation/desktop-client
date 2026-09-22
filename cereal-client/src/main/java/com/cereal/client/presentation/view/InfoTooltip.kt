package com.cereal.client.presentation.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * A tooltip with an info icon that shows tooltip text on hover.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoTooltip(
    tooltipText: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    TooltipArea(
        modifier = modifier,
        tooltip = {
            Surface(
                modifier = Modifier.shadow(4.dp),
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                CerealText(
                    text = tooltipText,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        delayMillis = 0,
        tooltipPlacement =
            TooltipPlacement.CursorPoint(
                alignment = Alignment.BottomStart,
                offset = DpOffset(0.dp, 8.dp),
            ),
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = Icons.Filled.Info,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
