package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.telemetry.GoalRunFinishReport
import com.cereal.client.domain.model.telemetry.GoalRunOutcome
import com.cereal.client.domain.model.telemetry.GoalRunStartReport
import com.cereal.client.infrastructure.data.datasource.network.marketplace.MarketplaceApiClient
import com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor.AuthorizationInterceptor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class GoalRunReportProviderImplTest {
    private val server = MockWebServer().apply { start() }

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun provider(token: String? = null) =
        GoalRunReportProviderImpl(
            MarketplaceApiClient(
                url = server.url("/").toString(),
                version = "2.0.0",
                tokenDataSource =
                    object : AuthorizationInterceptor.TokenDataSource {
                        override fun getToken() = token
                    },
                verifySignature = false,
            ),
        )

    private val start =
        GoalRunStartReport(
            runId = "run-1",
            installId = "install-1",
            goal = "buy the new Jordans",
            startUrl = "https://shop.example.com/jordans",
            runMode = "Once",
            variantId = null,
            agentVersion = 7,
            clientVersion = "2.0.0",
            llmProvider = "ANTHROPIC",
            llmModel = "claude-test",
            startedAt = Instant.parse("2026-11-01T10:00:00Z"),
        )

    @Test
    fun `a start is posted as json with the signed-in user's token`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(202))

            provider(token = "user-token").reportStart(start)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/telemetry/goal-runs", request.path)
            assertEquals("Bearer user-token", request.getHeader("Authorization"))
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(
                JsonObject(
                    mapOf(
                        "runId" to JsonPrimitive("run-1"),
                        "installId" to JsonPrimitive("install-1"),
                        "goal" to JsonPrimitive("buy the new Jordans"),
                        "startUrl" to JsonPrimitive("https://shop.example.com/jordans"),
                        "runMode" to JsonPrimitive("Once"),
                        "variantId" to JsonPrimitive(null as String?),
                        "agentVersion" to JsonPrimitive(7),
                        "clientVersion" to JsonPrimitive("2.0.0"),
                        "llmProvider" to JsonPrimitive("ANTHROPIC"),
                        "llmModel" to JsonPrimitive("claude-test"),
                        "startedAt" to JsonPrimitive("2026-11-01T10:00:00Z"),
                    ),
                ),
                body,
            )
        }

    @Test
    fun `a finish is patched onto its run, with a guest's token`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(202))

            provider(token = "guest-token").reportFinish(GoalRunFinishReport("run-1", GoalRunOutcome.Stopped, BigDecimal("0.42"), 90))

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/telemetry/goal-runs/run-1", request.path)
            assertEquals("Bearer guest-token", request.getHeader("Authorization"))
            assertEquals("""{"outcome":"stopped","cost":0.42,"durationSeconds":90}""", request.body.readUtf8())
        }

    @Test
    fun `without a token no authorization header is sent`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(202))

            provider().reportStart(start)

            assertNull(server.takeRequest().getHeader("Authorization"))
        }

    @Test
    fun `a server error or a dropped connection doesn't throw`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            provider().reportStart(start)
            provider().reportFinish(GoalRunFinishReport("run-1", GoalRunOutcome.Failed, null, 1))
        }
}
