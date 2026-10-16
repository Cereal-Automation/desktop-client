package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.model.llm.LlmFinishReason
import com.cereal.client.domain.model.llm.LlmMessage
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.provider.LlmProvider
import com.cereal.client.infrastructure.provider.llm.AnthropicLlmBackend
import com.cereal.client.infrastructure.provider.llm.LlmHttp
import com.cereal.client.infrastructure.provider.llm.OpenAiCompatibleLlmBackend
import io.github.optimumcode.json.schema.ErrorCollector
import io.github.optimumcode.json.schema.JsonSchema
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlin.time.Duration

/**
 * Routes each call to the backend for the config's [LlmProviderType]. Backends plug in here: each
 * implements [LlmProvider] on top of the shared [LlmHttp] (error mapping, retries, privacy rules) and
 * only maps its own request/response format.
 *
 * [retryDelays] is the wait before each retry of a 429/5xx; tests pass zeros.
 */
class LlmProviderImpl(
    client: OkHttpClient,
    retryDelays: List<Duration> = LlmHttp.DEFAULT_RETRY_DELAYS,
) : LlmProvider {
    private val http = LlmHttp(client, retryDelays)
    private val anthropic = AnthropicLlmBackend(http)
    private val openAiCompatible = OpenAiCompatibleLlmBackend(http)

    override suspend fun listModels(config: LlmProviderConfig): List<LlmModel> = backendFor(config.provider).listModels(config)

    /**
     * One backend call, with the output checked against the request's schemas on every provider (some
     * don't enforce them). Schemas that don't parse fail before anything is sent. A failed check is
     * [LlmCallException.InvalidOutput] and is never retried: a retry would spend tokens the script
     * didn't ask for.
     */
    override suspend fun complete(
        config: LlmProviderConfig,
        request: LlmCompletionRequest,
    ): LlmCompletion {
        val toolSchemas = request.tools.associate { it.name to compile(it.inputSchemaJson, "tool \"${it.name}\"") }
        val responseSchema = request.responseSchemaJson?.let { compile(it, "response") }
        val completion = backendFor(config.provider).complete(config, request)
        val assistant = LlmMessage.Assistant(completion.text, completion.toolCalls, completion.providerPayload)
        completion.toolCalls.forEach { call ->
            val schema = toolSchemas[call.name] ?: throw LlmCallException.InvalidOutput(call.argumentsJson, assistant)
            schema.check(call.argumentsJson, assistant)
        }
        if (responseSchema == null || completion.finishReason != LlmFinishReason.END_TURN) return completion
        responseSchema.check(completion.text, assistant)
        return completion.copy(structuredJson = completion.text)
    }

    private fun compile(
        schemaJson: String,
        what: String,
    ): JsonSchema =
        try {
            JsonSchema.fromDefinition(schemaJson)
        } catch (_: IllegalArgumentException) {
            // SerializationException included; its message would quote the schema.
            throw LlmCallException.InvalidRequest("The $what schema isn't a valid JSON Schema.")
        }

    private fun JsonSchema.check(
        raw: String,
        assistant: LlmMessage.Assistant,
    ) {
        val value = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
        if (value == null || !validate(value, ErrorCollector.EMPTY)) throw LlmCallException.InvalidOutput(raw, assistant)
    }

    private fun backendFor(provider: LlmProviderType): LlmProvider =
        when (provider) {
            LlmProviderType.ANTHROPIC -> anthropic
            LlmProviderType.OPENAI_COMPATIBLE -> openAiCompatible
        }
}
