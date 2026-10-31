package com.cereal.client.presentation.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class CerealButtonType {
    Primary,
    Secondary,
    Surface,
    Success,

    /** Neutral grey: filled with a subtle border, or outlined in the subtle border color. */
    Neutral,
}

enum class CerealButtonSize {
    Small,
    Large,
}

val CerealButtonSize.height: Dp
    get() =
        when (this) {
            CerealButtonSize.Small -> 40.dp
            CerealButtonSize.Large -> 56.dp
        }

@Composable
fun CerealButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    leadingIcon: DrawableResource? = null,
    trailingIcon: DrawableResource? = null,
    iconContentDescription: String? = null,
    enabled: Boolean = true,
    type: CerealButtonType = CerealButtonType.Primary,
    size: CerealButtonSize = CerealButtonSize.Small,
    contentPadding: PaddingValues =
        if (leadingIcon != null) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors =
        when (type) {
            CerealButtonType.Primary -> {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.readableUnderWhite())
            }

            CerealButtonType.Secondary -> {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.readableUnderWhite(),
                )
            }

            CerealButtonType.Surface -> {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }

            CerealButtonType.Success -> {
                ButtonDefaults.buttonColors(
                    containerColor = CerealTheme.colorScheme.success,
                    contentColor = CerealTheme.colorScheme.onSuccess,
                    disabledContainerColor = CerealTheme.colorScheme.success.copy(alpha = 0.5f),
                )
            }

            CerealButtonType.Neutral -> {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

    val shape = MaterialTheme.shapes.small
    val elevation = ButtonDefaults.buttonElevation()
    val border = if (type == CerealButtonType.Neutral) BorderStroke(1.dp, CerealTheme.colorScheme.border) else null

    val textStyle = cerealButtonTextStyle(size)

    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = size.height),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        elevation = elevation,
        shape = shape,
        border = border,
        contentPadding = contentPadding,
        content = {
            ProvideButtonTextStyle(size) {
                if (content != null) {
                    content()
                } else {
                    if (leadingIcon != null) {
                        Icon(
                            painterResource(leadingIcon),
                            contentDescription = iconContentDescription,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        if (!text.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        }
                    }

                    if (!text.isNullOrBlank()) {
                        CerealText(
                            text = text,
                            style = textStyle,
                        )
                    }

                    if (trailingIcon != null) {
                        if (!text.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        }
                        Image(
                            painterResource(trailingIcon),
                            contentDescription = text ?: iconContentDescription,
                            colorFilter =
                                androidx.compose.ui.graphics.ColorFilter
                                    .tint(LocalContentColor.current),
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun CerealTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    type: CerealButtonType = CerealButtonType.Primary,
    size: CerealButtonSize = CerealButtonSize.Small,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val colors =
        when (type) {
            CerealButtonType.Primary -> ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            CerealButtonType.Secondary -> ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
            CerealButtonType.Surface -> ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            CerealButtonType.Success -> ButtonDefaults.textButtonColors(contentColor = CerealTheme.colorScheme.success)
            CerealButtonType.Neutral -> ButtonDefaults.textButtonColors(contentColor = CerealTheme.colorScheme.contentSecondary)
        }

    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = size.height),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = { ProvideButtonTextStyle(size) { content() } },
    )
}

@Composable
fun CerealIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun CerealOutlinedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = CerealTheme.colorScheme.contentSecondary),
        border = BorderStroke(1.dp, CerealTheme.colorScheme.border.copy(alpha = if (enabled) 1f else 0.4f)),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun CerealOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    type: CerealButtonType = CerealButtonType.Primary,
    size: CerealButtonSize = CerealButtonSize.Small,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val (colors, borderColor) =
        when (type) {
            CerealButtonType.Primary -> {
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary) to MaterialTheme.colorScheme.primary
            }

            CerealButtonType.Secondary -> {
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary) to MaterialTheme.colorScheme.secondary
            }

            CerealButtonType.Surface -> {
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface) to MaterialTheme.colorScheme.onSurface
            }

            CerealButtonType.Success -> {
                ButtonDefaults.outlinedButtonColors(contentColor = CerealTheme.colorScheme.success) to
                    CerealTheme.colorScheme.success
            }

            CerealButtonType.Neutral -> {
                ButtonDefaults.outlinedButtonColors(contentColor = CerealTheme.colorScheme.contentSecondary) to
                    CerealTheme.colorScheme.border
            }
        }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = size.height),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = colors,
        elevation = null,
        border = BorderStroke(1.dp, borderColor),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = { ProvideButtonTextStyle(size) { content() } },
    )
}

/**
 * Replaces (rather than merges) the ambient text style, so labels never pick up the theme's
 * body text color and always follow the button's content color.
 */
@Composable
private fun ProvideButtonTextStyle(
    size: CerealButtonSize,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTextStyle provides cerealButtonTextStyle(size), content = content)
}

@Composable
private fun cerealButtonTextStyle(size: CerealButtonSize) =
    when (size) {
        CerealButtonSize.Small -> MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
        CerealButtonSize.Large -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
    }

/** Darkens a fill until white label text on it meets WCAG AA (4.5:1); covers white-label brand colors too. */
private fun Color.readableUnderWhite(): Color {
    var fill = this
    while (WHITE_LUMINANCE_OFFSET / (fill.luminance() + LUMINANCE_OFFSET) < AA_CONTRAST) fill = lerp(fill, Color.Black, DARKEN_STEP)
    return fill
}

private const val LUMINANCE_OFFSET = 0.05f
private const val WHITE_LUMINANCE_OFFSET = 1f + LUMINANCE_OFFSET
private const val AA_CONTRAST = 4.5f
private const val DARKEN_STEP = 0.05f
