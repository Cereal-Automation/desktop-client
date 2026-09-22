package com.cereal.client.presentation.brand

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.view.CerealCircularProgressIndicator

/**
 * Gates the authenticated app behind the Brand-script entitlement check (see docs/adr/0004).
 * Transparent for stock Cereal (the view model resolves to [BrandEntitlementViewState.Entitled]
 * immediately); for a white-label build it shows a loading spinner, then either the app or the
 * branded paywall.
 */
@Composable
fun BrandEntitlementGate(
    viewModel: BrandPaywallViewModel,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.check() }

    when (viewModel.state.value) {
        BrandEntitlementViewState.Entitled -> {
            content()
        }

        BrandEntitlementViewState.Loading -> {
            Box(modifier = Modifier.fillMaxSize()) {
                CerealCircularProgressIndicator(
                    modifier = Modifier.size(48.dp).align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            }
        }

        BrandEntitlementViewState.Locked -> {
            BrandPaywallScreen(
                brandName = viewModel.brandName,
                reason = BrandPaywallReason.NEEDS_SUBSCRIPTION,
                hasPurchaseUrl = viewModel.hasPurchaseUrl,
                onPurchase = viewModel::openPurchasePage,
                onRetry = viewModel::check,
                onLogout = viewModel::logout,
            )
        }

        BrandEntitlementViewState.Unavailable -> {
            BrandPaywallScreen(
                brandName = viewModel.brandName,
                reason = BrandPaywallReason.SCRIPTS_UNAVAILABLE,
                hasPurchaseUrl = viewModel.hasPurchaseUrl,
                onPurchase = viewModel::openPurchasePage,
                // Re-download the scripts (not just re-check) when they failed to install.
                onRetry = viewModel::retryDownload,
                onLogout = viewModel::logout,
            )
        }

        BrandEntitlementViewState.Error -> {
            BrandPaywallScreen(
                brandName = viewModel.brandName,
                reason = BrandPaywallReason.VERIFICATION_ERROR,
                hasPurchaseUrl = viewModel.hasPurchaseUrl,
                onPurchase = viewModel::openPurchasePage,
                onRetry = viewModel::check,
                onLogout = viewModel::logout,
            )
        }
    }
}
