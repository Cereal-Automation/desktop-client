package com.cereal.client.application.interactor.settings.ai

import com.cereal.client.application.Interactor
import com.cereal.client.application.SensitiveParams
import com.cereal.client.domain.model.llm.LlmConnection
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.provider.LlmProvider

/**
 * Fetches the provider's model list (`GET {baseUrl}/models`) for the Settings → AI form as typed, saved
 * or not; the same call is the form's connection test. Throws
 * [com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException] for invalid input and
 * [com.cereal.client.domain.model.exception.LlmCallException] when the provider call fails.
 */
class ListLlmModelsInteractor(
    private val llmProvider: LlmProvider,
) : Interactor<List<LlmModel>, ListLlmModelsInteractor.Params>() {
    override suspend fun run(params: Params): List<LlmModel> = llmProvider.listModels(LlmConnection.of(params.provider, params.baseUrl, params.apiKey))

    // NB: carries the API key — see [SensitiveParams].
    data class Params(
        val provider: LlmProviderType,
        val baseUrl: String,
        val apiKey: String,
    ) : SensitiveParams
}
