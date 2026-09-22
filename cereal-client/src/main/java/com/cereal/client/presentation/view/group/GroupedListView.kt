package com.cereal.client.presentation.view.group

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.view.ActivityIndicator
import com.cereal.client.presentation.view.CerealBadge
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ListItem
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.warning
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T> GroupedListView(
    viewModel: GroupedListViewModel<T>,
    modifier: Modifier = Modifier,
    onLongClick: ((GroupListItem<*>?) -> Unit)? = null,
) {
    val listState = rememberLazyListState()

    Box(modifier) {
        LazyColumn(
            modifier = Modifier.padding(vertical = 16.dp),
            state = listState,
        ) {
            items(viewModel.state.value, key = { it.content.id as Any }) {
                ListItem(
                    content = {
                        ScriptItemContent(it.content, it.selected)
                    },
                    isSelected = it.selected,
                    menuOptions = viewModel.menuOptions,
                    onMenuOptionClick = { menuItem ->
                        viewModel.onMenuOptionClick(it, menuItem)
                    },
                    onLongClick = {
                        onLongClick?.invoke(it)
                    },
                ) {
                    viewModel.onItemClick(it)
                }
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = listState),
        )
    }
}

@Composable
fun HeaderGroupedListView(
    headerTitle: String,
    headerSubtitle: String? = null,
    subMenu: @Composable RowScope.() -> Unit = { },
) {
    Column(modifier = Modifier.padding(horizontal = 25.dp).padding(top = 25.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                CerealText(
                    text = headerTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                headerSubtitle?.let {
                    CerealText(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            subMenu()
        }

        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScriptItemContent(
    item: GroupListItemContent<*>,
    isSelected: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        item.warningMessage?.let {
            TooltipArea(
                tooltip = {
                    // composable tooltip content
                    Surface(
                        modifier = Modifier.shadow(4.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        CerealText(
                            text = item.warningMessage,
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
                Row {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Filled.Warning,
                        contentDescription = stringResource(Res.string.warning),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                style =
                    if (isSelected) {
                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
            )

            if (!item.subTitle.isNullOrEmpty()) {
                CerealText(
                    text = item.subTitle,
                    fontSize = 12.sp,
                    modifier =
                        Modifier
                            .padding(vertical = 4.dp),
                )
            }
        }

        if (item.isRunning) {
            Spacer(modifier = Modifier.width(8.dp))
            ActivityIndicator(size = 20.dp)
        }

        if (!item.badge.isNullOrEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            CerealBadge {
                CerealText(
                    text = item.badge,
                )
            }
        }
    }
}
