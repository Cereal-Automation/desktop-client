package com.cereal.client.presentation.brand

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.auth.LogoutInteractor
import com.cereal.client.application.interactor.brand.BrandGateStatus
import com.cereal.client.application.interactor.brand.GetBrandGateStatusInteractor
import com.cereal.client.application.interactor.brand.SyncBrandScriptsInteractor
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

/**
 * Drives the branded gate (see docs/adr/0004): resolves whether to show the app, the subscription
 * paywall, or a "scripts unavailable" retry. Stock Cereal resolves to
 * [BrandEntitlementViewState.Entitled] immediately, so the gate is transparent there.
 */
class BrandPaywallViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val applicationConfig: ApplicationConfig,
    private val getBrandGateStatusInteractor: GetBrandGateStatusInteractor,
    private val syncBrandScriptsInteractor: SyncBrandScriptsInteractor,
    private val logoutInteractor: LogoutInteractor,
) {
    val brandName: String = applicationConfig.name
    val hasPurchaseUrl: Boolean = !applicationConfig.paywallUrl.isNullOrBlank()

    // Stock builds start (and stay) Entitled — no loading flash, no interactor call.
    private val _state =
        mutableStateOf(
            if (applicationConfig.isBranded) BrandEntitlementViewState.Loading else BrandEntitlementViewState.Entitled,
        )
    val state: State<BrandEntitlementViewState> = _state

    /** Re-evaluate the gate. Called when it is shown and after the user refreshes. */
    fun check() {
        if (!applicationConfig.isBranded) {
            _state.value = BrandEntitlementViewState.Entitled
            return
        }
        scope.launch(dispatcherProvider.io) {
            withContext(dispatcherProvider.main) { _state.value = BrandEntitlementViewState.Loading }
            getBrandGateStatusInteractor(Interactor.None()) { result ->
                withContext(dispatcherProvider.main) {
                    _state.value = result.toViewState()
                }
            }
        }
    }

    /** Re-download the Brand scripts (when entitled but unavailable), then re-evaluate the gate. */
    fun retryDownload() {
        scope.launch(dispatcherProvider.io) {
            withContext(dispatcherProvider.main) { _state.value = BrandEntitlementViewState.Loading }
            syncBrandScriptsInteractor(Interactor.None())
            getBrandGateStatusInteractor(Interactor.None()) { result ->
                withContext(dispatcherProvider.main) {
                    _state.value = result.toViewState()
                }
            }
        }
    }

    fun openPurchasePage() {
        applicationConfig.paywallUrl?.takeIf { it.isNotBlank() }?.let { Desktop.getDesktop().browse(URI(it)) }
    }

    fun logout() {
        scope.launch(dispatcherProvider.io) {
            logoutInteractor(Interactor.None())
        }
    }

    private fun SuspendableResult<BrandGateStatus, Exception>.toViewState(): BrandEntitlementViewState =
        when (this) {
            is SuspendableResult.Success -> {
                when (value) {
                    BrandGateStatus.Ready -> BrandEntitlementViewState.Entitled
                    BrandGateStatus.NeedsSubscription -> BrandEntitlementViewState.Locked
                    BrandGateStatus.ScriptsUnavailable -> BrandEntitlementViewState.Unavailable
                }
            }

            // Couldn't evaluate (e.g. transient network failure). Distinct from Locked so the
            // screen offers a retry rather than telling a subscriber they must buy.
            is SuspendableResult.Failure -> {
                BrandEntitlementViewState.Error
            }
        }
}

sealed class BrandEntitlementViewState {
    /** The gate is being evaluated. */
    data object Loading : BrandEntitlementViewState()

    /** Entitled and ready — render the app. */
    data object Entitled : BrandEntitlementViewState()

    /** A required subscription is missing — show the paywall. */
    data object Locked : BrandEntitlementViewState()

    /** Entitled, but the Brand scripts are not installed — offer a download retry. */
    data object Unavailable : BrandEntitlementViewState()

    /** The gate could not be evaluated — show a retry. */
    data object Error : BrandEntitlementViewState()
}
