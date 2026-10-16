package com.cereal.client.application.interactor.settings.ai

import com.cereal.client.application.FlowInteractor
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.repository.LlmProviderConfigRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Reads the user's saved [LlmProviderConfig] once, emitting `null` when provider is "None". A
 * [FlowInteractor] only because a one-shot [com.cereal.client.application.Interactor] can't return null.
 */
@OptIn(FlowPreview::class)
class GetLlmProviderConfigInteractor(
    private val llmProviderConfigRepository: LlmProviderConfigRepository,
) : FlowInteractor<LlmProviderConfig?, FlowInteractor.None>() {
    override suspend fun run(params: None): Flow<LlmProviderConfig?> = flow { emit(llmProviderConfigRepository.get()) }
}
