package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.provider.BrowserPrompt
import com.cereal.client.domain.provider.BrowserPromptProvider
import com.cereal.sdk.component.userinteraction.WebResourceRequest

/**
 * In-memory [BrowserPromptProvider] — never opens a browser. Records every prompt in [prompts] and
 * answers it with [respond], which by default completes immediately with a GET of the prompt's URL.
 */
class InMemoryBrowserPromptProvider(
    private val respond: suspend (BrowserPrompt) -> WebResourceRequest = {
        WebResourceRequest("GET", emptyMap(), it.url ?: "about:blank", null)
    },
) : BrowserPromptProvider {
    val prompts = mutableListOf<BrowserPrompt>()

    override suspend fun awaitPrompt(prompt: BrowserPrompt): WebResourceRequest {
        prompts += prompt
        return respond(prompt)
    }
}
