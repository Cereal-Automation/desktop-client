package com.cereal.client.application.interactor.agent

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.CerealException
import com.cereal.client.domain.model.agent.AI_BROWSER_AGENT_PACKAGE
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.provider.MarketplaceProvider

/** The AI browser agent's marketplace listing (for Install and its maintenance flag). */
class GetAiBrowserAgentListingInteractor(
    private val marketplaceProvider: MarketplaceProvider,
) : Interactor<MarketplaceScript, Interactor.None>() {
    // ponytail: the API has no lookup by id, so this pages the catalog; add one if the catalog grows large.
    override suspend fun run(params: None): MarketplaceScript {
        var page = 1
        do {
            val result = marketplaceProvider.getMarketplaceScripts(page = page, perPage = PER_PAGE)
            result.items.firstOrNull { it.publicIdentifier == AI_BROWSER_AGENT_PACKAGE }?.let { return it }
            page++
        } while (page <= result.lastPage)
        throw CerealException("The AI browser agent isn't available on the marketplace.")
    }

    private companion object {
        const val PER_PAGE = 100
    }
}
