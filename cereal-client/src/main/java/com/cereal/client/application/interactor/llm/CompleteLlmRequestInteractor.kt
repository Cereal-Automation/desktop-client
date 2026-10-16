package com.cereal.client.application.interactor.llm

import com.cereal.client.application.Interactor
import com.cereal.client.application.SensitiveParams
import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.provider.LlmProvider
import com.cereal.client.domain.repository.LlmProviderConfigRepository

/**
 * Makes one model call with the user's current Settings → AI config, read fresh on every call so a
 * saved change reaches tasks that are already running. Throws [LlmCallException.NotConfigured] before
 * any network call when no provider is set up.
 *
 * The SDK bridge calls [run] directly: [invoke] would fold the typed [LlmCallException]s into a generic
 * failure.
 */
class CompleteLlmRequestInteractor(
    private val llmProviderConfigRepository: LlmProviderConfigRepository,
    private val llmProvider: LlmProvider,
) : Interactor<LlmCompletion, CompleteLlmRequestInteractor.Params>() {
    override suspend fun run(params: Params): LlmCompletion {
        val config = llmProviderConfigRepository.get() ?: throw LlmCallException.NotConfigured()
        return llmProvider.complete(config, params.request)
    }

    /** Sensitive: prompts and images are user/page content that must not reach Sentry. */
    data class Params(
        val request: LlmCompletionRequest,
    ) : SensitiveParams
}
