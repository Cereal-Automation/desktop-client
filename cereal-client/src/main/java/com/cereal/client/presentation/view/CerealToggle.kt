package com.cereal.client.presentation.view

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme

/**
 * On/off switch. Announced as a switch and keyboard-operable (Tab to focus, Space to flip), with a
 * focus ring. Pass a null [onCheckedChange] when a parent row is the `toggleable` (so the label is
 * clickable too); the switch then only renders state.
 */
@Composable
fun CerealToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val trackColor = if (checked) readableUnderWhite(MaterialTheme.colorScheme.primary) else CerealTheme.colorScheme.border
    val thumbOffset by animateDpAsState(if (checked) 17.5.dp else 2.5.dp, label = "cerealToggleThumb")
    val alpha = if (enabled) 1f else 0.4f
    val shape = RoundedCornerShape(10.dp)
    val toggleModifier =
        if (onCheckedChange != null) {
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            )
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .then(toggleModifier)
                .size(width = 35.dp, height = 20.dp)
                .border(2.dp, if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent, shape)
                .clip(shape)
                .background(trackColor.copy(alpha = trackColor.alpha * alpha)),
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbOffset, y = 2.5.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = alpha)),
        )
    }
}
