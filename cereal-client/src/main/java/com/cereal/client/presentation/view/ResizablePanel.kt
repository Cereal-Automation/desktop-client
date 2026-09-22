package com.cereal.client.presentation.view

import androidx.compose.animation.core.Spring.StiffnessLow
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class PanelState(
    val collapsedSize: Dp = 24.dp,
    initialExpandedSize: Dp = 400.dp,
    val expandedSizeMin: Dp = 300.dp,
    val expandedSizeMax: Dp = 600.dp,
    initialIsExpanded: Boolean = true,
    val splitter: SplitterState = SplitterState(),
) {
    var expandedSize by mutableStateOf(initialExpandedSize)
    var isExpanded by mutableStateOf(initialIsExpanded)
}

class SplitterState(
    initialIsResizing: Boolean = false,
    initialIsResizeEnabled: Boolean = true,
) {
    var isResizing by mutableStateOf(initialIsResizing)
    var isResizeEnabled by mutableStateOf(initialIsResizeEnabled)
}

/**
 * Creates a [PanelState] that survives recomposition. Construct panel state through this factory
 * (or wrap a manual `PanelState()` in `remember`) — the holder backs its fields with
 * `mutableStateOf`, so building it directly in a composable body would reset on every recomposition.
 */
@Composable
fun rememberPanelState(): PanelState = remember { PanelState() }

@Composable
fun ResizablePanel(
    state: PanelState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(if (state.isExpanded) 1f else 0f, SpringSpec(stiffness = StiffnessLow))

    Box(modifier) {
        Box(Modifier.fillMaxSize().graphicsLayer(alpha = alpha)) {
            content()
        }
    }
}
