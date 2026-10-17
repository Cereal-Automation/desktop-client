package com.cereal.client.domain.model.llm

/**
 * The LLM API a user can configure in Settings → AI. "None" is the absence of a saved
 * [LlmProviderConfig], not a value here.
 *
 * @param defaultBaseUrl pre-filled in the form when the provider is chosen. Endpoints are appended to
 *   it (`{baseUrl}/models`, `{baseUrl}/messages`), so it carries the API version segment.
 * @param defaultModel preselected model id, or null when the provider has no sensible default.
 * @param requiresApiKey whether a config without a key is invalid (local OpenAI-compatible servers
 *   such as Ollama or LM Studio need none).
 */
enum class LlmProviderType(
    val defaultBaseUrl: String,
    val defaultModel: String?,
    val requiresApiKey: Boolean,
) {
    ANTHROPIC(
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-opus-5-5",
        requiresApiKey = true,
    ),
    OPENAI_COMPATIBLE(
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = null,
        requiresApiKey = false,
    ),
}
