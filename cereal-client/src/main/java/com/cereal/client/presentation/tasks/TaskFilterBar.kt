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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.StatusDot
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.filter_all_groups
import org.jetbrains.compose.resources.stringResource

/** The status chips across every task setup and, with more than one group, the group filter. */
@Composable
internal fun TaskFilterBar(
    chips: List<TaskFilterChip>,
    groupFilter: GroupFilterState?,
    onStatusFilterSelected: (TaskFilter) -> Unit,
    onGroupFilterSelected: (String?) -> Unit,
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
                            color = if (chip.active) CerealTheme.colorScheme.contentSecondary else CerealTheme.colorScheme.contentSubtle,
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
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CerealTheme.colorScheme.border))
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
