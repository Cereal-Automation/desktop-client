package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.marketplace.MarketplaceDirection
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.MarketplaceSort
import com.cereal.client.domain.model.marketplace.PaginatedResult
import com.cereal.client.domain.model.marketplace.ScriptSubscriptionResult
import com.cereal.client.domain.provider.MarketplaceProvider

/**
 * In-memory marketplace repository. Holds no built-in catalog: results come from the [catalog]
 * passed in (empty by default). The `mock` flavor injects a sample catalog through the DI module;
 * tests pass their own or none.
 */
class InMemoryMarketplaceProvider(
    private val catalog: List<MarketplaceScript> = emptyList(),
) : MarketplaceProvider {
    override suspend fun getMarketplaceScripts(
        search: String?,
        sort: MarketplaceSort,
        direction: MarketplaceDirection,
        community: Boolean?,
        isFree: Boolean?,
        page: Int,
        perPage: Int,
    ): PaginatedResult<MarketplaceScript> {
        val needle = search?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        val filtered =
            catalog.filter { script ->
                val matchesSearch =
                    needle == null ||
                        script.title.lowercase().contains(needle) ||
                        script.shortDescription?.lowercase()?.contains(needle) == true ||
                        script.developer
                            ?.name
                            ?.lowercase()
                            ?.contains(needle) == true ||
                        script.tags.any { it.lowercase().contains(needle) }
                val matchesCommunity = community == null || script.isCommunity == community
                val matchesFree = isFree == null || script.isFree == isFree
                matchesSearch && matchesCommunity && matchesFree
            }

        val fromIndex = ((page - 1) * perPage).coerceAtMost(filtered.size)
        val toIndex = (fromIndex + perPage).coerceAtMost(filtered.size)
        val pageItems = filtered.subList(fromIndex, toIndex)
        val lastPage = (filtered.size + perPage - 1) / perPage
        return PaginatedResult(
            items = pageItems,
            currentPage = page,
            lastPage = lastPage.coerceAtLeast(1),
            total = filtered.size,
            perPage = perPage,
        )
    }

    override suspend fun subscribeToScript(publicScriptId: String): ScriptSubscriptionResult = ScriptSubscriptionResult.Subscribed

    override suspend fun unsubscribeFromScript(publicScriptId: String) {
        // No-op in fake implementation.
    }
}
