package com.cereal.client.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme

/**
 * The app's one text input: the design's filled box (38dp single-line, taller when multi-line) with a
 * 1dp border that darkens on focus and turns red on error. Every text field renders through this so they
 * all look alike; labels, help and error text belong to the caller.
 */
@Composable
fun CerealTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val border =
        when {
            isError -> MaterialTheme.colorScheme.error
            focused -> CerealTheme.colorScheme.borderDark
            else -> InputBorder
        }
    val style = textStyle.copy(color = if (enabled) MaterialTheme.colorScheme.onSurface else CerealTheme.colorScheme.contentTertiary)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(if (singleLine) Modifier.height(38.dp) else Modifier.heightIn(min = 92.dp)),
        enabled = enabled,
        textStyle = style,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(InputBg, shape)
                        .border(1.dp, border, shape)
                        .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 12.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leadingIcon?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) CerealText(placeholder, style = style.copy(color = InputPlaceholder))
                    inner()
                }
                trailingIcon?.invoke()
            }
        },
    )
}
