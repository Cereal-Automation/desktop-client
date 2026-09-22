package com.cereal.client.presentation.brand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.logout
import com.cereal_automation.cereal_client.generated.resources.paywall_error_message
import com.cereal_automation.cereal_client.generated.resources.paywall_get_action
import com.cereal_automation.cereal_client.generated.resources.paywall_message
import com.cereal_automation.cereal_client.generated.resources.paywall_refresh_action
import com.cereal_automation.cereal_client.generated.resources.paywall_retry_action
import com.cereal_automation.cereal_client.generated.resources.paywall_title
import com.cereal_automation.cereal_client.generated.resources.paywall_unavailable_message
import org.jetbrains.compose.resources.stringResource

/** Why the branded gate is blocking the app — drives the message and the primary action. */
enum class BrandPaywallReason {
    /** No subscription for a required Brand script — offer the purchase page. */
    NEEDS_SUBSCRIPTION,

    /** Entitled, but the Brand scripts are not installed — offer a download retry. */
    SCRIPTS_UNAVAILABLE,

    /** The gate could not be evaluated — offer a retry. */
    VERIFICATION_ERROR,
}

/**
 * Branded screen shown when a white-label user is logged in but blocked from the app (see
 * docs/adr/0004). There is no marketplace to browse — just a targeted CTA (purchase or retry) plus
 * a logout escape hatch.
 */
@Composable
fun BrandPaywallScreen(
    brandName: String,
    reason: BrandPaywallReason,
    hasPurchaseUrl: Boolean,
    onPurchase: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(CerealTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.md),
        ) {
            CerealText(
                text = brandName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            CerealText(
                text = stringResource(Res.string.paywall_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            CerealText(
                text =
                    when (reason) {
                        BrandPaywallReason.NEEDS_SUBSCRIPTION -> stringResource(Res.string.paywall_message, brandName)
                        BrandPaywallReason.SCRIPTS_UNAVAILABLE -> stringResource(Res.string.paywall_unavailable_message, brandName)
                        BrandPaywallReason.VERIFICATION_ERROR -> stringResource(Res.string.paywall_error_message)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = CerealTheme.colorScheme.contentSecondary,
                textAlign = TextAlign.Center,
            )

            // The purchase CTA only makes sense when a subscription is actually missing and the Brand
            // declares a purchase URL; the other reasons are resolved by retrying.
            if (reason == BrandPaywallReason.NEEDS_SUBSCRIPTION && hasPurchaseUrl) {
                CerealButton(
                    onClick = onPurchase,
                    text = stringResource(Res.string.paywall_get_action, brandName),
                    type = CerealButtonType.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CerealButton(
                onClick = onRetry,
                text =
                    if (reason == BrandPaywallReason.NEEDS_SUBSCRIPTION) {
                        stringResource(Res.string.paywall_refresh_action)
                    } else {
                        stringResource(Res.string.paywall_retry_action)
                    },
                type = CerealButtonType.Surface,
                modifier = Modifier.fillMaxWidth(),
            )
            CerealTextButton(onClick = onLogout) {
                CerealText(text = stringResource(Res.string.logout))
            }
        }
    }
}
