package com.cereal.client.domain.provider

import com.cereal.sdk.component.userinteraction.WebResourceRequest

/**
 * Launches a script's browser prompt (`showUrl` / `showHtml`) and suspends until it completes.
 *
 * Called on the task's own coroutine, so the prompt lives as long as the task: cancelling the
 * caller (stopping the task) cancels the prompt and closes its browser.
 */
interface BrowserPromptProvider {
    /**
     * Opens [prompt] and returns the first outgoing request for which [BrowserPrompt.shouldFinish]
     * is true. Throws `WebResourceException` when the document fails to load.
     */
    suspend fun awaitPrompt(prompt: BrowserPrompt): WebResourceRequest
}

data class BrowserPrompt(
    val title: String,
    val url: String? = null,
    val html: String? = null,
    val headers: Map<String, String>? = null,
    val shouldFinish: (request: WebResourceRequest) -> Boolean,
)
