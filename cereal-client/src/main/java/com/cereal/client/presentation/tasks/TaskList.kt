package com.cereal.client.presentation.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.util.initialsOf
import com.cereal.client.presentation.view.ActivityIndicator
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealOutlinedIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.HorizontalDivider
import com.cereal.client.presentation.view.SectionLabel
import com.cereal.client.presentation.view.StatusDot
import com.cereal.client.presentation.view.button.CerealToolbarButton
import com.cereal.client.presentation.view.button.CerealToolbarButtonVariant
import com.cereal.client.presentation.view.group.MoreOptionsMenu
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.collapse
import com.cereal_automation.cereal_client.generated.resources.expand
import com.cereal_automation.cereal_client.generated.resources.fewer_concurrent_tasks
import com.cereal_automation.cereal_client.generated.resources.ic_play
import com.cereal_automation.cereal_client.generated.resources.ic_stop
import com.cereal_automation.cereal_client.generated.resources.more_concurrent_tasks
import com.cereal_automation.cereal_client.generated.resources.new_group
import com.cereal_automation.cereal_client.generated.resources.new_task_setup
import com.cereal_automation.cereal_client.generated.resources.setup_failed_count
import com.cereal_automation.cereal_client.generated.resources.setup_running_count
import com.cereal_automation.cereal_client.generated.resources.start_all_tasks
import com.cereal_automation.cereal_client.generated.resources.stop_all_tasks
import com.cereal_automation.cereal_client.generated.resources.stop_tasks_to_edit
import com.cereal_automation.cereal_client.generated.resources.task_setups_count
import com.cereal_automation.cereal_client.generated.resources.tasks
import com.cereal_automation.cereal_client.generated.resources.tasks_count
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** "Tasks · N setups · M tasks" with New group and New task setup; counts are left out while the list loads. */
@Composable
internal fun TasksHeader(
    counts: Pair<Int, Int>?,
    onNewGroup: () -> Unit,
    onNewTaskSetup: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = PanePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CerealText(
                text = stringResource(Res.string.tasks),
                style = CerealTypography.paneTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            counts?.let { (setups, tasks) ->
                CerealText(
                    text =
                        "·  ${pluralStringResource(Res.plurals.task_setups_count, setups, setups)} · " +
                            pluralStringResource(Res.plurals.tasks_count, tasks, tasks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CerealTheme.colorScheme.contentTertiary,
                )
            }
            Spacer(Modifier.weight(1f))
            CerealToolbarButton(variant = CerealToolbarButtonVariant.Ghost, onClick = onNewGroup) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                CerealText(text = stringResource(Res.string.new_group))
            }
            CerealToolbarButton(variant = CerealToolbarButtonVariant.Flat, onClick = onNewTaskSetup) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                CerealText(text = stringResource(Res.string.new_task_setup))
            }
        }
        HorizontalDivider()
    }
}

/** "DEFAULT · 2" above a group's task setups, with a "···" menu (Rename, Delete). */
@Composable
internal fun TaskGroupLabel(
    section: TaskGroupSection,
    onMenuOptionSelected: (GroupMenuOption) -> Unit,
) {
    Row(
        // The 28dp "···" sits inside the row, so its hover never spills under the setup card below.
        modifier = Modifier.fillMaxWidth().padding(start = PanePadding, end = 16.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(
            text = "${section.name.uppercase()} · ${section.setups.size}",
            contentPadding = PaddingValues(),
        )
        Spacer(Modifier.weight(1f))
        MoreOptionsMenu(tint = CerealTheme.colorScheme.contentSubtle, buttonModifier = Modifier.size(28.dp)) { close ->
            GroupMenuOption.entries.forEach { option ->
                MenuItem(stringResource(option.label)) {
                    close()
                    onMenuOptionSelected(option)
                }
            }
        }
    }
}

/** A task setup's header row: chevron, avatar, name, counts, stepper, Start all / Stop all and its "···" menu. */
@Composable
internal fun TaskSetupRow(
    section: TaskSetupSection,
    onToggleExpanded: () -> Unit,
    onStartAll: () -> Unit,
    onStopAll: () -> Unit,
    onDecreaseTasks: () -> Unit,
    onIncreaseTasks: () -> Unit,
    onMenuOptionSelected: (SetupMenuOption) -> Unit,
) {
    // The whole row toggles collapse; its buttons (stepper, Start/Stop all, "···") keep their own clicks.
    // The highlight follows the card's rounded top, and its rounded bottom while the setup is collapsed.
    val bottomRadius = if (section.expanded) 0.dp else CardRadius
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardRadius, CardRadius, bottomRadius, bottomRadius))
                .clickable(onClick = onToggleExpanded)
                .padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CerealIconButton(onClick = onToggleExpanded, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = if (section.expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(if (section.expanded) Res.string.collapse else Res.string.expand),
                tint = CerealTheme.colorScheme.contentTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        SetupAvatar(section.scriptName)
        SetupTitle(section, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (section.runningCount > 0) {
                CountWithDot(stringResource(Res.string.setup_running_count, section.runningCount), CerealTheme.colorScheme.warning)
            }
            if (section.failedCount > 0) {
                CountWithDot(stringResource(Res.string.setup_failed_count, section.failedCount), MaterialTheme.colorScheme.error)
            }
            CerealText(
                text = pluralStringResource(Res.plurals.tasks_count, section.taskCount, section.taskCount),
                style = MaterialTheme.typography.bodySmall,
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
        section.stepper?.let { ConcurrentTaskStepper(it, onDecreaseTasks, onIncreaseTasks) }
        CerealOutlinedIconButton(
            onClick = if (section.showStopAll) onStopAll else onStartAll,
            enabled = section.showStopAll || section.canStartAll,
        ) {
            Icon(
                painter = painterResource(if (section.showStopAll) Res.drawable.ic_stop else Res.drawable.ic_play),
                contentDescription = stringResource(if (section.showStopAll) Res.string.stop_all_tasks else Res.string.start_all_tasks),
                modifier = Modifier.size(16.dp),
            )
        }
        SetupMenu(section, onMenuOptionSelected)
    }
}

/**
 * A task setup with exactly one task, as one row: the setup's avatar and name, then that task's message, time,
 * status and action. Clicking it selects the task, like a task row.
 */
@Composable
internal fun SingleTaskSetupRow(
    section: TaskSetupSection,
    task: TaskUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onStartTask: () -> Unit,
    onStopTask: () -> Unit,
    onContinueClick: () -> Unit,
    onDecreaseTasks: () -> Unit,
    onIncreaseTasks: () -> Unit,
    onMenuOptionSelected: (SetupMenuOption) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardRadius))
                .background(if (isSelected) RowSelectedBg else Color.Transparent)
                .clickable(onClick = onClick),
    ) {
        if (isSelected) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .align(Alignment.CenterStart)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The chevron's slot, so avatars line up with expandable setups.
            Spacer(Modifier.size(28.dp))
            SetupAvatar(section.scriptName)
            TitleAndMessage(section, task, Modifier.weight(1f))
            TimeSince(task.statusSince)
            if (task.isRunning) ActivityIndicator(size = 18.dp)
            StatusChip(status = task.status, isRunning = task.isRunning, isError = task.isError, isSuccess = task.isSuccess)
            section.stepper?.let { ConcurrentTaskStepper(it, onDecreaseTasks, onIncreaseTasks) }
            TaskActionButton(task, onStartTask, onStopTask, onContinueClick)
            SetupMenu(section, onMenuOptionSelected)
        }
    }
}

/**
 * The setup's name, with its task's message right-aligned after it. The message gives way first: it shrinks, then
 * hides, so a narrow list keeps room for the name (the inspector still shows the message).
 */
@Composable
private fun TitleAndMessage(
    section: TaskSetupSection,
    task: TaskUiModel,
    modifier: Modifier,
) = Layout(
    contents =
        listOf(
            { SetupTitle(section, Modifier) },
            { TaskMessage(task, style = MaterialTheme.typography.bodySmall) },
        ),
    modifier = modifier,
) { (titles, messages), constraints ->
    val title = titles.single()
    val message = messages.single()
    val width = constraints.maxWidth
    val gap = 10.dp.roundToPx()
    val titleKeeps = minOf(title.maxIntrinsicWidth(constraints.maxHeight), TitleKeepsWidth.roundToPx())
    val messageRoom = minOf(width - titleKeeps - gap, MessageMaxWidth.roundToPx())
    val messagePlaceable = if (messageRoom >= MessageMinWidth.roundToPx()) message.measure(Constraints(maxWidth = messageRoom)) else null
    val messageSpace = messagePlaceable?.let { it.width + gap } ?: 0
    val titlePlaceable = title.measure(Constraints(maxWidth = (width - messageSpace).coerceAtLeast(0)))
    val height = maxOf(titlePlaceable.height, messagePlaceable?.height ?: 0)
    layout(width, height) {
        titlePlaceable.place(0, (height - titlePlaceable.height) / 2)
        messagePlaceable?.place(width - messagePlaceable.width, (height - messagePlaceable.height) / 2)
    }
}

/** The setup's name over its subtitle. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetupTitle(
    section: TaskSetupSection,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        // Ellipsized: on a narrow list the row's fixed-width items leave the title only what's left; hovering shows it whole.
        TooltipArea(tooltip = { TextTooltip(section.name) }) {
            CerealText(
                text = section.name,
                style = CerealTypography.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!section.subtitle.isNullOrEmpty()) {
            CerealText(
                text = section.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = CerealTheme.colorScheme.contentSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A setup's "···" menu; Edit is disabled, with the reason on hover, while its tasks run. */
@Composable
private fun SetupMenu(
    section: TaskSetupSection,
    onMenuOptionSelected: (SetupMenuOption) -> Unit,
) {
    MoreOptionsMenu(tint = CerealTheme.colorScheme.contentTertiary) { close ->
        section.menuOptions.forEach { option ->
            val label =
                if (option == SetupMenuOption.RESTART_FAILED) {
                    stringResource(option.label, section.failedCount)
                } else {
                    stringResource(option.label)
                }
            if (option == SetupMenuOption.EDIT && !section.editable) {
                DisabledMenuItem(label, reason = stringResource(Res.string.stop_tasks_to_edit))
            } else {
                MenuItem(label) {
                    close()
                    onMenuOptionSelected(option)
                }
            }
        }
    }
}

@Composable
private fun MenuItem(
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(text = { CerealText(label) }, onClick = onClick)
}

/** A menu entry that can't be picked right now; hovering it explains why. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DisabledMenuItem(
    label: String,
    reason: String,
) {
    TooltipArea(tooltip = { TextTooltip(reason) }, delayMillis = 0) {
        DropdownMenuItem(text = { CerealText(label) }, onClick = {}, enabled = false)
    }
}

@Composable
private fun TextTooltip(text: String) {
    Surface(modifier = Modifier.shadow(4.dp), shape = MaterialTheme.shapes.extraSmall) {
        CerealText(
            text = text,
            modifier = Modifier.padding(CerealTheme.spacing.sm),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CountWithDot(
    text: String,
    color: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color = color)
        CerealText(text = text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

/** `− count +`: the setup's number of concurrent tasks. */
@Composable
private fun ConcurrentTaskStepper(
    state: ConcurrentTaskStepperState,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CerealIconButton(onClick = onDecrease, enabled = state.canDecrease) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = stringResource(Res.string.fewer_concurrent_tasks),
                modifier = Modifier.size(18.dp),
            )
        }
        CerealText(text = state.count.toString(), style = MaterialTheme.typography.bodyMedium)
        CerealIconButton(onClick = onIncrease, enabled = state.canIncrease) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(Res.string.more_concurrent_tasks),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A child script's title above its task rows. */
@Composable
internal fun ChildScriptTitle(title: String) {
    SectionLabel(
        text = title,
        contentPadding = PaddingValues(start = PanePadding, end = PanePadding, top = 18.dp, bottom = 8.dp),
    )
}

/**
 * One horizontal slice of a task setup's card, which spans several list items: draws the card's fill and its left and
 * right border, plus the rounded top on the [first] slice and the rounded bottom on the [last].
 */
internal fun Modifier.setupCardSlice(
    first: Boolean,
    last: Boolean,
    fill: Color,
    border: Color,
): Modifier =
    padding(start = 16.dp, end = 16.dp, bottom = if (last) 8.dp else 0.dp).drawBehind {
        val radius = CardRadius.toPx()
        val stroke = 1.dp.toPx()
        // Slices that don't end the card push their rounded corners just past their bounds, so only the sides show.
        val top = if (first) stroke / 2 else -radius
        val bottom = if (last) size.height - stroke / 2 else size.height + radius
        clipRect {
            drawRoundRect(fill, Offset(0f, top), Size(size.width, bottom - top), CornerRadius(radius))
            drawRoundRect(border, Offset(stroke / 2, top), Size(size.width - stroke, bottom - top), CornerRadius(radius), style = Stroke(stroke))
        }
    }

private val CardRadius = 8.dp

/** How much of the setup's name [TitleAndMessage] keeps before giving its task's message any room. */
private val TitleKeepsWidth = 120.dp
private val MessageMaxWidth = 240.dp

/** Narrower than this, the message is hidden rather than ellipsized to a stub. */
private val MessageMinWidth = 48.dp

@Composable
private fun SetupAvatar(name: String) {
    val initials = remember(name) { initialsOf(name) }
    // Theme colours, minus error red so no setup looks failed.
    val palette = CerealTheme.colorScheme.run { listOf(info, success, warning, highlight, borderDark) }
    Box(
        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)).background(palette[avatarColorIndex(name, palette.size)]),
        contentAlignment = Alignment.Center,
    ) {
        CerealText(
            text = initials,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = TextUnit(-0.02f, TextUnitType.Em)),
            color = Color.White,
        )
    }
}

private const val HASH_PRIME = 31
private const val POSITIVE_INT_MASK = 0x7FFFFFFF

/** A stable index into a palette of [size] colours for [name]. */
private fun avatarColorIndex(
    name: String,
    size: Int,
): Int = name.fold(0) { acc, c -> (acc * HASH_PRIME + c.code) and POSITIVE_INT_MASK } % size
