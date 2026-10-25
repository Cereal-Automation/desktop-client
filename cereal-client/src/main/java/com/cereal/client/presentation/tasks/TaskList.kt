package com.cereal.client.presentation.tasks

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.util.initialsOf
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealOutlinedIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.SectionLabel
import com.cereal.client.presentation.view.StatusDot
import com.cereal.client.presentation.view.button.CerealToolbarButton
import com.cereal.client.presentation.view.button.CerealToolbarButtonVariant
import com.cereal.client.presentation.view.group.MoreOptionsMenu
import com.cereal_automation.cereal_client.generated.resources.Res
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
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CerealTheme.colorScheme.border),
        )
    }
}

/** "DEFAULT · 2" above a group's task setups, with a "···" menu (Rename, Delete). */
@Composable
internal fun TaskGroupLabel(
    section: TaskGroupSection,
    onMenuOptionSelected: (MenuOption) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = PanePadding, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(
            text = "${section.name.uppercase()} · ${section.setups.size}",
            contentPadding = PaddingValues(),
        )
        Spacer(Modifier.weight(1f))
        MoreOptionsMenu(GroupMenuOptions, onMenuOptionSelected, tint = CerealTheme.colorScheme.contentSubtle)
    }
}

private val GroupMenuOptions = listOf(MenuOption.RENAME, MenuOption.DELETE)

/** A task setup's header row: avatar, name, counts, stepper, Start all / Stop all and its "···" menu. */
@Composable
internal fun TaskSetupRow(
    section: TaskSetupSection,
    onStartAll: () -> Unit,
    onStopAll: () -> Unit,
    onDecreaseTasks: () -> Unit,
    onIncreaseTasks: () -> Unit,
    onMenuOptionSelected: (MenuOption) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SetupAvatar(section.name)
        Column(modifier = Modifier.weight(1f)) {
            CerealText(text = section.name, style = CerealTypography.rowTitle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (!section.subtitle.isNullOrEmpty()) {
                CerealText(
                    text = section.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = CerealTheme.colorScheme.contentSubtle,
                    maxLines = 1,
                )
            }
        }
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
        MoreOptionsMenu(
            options = section.menuOptions,
            onOptionSelected = onMenuOptionSelected,
            tint = CerealTheme.colorScheme.contentTertiary,
            label = { option ->
                if (option == MenuOption.RESTART_FAILED) {
                    stringResource(option.stringResource, section.failedCount)
                } else {
                    stringResource(option.stringResource)
                }
            },
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
internal fun TaskGroupTitle(title: String) {
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

@Composable
private fun SetupAvatar(name: String) {
    val initials = remember(name) { initialsOf(name) }
    val color = remember(name) { avatarColorFor(name) }
    Box(
        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        CerealText(
            text = initials,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = TextUnit(-0.02f, TextUnitType.Em)),
            color = Color.White,
        )
    }
}

private val AvatarPalette =
    listOf(
        Color(0xFFE54848),
        Color(0xFFF5B73A),
        Color(0xFF2ECC71),
        Color(0xFF4F8CFF),
        Color(0xFF5865F2),
        Color(0xFFB57AFF),
        Color(0xFF333333),
    )

private const val HASH_PRIME = 31
private const val POSITIVE_INT_MASK = 0x7FFFFFFF

private fun avatarColorFor(name: String): Color {
    if (name.isEmpty()) return AvatarPalette[0]
    val hash = name.fold(0) { acc, c -> (acc * HASH_PRIME + c.code) and POSITIVE_INT_MASK }
    return AvatarPalette[hash % AvatarPalette.size]
}
