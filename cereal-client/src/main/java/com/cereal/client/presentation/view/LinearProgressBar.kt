package com.cereal.client.presentation.view

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

private const val HIGHLIGHT_SWEEP_DURATION_MILLIS = 2200
private const val RGB_SHAKE_SETTLE_DURATION_MILLIS = 120

@Composable
fun LinearProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    heightDp: Float = 8f,
    baseColor: Color = MaterialTheme.colorScheme.primary,
    bgColor: Color = CerealTheme.colorScheme.progressTrack,
) {
    val infinite = rememberInfiniteTransition()

    val highlightShift by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(HIGHLIGHT_SWEEP_DURATION_MILLIS, easing = LinearEasing)),
    )

    val rgbShake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(1200, 2200))
            rgbShake.snapTo(Random.nextFloat() * 4f - 2f)
            rgbShake.animateTo(0f, tween(RGB_SHAKE_SETTLE_DURATION_MILLIS))
        }
    }

    Canvas(
        modifier =
            modifier
                .height(heightDp.dp)
                .fillMaxWidth(),
    ) {
        val barHeight = size.height
        val barWidth = size.width

        drawPath(
            path = progressPath(barWidth, barHeight),
            color = bgColor,
        )

        val filledWidth = barWidth * progress.coerceIn(0f, 1f)
        if (filledWidth > 0f) {
            listOf(
                Color.Cyan.copy(alpha = 0.4f) to rgbShake.value,
                Color.Red.copy(alpha = 0.4f) to -rgbShake.value,
                baseColor to 0f,
            ).forEach { (color, offset) ->
                drawPath(
                    path = progressPath(filledWidth, barHeight, offset),
                    color = color,
                )
            }

            val highlightWidth = barHeight * 2
            val start = Offset(barWidth * highlightShift, 0f)
            val end = Offset(barWidth * highlightShift + highlightWidth, barHeight)
            drawRect(
                brush =
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                        start = start,
                        end = end,
                    ),
                topLeft = Offset(0f, 0f),
                size = Size(filledWidth, barHeight),
                blendMode = BlendMode.Lighten,
            )
        }
    }
}

private const val MAX_SLANT = 12f

private fun progressPath(
    width: Float,
    height: Float,
    offsetX: Float = 0f,
): Path =
    Path().apply {
        val slant = min(height, MAX_SLANT)
        moveTo(0f + offsetX + slant, 0f)
        lineTo(width + offsetX, 0f)
        lineTo(width + offsetX - slant, height)
        lineTo(0f + offsetX, height)
        close()
    }
