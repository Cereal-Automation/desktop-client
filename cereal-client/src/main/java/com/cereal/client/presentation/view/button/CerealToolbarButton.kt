package com.cereal.client.presentation.view.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealOutlinedButton

enum class CerealToolbarButtonVariant { Primary, Green, Flat, Ghost }

/** A toolbar action (leading icon + label) drawn with the design-system buttons. */
@Composable
fun CerealToolbarButton(
    variant: CerealToolbarButtonVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val padding = ButtonDefaults.ButtonWithIconContentPadding
    val spaced: @Composable RowScope.() -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.IconSpacing),
            content = content,
        )
    }
    when (variant) {
        CerealToolbarButtonVariant.Primary -> {
            CerealButton(onClick, modifier, enabled = enabled, type = CerealButtonType.Primary, contentPadding = padding, content = spaced)
        }

        CerealToolbarButtonVariant.Green -> {
            CerealButton(onClick, modifier, enabled = enabled, type = CerealButtonType.Success, contentPadding = padding, content = spaced)
        }

        CerealToolbarButtonVariant.Flat -> {
            CerealButton(onClick, modifier, enabled = enabled, type = CerealButtonType.Neutral, contentPadding = padding, content = spaced)
        }

        CerealToolbarButtonVariant.Ghost -> {
            CerealOutlinedButton(onClick, modifier, enabled, type = CerealButtonType.Neutral, contentPadding = padding, content = spaced)
        }
    }
}
