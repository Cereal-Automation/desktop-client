package com.cereal.client.domain.provider

/**
 * Handles the external checkout flow for paid marketplace scripts.
 * Opens a browser session at the given checkout URL and suspends until
 * the user is redirected back to the marketplace (payment complete or cancelled).
 */
interface CheckoutProvider {
    suspend fun awaitCheckout(checkoutUrl: String)
}

/**
 * Thrown when the user cancels the external checkout flow.
 */
class CheckoutCancelledException : Exception("Checkout was cancelled.")
