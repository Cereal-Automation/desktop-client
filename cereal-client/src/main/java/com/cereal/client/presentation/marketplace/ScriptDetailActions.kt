package com.cereal.client.presentation.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.application.agent.isAiBrowserAgent
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.create_account
import com.cereal_automation.cereal_client.generated.resources.marketplace_awaiting_checkout
import com.cereal_automation.cereal_client.generated.resources.marketplace_guest_purchase_required
import com.cereal_automation.cereal_client.generated.resources.marketplace_install
import com.cereal_automation.cereal_client.generated.resources.marketplace_installing
import com.cereal_automation.cereal_client.generated.resources.marketplace_no_release
import com.cereal_automation.cereal_client.generated.resources.marketplace_purchase
import com.cereal_automation.cereal_client.generated.resources.marketplace_remove
import com.cereal_automation.cereal_client.generated.resources.marketplace_removing
import com.cereal_automation.cereal_client.generated.resources.marketplace_start_new_instance
import com.cereal_automation.cereal_client.generated.resources.marketplace_start_trial
import com.cereal_automation.cereal_client.generated.resources.marketplace_support
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ScriptDetailActions(
    script: MarketplaceScript,
    installState: ScriptDetailViewModel.InstallState,
    onInstall: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onStartNewInstance: () -> Unit,
    onRemove: () -> Unit,
    onRemoveEnabled: Boolean,
) {
    HorizontalDivider(color = CerealTheme.colorScheme.borderDark)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CerealTheme.spacing.xl, vertical = CerealTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Support link
        script.supportUrl?.let { url ->
            CerealButton(
                onClick = { onOpenUrl(url) },
                text = stringResource(Res.string.marketplace_support),
                type = CerealButtonType.Surface,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.height(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (installState) {
                is ScriptDetailViewModel.InstallState.Loading -> {
                    // Render nothing while the install state is being determined
                }

                is ScriptDetailViewModel.InstallState.Installing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
                    ) {
                        CerealCircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        CerealText(
                            text = stringResource(Res.string.marketplace_installing),
                            style = MaterialTheme.typography.bodySmall,
                            color = CerealTheme.colorScheme.contentSecondary,
                        )
                    }
                }

                is ScriptDetailViewModel.InstallState.Removing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
                    ) {
                        CerealCircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        CerealText(
                            text = stringResource(Res.string.marketplace_removing),
                            style = MaterialTheme.typography.bodySmall,
                            color = CerealTheme.colorScheme.contentSecondary,
                        )
                    }
                }

                is ScriptDetailViewModel.InstallState.AwaitingCheckout -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
                    ) {
                        CerealCircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        CerealText(
                            text = stringResource(Res.string.marketplace_awaiting_checkout),
                            style = MaterialTheme.typography.bodySmall,
                            color = CerealTheme.colorScheme.contentSecondary,
                        )
                    }
                }

                is ScriptDetailViewModel.InstallState.Success, ScriptDetailViewModel.InstallState.AlreadyInstalled -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!script.isAiBrowserAgent) {
                            CerealButton(
                                onClick = onRemove,
                                text = stringResource(Res.string.marketplace_remove),
                                type = CerealButtonType.Surface,
                                enabled = onRemoveEnabled,
                            )
                        }
                        CerealButton(
                            onClick = onStartNewInstance,
                            text = stringResource(Res.string.marketplace_start_new_instance),
                            type = CerealButtonType.Primary,
                        )
                    }
                }

                is ScriptDetailViewModel.InstallState.GuestPurchaseRequired -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
                    ) {
                        CerealText(
                            text = stringResource(Res.string.marketplace_guest_purchase_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = CerealTheme.colorScheme.contentSecondary,
                        )
                        CerealButton(
                            onClick = onRegisterClick,
                            text = stringResource(Res.string.create_account),
                            type = CerealButtonType.Surface,
                        )
                        // Keep what the guest came for (trial / price) on the main action; it asks them to sign in.
                        CerealButton(
                            onClick = onLoginClick,
                            text = if (script.latestRelease != null) purchaseButtonText(script) else stringResource(Res.string.marketplace_no_release),
                            enabled = script.latestRelease != null,
                            type = CerealButtonType.Primary,
                        )
                    }
                }

                is ScriptDetailViewModel.InstallState.Error -> {
                    CerealText(
                        text = installState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = CerealTheme.colorScheme.warning,
                    )
                }

                else -> {
                    val hasRelease = script.latestRelease != null
                    if (script.isFree) {
                        CerealButton(
                            onClick = onInstall,
                            text =
                                if (hasRelease) {
                                    stringResource(Res.string.marketplace_install)
                                } else {
                                    stringResource(Res.string.marketplace_no_release)
                                },
                            enabled = hasRelease,
                            type = CerealButtonType.Success,
                        )
                    } else {
                        CerealButton(
                            onClick = onInstall,
                            text = if (hasRelease) purchaseButtonText(script) else stringResource(Res.string.marketplace_no_release),
                            enabled = hasRelease,
                            type = CerealButtonType.Primary,
                        )
                    }
                }
            }
        }
    }
}

/** "Start 7-day trial · €4 / mo" or "Purchase · €4 / mo" for a paid script. */
@Composable
private fun purchaseButtonText(script: MarketplaceScript): String {
    val priceText = script.formattedPrice ?: script.price?.let { "$$it" }
    val trialDays = script.freeTrialDays?.takeIf { script.freeTrialEnabled && it > 0 }
    val action =
        if (trialDays != null) {
            stringResource(Res.string.marketplace_start_trial, trialDays)
        } else {
            stringResource(Res.string.marketplace_purchase)
        }
    return action + (priceText?.let { " · $it" } ?: "")
}
