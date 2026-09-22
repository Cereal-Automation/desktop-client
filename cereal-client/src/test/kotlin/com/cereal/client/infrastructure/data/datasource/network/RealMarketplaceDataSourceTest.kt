package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.infrastructure.data.datasource.network.marketplace.MarketplaceApiClient
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.GuestLoginRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.LoginRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.RegisterRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.CheckScriptLicenseResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.LoginResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.ScriptDownloadLinkResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.SubscribeScriptResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.MarketplaceScript
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.PaginatedResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Script
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Subscription
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * [RealMarketplaceDataSource] is a thin pass-through to [MarketplaceApiClient]. Each test confirms
 * the call is forwarded with the exact arguments and that the api client's result is returned
 * unchanged — the only behaviour this adapter owns.
 */
class RealMarketplaceDataSourceTest {
    private val apiClient = mockk<MarketplaceApiClient>()
    private lateinit var dataSource: RealMarketplaceDataSource

    @BeforeEach
    fun setUp() {
        dataSource = RealMarketplaceDataSource(apiClient)
    }

    @Test
    fun `authenticate delegates to the api client`() =
        runTest {
            val body = mockk<LoginRequestBody>()
            val expected = mockk<LoginResponse>()
            coEvery { apiClient.authenticate(body) } returns expected

            assertSame(expected, dataSource.authenticate(body))
            coVerify(exactly = 1) { apiClient.authenticate(body) }
        }

    @Test
    fun `authenticateGuest delegates to the api client`() =
        runTest {
            val body = mockk<GuestLoginRequestBody>()
            val expected = mockk<LoginResponse>()
            coEvery { apiClient.authenticateGuest(body) } returns expected

            assertSame(expected, dataSource.authenticateGuest(body))
            coVerify(exactly = 1) { apiClient.authenticateGuest(body) }
        }

    @Test
    fun `register delegates to the api client`() =
        runTest {
            val body = mockk<RegisterRequestBody>()
            val expected = mockk<LoginResponse>()
            coEvery { apiClient.register(body) } returns expected

            assertSame(expected, dataSource.register(body))
            coVerify(exactly = 1) { apiClient.register(body) }
        }

    @Test
    fun `getMySubscriptions delegates to the api client`() =
        runTest {
            val expected = listOf(mockk<Subscription>())
            coEvery { apiClient.getMySubscriptions() } returns expected

            assertEquals(expected, dataSource.getMySubscriptions())
            coVerify(exactly = 1) { apiClient.getMySubscriptions() }
        }

    @Test
    fun `getMyTeamScripts delegates to the api client`() =
        runTest {
            val expected = listOf(mockk<Script>())
            coEvery { apiClient.getMyTeamScripts() } returns expected

            assertEquals(expected, dataSource.getMyTeamScripts())
            coVerify(exactly = 1) { apiClient.getMyTeamScripts() }
        }

    @Test
    fun `getScriptDownloadLink delegates with id and version code`() =
        runTest {
            val expected = mockk<ScriptDownloadLinkResponse>()
            coEvery { apiClient.getScriptDownloadLink("pkg", 5L) } returns expected

            assertSame(expected, dataSource.getScriptDownloadLink("pkg", 5L))
            coVerify(exactly = 1) { apiClient.getScriptDownloadLink("pkg", 5L) }
        }

    @Test
    fun `getFreeScripts delegates to the api client`() =
        runTest {
            val expected = listOf(mockk<Script>())
            coEvery { apiClient.getFreeScripts() } returns expected

            assertEquals(expected, dataSource.getFreeScripts())
            coVerify(exactly = 1) { apiClient.getFreeScripts() }
        }

    @Test
    fun `verifyLicense delegates to the api client`() =
        runTest {
            val expected = mockk<CheckScriptLicenseResponse>()
            coEvery { apiClient.verifyLicense("pkg") } returns expected

            assertSame(expected, dataSource.verifyLicense("pkg"))
            coVerify(exactly = 1) { apiClient.verifyLicense("pkg") }
        }

    @Test
    fun `checkScriptLicense delegates with id and salt`() =
        runTest {
            val expected = mockk<Response>()
            coEvery { apiClient.checkScriptLicense("pkg", "salt") } returns expected

            assertSame(expected, dataSource.checkScriptLicense("pkg", "salt"))
            coVerify(exactly = 1) { apiClient.checkScriptLicense("pkg", "salt") }
        }

    @Test
    fun `downloadScript delegates with id and version code`() =
        runTest {
            val expected: InputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3))
            coEvery { apiClient.downloadScript("pkg", 9L) } returns expected

            assertSame(expected, dataSource.downloadScript("pkg", 9L))
            coVerify(exactly = 1) { apiClient.downloadScript("pkg", 9L) }
        }

    @Test
    fun `getAuthenticatedUser delegates to the api client`() =
        runTest {
            val expected = mockk<User>()
            coEvery { apiClient.getAuthenticatedUser() } returns expected

            assertSame(expected, dataSource.getAuthenticatedUser())
            coVerify(exactly = 1) { apiClient.getAuthenticatedUser() }
        }

    @Test
    fun `subscribeToScript delegates to the api client`() =
        runTest {
            val expected = mockk<SubscribeScriptResponse>()
            coEvery { apiClient.subscribeToScript("pkg") } returns expected

            assertSame(expected, dataSource.subscribeToScript("pkg"))
            coVerify(exactly = 1) { apiClient.subscribeToScript("pkg") }
        }

    @Test
    fun `unsubscribeFromScript delegates to the api client`() =
        runTest {
            coEvery { apiClient.unsubscribeFromScript("pkg") } returns Unit

            dataSource.unsubscribeFromScript("pkg")

            coVerify(exactly = 1) { apiClient.unsubscribeFromScript("pkg") }
        }

    @Test
    fun `getMarketplaceScripts forwards every paging and filter argument`() =
        runTest {
            val expected = mockk<PaginatedResponse<MarketplaceScript>>()
            coEvery {
                apiClient.getMarketplaceScripts("q", "title", "desc", true, false, 2, 50)
            } returns expected

            val result =
                dataSource.getMarketplaceScripts(
                    search = "q",
                    sort = "title",
                    direction = "desc",
                    community = true,
                    isFree = false,
                    page = 2,
                    perPage = 50,
                )

            assertSame(expected, result)
            coVerify(exactly = 1) {
                apiClient.getMarketplaceScripts("q", "title", "desc", true, false, 2, 50)
            }
        }

    @Test
    fun `forgotPassword delegates to the api client`() =
        runTest {
            coEvery { apiClient.forgotPassword("user@example.com") } returns Unit

            dataSource.forgotPassword("user@example.com")

            coVerify(exactly = 1) { apiClient.forgotPassword("user@example.com") }
        }
}
