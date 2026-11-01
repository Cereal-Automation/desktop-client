package com.cereal.client.presentation.settings.ai

import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.model.url.WebUrl

/**
 * A ready-made starting point for an [LlmProviderConfig], shown as a tile in Settings → AI and the Home
 * setup card. A preset only prefills the form: it maps onto one of the two [LlmProviderType]s plus a
 * base URL and model, so nothing about it is stored.
 *
 * @param baseUrl prefilled base URL; empty for [OTHER], where the user types it.
 * @param model the model a fresh setup starts on when the provider lists it; empty where [defaultModel] picks
 *   from the list instead.
 * @param keyUrl where to get an API key, or null when the preset takes none or has no single page.
 */
enum class LlmPreset(
    val provider: LlmProviderType,
    val baseUrl: String,
    val model: String,
    val keyUrl: String?,
) {
    GEMINI(LlmProviderType.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash", "https://aistudio.google.com/apikey"),
    ANTHROPIC(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "", "https://console.anthropic.com/settings/keys"),
    OPENAI(LlmProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "gpt-5", "https://platform.openai.com/api-keys"),
    OPENROUTER(LlmProviderType.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "", "https://openrouter.ai/keys"),
    LOCAL(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "", null),
    OTHER(LlmProviderType.OPENAI_COMPATIBLE, "", "", null),
    ;

    /** Whether the model list waits for a key; [LOCAL] and [OTHER] only need the server address. */
    val needsKey: Boolean get() = this != LOCAL && this != OTHER

    /**
     * Whether [model] gets the Free badge: every Local model runs on the user's machine; Anthropic, OpenAI and
     * Gemini send no price, so never; anyone else when the list prices it free.
     */
    fun showsFree(model: LlmModel): Boolean =
        when (this) {
            LOCAL -> true
            ANTHROPIC, OPENAI, GEMINI -> false
            OPENROUTER, OTHER -> model.pricedFree
        }

    /**
     * The model a fresh setup selects from [models] (newest first). A preset with its own [model] selects it
     * when listed, else nothing. Others select, in a dated list, the newest priced-free model, else the newest,
     * never an `openrouter/` router (those switch model per call). Null leaves the pick to the user: never a
     * `-latest` alias or the first row of a dateless list.
     */
    fun defaultModel(models: List<LlmModel>): LlmModel? {
        if (model.isNotEmpty()) return models.firstOrNull { it.id == model }
        if (models.none { it.created != null }) return null
        val candidates = models.filterNot { it.id.startsWith("openrouter/") }
        return candidates.firstOrNull { it.pricedFree } ?: candidates.firstOrNull()
    }

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
