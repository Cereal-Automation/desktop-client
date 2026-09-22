package com.cereal.client.infrastructure.data.datasource.network.marketplace

import com.cereal.client.infrastructure.data.datasource.network.exception.ApiException
import com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor.AuthorizationInterceptor
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.fail

/**
 * Error responses no longer reach the signature check (only 2xx is verified), so these assert the
 * diagnostics that make an edge-generated failure triageable once it surfaces as an [ApiException].
 */
class MarketplaceApiClientErrorDiagnosticsTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: MarketplaceApiClient

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client =
            MarketplaceApiClient(
                url = mockWebServer.url("/").toString(),
                version = "1.14.2",
                tokenDataSource =
                    object : AuthorizationInterceptor.TokenDataSource {
                        override fun getToken(): String = "test-token"
                    },
                verifySignature = false,
            )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /** [org.junit.jupiter.api.Assertions.assertThrows] takes a non-suspend lambda, so assert by hand. */
    private suspend fun assertApiException(block: suspend () -> Unit): ApiException =
        try {
            block()
            fail("Expected an ApiException to be thrown")
        } catch (e: ApiException) {
            e
        }

    @Test
    fun `an edge rate limit surfaces the status and CDN diagnostics instead of Unknown error`() =
        runTest {
            // A Cloudflare-style HTML block page: no JSON error body to read a message from.
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Content-Type", "text/html")
                    .setHeader("cf-ray", "9a1b2c3d4e5f6789-HEL")
                    .setHeader("cf-cache-status", "DYNAMIC")
                    .setBody("<html><title>429 Too Many Requests</title></html>"),
            )

            val exception = assertApiException { client.getFreeScripts() }

            assertEquals(429, exception.httpStatus)
            assertTrue(
                exception.message.contains("429"),
                "Expected the status in the message but was '${exception.message}'",
            )
            assertEquals("9a1b2c3d4e5f6789-HEL", exception.sentryContext()["cf-ray"])
            assertEquals("DYNAMIC", exception.sentryContext()["cf-cache-status"])
            assertEquals(429, exception.sentryContext()["http_status"])
            assertTrue(
                exception.sentryContext()["url"].toString().endsWith("api/script/free"),
                "Expected the failing endpoint in the context but was '${exception.sentryContext()["url"]}'",
            )
        }

    @Test
    fun `a JSON error body keeps the server message and still carries the status`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"message":"Server Error"}"""),
            )

            val exception = assertApiException { client.getFreeScripts() }

            assertEquals("Server Error", exception.message)
            assertEquals(500, exception.httpStatus)
            assertEquals(500, exception.sentryContext()["http_status"])
        }

    @Test
    fun `validation error contents are not reported to Sentry`() =
        runTest {
            // Validation messages echo user input, so only their count may be reported.
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(422)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"message":"Invalid data","errors":["The email cedric@example.com is taken."]}"""),
            )

            val exception = assertApiException { client.getFreeScripts() }

            assertEquals(listOf("The email cedric@example.com is taken."), exception.validationErrors)
            assertEquals(1, exception.sentryContext()["validation_error_count"])
            assertNull(
                exception.sentryContext().values.firstOrNull { it.toString().contains("cedric@example.com") },
                "Validation contents must not reach the Sentry context",
            )
        }
}
