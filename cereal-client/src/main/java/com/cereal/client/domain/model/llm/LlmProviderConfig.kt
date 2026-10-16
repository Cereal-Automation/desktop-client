package com.cereal.client.domain.model.llm

import com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.url.WebUrl

/**
 * The user's app-wide LLM provider setup: which API, where it lives, the key, and the model every
 * script call uses. One per Cereal user; no saved config means provider "None".
 *
 * Invalid combinations cannot be constructed: the [connection] rules hold (see [LlmConnection]) and the
 * model is non-blank. Violations throw
 * [InvalidLlmProviderConfigException] naming the offending [Field], so the form can mark it.
 *
 * [toString] masks the key (see [Secret]).
 */
data class LlmProviderConfig(
    val provider: LlmProviderType,
    val baseUrl: WebUrl,
    val apiKey: Secret?,
    val model: String,
) {
    /** Validates the base URL and key on construction. */
    val connection = LlmConnection(provider, baseUrl, apiKey)

    init {
        if (model.isBlank()) throw InvalidLlmProviderConfigException(Field.MODEL, MISSING_MODEL)
    }

    enum class Field { BASE_URL, API_KEY, MODEL }

    companion object {
        private const val MISSING_MODEL = "Enter a model."

        /** Builds a config from raw form input; see [LlmConnection.of]. The model is trimmed. */
        fun of(
            provider: LlmProviderType,
            baseUrl: String,
            apiKey: String,
            model: String,
        ): LlmProviderConfig = LlmConnection.of(provider, baseUrl, apiKey).let { LlmProviderConfig(it.provider, it.baseUrl, it.apiKey, model.trim()) }
    }
}
