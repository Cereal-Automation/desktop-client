package com.cereal.client.application.interactor.settings.ai

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.provider.LlmProvider
import com.cereal.client.domain.repository.LlmProviderConfigRepository

/**
 * The Home setup card's Connect: tests the connection with one model-list call, then saves the config
 * and returns it. Throws [com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException]
 * for invalid input and [com.cereal.client.domain.model.exception.LlmCallException] when the test call
 * fails; either way nothing is saved.
 */
class ConnectLlmProviderInteractor(
    private val llmProvider: LlmProvider,
    private val llmProviderConfigRepository: LlmProviderConfigRepository,
) : Interactor<LlmProviderConfig, SaveLlmProviderConfigInteractor.Params>() {
    override suspend fun run(params: SaveLlmProviderConfigInteractor.Params): LlmProviderConfig {
        val config = LlmProviderConfig.of(params.provider, params.baseUrl, params.apiKey, params.model)
        llmProvider.listModels(config.connection)
        llmProviderConfigRepository.save(config)
        return config
    }
}
