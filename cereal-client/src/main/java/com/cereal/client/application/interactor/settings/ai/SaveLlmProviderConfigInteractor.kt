package com.cereal.client.application.interactor.settings.ai

import com.cereal.client.application.Interactor
import com.cereal.client.application.SensitiveParams
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.repository.LlmProviderConfigRepository

/**
 * Validates raw Settings → AI input into an [LlmProviderConfig] and saves it, replacing any previous
 * config. Throws [com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException] (nothing
 * saved) when the input is invalid. Saving does not require a passing connection test.
 */
class SaveLlmProviderConfigInteractor(
    private val llmProviderConfigRepository: LlmProviderConfigRepository,
) : Interactor<Unit, SaveLlmProviderConfigInteractor.Params>() {
    override suspend fun run(params: Params) {
        llmProviderConfigRepository.save(
            LlmProviderConfig.of(params.provider, params.baseUrl, params.apiKey, params.model),
        )
    }

    // NB: carries the API key — see [SensitiveParams].
    data class Params(
        val provider: LlmProviderType,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
    ) : SensitiveParams
}
