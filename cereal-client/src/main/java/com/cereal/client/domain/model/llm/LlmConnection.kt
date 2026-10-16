package com.cereal.client.domain.model.llm

import com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.url.WebUrl

/**
 * Where an LLM provider lives and how to authenticate: everything a call needs except the model, so the
 * model list can be fetched before one is picked.
 *
 * The base URL is an http(s) URL with a host and [LlmProviderType.requiresApiKey] providers carry a key;
 * violations throw [InvalidLlmProviderConfigException] naming the offending field. [toString] masks the
 * key (see [Secret]).
 */
data class LlmConnection(
    val provider: LlmProviderType,
    val baseUrl: WebUrl,
    val apiKey: Secret?,
) {
    init {
        if (baseUrl.host.isNullOrBlank()) throw InvalidLlmProviderConfigException(LlmProviderConfig.Field.BASE_URL, INVALID_BASE_URL)
        if (provider.requiresApiKey && apiKey?.reveal().isNullOrBlank()) {
            throw InvalidLlmProviderConfigException(LlmProviderConfig.Field.API_KEY, MISSING_API_KEY)
        }
    }

    companion object {
        private const val INVALID_BASE_URL = "Enter an http:// or https:// base URL."
        private const val MISSING_API_KEY = "This provider needs an API key."

        /**
         * Builds a connection from raw form input: trims everything, drops trailing slashes from the base
         * URL, and treats a blank key as no key.
         */
        fun of(
            provider: LlmProviderType,
            baseUrl: String,
            apiKey: String,
        ): LlmConnection =
            LlmConnection(
                provider = provider,
                baseUrl =
                    WebUrl.parse(baseUrl.trim().trimEnd('/'))
                        ?: throw InvalidLlmProviderConfigException(LlmProviderConfig.Field.BASE_URL, INVALID_BASE_URL),
                apiKey = apiKey.trim().takeIf { it.isNotEmpty() }?.let(::Secret),
            )
    }
}
