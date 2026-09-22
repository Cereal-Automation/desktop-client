package com.cereal.client.infrastructure.provider

import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.exception.UserNotAuthenticatedException
import com.cereal.client.domain.model.exception.PaidSubscriptionActiveException
import com.cereal.client.domain.model.marketplace.MarketplaceDirection
import com.cereal.client.domain.model.marketplace.MarketplaceSort
import com.cereal.client.domain.model.marketplace.ScriptSubscriptionResult
import com.cereal.client.fixtures.FakeMarketplaceDataSource
import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import com.cereal.client.infrastructure.data.datasource.network.exception.ApiException
import com.cereal.client.infrastructure.data.datasource.network.exception.AuthenticationException
import com.cereal.client.infrastructure.data.datasource.network.exception.PaidSubscriptionException
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.SubscribeScriptResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.SubscribeStatus
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.MarketplaceScript
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.PaginatedResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Release as ReleaseResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.ScriptOwner as ScriptOwnerResponse

class MarketplaceProviderImplTest {
    private val dataSource = FakeMarketplaceDataSource()
    private lateinit var repository: MarketplaceProviderImpl

    @BeforeEach
    fun setUp() {
        repository = MarketplaceProviderImpl(dataSource)
    }

    private fun script(id: String = "id-1"): MarketplaceScript =
        MarketplaceScript(
            id = id,
            publicIdentifier = "public-$id",
            title = "Title $id",
        )

    private suspend fun fetch(
        sort: MarketplaceSort = MarketplaceSort.TITLE,
        direction: MarketplaceDirection = MarketplaceDirection.ASC,
    ) = repository.getMarketplaceScripts(
        search = null,
        sort = sort,
        direction = direction,
        community = null,
        isFree = null,
        page = 1,
        perPage = 20,
    )

    @Test
    fun `getMarketplaceScripts maps every PaginatedResponse field into PaginatedResult`() =
        runTest {
            dataSource.marketplaceScripts =
                PaginatedResponse(
                    data = listOf(script("a"), script("b")),
                    currentPage = 2,
                    lastPage = 5,
                    total = 100,
                    perPage = 20,
                )

            val result =
                repository.getMarketplaceScripts(
                    search = "query",
                    sort = MarketplaceSort.TITLE,
                    direction = MarketplaceDirection.ASC,
                    community = true,
                    isFree = false,
                    page = 2,
                    perPage = 20,
                )

            assertEquals(listOf("a", "b"), result.items.map { it.id })
            assertEquals(listOf("public-a", "public-b"), result.items.map { it.publicIdentifier })
            assertEquals(2, result.currentPage)
            assertEquals(5, result.lastPage)
            assertEquals(100, result.total)
            assertEquals(20, result.perPage)
        }

    @Test
    fun `getMarketplaceScripts maps a fully-populated DTO into its domain counterpart`() =
        runTest {
            val dto =
                MarketplaceScript(
                    id = "1",
                    publicIdentifier = "com.example",
                    title = "Example",
                    shortDescription = "Short",
                    isFree = false,
                    averageRating = 4.7,
                    latestRelease = ReleaseResponse(versionName = "1.2", versionCode = 12L, releaseNotes = "notes"),
                    developer = ScriptOwnerResponse(name = "Dev", avatarUrl = "https://a", verified = true),
                    tags = listOf("a", "b"),
                )
            dataSource.marketplaceScripts =
                PaginatedResponse(data = listOf(dto), currentPage = 1, lastPage = 1, total = 1, perPage = 20)

            val mapped = repository.getMarketplaceScripts().items.single()

            assertEquals("com.example", mapped.publicIdentifier)
            assertEquals("1.2", mapped.latestRelease?.versionName)
            assertEquals(12L, mapped.latestRelease?.versionCode)
            assertEquals("Dev", mapped.developer?.name)
            assertEquals(true, mapped.developer?.verified)
            assertEquals(listOf("a", "b"), mapped.tags)
        }

    @Test
    fun `getMarketplaceScripts maps the result for every sort value`() =
        runTest {
            dataSource.marketplaceScripts =
                PaginatedResponse(data = listOf(script()), currentPage = 1, lastPage = 1, total = 1, perPage = 20)

            for (sort in MarketplaceSort.entries) {
                assertEquals(1, fetch(sort = sort).total)
            }
        }

    @Test
    fun `getMarketplaceScripts maps the result for every direction value`() =
        runTest {
            dataSource.marketplaceScripts =
                PaginatedResponse(data = listOf(script()), currentPage = 1, lastPage = 1, total = 1, perPage = 20)

            for (direction in MarketplaceDirection.entries) {
                assertEquals(1, fetch(direction = direction).total)
            }
        }

    @Test
    fun `subscribeToScript maps SUBSCRIBED to Subscribed and records the identifier`() =
        runTest {
            dataSource.subscribeScriptResponse = SubscribeScriptResponse(status = SubscribeStatus.SUBSCRIBED)

            val result = repository.subscribeToScript("pub-1")

            assertEquals(ScriptSubscriptionResult.Subscribed, result)
            assertTrue(dataSource.subscribedPackageIds.contains("pub-1"))
        }

    @Test
    fun `subscribeToScript maps ALREADY_SUBSCRIBED to AlreadySubscribed`() =
        runTest {
            dataSource.subscribeScriptResponse = SubscribeScriptResponse(status = SubscribeStatus.ALREADY_SUBSCRIBED)

            assertEquals(ScriptSubscriptionResult.AlreadySubscribed, repository.subscribeToScript("pub-1"))
        }

    @Test
    fun `subscribeToScript maps CHECKOUT_INITIATED with a url to CheckoutRequired`() =
        runTest {
            dataSource.subscribeScriptResponse =
                SubscribeScriptResponse(
                    status = SubscribeStatus.CHECKOUT_INITIATED,
                    checkoutUrl = "https://checkout.example.com/pay",
                )

            val result = repository.subscribeToScript("pub-1")

            assertEquals(ScriptSubscriptionResult.CheckoutRequired("https://checkout.example.com/pay"), result)
        }

    @Test
    fun `subscribeToScript throws when CHECKOUT_INITIATED is missing the checkout url`() =
        runTest {
            dataSource.subscribeScriptResponse =
                SubscribeScriptResponse(status = SubscribeStatus.CHECKOUT_INITIATED, checkoutUrl = null)

            assertThrows<IllegalStateException> {
                repository.subscribeToScript("pub-1")
            }
        }

    @Test
    fun `unsubscribeFromScript records the public identifier on the data source`() =
        runTest {
            repository.unsubscribeFromScript("pub-2")

            assertTrue(dataSource.unsubscribedPackageIds.contains("pub-2"))
        }

    @Test
    fun `translates AuthenticationException into a domain exception at the boundary`() =
        runTest {
            val throwingDataSource = mockk<MarketplaceDataSource>()
            coEvery { throwingDataSource.subscribeToScript(any()) } throws AuthenticationException()

            assertThrows<UserNotAuthenticatedException> {
                MarketplaceProviderImpl(throwingDataSource).subscribeToScript("pub-1")
            }
        }

    @Test
    fun `translates PaidSubscriptionException into a domain exception at the boundary`() =
        runTest {
            val throwingDataSource = mockk<MarketplaceDataSource>(relaxed = true)
            coEvery { throwingDataSource.unsubscribeFromScript(any()) } throws PaidSubscriptionException()

            assertThrows<PaidSubscriptionActiveException> {
                MarketplaceProviderImpl(throwingDataSource).unsubscribeFromScript("pub-1")
            }
        }

    @Test
    fun `translates ApiException into a CerealException at the boundary`() =
        runTest {
            val throwingDataSource = mockk<MarketplaceDataSource>()
            coEvery { throwingDataSource.getMarketplaceScripts(any(), any(), any(), any(), any(), any(), any()) } throws
                ApiException("boom")

            assertThrows<CerealException> {
                MarketplaceProviderImpl(throwingDataSource).getMarketplaceScripts()
            }
        }
}
