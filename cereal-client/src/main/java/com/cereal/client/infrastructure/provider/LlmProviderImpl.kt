package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.provider.LlmProvider
import com.cereal.client.infrastructure.provider.llm.AnthropicLlmBackend
import com.cereal.client.infrastructure.provider.llm.LlmHttp
import com.cereal.client.infrastructure.provider.llm.OpenAiCompatibleLlmBackend
import okhttp3.OkHttpClient
import kotlin.time.Duration

/**
 * Routes each call to the backend for the config's [LlmProviderType]. Backends plug in here: each
 * implements [LlmProvider] on top of the shared [LlmHttp] (error mapping, retries, privacy rules) and
 * only maps its own request/response format.
 *
 * [retryDelays] is the wait before each retry of a 429/5xx; tests pass zeros.
 */
class LlmProviderImpl(
    client: OkHttpClient,
    retryDelays: List<Duration> = LlmHttp.DEFAULT_RETRY_DELAYS,
) : LlmProvider {
    private val http = LlmHttp(client, retryDelays)
    private val anthropic = AnthropicLlmBackend(http)
    private val openAiCompatible = OpenAiCompatibleLlmBackend(http)

    override suspend fun listModels(config: LlmProviderConfig): List<LlmModel> = backendFor(config.provider).listModels(config)

    override suspend fun complete(
        config: LlmProviderConfig,
        request: LlmCompletionRequest,
    ): LlmCompletion = backendFor(config.provider).complete(config, request)

    private fun backendFor(provider: LlmProviderType): LlmProvider =
        when (provider) {
            LlmProviderType.ANTHROPIC -> anthropic
            LlmProviderType.OPENAI_COMPATIBLE -> openAiCompatible
        }
}
