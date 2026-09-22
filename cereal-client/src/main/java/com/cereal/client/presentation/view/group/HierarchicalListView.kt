package com.cereal.client.presentation.view.group

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.util.initialsOf
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.button.CerealSmallIconButton
import com.cereal.client.presentation.view.chip.Pill
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.add_script
import com.cereal_automation.cereal_client.generated.resources.collapse
import com.cereal_automation.cereal_client.generated.resources.expand
import com.cereal_automation.cereal_client.generated.resources.more_options
import com.cereal_automation.cereal_client.generated.resources.new_group
import com.cereal_automation.cereal_client.generated.resources.scripts_column_summary
import com.cereal_automation.cereal_client.generated.resources.scripts_column_title
import com.cereal_automation.cereal_client.generated.resources.warning
import org.jetbrains.compose.resources.stringResource

@Composable
fun <P, C> ScriptsColumn(
    viewModel: HierarchicalListViewModel<P, C>,
    onCreateGroup: () -> Unit,
    onChildLongClick: ((HierarchicalListItem.Child<P, C>?) -> Unit)? = null,
) {
    val items = viewModel.state.value
    val parents =
        remember(items) {
            items.filterIsInstance<HierarchicalListItem.Parent<P, C>>()
        }
    val totalScripts = remember(parents) { parents.sumOf { it.children.size } }
    val runningScripts =
        remember(parents) {
            parents.sumOf { p -> p.children.count { it.content.isRunning } }
        }

    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .background(CerealTheme.colorScheme.cardDark),
    ) {
        ScriptsColumnHeader(
            totalScripts = totalScripts,
            runningScripts = runningScripts,
            onCreateGroup = onCreateGroup,
        )
        HierarchicalListView(
            viewModel = viewModel,
            onChildLongClick = onChildLongClick,
        )
    }
}

@Composable
private fun ScriptsColumnHeader(
    totalScripts: Int,
    runningScripts: Int,
    onCreateGroup: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CerealText(
                    text = stringResource(Res.string.scripts_column_title),
                    style = CerealTypography.paneTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CerealText(
                    text =
                        stringResource(
                            Res.string.scripts_column_summary,
                            totalScripts,
                            runningScripts,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = CerealTheme.colorScheme.contentTertiary,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }

            CerealSmallIconButton(onClick = onCreateGroup) {
                Icon(
                    imageVector = Icons.Filled.CreateNewFolder,
                    contentDescription = stringResource(Res.string.new_group),
                    modifier = Modifier.size(18.dp),
                    tint = CerealTheme.colorScheme.contentSecondary,
                )
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
fun <P, C> HierarchicalListView(
    viewModel: HierarchicalListViewModel<P, C>,
    onChildLongClick: ((HierarchicalListItem.Child<P, C>?) -> Unit)? = null,
) {
    val listState = rememberLazyListState()

    val groupedItems by remember(viewModel) {
        derivedStateOf { buildGroupedItems(viewModel.state.value) }
    }

    Box(modifier = Modifier.fillMaxHeight()) {
        LazyColumn(
            modifier = Modifier.padding(vertical = 6.dp),
            state = listState,
        ) {
            items(groupedItems, key = { it.parent.content.id as Any }) { groupedItem ->
                GroupSection(
                    parent = groupedItem.parent,
                    children = groupedItem.children,
                    viewModel = viewModel,
                    onChildLongClick = onChildLongClick,
                )
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = listState),
        )
    }
}

private data class GroupedItem<P, C>(
    val parent: HierarchicalListItem.Parent<P, C>,
    val children: List<HierarchicalListItem.Child<P, C>>,
)

private fun <P, C> buildGroupedItems(
    items: List<HierarchicalListItem<P, C>>,
): List<GroupedItem<P, C>> {
    val result = mutableListOf<GroupedItem<P, C>>()
    var currentParent: HierarchicalListItem.Parent<P, C>? = null
    var currentChildren = mutableListOf<HierarchicalListItem.Child<P, C>>()

    for (item in items) {
        when (item) {
            is HierarchicalListItem.Parent -> {
                currentParent?.let {
                    result.add(GroupedItem(it, currentChildren.toList()))
                }
                currentParent = item
                currentChildren = mutableListOf()
            }

            is HierarchicalListItem.Child -> {
                currentChildren.add(item)
            }
        }
    }

    currentParent?.let {
        result.add(GroupedItem(it, currentChildren.toList()))
    }

    return result
}

@Composable
private fun <P, C> GroupSection(
    parent: HierarchicalListItem.Parent<P, C>,
    children: List<HierarchicalListItem.Child<P, C>>,
    viewModel: HierarchicalListViewModel<P, C>,
    onChildLongClick: ((HierarchicalListItem.Child<P, C>?) -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GroupHeaderRow(
            parent = parent,
            menuOptions = viewModel.parentMenuOptions,
            onMenuOptionClick = { option -> viewModel.onParentMenuOptionClick(parent, option) },
            onToggle = { viewModel.onParentClick(parent) },
        )

        if (parent.expanded) {
            children.forEach { child ->
                ScriptRow(
                    child = child,
                    menuOptions = viewModel.childMenuOptions,
                    onMenuOptionClick = { option -> viewModel.onChildMenuOptionClick(child, option) },
                    onClick = { viewModel.onChildClick(child) },
                    onLongClick = { onChildLongClick?.invoke(child) },
                )
            }
            AddScriptRow(onClick = { viewModel.onAddChildClick(parent) })
        }
    }
}

@Composable
private fun <P, C> GroupHeaderRow(
    parent: HierarchicalListItem.Parent<P, C>,
    menuOptions: List<com.cereal.client.presentation.tasks.MenuOption>,
    onMenuOptionClick: (com.cereal.client.presentation.tasks.MenuOption) -> Unit,
    onToggle: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onToggle,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (parent.expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription =
                if (parent.expanded) {
                    stringResource(Res.string.collapse)
                } else {
                    stringResource(Res.string.expand)
                },
            modifier = Modifier.size(14.dp),
            tint = CerealTheme.colorScheme.contentSubtle,
        )

        parent.content.warningMessage?.let { message ->
            GroupWarningIcon(message)
        }

        CerealText(
            text = parent.content.title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = CerealTheme.colorScheme.contentSecondary,
        )
        CerealText(
            text = "· ${parent.children.size}",
            style = MaterialTheme.typography.labelMedium,
            color = CerealTheme.colorScheme.contentSubtle,
        )

        Spacer(Modifier.weight(1f))

        if (menuOptions.isNotEmpty()) {
            Box {
                CerealSmallIconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreHoriz,
                        contentDescription = stringResource(Res.string.more_options),
                        modifier = Modifier.size(18.dp),
                        tint = CerealTheme.colorScheme.contentSubtle,
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
                                onMenuOptionClick(option)
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun <P, C> ScriptRow(
    child: HierarchicalListItem.Child<P, C>,
    menuOptions: List<com.cereal.client.presentation.tasks.MenuOption>,
    onMenuOptionClick: (com.cereal.client.presentation.tasks.MenuOption) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val accentTint = accent.copy(alpha = 0.10f)
    val selected = child.selected
    val interactionSource = remember { MutableInteractionSource() }

    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(24.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                        .background(accent),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) accentTint else Color.Transparent)
                    .onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Secondary),
                        onClick = { if (menuOptions.isNotEmpty()) menuExpanded = true },
                        interactionSource = interactionSource,
                    ).combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        indication = ripple(color = accent),
                        interactionSource = interactionSource,
                    ).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScriptIconTile(title = child.content.title)

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    child.content.warningMessage?.let { message ->
                        GroupWarningIcon(message)
                        Spacer(Modifier.width(6.dp))
                    }
                    CerealText(
                        text = child.content.title,
                        style = CerealTypography.rowTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                if (!child.content.subTitle.isNullOrEmpty()) {
                    CerealText(
                        text = child.content.subTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = CerealTheme.colorScheme.contentSubtle,
                        maxLines = 1,
                    )
                }
            }

            if (child.content.isRunning) {
                RunningDot()
            }

            if (!child.content.badge.isNullOrEmpty()) {
                Pill(
                    text = child.content.badge,
                    contentColor = Color.White,
                    backgroundColor = accent,
                    textStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    contentPadding = CountPillPadding,
                )
            }

            if (menuOptions.isNotEmpty()) {
                Box {
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        menuOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { CerealText(stringResource(option.stringResource)) },
                                onClick = {
                                    onMenuOptionClick(option)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptIconTile(title: String) {
    val initial = remember(title) { initialsOf(title) }
    val color = remember(title) { iconColorFor(title) }
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color),
        contentAlignment = Alignment.Center,
    ) {
        CerealText(
            text = initial,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = TextUnit(-0.02f, TextUnitType.Em),
                ),
            color = Color.White,
        )
    }
}

private val IconPalette =
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

private fun iconColorFor(title: String): Color {
    if (title.isEmpty()) return IconPalette[0]
    val hash = title.fold(0) { acc, c -> (acc * HASH_PRIME + c.code) and POSITIVE_INT_MASK }
    return IconPalette[hash % IconPalette.size]
}

@Composable
private fun RunningDot() {
    val success = CerealTheme.colorScheme.success
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(success.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(success),
        )
    }
}

private val CountPillPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)

@Composable
private fun AddScriptRow(onClick: () -> Unit) {
    val border = CerealTheme.colorScheme.border
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp)
                .clip(RoundedCornerShape(6.dp))
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val dash = 4.dp.toPx()
                    val cornerPx = 6.dp.toPx()
                    drawRoundRect(
                        color = border,
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style =
                            Stroke(
                                width = strokeWidth,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), 0f),
                            ),
                    )
                }.clickable(
                    onClick = onClick,
                    indication = ripple(color = MaterialTheme.colorScheme.primary),
                    interactionSource = remember { MutableInteractionSource() },
                ).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = CerealTheme.colorScheme.contentSubtle,
            )
        }
        CerealText(
            text = stringResource(Res.string.add_script),
            style = CerealTypography.rowTitle,
            color = CerealTheme.colorScheme.contentSubtle,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupWarningIcon(message: String) {
    TooltipArea(
        tooltip = {
            Surface(
                modifier = Modifier.shadow(4.dp),
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                CerealText(
                    text = message,
                    modifier = Modifier.padding(10.dp),
                )
            }
        },
        delayMillis = 0,
        tooltipPlacement =
            TooltipPlacement.CursorPoint(
                alignment = Alignment.BottomEnd,
                offset = DpOffset((-24).dp, 0.dp),
            ),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = stringResource(Res.string.warning),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp),
        )
    }
}
