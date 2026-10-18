package com.cereal.client.presentation.settings.ai

import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.model.url.WebUrl

/**
 * A ready-made starting point for an [LlmProviderConfig], shown as a tile in Settings → AI and the Home
 * setup card. A preset only prefills the form: it maps onto one of the two [LlmProviderType]s plus a
 * base URL and model, so nothing about it is stored.
 *
 * @param baseUrl prefilled base URL; empty for [OTHER], where the user types it.
 * @param model prefilled model id; empty when the user picks one ([LOCAL] after connecting, [OTHER]).
 * @param keyUrl where to get an API key, or null when the preset takes none or has no single page.
 */
enum class LlmPreset(
    val provider: LlmProviderType,
    val baseUrl: String,
    val model: String,
    val keyUrl: String?,
) {
    GEMINI(LlmProviderType.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "https://aistudio.google.com/apikey"),
    ANTHROPIC(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5", "https://console.anthropic.com/settings/keys"),
    OPENAI(LlmProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "gpt-5", "https://platform.openai.com/api-keys"),
    OPENROUTER(LlmProviderType.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "anthropic/claude-opus-5-5", "https://openrouter.ai/keys"),
    LOCAL(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "", null),
    OTHER(LlmProviderType.OPENAI_COMPATIBLE, "", "", null),
    ;

    companion object {
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1")

        /** The preset a saved [config] opens on: matched by provider type and host, else [OTHER]. */
        fun of(config: LlmProviderConfig): LlmPreset {
            if (config.provider == LlmProviderType.ANTHROPIC) return ANTHROPIC
            val host = config.baseUrl.host
            if (host in LOCAL_HOSTS) return LOCAL
            return entries.firstOrNull { WebUrl.parse(it.baseUrl)?.host == host } ?: OTHER
        }
    }
}
