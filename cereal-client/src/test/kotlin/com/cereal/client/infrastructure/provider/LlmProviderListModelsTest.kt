package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration

/**
 * `GET {baseUrl}/models` on both backends against MockWebServer, with fixtures shaped like the
 * Anthropic and OpenAI Models API docs (`src/test/resources/llm/{anthropic,openai}/models.json`).
 */
class LlmProviderListModelsTest {
    private val server = MockWebServer()
    private val provider = LlmProviderImpl(OkHttpClient(), retryDelays = List(3) { Duration.ZERO })

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun config(
        type: LlmProviderType,
        apiKey: String = "sk-test",
    ) = LlmProviderConfig.of(type, server.url("/v1").toString(), apiKey, "any-model")

    private fun fixture(path: String) = MockResponse().setBody(javaClass.getResource("/llm/$path")!!.readText())

    @Test
    fun `anthropic lists models with its key and version headers`() =
        runTest {
            server.enqueue(fixture("anthropic/models.json"))

            val models = provider.listModels(config(LlmProviderType.ANTHROPIC))

            assertEquals(
                listOf(LlmModel("claude-opus-5-5", "Claude Opus 5.5"), LlmModel("claude-haiku-4-5-20251001", "Claude Haiku 4.5")),
                models,
            )
            val sent = server.takeRequest()
            assertEquals("GET", sent.method)
            assertEquals("/v1/models?limit=1000", sent.path)
            assertEquals("sk-test", sent.getHeader("x-api-key"))
            assertEquals("2023-06-01", sent.getHeader("anthropic-version"))
        }

    @Test
    fun `openai-compatible lists model ids with the key as a bearer token`() =
        runTest {
            server.enqueue(fixture("openai/models.json"))

            val models = provider.listModels(config(LlmProviderType.OPENAI_COMPATIBLE))

            assertEquals(listOf(LlmModel("gpt-5"), LlmModel("gpt-5-mini")), models)
            val sent = server.takeRequest()
            assertEquals("GET", sent.method)
            assertEquals("/v1/models", sent.path)
            assertEquals("Bearer sk-test", sent.getHeader("Authorization"))
        }

    @Test
    fun `openai-compatible without a key sends no authorization header`() =
        runTest {
            server.enqueue(fixture("openai/models.json"))

            provider.listModels(config(LlmProviderType.OPENAI_COMPATIBLE, apiKey = ""))

            assertNull(server.takeRequest().getHeader("Authorization"))
        }

    @Test
    fun `a rejected key is an authentication failure`() =
        runTest {
            LlmProviderType.entries.forEach { type ->
                server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid x-api-key"}}"""))

                assertThrows<LlmCallException.Authentication> { provider.listModels(config(type)) }
            }
        }

    @Test
    fun `no models endpoint at the base URL is a not-found failure`() =
        runTest {
            LlmProviderType.entries.forEach { type ->
                server.enqueue(MockResponse().setResponseCode(404).setBody("<html>Not Found</html>"))

                val error = assertThrows<LlmCallException.InvalidRequest> { provider.listModels(config(type)) }
                assertEquals("Not found: there are no models at this base URL. Check it, including the version path (for example /v1).", error.message)
            }
        }

    @Test
    fun `an unreachable server is unavailable`() =
        runTest {
            val config = config(LlmProviderType.OPENAI_COMPATIBLE)
            server.shutdown()

            assertThrows<LlmCallException.Unavailable> { provider.listModels(config) }
        }

    @Test
    fun `a body that isn't a model list is unavailable`() =
        runTest {
            server.enqueue(MockResponse().setBody("<html>a proxy login page</html>"))

            assertThrows<LlmCallException.Unavailable> { provider.listModels(config(LlmProviderType.OPENAI_COMPATIBLE)) }
        }
}
