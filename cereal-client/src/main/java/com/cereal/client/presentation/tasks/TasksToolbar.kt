package com.cereal.client.presentation.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.SectionLabel
import com.cereal.client.presentation.view.StatusDot
import com.cereal.client.presentation.view.button.CerealToolbarButton
import com.cereal.client.presentation.view.button.CerealToolbarButtonVariant
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.contact_support
import com.cereal_automation.cereal_client.generated.resources.errored
import com.cereal_automation.cereal_client.generated.resources.filter_label
import com.cereal_automation.cereal_client.generated.resources.ic_cancel
import com.cereal_automation.cereal_client.generated.resources.ic_play
import com.cereal_automation.cereal_client.generated.resources.idle
import com.cereal_automation.cereal_client.generated.resources.restart_errored
import com.cereal_automation.cereal_client.generated.resources.running
import com.cereal_automation.cereal_client.generated.resources.start_all
import com.cereal_automation.cereal_client.generated.resources.start_all_tasks
import com.cereal_automation.cereal_client.generated.resources.stop_all
import com.cereal_automation.cereal_client.generated.resources.stop_all_tasks
import com.cereal_automation.cereal_client.generated.resources.success
import com.cereal_automation.cereal_client.generated.resources.view_config
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TasksToolbar(
    title: String,
    subtitle: String?,
    onViewConfig: (() -> Unit)? = null,
    onContactSupport: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp)
                    .padding(horizontal = PanePadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CerealText(
                text = title,
                style = CerealTypography.paneTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrEmpty()) {
                CerealText(
                    text = "·  $subtitle",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = CerealTheme.colorScheme.contentTertiary,
                )
            }
            if (onViewConfig != null || onContactSupport != null) {
                Spacer(Modifier.weight(1f))
            }
            if (onContactSupport != null) {
                CerealToolbarButton(
                    variant = CerealToolbarButtonVariant.Ghost,
                    onClick = onContactSupport,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    CerealText(text = stringResource(Res.string.contact_support))
                }
            }
            if (onViewConfig != null) {
                CerealToolbarButton(
                    variant = CerealToolbarButtonVariant.Ghost,
                    onClick = onViewConfig,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    CerealText(text = stringResource(Res.string.view_config))
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CerealTheme.colorScheme.border),
        )
    }
}

@Composable
internal fun TaskStatsStrip(counts: TaskCounts) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PanePadding, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile(
            label = stringResource(Res.string.idle),
            value = counts.idle,
            swatch = CerealTheme.colorScheme.contentSecondary,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(Res.string.running),
            value = counts.running,
            swatch = CerealTheme.colorScheme.warning,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(Res.string.success),
            value = counts.finished,
            swatch = CerealTheme.colorScheme.success,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(Res.string.errored),
            value = counts.errored,
            swatch = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: Int,
    swatch: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(CerealTheme.colorScheme.cardDark)
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusDot(color = swatch)
            CerealText(
                text = label.uppercase(),
                style = CerealTypography.sectionLabel,
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
        CerealText(
            text = value.toString(),
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    letterSpacing = TextUnit(-0.025f, TextUnitType.Em),
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun TasksQuickActions(
    state: TaskQuickActionsState,
    callbacks: TaskQuickActionsCallbacks,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PanePadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CerealToolbarButton(
                variant = CerealToolbarButtonVariant.Green,
                onClick = callbacks.onStartAll,
                enabled = state.startAllEnabled,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_play),
                    contentDescription = stringResource(Res.string.start_all_tasks),
                    modifier = Modifier.size(14.dp),
                )
                CerealText(text = stringResource(Res.string.start_all))
            }
            CerealToolbarButton(
                variant = CerealToolbarButtonVariant.Flat,
                onClick = callbacks.onStopAll,
                enabled = state.stopAllEnabled,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_cancel),
                    contentDescription = stringResource(Res.string.stop_all_tasks),
                    modifier = Modifier.size(14.dp),
                )
                CerealText(text = stringResource(Res.string.stop_all))
            }
            CerealToolbarButton(
                variant = CerealToolbarButtonVariant.Ghost,
                onClick = callbacks.onRestartErrored,
                enabled = state.restartErroredEnabled,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                CerealText(text = stringResource(Res.string.restart_errored))
            }

            Spacer(Modifier.weight(1f))

            TaskFilterControl(
                filter = state.filter,
                onFilterChange = callbacks.onFilterChange,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CerealTheme.colorScheme.border),
        )
    }
}

@Composable
internal fun TaskGroupTitle(title: String) {
    SectionLabel(
        text = title,
        contentPadding =
            PaddingValues(
                start = PanePadding,
                end = PanePadding,
                top = 18.dp,
                bottom = 8.dp,
            ),
    )
}

@Composable
private fun TaskFilterControl(
    filter: TaskFilter,
    onFilterChange: (TaskFilter) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CerealText(
            text = stringResource(Res.string.filter_label),
            color = CerealTheme.colorScheme.contentTertiary,
            style = CerealTypography.controlLabel,
        )
        Box {
            CerealToolbarButton(
                variant = CerealToolbarButtonVariant.Flat,
                onClick = { menuExpanded = true },
            ) {
                CerealText(text = stringResource(filter.labelRes()))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                TaskFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { CerealText(stringResource(option.labelRes())) },
                        onClick = {
                            onFilterChange(option)
                            menuExpanded = false
                        },
                    )
                }
            }
        }
    }
}
