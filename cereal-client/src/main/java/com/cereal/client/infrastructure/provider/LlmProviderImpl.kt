package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.provider.LlmProvider

/**
 * Routes each call to the backend for the config's [LlmProviderType]. Backends plug in here: the
 * Anthropic Messages backend (#108) and the OpenAI-compatible Chat Completions backend (#109) each
 * implement [LlmProvider] and replace their branch below.
 */
class LlmProviderImpl : LlmProvider {
    override suspend fun listModels(config: LlmProviderConfig): List<LlmModel> = backendFor(config.provider).listModels(config)

    override suspend fun complete(
        config: LlmProviderConfig,
        request: LlmCompletionRequest,
    ): LlmCompletion = backendFor(config.provider).complete(config, request)

    private fun backendFor(provider: LlmProviderType): LlmProvider =
        when (provider) {
            // ponytail: no backend yet; #108 / #109 return their backend here.
            LlmProviderType.ANTHROPIC, LlmProviderType.OPENAI_COMPATIBLE -> {
                throw LlmCallException.Unavailable("This AI provider isn't supported by this version of Cereal yet.")
            }
        }
}
