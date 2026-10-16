package com.cereal.client.application.interactor.settings.ai

import com.cereal.client.application.Interactor
import com.cereal.client.application.SensitiveParams
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
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
    override suspend fun run(params: Params): List<LlmModel> {
        // Listing doesn't use the model, and the user may not have picked one yet.
        val model = params.model.ifBlank { UNUSED_MODEL }
        return llmProvider.listModels(LlmProviderConfig.of(params.provider, params.baseUrl, params.apiKey, model))
    }

    // NB: carries the API key — see [SensitiveParams].
    data class Params(
        val provider: LlmProviderType,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
    ) : SensitiveParams

    private companion object {
        const val UNUSED_MODEL = "unused"
    }
}
