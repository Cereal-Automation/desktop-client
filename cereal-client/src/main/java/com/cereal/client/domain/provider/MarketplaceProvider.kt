package com.cereal.client.domain.provider

import com.cereal.client.domain.model.marketplace.MarketplaceDirection
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.MarketplaceSort
import com.cereal.client.domain.model.marketplace.PaginatedResult
import com.cereal.client.domain.model.marketplace.ScriptSubscriptionResult

interface MarketplaceProvider {
    /**
     * Retrieves marketplace scripts with optional search, sorting, filtering, and pagination.
     * @param search Optional search query to filter scripts by title or tag.
     * @param sort The field to sort by.
     * @param direction The sort direction.
     * @param isFree Optional filter for free or paid scripts.
     * @param page The page number to retrieve (1-based).
     * @param perPage The number of scripts per page.
     * @return A [PaginatedResult] of [MarketplaceScript]s.
     */
    suspend fun getMarketplaceScripts(
        search: String? = null,
        sort: MarketplaceSort = MarketplaceSort.TITLE,
        direction: MarketplaceDirection = MarketplaceDirection.ASC,
        community: Boolean? = null,
        isFree: Boolean? = null,
        page: Int = 1,
        perPage: Int = 20,
    ): PaginatedResult<MarketplaceScript>

    /**
     * Subscribes the authenticated user to a marketplace script.
     * Returns a [ScriptSubscriptionResult] describing the outcome, which for paid scripts carries the
     * checkout URL the user must complete before the subscription becomes active.
     */
    suspend fun subscribeToScript(publicScriptId: String): ScriptSubscriptionResult

    /**
     * Unsubscribes the authenticated user from a marketplace script.
     * Idempotent — does not throw if there is no active subscription.
     */
    suspend fun unsubscribeFromScript(publicScriptId: String)
}
