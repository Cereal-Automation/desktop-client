package com.cereal.client.presentation.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.SectionLabel
import com.cereal.client.presentation.view.button.CerealToolbarButton
import com.cereal.client.presentation.view.button.CerealToolbarButtonVariant
import com.cereal.client.presentation.view.chip.Pill
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.contact_support
import com.cereal_automation.cereal_client.generated.resources.edit
import com.cereal_automation.cereal_client.generated.resources.ic_cancel
import com.cereal_automation.cereal_client.generated.resources.ic_play
import com.cereal_automation.cereal_client.generated.resources.more_options
import com.cereal_automation.cereal_client.generated.resources.restart_errored
import com.cereal_automation.cereal_client.generated.resources.start_all
import com.cereal_automation.cereal_client.generated.resources.start_all_tasks
import com.cereal_automation.cereal_client.generated.resources.stop_all
import com.cereal_automation.cereal_client.generated.resources.stop_all_tasks
import com.cereal_automation.cereal_client.generated.resources.stop_tasks_to_edit
import com.cereal_automation.cereal_client.generated.resources.view_config
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TasksToolbar(
    title: String,
    subtitle: String?,
    onViewConfig: (() -> Unit)? = null,
    onContactSupport: (() -> Unit)? = null,
    menuOptions: List<MenuOption> = emptyList(),
    onMenuOptionSelected: (MenuOption) -> Unit = {},
    onEdit: (() -> Unit)? = null,
    editEnabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = PanePadding),
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
                    color = CerealTheme.colorScheme.contentTertiary,
                )
            }
            Spacer(Modifier.weight(1f))
            if (onContactSupport != null) {
                CerealToolbarButton(
                    variant = CerealToolbarButtonVariant.Ghost,
                    onClick = onContactSupport,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    CerealText(text = stringResource(Res.string.contact_support))
                }
            }
            if (onEdit != null) {
                EditButton(onEdit, editEnabled)
            }
            if (onViewConfig != null) {
                CerealToolbarButton(
                    variant = CerealToolbarButtonVariant.Ghost,
                    onClick = onViewConfig,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    CerealText(text = stringResource(Res.string.view_config))
                }
            }
            if (menuOptions.isNotEmpty()) {
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    CerealIconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = stringResource(Res.string.more_options),
                            modifier = Modifier.size(18.dp),
                            tint = CerealTheme.colorScheme.contentSecondary,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        menuOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { CerealText(stringResource(option.stringResource)) },
                                onClick = {
                                    menuExpanded = false
                                    onMenuOptionSelected(option)
                                },
                            )
                        }
                    }
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

/** Edit is disabled while a task runs; hovering it then explains why. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditButton(
    onEdit: () -> Unit,
    enabled: Boolean,
) {
    val button = @Composable {
        CerealToolbarButton(
            variant = CerealToolbarButtonVariant.Ghost,
            onClick = onEdit,
            enabled = enabled,
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            CerealText(text = stringResource(Res.string.edit))
        }
    }
    if (enabled) {
        button()
        return
    }
    TooltipArea(
        tooltip = {
            Surface(modifier = Modifier.shadow(4.dp), shape = MaterialTheme.shapes.extraSmall) {
                CerealText(
                    text = stringResource(Res.string.stop_tasks_to_edit),
                    modifier = Modifier.padding(CerealTheme.spacing.sm),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        delayMillis = 0,
    ) {
        button()
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
            StatusChip(TaskFilter.IDLE, state.counts.idle, CerealTheme.colorScheme.contentSecondary, state, callbacks)
            StatusChip(TaskFilter.RUNNING, state.counts.running, CerealTheme.colorScheme.warning, state, callbacks)
            StatusChip(TaskFilter.SUCCESS, state.counts.finished, CerealTheme.colorScheme.success, state, callbacks)
            StatusChip(
                filter = TaskFilter.ERROR,
                count = state.counts.errored,
                // Red means "needs you": only when something actually errored.
                color =
                    if (state.counts.errored > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        CerealTheme.colorScheme.contentTertiary
                    },
                state = state,
                callbacks = callbacks,
            )

            Spacer(Modifier.weight(1f))

            if (state.showRestartErrored) {
                CerealToolbarButton(
                    variant = CerealToolbarButtonVariant.Ghost,
                    onClick = callbacks.onRestartErrored,
                    enabled = state.restartErroredEnabled,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    CerealText(text = stringResource(Res.string.restart_errored))
                }
            }
            if (state.showStopAll) {
                CerealToolbarButton(
                    variant = CerealToolbarButtonVariant.Flat,
                    onClick = callbacks.onStopAll,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_cancel),
                        contentDescription = stringResource(Res.string.stop_all_tasks),
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    CerealText(text = stringResource(Res.string.stop_all))
                }
            } else {
                CerealToolbarButton(
                    variant = CerealToolbarButtonVariant.Green,
                    onClick = callbacks.onStartAll,
                    enabled = state.startAllEnabled,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_play),
                        contentDescription = stringResource(Res.string.start_all_tasks),
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    CerealText(text = stringResource(Res.string.start_all))
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

/** "Idle 3"-style chip; clicking it filters the task list to [filter], clicking it again clears the filter. */
@Composable
private fun StatusChip(
    filter: TaskFilter,
    count: Int,
    color: Color,
    state: TaskQuickActionsState,
    callbacks: TaskQuickActionsCallbacks,
) {
    val active = state.filter == filter
    Pill(
        text = "${stringResource(filter.labelRes())} $count",
        contentColor = color,
        backgroundColor = if (active) color.copy(alpha = 0.15f) else Color.Transparent,
        borderColor = if (active) color else CerealTheme.colorScheme.border,
        leadingDot = true,
        onClick = { callbacks.onStatusChipClick(filter) },
    )
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
