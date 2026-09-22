package com.cereal.client.domain.model.checkout

import com.cereal.client.domain.model.url.WebUrl

/**
 * Domain policy that decides, from a navigated URL, whether the checkout flow has
 * succeeded and whether a navigation event belongs to the marketplace.
 *
 * "Checkout success" is defined as a redirect to the [CHECKOUT_SUCCESS_PATH] path on the
 * marketplace host. Both predicates parse via the shared [WebUrl] value object and compare the
 * parsed host/path, instead of [String.contains], to prevent spoofing via URLs such as:
 *   `https://evil.example/marketplace.cereal-automation.com/?next=/subscribed`.
 *
 * The host is derived once from [marketplaceBaseUrl]; an invalid base URL fails fast on
 * construction, surfacing a misconfiguration rather than silently never matching.
 */
class CheckoutUrlPolicy(
    marketplaceBaseUrl: String,
) {
    private val marketplaceHost: String =
        requireNotNull(WebUrl.parse(marketplaceBaseUrl)?.host) {
            "marketplaceBaseUrl must be an http(s) URL with a host: $marketplaceBaseUrl"
        }

    /**
     * Returns true only when [url] has exactly the marketplace host as its host
     * and its path starts with [CHECKOUT_SUCCESS_PATH].
     */
    fun isCheckoutSuccess(url: String): Boolean {
        val webUrl = WebUrl.parse(url) ?: return false
        return webUrl.host == marketplaceHost && webUrl.path.startsWith(CHECKOUT_SUCCESS_PATH)
    }

    /**
     * Returns true only when [url] belongs to the marketplace host (strict host equality).
     * Used to filter navigation events to the marketplace domain.
     */
    fun isMarketplaceFrame(url: String): Boolean {
        val webUrl = WebUrl.parse(url) ?: return false
        return webUrl.host == marketplaceHost
    }

    companion object {
        private const val CHECKOUT_SUCCESS_PATH = "/subscribed"
    }
}
