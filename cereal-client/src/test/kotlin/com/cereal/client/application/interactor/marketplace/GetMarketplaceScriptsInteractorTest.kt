package com.cereal.client.application.interactor.marketplace

import com.cereal.client.domain.model.marketplace.MarketplaceDirection
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.MarketplaceSort
import com.cereal.client.infrastructure.provider.inmemory.InMemoryMarketplaceProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the interactor against the real in-memory marketplace repository seeded with a small
 * deterministic catalog, asserting on the returned [PaginatedResult] rather than verifying mock calls.
 */
class GetMarketplaceScriptsInteractorTest {
    private val catalog =
        listOf(
            script("com.free.sneaker", "Sneaker Bot", isFree = true, isCommunity = false, tags = listOf("Sneakers")),
            script("com.paid.shop", "Shop Sniper", isFree = false, isCommunity = false, tags = listOf("E-commerce")),
            script("com.community.food", "Food Alerts", isFree = true, isCommunity = true, tags = listOf("Food")),
        )

    private val interactor = GetMarketplaceScriptsInteractor(InMemoryMarketplaceProvider(catalog))

    @Test
    fun `run returns the full catalog page with default params`() =
        runTest {
            val result = interactor.run(GetMarketplaceScriptsInteractor.Params())

            assertEquals(1, result.currentPage)
            assertEquals(20, result.perPage)
            assertEquals(3, result.total)
            assertEquals(3, result.items.size)
        }

    @Test
    fun `run forwards the search query and returns only matching scripts`() =
        runTest {
            val result = interactor.run(GetMarketplaceScriptsInteractor.Params(search = "sneaker"))

            assertEquals(listOf("com.free.sneaker"), result.items.map { it.publicIdentifier })
        }

    @Test
    fun `run forwards sort and direction and still returns results`() =
        runTest {
            val result =
                interactor.run(
                    GetMarketplaceScriptsInteractor.Params(
                        sort = MarketplaceSort.PRICE,
                        direction = MarketplaceDirection.DESC,
                    ),
                )

            assertEquals(3, result.items.size)
        }

    @Test
    fun `run forwards the isFree filter to the repository`() =
        runTest {
            val result = interactor.run(GetMarketplaceScriptsInteractor.Params(isFree = true))

            assertTrue(result.items.isNotEmpty())
            assertTrue(result.items.all { it.isFree })
            assertEquals(setOf("com.free.sneaker", "com.community.food"), result.items.map { it.publicIdentifier }.toSet())
        }

    @Test
    fun `run forwards the community filter to the repository`() =
        runTest {
            val result = interactor.run(GetMarketplaceScriptsInteractor.Params(community = true))

            assertTrue(result.items.isNotEmpty())
            assertTrue(result.items.all { it.isCommunity })
            assertEquals(listOf("com.community.food"), result.items.map { it.publicIdentifier })
        }

    @Test
    fun `run returns scripts identified by their public identifier`() =
        runTest {
            val result = interactor.run(GetMarketplaceScriptsInteractor.Params())

            assertEquals(
                listOf("com.free.sneaker", "com.paid.shop", "com.community.food"),
                result.items.map { it.publicIdentifier },
            )
        }

    @Test
    fun `run forwards page and perPage to the repository`() =
        runTest {
            val result = interactor.run(GetMarketplaceScriptsInteractor.Params(page = 1, perPage = 2))

            assertEquals(1, result.currentPage)
            assertEquals(2, result.perPage)
            assertEquals(3, result.total)
            assertEquals(2, result.items.size)
            assertEquals(2, result.lastPage)
        }

    private fun script(
        publicIdentifier: String,
        title: String,
        isFree: Boolean,
        isCommunity: Boolean,
        tags: List<String>,
    ) = MarketplaceScript(
        id = publicIdentifier,
        publicIdentifier = publicIdentifier,
        title = title,
        isFree = isFree,
        isCommunity = isCommunity,
        tags = tags,
    )
}
