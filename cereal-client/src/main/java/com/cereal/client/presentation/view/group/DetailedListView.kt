package com.cereal.client.presentation.view.group

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.delete
import com.cereal_automation.cereal_client.generated.resources.edit
import org.jetbrains.compose.resources.stringResource

data class DetailListViewHeader(
    val headers: List<String>,
)

data class DetailListViewItem(
    val id: Any,
    val attributes: List<String?>,
)

val ACTION_ITEM_WIDTH = 60.dp
val COLUMN_WIDTH_FONT_SIZE_MULTIPLIER = 12.sp
val ROW_PADDING_HORIZONTAL = 25.dp
val CELL_PADDING_HORIZONTAL = 0.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailedListView(
    header: DetailListViewHeader,
    items: List<DetailListViewItem>,
    modifier: Modifier = Modifier,
    isEditingEnabled: Boolean = true,
    isDeletingEnabled: Boolean = true,
    onEditListItem: (detailListViewItem: DetailListViewItem) -> Unit,
    onDeleteListItem: (detailListViewItem: DetailListViewItem) -> Unit,
) {
    val verticalScrollState = rememberLazyListState(0)
    val horizontalScrollState = rememberScrollState(0)

    var viewPortWidth by remember { mutableStateOf(0.dp) }
    val columnsLengths = remember(items) { calculateWeights(header, items) }
    val numberOfActions = listOf(isEditingEnabled, isDeletingEnabled).count { it }
    val isWeighted =
        remember(columnsLengths, viewPortWidth) {
            columnsLengths.sum() * COLUMN_WIDTH_FONT_SIZE_MULTIPLIER.value <
                (
                    viewPortWidth.value - (ROW_PADDING_HORIZONTAL.value * 2) -
                        (CELL_PADDING_HORIZONTAL.value * 2 * columnsLengths.size) -
                        (numberOfActions * ACTION_ITEM_WIDTH.value)
                )
        }
    val weights =
        remember(columnsLengths) { columnsLengths.map { it.toFloat() / columnsLengths.sum() } }
    val density = LocalDensity.current

    Box(
        modifier.fillMaxSize().onGloballyPositioned {
            viewPortWidth = with(density) { it.size.width.toDp() }
        },
    ) {
        // Render once the viewport width is known. Keep the Box (and its onGloballyPositioned) in
        // the tree every frame via an if-guard rather than early-returning from the composable,
        // which would drop the scrollbar siblings and change the subtree shape between frames.
        if (viewPortWidth != 0.dp) {
            LazyColumn(
                Modifier.scrollHorizontally(horizontalScrollState, viewPortWidth.value, isWeighted),
                verticalScrollState,
            ) {
                stickyHeader {
                    TableHeader(
                        header,
                        numberOfActions * ACTION_ITEM_WIDTH.value,
                        lengths = columnsLengths,
                        weights = weights,
                        isWeighted = isWeighted,
                    )
                }

                items(items, key = { it.id }) {
                    DetailedListItem(
                        item = it,
                        isEditingEnabled = isEditingEnabled,
                        isDeletingEnabled = isDeletingEnabled,
                        lengths = columnsLengths,
                        weights = weights,
                        isWeighted = isWeighted,
                        onEditItem = { onEditListItem(it) },
                        onDeleteItem = { onDeleteListItem(it) },
                    )
                }
            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = verticalScrollState),
            )

            HorizontalScrollbar(
                modifier = Modifier.align(Alignment.BottomStart),
                adapter = rememberScrollbarAdapter(scrollState = horizontalScrollState),
            )
        }
    }
}

@Composable
fun TableHeader(
    header: DetailListViewHeader,
    actionWidth: Float,
    weights: List<Float>,
    lengths: List<Int>,
    isWeighted: Boolean,
) {
    Box(
        modifier =
            Modifier
                .padding(horizontal = ROW_PADDING_HORIZONTAL)
                .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            header.headers.forEachIndexed { index, header ->
                HeaderCell(title = header, weight = weights[index], width = lengths[index], isWeighted = isWeighted)
            }

            Spacer(modifier = Modifier.width(actionWidth.dp))
        }
    }
}

@Composable
private fun RowScope.HeaderCell(
    title: String,
    weight: Float,
    width: Int,
    isWeighted: Boolean,
) {
    Box(
        modifier =
            Modifier
                .widthIn((width * COLUMN_WIDTH_FONT_SIZE_MULTIPLIER.value).dp)
                .then(if (isWeighted) Modifier.weight(weight) else Modifier)
                .padding(horizontal = CELL_PADDING_HORIZONTAL),
        contentAlignment = Alignment.Center,
    ) {
        CerealText(
            text = title,
            textAlign = TextAlign.Center,
            maxLines = 2,
            fontWeight = FontWeight.Bold,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DetailedListItem(
    item: DetailListViewItem,
    isEditingEnabled: Boolean,
    isDeletingEnabled: Boolean,
    weights: List<Float>,
    lengths: List<Int>,
    isWeighted: Boolean,
    onEditItem: () -> Unit,
    onDeleteItem: () -> Unit,
) {
    CerealCard(
        modifier =
            Modifier
                .padding(horizontal = ROW_PADDING_HORIZONTAL, vertical = 3.dp)
                .height(70.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            item.attributes.forEachIndexed { index, attribute ->
                TableCell(attribute, isWeighted = isWeighted, width = lengths[index], weight = weights[index])
            }

            if (isEditingEnabled) {
                Column(modifier = Modifier.width(ACTION_ITEM_WIDTH)) {
                    CerealIconButton(
                        onClick = {
                            onEditItem()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(Res.string.edit),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            if (isDeletingEnabled) {
                Column(modifier = Modifier.width(ACTION_ITEM_WIDTH)) {
                    CerealIconButton(onClick = {
                        onDeleteItem()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.delete),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TableCell(
    text: String?,
    isWeighted: Boolean,
    width: Int,
    weight: Float,
) {
    Box(
        modifier =
            Modifier
                .widthIn((width * COLUMN_WIDTH_FONT_SIZE_MULTIPLIER.value).dp)
                .then(if (isWeighted) Modifier.weight(weight) else Modifier)
                .padding(horizontal = CELL_PADDING_HORIZONTAL),
        contentAlignment = Alignment.Center,
    ) {
        CerealText(
            text = text ?: "-",
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun calculateWeights(
    header: DetailListViewHeader,
    items: List<DetailListViewItem>,
): List<Int> {
    val lengths =
        header.headers
            .map { it.length }
            .toMutableList()

    for (i in header.headers.indices) {
        val colContents = items.map { it.attributes[i] }
        var largest = header.headers[i].length

        for (content in colContents) {
            val contentLength = content?.split("\n")?.maxOf { it.length } ?: 0

            if (contentLength > largest) largest = contentLength
        }

        lengths[i] = largest
    }

    return lengths
}

private fun Modifier.scrollHorizontally(
    horizontalScrollState: ScrollState,
    viewPortWidth: Float?,
    isWeighted: Boolean,
): Modifier =
    this
        .fillMaxWidth()
        .horizontalScroll(horizontalScrollState)
        .padding()
        .then(if (viewPortWidth != null && isWeighted) Modifier.widthIn(max = viewPortWidth.dp) else Modifier)
