package com.cereal.client.presentation.view.chip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.StatusDot

/**
 * Pill-shaped chip rendering a short label in [CerealTypography.statusChipLabel]. Used
 * for status indicators (running/error/success), trial/price badges on script cards,
 * and active-filter chips. Optional leading dot, border, trailing icon, and click
 * handler cover the variants seen in the codebase.
 */
@Composable
fun Pill(
    text: String,
    contentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    leadingDot: Boolean = false,
    trailingIcon: ImageVector? = null,
    trailingIconContentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = DefaultPillPadding,
    textStyle: TextStyle? = null,
) {
    val shape = CircleShape
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(backgroundColor)
                .let { if (borderColor != null) it.border(1.dp, borderColor, shape) else it }
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingDot) {
            StatusDot(color = contentColor)
        }
        CerealText(
            text = text,
            color = contentColor,
            style = textStyle ?: CerealTypography.statusChipLabel,
        )
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = trailingIconContentDescription,
                tint = contentColor,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

private val DefaultPillPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
