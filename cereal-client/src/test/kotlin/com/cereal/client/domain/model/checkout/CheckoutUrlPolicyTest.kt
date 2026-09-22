package com.cereal.client.domain.model.checkout

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression tests for #493: checkout URL matching must use strict host + path comparison,
 * not substring matching that allows spoofing via URLs such as:
 *   https://evil.example/marketplace.cereal-automation.com/?next=/subscribed
 */
class CheckoutUrlPolicyTest {
    private val policy = CheckoutUrlPolicy("https://marketplace.cereal-automation.com")

    // ── isCheckoutSuccess ────────────────────────────────────────────────────

    @Test
    fun `isCheckoutSuccess returns true for valid success URL`() {
        assertTrue(policy.isCheckoutSuccess("https://marketplace.cereal-automation.com/subscribed"))
    }

    @Test
    fun `isCheckoutSuccess returns true when path has additional segments after success path`() {
        assertTrue(policy.isCheckoutSuccess("https://marketplace.cereal-automation.com/subscribed?plan=pro"))
    }

    @Test
    fun `isCheckoutSuccess returns false when host contains marketplace host as substring`() {
        assertFalse(policy.isCheckoutSuccess("https://evil.example/marketplace.cereal-automation.com/?next=/subscribed"))
    }

    @Test
    fun `isCheckoutSuccess returns false when path contains success path but is not a prefix`() {
        assertFalse(policy.isCheckoutSuccess("https://marketplace.cereal-automation.com/cancel?next=/subscribed"))
    }

    @Test
    fun `isCheckoutSuccess returns false for cancelled URL on correct host`() {
        assertFalse(policy.isCheckoutSuccess("https://marketplace.cereal-automation.com/cancelled"))
    }

    @Test
    fun `isCheckoutSuccess returns false for malformed URL`() {
        assertFalse(policy.isCheckoutSuccess("not a url ://bad"))
    }

    // ── isMarketplaceFrame ───────────────────────────────────────────────────

    @Test
    fun `isMarketplaceFrame returns true for exact host match`() {
        assertTrue(policy.isMarketplaceFrame("https://marketplace.cereal-automation.com/checkout"))
    }

    @Test
    fun `isMarketplaceFrame returns false when marketplace host appears in path only`() {
        assertFalse(policy.isMarketplaceFrame("https://evil.example/marketplace.cereal-automation.com/checkout"))
    }

    @Test
    fun `isMarketplaceFrame returns false when marketplace host appears in query string`() {
        assertFalse(policy.isMarketplaceFrame("https://evil.example/?redirect=marketplace.cereal-automation.com"))
    }

    @Test
    fun `isMarketplaceFrame returns false for malformed URL`() {
        assertFalse(policy.isMarketplaceFrame(":::bad"))
    }
}
