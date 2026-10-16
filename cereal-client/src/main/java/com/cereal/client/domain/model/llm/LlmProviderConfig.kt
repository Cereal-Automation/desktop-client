package com.cereal.client.domain.model.llm

import com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.url.WebUrl

/**
 * The user's app-wide LLM provider setup: which API, where it lives, the key, and the model every
 * script call uses. One per Cereal user; no saved config means provider "None".
 *
 * Invalid combinations cannot be constructed: the base URL is an http(s) URL with a host, the model is
 * non-blank, and [LlmProviderType.requiresApiKey] providers carry a key. Violations throw
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
    init {
        if (baseUrl.host.isNullOrBlank()) throw InvalidLlmProviderConfigException(Field.BASE_URL, INVALID_BASE_URL)
        if (model.isBlank()) throw InvalidLlmProviderConfigException(Field.MODEL, MISSING_MODEL)
        if (provider.requiresApiKey && apiKey?.reveal().isNullOrBlank()) {
            throw InvalidLlmProviderConfigException(Field.API_KEY, MISSING_API_KEY)
        }
    }

    enum class Field { BASE_URL, API_KEY, MODEL }

    companion object {
        private const val INVALID_BASE_URL = "Enter an http:// or https:// base URL."
        private const val MISSING_MODEL = "Enter a model."
        private const val MISSING_API_KEY = "This provider needs an API key."

        /**
         * Builds a config from raw form input: trims everything, drops trailing slashes from the base
         * URL, and treats a blank key as no key.
         */
        fun of(
            provider: LlmProviderType,
            baseUrl: String,
            apiKey: String,
            model: String,
        ): LlmProviderConfig =
            LlmProviderConfig(
                provider = provider,
                baseUrl =
                    WebUrl.parse(baseUrl.trim().trimEnd('/'))
                        ?: throw InvalidLlmProviderConfigException(Field.BASE_URL, INVALID_BASE_URL),
                apiKey = apiKey.trim().takeIf { it.isNotEmpty() }?.let(::Secret),
                model = model.trim(),
            )
    }
}
