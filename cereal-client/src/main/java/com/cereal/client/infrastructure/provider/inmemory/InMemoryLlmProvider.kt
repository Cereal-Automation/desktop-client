package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.model.llm.LlmConnection
import com.cereal.client.domain.model.llm.LlmFinishReason
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmTokenUsage
import com.cereal.client.domain.provider.LlmProvider
import kotlinx.coroutines.delay

/**
 * In-memory [LlmProvider] for the `mock` flavor and tests: no network. Records every call in
 * [requests]; [responder] decides the answer (default: a fixed sandbox reply from the configured model).
 * Set [listModelsError] to make the connection test ([listModels]) fail, and [listModelsLatencyMillis]
 * to make it take (virtual) time; every [listModels] call is recorded in [listModelsRequests].
 */
class InMemoryLlmProvider(
    var models: List<LlmModel> = listOf(LlmModel("sandbox-model")),
    var responder: suspend (LlmProviderConfig, LlmCompletionRequest) -> LlmCompletion = { config, _ -> sandboxReply(config.model) },
    var listModelsError: Exception? = null,
    var listModelsLatencyMillis: Long = 0,
) : LlmProvider {
    private val recorded = mutableListOf<Pair<LlmProviderConfig, LlmCompletionRequest>>()
    private val listed = mutableListOf<LlmConnection>()

    /** Every [complete] call in order, with the config it was made with. */
    val requests: List<Pair<LlmProviderConfig, LlmCompletionRequest>> get() = recorded.toList()

    /** Every [listModels] call in order, recorded when it starts. */
    val listModelsRequests: List<LlmConnection> get() = listed.toList()

    override suspend fun listModels(connection: LlmConnection): List<LlmModel> {
        listed += connection
        delay(listModelsLatencyMillis)
        return listModelsError?.let { throw it } ?: models
    }

    override suspend fun complete(
        config: LlmProviderConfig,
        request: LlmCompletionRequest,
    ): LlmCompletion {
        recorded += config to request
        return responder(config, request)
    }

    companion object {
        fun sandboxReply(model: String): LlmCompletion =
            LlmCompletion(
                text = "This is a sandbox reply.",
                toolCalls = emptyList(),
                structuredJson = null,
                finishReason = LlmFinishReason.END_TURN,
                usage = LlmTokenUsage(inputUncached = 0, cacheRead = 0, cacheWrite = 0, output = 0),
                model = model,
                providerPayload = null,
            )
    }
}
