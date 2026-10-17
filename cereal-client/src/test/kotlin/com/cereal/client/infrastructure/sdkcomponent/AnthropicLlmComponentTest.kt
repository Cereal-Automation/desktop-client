package com.cereal.client.infrastructure.sdkcomponent

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.cereal.client.application.interactor.llm.CompleteLlmRequestInteractor
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.provider.fixtures.llmProviderOverHttp
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
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

/**
 * `provider.llm()` end to end against the Anthropic Messages backend: real component, interactor and
 * HTTP backend, an in-memory config, and MockWebServer serving fixtures shaped like the Messages API
 * docs (`src/test/resources/llm/anthropic`).
 */
class AnthropicLlmComponentTest {
    private val server = MockWebServer()
    private val configRepository = InMemoryLlmProviderConfigRepository()
    private val taskLog = RecordingLoggerComponent()
    private val component =
        LlmComponentImpl(
            CompleteLlmRequestInteractor(configRepository, llmProviderOverHttp()),
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
            configRepository.save(
                LlmProviderConfig.of(LlmProviderType.ANTHROPIC, server.url("/v1").toString(), API_KEY, "claude-opus-5-5"),
            )
        }

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `a text conversation is sent as a Messages request with the fixed tuning`() =
        runTest {
            server.enqueue(fixture("messages-text.json"))

            component.complete(request)

            val sent = server.takeRequest()
            assertEquals("/v1/messages", sent.path)
            assertEquals("POST", sent.method)
            assertEquals(API_KEY, sent.getHeader("x-api-key"))
            assertEquals("2023-06-01", sent.getHeader("anthropic-version"))
            assertEquals(
                Json.parseToJsonElement(
                    """
                    {
                      "model": "claude-opus-5-5",
                      "max_tokens": 1024,
                      "system": "You answer geography questions.",
                      "messages": [
                        {"role": "user", "content": [{"type": "text", "text": "What is the capital of France?"}]}
                      ],
                      "thinking": {"type": "adaptive"},
                      "output_config": {"effort": "medium"},
                      "cache_control": {"type": "ephemeral"}
                    }
                    """,
                ),
                sent.json(),
            )
        }

    @Test
    fun `the response is normalized with text concatenated and cache tokens split out`() =
        runTest {
            server.enqueue(fixture("messages-text.json"))

            val response = component.complete(request)

            assertEquals("Paris is the capital of France.", response.text)
            assertEquals(StopReason.EndTurn, response.stopReason)
            assertEquals("claude-opus-5-5", response.model)
            assertEquals(LlmUsage(inputUncached = 21, cacheRead = 4200, cacheWrite = 1800, output = 57), response.usage)
            assertEquals(emptyList<Any>(), response.toolCalls)
            assertEquals(null, response.structuredJson)
        }

    @Test
    fun `the assistant turn sent back reproduces the original content blocks including thinking`() =
        runTest {
            server.enqueue(fixture("messages-text.json"))
            server.enqueue(fixture("messages-text.json"))

            val first = component.complete(request)
            val followUp = LlmTurn.User(listOf(UserPart.Text("And of Spain?")))
            component.complete(request.copy(turns = request.turns + first.assistantTurn + followUp))

            server.takeRequest()
            val messages = server.takeRequest().json()["messages"]!!.jsonArray
            val original = Json.parseToJsonElement(fixtureText("messages-text.json")).jsonObject["content"]
            assertEquals(3, messages.size)
            assertEquals("assistant", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
            assertEquals(original, messages[1].jsonObject["content"])
        }

    @Test
    fun `refusal and max_tokens stop reasons are mapped`() =
        runTest {
            server.enqueue(fixture("messages-refusal.json"))
            server.enqueue(fixture("messages-max-tokens.json"))

            val refusal = component.complete(request)
            val truncated = component.complete(request)

            assertEquals(StopReason.Refusal, refusal.stopReason)
            assertEquals("", refusal.text)
            assertEquals(StopReason.MaxTokens, truncated.stopReason)
            assertEquals("The answer begins", truncated.text)
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
    fun `retry-after in HTTP-date form is read as the time left until that date`() =
        runTest {
            val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(120))
            repeat(4) { server.enqueue(fixture("error-rate-limit.json", code = 429).setHeader("retry-after", date)) }

            val error = assertThrows<LlmException.RateLimited> { component.complete(request) }

            val retryAfter = error.retryAfter!!
            assertTrue(retryAfter > 100.seconds && retryAfter <= 120.seconds, "$retryAfter")
        }

    @Test
    fun `a request timeout is retried like a server error and then thrown as Unavailable`() =
        runTest {
            // OkHttp itself repeats a 408 once, so each of our attempts is two requests.
            repeat(8) { server.enqueue(MockResponse().setResponseCode(408)) }
            repeat(2) { server.enqueue(MockResponse().setResponseCode(408)) }
            server.enqueue(fixture("messages-text.json"))

            assertThrows<LlmException.Unavailable> { component.complete(request) }
            assertEquals(8, server.requestCount)
            assertEquals("Paris is the capital of France.", component.complete(request).text)
        }

    @Test
    fun `429 without retry-after has no retryAfter`() =
        runTest {
            repeat(4) { server.enqueue(fixture("error-rate-limit.json", code = 429)) }

            val error = assertThrows<LlmException.RateLimited> { component.complete(request) }

            assertNull(error.retryAfter)
        }

    @Test
    fun `an overloaded provider is retried and the later success is returned`() =
        runTest {
            server.enqueue(fixture("error-overloaded.json", code = 529))
            server.enqueue(fixture("messages-text.json"))

            val response = component.complete(request)

            assertEquals("Paris is the capital of France.", response.text)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `5xx is retried and then thrown as Unavailable`() =
        runTest {
            repeat(4) { server.enqueue(MockResponse().setResponseCode(500)) }

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
    fun `an image is sent as a base64 image block with its media type`() =
        runTest {
            ImageMediaType.entries.forEach { mediaType ->
                server.enqueue(fixture("messages-text.json"))
                val image = UserPart.Image(IMAGE_BYTES, mediaType)

                component.complete(request.copy(turns = listOf(LlmTurn.User(listOf(UserPart.Text("What is this?"), image)))))

                assertEquals(
                    Json.parseToJsonElement(
                        """
                        [
                          {"type": "text", "text": "What is this?"},
                          {"type": "image", "source": {"type": "base64", "media_type": "${mediaType.mimeType}", "data": "$IMAGE_BASE64"}}
                        ]
                        """,
                    ),
                    server
                        .takeRequest()
                        .json()["messages"]!!
                        .jsonArray[0]
                        .jsonObject["content"],
                )
            }
        }

    @Test
    fun `an image the provider rejects fails as InvalidRequest with the provider message`() =
        runTest {
            server.enqueue(fixture("error-image-too-large.json", code = 400))

            val error = assertThrows<LlmException.InvalidRequest> { component.complete(imageRequest) }

            assertEquals("messages.0.content.1.image.source.base64: image exceeds 5 MB maximum: 7340032 bytes > 5242880 bytes", error.message)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a validation failure is InvalidRequest with the provider message and is never retried`() =
        runTest {
            listOf(400, 413, 422).forEach { code ->
                server.enqueue(fixture("error-invalid-request.json", code = code))

                val error = assertThrows<LlmException.InvalidRequest> { component.complete(request) }

                assertEquals("messages: roles must alternate between \"user\" and \"assistant\"", error.message)
            }
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `a retired model fails as InvalidRequest and the task log names the configured model`() =
        runTest {
            configRepository.save(
                LlmProviderConfig.of(LlmProviderType.ANTHROPIC, server.url("/v1").toString(), API_KEY, "claude-retired-1"),
            )
            server.enqueue(fixture("error-model-not-found.json", code = 404))

            val error = assertThrows<LlmException.InvalidRequest> { component.complete(request) }

            assertEquals("model: claude-retired-1", error.message)
            assertEquals(1, server.requestCount)
            assertEquals(
                listOf("WARN The AI model \"claude-retired-1\" set in Settings → AI wasn't found. Choose another model there."),
                taskLog.lines,
            )
        }

    @Test
    fun `each successful call adds one task-log line with the model and the four token counts`() =
        runTest {
            server.enqueue(fixture("messages-text.json"))

            component.complete(request)

            assertEquals(
                listOf("INFO AI call to claude-opus-5-5 used 21 input, 4200 cache read, 1800 cache write and 57 output tokens."),
                taskLog.lines,
            )
        }

    @Test
    fun `an unreadable success body is Unavailable without echoing the body`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"content": [{"type": "text", "text": "Paris secret"""))

            val error = assertThrows<LlmException.Unavailable> { component.complete(request) }

            assertFalse(error.messageChain().contains("Paris"), error.messageChain())
        }

    @Test
    fun `a success body with blocks missing their fields is Unavailable without echoing the body`() =
        runTest {
            val usage = "\"usage\": {\"input_tokens\": 1, \"output_tokens\": 1}"
            server.enqueue(MockResponse().setBody("""{"model": "m", "content": [{"type": "text", "txt": "Paris"}], $usage}"""))
            server.enqueue(MockResponse().setBody("""{"model": "m", "content": [{"type": "tool_use", "id": "t1", "name": "Paris"}], $usage}"""))

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
                server.enqueue(fixture("messages-text.json"))
                server.enqueue(fixture("error-authentication.json", code = 401))
                repeat(4) { server.enqueue(fixture("error-rate-limit.json", code = 429)) }
                server.enqueue(fixture("error-invalid-request.json", code = 400))
                repeat(4) { server.enqueue(fixture("error-overloaded.json", code = 529)) }

                val first = component.complete(imageRequest)
                val next = imageRequest.copy(turns = imageRequest.turns + first.assistantTurn)
                repeat(4) { errors += assertThrows<LlmException> { component.complete(next) } }
            } finally {
                root.detachAppender(appLog)
            }

            val everything =
                taskLog.lines + appLog.list.map { it.formattedMessage } + errors.map { it.messageChain() }
            val secrets = listOf(API_KEY, "capital of France", "geography", "Paris", "EqQBCgIYAhIM1gbcDa9GJwZA", IMAGE_TEXT, IMAGE_BASE64)
            everything.forEach { line -> secrets.forEach { assertFalse(line.contains(it), "\"$it\" leaked in: $line") } }
            assertTrue(appLog.list.isNotEmpty(), "retries should be logged")
        }

    private fun Throwable.messageChain(): String = generateSequence(this) { it.cause }.joinToString(" | ") { "$it" }

    private fun fixtureText(name: String) = javaClass.getResource("/llm/anthropic/$name")!!.readText()

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
        const val API_KEY = "sk-ant-test-key-1234"
        const val IMAGE_TEXT = "secret-screenshot-pixels"
        val IMAGE_BYTES = IMAGE_TEXT.toByteArray()
        const val IMAGE_BASE64 = "c2VjcmV0LXNjcmVlbnNob3QtcGl4ZWxz"
    }
}
