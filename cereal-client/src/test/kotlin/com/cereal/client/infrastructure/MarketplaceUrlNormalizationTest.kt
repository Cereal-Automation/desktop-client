package com.cereal.client.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Regression test for issue #500 in the pre-open-source tracker.
 *
 * Verifies that marketplace URL derivation is correct regardless of whether
 * marketplaceBaseUrl is supplied with or without a trailing slash.
 */
class MarketplaceUrlNormalizationTest {
    private fun buildUrls(baseUrl: String): Map<String, String> {
        val normalized = "${baseUrl.trimEnd('/')}/"
        return mapOf(
            "viewProfile" to "${normalized}account",
            "searchScripts" to "${normalized}scripts",
            "viewSubscriptions" to "${normalized}account#billing",
            "register" to "${normalized}register?from_app=true",
            "forgotPassword" to "${normalized}password-reset/request",
        )
    }

    @Test
    fun `urls derived from base URL with trailing slash are correct`() {
        val urls = buildUrls("https://marketplace.cereal-automation.com/")

        assertEquals("https://marketplace.cereal-automation.com/account", urls["viewProfile"])
        assertEquals("https://marketplace.cereal-automation.com/scripts", urls["searchScripts"])
        assertEquals(
            "https://marketplace.cereal-automation.com/account#billing",
            urls["viewSubscriptions"],
        )
        assertEquals(
            "https://marketplace.cereal-automation.com/register?from_app=true",
            urls["register"],
        )
        assertEquals(
            "https://marketplace.cereal-automation.com/password-reset/request",
            urls["forgotPassword"],
        )
    }

    @Test
    fun `urls derived from base URL without trailing slash are correct`() {
        val urls = buildUrls("https://marketplace.cereal-automation.com")

        assertEquals("https://marketplace.cereal-automation.com/account", urls["viewProfile"])
        assertEquals("https://marketplace.cereal-automation.com/scripts", urls["searchScripts"])
        assertEquals(
            "https://marketplace.cereal-automation.com/account#billing",
            urls["viewSubscriptions"],
        )
        assertEquals(
            "https://marketplace.cereal-automation.com/register?from_app=true",
            urls["register"],
        )
        assertEquals(
            "https://marketplace.cereal-automation.com/password-reset/request",
            urls["forgotPassword"],
        )
    }

    @Test
    fun `urls never contain double slash between host and path`() {
        val urls = buildUrls("https://marketplace.cereal-automation.com/")

        urls.values.forEach { url ->
            assertFalse(
                url.removePrefix("https://").contains("//"),
                "URL must not contain double slash: $url",
            )
        }
    }

    @Test
    fun `urls derived from base URL with multiple trailing slashes are correct`() {
        val urls = buildUrls("https://marketplace.cereal-automation.com///")

        assertEquals("https://marketplace.cereal-automation.com/account", urls["viewProfile"])
    }
}
