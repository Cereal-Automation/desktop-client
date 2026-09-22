package com.cereal.client.application.interactor.marketplace

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.marketplace.MarketplaceDirection
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.MarketplaceSort
import com.cereal.client.domain.model.marketplace.PaginatedResult
import com.cereal.client.domain.provider.MarketplaceProvider

class GetMarketplaceScriptsInteractor(
    private val marketplaceRepository: MarketplaceProvider,
) : Interactor<PaginatedResult<MarketplaceScript>, GetMarketplaceScriptsInteractor.Params>() {
    data class Params(
        val search: String? = null,
        val sort: MarketplaceSort = MarketplaceSort.TITLE,
        val direction: MarketplaceDirection = MarketplaceDirection.ASC,
        val community: Boolean? = null,
        val isFree: Boolean? = null,
        val page: Int = 1,
        val perPage: Int = 20,
    )

    override suspend fun run(params: Params): PaginatedResult<MarketplaceScript> =
        marketplaceRepository.getMarketplaceScripts(
            search = params.search,
            sort = params.sort,
            direction = params.direction,
            community = params.community,
            isFree = params.isFree,
            page = params.page,
            perPage = params.perPage,
        )
}
