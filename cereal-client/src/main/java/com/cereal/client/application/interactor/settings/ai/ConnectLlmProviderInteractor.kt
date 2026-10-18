package com.cereal.client.application.interactor.settings.ai

import com.cereal.client.application.Interactor
import com.cereal.client.application.SensitiveParams
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.provider.LlmProvider

/**
 * The Home setup card's Connect: tests the connection with one model-list call, then saves the config
 * through [SaveLlmProviderConfigInteractor] and returns it. Throws
 * [com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException] for invalid input and
 * [com.cereal.client.domain.model.exception.LlmCallException] when the test call fails; either way
 * nothing is saved.
 */
class ConnectLlmProviderInteractor(
    private val llmProvider: LlmProvider,
    private val saveLlmProviderConfigInteractor: SaveLlmProviderConfigInteractor,
) : Interactor<LlmProviderConfig, ConnectLlmProviderInteractor.Params>() {
    override suspend fun run(params: Params): LlmProviderConfig {
        val save = SaveLlmProviderConfigInteractor.Params(params.provider, params.baseUrl, params.apiKey, params.model)
        val config = save.toConfig()
        llmProvider.listModels(config.connection)
        saveLlmProviderConfigInteractor.run(save)
        return config
    }

    // NB: carries the API key — see [SensitiveParams].
    data class Params(
        val provider: LlmProviderType,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
    ) : SensitiveParams
}
