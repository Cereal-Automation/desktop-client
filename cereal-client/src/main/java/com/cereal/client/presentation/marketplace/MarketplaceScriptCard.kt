package com.cereal.client.presentation.marketplace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.button.CerealToolbarButton
import com.cereal.client.presentation.view.button.CerealToolbarButtonVariant
import com.cereal.client.presentation.view.chip.Pill
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.marketplace_free_badge
import com.cereal_automation.cereal_client.generated.resources.marketplace_free_trial_days
import com.cereal_automation.cereal_client.generated.resources.marketplace_install
import com.cereal_automation.cereal_client.generated.resources.marketplace_status_maintenance
import com.cereal_automation.cereal_client.generated.resources.marketplace_status_maintenance_short
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ScriptCard(
    script: MarketplaceScript,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .hoverable(interactionSource)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        border =
            BorderStroke(
                1.dp,
                if (isHovered) CerealTheme.colorScheme.borderDark else CerealTheme.colorScheme.border,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Head: icon + title/author + price chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScriptIcon(script = script, size = 45.dp)

                Column(modifier = Modifier.weight(1f)) {
                    CerealText(
                        text = script.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    script.developer?.let { dev ->
                        Spacer(Modifier.height(3.dp))
                        CerealText(
                            text = dev.handle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CerealTheme.colorScheme.contentTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Top),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (script.maintenanceMode) MaintenanceBadge()
                    FreeTrialBadge(script)
                    PriceBadge(script)
                }
            }

            // Description — fixed to 2 lines so cards align.
            // lineHeight 22sp ≈ 1.55× the 14sp body (matches CSS .market-card .desc line-height: 1.55).
            CerealText(
                text = script.shortDescription ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = CerealTheme.colorScheme.contentSecondary,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.weight(1f))

            // Meta: rating. The whole card opens the detail dialog, where install/purchase lives.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint =
                        if (script.averageRating != null) {
                            CerealTheme.colorScheme.warning
                        } else {
                            CerealTheme.colorScheme.contentSubtle
                        },
                    modifier = Modifier.size(14.dp),
                )
                CerealText(
                    text = script.averageRating?.let { "%.1f".format(it) } ?: "—",
                    style = CerealTypography.controlLabel,
                    color = CerealTheme.colorScheme.contentSecondary,
                )
            }
        }
    }
}
