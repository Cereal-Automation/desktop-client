package com.cereal.client.presentation.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.tasks.MenuOption
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.more_options
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ListItem(
    content: @Composable () -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    menuOptions: List<MenuOption> = emptyList(),
    onMenuOptionClick: ((obj: MenuOption) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    backgroundColor: Color = CerealTheme.colorScheme.cardDark,
    outerPadding: PaddingValues = PaddingValues(horizontal = 25.dp, vertical = 3.dp),
    hoverActions: @Composable RowScope.() -> Unit = {},
    alwaysShowActions: Boolean = false,
    onClick: () -> Unit,
) {
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }
    var isHovering by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier =
            modifier
                .padding(outerPadding)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = {
                    isOverflowMenuExpanded = true
                }, interactionSource = remember { interactionSource })
                .indication(interactionSource, ripple(color = MaterialTheme.colorScheme.primary))
                .onClick(
                    matcher = PointerMatcher.mouse(PointerButton.Primary),
                    onLongClick = {
                        onLongClick?.invoke()
                    },
                    onClick = {
                        onClick()
                    },
                    interactionSource = remember { interactionSource },
                ).onPointerEvent(PointerEventType.Enter) {
                    isHovering = true
                }.onPointerEvent(PointerEventType.Exit) {
                    isHovering = false
                },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape =
            if (isSelected) {
                MaterialTheme.shapes.small.copy(
                    topStart = CornerSize(0.dp),
                    bottomStart = CornerSize(0.dp),
                )
            } else {
                MaterialTheme.shapes.small
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                if (isSelected) {
                    Modifier.background(
                        brush =
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        CerealTheme.colorScheme.highlight.copy(alpha = 0.102f),
                                        CerealTheme.colorScheme.highlight.copy(alpha = 0f),
                                    ),
                            ),
                    )
                } else {
                    Modifier
                },
        ) {
            if (isSelected) {
                Box(
                    Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .clip(RectangleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            } else {
                Spacer(Modifier.width(5.dp))
            }
            Box(
                modifier = Modifier.padding(horizontal = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1.0f)
                                .padding(vertical = 8.dp)
                                .align(Alignment.CenterVertically),
                    ) {
                        content()
                    }
                    val shouldShowActions = alwaysShowActions || isHovering || isOverflowMenuExpanded
                    if (shouldShowActions) {
                        hoverActions()
                    }
                    if (menuOptions.isNotEmpty() && shouldShowActions) {
                        Spacer(Modifier.width(12.dp))
                        Box {
                            CerealIconButton(
                                onClick = { isOverflowMenuExpanded = true },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(Res.string.more_options),
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                            if (menuOptions.isNotEmpty()) {
                                DropdownMenu(
                                    expanded = isOverflowMenuExpanded,
                                    onDismissRequest = { isOverflowMenuExpanded = false },
                                ) {
                                    menuOptions.forEach { menuOption ->
                                        DropdownMenuItem(
                                            text = {
                                                CerealText(stringResource(menuOption.stringResource))
                                            },
                                            onClick = {
                                                onMenuOptionClick?.invoke(menuOption)
                                                isOverflowMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
