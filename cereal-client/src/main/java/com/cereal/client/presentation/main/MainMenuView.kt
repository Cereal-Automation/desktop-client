package com.cereal.client.presentation.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.util.initialsOf
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.SectionLabel
import com.cereal.client.presentation.view.StatusDot
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.app_icon
import com.cereal_automation.cereal_client.generated.resources.app_name
import com.cereal_automation.cereal_client.generated.resources.application
import com.cereal_automation.cereal_client.generated.resources.collapse_sidebar
import com.cereal_automation.cereal_client.generated.resources.expand_sidebar
import com.cereal_automation.cereal_client.generated.resources.nav_user_fallback
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MainMenu(
    menuItems: ImmutableList<MenuItemUiModel>,
    collapsed: Boolean = false,
    onToggleCollapsed: () -> Unit = {},
    onClick: (MenuItemUiModel) -> Unit,
) {
    // Empty sections (e.g. a Brand build without the Marketplace) are simply absent from the map.
    val sections = remember(menuItems) { menuItems.filter { it.section != null }.groupBy { it.section!! } }
    val profileItem = menuItems.firstOrNull { it.section == null }
    val username = profileItem?.titleOverride
    val width by animateDpAsState(
        targetValue = if (collapsed) COLLAPSED_WIDTH else EXPANDED_WIDTH,
        animationSpec = tween(durationMillis = 180),
    )

    Column(
        modifier =
            Modifier
                .width(width)
                .fillMaxHeight()
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 12.dp, bottom = 8.dp),
    ) {
        SidebarBrand(collapsed = collapsed, onToggleCollapsed = onToggleCollapsed)

        AnimatedContent(
            targetState = collapsed,
            transitionSpec = {
                // Expanding: the labelled items rise into place from below; collapsing: a plain crossfade.
                val enter =
                    if (targetState) {
                        fadeIn(tween(durationMillis = 180))
                    } else {
                        slideInVertically(tween(durationMillis = 220)) { it / 6 } + fadeIn(tween(durationMillis = 220))
                    }
                enter togetherWith fadeOut(tween(durationMillis = 90))
            },
            contentAlignment = Alignment.TopStart,
        ) { isCollapsed ->
            Column {
                MenuSection.entries.filter { it in sections && it.titleResource != null }.forEachIndexed { index, section ->
                    if (!isCollapsed) {
                        MenuSectionLabel(stringResource(section.titleResource!!))
                    } else if (index > 0) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = CerealTheme.colorScheme.border,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    sections.getValue(section).forEach { item ->
                        MenuNavItem(item = item, collapsed = isCollapsed, onClick = { onClick(item) })
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Unlabelled sections sit just above the user card, not under a header of their own.
        MenuSection.entries.filter { it.titleResource == null }.forEach { section ->
            sections[section]?.forEach { item ->
                MenuNavItem(item = item, collapsed = collapsed, onClick = { onClick(item) })
            }
        }

        UserCard(
            username = username,
            collapsed = collapsed,
            onClick = { profileItem?.let { onClick(it) } },
        )
    }
}

@Composable
private fun SidebarBrand(
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
) {
    if (collapsed) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppIcon()
            SidebarToggle(collapsed = true, onClick = onToggleCollapsed)
        }
        return
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppIcon()
        CerealText(
            text = stringResource(Res.string.app_name),
            style =
                MaterialTheme.typography.titleSmall.copy(
                    letterSpacing = TextUnit(-0.01f, TextUnitType.Em),
                ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        SidebarToggle(collapsed = false, onClick = onToggleCollapsed)
    }
}

@Composable
private fun AppIcon() {
    Box(
        modifier =
            Modifier
                .size(30.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.onBackground),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.application),
            contentDescription = stringResource(Res.string.app_icon),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun SidebarToggle(
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    CerealIconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(
            imageVector = if (collapsed) Icons.Filled.KeyboardDoubleArrowRight else Icons.Filled.KeyboardDoubleArrowLeft,
            contentDescription = stringResource(if (collapsed) Res.string.expand_sidebar else Res.string.collapse_sidebar),
            tint = CerealTheme.colorScheme.contentTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun MenuSectionLabel(label: String) {
    SectionLabel(
        text = label.uppercase(),
        modifier = Modifier.fillMaxWidth(),
        color = CerealTheme.colorScheme.contentTertiary,
        // Start aligns with the nav item icons (8dp item inset + 16dp item padding).
        contentPadding = PaddingValues(start = 24.dp, end = 18.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun MenuNavItem(
    item: MenuItemUiModel,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    // Selection stays neutral: red is reserved for badges that need the user.
    val accent = MaterialTheme.colorScheme.onSurface
    val label = item.titleOverride ?: stringResource(item.titleResource)
    val iconTint = if (item.selected) accent else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
    ) {
        if (item.selected) {
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

        SidebarTooltip(text = label, enabled = collapsed) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (item.selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable(
                            onClick = onClick,
                            indication = ripple(color = accent),
                            interactionSource = remember { MutableInteractionSource() },
                        ).padding(horizontal = if (collapsed) 0.dp else 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(11.dp),
            ) {
                if (collapsed) {
                    // The badge and dot ride on the icon's top-right so they survive without the label. The
                    // fixed-height slot keeps a badge from growing the row; the badge hugs the row's end edge so
                    // a wide "9+" isn't cut off by the item's clip.
                    Box(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                        Box(modifier = Modifier.align(Alignment.Center)) {
                            NavIcon(icon = item.resourcePath, label = label, tint = iconTint)
                        }
                        item.badge?.let { count ->
                            NavBadge(
                                count = count,
                                isAttention = item.isBadgeAttention,
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .wrapContentSize(Alignment.TopEnd, unbounded = true)
                                        .offset(y = (-8).dp),
                            )
                        }
                        if (item.hasNotificationDot && item.badge == null) {
                            StatusDot(
                                color = CerealTheme.colorScheme.success,
                                modifier = Modifier.align(Alignment.Center).offset(x = 8.dp, y = (-8).dp),
                            )
                        }
                    }
                    return@Row
                }

                NavIcon(icon = item.resourcePath, label = label, tint = iconTint)

                CerealText(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    color = if (item.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                item.badge?.let { count -> NavBadge(count = count, isAttention = item.isBadgeAttention) }

                if (item.hasNotificationDot && item.badge == null) {
                    StatusDot(color = CerealTheme.colorScheme.success)
                }
            }
        }
    }
}

@Composable
private fun NavIcon(
    icon: IconSource,
    label: String,
    tint: Color,
) {
    when (icon) {
        is IconSource.Vector -> {
            Icon(
                imageVector = icon.imageVector,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }

        is IconSource.Drawable -> {
            Image(
                painter = painterResource(icon.drawableResource),
                contentDescription = label,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun NavBadge(
    count: Int,
    isAttention: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = 18.dp)
                .clip(CircleShape)
                .background(if (isAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        CerealText(
            // Cap the badge so a large unseen-notification count never widens the pill.
            text = if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString(),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = if (isAttention) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shows [text] beside [content] on hover, only while [enabled] (the collapsed rail has no labels). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarTooltip(
    text: String,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    TooltipArea(
        tooltip = {
            Surface(
                modifier = Modifier.shadow(4.dp),
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                CerealText(
                    text = text,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        delayMillis = 300,
        tooltipPlacement =
            TooltipPlacement.ComponentRect(
                anchor = Alignment.CenterEnd,
                alignment = Alignment.CenterEnd,
                offset = DpOffset(4.dp, 0.dp),
            ),
        content = content,
    )
}

@Composable
private fun UserCard(
    username: String?,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    val fallback = stringResource(Res.string.nav_user_fallback)
    val initials = remember(username, fallback) { initialsOf(username, fallback.first()) }
    val name = username ?: fallback

    if (collapsed) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            SidebarTooltip(text = name, enabled = true) {
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onClick)
                            .semantics { contentDescription = name }
                            .padding(4.dp),
                ) {
                    Avatar(initials = initials)
                }
            }
        }
        return
    }

    Row(
        modifier =
            Modifier
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
                .fillMaxWidth()
                .background(CerealTheme.colorScheme.cardDark, MaterialTheme.shapes.medium)
                .border(1.dp, CerealTheme.colorScheme.borderDark, MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium)
                .clickable(
                    onClick = onClick,
                    indication = ripple(color = MaterialTheme.colorScheme.onSurface),
                    interactionSource = remember { MutableInteractionSource() },
                ).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(initials = initials)

        CerealText(
            text = name,
            modifier = Modifier.weight(1f),
            style = CerealTypography.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun Avatar(initials: String) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    CerealTheme.colorScheme.warning,
                                ),
                        ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        CerealText(
            text = initials,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.surface,
        )
    }
}

/** Above this, the sidebar badge shows "9+" so a large count never widens the pill. */
private const val MAX_BADGE_COUNT = 9

private val EXPANDED_WIDTH = 232.dp
private val COLLAPSED_WIDTH = 56.dp
