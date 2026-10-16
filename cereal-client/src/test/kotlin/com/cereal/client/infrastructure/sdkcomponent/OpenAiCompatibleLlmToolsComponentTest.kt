package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.interactor.llm.CompleteLlmRequestInteractor
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.provider.fixtures.llmProviderOverHttp
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tools and structured output through `provider.llm()` against the OpenAI-compatible backend, with
 * MockWebServer serving fixtures shaped like the Chat Completions function-calling and structured
 * outputs docs.
 */
class OpenAiCompatibleLlmToolsComponentTest {
    private val server = MockWebServer()
    private val configRepository = InMemoryLlmProviderConfigRepository()
    private val component =
        LlmComponentImpl(
            CompleteLlmRequestInteractor(configRepository, llmProviderOverHttp()),
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
                LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, server.url("/v1").toString(), "sk-test", "gpt-5.6"),
            )
        }

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `a two-step tool loop ends in a validated structured answer`() =
        runTest {
            server.enqueue(fixture("chat-tool-calls.json"))
            server.enqueue(fixture("chat-structured.json"))

            val first = component.complete(request)

            assertEquals(StopReason.ToolUse, first.stopReason)
            // `arguments` arrives as a JSON string holding JSON; scripts get the plain object.
            assertEquals(
                listOf(
                    LlmToolCall("call_Paris0000000000000000001", "get_weather", """{"city":"Paris"}"""),
                    LlmToolCall("call_Madrid000000000000000002", "get_weather", """{"city":"Madrid"}"""),
                ),
                first.toolCalls,
            )
            assertEquals("", first.text)
            assertNull(first.structuredJson)

            val results =
                LlmTurn.User(
                    listOf(
                        UserPart.ToolResult("call_Paris0000000000000000001", "18 °C, sunny"),
                        UserPart.ToolResult("call_Madrid000000000000000002", "Weather service timed out.", isError = true),
                    ),
                )
            val second = component.complete(request.copy(turns = listOf(question, first.assistantTurn, results)))

            assertEquals(StopReason.EndTurn, second.stopReason)
            assertEquals("""{"warmest":"Paris","temperature_c":18}""", second.structuredJson)

            assertEquals(
                Json.parseToJsonElement(
                    """
                    {
                      "model": "gpt-5.6",
                      "max_completion_tokens": 1024,
                      "messages": [
                        {"role": "system", "content": "You compare weather."},
                        {"role": "user", "content": [{"type": "text", "text": "Which is warmer right now, Paris or Madrid?"}]}
                      ],
                      "tools": [{"type": "function", "function": {
                        "name": "get_weather", "description": "Current weather for a city.", "parameters": $WEATHER_SCHEMA
                      }}],
                      "tool_choice": "auto",
                      "response_format": {"type": "json_schema", "json_schema": {"name": "response", "schema": $ANSWER_SCHEMA}}
                    }
                    """,
                ),
                server.takeRequest().json(),
            )
            val messages = server.takeRequest().json()["messages"]!!.jsonArray
            val original =
                Json
                    .parseToJsonElement(fixtureText("chat-tool-calls.json"))
                    .jsonObject["choices"]!!
                    .jsonArray[0]
                    .jsonObject["message"]!!
            assertEquals(5, messages.size)
            assertEquals(JsonObject(original.jsonObject - "annotations"), messages[2])
            assertEquals(
                Json.parseToJsonElement(
                    """
                    [
                      {"role": "tool", "tool_call_id": "call_Paris0000000000000000001", "content": "18 °C, sunny"},
                      {"role": "tool", "tool_call_id": "call_Madrid000000000000000002", "content": "Error: Weather service timed out."}
                    ]
                    """,
                ),
                JsonArray(messages.drop(3)),
            )
        }

    @Test
    fun `text next to tool results becomes a user message after the tool messages`() =
        runTest {
            server.enqueue(fixture("chat-structured.json"))
            val turn = LlmTurn.User(listOf(UserPart.Text("Answer in Celsius."), UserPart.ToolResult("call_1", "18 °C")))

            component.complete(request.copy(turns = listOf(turn)))

            val messages = server.takeRequest().json()["messages"]!!.jsonArray
            assertEquals(
                listOf("system", "tool", "user"),
                messages.map { it.jsonObject["role"].toString().trim('"') },
            )
        }

    @Test
    fun `toolChoice None is sent as none and no schema means no response_format`() =
        runTest {
            server.enqueue(fixture("chat-text.json"))

            val response = component.complete(request.copy(toolChoice = ToolChoice.None, responseSchemaJson = null))

            val sent = server.takeRequest().json()
            assertEquals(Json.parseToJsonElement("\"none\""), sent["tool_choice"])
            assertNull(sent["response_format"])
            assertNull(response.structuredJson)
        }

    @Test
    fun `an answer that violates the response schema is InvalidOutput with the raw text and is not retried`() =
        runTest {
            server.enqueue(fixture("chat-structured-invalid.json"))
            server.enqueue(fixture("chat-structured.json"))

            val error = assertThrows<LlmException.InvalidOutput> { component.complete(request) }

            assertEquals("""{"warmest":"Paris"}""", error.raw)
            assertEquals("""{"warmest":"Paris"}""", error.assistantTurn.text)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `tool arguments that violate the tool's input schema are InvalidOutput`() =
        runTest {
            server.enqueue(fixture("chat-tool-calls-invalid-args.json"))

            val error = assertThrows<LlmException.InvalidOutput> { component.complete(request) }

            assertEquals("""{"town":"Madrid"}""", error.raw)
            assertEquals(2, error.assistantTurn.toolCalls.size)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a schema that isn't valid JSON fails as InvalidRequest before anything is sent`() =
        runTest {
            assertThrows<LlmException.InvalidRequest> { component.complete(request.copy(responseSchemaJson = "{not json")) }

            assertEquals(0, server.requestCount)
        }

    private fun fixtureText(name: String) = javaClass.getResource("/llm/openai/$name")!!.readText()

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
