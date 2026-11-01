package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmConnection
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.provider.fixtures.llmProviderOverHttp
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * `GET {baseUrl}/models` on both backends against MockWebServer, with fixtures shaped like the
 * Anthropic and OpenAI Models API docs (`src/test/resources/llm/{anthropic,openai}/models.json`).
 */
class LlmProviderListModelsTest {
    private val server = MockWebServer()
    private val provider = llmProviderOverHttp()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun config(
        type: LlmProviderType,
        apiKey: String = "sk-test",
    ) = LlmConnection.of(type, server.url("/v1").toString(), apiKey)

    private fun fixture(path: String) = MockResponse().setBody(javaClass.getResource("/llm/$path")!!.readText())

    @Test
    fun `anthropic lists models with its key and version headers`() =
        runTest {
            server.enqueue(fixture("anthropic/models.json"))

            val models = provider.listModels(config(LlmProviderType.ANTHROPIC))

            assertEquals(
                listOf(
                    LlmModel("claude-opus-5-5", "Claude Opus 5.5", created = Instant.parse("2026-05-01T00:00:00Z")),
                    LlmModel("claude-haiku-4-5-20251001", "Claude Haiku 4.5", created = Instant.parse("2025-10-01T00:00:00Z")),
                ),
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

            val created = Instant.ofEpochSecond(1754438400)
            assertEquals(listOf(LlmModel("gpt-5", created = created), LlmModel("gpt-5-mini", created = created)), models)
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

    private suspend fun listOne(entry: String): List<LlmModel> {
        server.enqueue(MockResponse().setBody("""{"data":[$entry]}"""))
        return provider.listModels(config(LlmProviderType.OPENAI_COMPATIBLE))
    }

    @Test
    fun `a zero or missing created date is unknown`() =
        runTest {
            assertNull(listOne("""{"id":"m","created":0}""").single().created)
            assertNull(listOne("""{"id":"m"}""").single().created)
        }

    @Test
    fun `an OpenRouter-style name is the display name`() =
        runTest {
            assertEquals("Mistral Small", listOne("""{"id":"mistral/small","name":"Mistral Small"}""").single().displayName)
        }

    @Test
    fun `a Gemini models prefix is stripped from the id`() =
        runTest {
            assertEquals("gemini-3.8-flash", listOne("""{"id":"models/gemini-3.8-flash"}""").single().id)
        }

    @Test
    fun `a model whose output is not just text is hidden`() =
        runTest {
            assertEquals(emptyList<LlmModel>(), listOne("""{"id":"img","architecture":{"output_modalities":["text","image"]}}"""))
            assertEquals(listOf("txt"), listOne("""{"id":"txt","architecture":{"output_modalities":["text"]}}""").map { it.id })
        }

    @Test
    fun `a model without a chat completions endpoint is hidden`() =
        runTest {
            assertEquals(emptyList<LlmModel>(), listOne("""{"id":"m:batch","endpoints":["/api/v1/batches"]}"""))
            assertEquals(listOf("m"), listOne("""{"id":"m","endpoints":["/api/v1/chat/completions"]}""").map { it.id })
        }

    @Test
    fun `a model that doesn't support tools is hidden`() =
        runTest {
            assertEquals(emptyList<LlmModel>(), listOne("""{"id":"m","supported_parameters":["temperature"]}"""))
            assertEquals(listOf("m"), listOne("""{"id":"m","supported_parameters":["tools","temperature"]}""").map { it.id })
        }

    @Test
    fun `a zero prompt and completion price is priced free, a negative one is not`() =
        runTest {
            assertEquals(true, listOne("""{"id":"m","pricing":{"prompt":"0","completion":"0"}}""").single().pricedFree)
            assertFalse(listOne("""{"id":"m","pricing":{"prompt":"-1","completion":"-1"}}""").single().pricedFree)
            assertFalse(listOne("""{"id":"m","pricing":{"prompt":"0","completion":"0.000002"}}""").single().pricedFree)
            assertFalse(listOne("""{"id":"m"}""").single().pricedFree)
        }

    @Test
    fun `malformed optional fields count as missing`() =
        runTest {
            val models =
                listOne(
                    """{"id":"m","created":"yesterday","created_at":42,"display_name":{},"pricing":"free",""" +
                        """"architecture":{"output_modalities":"text"},"endpoints":{},"supported_parameters":7}""",
                )

            assertEquals(listOf(LlmModel("m")), models)
        }

    @Test
    fun `entries without a string id, or that aren't objects, are skipped`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"data":[{"id":null},{"id":7},{"name":"no id"},"x",{"id":"ok"}]}"""))

            assertEquals(listOf("ok"), provider.listModels(config(LlmProviderType.OPENAI_COMPATIBLE)).map { it.id })
        }
}
