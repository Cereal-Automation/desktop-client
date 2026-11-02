package com.cereal.client.presentation.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

/**
 * A tooltip with an info icon that shows tooltip text on hover, and on keyboard focus (the icon is a tab
 * stop). Screen readers get the text as part of the icon's description.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoTooltip(
    tooltipText: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val tooltip: @Composable () -> Unit = {
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
    }
    TooltipArea(
        modifier = modifier,
        tooltip = tooltip,
        delayMillis = 0,
        tooltipPlacement =
            TooltipPlacement.CursorPoint(
                alignment = Alignment.BottomStart,
                offset = DpOffset(0.dp, 8.dp),
            ),
    ) {
        Box {
            Icon(
                modifier =
                    Modifier
                        .size(16.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable(),
                imageVector = Icons.Filled.Info,
                contentDescription = "$contentDescription. $tooltipText",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            if (focused) {
                Popup(alignment = Alignment.BottomStart, offset = IntOffset(0, FOCUS_TOOLTIP_OFFSET_PX)) { tooltip() }
            }
        }
    }
}

private const val FOCUS_TOOLTIP_OFFSET_PX = 24
