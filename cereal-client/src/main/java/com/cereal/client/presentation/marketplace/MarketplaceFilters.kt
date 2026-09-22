package com.cereal.client.presentation.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.DropdownMenu
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.SectionLabel
import com.cereal.client.presentation.view.button.CerealToolbarButton
import com.cereal.client.presentation.view.button.CerealToolbarButtonVariant
import com.cereal.client.presentation.view.chip.Pill
import com.cereal.client.presentation.view.table.PaneTableMetaIconColor
import com.cereal.client.presentation.view.table.PaneTablePadding
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.filter_action_label
import com.cereal_automation.cereal_client.generated.resources.filter_active_creator_chip
import com.cereal_automation.cereal_client.generated.resources.filter_active_price_chip
import com.cereal_automation.cereal_client.generated.resources.filter_clear
import com.cereal_automation.cereal_client.generated.resources.filter_section_creator
import com.cereal_automation.cereal_client.generated.resources.filter_section_price
import com.cereal_automation.cereal_client.generated.resources.marketplace_filter_community_all
import com.cereal_automation.cereal_client.generated.resources.marketplace_filter_community_yes
import com.cereal_automation.cereal_client.generated.resources.marketplace_filter_price_all
import com.cereal_automation.cereal_client.generated.resources.marketplace_filter_price_free
import com.cereal_automation.cereal_client.generated.resources.marketplace_filter_price_paid
import com.cereal_automation.cereal_client.generated.resources.marketplace_search_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FilterMenuButton(
    priceFilter: MarketplaceViewModel.PriceFilter,
    onPriceFilterChanged: (MarketplaceViewModel.PriceFilter) -> Unit,
    communityFilter: MarketplaceViewModel.CommunityFilter,
    onCommunityFilterChanged: (MarketplaceViewModel.CommunityFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasActiveFilter =
        priceFilter != MarketplaceViewModel.PriceFilter.ALL ||
            communityFilter != MarketplaceViewModel.CommunityFilter.ALL

    Box {
        CerealToolbarButton(
            variant = CerealToolbarButtonVariant.Ghost,
            onClick = { expanded = true },
        ) {
            Box {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                if (hasActiveFilter) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            CerealText(text = stringResource(Res.string.filter_action_label))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        ) {
            FilterSectionLabel(stringResource(Res.string.filter_section_price))
            MarketplaceViewModel.PriceFilter.entries.forEach { option ->
                val label =
                    when (option) {
                        MarketplaceViewModel.PriceFilter.ALL -> stringResource(Res.string.marketplace_filter_price_all)
                        MarketplaceViewModel.PriceFilter.FREE -> stringResource(Res.string.marketplace_filter_price_free)
                        MarketplaceViewModel.PriceFilter.PAID -> stringResource(Res.string.marketplace_filter_price_paid)
                    }
                FilterRadioItem(
                    label = label,
                    selected = priceFilter == option,
                    onClick = {
                        onPriceFilterChanged(option)
                        expanded = false
                    },
                )
            }

            HorizontalDivider(thickness = 1.dp, color = CerealTheme.colorScheme.border)

            FilterSectionLabel(stringResource(Res.string.filter_section_creator))
            MarketplaceViewModel.CommunityFilter.entries.forEach { option ->
                val label =
                    when (option) {
                        MarketplaceViewModel.CommunityFilter.ALL -> {
                            stringResource(Res.string.marketplace_filter_community_all)
                        }

                        MarketplaceViewModel.CommunityFilter.COMMUNITY -> {
                            stringResource(Res.string.marketplace_filter_community_yes)
                        }
                    }
                FilterRadioItem(
                    label = label,
                    selected = communityFilter == option,
                    onClick = {
                        onCommunityFilterChanged(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun ActiveFilterChips(
    priceFilter: MarketplaceViewModel.PriceFilter,
    communityFilter: MarketplaceViewModel.CommunityFilter,
    onClearPriceFilter: () -> Unit,
    onClearCommunityFilter: () -> Unit,
) {
    val priceLabel =
        when (priceFilter) {
            MarketplaceViewModel.PriceFilter.ALL -> null
            MarketplaceViewModel.PriceFilter.FREE -> stringResource(Res.string.marketplace_filter_price_free)
            MarketplaceViewModel.PriceFilter.PAID -> stringResource(Res.string.marketplace_filter_price_paid)
        }
    val communityLabel =
        when (communityFilter) {
            MarketplaceViewModel.CommunityFilter.ALL -> {
                null
            }

            MarketplaceViewModel.CommunityFilter.COMMUNITY -> {
                stringResource(Res.string.marketplace_filter_community_yes)
            }
        }

    if (priceLabel == null && communityLabel == null) return

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PaneTablePadding)
                .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        priceLabel?.let {
            ActiveFilterChip(
                label = stringResource(Res.string.filter_active_price_chip, it),
                onClear = onClearPriceFilter,
            )
        }
        communityLabel?.let {
            ActiveFilterChip(
                label = stringResource(Res.string.filter_active_creator_chip, it),
                onClear = onClearCommunityFilter,
            )
        }
    }
}

@Composable
private fun ActiveFilterChip(
    label: String,
    onClear: () -> Unit,
) {
    Pill(
        text = label,
        contentColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        trailingIcon = Icons.Default.Close,
        trailingIconContentDescription = stringResource(Res.string.filter_clear),
        onClick = onClear,
        contentPadding = PaddingValues(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun FilterSectionLabel(text: String) {
    SectionLabel(
        text = text.uppercase(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun FilterRadioItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        text = {
            CerealText(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
    )
}

@Composable
internal fun MarketplaceSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PaneTablePadding, vertical = 12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = PaneTableMetaIconColor,
                modifier = Modifier.size(14.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle =
                        LocalTextStyle.current.merge(
                            CerealTypography.controlLabel.copy(color = Color.White),
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
                if (value.isEmpty()) {
                    CerealText(
                        text = stringResource(Res.string.marketplace_search_placeholder),
                        style = CerealTypography.controlLabel,
                        color = PaneTableMetaIconColor,
                    )
                }
            }
        }
    }
}
