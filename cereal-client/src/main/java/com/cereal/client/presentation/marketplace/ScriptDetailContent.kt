package com.cereal.client.presentation.marketplace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.script_capacity_amount
import com.cereal_automation.cereal_client.generated.resources.script_capacity_unlimited
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import org.jetbrains.compose.resources.stringResource
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@Composable
internal fun ScriptDetailHero(script: MarketplaceScript) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.lg),
    ) {
        // Large icon
        ScriptIcon(script = script, size = 64.dp)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.xs),
        ) {
            // Badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (script.isCommunity) CommunityBadge()
                if (script.isNew) NewBadge()
                PriceBadge(script)
                if (script.freeTrialEnabled) FreeTrialBadge()
            }

            // Short description
            script.shortDescription?.let { short ->
                CerealText(
                    text = short,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CerealTheme.colorScheme.contentSecondary,
                )
            }

            // Rating
            script.averageRating?.let { rating ->
                RatingRow(rating)
            }

            // Developer
            script.developer?.let { dev ->
                DeveloperRow(developer = dev, avatarSize = 18.dp)
            }
        }
    }
}

@Composable
internal fun DetailSection(
    title: String?,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm)) {
        if (title != null) {
            CerealText(
                text = title.uppercase(),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing =
                            TextUnit(1f, TextUnitType.Sp),
                        color = CerealTheme.colorScheme.contentTertiary,
                    ),
            )
        }
        content()
    }
}

@Composable
internal fun MetaRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CerealText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = CerealTheme.colorScheme.contentTertiary,
        )
        CerealText(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The user-facing capacity label for a script's entitled record cap, or `null` when there is nothing
 * to show ([ScriptCapacity.None] — a free / single-price script). Finite caps render as "N records",
 * unlimited tiers as "Unlimited records", where the unit is server-provided.
 */
@Composable
internal fun scriptCapacityText(capacity: ScriptCapacity): String? =
    when (capacity) {
        ScriptCapacity.None -> null
        is ScriptCapacity.Unlimited -> stringResource(Res.string.script_capacity_unlimited, capacity.unit)
        is ScriptCapacity.Limited -> stringResource(Res.string.script_capacity_amount, capacity.records, capacity.unit)
    }

@Composable
internal fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Markdown(
        content = text,
        modifier = modifier,
        colors =
            markdownColor(
                text = CerealTheme.colorScheme.contentSecondary,
            ),
        typography =
            markdownTypography(
                h1 = MaterialTheme.typography.headlineLarge,
                h2 = MaterialTheme.typography.headlineMedium,
                h3 = MaterialTheme.typography.headlineSmall,
                h4 = MaterialTheme.typography.titleLarge,
                h5 = MaterialTheme.typography.titleMedium,
                h6 = MaterialTheme.typography.titleSmall,
                text = MaterialTheme.typography.bodyMedium,
                paragraph = MaterialTheme.typography.bodyMedium,
                code = MaterialTheme.typography.bodySmall,
            ),
    )
}

@Composable
internal fun TagChip(tag: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, CerealTheme.colorScheme.borderDark),
    ) {
        CerealText(
            text = tag,
            modifier =
                Modifier.padding(
                    horizontal = CerealTheme.spacing.md,
                    vertical = CerealTheme.spacing.xxs,
                ),
            style = MaterialTheme.typography.labelSmall,
            color = CerealTheme.colorScheme.contentSecondary,
        )
    }
}

private val marketplaceDateFormatter: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("d MMM yyyy", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalTime::class)
internal fun formatMarketplaceDate(instant: Instant): String = marketplaceDateFormatter.format(instant.toJavaInstant())
