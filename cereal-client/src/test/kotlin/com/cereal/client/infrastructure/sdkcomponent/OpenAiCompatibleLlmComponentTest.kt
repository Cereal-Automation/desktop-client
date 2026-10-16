package com.cereal.client.infrastructure.sdkcomponent

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.cereal.client.application.interactor.llm.CompleteLlmRequestInteractor
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.provider.LlmProviderImpl
import com.cereal.client.infrastructure.sdkcomponent.fixtures.RecordingLoggerComponent
import com.cereal.sdk.component.llm.ImageMediaType
import com.cereal.sdk.component.llm.LlmException
import com.cereal.sdk.component.llm.LlmRequest
import com.cereal.sdk.component.llm.LlmTurn
import com.cereal.sdk.component.llm.LlmUsage
import com.cereal.sdk.component.llm.StopReason
import com.cereal.sdk.component.llm.UserPart
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * `provider.llm()` end to end against the OpenAI-compatible Chat Completions backend: real component,
 * interactor and HTTP backend, an in-memory config, and MockWebServer serving fixtures shaped like the
 * OpenAI, Gemini and Ollama docs (`src/test/resources/llm/openai`).
 */
class OpenAiCompatibleLlmComponentTest {
    private val server = MockWebServer()
    private val configRepository = InMemoryLlmProviderConfigRepository()
    private val taskLog = RecordingLoggerComponent()
    private val component =
        LlmComponentImpl(
            CompleteLlmRequestInteractor(configRepository, LlmProviderImpl(OkHttpClient(), retryDelays = List(3) { Duration.ZERO })),
            taskLog,
        )

    private val request =
        LlmRequest(
            system = "You answer geography questions.",
            turns = listOf(LlmTurn.User(listOf(UserPart.Text("What is the capital of France?")))),
            maxTokens = 1024,
        )

    private val imageRequest =
        request.copy(turns = listOf(LlmTurn.User(listOf(UserPart.Text("What is the capital of France?"), UserPart.Image(IMAGE_BYTES, ImageMediaType.Png)))))

    @BeforeEach
    fun setUp() =
        runTest {
            server.start()
            configure()
        }

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `a text conversation is sent as a Chat Completions request with the key as a bearer token`() =
        runTest {
            server.enqueue(fixture("chat-text.json"))

            component.complete(request)

            val sent = server.takeRequest()
            assertEquals("/v1/chat/completions", sent.path)
            assertEquals("POST", sent.method)
            assertEquals("Bearer $API_KEY", sent.getHeader("Authorization"))
            assertEquals(
                Json.parseToJsonElement(
                    """
                    {
                      "model": "gpt-5.6",
                      "max_completion_tokens": 1024,
                      "messages": [
                        {"role": "system", "content": "You answer geography questions."},
                        {"role": "user", "content": [{"type": "text", "text": "What is the capital of France?"}]}
                      ]
                    }
                    """,
                ),
                sent.json(),
            )
        }

    @Test
    fun `a local server without a key gets no Authorization header and its reply is normalized`() =
        runTest {
            configure(apiKey = "", model = "llama3.2")
            server.enqueue(fixture("ollama-chat-text.json"))

            val response = component.complete(request)

            assertNull(server.takeRequest().getHeader("Authorization"))
            assertEquals("Paris is the capital of France.", response.text)
            assertEquals(StopReason.EndTurn, response.stopReason)
            assertEquals("llama3.2", response.model)
            assertEquals(LlmUsage(inputUncached = 32, cacheRead = 0, cacheWrite = 0, output = 8), response.usage)
        }

    @Test
    fun `the configured base URL is honoured including an OpenRouter-style path`() =
        runTest {
            configure(path = "/api/v1")
            server.enqueue(fixture("chat-text.json"))

            component.complete(request)

            assertEquals("/api/v1/chat/completions", server.takeRequest().path)
        }

    @Test
    fun `cached prompt tokens are split out of prompt_tokens`() =
        runTest {
            server.enqueue(fixture("chat-text.json"))

            val response = component.complete(request)

            assertEquals("Paris is the capital of France.", response.text)
            assertEquals("gpt-5.6-2026-08-01", response.model)
            // prompt_tokens 6021 = 21 uncached + 4200 cached + 1800 cache write (fixture from docs, not a live call)
            assertEquals(LlmUsage(inputUncached = 21, cacheRead = 4200, cacheWrite = 1800, output = 57), response.usage)
            assertEquals(emptyList<Any>(), response.toolCalls)
            assertEquals(null, response.structuredJson)
        }

    @Test
    fun `the assistant turn sent back keeps provider extras such as a Gemini thought signature`() =
        runTest {
            server.enqueue(fixture("chat-gemini-signature.json"))
            server.enqueue(fixture("chat-gemini-signature.json"))

            val first = component.complete(request)
            val followUp = LlmTurn.User(listOf(UserPart.Text("And of Spain?")))
            component.complete(request.copy(turns = request.turns + first.assistantTurn + followUp))

            server.takeRequest()
            val messages = server.takeRequest().json()["messages"]!!.jsonArray
            val original =
                Json
                    .parseToJsonElement(fixtureText("chat-gemini-signature.json"))
                    .jsonObject["choices"]!!
                    .jsonArray[0]
                    .jsonObject["message"]
            assertEquals(4, messages.size)
            assertEquals(original, messages[2])
        }

    @Test
    fun `an image is sent as an image_url part with a base64 data URI`() =
        runTest {
            ImageMediaType.entries.forEach { mediaType ->
                server.enqueue(fixture("chat-text.json"))
                val image = UserPart.Image(IMAGE_BYTES, mediaType)

                component.complete(request.copy(turns = listOf(LlmTurn.User(listOf(UserPart.Text("What is this?"), image)))))

                assertEquals(
                    Json.parseToJsonElement(
                        """
                        [
                          {"type": "text", "text": "What is this?"},
                          {"type": "image_url", "image_url": {"url": "data:${mediaType.mimeType};base64,$IMAGE_BASE64"}}
                        ]
                        """,
                    ),
                    server
                        .takeRequest()
                        .json()["messages"]!!
                        .jsonArray[1]
                        .jsonObject["content"],
                )
            }
        }

    @Test
    fun `an image sent to a model without vision fails as InvalidRequest with the provider message`() =
        runTest {
            server.enqueue(fixture("error-image-unsupported.json", code = 400))

            val error = assertThrows<LlmException.InvalidRequest> { component.complete(imageRequest) }

            assertEquals("Invalid content type. image_url is only supported by certain models.", error.message)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a Gemini reply to an image keeps the thought signature on its tool call when sent back`() =
        runTest {
            // Fixture from Google's OpenAI-compatibility docs (signature on tool_calls[].extra_content), not a live call.
            server.enqueue(fixture("gemini-chat-image-tool-call.json"))
            server.enqueue(fixture("chat-text.json"))

            val first = component.complete(imageRequest)
            component.complete(imageRequest.copy(turns = imageRequest.turns + first.assistantTurn + request.turns))

            assertEquals(StopReason.ToolUse, first.stopReason)
            server.takeRequest()
            val messages = server.takeRequest().json()["messages"]!!.jsonArray
            val original =
                Json
                    .parseToJsonElement(fixtureText("gemini-chat-image-tool-call.json"))
                    .jsonObject["choices"]!!
                    .jsonArray[0]
                    .jsonObject["message"]
            assertEquals(original, messages[2])
            assertEquals(
                "EjQKMgHGeminiImageSignatureExample0002",
                messages[2]
                    .jsonObject["tool_calls"]!!
                    .jsonArray[0]
                    .jsonObject["extra_content"]!!
                    .jsonObject["google"]!!
                    .jsonObject["thought_signature"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `length, content_filter and refusal are mapped`() =
        runTest {
            server.enqueue(fixture("chat-length.json"))
            server.enqueue(fixture("chat-content-filter.json"))
            server.enqueue(fixture("chat-refusal.json"))

            val truncated = component.complete(request)
            val filtered = component.complete(request)
            val refused = component.complete(request)

            assertEquals(StopReason.MaxTokens, truncated.stopReason)
            assertEquals("The answer begins", truncated.text)
            assertEquals(StopReason.Refusal, filtered.stopReason)
            assertEquals(StopReason.Refusal, refused.stopReason)
            assertEquals("", refused.text)
        }

    @Test
    fun `a rejected key fails as Authentication without retrying`() =
        runTest {
            server.enqueue(fixture("error-authentication.json", code = 401))

            assertThrows<LlmException.Authentication> { component.complete(request) }

            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a forbidden key fails as Authentication`() =
        runTest {
            server.enqueue(fixture("error-authentication.json", code = 403))

            assertThrows<LlmException.Authentication> { component.complete(request) }
        }

    @Test
    fun `429 is retried and then thrown as RateLimited with the retry-after header`() =
        runTest {
            repeat(4) { server.enqueue(fixture("error-rate-limit.json", code = 429).setHeader("retry-after", "30")) }

            val error = assertThrows<LlmException.RateLimited> { component.complete(request) }

            assertEquals(30.seconds, error.retryAfter)
            assertEquals(4, server.requestCount)
        }

    @Test
    fun `a server error is retried and the later success is returned`() =
        runTest {
            server.enqueue(fixture("error-server.json", code = 503))
            server.enqueue(fixture("chat-text.json"))

            val response = component.complete(request)

            assertEquals("Paris is the capital of France.", response.text)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `5xx is retried and then thrown as Unavailable`() =
        runTest {
            repeat(4) { server.enqueue(fixture("error-server.json", code = 500)) }

            assertThrows<LlmException.Unavailable> { component.complete(request) }

            assertEquals(4, server.requestCount)
        }

    @Test
    fun `a network failure is Unavailable`() =
        runTest {
            server.shutdown()

            assertThrows<LlmException.Unavailable> { component.complete(request) }
        }

    @Test
    fun `a validation failure is InvalidRequest with the provider message and is never retried`() =
        runTest {
            listOf(400, 422).forEach { code ->
                server.enqueue(fixture("error-invalid-request.json", code = code))

                val error = assertThrows<LlmException.InvalidRequest> { component.complete(request) }

                assertEquals("Invalid value for 'messages[1].role': expected one of system, developer, user, assistant, tool.", error.message)
            }
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `an unknown OpenAI model fails as InvalidRequest and the task log names the configured model`() =
        runTest {
            configure(model = "gpt-retired-1")
            server.enqueue(fixture("error-model-not-found.json", code = 404))

            val error = assertThrows<LlmException.InvalidRequest> { component.complete(request) }

            assertEquals("The model `gpt-retired-1` does not exist or you do not have access to it.", error.message)
            assertEquals(1, server.requestCount)
            assertEquals(
                listOf("WARN The AI model \"gpt-retired-1\" set in Settings → AI wasn't found. Choose another model there."),
                taskLog.lines,
            )
        }

    @Test
    fun `an unpulled Ollama model is reported as a missing model`() =
        runTest {
            configure(apiKey = "", model = "llama-retired")
            server.enqueue(fixture("ollama-error-model-not-found.json", code = 404))

            assertThrows<LlmException.InvalidRequest> { component.complete(request) }

            assertEquals(
                listOf("WARN The AI model \"llama-retired\" set in Settings → AI wasn't found. Choose another model there."),
                taskLog.lines,
            )
        }

    @Test
    fun `each successful call adds one task-log line with the model and the four token counts`() =
        runTest {
            server.enqueue(fixture("chat-text.json"))

            component.complete(request)

            assertEquals(
                listOf("INFO AI call to gpt-5.6-2026-08-01 used 21 input, 4200 cache read, 1800 cache write and 57 output tokens."),
                taskLog.lines,
            )
        }

    @Test
    fun `an unreadable success body is Unavailable without echoing the body`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices": [{"message": {"content": "Paris secret"""))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"model": "m", "choices": []}"""))

            repeat(2) {
                val error = assertThrows<LlmException.Unavailable> { component.complete(request) }
                assertFalse(error.messageChain().contains("Paris"), error.messageChain())
            }
        }

    @Test
    fun `no prompt, response, payload or key appears in any log line or exception message`() =
        runTest {
            val appLog = ListAppender<ILoggingEvent>().apply { start() }
            val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
            root.addAppender(appLog)
            val errors = mutableListOf<Throwable>()
            try {
                server.enqueue(fixture("chat-gemini-signature.json"))
                server.enqueue(fixture("error-authentication.json", code = 401))
                repeat(4) { server.enqueue(fixture("error-rate-limit.json", code = 429)) }
                server.enqueue(MockResponse().setResponseCode(400).setBody("not json"))
                repeat(4) { server.enqueue(fixture("error-server.json", code = 503)) }

                val first = component.complete(imageRequest)
                val next = imageRequest.copy(turns = imageRequest.turns + first.assistantTurn)
                repeat(4) { errors += assertThrows<LlmException> { component.complete(next) } }
            } finally {
                root.detachAppender(appLog)
            }

            val everything =
                taskLog.lines + appLog.list.map { it.formattedMessage } + errors.map { it.messageChain() }
            val secrets = listOf(API_KEY, "capital of France", "geography", "Paris", "CsQBAdHtim9", IMAGE_TEXT, IMAGE_BASE64)
            everything.forEach { line -> secrets.forEach { assertFalse(line.contains(it), "\"$it\" leaked in: $line") } }
            assertTrue(appLog.list.isNotEmpty(), "retries should be logged")
        }

    private fun Throwable.messageChain(): String = generateSequence(this) { it.cause }.joinToString(" | ") { "$it" }

    private suspend fun configure(
        apiKey: String = API_KEY,
        model: String = "gpt-5.6",
        path: String = "/v1",
    ) = configRepository.save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, server.url(path).toString(), apiKey, model))

    private fun fixtureText(name: String) = javaClass.getResource("/llm/openai/$name")!!.readText()

    private fun fixture(
        name: String,
        code: Int = 200,
    ): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("content-type", "application/json")
            .setBody(fixtureText(name))

    private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body.readUtf8()).jsonObject

    private companion object {
        const val API_KEY = "sk-test-key-1234"
        const val IMAGE_TEXT = "secret-screenshot-pixels"
        val IMAGE_BYTES = IMAGE_TEXT.toByteArray()
        const val IMAGE_BASE64 = "c2VjcmV0LXNjcmVlbnNob3QtcGl4ZWxz"
    }
}
