package com.cereal.client.infrastructure.data.datasource.network.marketplace

import com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor.AuthorizationInterceptor
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.GuestLoginRequestBody
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class MarketplaceApiClientUnitTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiClient: MarketplaceApiClient
    private val tokenDataSource = mockk<AuthorizationInterceptor.TokenDataSource>()

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        every { tokenDataSource.getToken() } returns "mock-token"

        apiClient =
            MarketplaceApiClient(
                url = mockWebServer.url("/").toString(),
                version = TEST_VERSION,
                tokenDataSource = tokenDataSource,
                enableLogging = true,
                verifySignature = false, // Disable signature verification for unit tests
            )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `authenticateGuest sends correct request and returns valid response`() =
        runTest {
            // Given
            val deviceId = UUID.randomUUID().toString()
            val requestBody = GuestLoginRequestBody(deviceId)
            val expectedToken = "guest-token-123"
            val jsonResponse =
                """
                {
                    "token": "$expectedToken",
                    "user": {
                        "id": "user-id",
                        "username": "guest-user",
                        "name": "Guest User",
                        "email": "guest@example.com",
                        "key": "user-key",
                        "is_guest": true
                    }
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(jsonResponse),
            )

            // When
            val response = apiClient.authenticateGuest(requestBody)

            // Then
            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("/api/auth/guest-token", recordedRequest.path)
            assertEquals("POST", recordedRequest.method)
            assertEquals(expectedToken, response.token)
        }

    @Test
    fun `requests carry the client version header`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("[]"),
            )

            apiClient.getFreeScripts()

            val recordedRequest = mockWebServer.takeRequest()
            assertEquals(TEST_VERSION, recordedRequest.getHeader("X-Client-Version"))
        }

    private companion object {
        const val TEST_VERSION = "1.2.3"
    }
}
