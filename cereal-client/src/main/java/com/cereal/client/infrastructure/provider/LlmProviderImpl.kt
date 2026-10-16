package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.model.llm.LlmConnection
import com.cereal.client.domain.model.llm.LlmFinishReason
import com.cereal.client.domain.model.llm.LlmMessage
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.provider.LlmProvider
import com.cereal.client.infrastructure.data.datasource.network.llm.AnthropicLlmDataSource
import com.cereal.client.infrastructure.data.datasource.network.llm.LlmDataSource
import com.cereal.client.infrastructure.data.datasource.network.llm.OpenAiCompatibleLlmDataSource
import io.github.optimumcode.json.schema.ErrorCollector
import io.github.optimumcode.json.schema.JsonSchema
import kotlinx.serialization.json.Json

/**
 * Routes each call to the datasource for the [LlmProviderType] in use, and checks the output against
 * the request's schemas. Each datasource maps one API format over the shared HTTP layer.
 */
class LlmProviderImpl(
    private val anthropic: AnthropicLlmDataSource,
    private val openAiCompatible: OpenAiCompatibleLlmDataSource,
) : LlmProvider {
    override suspend fun listModels(connection: LlmConnection): List<LlmModel> = dataSourceFor(connection.provider).listModels(connection)

    /**
     * One datasource call, with the output checked against the request's schemas on every provider (some
     * don't enforce them). Schemas that don't parse fail before anything is sent. A failed check is
     * [LlmCallException.InvalidOutput] and is never retried: a retry would spend tokens the script
     * didn't ask for. Tool arguments are checked only on a tool-use stop, the answer only on an end-turn
     * stop.
     */
    override suspend fun complete(
        config: LlmProviderConfig,
        request: LlmCompletionRequest,
    ): LlmCompletion {
        val toolSchemas = request.tools.associate { it.name to compile(it.inputSchemaJson, "tool \"${it.name}\"") }
        val responseSchema = request.responseSchemaJson?.let { compile(it, "response") }
        val completion = dataSourceFor(config.provider).complete(config, request)
        val assistant = LlmMessage.Assistant(completion.text, completion.toolCalls, completion.providerPayload)
        val invalid = { raw: String -> LlmCallException.InvalidOutput(raw, assistant, completion.model, completion.usage) }
        return when (completion.finishReason) {
            LlmFinishReason.TOOL_USE -> {
                completion.toolCalls.forEach { call ->
                    val schema = toolSchemas[call.name] ?: throw invalid(call.argumentsJson)
                    schema.check(call.argumentsJson, invalid)
                }
                completion
            }

            LlmFinishReason.END_TURN -> {
                if (responseSchema == null) return completion
                responseSchema.check(completion.text, invalid)
                completion.copy(structuredJson = completion.text)
            }

            // MaxTokens/Refusal: output may be cut off, so nothing is validated; the script sees the stop reason.
            else -> {
                completion
            }
        }
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
        invalid: (String) -> LlmCallException.InvalidOutput,
    ) {
        val value = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
        if (value == null || !validate(value, ErrorCollector.EMPTY)) throw invalid(raw)
    }

    private fun dataSourceFor(provider: LlmProviderType): LlmDataSource =
        when (provider) {
            LlmProviderType.ANTHROPIC -> anthropic
            LlmProviderType.OPENAI_COMPATIBLE -> openAiCompatible
        }
}
