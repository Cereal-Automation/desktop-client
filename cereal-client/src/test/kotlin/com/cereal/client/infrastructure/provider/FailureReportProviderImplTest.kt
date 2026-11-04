package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.diagnostics.FailureReport
import com.cereal.client.domain.provider.FailureReportProvider.Result
import com.cereal.client.infrastructure.data.datasource.network.marketplace.MarketplaceApiClient
import com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor.AuthorizationInterceptor
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FailureReportProviderImplTest {
    private val server = MockWebServer().apply { start() }

    @AfterEach
    fun tearDown() = server.shutdown()

    private val provider =
        FailureReportProviderImpl(
            MarketplaceApiClient(
                url = server.url("/").toString(),
                version = "2.0.0",
                tokenDataSource =
                    object : AuthorizationInterceptor.TokenDataSource {
                        override fun getToken() = "user-token"
                    },
                verifySignature = false,
            ),
            clientVersion = "2.0.0",
        )

    private fun report(
        learnedTrace: ByteArray? = null,
        outcome: String = "crash",
        note: String? = null,
    ) = FailureReport("run-1", "check-3", outcome, note, "own".toByteArray(), learnedTrace)

    private fun presigned(learned: Boolean = false) =
        MockResponse().setBody(
            """{"traceUrl":"${server.url("/spaces/trace")}"""" +
                (if (learned) ""","learnedTraceUrl":"${server.url("/spaces/learned")}"""" else "") + "}",
        )

    @Test
    fun `presigns, puts the trace to its url and registers the report`() =
        runTest {
            server.enqueue(presigned())
            server.enqueue(MockResponse())
            server.enqueue(MockResponse().setResponseCode(201))

            val result = provider.upload(report()) { true }

            assertEquals(Result.Registered, result)
            val presign = server.takeRequest()
            assertEquals("POST", presign.method)
            assertEquals("/api/telemetry/goal-runs/run-1/failure-reports/check-3/uploads", presign.path)
            assertEquals("Bearer user-token", presign.getHeader("Authorization"))
            assertEquals("""{"learnedTrace":false}""", presign.body.readUtf8())
            val put = server.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("/spaces/trace", put.path)
            assertNull(put.getHeader("Authorization"))
            assertEquals("own", put.body.readUtf8())
            val register = server.takeRequest()
            assertEquals("PUT", register.method)
            assertEquals("/api/telemetry/goal-runs/run-1/failure-reports/check-3", register.path)
            assertEquals("""{"outcome":"crash","note":null,"clientVersion":"2.0.0"}""", register.body.readUtf8())
        }

    @Test
    fun `a learned trace goes to its own presigned url, a flag carries its note`() =
        runTest {
            server.enqueue(presigned(learned = true))
            server.enqueue(MockResponse())
            server.enqueue(MockResponse())
            server.enqueue(MockResponse())

            provider.upload(report(learnedTrace = "learned".toByteArray(), outcome = "flagged", note = "It was sold out")) { true }

            assertEquals("""{"learnedTrace":true}""", server.takeRequest().body.readUtf8())
            val puts = listOf(server.takeRequest(), server.takeRequest()).associate { it.path to it.body.readUtf8() }
            assertEquals(mapOf("/spaces/trace" to "own", "/spaces/learned" to "learned"), puts)
            assertEquals("""{"outcome":"flagged","note":"It was sold out","clientVersion":"2.0.0"}""", server.takeRequest().body.readUtf8())
        }

    @ParameterizedTest
    @ValueSource(ints = [401, 429, 500, 503])
    fun `a 401, a 429 or a 5xx is retried`(status: Int) =
        runTest {
            server.enqueue(MockResponse().setResponseCode(status))

            assertEquals(Result.Retry, provider.upload(report()) { true })
        }

    @ParameterizedTest
    @ValueSource(ints = [400, 403, 404, 413])
    fun `a 404 or another 4xx drops the report`(status: Int) =
        runTest {
            server.enqueue(MockResponse().setResponseCode(status))

            assertEquals(Result.Drop, provider.upload(report()) { true })
        }

    @Test
    fun `a presign without the learned trace's url is retried, never registered without it`() =
        runTest {
            server.enqueue(presigned(learned = false))
            server.enqueue(MockResponse())

            assertEquals(Result.Retry, provider.upload(report(learnedTrace = "learned".toByteArray())) { true })
            server.takeRequest()
            assertEquals(1, server.requestCount)
        }

    @ParameterizedTest
    @ValueSource(ints = [400, 403, 404])
    fun `a 4xx from the presigned put is retried, as a fresh presign fixes an expired signature`(status: Int) =
        runTest {
            server.enqueue(presigned())
            server.enqueue(MockResponse().setResponseCode(status))

            assertEquals(Result.Retry, provider.upload(report()) { true })
        }

    @Test
    fun `a failed register is judged like the presign`() =
        runTest {
            server.enqueue(presigned())
            server.enqueue(MockResponse())
            server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(Result.Drop, provider.upload(report()) { true })

            server.enqueue(presigned())
            server.enqueue(MockResponse())
            server.enqueue(MockResponse().setResponseCode(502))
            assertEquals(Result.Retry, provider.upload(report()) { true })
        }

    @Test
    fun `a dropped connection is retried`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            assertEquals(Result.Retry, provider.upload(report()) { true })
        }

    @Test
    fun `stopping before a step sends nothing more and is retried`() =
        runTest {
            server.enqueue(presigned())
            var steps = 0

            val result = provider.upload(report()) { steps++ < 1 }

            assertEquals(Result.Retry, result)
            assertEquals(1, server.requestCount)
        }
}
