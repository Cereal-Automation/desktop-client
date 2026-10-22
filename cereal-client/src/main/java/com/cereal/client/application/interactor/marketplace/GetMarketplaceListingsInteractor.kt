package com.cereal.client.application.interactor.marketplace

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptPublicIdentifier
import com.cereal.client.domain.provider.MarketplaceProvider

/**
 * Resolves the marketplace listing of each given installed package, keyed by public identifier.
 * Packages without a listing are absent from the result.
 *
 * The marketplace has no single-script lookup, so this pages through the catalogue until every
 * package is matched or the pages run out.
 * ponytail: full catalogue scan; swap for a batch-by-identifier endpoint if the catalogue grows large.
 */
class GetMarketplaceListingsInteractor(
    private val marketplaceProvider: MarketplaceProvider,
) : Interactor<Map<ScriptPublicIdentifier, MarketplaceScript>, GetMarketplaceListingsInteractor.Params>() {
    override suspend fun run(params: Params): Map<ScriptPublicIdentifier, MarketplaceScript> {
        val remaining = params.publicIdentifiers.toMutableSet()
        val listings = mutableMapOf<ScriptPublicIdentifier, MarketplaceScript>()
        var page = 1
        while (remaining.isNotEmpty()) {
            val result = marketplaceProvider.getMarketplaceScripts(page = page, perPage = PER_PAGE)
            result.items
                .filter { it.publicIdentifier in remaining }
                .forEach {
                    listings[it.publicIdentifier] = it
                    remaining -= it.publicIdentifier
                }
            if (result.currentPage >= result.lastPage) break
            page++
        }
        return listings
    }

    data class Params(
        val publicIdentifiers: Set<ScriptPublicIdentifier>,
    )

    private companion object {
        const val PER_PAGE = 50
    }
}
