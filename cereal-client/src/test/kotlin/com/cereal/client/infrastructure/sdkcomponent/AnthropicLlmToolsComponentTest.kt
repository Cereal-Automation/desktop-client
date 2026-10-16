package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.interactor.llm.CompleteLlmRequestInteractor
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.provider.LlmProviderImpl
import com.cereal.client.infrastructure.sdkcomponent.fixtures.RecordingLoggerComponent
import com.cereal.sdk.component.llm.LlmException
import com.cereal.sdk.component.llm.LlmRequest
import com.cereal.sdk.component.llm.LlmTool
import com.cereal.sdk.component.llm.LlmToolCall
import com.cereal.sdk.component.llm.LlmTurn
import com.cereal.sdk.component.llm.StopReason
import com.cereal.sdk.component.llm.ToolChoice
import com.cereal.sdk.component.llm.UserPart
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration

/**
 * Tools and structured output through `provider.llm()` against the Anthropic Messages backend, with
 * MockWebServer serving fixtures shaped like the tool-use and structured-outputs docs.
 */
class AnthropicLlmToolsComponentTest {
    private val server = MockWebServer()
    private val configRepository = InMemoryLlmProviderConfigRepository()
    private val component =
        LlmComponentImpl(
            CompleteLlmRequestInteractor(configRepository, LlmProviderImpl(OkHttpClient(), retryDelays = List(3) { Duration.ZERO })),
            RecordingLoggerComponent(),
        )

    private val question = LlmTurn.User(listOf(UserPart.Text("Which is warmer right now, Paris or Madrid?")))
    private val request =
        LlmRequest(
            system = "You compare weather.",
            turns = listOf(question),
            tools = listOf(LlmTool("get_weather", "Current weather for a city.", WEATHER_SCHEMA)),
            responseSchemaJson = ANSWER_SCHEMA,
            maxTokens = 1024,
        )

    @BeforeEach
    fun setUp() =
        runTest {
            server.start()
            configRepository.save(
                LlmProviderConfig.of(LlmProviderType.ANTHROPIC, server.url("/v1").toString(), "sk-ant-test", "claude-opus-5-5"),
            )
        }

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `a two-step tool loop ends in a validated structured answer`() =
        runTest {
            server.enqueue(fixture("messages-tool-use.json"))
            server.enqueue(fixture("messages-structured.json"))

            val first = component.complete(request)

            assertEquals(StopReason.ToolUse, first.stopReason)
            assertEquals(
                listOf(
                    LlmToolCall("toolu_01A09q90qw90lq917835lq9", "get_weather", """{"city":"Paris"}"""),
                    LlmToolCall("toolu_01B18r81rx81mr826724mr8", "get_weather", """{"city":"Madrid"}"""),
                ),
                first.toolCalls,
            )
            assertNull(first.structuredJson)

            val results =
                LlmTurn.User(
                    listOf(
                        UserPart.ToolResult("toolu_01A09q90qw90lq917835lq9", "18 °C, sunny"),
                        UserPart.ToolResult("toolu_01B18r81rx81mr826724mr8", "Weather service timed out.", isError = true),
                    ),
                )
            val second = component.complete(request.copy(turns = listOf(question, first.assistantTurn, results)))

            assertEquals(StopReason.EndTurn, second.stopReason)
            assertEquals("""{"warmest":"Paris","temperature_c":18}""", second.structuredJson)

            assertEquals(
                Json.parseToJsonElement(
                    """
                    {
                      "model": "claude-opus-5-5",
                      "max_tokens": 1024,
                      "system": "You compare weather.",
                      "messages": [
                        {"role": "user", "content": [{"type": "text", "text": "Which is warmer right now, Paris or Madrid?"}]}
                      ],
                      "tools": [{"name": "get_weather", "description": "Current weather for a city.", "input_schema": $WEATHER_SCHEMA}],
                      "tool_choice": {"type": "auto"},
                      "thinking": {"type": "adaptive"},
                      "output_config": {"effort": "medium", "format": {"type": "json_schema", "schema": $ANSWER_SCHEMA}},
                      "cache_control": {"type": "ephemeral"}
                    }
                    """,
                ),
                server.takeRequest().json(),
            )
            val messages = server.takeRequest().json()["messages"]!!.jsonArray
            assertEquals(3, messages.size)
            assertEquals(
                Json.parseToJsonElement(fixtureText("messages-tool-use.json")).jsonObject["content"],
                messages[1].jsonObject["content"],
            )
            assertEquals(
                Json.parseToJsonElement(
                    """
                    {"role": "user", "content": [
                      {"type": "tool_result", "tool_use_id": "toolu_01A09q90qw90lq917835lq9", "content": "18 °C, sunny", "is_error": false},
                      {"type": "tool_result", "tool_use_id": "toolu_01B18r81rx81mr826724mr8", "content": "Weather service timed out.", "is_error": true}
                    ]}
                    """,
                ),
                messages[2],
            )
        }

    @Test
    fun `tool results go before text in the user turn, as Anthropic requires`() =
        runTest {
            server.enqueue(fixture("messages-structured.json"))
            val turn =
                LlmTurn.User(
                    listOf(UserPart.Text("Answer in Celsius."), UserPart.ToolResult("toolu_1", "18 °C")),
                )

            component.complete(request.copy(turns = listOf(turn)))

            val content =
                server
                    .takeRequest()
                    .json()["messages"]!!
                    .jsonArray[0]
                    .jsonObject["content"]!!
                    .jsonArray
            assertEquals(listOf("tool_result", "text"), content.map { it.jsonObject["type"].toString().trim('"') })
        }

    @Test
    fun `toolChoice None is sent as tool_choice none and no schema means no format`() =
        runTest {
            server.enqueue(fixture("messages-text.json"))

            val response = component.complete(request.copy(toolChoice = ToolChoice.None, responseSchemaJson = null))

            val sent = server.takeRequest().json()
            assertEquals(Json.parseToJsonElement("""{"type": "none"}"""), sent["tool_choice"])
            assertEquals(Json.parseToJsonElement("""{"effort": "medium"}"""), sent["output_config"])
            assertNull(response.structuredJson)
        }

    @Test
    fun `an answer that violates the response schema is InvalidOutput with the raw text and is not retried`() =
        runTest {
            server.enqueue(fixture("messages-structured-invalid.json"))
            server.enqueue(fixture("messages-structured.json"))

            val error = assertThrows<LlmException.InvalidOutput> { component.complete(request) }

            assertEquals("""{"warmest":"Paris"}""", error.raw)
            assertEquals("""{"warmest":"Paris"}""", error.assistantTurn.text)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `tool arguments that violate the tool's input schema are InvalidOutput`() =
        runTest {
            server.enqueue(fixture("messages-tool-use-invalid-args.json"))

            val error = assertThrows<LlmException.InvalidOutput> { component.complete(request) }

            assertEquals("""{"town":"Madrid"}""", error.raw)
            assertEquals(2, error.assistantTurn.toolCalls.size)
            assertEquals(1, server.requestCount)
        }

    private fun fixtureText(name: String) = javaClass.getResource("/llm/anthropic/$name")!!.readText()

    private fun fixture(name: String) =
        MockResponse()
            .setHeader("content-type", "application/json")
            .setBody(fixtureText(name))

    private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body.readUtf8()).jsonObject

    private companion object {
        const val WEATHER_SCHEMA =
            """{"type":"object","properties":{"city":{"type":"string"}},"required":["city"],"additionalProperties":false}"""
        const val ANSWER_SCHEMA =
            """{"type":"object","properties":{"warmest":{"type":"string"},"temperature_c":{"type":"integer"}},""" +
                """"required":["warmest","temperature_c"],"additionalProperties":false}"""
    }
}
