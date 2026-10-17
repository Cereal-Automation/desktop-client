package com.cereal.client.domain.provider

import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.model.llm.LlmConnection
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig

/**
 * Adapter to the user's LLM provider API (Anthropic Messages or OpenAI-compatible Chat Completions).
 * Stateless: the caller passes the [LlmProviderConfig] to use on every call, so a saved change applies
 * to the next call.
 *
 * Failures are thrown as [com.cereal.client.domain.model.exception.LlmCallException] subclasses;
 * cancellation propagates untouched.
 */
interface LlmProvider {
    /** `GET {baseUrl}/models`; needs no model. Also serves as Settings → AI's connection test. */
    suspend fun listModels(connection: LlmConnection): List<LlmModel>

    /** Exactly one model call with [config]'s model. */
    suspend fun complete(
        config: LlmProviderConfig,
        request: LlmCompletionRequest,
    ): LlmCompletion
}
