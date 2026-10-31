package com.cereal.client.presentation.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.domain.model.task.TaskSetupSort
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.HorizontalDivider
import com.cereal.client.presentation.view.StatusDot
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.filter_all_groups
import com.cereal_automation.cereal_client.generated.resources.sort_by
import org.jetbrains.compose.resources.stringResource

/** The status chips across every task setup, with more than one group the group filter, and the sort dropdown. */
@Composable
internal fun TaskFilterBar(
    chips: List<TaskFilterChip>,
    groupFilter: GroupFilterState?,
    sort: TaskSetupSort,
    onStatusFilterSelected: (TaskFilter) -> Unit,
    onGroupFilterSelected: (String?) -> Unit,
    onSortSelected: (TaskSetupSort) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PanePadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Segments {
                chips.forEach { chip ->
                    Segment(selected = chip.active, onClick = { onStatusFilterSelected(chip.filter) }) {
                        chip.filter.dotColor()?.let { StatusDot(color = it) }
                        SegmentLabel(stringResource(chip.filter.labelRes()), chip.active)
                        CerealText(
                            text = chip.count.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = if (chip.active) CerealTheme.colorScheme.contentSecondary else CerealTheme.colorScheme.contentTertiary,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            groupFilter?.let { filter ->
                Segments {
                    Segment(selected = filter.selectedGroupId == null, onClick = { onGroupFilterSelected(null) }) {
                        SegmentLabel(stringResource(Res.string.filter_all_groups), filter.selectedGroupId == null)
                    }
                    filter.groups.forEach { group ->
                        Segment(selected = filter.selectedGroupId == group.id, onClick = { onGroupFilterSelected(group.id) }) {
                            SegmentLabel(group.name, filter.selectedGroupId == group.id)
                        }
                    }
                }
            }
            SortMenu(sort, onSortSelected)
        }
        HorizontalDivider()
    }
}

/** "Sort: Newest first ▾", opening a menu of every sort. */
@Composable
private fun SortMenu(
    sort: TaskSetupSort,
    onSortSelected: (TaskSetupSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Segments {
            Segment(selected = false, onClick = { expanded = true }) {
                SegmentLabel(stringResource(Res.string.sort_by, stringResource(sort.labelRes())), selected = false)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = CerealTheme.colorScheme.contentTertiary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TaskSetupSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { CerealText(stringResource(option.labelRes())) },
                    onClick = {
                        expanded = false
                        onSortSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskFilter.dotColor(): Color? =
    when (this) {
        TaskFilter.ALL -> null
        TaskFilter.RUNNING -> CerealTheme.colorScheme.warning
        TaskFilter.ERROR -> MaterialTheme.colorScheme.error
        TaskFilter.SUCCESS -> CerealTheme.colorScheme.success
        TaskFilter.IDLE -> CerealTheme.colorScheme.contentSecondary
    }

/** A segmented control's container (the same look as the log level filter). */
@Composable
private fun Segments(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(5.dp))
                .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun Segment(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) CerealTheme.colorScheme.border else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun SegmentLabel(
    text: String,
    selected: Boolean,
) {
    CerealText(
        text = text,
        style = CerealTypography.controlLabel.copy(fontWeight = FontWeight.Medium),
        color = if (selected) Color.White else CerealTheme.colorScheme.contentTertiary,
    )
}
