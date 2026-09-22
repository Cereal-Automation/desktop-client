package com.cereal.client.presentation.marketplace

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.ScriptOwner
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.marketplace_community_badge
import com.cereal_automation.cereal_client.generated.resources.marketplace_free_badge
import com.cereal_automation.cereal_client.generated.resources.marketplace_free_trial
import com.cereal_automation.cereal_client.generated.resources.marketplace_new_badge
import com.cereal_automation.cereal_client.generated.resources.marketplace_status_maintenance
import com.cereal_automation.cereal_client.generated.resources.marketplace_status_operational
import com.cereal_automation.cereal_client.generated.resources.script_update_required_badge
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.jetbrains.compose.resources.stringResource

// Shared color palette used for script icons and developer avatars
internal val scriptColors =
    listOf(
        Color(0xFFEF5350), // Red 400
        Color(0xFFEC407A), // Pink 400
        Color(0xFFAB47BC), // Purple 400
        Color(0xFF7E57C2), // Deep Purple 400
        Color(0xFF5C6BC0), // Indigo 400
        Color(0xFF42A5F5), // Blue 400
        Color(0xFF26C6DA), // Cyan 400
        Color(0xFF26A69A), // Teal 400
        Color(0xFF66BB6A), // Green 400
        Color(0xFFD4E157), // Lime 400
        Color(0xFFFFCA28), // Amber 400
        Color(0xFFFFA726), // Orange 400
        Color(0xFF8D6E63), // Brown 400
        Color(0xFF78909C), // Blue Grey 400
    )

private const val POSITIVE_INT_MASK = 0x7FFFFFFF

internal fun scriptColorFor(id: Int): Color = scriptColors[id.hashCode().and(POSITIVE_INT_MASK) % scriptColors.size]

internal fun scriptColorFor(name: String): Color = scriptColors[name.hashCode().and(POSITIVE_INT_MASK) % scriptColors.size]

/**
 * A rounded-square icon that shows the first letter of the script title.
 *
 * @param script The script whose title drives the letter and color.
 * @param size   The size of the icon surface. Defaults to 40.dp (card size).
 */
@Composable
internal fun ScriptIcon(
    script: MarketplaceScript,
    size: Dp = 40.dp,
) {
    val iconColor = scriptColorFor(script.id)
    val letter =
        script.title
            .firstOrNull { it.isLetter() }
            ?.uppercaseChar()
            ?.toString() ?: "#"

    Surface(
        modifier = Modifier.size(size),
        shape = MaterialTheme.shapes.small,
        color = iconColor.copy(alpha = 0.12f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CerealText(
                text = letter,
                style =
                    if (size >= 56.dp) {
                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    },
                color = iconColor,
            )
        }
    }
}

/**
 * A small circular avatar showing the first letter of a name.
 *
 * @param name  The name used to derive the color and initial.
 * @param size  Diameter of the avatar. Defaults to 20.dp.
 */
@Composable
internal fun DeveloperAvatar(
    name: String,
    size: Dp = 20.dp,
) {
    val color = scriptColorFor(name)
    val initial =
        name
            .firstOrNull { it.isLetter() }
            ?.uppercaseChar()
            ?.toString() ?: "?"

    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = color.copy(alpha = 0.15f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CerealText(
                text = initial,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
        }
    }
}

/**
 * A row showing a developer avatar (image or fallback letter) and the developer name.
 *
 * @param developer  The script owner.
 * @param avatarSize Diameter of the avatar image / fallback. Defaults to 20.dp.
 * @param maxLines   Max lines for the developer name text. Defaults to 1.
 */
@Composable
internal fun DeveloperRow(
    developer: ScriptOwner,
    avatarSize: Dp = 20.dp,
    maxLines: Int = 1,
) {
    val avatarUrl = developer.avatarUrl

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.xs),
    ) {
        if (avatarUrl != null) {
            KamelImage(
                resource = { asyncPainterResource(avatarUrl) },
                contentDescription = developer.name,
                modifier = Modifier.size(avatarSize).clip(CircleShape),
                contentScale = ContentScale.Crop,
                onLoading = { DeveloperAvatar(developer.name, avatarSize) },
                onFailure = { DeveloperAvatar(developer.name, avatarSize) },
            )
        } else {
            DeveloperAvatar(developer.name, avatarSize)
        }

        val nameText = if (developer.verified) "${developer.name} · verified" else developer.name
        CerealText(
            text = nameText,
            style = MaterialTheme.typography.bodySmall,
            color = CerealTheme.colorScheme.contentSecondary,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A small badge with a colored background and text label.
 *
 * @param text           The label to display.
 * @param containerColor The background color of the badge.
 * @param textColor      The color of the text.
 */
@Composable
internal fun ScriptBadge(
    text: String,
    containerColor: Color,
    textColor: Color,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor,
    ) {
        CerealText(
            text = text,
            modifier =
                Modifier.padding(
                    horizontal = CerealTheme.spacing.sm,
                    vertical = 2.dp,
                ),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor,
        )
    }
}

@Composable
internal fun CommunityBadge() =
    ScriptBadge(
        text = stringResource(Res.string.marketplace_community_badge),
        containerColor = CerealTheme.colorScheme.info.copy(alpha = 0.12f),
        textColor = CerealTheme.colorScheme.info,
    )

@Composable
internal fun NewBadge() =
    ScriptBadge(
        text = stringResource(Res.string.marketplace_new_badge),
        containerColor = CerealTheme.colorScheme.warning.copy(alpha = 0.12f),
        textColor = CerealTheme.colorScheme.warning,
    )

@Composable
internal fun UpdateRequiredBadge() =
    ScriptBadge(
        text = stringResource(Res.string.script_update_required_badge),
        containerColor = CerealTheme.colorScheme.warning.copy(alpha = 0.12f),
        textColor = CerealTheme.colorScheme.warning,
    )

@Composable
internal fun FreeTrialBadge() =
    ScriptBadge(
        text = stringResource(Res.string.marketplace_free_trial),
        containerColor = CerealTheme.colorScheme.warning.copy(alpha = 0.12f),
        textColor = CerealTheme.colorScheme.warning,
    )

/**
 * Badge showing either "Free", a formatted price, or nothing for a script.
 */
@Composable
internal fun PriceBadge(script: MarketplaceScript) {
    if (script.isFree) {
        ScriptBadge(
            text = stringResource(Res.string.marketplace_free_badge),
            containerColor = CerealTheme.colorScheme.success.copy(alpha = 0.12f),
            textColor = CerealTheme.colorScheme.success,
        )
    } else {
        val priceText = script.formattedPrice ?: script.price?.let { "$$it" }
        if (priceText != null) {
            ScriptBadge(
                text = priceText,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                textColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Star icon + rating value, aligned in a row. Shows "–" when [rating] is null.
 */
@Composable
internal fun RatingRow(rating: Double?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.xxs),
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = Color(0xFFF59E0B),
        )
        CerealText(
            text = "${rating ?: "-"}",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A small coloured dot that communicates the script's maintenance status.
 * Green = operational, orange = under maintenance.
 * Displays a tooltip on hover that explains the status in plain language.
 *
 * @param maintenanceMode True when the script is under maintenance.
 */
@Composable
internal fun ScriptStatusDot(maintenanceMode: Boolean) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val density = LocalDensity.current

    val dotColor = if (maintenanceMode) CerealTheme.colorScheme.warning else CerealTheme.colorScheme.success
    val tooltipText =
        stringResource(
            if (maintenanceMode) {
                Res.string.marketplace_status_maintenance
            } else {
                Res.string.marketplace_status_operational
            },
        )

    Box(
        modifier =
            Modifier
                .size(10.dp)
                .hoverable(interactionSource),
    ) {
        Surface(
            modifier = Modifier.size(10.dp),
            shape = CircleShape,
            color = dotColor,
        ) {}

        if (isHovered) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, with(density) { -36.dp.roundToPx() }),
                properties = PopupProperties(focusable = false),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.inverseSurface,
                ) {
                    CerealText(
                        text = tooltipText,
                        modifier =
                            Modifier.padding(
                                horizontal = CerealTheme.spacing.sm,
                                vertical = CerealTheme.spacing.xs,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}
