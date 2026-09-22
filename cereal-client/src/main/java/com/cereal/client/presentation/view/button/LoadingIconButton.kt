package com.cereal.client.presentation.view.button

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.model.LoadState
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.error_refreshing_scripts
import com.cereal_automation.cereal_client.generated.resources.synchronize
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val SPIN_DURATION_MILLIS = 1000

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SyncIconButton(
    loadState: LoadState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var showSuccessIcon by remember { mutableStateOf(false) }

    CerealIconButton(
        modifier = modifier,
        enabled = loadState !is LoadState.Loading,
        onClick = { onClick() },
    ) {
        if (loadState is LoadState.Error) {
            TooltipArea(
                tooltip = {
                    Surface(
                        modifier = Modifier.shadow(4.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        CerealText(
                            text = loadState.message,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                },
                delayMillis = 0,
                tooltipPlacement =
                    TooltipPlacement.CursorPoint(
                        alignment = Alignment.BottomEnd,
                        offset = DpOffset((-24).dp, 0.dp),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.SyncProblem,
                    contentDescription = stringResource(Res.string.error_refreshing_scripts),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            val infiniteTransition = rememberInfiniteTransition()
            val angle by
                infiniteTransition.animateFloat(
                    initialValue = 0F,
                    targetValue = 360F,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(SPIN_DURATION_MILLIS, easing = LinearEasing),
                        ),
                )

            if (loadState is LoadState.NotLoading && loadState.success) {
                LaunchedEffect(Unit) {
                    showSuccessIcon = true
                    delay(1000)
                    showSuccessIcon = false
                }
            }

            val icon = if (showSuccessIcon) Icons.Filled.Done else Icons.Filled.Sync

            Icon(
                icon,
                modifier =
                    if (loadState is LoadState.Loading) {
                        Modifier.graphicsLayer { rotationZ = angle }
                    } else {
                        Modifier
                    },
                contentDescription = stringResource(Res.string.synchronize),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
