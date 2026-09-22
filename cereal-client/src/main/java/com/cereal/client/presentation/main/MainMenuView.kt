package com.cereal.client.presentation.main

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.util.initialsOf
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.SectionLabel
import com.cereal.client.presentation.view.StatusDot
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.app_icon
import com.cereal_automation.cereal_client.generated.resources.app_name
import com.cereal_automation.cereal_client.generated.resources.application
import com.cereal_automation.cereal_client.generated.resources.nav_section_system
import com.cereal_automation.cereal_client.generated.resources.nav_section_workspace
import com.cereal_automation.cereal_client.generated.resources.nav_user_fallback
import com.cereal_automation.cereal_client.generated.resources.scripts_count
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MainMenu(
    menuItems: ImmutableList<MenuItemUiModel>,
    appVersion: String,
    activeScriptCount: Int,
    onClick: (MenuItemUiModel) -> Unit,
) {
    val partitioned =
        remember(menuItems) {
            val (main, bottom) = menuItems.partition { it.mainItem }
            val profile = bottom.firstOrNull { it.titleOverride != null }
            val accountItems = bottom.filter { it !== profile }
            Triple(main, accountItems, profile)
        }
    val mainItems = partitioned.first
    val bottomItems = partitioned.second
    val profileItem = partitioned.third
    val username = profileItem?.titleOverride

    Column(
        modifier =
            Modifier
                .width(232.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 12.dp, bottom = 8.dp),
    ) {
        SidebarBrand(appVersion = appVersion)

        MenuSectionLabel(stringResource(Res.string.nav_section_workspace))
        mainItems.forEach { item ->
            MenuNavItem(item = item, onClick = { onClick(item) })
        }

        MenuSectionLabel(stringResource(Res.string.nav_section_system))
        bottomItems.forEach { item ->
            MenuNavItem(item = item, onClick = { onClick(item) })
        }

        Spacer(Modifier.weight(1f))

        UserCard(
            username = username,
            activeScriptCount = activeScriptCount,
            onClick = { profileItem?.let { onClick(it) } },
        )
    }
}

@Composable
private fun SidebarBrand(appVersion: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
        CerealText(
            text = stringResource(Res.string.app_name),
            style =
                MaterialTheme.typography.titleSmall.copy(
                    letterSpacing = TextUnit(-0.01f, TextUnitType.Em),
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            CerealText(
                text = "v$appVersion",
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = TextUnit(0.04f, TextUnitType.Em),
                    ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MenuSectionLabel(label: String) {
    SectionLabel(
        text = label.uppercase(),
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.outline,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun MenuNavItem(
    item: MenuItemUiModel,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val accentTint = accent.copy(alpha = 0.10f)
    val label = item.titleOverride ?: stringResource(item.titleResource)

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

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (item.selected) accentTint else Color.Transparent)
                    .clickable(
                        onClick = onClick,
                        indication = ripple(color = accent),
                        interactionSource = remember { MutableInteractionSource() },
                    ).padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            val iconTint = if (item.selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
            when (val res = item.resourcePath) {
                is IconSource.Vector -> {
                    Icon(
                        imageVector = res.imageVector,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp),
                    )
                }

                is IconSource.Drawable -> {
                    Image(
                        painter = painterResource(res.drawableResource),
                        contentDescription = label,
                        colorFilter = ColorFilter.tint(iconTint),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

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

            item.badge?.let { count ->
                Box(
                    modifier =
                        Modifier
                            .defaultMinSize(minWidth = 18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
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
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }

            if (item.hasNotificationDot && item.badge == null) {
                StatusDot(color = CerealTheme.colorScheme.success)
            }
        }
    }
}

@Composable
private fun UserCard(
    username: String?,
    activeScriptCount: Int,
    onClick: () -> Unit,
) {
    val fallback = stringResource(Res.string.nav_user_fallback)
    val initials = remember(username, fallback) { initialsOf(username, fallback.first()) }
    val scriptCountLabel = pluralStringResource(Res.plurals.scripts_count, activeScriptCount, activeScriptCount)

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

        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = username ?: fallback,
                style = CerealTypography.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CerealText(
                text = scriptCountLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Above this, the sidebar badge shows "9+" so a large count never widens the pill. */
private const val MAX_BADGE_COUNT = 9
