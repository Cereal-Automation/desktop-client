package com.cereal.client.application.interactor.settings.ai

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.LlmProviderConfigRepository

/** True when an AI provider config is saved. Checks presence only; makes no test call. */
class HasLlmProviderConfigInteractor(
    private val llmProviderConfigRepository: LlmProviderConfigRepository,
) : Interactor<Boolean, Interactor.None>() {
    override suspend fun run(params: None): Boolean = llmProviderConfigRepository.get() != null
}
