package com.cereal.client.infrastructure.provider.llm

import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.model.llm.LlmFinishReason
import com.cereal.client.domain.model.llm.LlmMessage
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmTokenUsage
import com.cereal.client.domain.model.llm.LlmToolCall
import com.cereal.client.domain.model.llm.LlmToolChoice
import com.cereal.client.domain.model.llm.LlmUserPart
import com.cereal.client.domain.provider.LlmProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/**
 * Anthropic Messages (`POST {baseUrl}/messages`). Fixed tuning: adaptive thinking, effort `medium`,
 * automatic prompt caching. The response's content blocks (thinking included) become the assistant
 * turn's provider payload and are sent back verbatim when that turn returns.
 */
class AnthropicLlmBackend(
    private val http: LlmHttp,
) : LlmProvider {
    // ponytail: Settings → AI's model list and connection test arrive with #110.
    override suspend fun listModels(config: LlmProviderConfig): List<LlmModel> = throw LlmCallException.Unavailable("Listing models isn't supported by this version of Cereal yet.")

    override suspend fun complete(
        config: LlmProviderConfig,
        request: LlmCompletionRequest,
    ): LlmCompletion {
        val body =
            http.postJson(
                url = "${config.baseUrl.value}/messages",
                headers =
                    buildMap {
                        config.apiKey?.let { put("x-api-key", it.reveal()) }
                        put("anthropic-version", API_VERSION)
                    },
                body = request.toBody(config.model).toString(),
                model = config.model,
                parseError = ::parseError,
            )
        return http.readResponse { json.decodeFromString<MessageResponse>(body).toCompletion() }
    }

    private fun LlmCompletionRequest.toBody(model: String): JsonObject =
        buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            system?.let { put("system", it) }
            putJsonArray("messages") { messages.forEach { add(it.toJson()) } }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", json.parseToJsonElement(tool.inputSchemaJson))
                        }
                    }
                }
                putJsonObject("tool_choice") {
                    put(
                        "type",
                        when (toolChoice) {
                            LlmToolChoice.AUTO -> "auto"
                            LlmToolChoice.NONE -> "none"
                        },
                    )
                }
            }
            putJsonObject("thinking") { put("type", "adaptive") }
            putJsonObject("output_config") {
                put("effort", "medium")
                responseSchemaJson?.let { schema ->
                    putJsonObject("format") {
                        put("type", "json_schema")
                        put("schema", json.parseToJsonElement(schema))
                    }
                }
            }
            putJsonObject("cache_control") { put("type", "ephemeral") }
        }

    private fun LlmMessage.toJson(): JsonObject =
        when (this) {
            is LlmMessage.User -> {
                buildJsonObject {
                    put("role", "user")
                    // Anthropic rejects a user turn whose tool_result blocks don't come first.
                    putJsonArray("content") { parts.sortedBy { it !is LlmUserPart.ToolResult }.forEach { add(it.toJson()) } }
                }
            }

            is LlmMessage.Assistant -> {
                buildJsonObject {
                    put("role", "assistant")
                    put(
                        "content",
                        providerPayload?.let { json.parseToJsonElement(it).jsonArray }
                            ?: buildJsonArray { addJsonObject { textBlock(text) } },
                    )
                }
            }
        }

    private fun LlmUserPart.toJson(): JsonObject =
        when (this) {
            is LlmUserPart.Text -> {
                buildJsonObject { textBlock(text) }
            }

            is LlmUserPart.Image -> {
                buildJsonObject {
                    put("type", "image")
                    putJsonObject("source") {
                        put("type", "base64")
                        put("media_type", mimeType)
                        put("data", Base64.getEncoder().encodeToString(bytes))
                    }
                }
            }

            is LlmUserPart.ToolResult -> {
                buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", toolCallId)
                    put("content", text)
                    put("is_error", isError)
                }
            }
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.textBlock(text: String) {
        put("type", "text")
        put("text", text)
    }

    private fun MessageResponse.toCompletion() =
        LlmCompletion(
            text = blocks("text").joinToString("") { it["text"]!!.jsonPrimitive.content },
            toolCalls =
                blocks("tool_use").map {
                    LlmToolCall(it["id"]!!.jsonPrimitive.content, it["name"]!!.jsonPrimitive.content, it["input"]!!.jsonObject.toString())
                },
            structuredJson = null,
            finishReason =
                when (stopReason) {
                    "max_tokens", "model_context_window_exceeded" -> LlmFinishReason.MAX_TOKENS
                    "refusal" -> LlmFinishReason.REFUSAL
                    "tool_use" -> LlmFinishReason.TOOL_USE
                    else -> LlmFinishReason.END_TURN
                },
            usage =
                LlmTokenUsage(
                    inputUncached = usage.inputTokens,
                    cacheRead = usage.cacheReadInputTokens ?: 0,
                    cacheWrite = usage.cacheCreationInputTokens ?: 0,
                    output = usage.outputTokens,
                ),
            model = model,
            providerPayload = content.toString(),
        )

    private fun MessageResponse.blocks(type: String): List<JsonObject> = content.filterIsInstance<JsonObject>().filter { it["type"]?.jsonPrimitive?.content == type }

    /** `{"type":"error","error":{"type":"not_found_error","message":"model: <id>"}}` means an unknown model. */
    private fun parseError(body: String): LlmHttp.ProviderError {
        val error = runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull() ?: return LlmHttp.ProviderError(null)
        return LlmHttp.ProviderError(
            message = error.message,
            modelNotFound = error.type == "not_found_error" && error.message.orEmpty().startsWith("model:"),
        )
    }

    @Serializable
    private data class ErrorResponse(
        val error: ErrorDetail,
    )

    @Serializable
    private data class ErrorDetail(
        val type: String? = null,
        val message: String? = null,
    )

    @Serializable
    private data class MessageResponse(
        val model: String,
        val content: JsonArray,
        @SerialName("stop_reason") val stopReason: String? = null,
        val usage: Usage,
    )

    @Serializable
    private data class Usage(
        @SerialName("input_tokens") val inputTokens: Int,
        @SerialName("output_tokens") val outputTokens: Int,
        @SerialName("cache_creation_input_tokens") val cacheCreationInputTokens: Int? = null,
        @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Int? = null,
    )

    private companion object {
        const val API_VERSION = "2023-06-01"
        val json = Json { ignoreUnknownKeys = true }
    }
}
