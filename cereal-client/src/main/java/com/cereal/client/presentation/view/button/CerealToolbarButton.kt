package com.cereal.client.presentation.view.button

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography

enum class CerealToolbarButtonVariant { Primary, Green, Flat, Ghost }

private val FlatButtonBg = Color(0xFF232323)
private val FlatButtonContent = Color(0xFFE8E8E8)
private val GreenButtonContent = Color(0xFF0C2418)

@Composable
fun CerealToolbarButton(
    variant: CerealToolbarButtonVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val toolbarBorder = CerealTheme.colorScheme.border
    val (bg, fg, borderColor) =
        when (variant) {
            CerealToolbarButtonVariant.Primary -> {
                Triple(MaterialTheme.colorScheme.primary, Color.White, Color.Transparent)
            }

            CerealToolbarButtonVariant.Green -> {
                Triple(CerealTheme.colorScheme.success, GreenButtonContent, Color.Transparent)
            }

            CerealToolbarButtonVariant.Flat -> {
                Triple(FlatButtonBg, FlatButtonContent, toolbarBorder)
            }

            CerealToolbarButtonVariant.Ghost -> {
                Triple(Color.Transparent, CerealTheme.colorScheme.contentSecondary, toolbarBorder)
            }
        }
    val resolvedBg = if (enabled) bg else bg.copy(alpha = 0.4f)
    val resolvedFg = if (enabled) fg else fg.copy(alpha = 0.4f)
    val resolvedBorder = if (enabled) borderColor else borderColor.copy(alpha = 0.4f)

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(resolvedBg)
                .border(1.dp, resolvedBorder, RoundedCornerShape(8.dp))
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                    indication = ripple(color = MaterialTheme.colorScheme.primary),
                    interactionSource = remember { MutableInteractionSource() },
                ).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedFg) {
            ProvideTextStyle(
                CerealTypography.controlLabel.copy(color = resolvedFg),
            ) {
                content()
            }
        }
    }
}
