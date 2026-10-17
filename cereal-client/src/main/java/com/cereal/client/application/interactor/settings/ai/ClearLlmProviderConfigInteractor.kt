package com.cereal.client.application.interactor.settings.ai

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.LlmProviderConfigRepository

/** Deletes the user's whole LLM provider config, key included (provider "None" + Save). */
class ClearLlmProviderConfigInteractor(
    private val llmProviderConfigRepository: LlmProviderConfigRepository,
) : Interactor<Unit, Interactor.None>() {
    override suspend fun run(params: None) {
        llmProviderConfigRepository.clear()
    }
}
