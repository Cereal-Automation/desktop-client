package com.cereal.client.presentation.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors =
        when (type) {
            CerealButtonType.Primary -> {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            }

            CerealButtonType.Secondary -> {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
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
                    disabledContainerColor = CerealTheme.colorScheme.success.copy(alpha = 0.5f),
                )
            }
        }

    val shape = MaterialTheme.shapes.small
    val elevation = ButtonDefaults.buttonElevation()
    val border: BorderStroke? = null

    val textStyle = cerealButtonTextStyle(size)

    Button(
        onClick = onClick,
        modifier = modifier.height(size.height),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        elevation = elevation,
        shape = shape,
        border = border,
        contentPadding = ButtonDefaults.ContentPadding,
        content = {
            ProvideTextStyle(value = textStyle) {
                if (content != null) {
                    content()
                } else {
                    if (leadingIcon != null) {
                        Icon(
                            painterResource(leadingIcon),
                            contentDescription = iconContentDescription,
                            modifier = Modifier.size(20.dp),
                        )
                        if (!text.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
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
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Image(
                            painterResource(trailingIcon),
                            contentDescription = text ?: iconContentDescription,
                            colorFilter =
                                androidx.compose.ui.graphics.ColorFilter
                                    .tint(LocalContentColor.current),
                            modifier = Modifier.size(20.dp),
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
        }

    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
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
fun CerealIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconToggleButtonColors = IconButtonDefaults.iconToggleButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun CerealFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.filledIconButtonColors(),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun CerealFilledIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.filledIconToggleButtonColors(),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun CerealFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.filledTonalIconButtonColors(),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun CerealFilledTonalIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(),
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
        colors = IconButtonDefaults.outlinedIconButtonColors(),
        border = IconButtonDefaults.outlinedIconButtonBorder(enabled),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun CerealOutlinedIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    OutlinedIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.outlinedIconToggleButtonColors(),
        border = IconButtonDefaults.outlinedIconToggleButtonBorder(enabled, checked),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun CerealElevatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun CerealFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = contentPadding,
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
        }

    val textStyle =
        when (size) {
            CerealButtonSize.Small -> {
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            CerealButtonSize.Large -> {
                MaterialTheme.typography.headlineSmall
            }
        }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(size.height),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = colors,
        elevation = null,
        border = BorderStroke(1.dp, borderColor),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = {
            ProvideTextStyle(value = textStyle) {
                content()
            }
        },
    )
}

@Composable
fun CerealFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = contentColorFor(MaterialTheme.colorScheme.primary),
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = FloatingActionButtonDefaults.shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun cerealButtonTextStyle(size: CerealButtonSize) =
    when (size) {
        CerealButtonSize.Small -> {
            MaterialTheme.typography.labelLarge.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 14.sp,
            )
        }

        CerealButtonSize.Large -> {
            MaterialTheme.typography.headlineSmall
        }
    }
