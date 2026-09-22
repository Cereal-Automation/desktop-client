package com.cereal.client.presentation.view

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.activity_indicator
import com.cereal_automation.cereal_client.generated.resources.ic_activity_indicator
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val SPIN_DURATION_MILLIS = 1000

@Composable
fun ActivityIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 360F,
        targetValue = 0F,
        animationSpec =
            infiniteRepeatable(
                animation = tween(SPIN_DURATION_MILLIS, easing = LinearEasing),
            ),
    )

    Image(
        painter = painterResource(Res.drawable.ic_activity_indicator),
        contentDescription = stringResource(Res.string.activity_indicator),
        modifier =
            modifier
                .size(size)
                .graphicsLayer {
                    rotationZ = angle
                },
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
    )
}
