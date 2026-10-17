package com.cereal.client.infrastructure.data.datasource.network.llm

import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.model.llm.LlmConnection
import com.cereal.client.domain.model.llm.LlmFinishReason
import com.cereal.client.domain.model.llm.LlmMessage
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmTokenUsage
import com.cereal.client.domain.model.llm.LlmToolCall
import com.cereal.client.domain.model.llm.LlmUserPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/**
 * OpenAI-compatible Chat Completions (`POST {baseUrl}/chat/completions`): OpenAI, OpenRouter, Gemini's
 * OpenAI layer, Ollama, LM Studio. The key goes out as a bearer token only when one is set (local
 * servers need none). The response's `message` object becomes the assistant turn's provider payload and
 * is sent back verbatim, so provider extras (e.g. Gemini's `extra_content` thought signatures) survive.
 */
class OpenAiCompatibleLlmDataSource(
    private val http: LlmHttp,
) : LlmDataSource {
    override suspend fun listModels(connection: LlmConnection): List<LlmModel> = http.getModels("${connection.baseUrl.value}/models", headers(connection))

    override suspend fun complete(
        config: LlmProviderConfig,
        request: LlmCompletionRequest,
    ): LlmCompletion {
        val body =
            http.postJson(
                url = "${config.baseUrl.value}/chat/completions",
                headers = headers(config.connection),
                body = request.toBody(config.model).toString(),
                model = config.model,
                isModelNotFound = ::isModelNotFound,
            )
        return http.readResponse { json.decodeFromString<ChatResponse>(body).toCompletion() }
    }

    private fun headers(connection: LlmConnection) = connection.apiKey?.let { mapOf("Authorization" to "Bearer ${it.reveal()}") }.orEmpty()

    private fun LlmCompletionRequest.toBody(model: String): JsonObject =
        buildJsonObject {
            put("model", model)
            put("max_completion_tokens", maxTokens)
            putJsonArray("messages") {
                system?.let { system ->
                    addJsonObject {
                        put("role", "system")
                        put("content", system)
                    }
                }
                messages.forEach { message -> message.toJson().forEach { add(it) } }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", json.parseToJsonElement(tool.inputSchemaJson))
                            }
                        }
                    }
                }
                // Ollama ignores tool_choice.
                put("tool_choice", toolChoice.wireName)
            }
            responseSchemaJson?.let { schema ->
                putJsonObject("response_format") {
                    put("type", "json_schema")
                    putJsonObject("json_schema") {
                        // OpenAI requires a name; scripts don't give one. No `strict`: OpenAI's strict mode
                        // demands every property be required, which the Anthropic subset doesn't.
                        put("name", "response")
                        put("schema", json.parseToJsonElement(schema))
                    }
                }
            }
        }

    /** One user turn may become several messages: one `role:"tool"` message per tool result, then the rest. */
    private fun LlmMessage.toJson(): List<JsonObject> =
        when (this) {
            is LlmMessage.User -> {
                val content = parts.filterNot { it is LlmUserPart.ToolResult }
                parts.filterIsInstance<LlmUserPart.ToolResult>().map { it.toToolMessage() } +
                    listOfNotNull(
                        content.takeIf { it.isNotEmpty() }?.let {
                            buildJsonObject {
                                put("role", "user")
                                putJsonArray("content") { content.forEach { add(it.toJson()) } }
                            }
                        },
                    )
            }

            is LlmMessage.Assistant -> {
                listOf(
                    providerPayload?.let { http.payloadAs<JsonObject>(it) }
                        ?: buildJsonObject {
                            put("role", "assistant")
                            put("content", text)
                        },
                )
            }
        }

    /** Chat Completions has no error flag on tool results, so a failed tool says so in its content. */
    private fun LlmUserPart.ToolResult.toToolMessage() =
        buildJsonObject {
            put("role", "tool")
            put("tool_call_id", toolCallId)
            put("content", if (isError) "Error: $text" else text)
        }

    private fun LlmUserPart.toJson(): JsonObject =
        when (this) {
            is LlmUserPart.Text -> {
                buildJsonObject {
                    put("type", "text")
                    put("text", text)
                }
            }

            is LlmUserPart.Image -> {
                buildJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") { put("url", "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}") }
                }
            }

            is LlmUserPart.ToolResult -> {
                error("tool results are sent as role:tool messages")
            }
        }

    private fun ChatResponse.toCompletion(): LlmCompletion {
        val choice = choices.first()
        val message = choice.message
        val cached = usage.promptTokensDetails?.cachedTokens ?: 0
        // Unverified on OpenAI (Gemini sends neither): cache_write_tokens is assumed to be inside prompt_tokens, like cached_tokens.
        val cacheWrite = usage.promptTokensDetails?.cacheWriteTokens ?: 0
        return LlmCompletion(
            text =
                message["content"]
                    ?.takeIf { it !is JsonNull }
                    ?.jsonPrimitive
                    ?.content
                    .orEmpty(),
            toolCalls = message.toolCalls(),
            structuredJson = null,
            finishReason =
                when {
                    choice.finishReason == "content_filter" -> LlmFinishReason.REFUSAL
                    message["refusal"]?.takeIf { it !is JsonNull } != null -> LlmFinishReason.REFUSAL
                    choice.finishReason == "length" -> LlmFinishReason.MAX_TOKENS
                    choice.finishReason == "tool_calls" -> LlmFinishReason.TOOL_USE
                    else -> LlmFinishReason.END_TURN
                },
            usage =
                LlmTokenUsage(
                    inputUncached = usage.promptTokens - cached - cacheWrite,
                    cacheRead = cached,
                    cacheWrite = cacheWrite,
                    // Gemini leaves thinking tokens out of completion_tokens but counts them in total_tokens.
                    output = maxOf(usage.completionTokens, (usage.totalTokens ?: 0) - usage.promptTokens),
                ),
            model = model,
            // `annotations` is response-only on OpenAI; everything else goes back as received.
            providerPayload = JsonObject(message - "annotations").toString(),
        )
    }

    /** `arguments` is a JSON string holding the JSON object; some compatible servers send the object itself. */
    private fun JsonObject.toolCalls(): List<LlmToolCall> =
        (get("tool_calls") as? JsonArray).orEmpty().map { call ->
            val function = call.jsonObject.getValue("function").jsonObject
            val arguments = function.getValue("arguments")
            LlmToolCall(
                id =
                    call.jsonObject
                        .getValue("id")
                        .jsonPrimitive.content,
                name = function.getValue("name").jsonPrimitive.content,
                argumentsJson = (arguments as? JsonPrimitive)?.content ?: arguments.toString(),
            )
        }

    /** OpenAI: `{"error":{"code":"model_not_found",...}}`. Ollama: `{"error":{"message":"model \"x\" not found, ..."}}`. */
    private fun isModelNotFound(error: LlmHttp.ErrorDetail): Boolean {
        val message = error.message.orEmpty()
        return (error.code as? JsonPrimitive)?.contentOrNull == "model_not_found" || (message.startsWith("model ") && message.contains("not found"))
    }

    @Serializable
    private data class ChatResponse(
        val model: String,
        val choices: List<Choice>,
        // Some local servers omit usage.
        val usage: Usage = Usage(promptTokens = 0, completionTokens = 0),
    )

    @Serializable
    private data class Choice(
        val message: JsonObject,
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    private data class Usage(
        @SerialName("prompt_tokens") val promptTokens: Int,
        @SerialName("completion_tokens") val completionTokens: Int,
        @SerialName("total_tokens") val totalTokens: Int? = null,
        @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null,
    )

    @Serializable
    private data class PromptTokensDetails(
        @SerialName("cached_tokens") val cachedTokens: Int? = null,
        @SerialName("cache_write_tokens") val cacheWriteTokens: Int? = null,
    )

    private companion object {
        val json = LlmHttp.json
    }
}
