package com.cereal.client.infrastructure.provider.llm

import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.model.llm.LlmFinishReason
import com.cereal.client.domain.model.llm.LlmMessage
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmTokenUsage
import com.cereal.client.domain.model.llm.LlmUserPart
import com.cereal.client.domain.provider.LlmProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
class OpenAiCompatibleLlmBackend(
    private val http: LlmHttp,
) : LlmProvider {
    override suspend fun listModels(config: LlmProviderConfig): List<LlmModel> {
        val body =
            http.getJson(
                url = "${config.baseUrl.value}/models",
                headers = config.apiKey?.let { mapOf("Authorization" to "Bearer ${it.reveal()}") }.orEmpty(),
            )
        return http.readResponse { json.decodeFromString<ModelList>(body).data.map { LlmModel(it.id) } }
    }

    @Serializable
    private data class ModelList(
        val data: List<ModelEntry>,
    )

    @Serializable
    private data class ModelEntry(
        val id: String,
    )

    override suspend fun complete(
        config: LlmProviderConfig,
        request: LlmCompletionRequest,
    ): LlmCompletion {
        val body =
            http.postJson(
                url = "${config.baseUrl.value}/chat/completions",
                headers = config.apiKey?.let { mapOf("Authorization" to "Bearer ${it.reveal()}") }.orEmpty(),
                body = request.toBody(config.model).toString(),
                model = config.model,
                parseError = ::parseError,
            )
        return http.readResponse { json.decodeFromString<ChatResponse>(body).toCompletion() }
    }

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
                messages.forEach { add(it.toJson()) }
            }
        }

    private fun LlmMessage.toJson(): JsonObject =
        when (this) {
            is LlmMessage.User -> {
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") { parts.forEach { add(it.toJson()) } }
                }
            }

            is LlmMessage.Assistant -> {
                providerPayload?.let { json.parseToJsonElement(it).jsonObject }
                    ?: buildJsonObject {
                        put("role", "assistant")
                        put("content", text)
                    }
            }
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

            // ponytail: tool results arrive with #111.
            is LlmUserPart.ToolResult -> {
                throw LlmCallException.InvalidRequest("This version of Cereal can only send text to the AI provider.")
            }
        }

    private fun ChatResponse.toCompletion(): LlmCompletion {
        val choice = choices.firstOrNull() ?: error("no choices")
        val message = choice.message
        val cached = usage.promptTokensDetails?.cachedTokens ?: 0
        // Unverified (no live call): cache_write_tokens is assumed to be counted inside prompt_tokens, like cached_tokens.
        val cacheWrite = usage.promptTokensDetails?.cacheWriteTokens ?: 0
        return LlmCompletion(
            text =
                message["content"]
                    ?.takeIf { it !is JsonNull }
                    ?.jsonPrimitive
                    ?.content
                    .orEmpty(),
            toolCalls = emptyList(),
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
                    output = usage.completionTokens,
                ),
            model = model,
            // `annotations` is response-only on OpenAI; everything else goes back as received.
            providerPayload = JsonObject(message - "annotations").toString(),
        )
    }

    /**
     * OpenAI: `{"error":{"code":"model_not_found",...}}`. Ollama: `{"error":{"message":"model \"x\" not found, ..."}}`.
     * Bodies elsewhere may be any text, or an array (some proxies), so nothing here may throw.
     */
    private fun parseError(body: String): LlmHttp.ProviderError {
        val error = runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull() ?: return LlmHttp.ProviderError(null)
        val message = error.message
        return LlmHttp.ProviderError(
            message = message,
            modelNotFound =
                error.code?.jsonPrimitive?.contentOrNull == "model_not_found" ||
                    (message.orEmpty().startsWith("model ") && message.orEmpty().contains("not found")),
        )
    }

    @Serializable
    private data class ErrorResponse(
        val error: ErrorDetail,
    )

    // `code` is a string on OpenAI but a number on some compatible servers.
    @Serializable
    private data class ErrorDetail(
        val message: String? = null,
        val code: JsonElement? = null,
    )

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
        @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null,
    )

    @Serializable
    private data class PromptTokensDetails(
        @SerialName("cached_tokens") val cachedTokens: Int? = null,
        @SerialName("cache_write_tokens") val cacheWriteTokens: Int? = null,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
