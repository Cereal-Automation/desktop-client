package com.cereal.client.infrastructure.data.notification.discord

import com.cereal.client.infrastructure.data.notification.discord.serializable.SerializableDiscordMessage
import com.cereal.sdk.component.notification.discord.model.DiscordMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract test for the Discord webhook integration. Exercises [DiscordHttpClient] against a real
 * [MockWebServer] so the wire contract with Discord's webhook endpoint (HTTP method, path, payload
 * shape, and failure handling) is pinned without ever contacting discord.com.
 */
class DiscordHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DiscordHttpClient
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = DiscordHttpClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun webhookUrl(path: String = "/api/webhooks/123/abc") = server.url(path).toString()

    @Test
    fun `message posts the serialized payload to the webhook url as json`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            client.message(
                url = webhookUrl(),
                discordMessage = DiscordMessage(username = "Cereal", content = "Task finished"),
            )

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/webhooks/123/abc", request.path)
            assertTrue(
                request.getHeader("Content-Type")?.startsWith("application/json") == true,
                "Discord webhook payload must be sent as application/json",
            )

            val sent = json.decodeFromString<SerializableDiscordMessage>(request.body.readUtf8())
            assertEquals("Cereal", sent.username)
            assertEquals("Task finished", sent.content)
        }

    @Test
    fun `message sends exactly one request on a successful response`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            client.message(
                url = webhookUrl(),
                discordMessage = DiscordMessage(content = "hi"),
            )

            assertEquals(1, server.requestCount)
        }

    @Test
    fun `message retries up to maxAttempts when the connection keeps failing`() =
        runTest {
            repeat(3) {
                server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            }

            // Must complete normally: delivery failures are logged and retried, never thrown.
            client.message(
                url = webhookUrl(),
                discordMessage = DiscordMessage(content = "hi"),
                maxAttempts = 3,
            )

            assertEquals(3, server.requestCount)
        }

    @Test
    fun `message completes without throwing when discord reports a rate limit`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setBody("""{"message":"You are being rate limited","retry_after":1}"""),
            )

            // Should complete normally; a rate-limit response is observed, not propagated as an error.
            client.message(
                url = webhookUrl(),
                discordMessage = DiscordMessage(content = "hi"),
            )

            assertEquals(1, server.requestCount)
        }
}
