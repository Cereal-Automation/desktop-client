package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Script
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Subscription as ApiSubscription

class RealSubscriptionDataSourceTest {
    private val marketplaceDataSource = mockk<MarketplaceDataSource>()
    private val userSession = mockk<UserSession>()
    private lateinit var dataSource: RealSubscriptionDataSource

    @BeforeEach
    fun setUp() {
        dataSource = RealSubscriptionDataSource(marketplaceDataSource, userSession)
    }

    @Test
    fun `getSubscriptions should return mapped subscriptions from API`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            every { user.id } returns "user-1"
            coEvery { marketplaceDataSource.getMySubscriptions() } returns
                listOf(
                    ApiSubscription(
                        id = "sub-1",
                        script =
                            Script(
                                publicIdentifier = "com.example.script",
                                title = "Test Script",
                                latestRelease = null,
                                latestDraftRelease = null,
                                shortDescription = null,
                                price = null,
                                supportUrl = "https://example.com/support",
                            ),
                    ),
                )

            val result = dataSource.getSubscriptions()

            assertEquals(1, result.size)
            assertEquals("sub-1", result[0].id)
            assertEquals("com.example.script", result[0].entitlement.publicIdentifier)
            assertEquals("https://example.com/support", result[0].entitlement.supportUrl)
        }

    @Test
    fun `getSubscriptions should return cached result on second call`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            every { user.id } returns "user-1"
            coEvery { marketplaceDataSource.getMySubscriptions() } returns emptyList()

            dataSource.getSubscriptions()
            dataSource.getSubscriptions()

            coVerify(exactly = 1) { marketplaceDataSource.getMySubscriptions() }
        }

    @Test
    fun `invalidateCache should force fresh API call on next getSubscriptions`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            every { user.id } returns "user-1"
            coEvery { marketplaceDataSource.getMySubscriptions() } returns emptyList()

            dataSource.getSubscriptions()
            dataSource.invalidateCache()
            dataSource.getSubscriptions()

            coVerify(exactly = 2) { marketplaceDataSource.getMySubscriptions() }
        }

    @Test
    fun `getSubscriptions should map null supportUrl correctly`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            every { user.id } returns "user-1"
            coEvery { marketplaceDataSource.getMySubscriptions() } returns
                listOf(
                    ApiSubscription(
                        id = "sub-2",
                        script =
                            Script(
                                publicIdentifier = "com.example.noscript",
                                title = "No Support Script",
                                latestRelease = null,
                                latestDraftRelease = null,
                                shortDescription = null,
                                price = null,
                                supportUrl = null,
                            ),
                    ),
                )

            val result = dataSource.getSubscriptions()

            assertNull(result[0].entitlement.supportUrl)
        }

    @Test
    fun `getSubscriptions maps the script capacity pair into ScriptCapacity`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            every { user.id } returns "user-1"
            coEvery { marketplaceDataSource.getMySubscriptions() } returns
                listOf(
                    ApiSubscription(
                        id = "sub-3",
                        script =
                            Script(
                                publicIdentifier = "com.example.tiered",
                                title = "Tiered Script",
                                latestRelease = null,
                                latestDraftRelease = null,
                                shortDescription = null,
                                price = null,
                                capacity = 500,
                                capacityUnit = "records",
                            ),
                    ),
                )

            val result = dataSource.getSubscriptions()

            assertEquals(ScriptCapacity.Limited(500, "records"), result[0].entitlement.capacity)
        }

    @Test
    fun `getSubscriptions maps absent capacity fields into ScriptCapacity None`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            every { user.id } returns "user-1"
            coEvery { marketplaceDataSource.getMySubscriptions() } returns
                listOf(
                    ApiSubscription(
                        id = "sub-4",
                        script =
                            Script(
                                publicIdentifier = "com.example.plain",
                                title = "Plain Script",
                                latestRelease = null,
                                latestDraftRelease = null,
                                shortDescription = null,
                                price = null,
                            ),
                    ),
                )

            val result = dataSource.getSubscriptions()

            assertEquals(ScriptCapacity.None, result[0].entitlement.capacity)
        }
}
