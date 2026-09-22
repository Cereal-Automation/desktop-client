package com.cereal.client.infrastructure.provider

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.checkout.CheckoutUrlPolicy
import com.cereal.client.domain.provider.CheckoutCancelledException
import com.cereal.client.domain.provider.CheckoutProvider
import com.cereal.client.infrastructure.data.datasource.os.BrowserDataSource
import dev.kdriver.cdp.domain.page
import dev.kdriver.core.browser.Browser
import dev.kdriver.core.browser.createBrowser
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

class CheckoutProviderImpl(
    private val applicationConfig: ApplicationConfig,
    private val browserDataSource: BrowserDataSource,
) : CheckoutProvider {
    private val logger = LoggerFactory.getLogger(CheckoutProviderImpl::class.java)

    private val urlPolicy: CheckoutUrlPolicy
        get() = CheckoutUrlPolicy(applicationConfig.marketplaceBaseUrl)

    companion object {
        private const val CHECKOUT_TIMEOUT_MS = 10L * 60 * 1000 // 10 minutes
        private const val MAX_TAB_OPEN_RETRIES = 20
        private const val TAB_OPEN_RETRY_DELAY_MS = 250L
    }

    override suspend fun awaitCheckout(checkoutUrl: String) {
        val scope = CoroutineScope(Dispatchers.IO)
        try {
            val policy = urlPolicy
            val browser = createBrowser(coroutineScope = scope, headless = false)
            try {
                val tab = openCheckoutTab(browser, checkoutUrl)

                // Enable Page events so frameNavigated fires
                tab.page.enable()

                logger.info("Checkout browser opened, waiting for redirect to marketplace…")

                val finalEvent =
                    withTimeout(CHECKOUT_TIMEOUT_MS) {
                        tab.page.frameNavigated.first { event ->
                            // Only care about top-level frames (no parentId); the domain policy
                            // decides whether the URL belongs to the marketplace.
                            event.frame.parentId == null && policy.isMarketplaceFrame(event.frame.url)
                        }
                    }

                val finalUrl = finalEvent.frame.url
                logger.info("Checkout redirect detected to url: {}, closing browser.", finalUrl)

                if (!policy.isCheckoutSuccess(finalUrl)) {
                    throw CheckoutCancelledException()
                }
            } finally {
                browser.stop()
            }
        } catch (e: CheckoutCancelledException) {
            throw e
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logger.error("Failed to open checkout with kdriver, falling back to default browser.", e)
            CrashReporter.report(e)
            browserDataSource.attemptDesktopBrowse(checkoutUrl)
        }
    }

    /**
     * Opens [checkoutUrl] in a new tab, retrying on transient [NoSuchElementException].
     *
     * kdriver's browser.get() synchronously expects the browser's target list to be populated.
     * On fresh browser launch, fetching targets via websocket can race with this call, causing a
     * NoSuchElementException. We retry with a delay to allow the browser to initialize its targets.
     */
    private suspend fun openCheckoutTab(
        browser: Browser,
        checkoutUrl: String,
    ): Tab {
        var retries = 0
        while (true) {
            try {
                return browser.get(checkoutUrl)
            } catch (e: NoSuchElementException) {
                if (retries >= MAX_TAB_OPEN_RETRIES) throw e
                delay(TAB_OPEN_RETRY_DELAY_MS)
                retries++
            }
        }
    }
}
