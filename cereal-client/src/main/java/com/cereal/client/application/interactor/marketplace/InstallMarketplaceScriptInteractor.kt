package com.cereal.client.application.interactor.marketplace

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.ScriptSubscriptionResult
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.CheckoutProvider
import com.cereal.client.domain.provider.MarketplaceProvider
import com.cereal.client.domain.provider.ScriptInstallProvider

class InstallMarketplaceScriptInteractor(
    private val scriptInstallProvider: ScriptInstallProvider,
    private val marketplaceRepository: MarketplaceProvider,
    private val checkoutRepository: CheckoutProvider,
) : Interactor<ScriptPackage, InstallMarketplaceScriptInteractor.Params>() {
    override suspend fun run(params: Params): ScriptPackage {
        val release =
            params.script.latestRelease
                ?: error("Script '${params.script.title}' has no available release to install.")

        val subscriptionResult = marketplaceRepository.subscribeToScript(params.script.publicIdentifier)

        if (subscriptionResult is ScriptSubscriptionResult.CheckoutRequired) {
            checkoutRepository.awaitCheckout(subscriptionResult.checkoutUrl)
        }

        return scriptInstallProvider.installScript(
            packageName = params.script.publicIdentifier,
            release = release,
        )
    }

    data class Params(
        val script: MarketplaceScript,
    )
}
