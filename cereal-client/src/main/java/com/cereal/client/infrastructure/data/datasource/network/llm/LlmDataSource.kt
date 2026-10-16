package com.cereal.client.infrastructure.data.datasource.network.llm

import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.model.llm.LlmConnection
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig

/**
 * Network seam for one LLM API format. Implementations ([AnthropicLlmDataSource],
 * [OpenAiCompatibleLlmDataSource]) map the client's types to their wire JSON and back, on top of the
 * shared [LlmHttp]; they throw only [com.cereal.client.domain.model.exception.LlmCallException]s.
 */
interface LlmDataSource {
    /** `GET {baseUrl}/models`. */
    suspend fun listModels(connection: LlmConnection): List<LlmModel>

    /** Exactly one model call with [config]'s model; no output validation. */
    suspend fun complete(
        config: LlmProviderConfig,
        request: LlmCompletionRequest,
    ): LlmCompletion
}
