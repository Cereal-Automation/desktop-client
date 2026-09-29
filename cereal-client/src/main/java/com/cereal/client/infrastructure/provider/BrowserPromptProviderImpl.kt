package com.cereal.client.infrastructure.provider

import com.cereal.client.application.exception.ChromeNotInstalledException
import com.cereal.client.domain.provider.BrowserPrompt
import com.cereal.client.domain.provider.BrowserPromptProvider
import com.cereal.sdk.component.userinteraction.WebResourceException
import com.cereal.sdk.component.userinteraction.WebResourceRequest
import dev.kdriver.cdp.domain.Network
import dev.kdriver.cdp.domain.network
import dev.kdriver.cdp.domain.page
import dev.kdriver.core.browser.Browser
import dev.kdriver.core.browser.createBrowser
import dev.kdriver.core.exceptions.BrowserExecutableNotFoundException
import dev.kdriver.core.exceptions.NoBrowserExecutablePathException
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder

/** Desktop [BrowserPromptProvider]: opens the prompt in a visible Chrome window. */
class BrowserPromptProviderImpl : BrowserPromptProvider {
    companion object {
        private const val MAX_TAB_OPEN_RETRIES = 20
        private const val TAB_OPEN_RETRY_DELAY_MS = 250L
    }

    override suspend fun awaitPrompt(prompt: BrowserPrompt): WebResourceRequest {
        val browserScope = CoroutineScope(Dispatchers.IO)
        var browser: Browser? = null
        try {
            browser = createBrowser(coroutineScope = browserScope, headless = false)
            val tab = openTab(browser, prompt.url?.ifEmpty { null } ?: "about:blank")

            if (prompt.html?.isNotEmpty() == true) {
                tab.page.enable()
                val frameTree = tab.page.getFrameTree()
                tab.page.setDocumentContent(frameTree.frameTree.frame.id, prompt.html)
            }

            tab.network.enable()
            if (!prompt.headers.isNullOrEmpty()) {
                tab.network.setExtraHTTPHeaders(prompt.headers.mapValues { (_, v) -> JsonPrimitive(v) })
            }

            // The first finishing request or document load failure ends the prompt.
            return merge(
                tab.network.requestWillBeSent
                    .map { it.request.toWebResourceRequest() }
                    .filter { prompt.shouldFinish(it) }
                    .map { Result.success(it) },
                tab.network.loadingFailed
                    .filter { it.type == Network.ResourceType.DOCUMENT }
                    .map {
                        Result.failure<WebResourceRequest>(
                            WebResourceException(failedUrl = "", errorText = it.errorText, errorCode = -1),
                        )
                    },
            ).first().getOrThrow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw mapBrowserException(e) ?: e
        } finally {
            withContext(NonCancellable) { browser?.takeIf { !it.stopped }?.stop() }
            browserScope.cancel()
        }
    }

    /**
     * kdriver's browser.get() synchronously expects the browser's target list to be populated.
     * On fresh browser launch, fetching targets via websocket can race with this call, causing a
     * NoSuchElementException. We retry with a delay to allow the browser to initialize its targets.
     */
    private suspend fun openTab(
        browser: Browser,
        url: String,
    ): Tab {
        var retries = 0
        while (true) {
            try {
                return browser.get(url)
            } catch (e: NoSuchElementException) {
                if (retries >= MAX_TAB_OPEN_RETRIES) throw e
                delay(TAB_OPEN_RETRY_DELAY_MS)
                retries++
            }
        }
    }
}

private fun Network.Request.toWebResourceRequest(): WebResourceRequest {
    val headers =
        headers.mapValues { (_, v) ->
            val str = v.toString()
            // Strip surrounding quotes from JsonElement string representation
            if (str.startsWith("\"") && str.endsWith("\"")) str.substring(1, str.length - 1) else str
        }
    return WebResourceRequest(
        method = method,
        requestHeaders = headers,
        url = url,
        postData = parseFormPostData(postData, headers["Content-Type"] ?: headers["content-type"]),
    )
}

internal fun mapBrowserException(e: Exception): ChromeNotInstalledException? =
    when (e) {
        is NoBrowserExecutablePathException,
        is BrowserExecutableNotFoundException,
        -> ChromeNotInstalledException(cause = e)

        else -> null
    }

/**
 * Parses URL-encoded form POST data into a key-value map.
 * Only parses if the content type is `application/x-www-form-urlencoded`.
 */
private fun parseFormPostData(
    rawPostData: String?,
    contentType: String?,
): Map<String, String>? {
    if (rawPostData.isNullOrEmpty()) return null
    if (contentType?.contains("application/x-www-form-urlencoded") != true) return null

    val result = mutableMapOf<String, String>()
    rawPostData.split("&").forEach { param ->
        val keyValue = param.split("=", limit = 2)
        if (keyValue.size == 2) {
            val key = URLDecoder.decode(keyValue[0], "UTF-8")
            val value = URLDecoder.decode(keyValue[1], "UTF-8")
            result[key] = value
        }
    }
    return result
}
